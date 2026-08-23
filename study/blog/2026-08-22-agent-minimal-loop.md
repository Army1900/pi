# 一个 Agent 的最小逻辑 —— 从 20 行伪代码读懂 pi 的 agent-loop

> 关联模块:[../04-pi-agent-core-基础.md](../04-pi-agent-core-基础.md) | 日期:2026-08-22
>
> 所有代码位置基于本文写作时的 `main`,格式 `文件:行号`,可直接点击跳转。

## 问题 / 背景

"AI agent" 这个词被产品文案淹没之后,它到底指什么?把 UI、会话管理、持久化、扩展系统全部剥掉,一个 agent 剩下的最小逻辑是什么?这篇文章沿着 `packages/agent`(pi-agent-core)的非 harness 部分——也就是 [../04-pi-agent-core-基础.md](../04-pi-agent-core-基础.md) 对应的代码——自底向上把这个问题拆成六层。读完你应该能默写出这个循环,并知道每一层对应的真实代码在哪。

先剧透答案:**agent = 「消息列表 + 工具集」喂给 LLM;模型要么说话,要么发起工具调用;工具结果作为新消息塞回去;再来一轮;直到模型不再要工具。**

## Level 0:最小心智模型

不看任何代码,一个 agent 的骨架就是这段伪代码:

```text
messages = [用户消息]
tools    = [...]
while true:
    assistant = 调 LLM(system prompt, messages, tools)
    messages.push(assistant)
    toolCalls = assistant 里所有 type == "toolCall" 的块
    if toolCalls 为空:
        break                      # 模型说完了,本次运行结束
    for call in toolCalls:
        result = 执行(call)        # 出错也返回一个"错误结果",而不是抛异常
        messages.push(result)
```

真实实现比它多出的所有东西,都在回答四个工程问题:**消息长什么样、怎么调 LLM、循环何时停、工具怎么执行**。下面逐层对应。

## Level 1:数据 —— 三种消息和一个工具定义

agent 的一切状态就是 `messages`。消息类型定义不在 agent 包,而在它依赖的 pi-ai 里(`packages/agent/src/types.ts:1-15` 的 import 可以看到这条依赖边):

```ts
// packages/ai/src/types.ts:455
export type Message = UserMessage | AssistantMessage | ToolResultMessage;
```

- `UserMessage`(`packages/ai/src/types.ts:409`)—— 用户输入;
- `AssistantMessage`(`packages/ai/src/types.ts:415`)—— 模型回复,内容是块数组,可以是文本、思考,或 **toolCall 块**(`packages/ai/src/types.ts:361`:`type: "toolCall"`);
- `ToolResultMessage`(`packages/ai/src/types.ts:437`)—— 工具执行结果,靠 `toolCallId` 与请求它的 toolCall 配对。

agent 包在这之上做了一个扩展点:

```ts
// packages/agent/src/types.ts:325
export type AgentMessage = Message | CustomAgentMessages[keyof CustomAgentMessages];
```

应用可以通过 declaration merging(`packages/agent/src/types.ts:316-318` 的注释示例)注入自己的消息类型——UI 通知、工件消息等。这些富消息**只在 agent 内部流转**,喂给 LLM 前会被过滤/转换(见 Level 2)。

工具的定义:

```ts
// packages/agent/src/types.ts:386(节选)
export interface AgentTool<TParameters extends TSchema = TSchema, TDetails = any> extends Tool<TParameters> {
	label: string;
	execute: (
		toolCallId: string,
		params: Static<TParameters>,
		signal?: AbortSignal,
		onUpdate?: AgentToolUpdateCallback<TDetails>,
	) => Promise<AgentToolResult<TDetails>>;
}
```

两个值得注意的细节:

- 参数用 typebox schema 描述(`TParameters`),执行前会做 schema 校验(见 Level 4);
- `execute` 的返回是 `AgentToolResult`(`packages/agent/src/types.ts:361-375`):`content`(给模型看的文本/图片)+ `details`(给 UI/日志的结构化数据)分离——**给模型的和给人看的是两份数据**。

最后,一次循环运行需要的全部输入打包在 `AgentContext`(`packages/agent/src/types.ts:412-419`):`systemPrompt` + `messages` + `tools`。就这么三样。

## Level 2:LLM 调用边界 —— StreamFn 与消息转换

