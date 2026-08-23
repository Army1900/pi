/**
 * 消息词汇 —— 非聚合,域的通用语言(lingua franca):被所有聚合与端口引用。
 *
 * <p>归属判定:这些类型离开任何聚合都完全成立 —— run 造它、gateway 吃它、event 运它;
 * 类比 DDD 的 Money / Address(跨聚合共享 VO),而非 OrderLine(离不开 Order 的内部成员)。
 * 不变量自包含(如 ToolResult 凭 toolCallId 配对),不需要任何根来执行。
 *
 * <p>阶段 3 的树结构(entry:身份、parentId、分支)才是 Session 聚合的内部模型 ——
 * 载荷与树分层,对应 pi harness 里 tree entry 包装 message 的设计。
 */
package dev.myagent.domain.model.message;
