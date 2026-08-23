# Pi 学习路线

本文件夹是学习 Pi 这个 monorepo 的脚手架。每个模块一个 md 文件,内含:学习目标、为什么现在学、关键文件入口、核心概念、检查点,以及**预留的笔记区**(等你边读边填)。

学习过程中整理出的对外文章放 [`blog/`](./blog/)。

## 怎么用

1. 按下面的顺序读,主线不要跳;支线可按需推迟。
2. 每个模块:先看「关键文件」里的入口文件,再按「建议阅读顺序」展开。遇到不懂的概念记到该模块的「笔记区」。
3. 用「检查点」自测——能答上来再进入下一模块。
4. 实操命令(项目根目录):
   - `./pi-test.sh` —— 从源码跑 `pi`,随时上手体验
   - `npm run check` —— 改完代码用;不会跑测试
   - `./test.sh` —— 跑所有非 e2e 测试(别直接跑 `vitest`,会触发付费的真实 provider 测试)

## 阶段总览

主线 = 理解 agent 本身的必经路径(依赖链的脊柱)。支线 = 独立库或可选能力,影响面小,可推迟。

| 顺序 | 模块 | 包路径 | 阶段 | 前置依赖 |
|---|---|---|---|---|
| 00 | [项目导览](./00-项目导览.md) | 全局 | 主线 | — |
| 01 | [pi-tui(终端 UI)](./01-pi-tui.md) | `packages/tui` | 支线 | 无(叶子) |
| 02 | [pi-protocol(CBOR 协议)](./02-pi-protocol.md) | `packages/protocol` | 支线 | 无(叶子) |
| 03 | [pi-ai(统一 LLM API)](./03-pi-ai.md) | `packages/ai` | **主线** | — |
| 04 | [pi-agent-core 基础(loop/types)](./04-pi-agent-core-基础.md) | `packages/agent` | **主线** | 03 |
| 05 | [AgentHarness(持久化,核心)](./05-AgentHarness-持久化.md) | `packages/agent/src/harness` + `docs/` | **主线(心脏)** | 04 |
| 06 | [session 存储(sqlite-node / jsonl)](./06-session存储-sqlite-node.md) | `packages/session-backends/sqlite-node` | **主线** | 05 |
| 07 | [pi-telemetry(遥测契约)](./07-pi-telemetry.md) | `packages/telemetry` | 支线 | 无 |
| 08 | [pi-client / pi-server(远程会话)](./08-pi-client-pi-server.md) | `packages/client`, `packages/server` | 支线 | 02 |
| 09 | [pi-coding-agent(`pi` CLI,大结局)](./09-pi-coding-agent.md) | `packages/coding-agent` | **主线** | 03/04/05/01 |
| 10 | [pi-evals 与贡献流程](./10-pi-evals-与贡献.md) | `packages/evals` + `scripts/` | 支线(进阶) | 09 |

## 主线脊柱(只想理解 agent 的话,走这条)

```
00 导览 → 03 pi-ai → 04 agent-core 基础 → 05 AgentHarness → 06 存储 → 09 coding-agent
```

支线(01 tui / 02 protocol / 07 telemetry / 08 client-server)在主线之后或穿插学习即可,彼此独立。

## 一句话定位每个包

- **pi-tui**:差分渲染的终端 UI 库,coding-agent 的交互式界面靠它。
- **pi-protocol**:传输无关的 CBOR 协议,远程会话的字节格式。
- **pi-ai**:把 OpenAI/Anthropic/Google/Bedrock 等统一成一个接口,所有 LLM 调用走这里。
- **pi-agent-core**:agent 运行时——轮次循环、工具调用、状态;以及**持久化 AgentHarness**(当前开发重点)。
- **session-backends/sqlite-node**:会话落盘,表结构直接对应 harness 的 tree/lanes/records/facts/单写者。
- **pi-telemetry**:厂商无关的遥测契约与 schema。
- **pi-client / pi-server**:远程会话的客户端 / 服务端,快照 + 事件流。
- **pi-coding-agent**:把上面所有东西组装成 `pi` 这个 CLI。
- **pi-evals**:评测框架。
