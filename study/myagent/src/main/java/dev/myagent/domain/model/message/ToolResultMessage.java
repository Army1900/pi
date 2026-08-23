package dev.myagent.domain.model.message;

import java.time.Instant;
import java.util.List;

/**
 * 工具执行结果消息,凭 toolCallId 与请求它的 Content.ToolCall 配对。
 * isError 表达"预期失败"(errors-as-data 的落点);details 是给 UI / 日志的结构化数据,
 * 与给模型看的 content 刻意分离。
 */
public record ToolResultMessage(
		String toolCallId,
		String toolName,
		List<Content.TextContent> content,
		Object details,
		boolean isError,
		Instant timestamp) implements Message {
	public ToolResultMessage {
		content = List.copyOf(content);
	}
}
