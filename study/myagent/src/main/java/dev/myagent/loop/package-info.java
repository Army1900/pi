/**
 * 阶段 1-2:agent 循环与控制面 —— runLoop(双层 while)、steering/followUp 队列、abort、
 * before/afterToolCall 拦截、有状态的 Agent 外壳。
 *
 * <p>对应 pi 的 {@code agent-loop.ts} 与 {@code agent.ts}。循环尽量无状态(快照进、事件出),
 * 状态由 Agent 归约事件维护 —— 事件是观察口,不是控制口。
 */
package dev.myagent.loop;
