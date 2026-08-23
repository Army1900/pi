/**
 * 端口载荷:跨边界的东西里<b>不属于领域词汇</b>的那部分 ——
 * 端口方言与装配形状。领域名词过边界时留在 model(Message、ToolDescriptor 同等待遇),
 * 由本包的 Context 引用,不在 dto 安家。
 *
 * <ul>
 *   <li>装配形状:Context(每次调用的取景框,从 Session 派生)</li>
 *   <li>provider 目录数据:Model(pi 佐证:Model 住在 pi-ai 边界库,不在 agent-core)</li>
 *   <li>端口方言:StreamEvent(增量怎么流是 provider 侧的事;循环负责翻译成 AgentEvent)</li>
 * </ul>
 *
 * <p>"哑在边界,活在域内"的物理落点;警戒线:域内对象(如 Session)DTO 化才是 anemic。
 */
package dev.myagent.domain.gateway.dto;
