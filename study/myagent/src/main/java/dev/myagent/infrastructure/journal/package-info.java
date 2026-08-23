/**
 * Journal 文件适配器(阶段 3):JSONL 逐行追加、截断容忍(末行写坏即回退上一完整边界)。
 * Jackson 序列化住在这 —— domain 不许见 Jackson。对应 pi 的 harness/session/jsonl.ts。
 */
package dev.myagent.infrastructure.journal;
