/**
 * 本地能力适配器:实现 domain 的 AgentTool 端口 —— 对面是<b>操作系统</b>
 * (文件系统、进程子系统),与 {@code llm/} 的网络适配器同性质:
 * 端口适配器住 infra,无论对面是网络还是操作系统。
 *
 * <p>与"清单"的分工(判据修正记录:实现 ≠ 清单):实现住这;
 * 选哪些工具、怎么装配是 application / 组合根的事 —— application 持
 * {@code List<AgentTool>}(domain 类型)由组合根注入,不 import 本包。
 *
 * <p>关注点分离的预演:执行(读什么、返回什么)与"呈现/安全"(截断、输出上限)
 * 拆成独立步骤 —— pi 在 core/tools 里用 truncate.ts / output-accumulator.ts
 * 单独承载后者。
 */
package dev.myagent.infrastructure.tools;
