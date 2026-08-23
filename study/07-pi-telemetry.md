# 07. pi-telemetry(厂商无关遥测契约)

> 阶段:支线(独立叶子) | 前置:无

`packages/telemetry` —— 厂商无关的遥测(telemetry)契约、参考适配器、一致性测试、typed schema。与可观测性相关,但不参与 agent 执行逻辑,可独立学。

## 学习目标

- 理解「契约 + 适配器 + 一致性测试」这种设计模式
- 看懂 typed schema 如何描述遥测事件
- 知道 harness / coding-agent 如何接入遥测

## 关键文件

- `src/index.ts` —— 导出契约与类型
- `src/memory.ts` —— 内存适配器(参考实现 + 测试用)
- `src/noop.ts` —— 空实现(默认不采集时用)
- `src/testing/` —— 一致性测试(任何适配器都要过)
- `packages/agent/docs/telemetry-schema.md` —— **遥测 schema 文档**(重要,跨包共享)

## 核心概念

- **契约优先**:只定义「有哪些遥测事件、字段长什么样」,不绑死后端(可接 OTel、自研、文件…)。
- **适配器**:具体后端实现契约。`memory`/`noop` 是参考实现。
- **一致性测试**:保证每个适配器行为一致(`testing/`)。
- **typed schema**:字段有强类型,减少手写错误。

## 建议阅读顺序

1. `src/index.ts`:看契约全貌(有哪些事件/接口)。
2. `docs/telemetry-schema.md`(在 agent 包下):读 schema 定义。
3. `src/memory.ts`:看一个适配器怎么实现契约。
4. `src/testing/`:看一致性测试如何约束适配器。
5. 回到 [05] 的 `harness/telemetry.ts`,看 harness 如何打点。

## 检查点

- [ ] 为什么遥测要做成「契约 + 可换适配器」?
- [ ] 一致性测试解决了什么问题?
- [ ] 默认不采集时用哪个适配器?

---

## 笔记

### 概念与要点
（待补充）

### 代码走读
（待补充）

### 疑问与待查
（待补充）
