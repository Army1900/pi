# myagent 实施计划

用 Java 21 重推导 pi 的 agent 核心。三阶段:最小循环 → 控制面 → 迷你持久化 harness。每阶段的产出以**测试变绿**为验收,以**与 pi 的 diff**为学习信号。

## 方法:每阶段的工作循环

```
1. 读    通读该阶段的 pi 参考切片(见各阶段「pi 参考」)
2. 测    关上源码,先写测试(移植 pi 的 mock 测试思路,先红)
3. 推    凭记忆与规格重推导实现(允许撞墙,撞墙点就是知识点)
4. 校    测试到绿
5. 对    回到 pi 源码逐段 diff,记录:哪些你想对了 / 哪些 pi 有非显然洞见
6. 记    diff 与结论写回本文件「diff 记录」+ study 对应模块笔记
```

铁律:**不逐行翻译**。第 3 步如果发现自己"记得 pi 怎么写",说明退回翻译模式了——回到行为规格(测试)重新问"我会怎么设计"。

## 分层规则(2026-08-23 确立,DDD 四层)

```
dev.myagent
├── domain/            # 纯领域:只依赖 JDK(连 Jackson 都不许进)
│   ├── model/         # 一个聚合 + 词汇 + 公共抽象(契约在 package-info):
│   │   ├── message/   #   共享词汇:Message、Content、StopReason、Usage(session 持有、gateway 装配、AgentEvent 运载)
│   │   ├── tool/      #   工具词汇(领域名词过边界,与 message 同等待遇):ToolDescriptor、ToolSchema、AgentToolResult
│   │   ├── session/   #   聚合根 Session:对话消息(只增)+ [阶段3: entry/lanes/facts/Record(RunId 随记录回归)]
│   │   └── shared/    #   公共抽象:AggregateRoot(范型主键 + 待发布事件)、DomainEvent(账本事件标记)
│   ├── gateway/       # 端口 + 流机制:StreamFn、AgentTool、AssistantMessageStream(有行为,是机制不是数据)
│   │   └── dto/       #   端口载荷(非领域词汇的部分):Context(装配形状)、Model(provider 目录)、StreamEvent(端口方言)
│   ├── service/       # 领域服务:AgentLoop(Run 概念的执行者,功课)、ToolExecutor、Reducer(阶段3);词汇:AgentEvent、Result
│   └── repository/    # 仓储端口:JournalRepository(阶段3)
├── application/       # 只依赖 domain:Agent 用例门面、事件分发、恢复用例
├── infrastructure/    # 适配器,实现 domain 端口:llm / journal / lease
└── (share:不建。准入规则:被 ≥2 层需要且不属于任何一层词汇,才建)
```

- **依赖单向**:infrastructure → domain ← application;domain 零外部依赖。阶段 3 结束用 grep 自查。
- **端口防泄漏自检**:每个端口方法能否不用 file / JSON / SQL / HTTP 词汇描述?不能 = 端口被污染。
- **端口失败契约(领域规则,写进接口 Javadoc)**:`StreamFn` 失败=数据(禁止抛)→ 循环不死;`JournalRepository` 磁盘故障=崩溃(允许抛)→ 崩溃边界即在此。哪些失败是数据、哪些是进程死,按端口分。
- **两种事件勿混**:AgentEvent(观察,不落盘,消费方不能影响执行)vs Record(领域事件,事件溯源素材)。都定义在 domain,发布/分发在 application。
- **反仪式**:不为唯一实现预埋接口、无第二调用方不建 DTO/mapper;每次抽象在 diff 记录里写理由。

## 阶段 0:环境验收

- [x] `java -version` 为 21+(21.0.12.1),`mvn -version` 正常(Maven 3.9.16)
- [x] `mvn test` 跑通 `SmokeTest`(绿色)——工具链 OK(2026-08-23)
- [ ] (可选)`git init` 独立建仓,每阶段完成时提交一次

## 阶段 1:最小循环(核心,约 200-500 行)

**目标**:跑通「消息列表 + 工具集 → LLM → toolCall → 执行 → 结果回喂 → 循环到无工具调用」。

### 范围内

- 消息模型:`user` / `assistant` / `toolResult` 三种消息 + assistant 内容里的 `toolCall` 块
- `AgentTool` 接口:name、description、参数 schema、`execute`
- `StreamFn` 契约(model, context) → 流式 assistant 响应;**禁止抛异常,失败编码为 `stopReason: "error"` 的消息**
- `runLoop`:单层循环(此阶段不做 steering/followUp)
- 事件:agent/turn/message/tool_execution 四层生命周期
- errors-as-data:工具抛异常 → `isError` 的 toolResult,循环不死

