# 05. AgentHarness(持久化、显式状态、lane)

> 阶段:主线(本项目的心脏 + 当前开发重点) | 前置:[04 agent-core 基础](./04-pi-agent-core-基础.md)

`packages/agent/src/harness/` —— **持久化、显式状态的 agent harness**。设计文档是理解它的关键,务必先读 docs 再读代码。这是最近一批提交的核心(见 git log 的 `docs(agent): ... harness design`)。

## 学习目标

- 理解「会话 = tree + lanes + lane records + global facts」四元结构
- 理解「持久化运行 / 无部分结果 / 单写者」
- 区分 **events(观察,不能改执行)** 与 **hooks(拦截,能改执行)**
- 理解 `drive: "manual"` 如何让测试逐边界驱动 + 模拟崩溃
- 理解兼容策略(仅 v3 JSONL 需向后兼容)

## ⚠️ 先读设计文档(必读)

- `packages/agent/docs/harness-v2.md` —— 主设计文档(很大,分 Part I/II/III)。先读 Part I「Concepts」(目标、会话是什么、lanes、events vs hooks)。
- `packages/agent/docs/harness-v2-state-machine.md` —— 状态机与操作记录的权威细节。
- `packages/agent/docs/harness-v2-test-matrix.md` —— 测试矩阵,帮助理解各种边界(崩溃/恢复/中止)。

## 关键文件

- `harness/agent-harness.ts` —— **harness 入口**
- `harness/types.ts` —— 类型:`Result<T,E>` + `ok/err/getOrThrow`(fallible 操作惯例)、`Skill`、`PromptTemplate`、`AgentHarnessResources`、`AgentTool` 定义
- `harness/session/` —— 会话四元结构的实现:
  - `session.ts`、`state.ts`、`context.ts`、`types.ts`、`index.ts`
  - `jsonl/` + `jsonl.ts` —— JSONL 编码(持久化格式之一)
  - `memory.ts` —— 内存后端
  - `search.ts` —— 会话内搜索
  - `testing/` —— 测试辅助
- `harness/reducer.ts` —— 状态归约(记录 → live state 的核心,恢复时也走它)
- `harness/events.ts` —— 事件定义(observe)
- `harness/messages.ts` —— 消息/记录类型
- `harness/system-prompt.ts` + `prompt-templates.ts` —— 系统提示与 prompt 模板
- `harness/skills.ts` —— skill 加载与系统提示注入
- `harness/compaction/`(`compaction.ts` / `branch-summarization.ts` / `utils.ts`)—— 上下文压缩 / 分支摘要
- `harness/tools/`(`read`/`write`/`edit`/`bash`/`image`/`edit-diff`/`file-mutation-queue`/`tool-context`/`path-utils`)—— 内置工具实现
- `harness/env/` —— 注入边界(drive 模式、effect 边界)
- `harness/telemetry.ts`、`harness/result.ts`、`harness/utils/`

## 核心概念(对应设计文档)

- **四个持久部分**:
  1. **tree** —— 对话树,`parentId` 链接的消息/摘要/自定义条目;共享、被动、只增不改不删。
  2. **lanes** —— 工作发生的位置。一个 lane = 名字 + 叶子(未来要扩展的条目)。必有 `main`,可按外部身份建更多(Slack 线程、邮件线程…)。lane 间并行。
  3. **lane records** —— lane 的「当前完整配置 + 发生了什么 + 接下来要发生什么」。每 lane 一条扁平、按时间的记录序列(`lane_config` 替换 + 操作记录:started/step/attempt/tool batch/queued/finished)。崩溃后靠它重建。
  4. **global facts** —— 会话级「最后写赢」的值:会话名、条目标签、应用自定义 fact。与 tree 分离,append-only 历史。
- **持久化运行**:接受的 prompt 是一个**持久操作**;崩溃后新进程从记录重建,从上一个持久边界恢复。任何崩溃状态都可恢复,没有「半完成」可观测态。
- **单写者**:一个会话同时只有一个 harness 写;服务层强制。多进程/复制 out of scope(用 lanes 覆盖「看起来像多写者」的场景)。
- **events 观察 vs hooks 拦截**:events 只能观察执行、不能改;hooks 能改(context / requests / tools / 运行边界)。扩展建立在两者之上。
- **`drive: "manual"`**:每个 effect(持久写、provider 请求、工具执行、hook、定时器)跨一个注入边界。manual 模式下 harness 在每个 effect 前停下,测试逐调用驱动、可随时注入输入、可关闭重开模拟崩溃。**生产与测试跑同一套过程,drive 模式只控制边界。**
- **兼容策略**:仅 coding-agent **v3** JSONL 会话需向后兼容(能打开并恢复到 idle)。`harness/` 与 `session-backends/sqlite-node` 的其他格式/API/测试可自由 break,无需迁移。

## 建议阅读顺序

1. **先读 docs**:`harness-v2.md` 的 Part I(Goals / Non-goals / What a session is / Active vs passive / Invariants)。
2. 读 `harness/types.ts`:`Result` 体系 + `Skill`/`PromptTemplate`/`AgentTool`。
3. 读 `harness/session/state.ts` + `reducer.ts`:理解「记录 → live state」的归约(恢复的核心)。
4. 读 `harness/session/session.ts`:四元结构如何组装。
5. 读 `harness/agent-harness.ts`:入口如何跑一次 run。
6. 读 `harness/events.ts`,对比 hooks(在文档 Part I 找 hooks 一节)。
7. 读 `harness/env/`:理解 drive 边界注入。
8. 配合 [06 存储](./06-session存储-sqlite-node.md) 看记录怎么落盘。

## 检查点

- [ ] 会话的四个持久部分分别是什么?哪些被动、哪些主动?
- [ ] 为什么说「崩溃后没有可观测的半完成状态」?靠什么保证?
- [ ] event 和 hook 的根本区别是什么?
- [ ] `drive: "manual"` 怎么让测试能模拟崩溃,又保证生产/测试同流程?
- [ ] 单写者约束为什么需要? lanes 如何替代「看起来要多写者」的需求?
- [ ] v3 JSONL 的兼容要求是什么?其他格式为什么可以随便 break?

---

## 笔记

### 概念与要点
（待补充）

### 四元结构记录
（tree / lanes / lane records / global facts —— 边读边填）

### events vs hooks
（待补充）

### 状态机与恢复流程
（待补充）

### 疑问与待查
（待补充）
