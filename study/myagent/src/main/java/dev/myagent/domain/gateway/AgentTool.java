package dev.myagent.domain.gateway;

import dev.myagent.domain.model.tool.AgentToolResult;
import dev.myagent.domain.model.tool.ToolDescriptor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工具能力端口 —— 循环的注入点(对应 pi 的 AgentTool,packages/agent/src/types.ts:386)。
 * 描述与能力分离:描述子(ToolDescriptor)是 run 的请求词汇,进 Context;
 * 本端口只回答"给了 toolCallId 和参数,执行出什么"。
 *
 * <p>归属依据:消费者拥有抽象 —— 调用 execute 的是领域循环,故接口在领域
 * (实现在 application / 测试,注册清单也是应用层的事 —— 名词是领域的,清单是应用的)。
 *
 * <p>失败契约:execute 允许抛异常 —— 由循环捕获并转为 isError 的 ToolResultMessage;
 * errors-as-data 的转换发生在循环边界,不在工具内部(pi 同此)。
 * abort 信号与 onUpdate 进度回调在阶段 2 引入。
 */
public interface AgentTool {

	ToolDescriptor descriptor();

	CompletableFuture<AgentToolResult> execute(String toolCallId, Map<String, Object> arguments);
}