### 范围外(推后)

- transformContext / convertToLlm 分离(此阶段消息模型单一,直接喂)
- 并行工具执行(此阶段顺序)
- 截断处理(stopReason `length`)——作为选做加分项

### pi 参考

| 看什么 | 位置 |
|---|---|
| 全部核心类型(先读这个) | `packages/agent/src/types.ts`(`StreamFn`:28、`AgentToolResult`:361、`AgentTool`:386、`AgentContext`:412、`AgentEvent`:428) |
| 主循环 | `packages/agent/src/agent-loop.ts`(`runLoop`:155、`streamAssistantResponse`:281) |
| 工具执行三段式 | 同文件(`prepareToolCall`:600、`executePreparedToolCall`:670、`finalizeExecutedToolCall`:713) |
| 测试怎么 mock | `packages/agent/test/agent-loop.test.ts`(`MockAssistantStream`:16、`createModel`:40、`createAssistantMessage`:55、`identityConverter`:80) |
| 消息的原始定义 | `packages/ai/src/types.ts`(`Message`:455、`Context`:509、toolCall 块:361) |

### 测试先行(移植清单)

- [ ] T1 纯对话:用户问 → mock 返回纯文本 → 收到 1 assistant 消息,循环结束
- [ ] T2 工具往返:mock 返回 toolCall → 工具被执行 → toolResult 回喂 → mock 收到的下一次请求包含该 toolResult → 返回文本 → 结束
- [ ] T3 工具失败:工具 `execute` 抛 `RuntimeException` → 产出 `isError=true` 的 toolResult → 循环继续,模型看到错误后收尾
- [ ] T4 流失败:mock 返回 `stopReason="error"` → 循环干净收尾,不抛异常
- [ ] T5 事件序列:一次 T2 的完整事件序 = `agent_start, turn_start, message_start(prompt), message_end, message_start(assistant), message_end, tool_execution_start, tool_execution_end, message_start(toolResult), message_end, turn_end, agent_end`
- [ ] T6(选做)截断:mock 返回 `stopReason="length"` 且带 toolCall → 工具不执行,全部变错误结果(对照 `agent-loop.ts:381`)

### 预期 diff 点(Java 侧要做的决策,先想后查)

1. **discriminated union 用什么表达**:`Message` 建模为 sealed interface + 三个 record?`content` 块的 toolCall/text 联合怎么拼?(pi 一个 `|` 完事,Java 的样板是学费)
2. **schema 校验**:没有 typebox。选项:手写 validator / Jackson + 手写 schema 描述 / 引入 json-schema 库。建议先手写,痛了再换——这个"痛"本身是 pi 选 typebox 的原因
3. **流式**:TS 的 `for await` 在 Java 对应什么?选项:`CompletableFuture` + 回调 / 虚拟线程 + `BlockingQueue` / `Stream<AssistantMessageEvent>`。**建议虚拟线程 + 队列**,最接近 TS 异步迭代器的体感(阶段 3 也用得上)
4. **事件发射**:`emit` 回调 → Java 的 `List<Consumer<AgentEvent>>`?同步逐个调用,先不做异步

### 验收

- [ ] 上述测试全绿
- [ ] 能不看资料在白纸上画出消息流转图(user → assistant(toolCall) → toolResult → assistant)
- [ ] diff 记录 ≥3 条

## 阶段 2:控制面(steering / followUp / abort / hooks)

**目标**:把单层循环升级为 pi 的双层 while,加上拦截与中断。

### 范围内

- 双层循环:内层(工具调用 + steering)、外层(followUp 重启内层)
- `PendingMessageQueue`:`all` / `one-at-a-time` 两种 drain 模式
- `steer()`(当前 turn 结束后注入)/ `followUp()`(agent 本要停止时注入)
- `beforeToolCall`(可 block,产生错误 toolResult)/ `afterToolCall`(字段级覆盖)
- abort:`Thread.interrupt()` 或协作式 `volatile boolean`——对照 pi 的 AbortSignal
- 有状态外壳 `Agent` 类:持有 transcript、订阅者、队列;`prompt()` 拍快照交给无状态循环(对照 pi 的 Agent/processEvents 分离)

### pi 参考

