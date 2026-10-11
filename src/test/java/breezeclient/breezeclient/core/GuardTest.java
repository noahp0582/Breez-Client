package breezeclient.breezeclient.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GuardTest {
	@Test
	void runSwallowsExceptionsAndCountsThem() {
		int before = Guard.failures();
		AtomicInteger after = new AtomicInteger();
		Guard.run("test run", () -> { throw new IllegalStateException("boom"); });
		Guard.run("test run", after::incrementAndGet);
		assertEquals(before + 1, Guard.failures());
		assertEquals(1, after.get());
	}

	@Test
	void callReturnsTheValueOrTheFallback() {
		assertEquals("ok", Guard.call("test call", "fallback", () -> "ok"));
		assertEquals("fallback", Guard.call("test call", "fallback", () -> { throw new IndexOutOfBoundsException(); }));
	}

	@Test
	void stackOverflowIsCaught() {
		assertEquals("fallback", Guard.call("test overflow", "fallback", () -> { throw new StackOverflowError(); }));
	}

	@Test
	void otherErrorsStayFatal() {
		assertThrows(OutOfMemoryError.class, () -> Guard.run("test oom", () -> { throw new OutOfMemoryError(); }));
		assertThrows(OutOfMemoryError.class, () -> Guard.call("test oom", "x", () -> { throw new OutOfMemoryError(); }));
	}
}
