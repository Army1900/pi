package dev.myagent.domain.model.message;

/** 一次调用的 token 用量。pi 版本还含 cost 结构,阶段 1 先记 token 四项。 */
public record Usage(long input, long output, long cacheRead, long cacheWrite) {
	public static final Usage ZERO = new Usage(0, 0, 0, 0);
}
