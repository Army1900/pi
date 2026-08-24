package dev.myagent.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.myagent.domain.model.message.Usage;
import dev.myagent.domain.model.message.UserMessage;
import dev.myagent.domain.model.tool.ToolDescriptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI 兼容(chat-completions)流式适配器 —— 原 RealStreamFn 参考实现的转正:
 * baseUrl 从硬编码占位改为构造参数。契约与教学注释见类族:发起即返回(空管道)、
 * 边到边推、失败也是数据(catch Throwable → complete(ERROR))、终态恰好一次。
 *
 * <p>从略(与 Anthropic 适配器同注):tool_call 流式增量拼接、usage、重试限流;
 * 两个适配器刻意各自独立可读,共享骨架(虚拟线程 + SSE 循环)的抽取留作触发器:
 * 第三个 provider 出现时再抽。
 */
public final class OpenAiStreamFn implements StreamFn {

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	private final ObjectMapper json = new ObjectMapper();
	private final String baseUrl;
	private final String apiKey;

	public OpenAiStreamFn(String baseUrl, String apiKey) {
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
	}

	@Override
	public AssistantMessageStream stream(Model model, Context context) {
		AssistantMessageStream pipe = new AssistantMessageStream();
		Thread.ofVirtual().name("llm-stream").start(() -> {
			try {
				pump(model, context, pipe);
			} catch (Throwable t) {
				pipe.complete(failure(t.getMessage() == null ? t.toString() : t.getMessage()));
			}
		});
		return pipe;
	}

	private void pump(Model model, Context context, AssistantMessageStream pipe) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/chat/completions"))
				.timeout(Duration.ofMinutes(5))
				.header("Authorization", "Bearer " + apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(buildBody(model, context), StandardCharsets.UTF_8))
				.build();

		HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			pipe.complete(failure("HTTP " + response.statusCode() + ": " + readAll(response)));
			return;
		}

		StringBuilder text = new StringBuilder();
		try (BufferedReader reader =
				new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.startsWith("data: ")) {
					continue;
				}
				String payload = line.substring("data: ".length());
				if ("[DONE]".equals(payload.trim())) {
					break;
				}
				JsonNode node = json.readTree(payload);
				String delta = node.path("choices").path(0).path("delta").path("content").asText("");
				if (!delta.isEmpty()) {
					text.append(delta);
					pipe.push(new StreamEvent.TextDelta(delta));
				}
			}
		}

		pipe.complete(new AssistantMessage(
				"openai", "openai", model.id(),
				text.isEmpty() ? List.of() : List.of(new Content.TextContent(text.toString())),
				Usage.ZERO, StopReason.STOP, Optional.empty(), Instant.now()));
	}

	private String buildBody(Model model, Context context) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model.id());
		body.put("stream", true);
		List<Map<String, Object>> messages = new ArrayList<>();
		if (!context.systemPrompt().isEmpty()) {
			messages.add(Map.of("role", "system", "content", context.systemPrompt()));
		}
		for (Message message : context.messages()) {
			messages.add(toWire(message));
		}
		body.put("messages", messages);
		if (!context.tools().isEmpty()) {
			body.put("tools", context.tools().stream().map(this::toWire).toList());
		}
		return json.writeValueAsString(body);
	}

	private Map<String, Object> toWire(Message message) {
		if (message instanceof UserMessage user) {
			return Map.of("role", "user", "content", concatText(user.content()));
		}
		if (message instanceof AssistantMessage assistant) {
			Map<String, Object> wire = new LinkedHashMap<>();
			wire.put("role", "assistant");
			wire.put("content", concatText(assistant.content()));
			List<Map<String, Object>> calls = new ArrayList<>();
			for (Content content : assistant.content()) {
				if (content instanceof Content.ToolCall call) {
					calls.add(Map.of(
							"id", call.id(),
							"type", "function",
							"function", Map.of(
									"name", call.name(),
									"arguments", json.valueToTree(call.arguments()).toString())));
				}
			}
			if (!calls.isEmpty()) {
				wire.put("tool_calls", calls);
			}
			return wire;
		}
		if (message instanceof ToolResultMessage tool) {
			return Map.of(
					"role", "tool",
					"tool_call_id", tool.toolCallId(),
					"content", concatText(tool.content()));
		}
		throw new IllegalStateException("未知消息类型: " + message);
	}

	private Map<String, Object> toWire(ToolDescriptor tool) {
		return Map.of("type", "function", "function", Map.of(
				"name", tool.name(),
				"description", tool.description(),
				"parameters", tool.parameters()));
	}

	private static String concatText(List<? extends Content> contents) {
		StringBuilder text = new StringBuilder();
		for (Content content : contents) {
			if (content instanceof Content.TextContent tc) {
				text.append(tc.text());
			}
		}
		return text.toString();
	}

	private static AssistantMessage failure(String reason) {
		return new AssistantMessage(
				"openai", "openai", "unknown",
				List.of(new Content.TextContent(reason)),
				Usage.ZERO, StopReason.ERROR, Optional.of(reason), Instant.now());
	}

	private static String readAll(HttpResponse<InputStream> response) {
		try (InputStream in = response.body()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "<响应体不可读>";
		}
	}
}
