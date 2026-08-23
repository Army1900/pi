# 02. pi-protocol(传输无关 CBOR 协议)

> 阶段:支线(小而自包含,可推迟) | 前置:无

`packages/protocol` —— 远程会话的**字节协议**。传输无关(不绑死 stdio/socket/WebSocket),用 CBOR 编码 + 长度前缀封帧。`pi-client` / `pi-server` 建立在它之上。

## 学习目标

- 理解「传输无关协议」的意义
- 看懂 CBOR 编解码 + 帧封包
- 知道消息 schema 如何定义与校验

## 关键文件

- `src/index.ts` —— 导出入口
- `src/codec.ts` —— 消息编解码(对象 ↔ 字节)
- `src/framing.ts` —— 长度前缀封帧(从字节流切出一条条完整消息)
- `src/schemas.ts` —— 消息 schema 定义(配合 typebox)
- `src/cbor/` —— CBOR 实现细节

## 核心概念

- **传输无关**:协议只规定「字节里是什么」,不管这些字节走 stdio、Unix socket 还是网络。client/server 各自接不同的 transport,协议层共用。
- **framing(封帧)**:流式字节没有天然边界,用长度前缀把流切成一条条消息。
- **CBOR**:比 JSON 更紧凑的二进制结构化格式,支持二进制/Map 等。
- **schema + 校验**:用 typebox 之类的 schema 描述消息形状,编解码时校验。

## 建议阅读顺序

1. `src/schemas.ts`:先看「有哪些消息、长什么样」。
2. `src/codec.ts`:消息怎么变成字节、怎么还原。
3. `src/framing.ts`:字节流怎么切成消息。
4. `src/cbor/`:CBOR 细节(按需)。
5. 结合 [08 pi-client/pi-server](./08-pi-client-pi-server.md) 理解它在整体里的位置。

## 检查点

- [ ] 为什么协议层要和传输层解耦?
- [ ] framing 解决了什么问题?不用长度前缀会怎样?
- [ ] 为什么用 CBOR 而不是 JSON?

---

## 笔记

### 概念与要点
（待补充）

### 代码走读
（待补充）

### 疑问与待查
（待补充）
