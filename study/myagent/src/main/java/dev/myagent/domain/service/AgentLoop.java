package dev.myagent.domain.service;

import dev.myagent.domain.gateway.AgentTool;
import dev.myagent.domain.gateway.AssistantMessageStream;
import dev.myagent.domain.gateway.StreamFn;
import dev.myagent.domain.gateway.dto.Context;
import dev.myagent.domain.gateway.dto.Model;
import dev.myagent.domain.gateway.dto.StreamEvent;
import dev.myagent.domain.model.message.AssistantMessage;
import dev.myagent.domain.model.message.Content;
import dev.myagent.domain.model.message.Message;
import dev.myagent.domain.model.message.StopReason;
import dev.myagent.domain.model.message.ToolResultMessage;
import dev.myagent.domain.model.session.Session;
import dev.myagent.domain.model.tool.AgentToolResult;
import dev.myagent.domain.model.tool.ToolDescriptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * 领域服务:执行一次 Run(过程)的循环 —— 从 prompts 到模型不再请求工具为止。
 * 对应 pi 的 runLoop + streamAssistantResponse(agent-loop.ts:155 / :281)。
 *
 * <p>自身无状态(快照进、事件出)—— Run 降级为过程后,执行状态就住在这里的方法栈里,
 * 痕迹(追加的消息)归 Session。
 *
 * <p>拥有的不变量(契约见本包 package-info):
 * <ul>
 *   <li><b>停止条件</b>:本轮无 toolCall 且无待注入消息 → 结束;</li>
 *   <li><b>errors-as-data</b>:工具异常在这里被捕获并转成 isError 的 ToolResultMessage
 *       —— 转换边界在循环,不在工具(端口 Javadoc 同款契约);流失败(stopReason=ERROR/ABORTED)
 *       干净收尾,不抛异常;</li>
 *   <li><b>截断全败</b>(T6 功课,未实现):stopReason=LENGTH 时整批 toolCall 不执行。</li>
 * </ul>
 *
 * <p>与 pi 的既定 diff:工具经构造注入(pi 是每次 run 的 context 携带);增量暂不翻译成
 * MessageUpdate 直播(升级点见 AgentEvent.MessageUpdate);三段式合并为私有方法,
 * 阶段 2 引入 before/afterToolCall 时再抽 ToolExecutor。
 */
public final class AgentLoop {

	private final Model model;
	private final StreamFn streamFn;
	private final Map<String, AgentTool> toolsByName;

	public AgentLoop(Model model, StreamFn streamFn, List<AgentTool> tools) {
		this.model = Objects.requireNonNull(model);
		this.streamFn = Objects.requireNonNull(streamFn);
		Map<String, AgentTool> byName = new LinkedHashMap<>();
		for (AgentTool tool : tools) {
			byName.put(tool.descriptor().name(), tool);
		}
		this.toolsByName = Map.copyOf(byName);
	}

