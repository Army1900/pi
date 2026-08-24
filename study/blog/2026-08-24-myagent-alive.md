# 让 Agent 活起来——工具、双方言与分片拼装的一天

> 关联模块:[../myagent/PLAN.md](../myagent/PLAN.md) | 日期:2026-08-24
>
> 前情:[结构是被问出来的](./2026-08-23-myagent-ddd-evolution.md) 完成了 DDD 骨架与判据体系。这一天让结构"活"了:三个真工具、两种线格式、真模型接线——以及三个被测试和现实抓住的 bug。代码为主,教训为辅。

## 一、工具三件套:read / ls / bash

### 1.1 手写校验——typebox 缺位之"痛"的实体

pi 用 typebox 让一份 schema 同时产出 JSON Schema 和 TS 类型;Java 没有这回事,校验只能自己写。三个工具共用的 `ToolArgs`:

```java
// infrastructure/tools/ToolArgs.java
static String requireString(Map<String, Object> arguments, String name) {
    Object value = arguments.get(name);
    if (!(value instanceof String string) || string.isBlank()) {
        throw new IllegalArgumentException("缺少必填参数 " + name + "(string)");
    }
    return string;
}
```

**失败契约**照旧:校验失败以异常表达,由循环转成 `isError` 的 ToolResultMessage——工具不自己编码错误。这就是 PLAN diff 点 #2 的"痛"落地:写了三遍 `instanceof` 检查后,你会真心理解 pi 为什么选 typebox。

### 1.2 ReadTool:截断与受众分离

两个关注点刻意分开——读是读,截断是截断(pi 用独立的 `truncate.ts` 回答同一问题):

```java
// ReadTool.read() 的截断段
boolean truncated = lines.size() > maxLines;
List<Content.TextContent> content = new ArrayList<>(
        lines.subList(0, truncated ? maxLines : lines.size()).stream()
                .map(Content.TextContent::new).toList());
if (truncated) {
    content.add(new Content.TextContent("…(已截断:显示 " + maxLines + " / 共 " + lines.size() + " 行)"));
}

return new AgentToolResult(
        content,                                                    // 给模型
        Map.of("path", path.toString(), "lines", lines.size(),
               "truncated", truncated),                             // 给 UI
        Optional.empty());
```

读一个 10MB 文件不截断,上下文窗口当场爆炸——这不是装饰,是生存技能。

### 1.3 BashTool:今天最好的 bug——超时形同虚设

初版"先读后等":

```java
// ✗ 初版:超时永远不会触发
output = readCapped(process.getInputStream(), MAX);   // 阻塞到进程退出才返回
finished = process.waitFor(timeoutMs, MILLISECONDS); // 到这里进程早就结束了
```

测试 `bashTimeoutKillsProcess` 跑了**整整 5 秒**才暴露——`sleep 5` + `timeoutMs=200` 居然完整执行了。修法是**读与等并发**:

```java
// ✓ 正确:输出在独立虚拟线程排空,waitFor 的超时才真正生效
CompletableFuture<String> output = new CompletableFuture<>();
Thread.ofVirtual().name("bash-output").start(() -> {
    try { output.complete(readCapped(process.getInputStream(), MAX_OUTPUT_CHARS)); }
    catch (Throwable t) { output.completeExceptionally(t); }
});
boolean finished = process.waitFor(timeoutMs, MILLISECONDS);   // 现在这行真的在计时
if (!finished) {
    process.destroyForcibly();
    throw new IllegalStateException("命令超时(" + timeoutMs + "ms),已终止: " + command);
}
```

修复后同一测试 **0.265 秒**。教训两层:①进程输出必须被持续消费(否则管道缓冲区满 → 子进程写阻塞 → 假死);②**凡是"等待 X"的代码,检查一下 X 是不是真的在被并发地等待**。

### 1.4 一次被抓住的结构错误:工具住哪

