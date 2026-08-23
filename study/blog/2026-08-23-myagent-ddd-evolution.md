# 结构是被问出来的——myagent 的 DDD 演化全记录

> 关联模块:[../myagent/PLAN.md](../myagent/PLAN.md) | 日期:2026-08-23
>
> 前情:[一个 Agent 的最小逻辑](./2026-08-22-agent-minimal-loop.md) 拆解了 pi 的 agent-loop;本文记录次日动手用 Java 重推导时,项目结构如何在十五轮追问中从平铺包演化成 DDD 分层。主角不是代码,是**判据**。

## 问题 / 背景

用 Java 21 重实现 pi 的 agent 核心作为学习(方法:测试先行 + 与 pi 对照 diff)。初始脚手架是按技术角色平铺的四个包(`core / loop / mock / journal`),我熟悉 DDD,于是要求改成四层 + 共享层。此后的一天里,我对几乎每一个结构决定发起了追问——**每一个"没懂"都精确暴露一处结构噪音**。本文是这场对话的解剖:什么被推翻、什么被立住、什么死了又活。

## 演化时间线:十五轮,每轮立一条判据

| # | 挑战 | 立下的判据 | 结构变动 |
|---|---|---|---|
| 0 | 初始脚手架 | — | `core/loop/mock/journal` 平铺 |
| 1 | 改 DDD 四层,加 share 层 | share 准入规则:被 ≥2 层需要且非任何一层词汇 | 四层确立,**share 先不建** |
| 2 | 事件定义该在领域层吧? | **消费者拥有抽象**(循环发出事件→类型必须住 domain,否则依赖反向) | AgentEvent 定义入 domain |
| 3 | 为啥没有 model/ 包? | 按构造型分包(Vernon 式) | `model/event/service/repository/gateway` |
| 4 | 每个聚合根一个文件夹,事件随聚合 | 聚合地图:Session(树)+ Run(执行) | `model/session`、`model/run`、`model/shared` |
| 5 | message 不属于 session 吧? | **引用 ≠ 拥有**(Money ≠ OrderLine:离开根仍成立的是共享词汇) | `model/message` 词汇包独立 |
| 6 | 要公共聚合根基类 | 三条刻意决定:AgentEvent 不进 DomainEvent 继承树;去除只做 clearEvents;write-ahead 张力标注 | `AggregateRoot<ID>` + `DomainEvent` |
| 7 | message 咋没继承聚合根? | **聚合三问**:身份?不变量?事件?——三问皆无不配聚合 | message 维持词汇 |
| 8 | session 只有一个 ID 啊 | **已知状态给,待推导状态留白**(Session 的对话是已知的;Run 的状态是功课) | Session 持 messages+append(不变量用"没有方法"表达) |
| 9 | Run 无状态无事件,并进 session 呗? | **实体是被根持有、结束后仍在的东西**;run 留下的是痕迹不是对象——过程不是聚合 | Run 类删除,降级为过程 |
| 10 | RunId 被谁持有?啥聚合 run/ 包? | **叙事性内聚 ≠ 结构性内聚**:结构跟着现役引用走 | run/ 解散:边界词汇→gateway,AgentEvent→service,RunId 删除(零引用) |
| 11 | Context 是啥,systemPrompt 归谁? | **账本 vs 取景框**:Context 从 Session 单向装配,可裁剪;systemPrompt 是会话级配置 | Session 增 systemPrompt |
| 12 | Context 其实是 DTO 吧? | **哑在边界,活在域内** | `gateway/dto/` |
| 13 | ToolDescriptor 该在领域层吧? | **过边界三分法**:领域名词留 model(被端口引用≠归端口所有);端口方言/装配形状才进 dto | `model/tool/` |
| 14 | AgentLoop 咋定义咋写? | 测试先行:签名由 T1 逼出来 | T1/T2 红→绿,主轴上线 |

## 判据工具箱(本文真正想留下的东西)

结论会被下一轮推翻,判据越用越锋利。十条,按出场顺序:

1. **消费者拥有抽象**——契约归消费者的层次,不归提供者(StreamFn 在 domain 因为循环消费它;工具实现在 application 因为循环不关心谁实现)。
2. **聚合三问**——身份?要守的不变量?要记的事件?三问皆无,不配根。
3. **引用 ≠ 拥有**——Session 持有 `List<Message>` 但不拥有消息词汇;判据:成员离开根是否仍成立(OrderLine 离开 Order 无意义,Money 到处成立)。
4. **单消费者住进消费者,多消费者立词汇包**——Result 的两次搬家都由"移动触发器"驱动:第二个消费者出现之日即上移之时。
5. **结构性内聚 > 叙事性内聚**——"都在一次执行的故事里"是叙事,好听无约束;"谁在代码里持有它"才是结构。run/ 死于有叙事无结构。
6. **过边界三分法**——领域名词(Message/ToolDescriptor)留 model 由 Context 引用;端口方言(StreamEvent)、装配形状(Context)、provider 目录(Model)进 dto。
7. **端口说领域语言**——被端口引用 ≠ 归端口所有;AgentTool.descriptor() 返回 model 的类型,不是 gateway 的方言。
8. **哑在边界,活在域内**——DTO 集中在 dto 是对的;Session 若 DTO 化才是 anemic。
9. **反仪式**——唯一实现不预埋接口(JDK 的 Iterable 白捡就不造);不预建空壳(Run/RunId 之死);用移动触发器代替预判。
10. **死掉的结构也是产出**——每次死亡都编码了一条规则(见下)。

