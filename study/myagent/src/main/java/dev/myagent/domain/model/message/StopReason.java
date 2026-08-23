package dev.myagent.domain.model.message;

/**
 * 停止原因。线格式(pi)为 "stop" | "length" | "error" | "aborted";
 * 枚举名与线格式的映射在阶段 3(序列化)时定。
 */
public enum StopReason { STOP, LENGTH, ERROR, ABORTED }