| 看什么 | 位置 |
|---|---|
| 队列与外壳 | `packages/agent/src/agent.ts`(`PendingMessageQueue`:125、`Agent`:173、`steer`:283、`followUp`:288、`createLoopConfig`:445、`processEvents`:544) |
| 双层循环 | `packages/agent/src/agent-loop.ts:170-274`(内层 while / 外层 while / 两个出口) |
| 拦截类型 | `packages/agent/src/types.ts`(`BeforeToolCallResult`:61、`AfterToolCallResult`:84) |

### 测试先行

- [ ] T7 steering:第一轮 mock 返回 toolCall;执行期间 `steer(新消息)`;下一轮 LLM 请求里既有 toolResult 又有插入消息
- [ ] T8 followUp:对话自然结束后 `followUp` 的消息让循环再跑一轮
- [ ] T9 block:`beforeToolCall` 返回 block → 工具不执行,产出错误 toolResult,模型看到 reason
- [ ] T10 afterToolCall 覆盖:改写工具结果的 content,模型看到的是改写后的
- [ ] T11 abort:LLM 流中途 abort → 循环以 `stopReason="aborted"` 干净收尾,不抛异常

### 预期 diff 点

1. **abort 语义**:Java 没有 AbortSignal。协作式中断 vs `Thread.interrupt()` vs 虚拟线程 cancel——注意 pi 的契约"abort 也是数据"(aborted 消息),不是异常
2. **单写者的伏笔**:为什么 `prompt()` 在运行中要抛错而不是排队(pi `agent.ts:351`)?——这就是 harness"单写者"的进程内前身,想清楚写下来

### 验收

- [ ] 测试全绿;能口头解释内层/外层各自的存在理由
- [ ] diff 记录累计 ≥6 条

## 阶段 3:迷你持久化 harness(最有价值)

**目标**:把循环的每步变成 append-only 记录,实现崩溃恢复与单写者。做完这步,`harness-v2.md` 的"durable runs / 无部分结果"从文档变成肌肉记忆。

### 范围内

- **journal**:append-only 文件(每行一条 JSON 记录,Jackson 序列化;对应 pi 的 jsonl 后端)
- **记录类型**(sealed interface + 类型判别字段):`run_started`、`message_appended`(user/assistant/toolResult)、`llm_request_started`、`llm_response_appended`、`tool_execution_started`、`tool_result_appended`、`run_finished`
- **reducer**:journal 折叠(fold)出当前状态——对照 pi 的 `harness/session/state.ts` + `reducer.ts`
- **恢复**:新进程/新实例读 journal → replay 到状态 → 从最后持久边界继续未完成的 run
- **崩溃语义**:区分"预期失败"(→ 错误结果,数据)与"崩溃"(异常逃逸,进程级)。崩溃恢复时:无 response 的 LLM 请求 = 未知的 provider 效应(对照 harness-v2 的处理)
- **单写者**:启动时对 `.lock` 文件 `FileChannel.tryLock()`,拿不到 → 拒绝启动(对照 `sqlite-node/.../writer-leases.ts`)
- **容忍截断**:最后一行写一半(JSON 不完整)→ 丢弃该行,回退到上一条完整边界

### pi 参考

| 看什么 | 位置 |
|---|---|
| 设计文档(**必读** Part I) | `packages/agent/docs/harness-v2.md`(四元结构 / durable runs / 单写者 / 无部分结果) |
| 状态折叠 | `packages/agent/src/harness/session/state.ts`、`reducer.ts` |
| JSONL 后端 | `packages/agent/src/harness/session/jsonl.ts` + `jsonl/` |
| 表结构映射(看形状即可) | `packages/session-backends/sqlite-node/src/sqlite/storage/`(entries/lanes/records/facts/writer-leases) |

### 测试先行

- [ ] T12 replay 等价:一次完整运行后,replay(journal) 得到的状态 == 运行结束时的 live 状态
- [ ] T13 崩溃恢复(工具中途):工具执行中抛 `CrashException`(不被 errors-as-data 捕获,设计成逃逸)→ 新实例 replay → 未完成的 run 从边界继续,最终完成
- [ ] T14 崩溃恢复(LLM 后):LLM 请求后、响应前崩溃 → 恢复时不重放该请求的响应(未知效果),重新发起或按策略标记,不允许"半响应"
- [ ] T15 单写者:持锁的实例运行中,第二个实例尝试打开同一 journal → 被拒绝
- [ ] T16 截断容忍:手工截断最后一行 → replay 正常,状态回退到最后完整边界

### 预期 diff 点

