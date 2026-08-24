package dev.myagent;

import dev.myagent.domain.gateway.StreamFn;
import dev.myagent.domain.gateway.dto.Model;
import dev.myagent.domain.model.message.AssistantMessage;
import dev.myagent.domain.model.message.Content;
import dev.myagent.domain.model.message.StopReason;
import dev.myagent.domain.model.message.UserMessage;
import dev.myagent.domain.model.session.Session;
import dev.myagent.domain.model.session.SessionId;
import dev.myagent.domain.service.AgentEvent;
import dev.myagent.domain.service.AgentLoop;
import dev.myagent.infrastructure.config.AgentConfig;
import dev.myagent.infrastructure.llm.AnthropicStreamFn;
import dev.myagent.infrastructure.llm.OpenAiStreamFn;
import dev.myagent.infrastructure.tools.BashTool;
import dev.myagent.infrastructure.tools.LsTool;
import dev.myagent.infrastructure.tools.ReadTool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * 组合根(Composition Root)第一次亮相 —— 整个项目唯一一处允许"什么都知道"的地方:
 * 读配置 → 选适配器 → 装配工具与循环 → 起一个迷你 REPL。
 * 这几天的结构在这里第一次"活"起来:domain 不认识任何适配器,
 * application 一个都没写,全部依赖边在这一间房里手工焊死(不引 Spring,肉眼可见)。
 *
 * <p>运行:cp myagent.properties.example myagent.properties → 填好 provider/model、
 * export 对应的 *_API_KEY → mvn -q compile exec:java
 */
public final class Main {

	public static void main(String[] args) throws Exception {
		Path configPath = Path.of(args.length > 0 ? args[0] : "myagent.properties");
		AgentConfig config = AgentConfig.load(configPath);

		Model model = new Model(config.model(), config.model(), config.api(), config.api(),
				200_000, config.maxTokens());
		StreamFn llm = switch (config.api()) {
			case "openai" -> new OpenAiStreamFn(config.baseUrl(), config.apiKey());
			case "anthropic" -> new AnthropicStreamFn(config.baseUrl(), config.apiKey());
			default -> throw new IllegalStateException("未知 api 格式: " + config.api());
		};

		Session session = new Session(new SessionId("main"), "你是一个运行在终端里的助手,善于使用提供的工具完成任务。");
		AgentLoop loop = new AgentLoop(model, llm, List.of(new ReadTool(), new LsTool(), new BashTool()));

		System.out.println("myagent 已启动(" + config.api() + " 格式 @ " + config.baseUrl() + " / " + config.model() + "),输入 exit 退出。");
		try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
			while (true) {
				System.out.print("\nmyagent> ");
				System.out.flush();
				String line = in.readLine();
				if (line == null || line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
					break;
				}
				if (line.isBlank()) {
					continue;
				}
				loop.run(session,
						List.of(new UserMessage(List.of(new Content.TextContent(line)), Instant.now())),
						Main::onEvent);
			}
		}
	}

	/** 直播消费:只看工具执行与 assistant 消息,别的静默。 */
	private static void onEvent(AgentEvent event) {
		switch (event) {
			case AgentEvent.ToolExecutionStart e ->
					System.out.println("  [工具] " + e.toolName() + " " + e.arguments());
			case AgentEvent.ToolExecutionEnd e ->
					System.out.println(e.isError() ? "  [工具失败] " + preview(textOf(e)) : "  [工具结果] " + preview(textOf(e)));
			case AgentEvent.MessageEnd e -> {
				if (e.message() instanceof AssistantMessage assistant) {
					if (assistant.stopReason() == StopReason.ERROR || assistant.stopReason() == StopReason.ABORTED) {
						System.out.println("  [模型错误] " + assistant.errorMessage().orElse("(无详情)"));
					} else {
						String text = assistant.content().stream()
								.filter(c -> c instanceof Content.TextContent)
								.map(c -> ((Content.TextContent) c).text())
								.reduce("", (a, b) -> a + b);
						if (!text.isBlank()) {
							System.out.println("  " + text);
						}
					}
				}
			}
			default -> {
			}
		}
	}

	private static String textOf(AgentEvent.ToolExecutionEnd e) {
		return e.result().content().stream()
				.map(c -> c.text())
				.reduce("", (a, b) -> a + b);
	}

	private static String preview(String text) {
		String flat = text.replace("\n", " ⏎ ");
		return flat.length() > 200 ? flat.substring(0, 200) + "…" : flat;
	}
}
