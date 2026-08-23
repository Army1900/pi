package dev.myagent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 阶段 0 验收:工具链可用。mvn test 变绿即可开工,之后进入 PLAN.md 阶段 1。
 */
class SmokeTest {

	@Test
	void toolchainWorks() {
		assertTrue(Runtime.version().feature() >= 21, "需要 Java 21+(虚拟线程在阶段 1/3 会用到)");
	}
}
