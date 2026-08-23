package dev.myagent.domain.model.tool;

import dev.myagent.domain.model.message.Content;
import dev.myagent.domain.model.message.Usage;

import java.util.List;
import java.util.Optional;

/** 工具执行产物:content 给模型,details 给 UI / 日志 —— 两份数据刻意分离(pi: types.ts:361)。 */
public record AgentToolResult(List<Content.TextContent> content, Object details, Optional<Usage> usage) {
	public AgentToolResult {
		content = List.copyOf(content);
	}
}
