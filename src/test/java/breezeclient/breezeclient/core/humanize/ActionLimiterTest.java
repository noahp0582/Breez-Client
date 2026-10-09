package breezeclient.breezeclient.core.humanize;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ActionLimiterTest {
	@Test
	void neverBelowMin() {
		ActionLimiter l = new ActionLimiter(new Random(1));
		for (int i = 0; i < 5000; i++) assertTrue(l.nextDelayTicks(2, 5, 1) >= 1);
	}

	@Test
	void meanWithinFivePercent() {
		ActionLimiter l = new ActionLimiter(new Random(2));
		double sum = 0;
		int n = 10000;
		for (int i = 0; i < n; i++) sum += l.nextDelayTicks(6, 1.5, 1);
		assertEquals(6.0, sum / n, 0.3);
	}

	@Test
	void notConstant() {
		ActionLimiter l = new ActionLimiter(new Random(3));
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < 200; i++) seen.add(l.nextDelayTicks(6, 1.5, 1));
		assertTrue(seen.size() > 3);
	}

	@Test
	void betweenInclusive() {
		ActionLimiter l = new ActionLimiter(new Random(4));
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < 500; i++) seen.add(l.between(3, 5));
		assertEquals(Set.of(3, 4, 5), seen);
	}
}
