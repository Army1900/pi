/**
 * 工具词汇(共享,非聚合)—— 主语【工具】,与 message/ 平行:
 * <ul>
 *   <li>{@link dev.myagent.domain.model.tool.ToolDescriptor ToolDescriptor} —— 工具是什么
 *       (name / description / schema):给模型看、进 Context 装配的领域名词;也是
 *       AgentTool 能力端口"说"的领域词汇(端口说领域语言,不说自己的方言)。</li>
 *   <li>{@link dev.myagent.domain.model.tool.ToolSchema ToolSchema} —— 参数形状(JSON-Schema 子集);
 *       循环执行前的参数校验以它为据(pi:validateToolArguments)。</li>
 *   <li>{@link dev.myagent.domain.model.tool.AgentToolResult AgentToolResult} —— 执行产出
 *       (content 给模型 / details 给 UI)。</li>
 * </ul>
 *
 * <p>归属判据:领域通用语言里的名词,恰好要过边界 —— 与 Message 同等待遇
 * (pi 佐证:AgentTool / AgentToolResult 均在 agent-core 的 types.ts,而 Model 在 pi-ai)。
 */
package dev.myagent.domain.model.tool;
