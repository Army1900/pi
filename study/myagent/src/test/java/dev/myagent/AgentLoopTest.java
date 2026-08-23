package dev.myagent;

import dev.myagent.domain.gateway.AgentTool;
import dev.myagent.domain.gateway.AssistantMessageStream;
import dev.myagent.domain.gateway.StreamFn;
import dev.myagent.domain.gateway.dto.Context;
import dev.myagent.domain.gateway.dto.Model;
import dev.myagent.domain.model.message.AssistantMessage;
import dev.myagent.domain.model.message.Content;
import dev.myagent.domain.model.message.Message;
import dev.myagent.domain.model.message.StopReason;
import dev.myagent.domain.model.message.ToolResultMessage;
import dev.myagent.domain.model.message.Usage;
import dev.myagent.domain.model.message.UserMessage;
import dev.myagent.domain.model.session.Session;
import dev.myagent.domain.model.session.SessionId;
import dev.myagent.domain.model.tool.AgentToolResult;
import dev.myagent.domain.model.tool.ToolDescriptor;
import dev.myagent.domain.model.tool.ToolSchema;
import dev.myagent.domain.service.AgentEvent;
import dev.myagent.domain.service.AgentLoop;
import dev.myagent.infrastructure.llm.MockStreamFn;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 1 测试。T1/T2 为参考示例;T3-T6 按 PLAN 补齐(预期 T3/T4 直接绿——实现里已按契约处理,
 * 写出来看它绿也是校验;T6 截断全败会红,是功课)。
 */
class AgentLoopTest {

	/** T1 纯对话:用户问 → mock 答文本 → 一轮结束。 */
	@Test
	void t1_pureConversation() {
		StreamFn llm = new MockStreamFn(List.of(MockStreamFn.textReply("你好,有什么可以帮你?", StopReason.STOP)));
		AgentLoop loop = new AgentLoop(mockModel(), llm, List.of());
		Session session = new Session(new SessionId("s1"), "你是测试助手");
		List<AgentEvent> events = new ArrayList<>();

		List<Message> newMessages = loop.run(session,
				List.of(new UserMessage(List.of(new Content.TextContent("你好")), Instant.now())),
				events::add);

		assertEquals(2, session.messages().size(), "user + assistant 各一条");
		assertInstanceOf(AssistantMessage.class, session.messages().get(1));
		assertEquals(2, newMessages.size());
		assertInstanceOf(AgentEvent.AgentEnd.class, events.get(events.size() - 1), "以 agent_end 收尾");
	}

	/** T2 工具往返:toolCall → 执行 → toolResult 回喂(下一次请求里可见)→ 模型收尾。 */
	@Test
	void t2_toolRoundTrip() {
		AtomicBoolean executed = new AtomicBoolean(false);
		AgentTool echo = new EchoTool(executed);
		RecordingStreamFn llm = new RecordingStreamFn(new MockStreamFn(List.of(
				assistantWithToolCall("c1", "echo", Map.of("text", "hi")),
				MockStreamFn.textReply("echo 完成", StopReason.STOP))));
		AgentLoop loop = new AgentLoop(mockModel(), llm, List.of(echo));
		Session session = new Session(new SessionId("s2"), "你是测试助手");
		List<AgentEvent> events = new ArrayList<>();

		loop.run(session, List.of(new UserMessage(List.of(new Content.TextContent("echo hi")), Instant.now())),
				events::add);

		assertTrue(executed.get(), "工具确实被执行");
		assertEquals(2, llm.requests.size(), "两次 LLM 调用");
		Message lastToLlm = llm.requests.get(1).messages().get(llm.requests.get(1).messages().size() - 1);
		ToolResultMessage toolResult = assertInstanceOf(ToolResultMessage.class, lastToLlm,
				"下一次请求的最后一条是 toolResult");
		assertEquals("c1", toolResult.toolCallId());
		assertEquals(4, session.messages().size(), "user + assistant + toolResult + assistant");
		assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.ToolExecutionStart));
		assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.ToolExecutionEnd));
	}

	// ── 测试基建:这三个类本身就是教具 ─────────────────────────────

	/** 假厨房:实现 AgentTool 端口的最小工具。 */
	static final class EchoTool implements AgentTool {
		private final AtomicBoolean executed;

		EchoTool(AtomicBoolean executed) {
			this.executed = executed;
		}

		@Override
		public ToolDescriptor descriptor() {
			return new ToolDescriptor("echo", "原样返回输入的文本",
					new ToolSchema(Map.of("text", Map.of("type", "string")), List.of("text")));
		}

		@Override
		public CompletableFuture<AgentToolResult> execute(String toolCallId, Map<String, Object> arguments) {
			executed.set(true);
			String text = (String) arguments.get("text");
			return CompletableFuture.completedFuture(new AgentToolResult(
					List.of(new Content.TextContent("echo: " + text)),
					Map.of("length", text.length()),
					Optional.empty()));
		}
	}

	/** 端口可换性的活证明:装饰 StreamFn,记录每次发给 LLM 的 Context。 */
	static final class RecordingStreamFn implements StreamFn {
		final StreamFn delegate;
		final List<Context> requests = new ArrayList<>();

		RecordingStreamFn(StreamFn delegate) {
			this.delegate = delegate;
		}

		@Override
		public AssistantMessageStream stream(Model model, Context context) {
			requests.add(context);
			return delegate.stream(model, context);
		}
	}

	static Model mockModel() {
		return new Model("mock", "mock", "mock", "mock", 8192, 2048);
	}

	static AssistantMessage assistantWithToolCall(String id, String name, Map<String, Object> arguments) {
		return new AssistantMessage("mock", "mock", "mock",
				List.of(new Content.ToolCall(id, name, arguments)), Usage.ZERO, StopReason.STOP,
				Optional.empty(), Instant.now());
	}
}
