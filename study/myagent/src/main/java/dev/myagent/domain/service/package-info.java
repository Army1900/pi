/**
 * 领域服务 —— 阶段 1 实现区,是重推导功课的核心;此处只立契约,类由你写出。
 *
 * <p>契约(实现时逐条对照 pi):
 * <ul>
 *   <li><b>Result</b>(已落地):errors-as-data 词汇根 —— 循环校验步骤(参数校验等)的
 *       可失败表达;移动触发器:session 恢复(阶段 3)开始消费之日,上移共享词汇层。</li>
 *   <li><b>AgentEvent</b>(已落地):执行的观察事件(直播)—— 定义与发射都在本包的循环,
 *       分发编排在 application;与 session 的 Record(账本,阶段 3)相对。
 *       归属判据:定义跟着<b>构造者</b>走(读者不算数)—— 当前唯一构造者是 AgentLoop。
 *       移动触发器:阶段 2 若复刻 pi 的 handleRunFailure(agent.ts:511,外壳合成失败事件),
 *       application 成为第二构造者 → 迁往共享词汇位。</li>
 *   <li><b>AgentLoop</b>:双层 while(内层=工具调用+steering,外层=followUp)。
 *       拥有的不变量:停止条件;errors-as-data 转换(工具异常 → isError 结果,转换在循环边界);
 *       截断全败(stopReason=LENGTH 时整批工具调用不执行,pi agent-loop.ts:381)。
 *       对应 pi 的 runLoop(agent-loop.ts:155)+ streamAssistantResponse(:281)。</li>
 *   <li><b>ToolExecutor</b>:三段式 prepare(校验+beforeToolCall)/ execute / finalize(afterToolCall),
 *       pi agent-loop.ts:600 / :670 / :713。</li>
 *   <li><b>Reducer</b>(阶段 3):journal 记录 → 状态的纯折叠。对应 harness 的 reducer.ts。</li>
 * </ul>
 */
package dev.myagent.domain.service;
