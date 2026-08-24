package dev.myagent.infrastructure.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.myagent.domain.model.message.Content;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 工具调用流式拼装器 —— 两家方言剥掉外壳后共享的同一件事:
 * 身份按 index 注册,参数以<b>字符串分片</b>到达、拼接,完整后 JSON 解析。
 *
 * <ul>
 *   <li>OpenAI:delta.tool_calls[].function.arguments 分片(id/name 随首片);</li>
 *   <li>Anthropic:content_block_start 给全身份,input_json_delta.partial_json 分片。</li>
 * </ul>
 *
 * <p>与 pi 的差异记录:pi 在这里用"尽力抢救的 JSON 解析器"(agent-loop.ts 注释);
 * 我们从严 —— 解析失败即流错误,由外层转 ERROR 终态(失败也是数据)。
 */
final class ToolCallAssembler {

	private static final class Builder {
		String id = "";
		String name = "";
		final StringBuilder arguments = new StringBuilder();
	}

	/** TreeMap:按 index 排序输出(并行调用保序)。 */
	private final Map<Integer, Builder> builders = new TreeMap<>();

	/** 注册/补全身份:非空才生效 —— OpenAI 后续分片不带 id/name,天然幂等。 */
	void start(int index, String id, String name) {
		Builder builder = builders.computeIfAbsent(index, key -> new Builder());
		if (id != null && !id.isEmpty()) {
			builder.id = id;
		}
		if (name != null && !name.isEmpty()) {
			builder.name = builder.name.isEmpty() ? name : builder.name + name; // name 理论上整片到达,拼接以防万一
		}
	}

	/** 追加一段参数 JSON 分片。 */
	void appendArguments(int index, String fragment) {
		if (fragment == null || fragment.isEmpty()) {
			return;
		}
		builders.computeIfAbsent(index, key -> new Builder()).arguments.append(fragment);
	}

	boolean isEmpty() {
		return builders.isEmpty();
	}

	/** 拼装完成:按 index 序产出 ToolCall 块。参数 JSON 解析失败抛 IOException → 外层转 ERROR。 */
	List<Content.ToolCall> finish(ObjectMapper json) throws IOException {
		List<Content.ToolCall> calls = new ArrayList<>();
		for (Builder builder : builders.values()) {
			calls.add(new Content.ToolCall(builder.id, builder.name, parseArguments(json, builder.arguments.toString())));
		}
		return calls;
	}

	private static Map<String, Object> parseArguments(ObjectMapper json, String raw) throws IOException {
		if (raw.isBlank()) {
			return Map.of();
		}
		return json.readValue(raw, new TypeReference<Map<String, Object>>() {});
	}
}
