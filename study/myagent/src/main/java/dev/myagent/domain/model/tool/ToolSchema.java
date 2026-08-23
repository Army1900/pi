package dev.myagent.domain.model.tool;

import java.util.List;
import java.util.Map;

/**
 * 工具参数 schema(JSON-Schema 子集):properties + required。
 * pi 用 typebox 一份定义同时产出 JSON Schema 与 TS 类型;Java 无此一鱼两吃,
 * 阶段 1 由手写校验器消费此类型(PLAN diff 点 #2:痛了再换库)。
 */
public record ToolSchema(Map<String, Object> properties, List<String> required) {
	public ToolSchema {
		properties = Map.copyOf(properties);
		required = List.copyOf(required);
	}
}
