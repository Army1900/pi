package dev.myagent.infrastructure.tools;

import dev.myagent.domain.gateway.AgentTool;
import dev.myagent.domain.model.message.Content;
import dev.myagent.domain.model.tool.AgentToolResult;
import dev.myagent.domain.model.tool.ToolDescriptor;
import dev.myagent.domain.model.tool.ToolSchema;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * read 工具:读取文本文件,练三件事 —— 手写 schema 校验、错误路径(不存在的文件 →
 * 异常 → 循环转 isError)、截断(护上下文窗口:pi 用整份 truncate.ts 回答的问题,
 * 这里以"行数上限 + 标注行"作最小答案)。
 *
 * <p>content / details 分离的示范:content 给模型(行文本),details 给 UI
 * (路径、总行数、是否截断)。
 */
public final class ReadTool implements AgentTool {

	static final int DEFAULT_MAX_LINES = 500;

	@Override
	public ToolDescriptor descriptor() {
		return new ToolDescriptor(
				"read",
				"读取文本文件内容,按行返回;超出 maxLines 截断并标注。",
				new ToolSchema(
						Map.of(
								"path", Map.of("type", "string", "description", "要读取的文件路径"),
								"maxLines", Map.of("type", "integer", "description", "最多返回行数,默认 " + DEFAULT_MAX_LINES)),
						List.of("path")));
	}

	@Override
	public CompletableFuture<AgentToolResult> execute(String toolCallId, Map<String, Object> arguments) {
		String path = ToolArgs.requireString(arguments, "path");
		int maxLines = ToolArgs.optionalPositiveInt(arguments, "maxLines", DEFAULT_MAX_LINES);
		return CompletableFuture.completedFuture(read(Path.of(path), maxLines));
	}

	private AgentToolResult read(Path path, int maxLines) {
		try {
			List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

			// 呈现/安全关注点:截断独立成段,不与读取搅拌(pi: truncate.ts 的精神)
			boolean truncated = lines.size() > maxLines;
			List<Content.TextContent> content = new ArrayList<>(
					lines.subList(0, truncated ? maxLines : lines.size()).stream()
							.map(Content.TextContent::new)
							.toList());
			if (truncated) {
				content.add(new Content.TextContent("…(已截断:显示 " + maxLines + " / 共 " + lines.size() + " 行)"));
			}

			return new AgentToolResult(
					content,
					Map.of("path", path.toString(), "lines", lines.size(), "truncated", truncated),
					Optional.empty());
		} catch (IOException e) {
			// 错误路径:抛,由循环转 isError —— 工具不自己编码错误(pi 契约)
			throw new UncheckedIOException("读取失败: " + path + " (" + e.getMessage() + ")", e);
		}
	}
}
