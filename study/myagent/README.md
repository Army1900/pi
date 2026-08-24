# myagent

用 Java 重实现 pi([pi-mono](../../../)) 的 agent 核心,以此学习 agent 设计。**不是翻译,是重推导**:读 pi 的一个切片 → 关上源码写测试 → 用 Java 重新实现 → 与 pi 对照 diff → 把 diff 写回 study 笔记。

- 实施计划:[PLAN.md](./PLAN.md)(三阶段课程表 + pi 文件映射 + 验收标准)
- 学习笔记:回到 [`study/`](../) 各模块的笔记区,尤其是 [04 agent-core](../04-pi-agent-core-基础.md) 与 [05 harness](../05-AgentHarness-持久化.md)
- 背景文章:[一个 Agent 的最小逻辑](../blog/2026-08-22-agent-minimal-loop.md)、[Agent 该用什么语言写?](../blog/2026-08-22-agent-language-layering.md)

## 环境(已于 2026-08-23 配置完成)

- **JDK 21**:`brew install openjdk@21`(21.0.12.1,装在 `/opt/homebrew/opt/openjdk@21`,keg-only 不进默认 PATH)
- **Maven 3.9.16**:brew 安装因本机 brew tap 陈旧 + bottle 镜像缺失而失败(`load_tab` 报错,清缓存无效),改用 Apache 二进制包直装:`~/tools/apache-maven-3.9.16`
- **`~/.zshrc`** 已追加:`JAVA_HOME` 指向 openjdk@21,PATH 加入 `$JAVA_HOME/bin` 和 maven bin
- **`~/.m2/settings.xml`** 已配置阿里云中央仓库镜像(国内拉依赖必需)
- ~~已知遗留问题:本机 `brew update` 因 aliyun 镜像卡死长期失败~~ **已修复(2026-08-23)**:brew remote 切回官方 GitHub 源,5.1.0 → 6.0.18 追平;阿里云 bottle 镜像因缺新包已在 `~/.zshrc` 停用

## 日常命令

```bash
mvn test                              # 全部测试
mvn test -Dtest=AgentLoopTest         # 单个测试类
mvn test -Dtest='AgentLoopTest#should*'  # 单个方法
mvn -q compile exec:java              # 跑 Main(真模型 REPL,见下)
```

## 跑起来(真模型)

```bash
cp myagent.properties.example myagent.properties   # 编辑:api(格式)/baseUrl(端点)/model —— 格式与端点正交
export DEEPSEEK_API_KEY=sk-...                     # 或 ANTHROPIC/OPENAI_API_KEY;apiKey=env:... 引用它
mvn -q compile exec:java
```

配置要点:apiKey 支持 `env:NAME` 引用(密钥不落明文,配置文件无秘密);baseUrl 省略走 provider 默认;单测始终零网络零密钥。

## 约定

- **测试先行**:每个阶段先移植 pi 对应测试(`MockAssistantStream` 那套),红 → 实现到绿。
- **errors-as-data**:工具失败、流失败一律表达为数据(`isError` 的结果/`stopReason: "error"`),不允许异常杀死循环——除非是阶段 3 定义的"崩溃"。
- **不用 Spring**:这是学习核心,DI 手写构造函数,保持每条依赖边可见。
- **刻意保留的 diff**:Java 与 TS 表达力差异导致的取舍(sealed interface 拼 discriminated union 等)是学习目标本身,记进 PLAN.md 的「diff 记录」。

## 结构(DDD 四层,按阶段生长)

```
src/main/java/dev/myagent/
├── domain/            # 领域层:只依赖 JDK
│   ├── model/         # 一个聚合 + 词汇 + 公共抽象(契约在 package-info):
│   │   ├── message/   #   共享词汇:Message、Content、StopReason、Usage
│   │   ├── tool/      #   工具词汇(领域名词过边界):ToolDescriptor、ToolSchema、AgentToolResult
│   │   ├── session/   #   聚合根 Session:对话消息(只增)+ [阶段3: entry/lanes/facts/Record(RunId 随记录回归)]
│   │   └── shared/    #   公共抽象:AggregateRoot(范型主键+事件)、DomainEvent(账本事件标记)
│   ├── gateway/       #   端口 + 流机制 ✅:StreamFn、AgentTool、AssistantMessageStream
│   │   └── dto/       #   端口载荷(非领域词汇):Context、Model、StreamEvent
│   ├── service/       #   AgentLoop/ToolExecutor(功课);词汇:AgentEvent、Result
│   └── repository/    #   端口:JournalRepository(阶段3,失败契约已立)
├── application/       # 应用层:只依赖 domain —— Agent 用例门面、事件分发、恢复(阶段2)
└── infrastructure/    # 基础设施:适配器 —— llm ✅(MockStreamFn/RealStreamFn)、tools ✅(read/ls/bash,本地能力)、journal(阶段3)、lease(阶段3)
```

依赖铁律:domain 零外部依赖;application 只 import domain;infrastructure 只实现 domain 端口;组装在组合根(测试 setup / Main)手写构造函数。详见 [PLAN.md](./PLAN.md) 的「分层规则」。
