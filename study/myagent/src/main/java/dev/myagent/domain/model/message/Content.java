package dev.myagent.domain.model.message;

import java.util.Map;

/**
 * 消息内容块。对应 pi 的 content 联合;阶段 1 仅 text 与 toolCall(thinking 块按需再加)。
 */
public sealed interface Content {

	record TextContent(String text) implements Content {}

	/**
	 * 模型发起的工具调用。arguments 是 JSON 对象形状 —— 阶段 1 用 {@code Map<String, Object>} 承载,
	 * 类型安全靠执行前校验补(见 AgentTool:pi 用 typebox 一鱼两吃,Java 无此物,PLAN diff 点)。
	 */
	record ToolCall(String id, String name, Map<String, Object> arguments) implements Content {}
}
