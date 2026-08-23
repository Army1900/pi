package dev.myagent.domain.model.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 聚合根公共抽象:身份(范型主键)+ 待发布领域事件集合。
 * 对应 Axon AggregateRoot / Spring AbstractAggregateRoot 的去框架化最小内核。
 *
 * <p>事件语义:<b>append-only</b> —— 聚合内只增;唯一合法的"去除"是 {@link #clearEvents()}:
 * 事件被 journal / publisher 接手后清空(Spring 的 afterPublication 语义)。
 * 不提供单条删除 —— 那会把账本变成可编辑的。
 *
 * <p>线程语义:依赖聚合的单写者边界(session 聚合 package-info)—— 单写者之内
 * 不需要并发容器。阶段 3 若引入 WriteAhead 语义(append 后即清),在此调和。
 */
public abstract class AggregateRoot<ID> {

	private final ID id;
	private final List<DomainEvent> events = new ArrayList<>();

	protected AggregateRoot(ID id) {
		this.id = Objects.requireNonNull(id, "聚合根必须有身份");
	}

	public ID id() {
		return id;
	}

	/** 记录一条待发布领域事件(protected:事件只由聚合自己的行为方法触发,外部不许直接塞)。 */
	protected void registerEvent(DomainEvent event) {
		events.add(Objects.requireNonNull(event));
	}

	/** 当前待发布事件(不可变视图)。 */
	public List<DomainEvent> events() {
		return List.copyOf(events);
	}

	/** 事件被 journal / publisher 接手后清空 —— 唯一合法的去除。 */
	public void clearEvents() {
		events.clear();
	}
}