## 死亡名单(和它们教会我们的事)

| 结构 | 死因 | 遗产 |
|---|---|---|
| `share/` v1(装 Result) | 预判共享,无第二消费者 | "住在消费者处 + 触发器"规则 |
| `Run` 聚合 | 三问皆无;状态在服务、痕迹在 Session、记录在 journal | 过程 vs 聚合的判别;pi 的 run 持久形态是 records 不是对象 |
| `RunId` | 零引用(为阶段 3 预售) | 与 Run 类同一逻辑:无持有者不预建 |
| `run/` 包 | 叙事性内聚无结构胶水 | 结构跟着现役引用走 |
| `domain/event/` 包 | 事件各有其主:观察事件随发射者(service),账本事件将随聚合(session) | 两种事件的区分落进目录 |
| `Result` 的两次搬家 | 消费者变化 | 移动触发器的活教材 |

`share/` 死了两次活了一次:v1 装 Result(预判,拆)、v2 装 AggregateRoot(两个聚合真实继承,通过准入;Run 降级后变单消费者暂态,**如实标注**而非抹掉)。同一个包,三种命运——入场方式比存在本身重要。

## 终局与受力验证

```
domain/
├── model/          # message(词汇) tool(词汇) session(唯一聚合) shared(AggregateRoot+DomainEvent)
├── gateway/        # StreamFn、AgentTool、AssistantMessageStream(端口+机制)+ dto(载荷)
├── service/        # AgentLoop、AgentEvent、Result
└── repository/     # JournalRepository 契约(阶段3)
```

结构收口的标志不是"无可再调",而是**第一个消费者上线**:AgentLoop 落地当天——

- T1 首跑炸出 `AssistantMessageStream` 迭代器的真 bug(`next()` 内重复 `hasNext()`,对同一事件说法不一致):**机制类没有消费者时永远不知道对不对**;
- 一处死参数(`callLlm` 里未使用的 emit)被读者抓到而清理。

这和 TDD 是同一个道理的两次显形:**纸面推演有极限,引用与运行才是验收**。

## 与 pi 的对照(为什么两种都诚实)

pi 把全部类型平铺在一个 `types.ts`——它**拒绝在消费者出生前假装结构**。EventStream 具体类住 pi-ai 的 utils(边界库),Model 也在 pi-ai;AgentTool/AgentEvent 在 agent-core 的 types.ts——pi 的平铺里藏着同一条判据(领域 vs 边界),只是不靠目录表达。

我们的版本是"每个包都能指出现役持有者"的 DDD 表达——判据相同,表达加重。**学到的正是这一点:结构是判据的物化,判据相同,表达可以随语言与规模伸缩**——这也是[语言之争](./2026-08-22-agent-language-layering.md)里"中间层要 Java 级强制力"的微观注脚:这里的每条判据在 TS 里靠纪律,在 Java 里靠包、sealed、import 与编译器。

## 代码定位

| 位置 | 说明 |
|---|---|
| `study/myagent/PLAN.md` | 分层规则全文、判据的正式版本、T1-T16 |
| `domain/model/session/Session.java` | 唯一聚合:systemPrompt + 只增对话(不变量用"没有方法"表达) |
| `domain/model/shared/AggregateRoot.java` | 范型主键 + append-only 事件 + clearEvents |
| `domain/gateway/` | 端口 + 机制 + dto 三分 |
| `domain/service/AgentLoop.java` | 主轴(参考实现);Javadoc 记录与 pi 的既定 diff |
| 各 `package-info.java` | 判据的安放处——每条规则写在它守卫的包门口 |

## 总结

1. **结构是被问出来的**:十五轮追问,每轮"没懂"都指向一处判据缺失;把"没懂"当测试跑,结构就收敛。
2. **判据先于结论**:结论(Run 是聚合)会被推翻;判据(三问)不会,它继续裁决下一个候选。
3. **死亡是产出**:share/ 的三起三落、run/ 的解散、RunId 的删除——每个死掉的结构都在 package-info 里留下了它教的那一课。
4. **收口靠受力**:AgentLoop 一落地,bug 和死参数自己浮上来——结构的最終验收永远是第一个消费者。

下一篇大概率是:T3-T6 功课 + 与 `agent-loop.ts` 的正式 diff——那时"判据"要让位于"行为"。
