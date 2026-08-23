package dev.myagent.domain.model.message;

/**
 * 对话消息的可辨识联合,对应 pi 的 Message(packages/ai/src/types.ts:455)。
 * TS 一个 | 完成;Java 用 sealed interface + record 拼出等价物 —— 第一个表达力 diff。
 */
public sealed interface Message permits UserMessage, AssistantMessage, ToolResultMessage {}
