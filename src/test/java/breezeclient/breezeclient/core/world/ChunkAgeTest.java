package breezeclient.breezeclient.core.world;

import breezeclient.breezeclient.core.world.ChunkAge.State;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkAgeTest {
	private static final long A = ChunkAge.key(3, -7), B = ChunkAge.key(-1, 5);

	@Test
	void keyRoundTripsNegativeCoordinates() {
		long k = ChunkAge.key(-123, 456);
		assertEquals(-123, ChunkAge.x(k));
		assertEquals(456, ChunkAge.z(k));
		long k2 = ChunkAge.key(77, -88);
		assertEquals(77, ChunkAge.x(k2));
		assertEquals(-88, ChunkAge.z(k2));
		assertNotEquals(ChunkAge.key(1, 2), ChunkAge.key(2, 1));
	}

	@Test
	void fluidFlowRightAfterLoadMeansNew() {
		ChunkAge c = new ChunkAge(100);
		c.onLoad(A, 1000);
		assertEquals(State.UNKNOWN, c.state(A));
		c.onFluidFlow(A, 1050);
		assertEquals(State.NEW, c.state(A));
	}

	@Test
	void noFlowInWindowMeansOld() {
		ChunkAge c = new ChunkAge(100);
		c.onLoad(A, 1000);
		c.tick(1100, k -> true);
		assertEquals(State.UNKNOWN, c.state(A), "window not over yet");
		c.tick(1101, k -> true);
		assertEquals(State.OLD, c.state(A));
	}

	@Test
	void flowAfterWindowIsIgnored() {
		ChunkAge c = new ChunkAge(100);
		c.onLoad(A, 1000);
		c.tick(1200, k -> true);
		c.onFluidFlow(A, 1210);
		assertEquals(State.OLD, c.state(A));
	}

	@Test
	void flowAfterWindowButBeforeTickIsIgnoredToo() {
		ChunkAge c = new ChunkAge(100);
		c.onLoad(A, 1000);
		c.onFluidFlow(A, 1500);
		assertEquals(State.UNKNOWN, c.state(A));
		c.tick(1501, k -> true);
		assertEquals(State.OLD, c.state(A));
	}

	@Test
	void firstClassificationIsKeptOnReload() {
		ChunkAge c = new ChunkAge(100);
		c.onLoad(A, 0);
		c.onFluidFlow(A, 10);
		c.onUnload(A, k -> true);
		c.onLoad(A, 5000);
		c.onFluidFlow(A, 5010);
		c.tick(6000, k -> true);
		assertEquals(State.NEW, c.state(A), "a chunk new on first sight stays new");
		c.onLoad(B, 0);
		c.tick(500, k -> true);
		c.onLoad(B, 9000);
		c.onFluidFlow(B, 9010);
		assertEquals(State.OLD, c.state(B));
	}

	@Test
	void unloadingBeforeTheWindowEndsMakesItOld() {
		ChunkAge c = new ChunkAge(100);
		c.onLoad(A, 0);
		c.onUnload(A, k -> true);
		assertEquals(State.OLD, c.state(A));
	}

	@Test
	void flowForAChunkThatNeverLoadedIsIgnored() {
		ChunkAge c = new ChunkAge(100);
		c.onFluidFlow(A, 5);
		assertEquals(State.UNKNOWN, c.state(A));
		assertEquals(0, c.count(State.NEW));
	}

	@Test
	void countsAndSnapshotAndRestore() {
		ChunkAge c = new ChunkAge(100);
		c.onLoad(A, 0);
		c.onFluidFlow(A, 1);
		c.onLoad(B, 0);
		c.tick(1000, k -> true);
		assertEquals(1, c.count(State.NEW));
		assertEquals(1, c.count(State.OLD));
		ChunkAge d = new ChunkAge(100);
		d.restore(c.snapshot());
		assertEquals(State.NEW, d.state(A));
		assertEquals(State.OLD, d.state(B));
		d.restore(Map.of(A, State.OLD));
		assertEquals(State.NEW, d.state(A), "restoring never overwrites what this session saw");
	}

	@Test
	void strictModeNeedsLiquidToCallAChunkOld() {
		ChunkAge c = new ChunkAge(100);
		c.setStrict(true);
		c.onLoad(A, 0);
		c.onLoad(B, 0);
		c.tick(500, k -> k == A); // only A contains liquid
		assertEquals(State.OLD, c.state(A));
		assertEquals(State.UNSURE, c.state(B));
	}

	@Test
	void unsureChunksAreReevaluatedOnReload() {
		ChunkAge c = new ChunkAge(100);
		c.setStrict(true);
		c.onLoad(B, 0);
		c.tick(500, k -> false);
		assertEquals(State.UNSURE, c.state(B));
		c.onLoad(B, 9000);
		c.onFluidFlow(B, 9010);
		assertEquals(State.NEW, c.state(B));
	}

	@Test
	void unsureIsNeverPersisted() {
		ChunkAge c = new ChunkAge(100);
		c.setStrict(true);
		c.onLoad(B, 0);
		c.tick(500, k -> false);
		ChunkAge d = new ChunkAge(100);
		d.restore(c.snapshot());
		assertEquals(State.UNKNOWN, d.state(B));
	}

	@Test
	void manyChunksSettleInOrder() {
		ChunkAge c = new ChunkAge(50);
		for (int i = 0; i < 1000; i++) c.onLoad(ChunkAge.key(i, 0), i);
		c.tick(120, k -> true);
		assertEquals(State.OLD, c.state(ChunkAge.key(0, 0)));
		assertEquals(State.OLD, c.state(ChunkAge.key(69, 0)));
		assertEquals(State.UNKNOWN, c.state(ChunkAge.key(70, 0)));
		c.tick(2000, k -> true);
		assertEquals(1000, c.count(State.OLD));
	}
}
