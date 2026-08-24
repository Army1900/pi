package dev.myagent.infrastructure.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 * 运行配置(properties 文件 → 值对象)。两个关键设计:
 *
 * <p><b>线格式与端点正交</b>:api 选择的是【接口格式】(openai = chat-completions 兼容;
 * anthropic = Messages API),不是品牌 —— 任何兼容网关(DeepSeek、通义、Kimi、vLLM、
 * one-api …)都填 api=openai + 自己的 baseUrl。官方域名仅是 baseUrl 未填时的便利缺省。
 *
 * <p><b>密钥卫生</b>:值支持 {@code env:NAME} 引用 —— 密钥留在环境变量,配置文件本身
 * 无秘密,可提交可分享;裸 key 写进文件迟早推上 GitHub。
 *
 * <p>归属:文件 IO → infrastructure;消费它的是组合根(Main)。
 */
public record AgentConfig(String api, String model, String baseUrl, String apiKey, int maxTokens) {

	/** 各格式的官方端点 —— 仅当 baseUrl 未填时使用;自定义网关直填 baseUrl 即可。 */
	private static final Map<String, String> OFFICIAL_BASE_URLS = Map.of(
			"openai", "https://api.openai.com/v1",
			"anthropic", "https://api.anthropic.com");

	public static AgentConfig load(Path path) throws IOException {
		return load(path, System::getenv);
	}

	/** env 参数可注入 —— 测试无需操纵进程环境变量。 */
	public static AgentConfig load(Path path, Function<String, String> env) throws IOException {
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			props.load(reader);
		}

		String api = required(props, "api", env).trim();
		if (!OFFICIAL_BASE_URLS.containsKey(api)) {
			throw new IOException("未知 api 格式: " + api + "(支持: " + OFFICIAL_BASE_URLS.keySet() + ")");
		}
		String model = required(props, "model", env).trim();
		String baseUrl = props.containsKey("baseUrl")
				? resolve(props.getProperty("baseUrl"), env).trim()
				: OFFICIAL_BASE_URLS.get(api);
		String apiKey = required(props, "apiKey", env);
		int maxTokens = props.containsKey("maxTokens")
				? Integer.parseInt(resolve(props.getProperty("maxTokens"), env).trim())
				: 4096;
		return new AgentConfig(api, model, baseUrl, apiKey, maxTokens);
	}

	/** env:NAME 引用解析;未设置的环境变量是启动错误,不是静默空值。 */
	private static String resolve(String value, Function<String, String> env) {
		if (value != null && value.startsWith("env:")) {
			String name = value.substring(4).trim();
			String resolved = env.apply(name);
			if (resolved == null || resolved.isBlank()) {
				throw new IllegalStateException("配置引用的环境变量未设置: " + name);
			}
			return resolved;
		}
		return value;
	}

	private static String required(Properties props, String key, Function<String, String> env) throws IOException {
		String value = props.getProperty(key);
		if (value == null || value.isBlank()) {
			throw new IOException("配置缺少必填项: " + key);
		}
		return resolve(value, env);
	}
}
