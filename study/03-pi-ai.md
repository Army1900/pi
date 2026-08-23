# 03. pi-ai(统一多 provider LLM API)

> 阶段:主线(核心) | 前置:无

`packages/ai` —— 把 OpenAI / Anthropic / Google / Bedrock 等多家 LLM 统一到一个接口背后。**所有 LLM 调用都走这里**(agent-core、coding-agent 都依赖它)。是理解整个项目的地基之一。

## 学习目标

- 看懂 `Model` / `Transport` / `SimpleStreamOptions` 这套核心抽象
- 理解模型发现与 catalog、生成文件的角色
- 理解 auth(oauth / env key)与 provider 适配
- 知道 compat 兼容层在解决什么

## 关键文件

- `src/index.ts` —— 导出入口,从这里开始摸对外 API
- `src/types.ts` —— **核心类型**:`Model`、`Transport`、`SimpleStreamOptions`、`Message`/`TextContent`/`ImageContent`、`ThinkingBudgets`、`Usage` 等
- `src/models.ts` + `src/models-store.ts` + `src/models.generated.ts` —— 模型元数据。**`models.generated.ts` 是生成文件,禁止手改**(改 `scripts/generate-models.ts` 后 `npm run generate:models`)
- `src/image-models.ts` + `src/image-models.generated.ts` + `src/images*.ts` —— 图像模型
- `src/model-catalog.ts` —— 模型目录
- `src/providers/` —— **各家 provider 的具体适配**,每家一个子模块(OpenAI/Anthropic/Google/Bedrock/…)
- `src/auth/` + `src/oauth.ts` + `src/env-api-keys.ts` + `src/bun-oauth.ts` —— 鉴权:OAuth 流程、环境变量 API key
- `src/compat.ts` + `src/compat/` —— 兼容层(provider SDK 差异抹平 / legacy 别名,见 `legacy-api-aliases.ts`)
- `src/api/` —— API 层实现细节
- `src/session-resources.ts` —— 会话级资源(handle 等,与 deferred 流相关)
- `src/cli.ts` —— 包自带的 CLI(bin)

## 核心概念

- **Transport 抽象**:provider 各家 SDK/HTTP 接口不同,统一成一套 transport 调用 + 流式回调。上层只面对统一接口。
- **Model 元数据**:每个模型有 id、上下文窗口、cost、能力(reasoning / image 输入…)、baseUrl 等。`models.generated.ts` 是从各 provider 目录抓取后生成的快照。
- **流式(SimpleStreamOptions)**:统一了流式生成 + 可中断 + 用量统计(input/output/cache read/write/cost)。
- **Auth 双轨**:OAuth(交互式登录、token 刷新)vs API key(环境变量)。
- **Deferred 流**:某些 provider 支持异步请求,`session-resources` 持有 provider handle,稍后 redeem 响应(harness 文档里会再提到)。

## 建议阅读顺序

1. `src/index.ts` → `src/types.ts`:先吃透核心类型(`Model`、`Transport`、消息与 content、Usage)。
2. `src/models.ts` + `models-store.ts`:模型怎么注册/查找。看一眼 `models.generated.ts` 的**形状**(不要改)。
3. 挑**一家 provider**(建议 OpenAI 或 Anthropic)进 `src/providers/` 读完整适配,理解「统一接口 ↔ provider 具体调用」的映射。
4. `src/oauth.ts` + `env-api-keys.ts`:两条鉴权路径。
5. `src/compat.ts`:看它抹平了哪些差异。
6. `src/session-resources.ts`:deferred 流的 handle。

## 检查点

- [ ] 上层调 LLM 时,如何做到不关心是哪家 provider?
- [ ] 想新增一个 provider,要改哪些地方?(提示:`AGENTS.md` 提到加 provider 要有相应测试)
- [ ] `models.generated.ts` 怎么来的?为什么不能手改?
- [ ] 流式调用的用量(尤其 cache read/write)是怎么算出来的?

---

## 笔记

### 概念与要点
（待补充）

### 代码走读
（待补充）

### 疑问与待查
（待补充）
