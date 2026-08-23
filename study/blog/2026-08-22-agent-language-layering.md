# Agent 该用什么语言写?——从 TS vs Java 聊到"语言问题是分层问题"

> 关联模块:[../README.md](../README.md) | 日期:2026-08-22
>
> 这是一场真实讨论的整理。起点是一个具体问题(为什么现在的 coding agent 大多是 TS 不是 Java),终点是一个反转(这个问题本身是伪问题)。中间经历了多次反驳与修正——保留这个辩证过程,比直接给结论更有价值。讨论以 [pi](https://github.com/earendil-works/pi-mono) 代码库为标本,所有代码位置可点击。

## 问题 / 背景

2026 年,主流的交互式 coding agent(Claude Code、Gemini CLI、Cline、pi……)几乎清一色 TypeScript。Java——企业后端二十年的霸主——在这个品类里缺席。为什么?

一个 Java 工程师视角的直觉答案是:"等这个品类成熟,Java 的工程优势会赢回来。"这篇文章检验这个直觉,最终发现:**真正的答案不在任何语言里,而在分层里。**

## 一、TS 为什么赢了今天:五个理由

以 pi 为标本,当时给出的理由:

1. **瓶颈在模型不在运行时**。Agent 95% 的时间在等 LLM 流式响应(秒级)和子进程执行。JVM 比 V8 快的吞吐用不上;而"每次敲命令等 JVM 冷启动几百毫秒"对高频 CLI 是可感知的痛。pi 还能用 Bun `--compile` 打单文件二进制。
2. **JSON 形状的域**。工具协议、消息载荷全是 JSON。pi 的 `AgentTool`(`packages/agent/src/types.ts:386`)用一个 typebox schema 同时产出给模型看的 JSON Schema 和给编译器看的 TS 类型——一鱼两吃。
3. **交互式 UI 生态**。pi 自带 `pi-tui`(差分渲染);JS 圈还有 Ink。Java 的终端 UI(Lanterna/JLine)生态薄得多。
4. **SDK 与路径依赖**。各家官方 SDK、MCP 参考实现、教程示例都是 TS/Python 一等公民。第一批 agent 因此全是 TS,后来者抄最短路径——**路径依赖,不是技术必然**。
5. **自我扩展性**。pi 定位 self-extensible agent,`.pi/extensions` 里放用户代码运行时直接加载,JS 里是 `import()` 一个文件的事。

其中第 5 条很快被挑战了。

## 二、反驳与修正:哪些理由经得起成熟

### "动态加载用户代码是产品早期通病"

挑战:成熟产品不该需要那么多用户自定义;这更像发展中的病。

这个反驳**部分成立**。看成熟工具的真实做法:Vim/Emacs/Blender/Excel 没有一家是"用户写宿主语言代码、动态加载进宿主进程"——它们嵌入脚本语言(Vimscript/Python/VBA);VS Code 扩展跑在独立 Extension Host 进程。业界的收敛答案是**语言无关的机制**:

- **进程外插件**:MCP server 是子进程,崩了 = 客户端收到错误结果,Agent 活着。MCP server 可以用 Java 写;
- **VM 沙箱**:pi 的容器化文档里,Gondolin 把工具调用路由进 micro-VM;
- **文档**:pi 的 skill 就是一个 `SKILL.md`(markdown),零代码——连 `harness/types.ts` 的注释都在引用 agentskills.io 规范。

修正后的结论:专业工具的长尾定制**不会**随成熟度消失(Vim 三十年了还在被写脚本),但**形态从"进程内代码"迁移到"协议 + 文档 + 沙箱"**。"Java 没法动态加载所以做不了 agent"站不住。

### "Java 做服务端,TS 做客户端,也能解决 UI"

这个方案 pi 其实**原样实现了**:`packages/protocol`(传输无关 CBOR)→ `packages/server`(持有 harness,单写者)→ `packages/client`(原子快照 + 增量事件,事件不重放,重连 = 新快照)。接缝刻意窄:进的是 `prompt/steer/abort/config`,出的是 `snapshot + events`。理论上 server 端换语言、TS 客户端不用改。

判断标准由此清晰:**拆分要为产品理由(远程会话、多客户端、常驻服务),不能为语言偏好**。本地 CLI 为用 Java 劈成两进程,是为语言偏好支付分布式成本——schema 双份、版本偏移、进程管理,本末倒置。

还有一个隐藏耦合:pi 的 hooks/extensions 跑在 harness 进程里,**协议解耦了进程,没解耦编程模型**——扩展 API 的语言仍由服务端语言决定。

### "TS 太自由,崩溃多、难定位;运行中的 Agent 不能被插件弄崩"

Java 工程师的核心批评。这次反驳要先承认机制真实:

- **TS 类型运行时不存在**:`javac` 失败就跑不起来(强制门禁);`tsc` 报错可以无视,错误延迟到运行时变成 `undefined is not a function`,且失败点离根因很远。pi 的注脚:工具参数执行前要过 `validateToolArguments`(`agent-loop.ts:618`)——正因为类型只在编译期,边界处必须用 schema 再验一次;
- **Node 进程模型**:未 catch 的 Promise rejection 默认杀死进程。JVM 里插件抛异常框架 catch 住线程还活着;Node 里插件异步抛一下整个 Agent 没了——**这是"插件弄崩宿主"担忧的技术内核,JVM 确实更强**;
- **模块系统脆弱**:ESM/CJS 互操作、循环依赖,"启动不起来"多源于此。

但 pi 是"纪律化 TS"的反例样本:`strict` + `erasableSyntaxOnly`(语言层面收敛自由度)、pre-commit 强制 `npm run check`(类型门禁从可选变强制)、以及 agent loop 的 **errors-as-data 契约**——`StreamFn` 禁止 throw(`types.ts:22-27`),工具异常全部转错误结果(`agent-loop.ts:701`)。

而"插件崩死宿主"的最终答案是**信任边界**,与语言无关:不可信代码不配进宿主进程——任何语言。JVM 的类加载器隔离也只是半措施(metaspace 泄漏照样拖死服务)。

**把五个理由按"成熟后剩多少"重排**:

| 理由 | 成熟后 |
|---|---|
| 动态加载用户代码 | **淘汰**——被 MCP/沙箱/技能文档取代 |
| SDK 生态路径依赖 | 衰减但长期存在 |
| 分发摩擦(JVM 启动 / GraalVM) | **仍在** |
| JSON 亲和(schema 即类型) | **仍在** |
| TUI 生态 | **仍在** |

### 顺带:未来的 Java agent 长什么样

不是本地 CLI,而是三种形态:①**企业 agent 服务**(Spring AI/LangChain4j 走向:多租户、lanes=工单/群聊、工具过 RBAC 与审计、人工审批步骤);②**持久化工作流引擎**——pi harness 的核心诉求(durable runs、records 重建、无部分结果、单写者)几乎就是 event sourcing + CQRS 的教科书定义,JVM 在这领域有 jBPM/Camunda/Temporal/Akka 二十年家底;③**"验证者宿主"**——把"AI 写的扩展跑在我这儿崩不了"做成卖点(sealed interface + 注解处理器生成 schema + GraalWasm 插件)。

预测:**个人开发者手里的 agent 是 TS,公司里的 agent 是 Java/.NET,彼此用 MCP 和协议缝合**——就像 Java 当年没赢编辑器和脚本工具,赢下了后端服务器。

## 三、讨论的真正收获:中间层

抛开了具体语言之后,问题变成:**如果人只操作高抽象层、AI 填充底层,Agent 应该贴近底层还是贴近人?**

答案是:这个问题缺了一层。"人写高层意图 → AI 填底层实现"里,生成那侧没问题,难的是**人怎么知道底下填的符合上面的意图**。未来人类真正"操作的代码"既不是顶层 prompt(太模糊)也不是底层实现(太丰富、不值得读),而是**中间层:schema、测试、不变量、策略、记录**——新的源码。

pi 的实际演化印证这个收敛:人给 agent 编程的接口是 `Skill`(markdown)、`PromptTemplate`(文本文件)、hooks(策略拦截);而 harness 的最大工程投入在 records(审计轨迹)、`drive: "manual"`(边界逐步检查)、events/hooks 分离。**对 agent 的"编程"已经从写代码退化成写文档和契约。**

于是 Agent 的位置:**不是在哪一层,是桥**——

- **接口那半,无限贴近人**(语言、技能、策略);
- **基座那半,扎在底层一步不让**——因为**不可逆性住在底层**(文件写入、进程执行、外部副作用),治理不了自己不拥有边界的效应。

一个推论:底层一旦 AI 生成、持续再生成,就成了**可抛弃资产**;不可再生的只剩三样——意图(对话)、历史(records)、契约(schema/测试)。pi 把会话树和 records 当一等公民持久化,甚至鼓励把 session 作为数据集发布:**durable 的单位正从 repo 移向 session + contract**。

## 四、中间层该用什么语言:表达 vs 强制

"中间层应该用 Java 那种抽象好的语言,DDD 明显 Java 更合适"——这个直觉对了一半。

**对的一半**:TS 是结构化类型,`OrderId` 和 `UserId` 都是 string 就能互换,DDD 的值对象纪律要靠 branding hack 模拟;Java 的 nominal typing 让领域边界写在类型身份里。访问修饰符、sealed interface、不可跳过的编译门禁——**TS 的抽象全是建议性的**(`any`/`as`/`@ts-ignore` 随时绕过)。

**修一半**:TS 不是不擅长"表达"抽象,是不擅长"**强制**"抽象。discriminated union(`Result = {ok:true,value} | {ok:false,error}`,pi 的 `harness/types.ts` 一行写完)配穷尽检查,Java 17 前根本表达不了。准确的说法:**TS 给你便宜的抽象,Java 给你承重的抽象**——中间层需要承重的。

最有说服力的旁证在 pi 自己身上:`erasableSyntaxOnly` **主动禁用了 TS 最自由的特性**(enum、参数属性、namespace 全不许),要求显式字段、显式构造赋值——**一个 TS 项目认真做耐久性系统时,把 TS 裁剪成了 Java 的形状**。语言决定的只是"强制"要花多少纪律成本去买;设计本身(回看 pi harness:records 即真相、replay 重建、snapshot+events 读模型、单写者一致性边界——标准 DDD 战术模式)是跨语言可迁移的。

另一个重要修正:"抽象代码天然贴合业务、好理解"——**不成立**。好理解的是"贴合业务",不是"抽象";2000 年代企业 Java 的 `AbstractFactoryProviderManager` 们抽象得极高且离业务十万八千里。而且业务逻辑分三处住:领域规则(该抽象)、流程编排(未必领域化)、**隐性知识**("2018 年前的保单向上取整是因为老系统 X"——这种 why 不在任何代码里,在提交历史和工单里)。这也是 pi 把 records 当一等公民的另一个理由:让"当时为什么这么执行"可回放,和让"业务是什么"可阅读,是两条互补的线。中间层与业务的贴合是**持续维护**出来的,没有完成时。

## 五、工作流的再构造:不是流水线,是验证驱动的循环

"自然语言 → 设计 → 领域设计 → 工程代码"(而不是直接到底层代码)——方向对,且已是现在(pi 的 357KB harness 设计文档支配实现;evals 独立成包)。但两个修正:

**修正一:不是瀑布 2.0**。生成方向免费之后,**反馈方向成为唯一瓶颈**——工程师的核心产出不是"做验证",是**造验证机器**(属性测试、不变量、evals、可回放记录)。pi 投资的全是验证机器:`drive: "manual"`、test matrix、records。**evals 正在变成新的 tests。**

**修正二:这条路前人失败过**。"设计 → 自动生成代码"就是 MDA/UML,死因:模型与现实失同步、生成器僵硬、再生成太贵。现在成立,因为三条全反转:LLM 消化模糊性、可从存量代码反向萃取、再生成廉价到漂移无所谓——**抽象层不再需要完备,只有契约需要精确**。押"契约的精确表达与机械验证",不押"大而全的统一建模语言"。

由此,未来工程师的角色精确化为三个(而非两个):

1. ~~把抽象翻译成代码~~——被自动化,就是传统意义上的初级工程师工作;
2. **验证者**,再劈两半:代码 vs 规格(机械化程度越来越高,会被 AI 吃)/ **规格 vs 真实需求**(不可机械化,项目失败大多死在这,人最不可替代的位置);
3. **接缝管理者**(容易被漏掉):决定哪些冻结成持久契约、哪些放任 AI 再生成。在再生成廉价的世界,**需要人维护的抽象必须挣得存在理由**——把"高抽象"当默认美德会造出新一代千层饼。

外加一个防御性保留:人不再写底层,但**读底层的能力要保留**——契约没拦住、行为与规格对不上时,总得有人能顺着 records 下到 AI 生成的代码里。像今天没人写汇编,但崩溃调试者能读。

## 六、终局反转:当人人都能组装 Agent

最后一层推论:如果组装 agent 变成大众活动(像做网页、做表格),那组装用的"语言"该是"最适合表达领域模型、贴合业务约束、新手易上手"的语言——大概率还是 TS 或 Java 之一?

**都不是。历史规律:品类走向大众时,表达层脱离通用语言,变成声明式的领域媒介**——数据库给开发者的是 SQL 不是 C;"人人编程"最成功的产物是 Excel 不是 Python;网页是 HTML。通用语言退到实现层。而 agent 品类特殊在:**LLM 第一次让自然语言可执行**——大众组装 agent 的语言就是自然语言本身。

但纯自然语言撑不住约束(歧义、不可穷尽检查、没法 diff review),所以成熟的组装面是**三明治**,pi 现在恰好就是这个形状:

```
意图层:自然语言(prompt、SKILL.md、工作流描述)   ← 大众写这个
约束层:schema、policy、验收标准(eval)           ← 懂业务的人写这个
逃生层:真正的代码(tools、extensions)            ← 工程师写这个
```

而"新手易上手"不是语言问题,是**反馈回路问题**:Excel 赢不是因为公式语法,是因为改一格立刻见结果、错了不炸、组件可拼。对应到 agent 组装:跑起来立刻看到执行轨迹(pi 的 events/records 就是"电子表格的单元格")、失败安全(errors-as-data、沙箱)、组件可发现可拼、agent 自己当老师。未来大众的 IDE 是**对话 + 执行轨迹**,不是编辑器。

## 代码定位(本文引用的 pi 位置)

| 位置 | 说明 |
|---|---|
| `packages/agent/src/types.ts:386` | `AgentTool`:typebox schema 一份定义两用(JSON Schema + TS 类型) |
| `packages/agent/src/types.ts:22-27` | `StreamFn` 契约:禁止 throw,失败编码进流 |
| `packages/agent/src/agent-loop.ts:701` | 工具异常 → 错误结果,循环不死 |
| `packages/agent/src/agent-loop.ts:618` | `validateToolArguments`:边界处的运行时校验 |
| `packages/agent/src/harness/types.ts` | `Skill`(markdown)/`Result`(discriminated union) |
| `packages/protocol` / `client` / `server` | 快照 + 事件流的窄接缝,单写者 |
| `packages/session-backends/sqlite-node/src/sqlite/storage/` | 表结构直接映射 harness 四元模型(entries/lanes/records/facts/writer-leases) |
| `tsconfig.base.json` + `.husky/pre-commit` | `erasableSyntaxOnly` + 强制 check:TS 裁剪成 Java 的形状 |
| `packages/agent/docs/harness-v2.md` | durable runs / lanes / records:事件溯源 + CQRS 的形状 |

## 总结

从"为什么 agent 都是 TS"出发,走完整场讨论:

1. TS 赢下的是**今天和最底层**:分发、JSON 亲和、生态引力、TUI——其中动态扩展那条理由会随成熟淘汰;
2. Java 的机会在**服务形态与企业**:持久化工作流引擎的家底、验证者宿主的定位;但本地 CLI 不会是它;
3. 真正持久的工程资产是**中间层**(契约 + 验证机器 + 记录)——它要的不是某门语言,是 **Java 级的强制力**;TS 能提供,但必须自我裁剪(pi 即是);
4. 未来的工作流不是"意图→代码"的流水线,是**验证驱动的循环**;人的不可替代位置在"规格 vs 真实需求"与接缝管理;
5. 当组装大众化,**组装语言不再是编程语言**:自然语言是接口,schema+eval 是类型系统,代码退到宿主层——

最初的语言之争,最终溶解成分层问题:

| 层 | 谁 | 用什么 |
|---|---|---|
| 组装 | 每个人 | 自然语言 + 文档 |
| 约束/领域 | 懂业务的人 | schema + 规则 + eval |
| 宿主/工具 | 工程师 | TS / Java / Rust,无所谓 |

**问"Agent 该用什么语言写",就像问"Excel 该用什么语言写"——问错了层面。该问的是:你的哪一层,给谁操作。**
