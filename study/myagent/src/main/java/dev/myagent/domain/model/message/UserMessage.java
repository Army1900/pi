package dev.myagent.domain.model.message;

import java.time.Instant;
import java.util.List;

/** 用户消息。内容暂为纯文本块(图片块按需再加)。 */
public record UserMessage(List<Content.TextContent> content, Instant timestamp) implements Message {
	public UserMessage {
		content = List.copyOf(content);
	}
}
