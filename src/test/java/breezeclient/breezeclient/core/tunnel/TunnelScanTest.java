package breezeclient.breezeclient.core.tunnel;

import breezeclient.breezeclient.core.tunnel.TunnelScan.Grid;
import breezeclient.breezeclient.core.tunnel.TunnelScan.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelScanTest {
	private static Grid solidBlock() {
		Grid g = new Grid(18, 18, 12);
		for (int x = 0; x < 18; x++) for (int z = 0; z < 18; z++) for (int y = 0; y < 12; y++) g.set(x, y, z, true, false);
		return g;
	}

	private static void carve(Grid g, int x, int y, int z) {
		g.set(x, y, z, false, true);
	}

	@Test
	void straightOneByTwoTunnelIsFound() {
		Grid g = solidBlock();
		for (int x = 1; x <= 16; x++) {
			carve(g, x, 4, 7);
			carve(g, x, 5, 7);
		}
		List<Segment> s = TunnelScan.find(g, 12);
		assertEquals(1, s.size());
		assertEquals(16, s.get(0).length());
		assertTrue(s.get(0).alongX());
		assertEquals(4, s.get(0).y());
		assertEquals(7, s.get(0).z());
	}

	@Test
	void tunnelAlongZIsFound() {
		Grid g = solidBlock();
		for (int z = 1; z <= 16; z++) {
			carve(g, 9, 3, z);
			carve(g, 9, 4, z);
		}
		List<Segment> s = TunnelScan.find(g, 12);
		assertEquals(1, s.size());
		assertFalse(s.get(0).alongX());
	}

	@Test
	void wideRoomIsNotATunnel() {
		Grid g = solidBlock();
		for (int x = 1; x <= 16; x++) for (int z = 5; z <= 7; z++) {
			carve(g, x, 4, z);
			carve(g, x, 5, z);
		}
		assertTrue(TunnelScan.find(g, 8).isEmpty());
	}

	@Test
	void blockedTunnelSplitsIntoShortRuns() {
		Grid g = solidBlock();
		for (int x = 1; x <= 16; x++) {
			if (x == 8) continue;
			carve(g, x, 4, 7);
			carve(g, x, 5, 7);
		}
		assertTrue(TunnelScan.find(g, 12).isEmpty());
		assertEquals(2, TunnelScan.find(g, 6).size());
	}

	@Test
	void openRoofIsNotATunnel() {
		Grid g = solidBlock();
		for (int x = 1; x <= 16; x++) {
			carve(g, x, 4, 7);
			carve(g, x, 5, 7);
			carve(g, x, 6, 7); // the ceiling is open
		}
		assertTrue(TunnelScan.find(g, 8).isEmpty());
	}
}