循环里"调 LLM"这一步被抽象成一个函数类型:

```ts
// packages/agent/src/types.ts:28
export type StreamFn = (
	model: Model<Api>,
	context: Context,
	options?: SimpleStreamOptions,
) => AssistantMessageEventStream | Promise<AssistantMessageEventStream>;
```

`Context` 是 pi-ai 的请求形状(`packages/ai/src/types.ts:509`)。pi-ai 的 `Models.streamSimple` 天然满足这个签名(注释写在 `packages/agent/src/types.ts:18-27`)——所以"换一家 provider"对 agent 循环来说是透明的。

这个类型的契约很有讲究(`packages/agent/src/types.ts:22-27`):

> **Must not throw** …… Failures must be encoded in the returned stream …… final AssistantMessage with stopReason "error" or "aborted"

**失败也是数据,不是异常。**错误最终以 `AssistantMessage.stopReason === "error"` 的形式从流里出来,循环据此收尾(见 Level 3)。这是整个 agent 包反复出现的设计基调。

真正发起调用的是 `streamAssistantResponse`(`packages/agent/src/agent-loop.ts:281`),它是 AgentMessage 世界与 LLM Message 世界的边界,四步:

```ts
// packages/agent/src/agent-loop.ts:288-312(节选)
let messages = context.messages;
if (config.transformContext) {
	messages = await config.transformContext(messages, signal); // ① AgentMessage → AgentMessage
}
const llmMessages = await config.convertToLlm(messages);        // ② AgentMessage → Message
const llmContext: Context = { systemPrompt, messages: llmMessages, tools };
const resolvedApiKey = (config.getApiKey ? await config.getApiKey(model.provider) : undefined) || config.apiKey; // ③ 每次调用前解析 key(给会过期的 OAuth token 用)
const response = await streamFunction(config.model, llmContext, { ...config, apiKey, signal }); // ④ 真正调用
```

② 是应用必须提供的 `convertToLlm`(`packages/agent/src/types.ts:178`):把内部的富消息转成 LLM 认识的三种消息,转换不了的(UI 通知等)过滤掉。默认实现很朴素——直接按 role 过滤(`packages/agent/src/agent.ts:33-37`)。

拿到流之后,函数把流事件转发成 agent 事件:`start`/各种 `_delta` 推着 `message_update` 走,`done`/`error` 时取最终消息、发 `message_end` 并返回(`packages/agent/src/agent-loop.ts:317-361`)。注意一个细节:**partial 消息在流开始时就被 push 进 `context.messages`**(`packages/agent/src/agent-loop.ts:321`),结束后原位替换——外部观察者在中途就能看到"正在生成的那条消息"。

## Level 3:主循环 —— runLoop

核心中的核心是 `runLoop`(`packages/agent/src/agent-loop.ts:155`),全部逻辑约 120 行,结构是**双层 while**:

```ts
// packages/agent/src/agent-loop.ts:170-174(节选)
while (true) {                                  // 外层:follow-up 驱动
	let hasMoreToolCalls = true;
	while (hasMoreToolCalls || pendingMessages.length > 0) {   // 内层:工具调用 + steering 驱动
		...
	}
	...
}
```

内层每一圈就是 Level 0 伪代码的一轮:

1. 注入积压消息(`agent-loop.ts:182-190`);
2. `streamAssistantResponse` 拿到 assistant 消息(`agent-loop.ts:193`);
3. 若 `stopReason` 是 `"error"` 或 `"aborted"`,立即 `turn_end` + `agent_end` 退出(`agent-loop.ts:196-200`)——这就是 Level 2 那个"错误即数据"契约的落点;
4. 收集 toolCall 块(`agent-loop.ts:203`),执行(见 Level 4),结果 push 回 `currentContext.messages` 和 `newMessages`(`agent-loop.ts:218-221`);
5. `turn_end`,然后 `prepareNextTurn` 钩子可以替换下一轮的 context/model/thinking level(`agent-loop.ts:226-245`),`shouldStopAfterTurn` 可以优雅停车(`agent-loop.ts:247-257`),`getSteeringMessages` 取用户中途插的话(`agent-loop.ts:259`)。

内层退出(模型不再要工具、也没有插话)后,外层问一次 `getFollowUpMessages`:有排队的后续消息就把它当 `pendingMessages` 继续 `continue` 内层,否则 `break`,发 `agent_end`(`agent-loop.ts:262-274`)。

