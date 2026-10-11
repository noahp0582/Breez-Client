package breezeclient.breezeclient.core.tunnel;

import breezeclient.breezeclient.core.tunnel.TunnelChain.Run;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TunnelChainTest {
	@Test
	void runsThatTouchAtAChunkBorderBecomeOneTunnel() {
		var chains = TunnelChain.merge(List.of(new Run(0, 10, 5, true, 16), new Run(16, 10, 5, true, 16), new Run(32, 10, 5, true, 9)), 1);
		assertEquals(1, chains.size());
		var c = chains.get(0);
		assertEquals(41, c.length());
		assertEquals(0, c.x1());
		assertEquals(40, c.x2());
		assertEquals(5, c.z1());
		assertEquals(3, c.runs());
	}

	@Test
	void aGapLargerThanAllowedSplitsTheTunnel() {
		var chains = TunnelChain.merge(List.of(new Run(0, 10, 5, true, 10), new Run(14, 10, 5, true, 10)), 1);
		assertEquals(2, chains.size());
		assertEquals(1, TunnelChain.merge(List.of(new Run(0, 10, 5, true, 10), new Run(14, 10, 5, true, 10)), 4).size());
	}

	@Test
	void differentLinesAreNeverJoined() {
		assertEquals(2, TunnelChain.merge(List.of(new Run(0, 10, 5, true, 16), new Run(16, 10, 6, true, 16)), 1).size()); // other z
		assertEquals(2, TunnelChain.merge(List.of(new Run(0, 10, 5, true, 16), new Run(16, 12, 5, true, 16)), 1).size()); // other y
		assertEquals(2, TunnelChain.merge(List.of(new Run(0, 10, 5, true, 16), new Run(0, 10, 5, false, 16)), 1).size()); // other axis
	}

	@Test
	void alongZRunsAreMergedOnTheZAxis() {
		var chains = TunnelChain.merge(List.of(new Run(7, 20, -16, false, 16), new Run(7, 20, 0, false, 12)), 1);
		assertEquals(1, chains.size());
		assertEquals(28, chains.get(0).length());
		assertEquals(-16, chains.get(0).z1());
		assertEquals(11, chains.get(0).z2());
		assertFalse(chains.get(0).alongX());
	}

	@Test
	void negativeCoordinatesAndLongestFirst() {
		var chains = TunnelChain.merge(List.of(new Run(-40, -30, -8, true, 16), new Run(-24, -30, -8, true, 16), new Run(100, 5, 5, true, 6)), 1);
		assertEquals(2, chains.size());
		assertEquals(32, chains.get(0).length());
		assertEquals(-40, chains.get(0).x1());
		assertEquals(6, chains.get(1).length());
	}

	@Test
	void emptyAndZeroLengthInput() {
		assertTrue(TunnelChain.merge(List.of(), 1).isEmpty());
		assertTrue(TunnelChain.merge(List.of(new Run(0, 0, 0, true, 0)), 1).isEmpty());
	}
}
