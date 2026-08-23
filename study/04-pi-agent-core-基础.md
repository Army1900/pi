# 04. pi-agent-core 基础(loop / types)

> 阶段:主线 | 前置:[03 pi-ai](./03-pi-ai.md)

`packages/agent` —— agent 运行时。本节先学**经典 agent 循环**(`agent.ts` / `agent-loop.ts` / `types.ts`),建立「轮次、工具调用、消息、事件」的基础心智模型。**下一节(05)再学建立在其上的持久化 AgentHarness。**

## 学习目标

- 理解 agent 的一次「轮次(turn)」怎么跑
- 吃透 `AgentContext` / `AgentState` / `AgentMessage` / `AgentTool` / `AgentEvent` 等核心类型
- 理解工具调用的生命周期(before / 执行 / after)
- 知道 stream 函数、queue 模式的作用

## 关键文件

- `src/types.ts` —— **核心类型集**(最重要):
  - `AgentContext`、`AgentState`、`AgentMessage`
  - `AgentTool`、`AgentToolResult`、`AgentToolUpdateCallback`
  - `BeforeToolCallContext/Result`、`AfterToolCallContext/Result`(工具调用前后拦截点)
  - `AgentEvent`、`AgentLoopConfig`、`AgentLoopTurnUpdate`
  - `ShouldStopAfterTurnContext`、`PrepareNextTurnContext`
  - `QueueMode`、`ToolExecutionMode`、`StreamFn`
- `src/agent.ts` —— `runAgentLoop` / `runAgentLoopContinue` 入口;默认的 message→LLM 转换、默认 model/usage
- `src/agent-loop.ts` —— 循环实现:发请求 → 解析工具调用 → 执行 → 把结果喂回 → 下一轮,直到停止条件
- `src/stream-fn.ts` —— `getDefaultStreamFn`:如何拿到流式 stream(harness 也会用)
- `src/node.ts` —— Node 环境下的入口/胶水
- `src/proxy.ts` —— proxy 相关(MCP/远程工具代理,按需)
- `src/index.ts` —— 导出

## 核心概念

- **turn 循环**:用户/队列消息 → 调 LLM → LLM 可能返回工具调用 → 执行工具 → 工具结果作为新消息 → 再调 LLM → …… → 满足停止条件(如模型不再请求工具)结束这一轮。
- **AgentMessage vs LLM Message**:`AgentMessage` 是 agent 内部的富消息(带工具结果、元数据等),喂给 LLM 前会转换成 provider 的 `Message`。
- **工具拦截点**:`beforeToolCall`(可改/拦截)、执行、`afterToolCall`(可加工结果)。这是「hook 能改变执行」的雏形。
- **停止判定**:`shouldStopAfterTurn` 决定这一轮结束后是否继续。
- **QueueMode**:用户在 agent 跑的过程中排队发新消息时的策略。

## 建议阅读顺序

1. `src/types.ts` **通读**(这是后面所有东西的词汇表)。逐个类型理解字段含义。
2. `src/agent-loop.ts`:跟着一轮 turn 走完整流程,对照类型看每步用到谁。
3. `src/agent.ts`:看入口如何组装 config、调用 loop、处理默认值。
4. `src/stream-fn.ts`:stream 怎么来的。
5. 试着在脑中跑一遍「用户提问 → 模型调用 read 工具 → 读取后回复」的完整消息序列。

## 检查点

- [ ] 一轮 turn 从开始到结束,消息序列长什么样?
- [ ] `beforeToolCall` 和 `afterToolCall` 各能做什么?哪个能「改变」执行?
- [ ] `AgentMessage` 为什么要先转换才能喂给 LLM?
- [ ] `QueueMode` 解决什么问题?

---

## 笔记

### 概念与要点
（待补充）

### 代码走读
（待补充）

### 疑问与待查
（待补充）
