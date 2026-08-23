/**
 * 阶段 3:迷你持久化 harness —— append-only journal(JSONL)、reducer(记录折叠成状态)、
 * 崩溃恢复、单写者文件锁(WriterLease)。
 *
 * <p>对应 pi 的 {@code harness/session/}(jsonl / state / reducer)与
 * {@code session-backends/sqlite-node/.../storage/writer-leases.ts}。
 * 核心验收:replay(journal) == live 状态;崩溃后从最后持久边界恢复,无部分结果可观测。
 */
package dev.myagent.journal;
