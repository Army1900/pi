package dev.myagent.domain.gateway.dto;

import dev.myagent.domain.model.message.Message;
import dev.myagent.domain.model.tool.ToolDescriptor;

import java.util.List;

/**
 * 一次 LLM 请求的【装配快照 / 取景框】:这一次调用里模型看到什么(pi 的 Context,ai/types.ts:509)。
 *
 * <p>不是第二份真相,是从 Session <b>派生</b>的一次性视图:消息可裁剪(compaction / 过滤 /
 * 注入 steering,pi 的装配点在 streamAssistantResponse);systemPrompt 由会话配置提供,
 * 本类型只是捎给端口的信使。短命:装配、发送、丢弃。
 *
 * <p>持有的是 ToolDescriptor(描述),不是可执行的 AgentTool —— provider 只需要广播"有哪些手"。
 */
public record Context(String systemPrompt, List<Message> messages, List<ToolDescriptor> tools) {
	public Context {
		messages = List.copyOf(messages);
		tools = List.copyOf(tools);
	}
}
