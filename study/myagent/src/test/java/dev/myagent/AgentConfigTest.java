package dev.myagent;

import dev.myagent.infrastructure.config.AgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 配置加载:env: 引用解析、官方缺省端点、必填校验 —— 格式与端点正交。 */
class AgentConfigTest {

	@TempDir
	Path dir;

	@Test
	void resolvesEnvReferenceAndAppliesOfficialDefault() throws IOException {
		Path file = write("""
				api=openai
				model=gpt-4o-mini
				apiKey=env:TEST_KEY
				""");

		AgentConfig config = AgentConfig.load(file, name -> "TEST_KEY".equals(name) ? "sk-test" : null);

		assertEquals("sk-test", config.apiKey(), "env: 引用被解析");
		assertEquals("https://api.openai.com/v1", config.baseUrl(), "未填 baseUrl 用官方缺省");
		assertEquals(4096, config.maxTokens());
	}

	@Test
	void formatIsOrthogonalToEndpoint() throws IOException {
		// 核心场景:OpenAI 兼容格式 + 任意自建网关(DeepSeek 等)
		Path file = write("""
				api=openai
				baseUrl=https://api.deepseek.com
				model=deepseek-chat
				apiKey=env:K
				""");

		AgentConfig config = AgentConfig.load(file, name -> "K".equals(name) ? "x" : null);

		assertEquals("openai", config.api(), "格式归格式");
		assertEquals("https://api.deepseek.com", config.baseUrl(), "端点归端点");
		assertEquals("deepseek-chat", config.model());
	}

	@Test
	void anthropicFormatWithExplicitBaseUrl() throws IOException {
		Path file = write("""
				api=anthropic
				model=claude-sonnet-4-5
				apiKey=env:K
				baseUrl=https://my-gateway.example.com
				""");

		AgentConfig config = AgentConfig.load(file, name -> "K".equals(name) ? "x" : null);

		assertEquals("anthropic", config.api());
		assertEquals("https://my-gateway.example.com", config.baseUrl());
	}

	@Test
	void missingEnvVariableIsStartupError() throws IOException {
		Path file = write("""
				api=openai
				model=m
				apiKey=env:NO_SUCH_KEY
				""");

		assertThrows(IllegalStateException.class,
				() -> AgentConfig.load(file, name -> null),
				"未设置的环境变量是启动错误,不是静默空值");
	}

	@Test
	void unknownFormatAndMissingRequiredAreRejected() throws IOException {
		assertThrows(IOException.class, () -> AgentConfig.load(write("""
				api=gemini
				model=m
				apiKey=k
				"""), name -> null), "未知格式拒绝启动");
		assertThrows(IOException.class, () -> AgentConfig.load(write("""
				api=openai
				"""), name -> null), "缺 model/apiKey 拒绝启动");
	}

	private Path write(String content) throws IOException {
		Path file = dir.resolve("myagent.properties");
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}
}
