package dev.myagent.infrastructure.tools;

import java.util.Map;

/**
 * 手写参数校验/取值 —— typebox 缺位的替代(PLAN diff 点 #2 的"痛"落点:
 * pi 一份 schema 同时产出 JSON Schema 与 TS 类型,Java 只有 Map,校验只能自己写)。
 *
 * <p>失败契约:校验失败以异常表达,由循环转成 isError 的 ToolResultMessage
 * (与 AgentTool 端口的 Javadoc 同款:工具允许抛,转换在循环边界)。
 */
final class ToolArgs {

	private ToolArgs() {
	}

	static String requireString(Map<String, Object> arguments, String name) {
		Object value = arguments.get(name);
		if (!(value instanceof String string) || string.isBlank()) {
			throw new IllegalArgumentException("缺少必填参数 " + name + "(string)");
		}
		return string;
	}

	static String optionalString(Map<String, Object> arguments, String name, String defaultValue) {
		Object value = arguments.get(name);
		if (value == null) {
			return defaultValue;
		}
		if (!(value instanceof String string)) {
			throw new IllegalArgumentException("参数 " + name + " 应为 string");
		}
		return string;
	}

	static int optionalPositiveInt(Map<String, Object> arguments, String name, int defaultValue) {
		Object value = arguments.get(name);
		if (value == null) {
			return defaultValue;
		}
		if (value instanceof Number number && number.intValue() > 0) {
			return number.intValue();
		}
		throw new IllegalArgumentException("参数 " + name + " 应为正整数");
	}
}
