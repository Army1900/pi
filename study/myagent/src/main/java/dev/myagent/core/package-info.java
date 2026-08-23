/**
 * 阶段 1:核心类型 —— 消息(user/assistant/toolResult)、AgentTool、StreamFn 契约、AgentEvent。
 *
 * <p>对应 pi 的 {@code packages/agent/src/types.ts}。刻意保留的设计约束:errors-as-data ——
 * StreamFn 禁止抛异常,失败编码为 stopReason="error" 的消息;工具失败变成 isError 的 toolResult。
 */
package dev.myagent.core;
