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
- 已知遗留问题(与本学习项目无关):本机 `brew update` 因 aliyun brew.git 镜像拉取卡死而长期失败;如需修复 brew,可换 `HOMEBREW_BREW_GIT_REMOTE` 或临时直连官方源

## 日常命令

```bash
mvn test                              # 全部测试
mvn test -Dtest=AgentLoopTest         # 单个测试类
mvn test -Dtest='AgentLoopTest#should*'  # 单个方法
mvn -q package                        # 打包(暂时用不到)
```

## 约定

- **测试先行**:每个阶段先移植 pi 对应测试(`MockAssistantStream` 那套),红 → 实现到绿。
- **errors-as-data**:工具失败、流失败一律表达为数据(`isError` 的结果/`stopReason: "error"`),不允许异常杀死循环——除非是阶段 3 定义的"崩溃"。
- **不用 Spring**:这是学习核心,DI 手写构造函数,保持每条依赖边可见。
- **刻意保留的 diff**:Java 与 TS 表达力差异导致的取舍(sealed interface 拼 discriminated union 等)是学习目标本身,记进 PLAN.md 的「diff 记录」。

## 结构(按阶段生长)

```
src/main/java/dev/myagent/
├── core/     # 阶段1:消息类型、AgentTool、StreamFn 契约、事件
├── loop/     # 阶段1-2:runLoop、控制面(steering/followUp/abort/hooks)
├── mock/     # 测试配套:MockStreamFn、假 Model(对应 pi 的 MockAssistantStream)
└── journal/  # 阶段3:append-only 记录、replay、恢复、单写者锁
```
