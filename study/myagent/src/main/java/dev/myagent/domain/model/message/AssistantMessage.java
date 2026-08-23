package dev.myagent.domain.model.message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 模型回复。失败也是数据:stopReason = ERROR / ABORTED 时 errorMessage 说明原因 ——
 * pi 的 StreamFn 契约(禁止抛异常,失败编码为消息)在此落型。
 */
public record AssistantMessage(
		String api,
		String provider,
		String model,
		List<Content> content,
		Usage usage,
		StopReason stopReason,
		Optional<String> errorMessage,
		Instant timestamp) implements Message {
	public AssistantMessage {
		content = List.copyOf(content);
	}
}
