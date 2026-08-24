package dev.myagent.infrastructure.tools;

import dev.myagent.domain.gateway.AgentTool;
import dev.myagent.domain.model.message.Content;
import dev.myagent.domain.model.tool.AgentToolResult;
import dev.myagent.domain.model.tool.ToolDescriptor;
import dev.myagent.domain.model.tool.ToolSchema;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * ls 工具:列出目录内容。练两件事 —— 可选参数带默认值(path 缺省当前目录)、
 * 对人友好的排序(目录在前、名称在后)。
 */
public final class LsTool implements AgentTool {

	@Override
	public ToolDescriptor descriptor() {
		return new ToolDescriptor(
				"ls",
				"列出目录内容:目录在前、按名称排序,文件标注字节大小。",
				new ToolSchema(
						Map.of("path", Map.of("type", "string", "description", "目录路径,默认当前目录")),
						List.of()));
	}

	@Override
	public CompletableFuture<AgentToolResult> execute(String toolCallId, Map<String, Object> arguments) {
		String path = ToolArgs.optionalString(arguments, "path", ".");
		return CompletableFuture.completedFuture(list(Path.of(path)));
	}

	private AgentToolResult list(Path path) {
		if (!Files.isDirectory(path)) {
			throw new IllegalArgumentException("不是目录: " + path);
		}
		List<Path> entries;
		try (Stream<Path> stream = Files.list(path)) {
			entries = stream
					.sorted(Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p))
							.thenComparing(p -> p.getFileName().toString()))
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException("列目录失败: " + path + " (" + e.getMessage() + ")", e);
		}

		List<Content.TextContent> content;
		if (entries.isEmpty()) {
			content = List.of(new Content.TextContent("(空目录)"));
		} else {
			content = entries.stream().map(entry -> {
				String name = entry.getFileName().toString();
				if (Files.isDirectory(entry)) {
					return new Content.TextContent("d " + name + "/");
				}
				try {
					return new Content.TextContent("- " + name + " (" + Files.size(entry) + " bytes)");
				} catch (IOException e) {
					return new Content.TextContent("- " + name + " (大小未知)");
				}
			}).toList();
		}

		return new AgentToolResult(
				content,
				Map.of("path", path.toString(), "count", entries.size()),
				Optional.empty());
	}
}
