package dev.myagent.domain.model.session;

import dev.myagent.domain.model.message.Message;
import dev.myagent.domain.model.shared.AggregateRoot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 聚合根:Session —— 消息树(阶段 1 形态:平铺列表;阶段 3 长出 entry 的 parentId / 分支)。
 *
 * <p>对话的持有者:application 的 Agent 把 message_end 事件归约成 {@link #append}
 * (镜像 pi 的 processEvents → state.messages)。持有 Message 的引用,不拥有其词汇。
 *
 * <p>不变量"只增"用<b>没有方法</b>表达:不提供删除 / 修改 / 清空对话的 API。
 * 单写者 = 聚合边界(阶段 3 由 WriterLease 强制)。
 *
 * <p>事件机制暂未点亮:append 不 registerEvent —— 没有 journal 的 event 只是内存泄漏;
 * 第一个 DomainEvent 实现者(MessageAppended 等 Record 家族)随阶段 3 到来,
 * append 之时即 registerEvent 之刻。
 */
public final class Session extends AggregateRoot<SessionId> {

	private final String systemPrompt;
	private final List<Message> messages = new ArrayList<>();

	/**
	 * @param systemPrompt 会话级系统提示(pi:AgentState.systemPrompt;harness 的 lane_config 前身)。
	 *                      Context(取景框)只是把它捎给端口的信使,不拥有它。
	 */
	public Session(SessionId id, String systemPrompt) {
		super(id);
		this.systemPrompt = Objects.requireNonNull(systemPrompt, "系统提示不能为空(可为空串)");
	}

	/** 会话级系统提示 —— 装配 Context 时从这里取。 */
	public String systemPrompt() {
		return systemPrompt;
	}

	/** 追加一条消息(树只增)。 */
	public void append(Message message) {
		messages.add(Objects.requireNonNull(message, "消息不能为空"));
	}

	/** 当前对话(不可变视图)。 */
	public List<Message> messages() {
		return List.copyOf(messages);
	}
}