	/** 执行一次 Run:注入 prompts → 循环(调 LLM → 执行 toolCall → 回喂)直到停止条件。 */
	public List<Message> run(Session session, List<Message> prompts, Consumer<AgentEvent> emit) {
		List<Message> newMessages = new ArrayList<>();
		List<Message> pending = List.copyOf(prompts);
		emit.accept(new AgentEvent.AgentStart());

		boolean moreToolCalls = true;
		while (moreToolCalls) {
			emit.accept(new AgentEvent.TurnStart());

			// 注入本次 prompts(仅首轮非空;阶段 2 的 steering 也走这个口)
			for (Message prompt : pending) {
				session.append(prompt);
				newMessages.add(prompt);
				emit.accept(new AgentEvent.MessageStart(prompt));
				emit.accept(new AgentEvent.MessageEnd(prompt));
			}
			pending = List.of();

			// 取景框:从 Session 装配(阶段1 无裁剪;compaction/过滤是这里的升级点)
			AssistantMessage assistant = callLlm(session);
			session.append(assistant);
			newMessages.add(assistant);
			emit.accept(new AgentEvent.MessageStart(assistant));
			emit.accept(new AgentEvent.MessageEnd(assistant));

			// 流失败也是数据:干净收尾,循环不死(端口契约的落点)
			if (assistant.stopReason() == StopReason.ERROR || assistant.stopReason() == StopReason.ABORTED) {
				emit.accept(new AgentEvent.TurnEnd(assistant, List.of()));
				break;
			}

			List<Content.ToolCall> toolCalls = toolCallsOf(assistant);
			List<ToolResultMessage> toolResults = new ArrayList<>();
			for (Content.ToolCall toolCall : toolCalls) {
				ToolResultMessage result = executeTool(toolCall, emit);
				toolResults.add(result);
				session.append(result);
				newMessages.add(result);
			}
			emit.accept(new AgentEvent.TurnEnd(assistant, toolResults));

			moreToolCalls = !toolCalls.isEmpty();
		}

		emit.accept(new AgentEvent.AgentEnd(newMessages));
		return newMessages;
	}

	/**
	 * 发起一次 LLM 调用:装配 Context → 经端口 → 收割终态。调用即效应,不可逆。
	 * 直播升级点:实现 MessageUpdate 翻译时,本方法将重新接收 emit —— for 循环里把
	 * TextDelta 翻译成 MessageUpdate 发射(pi 把 assistant 的 message_start/end 也发在这里,
	 * :323/:357;我们没有 partial 消息可带,故暂由 run() 在返回后补发)。
	 */
	private AssistantMessage callLlm(Session session) {
		Context context = new Context(session.systemPrompt(), session.messages(), descriptors());
		AssistantMessageStream stream = streamFn.stream(model, context);
		for (StreamEvent event : stream) {
			// 消费增量但暂不翻译直播(见上:升级点)
		}
		return stream.result();
	}

	/** 找厨房 → 干活 → 拿菜;所有失败路径都产出 isError 的结果,绝不向上抛。 */
	private ToolResultMessage executeTool(Content.ToolCall toolCall, Consumer<AgentEvent> emit) {
		emit.accept(new AgentEvent.ToolExecutionStart(toolCall.id(), toolCall.name(), toolCall.arguments()));

		AgentTool tool = toolsByName.get(toolCall.name());
		AgentToolResult result;
		boolean isError;
		if (tool == null) {
			result = errorResult("找不到工具: " + toolCall.name());
			isError = true;
		} else {
			try {
				result = tool.execute(toolCall.id(), toolCall.arguments()).join();
				isError = false;
			} catch (RuntimeException e) {
				// .join() 会把工具异常包成 CompletionException —— 解包,取真实原因
				Throwable cause = e instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : e;
				result = errorResult(cause.getMessage() == null ? cause.toString() : cause.getMessage());
				isError = true;
			}
		}

		emit.accept(new AgentEvent.ToolExecutionEnd(toolCall.id(), toolCall.name(), result, isError));

		// 翻译:执行产物 → 对话条目(补配对与时间戳;content 同类型直传,零映射)
		ToolResultMessage message = new ToolResultMessage(toolCall.id(), toolCall.name(),
				result.content(), result.details(), isError, Instant.now());
		emit.accept(new AgentEvent.MessageStart(message));
		emit.accept(new AgentEvent.MessageEnd(message));
		return message;
	}

	private List<ToolDescriptor> descriptors() {
		return toolsByName.values().stream().map(AgentTool::descriptor).toList();
	}

	private static List<Content.ToolCall> toolCallsOf(AssistantMessage message) {
		return message.content().stream()
				.filter(content -> content instanceof Content.ToolCall)
				.map(content -> (Content.ToolCall) content)
				.toList();
	}

	private static AgentToolResult errorResult(String message) {
		return new AgentToolResult(List.of(new Content.TextContent(message)), Map.of(), Optional.empty());
	}
}