**内层 = "当前这摊事没干完",外层 = "干完了但还有新任务"。**steering(运行中插话,不打断工具)与 follow-up(等 agent 停了再说)的区分就在这两个出口上。

一个容易忽略的正确性细节:如果 `stopReason === "length"`(输出被 token 上限截断),**整条消息里的工具调用全部判失败,一个都不执行**(`packages/agent/src/agent-loop.ts:210-214`,实现于 `failToolCallsFromTruncatedMessage`,`agent-loop.ts:381`)。注释解释了为什么:流式工具参数经过"尽力抢救的 JSON 解析器",截断的参数可能**恰好能通过校验但内容不完整**——执行它们等于执行一条看起来合法的坏指令。宁可全部报错让模型重发。

## Level 4:工具执行 —— prepare / execute / finalize 三段式

工具执行入口是 `executeToolCalls`(`packages/agent/src/agent-loop.ts:411`),按配置选顺序(`executeToolCallsSequential`,`agent-loop.ts:433`)或并行(`executeToolCallsParallel`,`agent-loop.ts:489`)。默认并行(默认值在 `packages/agent/src/agent.ts:237`),但任一被调工具自己声明 `executionMode: "sequential"` 就整批降级为顺序(`agent-loop.ts:419-425`)——比如写文件的工具不该和别的并发。

无论哪种模式,每个工具调用都走同一条三段流水线:

**① prepare(`prepareToolCall`,`` `packages/agent/src/agent-loop.ts:600`)**:找到工具 → `prepareArguments` 兼容 shim → `validateToolArguments`(schema 校验,来自 pi-ai)→ `beforeToolCall` 钩子。钩子返回 `{ block: true }` 就不执行,循环替它生成一条错误 toolResult(`agent-loop.ts:636-646`)——**拦截的结果对模型表现为"工具失败了"**,模型能看到原因并调整策略。工具不存在、参数不合法同样在这里变成错误结果(`agent-loop.ts:607-613`)。

**② execute(`executePreparedToolCall`,`packages/agent/src/agent-loop.ts:670`)**:调 `tool.execute(...)`,工具可用 `onUpdate` 回调流式上报进度(转成 `tool_execution_update` 事件)。关键是 catch 分支(`agent-loop.ts:701-707`):**工具抛出的任何异常都被转成错误结果**。所以 Level 0 伪代码里那句注释是真的——工具失败永远不会杀死循环,只会变成一条 `isError` 的消息喂回模型,下一轮模型自己决定重试还是放弃。

**③ finalize(`finalizeExecutedToolCall`,`packages/agent/src/agent-loop.ts:713`)**:`afterToolCall` 钩子可对结果做**字段级覆盖**(`content`/`details`/`isError`/`usage`/`terminate`,语义见 `packages/agent/src/types.ts:84-95`)——省略的字段保留原值,不做深合并。

最后 `createToolResultMessage`(`packages/agent/src/agent-loop.ts:777`)把结果包装成 `ToolResultMessage`。这里有个防御性细节的注释(`agent-loop.ts:782-784`):无类型的 JS 扩展工具可能返回没有 `content` 的结果,这里归一化成 `[]`,**不让 null 进入会话历史或 provider 载荷**。

还有个微妙的批处理规则:某个结果想要"提前终止",必须**这一批所有结果都要求终止**才生效(`shouldTerminateToolBatch`,`packages/agent/src/agent-loop.ts:582-584`)——防止一个工具的单方面终止意图掐断整批工作。

## Level 5:事件与状态 —— 无状态循环 + 有状态 Agent

到目前为止,循环本身几乎是无状态的:`runAgentLoop`(`packages/agent/src/agent-loop.ts:95`)接收 context 快照,通过 `emit` 回调往外发事件,返回本次新增的消息。它对外界的全部影响就是**事件流**。

事件类型是个刻意分层的 union(`packages/agent/src/types.ts:428-443`):

```ts
export type AgentEvent =
	| { type: "agent_start" }
	| { type: "agent_end"; messages: AgentMessage[] }
	| { type: "turn_start" }
	| { type: "turn_end"; message: AgentMessage; toolResults: ToolResultMessage[] }
	| { type: "message_start"; message: AgentMessage }
	| { type: "message_update"; ... }
	| { type: "message_end"; message: AgentMessage }
	| { type: "tool_execution_start" | "tool_execution_update" | "tool_execution_end"; ... };
```

