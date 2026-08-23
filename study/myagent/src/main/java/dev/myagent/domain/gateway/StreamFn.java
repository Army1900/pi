package dev.myagent.domain.gateway;

import dev.myagent.domain.gateway.dto.Context;
import dev.myagent.domain.gateway.dto.Model;

/**
 * LLM 端口 —— 对应 pi 的 StreamFn(packages/agent/src/types.ts:28-32)。
 *
 * <p>失败契约(领域不变量,钉死在端口上):实现禁止以异常表达"模型调用失败"。
 * 请求级失败必须编码为终态消息:stopReason = ERROR(或 ABORTED)+ errorMessage。
 * 这是"循环永不死于适配器"的强制机制 —— mock 再假,也得守真 provider 的规矩(T4 验收)。
 *
 * <p>阶段 2 扩展:options(apiKey、abort signal 等,对应 pi 的 SimpleStreamOptions)。
 */
public interface StreamFn {

	AssistantMessageStream stream(Model model, Context context);
}
