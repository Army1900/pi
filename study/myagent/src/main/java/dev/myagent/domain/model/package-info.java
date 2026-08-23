/**
 * 领域模型:一个聚合 + 词汇包 + 公共抽象。
 *
 * <ul>
 *   <li>{@link dev.myagent.domain.model.session session} —— <b>聚合根 Session(会话)</b>:
 *       消息树。当前唯一的聚合。</li>
 *   <li>{@link dev.myagent.domain.model.message message} —— 共享词汇(非聚合):
 *       消息形状,被所有层引用(Money / Address 一类,不是 OrderLine)。</li>
 *   <li>{@link dev.myagent.domain.model.tool tool} —— 工具词汇(共享,非聚合):
 *       ToolDescriptor / ToolSchema / AgentToolResult —— 领域名词恰好过边界,与 message 同等待遇。</li>
 *   <li>{@link dev.myagent.domain.model.shared shared} —— 公共抽象:AggregateRoot
 *       (范型主键 + 待发布事件)+ DomainEvent(账本事件标记)。</li>
 * </ul>
 *
 * <p>聚合根不预建空壳(Run 的教训:无状态无事件者不配聚合资格,已降级为过程;
 * run/ 包随之解散 —— 词汇回到今天真实持有它们的地方:边界语言归 gateway 端口签名,
 * AgentEvent 归 service 发射者;RunId 无持有者不预建,阶段 3 随记录回归)。
 * 对应 pi 的 types.ts / ai types.ts(平铺无分层 —— 这里的分包本身就是一个 diff)。
 */
package dev.myagent.domain.model;
