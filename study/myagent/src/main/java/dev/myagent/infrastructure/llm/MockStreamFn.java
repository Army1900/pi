package dev.myagent.infrastructure.llm;

import dev.myagent.domain.gateway.AssistantMessageStream;
import dev.myagent.domain.gateway.dto.StreamEvent;
import dev.myagent.domain.gateway.StreamFn;
import dev.myagent.domain.gateway.dto.Context;
import dev.myagent.domain.gateway.dto.Model;
import dev.myagent.domain.model.message.AssistantMessage;
import dev.myagent.domain.model.message.Content;
import dev.myagent.domain.model.message.StopReason;
import dev.myagent.domain.model.message.Usage;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * StreamFn 的假适配器(对应 pi 测试的 MockAssistantStream,test/agent-loop.test.ts:16)。
 * 领域循环无从分辨对面是 mock 还是真 provider —— T1-T6 零网络跑通即靠此。
 *
 * <p>剧本耗尽时遵守端口契约:返回 stopReason=ERROR 的消息,而不是抛异常。
 */
public final class MockStreamFn implements StreamFn {

	private final Deque<AssistantMessage> script;

	public MockStreamFn(List<AssistantMessage> scriptedReplies) {
		this.script = new ArrayDeque<>(scriptedReplies);
	}

	@Override
	public AssistantMessageStream stream(Model model, Context context) {
		AssistantMessageStream stream = new AssistantMessageStream();
		AssistantMessage reply = script.poll();
		if (reply == null) {
			stream.complete(textReply("mock 剧本耗尽:没有更多脚本化回复", StopReason.ERROR));
			return stream;
		}
		for (Content content : reply.content()) {
			if (content instanceof Content.TextContent text) {
				stream.push(new StreamEvent.TextDelta(text.text()));
			}
		}
		stream.complete(reply);
		return stream;
	}

	/** 便捷工厂:构造一条纯文本回复(对照 pi 的 createAssistantMessage,test:55)。 */
	public static AssistantMessage textReply(String text, StopReason stopReason) {
		boolean isFailure = stopReason == StopReason.ERROR || stopReason == StopReason.ABORTED;
		return new AssistantMessage(
				"mock",
				"mock",
				"mock",
				text == null || text.isEmpty() ? List.of() : List.of(new Content.TextContent(text)),
				Usage.ZERO,
				stopReason,
				isFailure ? Optional.of(text) : Optional.empty(),
				Instant.now());
	}
}
