/**
 * 能力端口 + 流机制(边界语言/DTO 在 {@link dev.myagent.domain.gateway.dto dto} 子包)。
 *
 * <p>端口:{@link dev.myagent.domain.gateway.StreamFn StreamFn}(LLM)、
 * {@link dev.myagent.domain.gateway.AgentTool AgentTool}(工具能力)—— 消费者是领域循环,
 * 适配器在 infrastructure / application / 测试实现。
 * 流机制:{@link dev.myagent.domain.gateway.AssistantMessageStream AssistantMessageStream}
 * (有行为:push/complete/迭代 —— 是机制不是数据,故不进 dto)。
 *
 * <p>防泄漏自检:端口方法不得出现 file / JSON / SQL / HTTP 词汇。
 * 失败契约按端口分工:StreamFn 失败 = 数据(禁止抛)→ 循环不死;AgentTool 允许抛 →
 * 由循环转 isError 结果;repository(阶段 3)允许抛 = 崩溃边界。
 */
package dev.myagent.domain.gateway;
