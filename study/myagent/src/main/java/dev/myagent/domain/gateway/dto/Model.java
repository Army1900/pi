package dev.myagent.domain.gateway.dto;

/** 模型元数据(形状对照 pi 测试里的 createModel:api / provider / 上下文窗口 / 最大输出)。 */
public record Model(String id, String name, String api, String provider, long contextWindow, long maxTokens) {}
