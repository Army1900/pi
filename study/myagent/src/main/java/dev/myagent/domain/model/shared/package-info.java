/**
 * 公共抽象:{@link dev.myagent.domain.model.shared.AggregateRoot AggregateRoot}
 * (范型主键 + 待发布事件集合)与 {@link dev.myagent.domain.model.shared.DomainEvent DomainEvent}
 * (账本事件标记 —— 观察事件 AgentEvent 刻意不在其继承树内)。
 *
 * <p>准入状态(诚实记录):当前唯一消费者是 Session —— Run 降级为过程所致的<b>单消费者暂态</b>。
 * 保留理由:本抽象是用户委任的学习件(参照 Axon / Spring Modulith 的标准形状);
 * 阶段 3 复审 Run 聚合资格时一并复审此处的准入。
 */
package dev.myagent.domain.model.shared;
