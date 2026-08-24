package dev.myagent;

import dev.myagent.infrastructure.tools.BashTool;
import dev.myagent.infrastructure.tools.LsTool;
import dev.myagent.infrastructure.tools.ReadTool;
import dev.myagent.domain.model.message.Content;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三个基础工具的行为验收:校验、截断、错误路径、异步、超时。 */
class ToolsTest {

	@TempDir
	Path dir;

	@Test
	void readReturnsLinesAndTruncates() throws IOException {
		Path file = dir.resolve("a.txt");
		Files.write(file, List.of("one", "two", "three"), StandardCharsets.UTF_8);

		var result = new ReadTool().execute("c1", Map.of("path", file.toString(), "maxLines", 2)).join();

		assertEquals(3, result.content().size(), "两行内容 + 一行截断标注");
		assertTrue(result.content().get(2).text().contains("已截断"));
	}

	@Test
	void readMissingFileFailsAsException() {
		// 同步工具(completedFuture)的失败面:异常在 execute() 直接抛,不经过 future
		// —— 异步工具(bash)则是 join() 时以 CompletionException 冒出;循环的 try 覆盖两路
		assertThrows(java.io.UncheckedIOException.class,
				() -> new ReadTool().execute("c2", Map.of("path", dir.resolve("nope").toString())));
	}

	@Test
	void readMissingRequiredArgumentFails() {
		assertThrows(IllegalArgumentException.class, () -> new ReadTool().execute("c3", Map.of()));
	}

	@Test
	void lsListsDirectoriesFirstWithSizes() throws IOException {
		Files.createDirectory(dir.resolve("sub"));
		Files.writeString(dir.resolve("b.txt"), "hello", StandardCharsets.UTF_8);

		var result = new LsTool().execute("c4", Map.of("path", dir.toString())).join();

		assertEquals(2, result.content().size());
		assertTrue(result.content().get(0).text().startsWith("d sub/"), "目录在前");
		assertTrue(result.content().get(1).text().contains("b.txt"));
		assertTrue(result.content().get(1).text().contains("5 bytes"));
	}

	@Test
	void bashCapturesOutput() {
		var result = new BashTool().execute("c5", Map.of("command", "echo hello")).join();

		assertEquals(1, result.content().size());
		assertTrue(result.content().get(0).text().contains("hello"));
	}

	@Test
	void bashNonZeroExitFailsButOutputVisible() {
		var thrown = assertThrows(CompletionException.class,
				() -> new BashTool().execute("c6", Map.of("command", "echo oops >&2; exit 3")).join());
		// stderr 已并入,失败消息里输出可见(errors-as-data:失败但可读)
		assertTrue(thrown.getCause().getMessage().contains("oops"));
		assertTrue(thrown.getCause().getMessage().contains("exit 3"));
	}

	@Test
	void bashTimeoutKillsProcess() {
		var thrown = assertThrows(CompletionException.class,
				() -> new BashTool().execute("c7", Map.of("command", "sleep 5", "timeoutMs", 200)).join());
		assertTrue(thrown.getCause().getMessage().contains("超时"));
	}
}