三层生命周期(agent / turn / message)加一层工具执行,UI 只需要消费这些事件就能完整渲染过程——**事件是观察口,不是控制口**。

状态住在 `Agent` 类里(`packages/agent/src/agent.ts:173`)。它是有状态的壳:持有 transcript、订阅者集合、steering/followUp 两个队列(`PendingMessageQueue`,`agent.ts:125`,默认 `one-at-a-time`,`agent.ts:231-232`)。`prompt()`(`agent.ts:348-358`)把输入归一化成消息数组,经 `runPromptMessages`(`agent.ts:409-423`)拍一份 context 快照(`createContextSnapshot`,`agent.ts:437-443`)交给无状态的 `runAgentLoop`,并用 `processEvents`(`agent.ts:544-591`)把事件**归约**进自己的状态:`message_end` 时 push 进 transcript,`tool_execution_start/end` 时维护 `pendingToolCalls` 集合,等等,然后依次 await 所有订阅者。

顺便:`agentLoop`(`agent-loop.ts:31`)是 `runAgentLoop` 的流式包装——把回调形式包成 `EventStream<AgentEvent, AgentMessage[]>`,`agent_end` 作为流终结(`createAgentStream`,`agent-loop.ts:145`)。`stream-fn.ts` 全文 20 行,只是个可注入的默认 StreamFn 槽位(`packages/agent/src/stream-fn.ts:11-20`)——pi-agent-core 刻意不依赖任何 provider 目录,宿主(coding-agent)自己装。

## Level 6:拼一个最小可跑的例子

测试文件里就有一个手写的最小例子。Mock 一个 StreamFn(`MockAssistantStream`,`packages/agent/test/agent-loop.test.ts:16`)、一个假 Model(`createModel`,`test/agent-loop.test.ts:40`),再配一个直通的 `identityConverter`(`test/agent-loop.test.ts:80`),然后:

```ts
// 改编自 packages/agent/test/agent-loop.test.ts:97-118
const context: AgentContext = { systemPrompt: "", messages: [], tools: [] };
const config: AgentLoopConfig = { model: createModel(), convertToLlm: identityConverter };

const stream = agentLoop([createUserMessage("Hello")], context, config, undefined);

await stream.result(); // 拿到本次运行新增的全部消息
```

把 mock 换成 pi-ai 的 `Models.streamSimple`、`tools` 换成真实工具、`identityConverter` 换成你的转换逻辑,这就是一个能干活的 agent。整个包没有更多魔法。

## 尾声:这套循环与 AgentHarness 的关系

这个 ~800 行的 `agent-loop.ts` 是 pi 的**过程内**最小 agent。而 [../05-AgentHarness-持久化.md](../05-AgentHarness-持久化.md) 对应的 harness 要解决的是它没有回答的问题:进程崩了怎么办?工具执行到一半被谁看到了吗?多路并行会话怎么共享历史?

harness 的答案是把这个循环的**每一步**都变成显式的、可持久化的操作记录(回忆 harness 设计里的 lane records),每个 effect 跨一个注入边界(`drive: "manual"` 让测试逐边界驱动)。概念上,你可以把本文的循环看作 harness 状态机的"无持久化投影"——先彻底理解前者,再去看 `packages/agent/docs/harness-v2.md` 的 Part I,会顺很多。

## 总结

一个 agent 的最小逻辑:

```
(messages, tools) → LLM → toolCalls? → 执行 → 结果入列 → 循环
```

以及三个让它"能用"的工程设计,全部有代码为证:

1. **失败是数据,不是异常**——StreamFn 不许 throw(`types.ts:22-27`),工具异常转错误结果(`agent-loop.ts:701`),被截断的整批工具调用宁可全败不执行(`agent-loop.ts:210`);
2. **事件外置**——循环无状态,状态由 `Agent` 归约事件维护,UI 与扩展只观察(`types.ts:428`,`agent.ts:544`);
3. **边界分层**——AgentMessage/Message 两个世界在 `convertToLlm` 处显式转换(`agent-loop.ts:295`),给模型的与给 UI 的数据从工具返回值起就分离(`types.ts:361-375`)。
