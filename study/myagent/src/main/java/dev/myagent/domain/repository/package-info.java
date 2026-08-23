/**
 * 仓储端口 —— 阶段 3 落地接口定义(Record 领域事件类型同批出现,现在不预埋)。
 *
 * <p>JournalRepository 的形状:append-only 追加 + 全量读取(replay 用)。
 *
 * <p>失败契约与 {@link dev.myagent.domain.gateway.StreamFn} 相反:磁盘级故障【允许抛】——
 * 崩溃边界定义在此端口。原则:预期失败 = 数据(StreamFn),进程级故障 = 异常(本端口)。
 * 对应 pi harness/session 的 jsonl 后端与 sqlite-node storage/。
 */
package dev.myagent.domain.repository;
