package dev.myagent.domain.service;

/**
 * 可失败操作的领域结果(errors-as-data 的词汇根,对应 pi harness 的 Result)。
 * 预期失败走 {@link Err} 并作为数据流转,不用异常表达。
 */
public sealed interface Result<T, E> {

	record Ok<T, E>(T value) implements Result<T, E> {}

	record Err<T, E>(E error) implements Result<T, E> {}

	static <T, E> Result<T, E> ok(T value) {
		return new Ok<>(value);
	}

	static <T, E> Result<T, E> err(E error) {
		return new Err<>(error);
	}

	default boolean isOk() {
		return this instanceof Ok<T, E>;
	}
}
