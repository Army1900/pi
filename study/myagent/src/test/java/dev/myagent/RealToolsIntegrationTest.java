package dev.myagent;

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
import dev.myagent.domain.service.AgentLoop;
import dev.myagent.infrastructure.llm.MockStreamFn;
import dev.myagent.infrastructure.tools.BashTool;
import dev.myagent.infrastructure.tools.LsTool;
import dev.myagent.infrastructure.tools.ReadTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端集成:真工具(read/ls/bash,真实的文件与进程效应)× 真循环 × 假模型。
 * 证明三个适配器能被 AgentLoop 完整调用——工具链不只是单元级可运行。
 * (真模型接线是"配置 + Main"那一步,不在本测试范围。)
 */
class RealToolsIntegrationTest {

	@TempDir
	Path dir;

	@Test
	void agentUsesRealToolsEndToEnd() throws IOException {
		Path file = dir.resolve("num.txt");
		Files.writeString(file, "21", StandardCharsets.UTF_8);

		// 剧本:"模型"先要 read,看到 21 后要 bash 算 ×2,最后收尾
		MockStreamFn llm = new MockStreamFn(List.of(
				toolCall("c1", "read", Map.of("path", file.toString())),
				toolCall("c2", "bash", Map.of("command", "echo $(( $(cat " + file + ") * 2 ))")),
				MockStreamFn.textReply("文件里的数字乘 2 是 42。", StopReason.STOP)));

		AgentLoop loop = new AgentLoop(
				new Model("mock", "mock", "mock", "mock", 8192, 2048),
				llm,
				List.of(new ReadTool(), new LsTool(), new BashTool()));
		Session session = new Session(new SessionId("s1"), "你是测试助手");

		loop.run(session,
				List.of(new UserMessage(List.of(new Content.TextContent("num.txt 里的数字乘 2 是多少?")), Instant.now())),
				event -> {
				});

		List<ToolResultMessage> toolResults = session.messages().stream()
				.filter(message -> message instanceof ToolResultMessage)
				.map(message -> (ToolResultMessage) message)
				.toList();

		assertEquals(2, toolResults.size(), "read 和 bash 各执行一次");
		assertFalse(toolResults.get(0).isError());
		assertFalse(toolResults.get(1).isError());
		assertEquals("21", toolResults.get(0).content().get(0).text().trim(), "read 真读到了文件内容");
		assertTrue(toolResults.get(1).content().get(0).text().contains("42"), "bash 真算出了 42");
	}

	private static AssistantMessage toolCall(String id, String name, Map<String, Object> arguments) {
		return new AssistantMessage("mock", "mock", "mock",
				List.of(new Content.ToolCall(id, name, arguments)), Usage.ZERO, StopReason.STOP,
				Optional.empty(), Instant.now());
	}
}
