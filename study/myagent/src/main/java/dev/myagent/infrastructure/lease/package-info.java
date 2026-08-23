/**
 * 单写者租约适配器(阶段 3):FileChannel.tryLock 文件锁,拿不到即拒绝启动 —— 单写者的强制点。
 * 对应 pi 的 sqlite-node storage/writer-leases.ts。
 */
package dev.myagent.infrastructure.lease;
