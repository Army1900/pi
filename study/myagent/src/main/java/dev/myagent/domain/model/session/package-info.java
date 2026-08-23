/**
 * 聚合根:Session(会话)—— 消息树。
 *
 * <p>本聚合持有 Message 的【引用】,不拥有其词汇:消息形状
 * (dev.myagent.domain.model.message)是跨聚合共享词汇;Session 守的是
 * <b>树</b>的不变量 —— 只增不改不删、ToolResult 凭 toolCallId 与 ToolCall 配对、
 * <b>单写者 = 聚合边界</b>(pi single writer,阶段 3 由 WriterLease 强制)。
 *
 * <ul>
 *   <li><b>身份</b>:sessionId(阶段 3 显式)。</li>
 *   <li><b>内部模型</b>:会话级配置(systemPrompt,阶段 1 已落地 —— lane_config 的前身)、
 *       对话消息(平铺列表 —— Session.append/messages);
 *       树 entry(身份、parentId、分支)、lanes、global facts、Record 领域事件
 *       (账本 / 事件溯源素材,阶段 3 —— 与 run 的 AgentEvent 观察事件相对:
 *       一个落盘可重放,一个即发即弃)。</li>
 * </ul>
 *
 * <p>对应 pi harness 的 session 四部分(tree / lanes / lane records / global facts)。
 */
package dev.myagent.domain.model.session;
