package dev.myagent.domain.gateway.dto;

import dev.myagent.domain.model.message.AssistantMessage;

/**
 * 流式增量事件(阶段 1 最小集)。
 * pi 版本含 text_start/end、thinking_*、toolcall_* 增量;按需扩,
 * 扩展时同步升级 AgentEvent.MessageUpdate 的载荷(见其 Javadoc)。
 */
public sealed interface StreamEvent {

	record TextDelta(String text) implements StreamEvent {}

	/** 终态:携带最终 AssistantMessage —— 成功与失败都是它(失败=stopReason ERROR/ABORTED)。 */
	record Completed(AssistantMessage message) implements StreamEvent {}
}
