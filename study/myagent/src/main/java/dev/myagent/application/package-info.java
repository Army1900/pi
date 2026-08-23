/**
 * 应用层:只依赖 domain。阶段 2 动工。
 *
 * <ul>
 *   <li><b>Agent</b>(pi agent.ts:173 的有状态外壳):prompt / steer / followUp 用例;
 *       context 快照进、事件归约出(pi 的 processEvents:544 —— 状态由事件归约维护)。</li>
 *   <li><b>事件分发</b>:AgentEvent 的订阅、顺序、异步扇出 —— "定义在领域、发布在应用"的发布侧。</li>
 *   <li><b>recovery</b>(阶段 3):崩溃恢复用例(读 journal → replay → 从最后边界继续)。</li>
 * </ul>
 */
package dev.myagent.application;
