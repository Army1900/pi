package dev.myagent.domain.gateway;

import dev.myagent.domain.gateway.dto.StreamEvent;
import dev.myagent.domain.model.message.AssistantMessage;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * StreamFn 端口的返回类型:可迭代的增量流 + 终态结果。
 * JDK-only 实现(BlockingQueue + CompletableFuture),阻塞消费对虚拟线程友好 ——
 * 这是"TS 异步迭代器 → Java 什么"的答案(PLAN 阶段 1 diff 点 #3)。
 *
 * <p>契约:{@link StreamEvent.Completed} 是最后一个事件;终态只能经 {@link #complete} 进入。
 */
public final class AssistantMessageStream implements Iterable<StreamEvent> {

	private final BlockingQueue<StreamEvent> events = new LinkedBlockingQueue<>();
	private final CompletableFuture<AssistantMessage> finalMessage = new CompletableFuture<>();

	/** 追加一个非终态增量。 */
	public void push(StreamEvent event) {
		if (event instanceof StreamEvent.Completed) {
			throw new IllegalArgumentException("终态事件只能通过 complete() 进入");
		}
		events.add(event);
	}

	/** 以最终消息终止流(成功与失败皆是:失败 = stopReason ERROR/ABORTED 的消息)。 */
	public void complete(AssistantMessage message) {
		events.add(new StreamEvent.Completed(message));
		finalMessage.complete(message);
	}

	/** 阻塞取最终消息;流未被 complete 前调用会等待。 */
	public AssistantMessage result() {
		return finalMessage.join();
	}

	@Override
	public Iterator<StreamEvent> iterator() {
		return new Iterator<>() {
			private StreamEvent buffered;
			private boolean finished;

			@Override
			public boolean hasNext() {
				if (finished) {
					return false;
				}
				if (buffered == null) {
					try {
						buffered = events.take();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						finished = true;
						return false;
					}
				}
				return true; // buffered 里有一个未消费的事件(包括终态 Completed)
			}

			@Override
			public StreamEvent next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				StreamEvent current = buffered;
				buffered = null;
				if (current instanceof StreamEvent.Completed) {
					finished = true; // 交出终态之后才结束 —— hasNext/next 对同一事件必须说法一致
				}
				return current;
			}
		};
	}
}
