# 09. pi-coding-agent(`pi` CLI,大结局)

> 阶段:主线(所有模块汇合) | 前置:[03 ai](./03-pi-ai.md)、[04 agent-core](./04-pi-agent-core-基础.md)、[05 harness](./05-AgentHarness-持久化.md)、[01 tui](./01-pi-tui.md)

`packages/coding-agent` —— 把前面所有东西组装成 `pi` 这个 CLI。这是最后一站:到这里,你会看到 `pi-ai`(调模型)、`pi-agent-core`/harness(跑会话)、`pi-tui`(交互界面)、`pi-client`/`server`(远程)如何拼成一个产品。

## 学习目标

- 看懂 CLI 的入口与几种 mode(interactive / rpc / print / json-event)
- 理解会话生命周期(session runtime / services / manager)
- 理解内置工具、skills、prompt 模板、extensions 如何加载与注入
- 理解模型解析与 provider 组合、系统提示组装、compaction

## 入口与 mode

- `src/cli.ts`(`bin`: `pi`)、`src/main.ts`、`src/index.ts`、`src/rpc-entry.ts` —— CLI 入口
- `src/config.ts` —— 配置
- `src/modes/`:
  - `interactive/` —— TUI 交互模式(用 [01] 的 tui)
  - `rpc/` —— RPC 模式(给程序化调用 / 远程)
  - `print-mode.ts` —— 一次性打印模式(`pi -p "..."`)
  - `json-event.ts` —— JSON 事件流模式

## core/(应用内核)

- `agent-session.ts` + `agent-session-runtime.ts` + `agent-session-services.ts` —— **会话运行时与服务**,核心
- `session-manager.ts`、`session-cwd.ts` —— 会话管理与工作目录
- `system-prompt.ts` + `prompt-templates.ts` —— 系统提示与模板
- `skills.ts` + `slash-commands.ts` —— skill 与 slash 命令
- `model-resolver.ts` + `model-runtime.ts` + `model-config.ts` + `model-registry.ts` + `models-store.ts` —— 模型解析/运行时/注册
- `provider-composer.ts` + `provider-attribution.ts` —— provider 组合与归因
- `extensions/`(`loader.ts` / `runner.ts` / `wrapper.ts` / `types.ts`)—— 扩展加载/运行
- `compaction/` —— 上下文压缩
- `tools/`(`read`/`write`/`edit`/`edit-diff`/`bash`/`find`/`grep`/`ls`/`image`/`output-accumulator`/`truncate`/`tool-definition-wrapper`/`render-utils`/`path-utils`)—— **内置工具实现**
- 其他:`settings-manager.ts`、`trust-manager.ts`/`project-trust.ts`、`auth-storage.ts`/`auth-guidance.ts`/`runtime-credentials.ts`、`telemetry.ts`、`event-bus.ts`、`messages.ts`、`diagnostics.ts`、`timings.ts`、`usage-totals.ts`、`cache-stats.ts`、`export-html/`、`resource-loader.ts`、`pi-manifest.ts`、`sdk.ts`、`experimental.ts`

## 远程与 CLI 子命令

- `src/server/create-harness.ts` —— 服务端建 harness
- `src/client/remote-session.ts` + `transcript.ts` —— 作为客户端连远程
- `src/cli/`:`args.ts`、`auth-command.ts`/`auth-check.ts`、`config-selector.ts`、`session-picker.ts`、`startup-ui.ts`、`list-models.ts`、`credential-print.ts`、`file-processor.ts`、`initial-message.ts`、`experimental/`、`project-trust.ts`

## 配套概念

- **扩展与 skills**:`.pi/extensions`、`.pi/skills`(`SKILL.md`),会被加载并注入系统提示(见 [05] 的 `Skill` 类型与 `harness/skills.ts`)。
- **provider 组合**:一个会话可由多个 provider 能力组合而成(`provider-composer.ts`)。
- **信任(trust)**:项目级信任(`project-trust.ts`/`trust-manager.ts`),决定是否在某个 cwd 自动放行。
- **modes 的本质**:同一套会话内核,套不同「前端」(TUI / RPC / 打印 / JSON 事件)。

## 建议阅读顺序

1. `src/cli.ts` → `src/main.ts`:看参数解析与启动流程,选哪个 mode。
2. `src/config.ts`:配置加载。
3. `core/agent-session.ts` 系列:会话怎么起来、服务怎么注入。
4. `core/model-resolver.ts` + `provider-composer.ts`:模型怎么定、provider 怎么组。
5. `core/system-prompt.ts` + `skills.ts`:提示与 skill 注入。
6. `core/tools/`:挨个看内置工具(从 `read`/`write`/`edit`/`bash` 入手)。
7. `core/extensions/`:扩展如何挂进执行(hooks/events,呼应 [05])。
8. 选一个 mode(建议 `print-mode.ts` 最简单,再看 `interactive/`)看内核如何接到前端。
9. `server/create-harness.ts` + `client/remote-session.ts`:远程串起来(呼应 [08])。

## 检查点

- [ ] 一个 prompt 从 CLI 进来,到 LLM 返回,经过了哪些层?
- [ ] 四种 mode 共享什么、各自特殊在哪?
- [ ] skill / prompt template / extension 分别从哪加载、怎么进系统提示?
- [ ] 内置工具有哪些?各自的安全边界(bash 执行、文件写入…)在哪?

---

## 笔记

### 启动与 mode 选择流程
（待补充）

### 会话内核(agent-session 系列)
（待补充）

### 工具 / skills / extensions
（待补充）

### 疑问与待查
（待补充）
