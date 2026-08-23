package dev.myagent.domain.service;

import dev.myagent.domain.model.tool.AgentToolResult;
import dev.myagent.domain.model.message.AssistantMessage;
import dev.myagent.domain.model.message.Message;
import dev.myagent.domain.model.message.ToolResultMessage;

import java.util.List;
import java.util.Map;

/**
 * Run 的观察事件流,对应 pi 的 AgentEvent(packages/agent/src/types.ts:428-443)。
 * 四层生命周期:agent / turn / message / tool_execution。
 *
 * <p>角色:观察(直播)—— 不落盘、可丢、消费方不能影响执行;
 * 与 session 聚合的 Record(账本,阶段 3)相对。
 * 发出于领域(AgentLoop),分发编排在 application —— 单向不回头。
 *
 * <p>载荷刻意不做防御性拷贝:事件是瞬态的(pi:state 拷贝、event 不拷贝)。
 */
public sealed interface AgentEvent {

	record AgentStart() implements AgentEvent {}

	record AgentEnd(List<Message> messages) implements AgentEvent {}

	record TurnStart() implements AgentEvent {}

	record TurnEnd(Message message, List<ToolResultMessage> toolResults) implements AgentEvent {}

	record MessageStart(Message message) implements AgentEvent {}

	/**
	 * 阶段 1 载荷从简(textDelta);待流事件类型扩展后,升级为结构化 delta
	 * (pi 版本携带完整 assistantMessageEvent)。
	 */
	record MessageUpdate(AssistantMessage message, String textDelta) implements AgentEvent {}

	record MessageEnd(Message message) implements AgentEvent {}

	record ToolExecutionStart(String toolCallId, String toolName, Map<String, Object> arguments)
			implements AgentEvent {}

	record ToolExecutionUpdate(
			String toolCallId,
			String toolName,
			Map<String, Object> arguments,
			AgentToolResult partialResult) implements AgentEvent {}

	record ToolExecutionEnd(String toolCallId, String toolName, AgentToolResult result, boolean isError)
			implements AgentEvent {}
}
