# 08. pi-client / pi-server(远程会话)

> 阶段:支线(可选,做远程/多端时再深入) | 前置:[02 protocol](./02-pi-protocol.md)、[05 harness](./05-AgentHarness-持久化.md)

远程会话:把一个跑在服务端的 harness 暴露给客户端,客户端拿「一次原子快照 + 之后的事件流」。`pi-server` 持有 harness 并强制单写者,`pi-client` 通过 CBOR 协议与之通信。

## 学习目标

- 理解「快照 + 事件流」的 UI/同步模型
- 理解服务端如何强制单写者
- 看懂 transport 抽象(stdio / Unix socket / …)

## 关键文件 — client

- `packages/client/src/`
  - `client.ts`、`index.ts` —— 客户端入口
  - `connection.ts`、`transport.ts` —— 连接与传输抽象
  - `session-handle.ts` —— 对一个远程会话的句柄
  - `state.ts` —— 客户端本地状态(快照投影)
  - `unix.ts` —— Unix socket transport
  - `promise.ts`、`errors.ts`、`types.ts`

## 关键文件 — server

- `packages/server/src/`
  - `server.ts`、`index.ts` —— 服务端入口
  - `sessions.ts` —— 会话管理(单写者路由:一个会话的所有流量发给持有它的进程)
  - `snapshots.ts` —— 快照生成
  - `listener.ts`、`connection.ts`、`protocol.ts` —— 监听 / 连接 / 协议处理
  - `transports/` —— 多种 transport 实现
  - `testing/`、`errors.ts`、`types.ts`
- `packages/coding-agent/src/client/remote-session.ts` —— coding-agent 作为客户端连远程会话
- `packages/coding-agent/src/server/create-harness.ts` —— 在服务端创建 harness

## 核心概念

- **快照 + 事件流**:客户端连上先拿一份**原子快照**(完整当前状态),之后只收**增量事件**。事件不重放;重连 = 拿新快照。
- **单写者路由**:服务层把某会话的所有流量都路由到持有该会话 harness 的进程;满足 harness 的单写者约束。
- **session handle**:客户端对一个远程会话的抽象引用,用来发 prompt / steer / abort。
- **transport 无关**:复用 [02] 的协议层,transport 可换。

## 建议阅读顺序

1. 回顾 [02] 协议层(codec / framing / schemas)。
2. `server/src/server.ts` → `sessions.ts`:理解会话管理与单写者路由。
3. `server/src/snapshots.ts`:快照怎么生成。
4. `client/src/client.ts` → `session-handle.ts` → `state.ts`:客户端如何持有与投影状态。
5. `coding-agent/src/server/create-harness.ts` + `client/remote-session.ts`:coding-agent 如何串起两端。

## 检查点

- [ ] 为什么用「快照 + 事件流」而不是让客户端重放全部历史事件?
- [ ] 单写者约束在服务端如何被强制?
- [ ] 重连时会发生什么?

---

## 笔记

### 概念与要点
（待补充）

### 代码走读
（待补充）

### 疑问与待查
（待补充）
