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
 * Anthropic Messages API 流式适配器。与 OpenAI 的差异就是"端口方言"的活教材:
 * <ul>
 *   <li>端点/鉴权:{baseUrl}/v1/messages + x-api-key + anthropic-version(不是 Bearer);</li>
 *   <li>请求体:system 是独立字段(不进 messages);max_tokens <b>必填</b>;
 *       工具描述叫 input_schema(不叫 parameters);</li>
 *   <li>消息形状:assistant 的工具调用是 tool_use <b>内容块</b>(不是独立字段);
 *       工具结果以 role:user + tool_result 块回传(不是 role:tool);</li>
 *   <li>SSE:按 type 字段路由(content_block_delta / message_stop / error),
 *       没有 [DONE] 哨兵。</li>
 * </ul>
 *
 * <p>这些差异全部被压在适配器肚子里 —— 领域词汇(Message/ToolCall/ToolResult)到端口为止,
 * 这正是"端口说领域语言、方言归适配器"的完整闭环。工具参数的流式拼装由共享的
 * {@link ToolCallAssembler} 承担(两家方言归约后的同一件事);从略的仍是 usage、重试。
 */
public final class AnthropicStreamFn implements StreamFn {

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	private final ObjectMapper json = new ObjectMapper();
	private final String baseUrl;
	private final String apiKey;

	public AnthropicStreamFn(String baseUrl, String apiKey) {
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
				.uri(URI.create(baseUrl + "/v1/messages"))
				.timeout(Duration.ofMinutes(5))
				.header("x-api-key", apiKey)
				.header("anthropic-version", "2023-06-01")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(buildBody(model, context), StandardCharsets.UTF_8))
				.build();

		HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			pipe.complete(failure("HTTP " + response.statusCode() + ": " + readAll(response)));
			return;
		}

		StringBuilder text = new StringBuilder();
		ToolCallAssembler toolCalls = new ToolCallAssembler();
		try (BufferedReader reader =
				new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.startsWith("data:")) {
					continue; // event:/注释行 —— 只认 data:
				}
				String payload = line.substring("data:".length()).trim();
				if (payload.isEmpty()) {
					continue;
				}
				JsonNode node = json.readTree(payload);
				String type = node.path("type").asText("");
				int index = node.path("index").asInt(0);
				if (type.equals("content_block_start")
						&& node.path("content_block").path("type").asText("").equals("tool_use")) {
					// 工具调用身份在此一次给全(id/name),参数随后以分片流入
					JsonNode block = node.path("content_block");
					toolCalls.start(index,
							block.path("id").isTextual() ? block.path("id").asText() : null,
							block.path("name").isTextual() ? block.path("name").asText() : null);
				} else if (type.equals("content_block_delta")) {
					String deltaType = node.path("delta").path("type").asText("");
					if (deltaType.equals("text_delta")) {
						String delta = node.path("delta").path("text").asText("");
						if (!delta.isEmpty()) {
							text.append(delta);
							pipe.push(new StreamEvent.TextDelta(delta));
						}
					} else if (deltaType.equals("input_json_delta")) {
						toolCalls.appendArguments(index, node.path("delta").path("partial_json").asText(""));
					}
				} else if (type.equals("message_stop")) {
					break;
				} else if (type.equals("error")) {
					throw new IllegalStateException("provider 错误: " + payload);
				}
			}
		}

		// 终态:文本块在前,工具调用块在后(参数解析失败 → IOException → 外层转 ERROR)
		List<Content> content = new ArrayList<>();
		if (!text.isEmpty()) {
			content.add(new Content.TextContent(text.toString()));
		}
		content.addAll(toolCalls.finish(json));

		pipe.complete(new AssistantMessage(
				"anthropic", "anthropic", model.id(),
				content,
				Usage.ZERO, StopReason.STOP, Optional.empty(), Instant.now()));
	}

	private String buildBody(Model model, Context context) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model.id());
		body.put("max_tokens", model.maxTokens()); // Anthropic 必填
		body.put("stream", true);
		if (!context.systemPrompt().isEmpty()) {
			body.put("system", context.systemPrompt()); // 独立字段,不进 messages
		}
		List<Map<String, Object>> messages = new ArrayList<>();
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
			// assistant 的内容是块数组:文本块 + tool_use 块(不是独立字段)
			List<Map<String, Object>> blocks = new ArrayList<>();
			String text = concatText(assistant.content());
			if (!text.isEmpty()) {
				blocks.add(Map.of("type", "text", "text", text));
			}
			for (Content content : assistant.content()) {
				if (content instanceof Content.ToolCall call) {
					blocks.add(Map.of(
							"type", "tool_use",
							"id", call.id(),
							"name", call.name(),
							"input", call.arguments() == null ? Map.of() : call.arguments()));
				}
			}
			return Map.of("role", "assistant", "content", blocks);
		}
		if (message instanceof ToolResultMessage tool) {
			// 工具结果以 role:user + tool_result 块回传(不是 role:tool)
			return Map.of(
					"role", "user",
					"content", List.of(Map.of(
							"type", "tool_result",
							"tool_use_id", tool.toolCallId(),
							"content", concatText(tool.content()))));
		}
		throw new IllegalStateException("未知消息类型: " + message);
	}

	private Map<String, Object> toWire(ToolDescriptor tool) {
		return Map.of(
				"name", tool.name(),
				"description", tool.description(),
				"input_schema", tool.parameters()); // 叫 input_schema,不叫 parameters
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
				"anthropic", "anthropic", "unknown",
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