1. **"崩溃"怎么在单测里表达**:真 kill 进程(脚本集成测试)vs 用逃逸异常模拟(单测)。pi 用 `drive: "manual"` 停在每个边界——你可以做个简化版:journal 写入点即边界,测试在这些点注入失败
2. **记录粒度**:上面给的记录类型是建议,自己撞一遍"哪些状态恢复时必须知道"再定——这正是 pi 把 lane records 设计成"发生了什么+接下来必须发生什么"的原因
3. **幂等**:恢复时重放 tool 执行吗?(pi 的答案:工具副作用对 harness 不可见,恢复从边界继续,可能重执行——读完 harness-v2 的 Non-goals: exactly-once 一节对照你的选择)

### 验收

- [ ] 测试全绿
- [ ] 能不看资料讲清:为什么"无部分结果可观测"要求响应完整落盘后才分类
- [ ] 对照 `sqlite-node/storage/` 的文件名,解释每个文件对应的 journal 记录类别
- [ ] diff 记录累计 ≥10 条

## 范围外(明确不做)

- 真实 provider HTTP 调用(mock 到底;`infrastructure/llm/RealStreamFn` 为编译通过的参考实现,不接线 —— 证明端口契约可被真实 HTTP 流实现)
- lanes 多通道、compaction、全局 facts(知道概念即可)
- TUI、MCP、扩展系统
- 跨进程复制/多写者

## pi ↔ myagent 映射总表

| pi | myagent(DDD 分层) | 阶段 |
|---|---|---|
| `types.ts` + `ai/types.ts` 消息与请求形状 | `domain/model/message`(Message 等)+ `model/tool`(ToolDescriptor、ToolSchema、AgentToolResult —— 领域名词过边界)+ `gateway/dto`(Context、Model)+ `service`(Result)—— 判据:领域词汇留 model,端口方言/装配形状进 dto | 1 ✅ 已初始化 |
| `types.ts` AgentEvent(:428) | `domain/service`:`AgentEvent`(观察事件;定义与发射在领域服务,分发在 application) | 1 ✅ 已初始化 |
| `types.ts` AgentTool(:386) | `domain/gateway`:`AgentTool`(能力端口,说领域词汇)+ `model/tool`:`ToolDescriptor`(描述子)—— 描述/能力拆分,pi 焊在一起的 diff | 1 ✅ 已初始化 |
| `types.ts` StreamFn 契约(:28) | `domain/gateway`:`StreamFn` + `StreamEvent` + `AssistantMessageStream` | 1 ✅ 已初始化 |
| `agent-loop.ts` runLoop / streamAssistantResponse | `domain/service`:`AgentLoop`(✅ 参考实现,T1/T2 绿;截断全败=T6 功课) | 1 ✅ |
| `agent-loop.ts` 三段式(prepare/execute/finalize) | `domain/service`:`ToolExecutor` | 1-2 |
| `agent.ts` Agent / PendingMessageQueue / processEvents | `application`:`Agent` + 事件分发 | 2 |
| `harness/session/` jsonl / state / reducer | `domain/service`:`Reducer`、`domain/repository`:`JournalRepository`、`infrastructure/journal` 实现 | 3 |
| `sqlite-node/.../writer-leases.ts` | `infrastructure/lease`(文件锁) | 3 |
| Axon `AggregateRoot` / Spring `AbstractAggregateRoot`(外部参照,非 pi) | `domain/model/shared`:`AggregateRoot` + `DomainEvent`;`session/Session` 继承(Run 已降级为过程 —— 状态归服务、痕迹归 Session,阶段 3 复审资格)。AgentEvent 刻意不实现 DomainEvent | ✅ 已初始化 |
| `test/agent-loop.test.ts` MockAssistantStream | `infrastructure/llm`:`MockStreamFn`(假适配器,剧本耗尽守错误契约) | 1 ✅ 已初始化 |

---

## 进度

| 阶段 | 状态 | 完成日期 |
|---|---|---|
| 0 环境 | ✅ | 2026-08-23 |
| 1 最小循环 | ◐ AgentLoop 参考实现落地,T1/T2 绿(2026-08-23);T3-T6 功课 | — |
| 2 控制面 | ☐ | — |
| 3 迷你 harness | ☐ | — |

## diff 记录(每阶段回填)

### 阶段 1
（待补充:我想对了什么 / pi 的非显然洞见 / Java 表达的取舍）

### 阶段 2
（待补充)

### 阶段 3
（待补充)
