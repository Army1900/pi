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

/**
 * <b>参考实现,不接线</b>(PLAN 范围外:学习项目 mock 到底)。没有任何代码引用本类 ——
 * 它的存在意义:① 证明 StreamFn 端口契约可被真实 HTTP 流式适配器实现(编译器背书);
 * ② 作为流式概念的教学实物。
 *
 * <p>阅读要点(每一处都对应一条已讨论过的契约):
 * <ul>
 *   <li><b>发起即返回</b>:{@link #stream} 只负责"开始" —— 虚拟线程上跑生成,方法体毫秒级返回,
 *       交出去的管道是<b>空的</b>;</li>
 *   <li><b>边到边推</b>:SSE 每行到达即解析、push —— 消费端 take() 被 push 唤醒,边到边转;</li>
 *   <li><b>失败也是数据</b>:外层 catch Throwable → complete(stopReason=ERROR 的消息),
 *       永不抛 —— 端口失败契约的落点(HTTP 错、断线、解析失败全走同一条路);</li>
 *   <li><b>终态恰好一次</b>:成功/失败都汇聚到 complete,管道随之关闭;</li>
 *   <li><b>翻译的逆过程</b>:toWire 系列把领域词汇(Message/ToolDescriptor)译成 provider 线格式 ——
 *       端口方言住在这里,这正是"领域名词留 model、方言归适配器"的另一半;</li>
 *   <li><b>Jackson/HTTP 只住 infra</b>:domain 对它们一无所知(防泄漏自检的反面:适配器内随便说技术方言)。</li>
 * </ul>
 *
 * <p>刻意从略(阶段 2 的活):abort(线程中断 → 应转 ABORTED 而非 ERROR)、tool_call 流式增量拼接
 * (delta.tool_calls 的分片组装)、usage 统计、重试与限流。线格式以 OpenAI chat-completions 为样。
 */
public final class RealStreamFn implements StreamFn {

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	private final ObjectMapper json = new ObjectMapper();
	private final String apiKey;

	public RealStreamFn(String apiKey) {
		this.apiKey = apiKey;
	}

	@Override
	public AssistantMessageStream stream(Model model, Context context) {
		AssistantMessageStream pipe = new AssistantMessageStream();
		// "等待"被挪出方法体:生成在虚拟线程上进行,方法体只负责发起
		Thread.ofVirtual().name("llm-stream").start(() -> {
			try {
				pump(model, context, pipe);
			} catch (Throwable t) {
				pipe.complete(failure(t.getMessage() == null ? t.toString() : t.getMessage()));
			}
		});
		return pipe; // ← 此刻管道是空的,LLM 还没吐出第一个 token
	}

	private void pump(Model model, Context context, AssistantMessageStream pipe) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api.example.com/v1/chat/completions")) // 实际应取自 Model.baseUrl,此处示意
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
					continue; // SSE 注释/心跳
				}
				String payload = line.substring("data: ".length());
				if ("[DONE]".equals(payload.trim())) {
					break; // 结束哨兵
				}
				JsonNode node = json.readTree(payload);
				String delta = node.path("choices").path(0).path("delta").path("content").asText("");
				if (!delta.isEmpty()) {
					text.append(delta);
					pipe.push(new StreamEvent.TextDelta(delta)); // 每个 chunk 到达即直播
				}
				// delta.tool_calls(分片拼接工具调用)与 usage 同理,形状相同,从略
			}
		}

		pipe.complete(new AssistantMessage(
				"openai", "openai", model.id(),
				text.isEmpty() ? List.of() : List.of(new Content.TextContent(text.toString())),
				Usage.ZERO, StopReason.STOP, Optional.empty(), Instant.now()));
	}

	// ── 领域词汇 → 线格式(装配的逆翻译)──────────────────────────────

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

	/** 失败终态(与 MockStreamFn.textReply 同形 —— 第二处出现,抽公共工厂的移动触发器已到)。 */
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
