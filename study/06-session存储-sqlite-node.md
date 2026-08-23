# 06. session 存储(sqlite-node / jsonl)

> 阶段:主线 | 前置:[05 AgentHarness](./05-AgentHarness-持久化.md)

会话怎么落盘、怎么恢复。这一节把 [05] 的抽象模型落到具体存储后端。**注意 sqlite-node 的表结构几乎一一对应 harness 的四元结构 + 单写者**,这是理解持久化的最短路径。

## 学习目标

- 看懂 sqlite-node 后端的表/模块如何映射 harness 模型
- 理解 JSONL 与 SQLite 两种后端的角色
- 理解持久化、恢复、迁移、writer-lease(单写者租约)

## 关键文件

- `packages/session-backends/sqlite-node/src/sqlite/`:
  - `index.ts`、`types.ts`、`repo.ts`(仓储入口)、`sql.ts`(SQL 胶水)
  - **`storage/` —— 每个文件对应一种持久对象,直接映射 harness 模型**:
    - `entries.ts` → **tree**(对话条目)
    - `lanes.ts` → **lanes**(lane 名 + 叶子)
    - `records.ts` → **lane records**(操作记录序列)
    - `facts.ts` → **global facts**(会话名/标签/自定义)
    - `sessions.ts`、`session-sequences.ts` → 会话与单调序列号
    - `writer-leases.ts` → **单写者租约**(强制一次只有一个写者)
    - `branch-entries.ts` / `branch-tips.ts` / `branch-cache.ts` → 分支(tree 的 parentId 结构 / 叶子缓存)
    - `session-stats.ts` → 统计
  - `migrations.ts` + `migrations/001_initial.sql` → schema 迁移
  - `search-backend.ts`、`branch-cache.ts`
- `packages/agent/src/harness/session/`:
  - `jsonl.ts` + `jsonl/` —— JSONL 编码后端
  - `memory.ts` —— 内存后端(不落盘,测试用)
  - `search.ts` —— 会话内搜索
- `packages/coding-agent/src/migrations.ts` —— coding-agent 自身的 v3 JSONL 迁移/兼容

## 核心概念

- **后端抽象**:harness 通过统一接口读写会话,具体后端可换(memory / jsonl / sqlite)。sqlite-node 是生产级落盘后端。
- **表 ↔ 模型映射**:`storage/` 下每个模块就是 harness 四元结构的一个面。看到 `records.ts` 就想到「lane records」,看到 `writer-leases.ts` 就想到「单写者」。
- **单调序列号**:所有写共享一个单调递增 seq(`session-sequences.ts`),用来排序 global-fact 历史、让 lane record 能引用 tree 位置。
- **writer-lease**:单写者靠租约实现——拿到 lease 才能写,租约有期限/持有者,防止两进程同写。
- **迁移**:schema 变更走 `migrations/`(目前 `001_initial.sql`)。注意兼容策略:只有 v3 JSONL 要向后兼容。
- **恢复**:启动时读 records → 跑 reducer(见 05)→ 重建 live state → 从上一个持久边界恢复。

## 建议阅读顺序

1. 先扫 `storage/` 目录,把每个文件名对应到 harness 的四元结构(建立映射表)。
2. 读 `001_initial.sql`:看真实表结构,验证你的映射。
3. 读 `repo.ts` / `index.ts`:后端如何对外暴露。
4. 读 `writer-leases.ts`:单写者怎么强制。
5. 读 `records.ts` + 回到 [05] 的 `reducer.ts`:记录如何变回 live state。
6. 读 `harness/session/jsonl.ts`:对比 JSONL 后端与 SQLite 后端的取舍。
7. 读 `coding-agent/src/migrations.ts`:v3 兼容这条特殊路径。

## 检查点

- [ ] `storage/` 的每个文件分别对应 harness 的哪个概念?
- [ ] 单写者是用什么机制保证的?租约过期会怎样?
- [ ] 单调序列号为什么需要?谁在用它?
- [ ] 内存 / JSONL / SQLite 三个后端各自适合什么场景?
- [ ] 为什么只有 v3 JSONL 需要向后兼容?

---

## 笔记

### 表 ↔ 模型映射表
（边读 storage/ 边填:文件 → harness 概念 → 关键列）

### 恢复流程
（records → reducer → live state）

### 疑问与待查
（待补充）