我先把工具放进了 `application/tools/`,理由是"外部系统(网络)归 infra、本地能力(文件/进程)归 application"。被一句"tools 不是基础设施的实现吗"问倒了——**那条线是软的**:文件系统和进程子系统与 HTTP 服务同属"应用边界之外的机制",三个工具都是 domain 端口的适配器。修正:

```
infrastructure/
├── llm/     # 网络适配器(对面:HTTP 服务)
└── tools/   # 本地能力适配器(对面:OS 文件系统 / 进程)← 搬家
```

根因是**用类比替代了判据**:pi 把工具放 `core/tools`(它不分层,实现和装配混放),我照搬时没拆。正确拆分:**实现 = 适配器 = infra;清单/装配 = application/组合根**——application 持 `List<AgentTool>`(domain 类型),由组合根注入,永不 import infra。

## 二、配置:格式与端点正交

### 2.1 env: 引用——密钥卫生

```java
// AgentConfig:未设置的环境变量是启动错误,不是静默空值
private static String resolve(String value, Function<String, String> env) {
    if (value != null && value.startsWith("env:")) {
        String name = value.substring(4).trim();
        String resolved = env.apply(name);
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalStateException("配置引用的环境变量未设置: " + name);
        }
        return resolved;
    }
    return value;
}
```

配置文件本身无秘密、可提交;`env` 查找做成可注入的 `Function`,测试零环境操纵。

### 2.2 "支持两家"的正解:两种格式,不是两个域名

我最初把字段叫 `provider` 并配了官方域名缺省,被纠正:**线格式和端点是正交的**。DeepSeek、通义、Kimi、vLLM、one-api 全说 OpenAI 格式,域名各不相同:

```properties
api=openai                          # 接口【格式】:chat-completions 兼容
baseUrl=https://api.deepseek.com    # 端【点】:任意兼容网关
model=deepseek-chat
apiKey=env:DEEPSEEK_API_KEY
```

一个 `OpenAiStreamFn` 通吃所有说这套方言的端点——**"支持两种接口格式"比"支持两家厂商"多一个维度的自由,而且零额外代码**。

## 三、双方言适配器:差异是教材

同一段对话,两家的请求体长这样(节选):

```jsonc
// OpenAI:system 是消息,工具叫 function/parameters,结果走 role:tool
{"model":"...","messages":[
   {"role":"system","content":"..."},
   {"role":"assistant","tool_calls":[{"id":"c1","type":"function",
      "function":{"name":"read","arguments":"{\"path\":\"...\"}"}}]},
   {"role":"tool","tool_call_id":"c1","content":"文件内容"}],
 "tools":[{"type":"function","function":{"name":"read","parameters":{...}}}]}

// Anthropic:system 是独立字段,max_tokens 必填,工具调用是内容块,结果以 role:user 回传
{"model":"...","max_tokens":4096,"system":"...",
 "messages":[
   {"role":"assistant","content":[{"type":"tool_use","id":"c1","name":"read","input":{...}}]},
   {"role":"user","content":[{"type":"tool_result","tool_use_id":"c1","content":"文件内容"}]}],
 "tools":[{"name":"read","description":"...","input_schema":{...}}]}
```

流式事件同样各异:OpenAI 是 `data: {...choices[0].delta...}` + `[DONE]` 哨兵;Anthropic 按 `type` 路由(`content_block_delta`/`message_stop`),没有哨兵。**这些差异全部被压在适配器肚子里——领域词汇到端口为止,"端口说领域语言、方言归适配器"的完整闭环。**

## 四、组合根:结构合流的房间

```java
// Main.java —— 全项目唯一"什么都知道"的地方
StreamFn llm = switch (config.api()) {
    case "openai"    -> new OpenAiStreamFn(config.baseUrl(), config.apiKey());
    case "anthropic" -> new AnthropicStreamFn(config.baseUrl(), config.apiKey());
    default -> throw new IllegalStateException("未知 api 格式: " + config.api());
};
Session session = new Session(new SessionId("main"), "你是一个运行在终端里的助手…");
AgentLoop loop = new AgentLoop(model, llm, List.of(new ReadTool(), new LsTool(), new BashTool()));
```

