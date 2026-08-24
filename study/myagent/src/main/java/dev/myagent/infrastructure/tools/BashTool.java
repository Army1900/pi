package dev.myagent.infrastructure.tools;

import dev.myagent.domain.gateway.AgentTool;
import dev.myagent.domain.model.message.Content;
import dev.myagent.domain.model.tool.AgentToolResult;
import dev.myagent.domain.model.tool.ToolDescriptor;
import dev.myagent.domain.model.tool.ToolSchema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * bash 工具:shell 执行命令。这一课练的全是异步与生命周期的硬骨头:
 *
 * <ul>
 *   <li><b>异步</b>:execute 立刻返回未完成的 future,命令在虚拟线程上跑
 *       —— 与 AssistantMessageStream 同款形状("等待被挪出方法体");</li>
 *   <li><b>超时</b>:waitFor(timeoutMs) 到点 destroyForcibly,超时以异常表达
 *       → 循环转 isError(errors-as-data);</li>
 *   <li><b>中断</b>:worker 被中断时杀进程并恢复中断标志(阶段 2 的 abort 从这里进场);</li>
 *   <li><b>管道排空 + 读等并发</b>:输出必须被持续消费(否则管道缓冲区满 → 子进程写阻塞 → 假死),
 *       且消费必须与 waitFor 并发 —— 先读后等会让超时永不触发(初版的真实 bug,测试抓住);</li>
 *   <li><b>非零退出 = 失败</b>:输出随异常消息上交 → isError 结果里模型仍看得到完整输出
 *       (pi/Claude Code 的惯例:失败但输出可见)。</li>
 * </ul>
 *
 * <p>诚实的简化(注释即账):超时时未保留部分输出;输出上限以字符计。
 * 阶段 2 的 abort 会补上调用方侧的协作取消。
 */
public final class BashTool implements AgentTool {

	static final int DEFAULT_TIMEOUT_MS = 30_000;
	static final int MAX_OUTPUT_CHARS = 10_000;

	@Override
	public ToolDescriptor descriptor() {
		return new ToolDescriptor(
				"bash",
				"在 shell 中执行命令并返回合并后的输出(stdout+stderr)。超时自动终止;非零退出视为失败但输出仍可见。",
				new ToolSchema(
						Map.of(
								"command", Map.of("type", "string", "description", "要执行的 shell 命令"),
								"timeoutMs", Map.of("type", "integer", "description", "超时毫秒数,默认 " + DEFAULT_TIMEOUT_MS)),
						List.of("command")));
	}

	@Override
	public CompletableFuture<AgentToolResult> execute(String toolCallId, Map<String, Object> arguments) {
		String command = ToolArgs.requireString(arguments, "command");
		long timeoutMs = ToolArgs.optionalPositiveInt(arguments, "timeoutMs", DEFAULT_TIMEOUT_MS);

		CompletableFuture<AgentToolResult> future = new CompletableFuture<>();
		Thread.ofVirtual().name("bash-tool").start(() -> {
			try {
				future.complete(run(command, timeoutMs));
			} catch (Throwable t) {
				future.completeExceptionally(t);
			}
		});
		return future; // 立刻返回:命令在虚拟线程上跑,调用方 join 时才等待
	}

	private AgentToolResult run(String command, long timeoutMs) {
		Process process;
		try {
			process = new ProcessBuilder("/bin/sh", "-c", command)
					.redirectErrorStream(true) // stderr 并入 stdout,一并给模型
					.start();
		} catch (IOException e) {
			throw new UncheckedIOException("无法启动 shell: " + e.getMessage(), e);
		}

		// 读与等必须【并发】:输出在独立虚拟线程上排空,waitFor 的超时才真正生效。
		// (初版先读后等:read 阻塞到进程退出,超时形同虚设 —— bashTimeoutKillsProcess 测试抓住的真实 bug)
		CompletableFuture<String> output = new CompletableFuture<>();
		Thread.ofVirtual().name("bash-output").start(() -> {
			try {
				output.complete(readCapped(process.getInputStream(), MAX_OUTPUT_CHARS));
			} catch (Throwable t) {
				output.completeExceptionally(t);
			}
		});

		boolean finished;
		try {
			finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			process.destroyForcibly(); // 杀进程会关闭管道,读线程随 EOF 自然结束
			Thread.currentThread().interrupt();
			throw new IllegalStateException("命令被中断,已终止: " + command);
		}
		if (!finished) {
			process.destroyForcibly();
			throw new IllegalStateException("命令超时(" + timeoutMs + "ms),已终止: " + command);
		}

		String text = output.join(); // 进程已退出,输出很快 EOF;读失败会以 CompletionException 冒出
		int exitCode = process.exitValue();
		if (exitCode != 0) {
			// 失败但输出可见:输出随异常消息交给循环,最终落在 isError 结果的 content 里
			throw new IllegalStateException("命令失败(exit " + exitCode + "):\n" + text);
		}

		return new AgentToolResult(
				text.isEmpty() ? List.of() : List.of(new Content.TextContent(text)),
				Map.of("command", command, "exitCode", exitCode, "chars", text.length()),
				Optional.empty());
	}

	/** 读到 EOF,但最多保留 maxChars(超出部分丢弃并标注)—— 护上下文窗口(pi: truncate 的精神)。 */
	private static String readCapped(InputStream in, int maxChars) throws IOException {
		StringBuilder text = new StringBuilder();
		byte[] buffer = new byte[8192];
		int read;
		int kept = 0;
		boolean capped = false;
		while ((read = in.read(buffer)) > 0) {
			if (kept >= maxChars) {
				capped = true;
				continue; // 继续读到 EOF,只是不再保留 —— 进程才不会因管道满而阻塞
			}
			String chunk = new String(buffer, 0, read, StandardCharsets.UTF_8);
			kept += chunk.length();
			text.append(chunk);
		}
		if (capped) {
			text.append("\n…(输出超长,已截断至 ").append(maxChars).append(" 字符)");
		}
		return text.toString();
	}
}
