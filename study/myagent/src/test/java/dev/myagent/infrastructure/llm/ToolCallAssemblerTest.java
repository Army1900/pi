package dev.myagent.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.myagent.domain.model.message.Content;
import dev.myagent.infrastructure.llm.ToolCallAssembler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具调用流式拼装:两家方言(OpenAI 分片 / Anthropic 分片)归约后的共同机制,脱网单测。
 */
class ToolCallAssemblerTest {

	private final ObjectMapper json = new ObjectMapper();

	@Test
	void openAiStyleFragmentsAssembleInOrder() throws IOException {
		ToolCallAssembler assembler = new ToolCallAssembler();

		// OpenAI 形状:首片带 id+name,后续只带 arguments 分片
		assembler.start(0, "call_1", "read");
		assembler.appendArguments(0, "{\"pa");
		assembler.appendArguments(0, "th\": \"/tm");
		assembler.appendArguments(0, "p/a.txt\"}");

		List<Content.ToolCall> calls = assembler.finish(json);

		assertEquals(1, calls.size());
		assertEquals("call_1", calls.get(0).id());
		assertEquals("read", calls.get(0).name());
		assertEquals("/tmp/a.txt", calls.get(0).arguments().get("path"));
	}

	@Test
	void anthropicStyleStartOnceThenFragments() throws IOException {
		ToolCallAssembler assembler = new ToolCallAssembler();

		assembler.start(0, "toolu_1", "bash");
		assembler.appendArguments(0, "{\"comma");
		assembler.appendArguments(0, "nd\": \"echo hi\"}");

		List<Content.ToolCall> calls = assembler.finish(json);

		assertEquals("bash", calls.get(0).name());
		assertEquals("echo hi", calls.get(0).arguments().get("command"));
	}

	@Test
	void parallelCallsKeyedByIndexStaySorted() throws IOException {
		ToolCallAssembler assembler = new ToolCallAssembler();

		assembler.start(1, "call_b", "ls");
		assembler.appendArguments(1, "{}");
		assembler.start(0, "call_a", "read");
		assembler.appendArguments(0, "{}");

		List<Content.ToolCall> calls = assembler.finish(json);

		assertEquals(2, calls.size());
		assertEquals("call_a", calls.get(0).id(), "按 index 排序,与到达顺序无关");
		assertEquals("call_b", calls.get(1).id());
	}

	@Test
	void blankArgumentsBecomeEmptyMap() throws IOException {
		ToolCallAssembler assembler = new ToolCallAssembler();
		assembler.start(0, "call_x", "ping");

		assertTrue(assembler.finish(json).get(0).arguments().isEmpty());
	}

	@Test
	void malformedArgumentsJsonIsAnError() {
		ToolCallAssembler assembler = new ToolCallAssembler();
		assembler.start(0, "call_bad", "read");
		assembler.appendArguments(0, "{\"path\": /未闭合");

		assertThrows(IOException.class, () -> assembler.finish(json),
				"从严:解析失败即流错误(pi 用抢救式解析,差异记录在案)");
	}
}