几天来的全部概念在此合流:配置 → 选适配器 → 装配 → REPL。domain 不认识任何适配器,依赖边肉眼可见,不引 Spring。

## 五、隐藏缺口:会聊天,不会动手

"接线完成"后自查,发现一个 mock 掩盖的缺口:**两个真适配器只拼了文本增量,工具调用的流式分片被丢弃**。后果链:

```
真模型决定调工具 → 适配器丢掉工具增量 → 最终消息里没有 ToolCall 块
→ AgentLoop 看不到工具请求 → 模型"只用嘴说,从不动手"
```

Mock 里工具往返是通的,因为剧本直接造 `Content.ToolCall`——**mock 验证了管线,掩盖了半截**。修法的关键观察:两家最深的方言差异剥掉外壳是**同一个机制**:

```java
// ToolCallAssembler:两家共享的拼装器
void start(int index, String id, String name) { ... }          // 按 index 注册身份
void appendArguments(int index, String fragment) { ... }       // 追加参数分片
List<Content.ToolCall> finish(ObjectMapper json) { ... }       // 拼完整体 → JSON 解析

// OpenAI 接线(6 行):delta.tool_calls[] → 首片 id/name,后续 arguments 分片
// Anthropic 接线(6 行):content_block_start 给身份,input_json_delta 给分片
```

工具参数是**分片到达**的(`{"pa` + `th": "/tm` + `p/a.txt"}`)——这正是它当初被从略的原因,也是"编译通过"和"真的能用"之间的最后一公里。拼装器 5 个脱网单测(两种方言形状、并行 index 排序、空参数、坏 JSON 拒绝),真验收是真模型下问一句"看看当前目录有什么"。

## 六、判据清单的今日增补

延续[昨天](./2026-08-23-myagent-ddd-evolution.md)的工具箱,今天加了四条:

1. **定义跟着构造者走,读者不算数**(AgentEvent 归属;阶段 2 外壳开始合成失败事件时触发迁移);
2. **实现 ≠ 清单**:适配器住 infra 无论对面是网络还是 OS;装配归 application;
3. **格式与端点正交**:"支持 N 家"的正解是支持 N 种格式,域名只是缺省;
4. **凡是等待,检查并发**:读与等、生产与消费,串行化的等待是超时的坟墓。

以及一条元判据的第三次验证:**类比是线索不是答案**——pi 的 `core/tools` 位置搬进分层世界前,必须重新过自己的尺子。

## 代码定位

| 文件 | 本日角色 |
|---|---|
| `infrastructure/tools/ToolArgs、ReadTool、LsTool、BashTool` | 工具三件套 + 手写校验 |
| `infrastructure/config/AgentConfig` | env: 密钥卫生、格式/端点正交 |
| `infrastructure/llm/OpenAiStreamFn、AnthropicStreamFn` | 双方言适配器 |
| `infrastructure/llm/ToolCallAssembler` | 分片拼装器(两家归约) |
| `Main.java` | 组合根 + REPL |
| `src/test/.../BashTool 超时测试、ToolCallAssemblerTest、RealToolsIntegrationTest` | 三个 bug/缺口的第一现场 |

## 总结

1. **三个被抓住的 bug 是今天最贵的产出**:流的迭代器(`next()` 重复问 `hasNext()`)、bash 超时失效(先读后等)、工具分片丢弃(mock 掩盖)——分别被第一个消费者、一个 5 秒的测试、一次"完成了吗"的自问抓住。**结构收口的标志是消费者上线,收口的收益是 bug 自己浮上来**。
2. **方言的归宿是薄翻译层**:两家最深的差异归约成一个 60 行的拼装器加两处 6 行接线——差异被消化,而不是被复制。
3. **"活"的判定标准**:REPL 里真模型调真工具读真磁盘。到此,最小 AgentLoop 的骨架、验证大半完成;剩 T6 截断全败与阶段 2 的控制面——明天的事。
