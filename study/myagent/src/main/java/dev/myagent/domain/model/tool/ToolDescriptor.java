package dev.myagent.domain.model.tool;

/**
 * 工具描述子 —— 给模型与 provider 看的全部:name / description / 参数 schema。
 * 请求(Context)持有的是它;执行能力在 gateway 的 AgentTool 端口。
 * 两拨消费者两份契约:provider 广播工具只要描述,循环执行只要能力(pi 把两者焊在
 * AgentTool 一个接口里,types.ts:386 —— 此拆分即一个 diff 记录)。
 */
public record ToolDescriptor(String name, String description, ToolSchema parameters) {}
