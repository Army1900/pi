package dev.myagent.domain.model.shared;

/**
 * 领域事件(账本事件)的公共标记:实现者是被持久化、可重放的事实
 * (session 的 Record 家族,阶段 3)。
 *
 * <p>刻意与 run 聚合的 AgentEvent(观察事件,直播)<b>无继承关系</b> ——
 * 直播不是账本:一个不落盘、可丢、不影响执行,一个是事件溯源素材。
 * 类型上隔离,不靠注释约定。
 */
public interface DomainEvent {}
