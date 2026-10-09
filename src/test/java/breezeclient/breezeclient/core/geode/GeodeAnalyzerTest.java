package breezeclient.breezeclient.core.geode;

import breezeclient.breezeclient.core.geode.GeodeAnalyzer.Bud;
import breezeclient.breezeclient.core.geode.GeodeAnalyzer.Geode;
import breezeclient.breezeclient.core.geode.GeodeAnalyzer.Stage;
import breezeclient.breezeclient.core.nav.Pos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GeodeAnalyzerTest {
	private static Pos p(int x, int y, int z) { return new Pos(x, y, z); }

	/** One budding block at the origin with buds on the given faces (+x, -x, +y, -y, +z, -z order). */
	private static List<Geode> single(Stage... faces) {
		List<Bud> buds = new ArrayList<>();
		int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int i = 0; i < faces.length; i++) if (faces[i] != null) buds.add(new Bud(p(d[i][0], d[i][1], d[i][2]), faces[i]));
		return GeodeAnalyzer.analyze(List.of(p(0, 0, 0)), buds, pos -> false, 4);
	}

	@Test
	void noBuddingBlocksMeansNoGeodes() {
		assertTrue(GeodeAnalyzer.analyze(List.of(), List.of(), x -> true, 4).isEmpty());
	}

	@Test
	void countsEachStageAndPercent() {
		Geode g = single(Stage.CLUSTER, Stage.CLUSTER, Stage.CLUSTER, Stage.LARGE, Stage.MEDIUM, Stage.SMALL).get(0);
		assertEquals(3, g.cluster());
		assertEquals(1, g.large());
		assertEquals(1, g.medium());
		assertEquals(1, g.small());
		assertEquals(6, g.buds());
		assertEquals(50.0, g.grownPercent(), 1e-9);
	}

	@Test
	void thresholdEdgeCases() {
		// 9 clusters + 1 large across two budding blocks = 90%
		List<Pos> budding = List.of(p(0, 0, 0), p(3, 0, 0));
		List<Bud> buds = new ArrayList<>();
		int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		int made = 0;
		for (Pos b : budding) {
			for (int[] dir : d) {
				if (made >= 10) break;
				Pos f = p(b.x() + dir[0], b.y() + dir[1], b.z() + dir[2]);
				if (f.equals(p(1, 0, 0)) && b.equals(p(0, 0, 0))) { /* fine */ }
				buds.add(new Bud(f, made == 9 ? Stage.LARGE : Stage.CLUSTER));
				made++;
			}
		}
		Geode g = GeodeAnalyzer.analyze(budding, buds, x -> false, 4).get(0);
		assertEquals(1, GeodeAnalyzer.analyze(budding, buds, x -> false, 4).size());
		assertEquals(10, g.buds());
		assertEquals(90.0, g.grownPercent(), 1e-9);
		assertTrue(g.fullyGrown(90, 4));
		assertFalse(g.fullyGrown(91, 4));
		assertFalse(g.fullyGrown(90, 11), "needs at least the minimum number of buds");
	}

	@Test
	void everyBudAClusterIsOneHundredPercent() {
		Geode g = single(Stage.CLUSTER, Stage.CLUSTER, Stage.CLUSTER, Stage.CLUSTER, null, null).get(0);
		assertEquals(100.0, g.grownPercent(), 1e-9);
		assertTrue(g.fullyGrown(100, 4));
	}

	@Test
	void geodeWithNoBudsIsNotFullyGrown() {
		Geode g = single().get(0);
		assertEquals(0, g.buds());
		assertFalse(g.fullyGrown(90, 1));
		assertFalse(g.fullyGrown(0, 4));
	}

	@Test
	void budsNotTouchingBuddingAreIgnored() {
		List<Geode> gs = GeodeAnalyzer.analyze(List.of(p(0, 0, 0)), List.of(new Bud(p(5, 5, 5), Stage.CLUSTER), new Bud(p(1, 0, 0), Stage.SMALL)), x -> false, 4);
		assertEquals(1, gs.get(0).buds());
		assertEquals(0.0, gs.get(0).grownPercent(), 1e-9);
	}

	@Test
	void sharedBudBetweenTwoBuddingBlocksCountsOnce() {
		Geode g = GeodeAnalyzer.analyze(List.of(p(0, 0, 0), p(2, 0, 0)), List.of(new Bud(p(1, 0, 0), Stage.CLUSTER)), x -> false, 4).get(0);
		assertEquals(1, g.cluster());
	}

	@Test
	void openSlotsAreFacesWithAirAndNoBud() {
		Set<Pos> air = Set.of(p(1, 0, 0), p(-1, 0, 0), p(0, 1, 0));
		Geode g = GeodeAnalyzer.analyze(List.of(p(0, 0, 0)), List.of(new Bud(p(1, 0, 0), Stage.SMALL)), air::contains, 4).get(0);
		assertEquals(2, g.openSlots(), "the face with a bud is not an open slot");
		assertEquals(1.0 / 3.0 * 100, g.coverage(), 1e-9);
	}

	@Test
	void distantBuddingBlocksFormSeparateGeodes() {
		List<Geode> gs = GeodeAnalyzer.analyze(List.of(p(0, 0, 0), p(2, 1, 0), p(100, 0, 0), p(102, 0, 1)), List.of(), x -> false, 4);
		assertEquals(2, gs.size());
		assertEquals(p(0, 0, 0), gs.get(0).anchor());
		assertEquals(p(100, 0, 0), gs.get(1).anchor());
		assertEquals(2, gs.get(0).budding());
	}

	@Test
	void chainOfBuddingBlocksLinksIntoOneGeode() {
		List<Pos> chain = new ArrayList<>();
		for (int i = 0; i < 10; i++) chain.add(p(i * 4, 0, 0));
		assertEquals(1, GeodeAnalyzer.analyze(chain, List.of(), x -> false, 4).size());
		assertEquals(10, GeodeAnalyzer.analyze(chain, List.of(), x -> false, 3).size());
	}

	@Test
	void negativeCoordinatesAreGroupedCorrectly() {
		List<Geode> gs = GeodeAnalyzer.analyze(List.of(p(-1, -1, -1), p(1, 1, 1), p(-100, -64, -100)), List.of(), x -> false, 4);
		assertEquals(2, gs.size());
		assertEquals(p(-100, -64, -100), gs.get(0).anchor());
		assertEquals(2, gs.get(1).budding());
	}

	@Test
	void estimatedAgeGrowsWithStage() {
		double young = single(Stage.SMALL, Stage.SMALL).get(0).estHours();
		double old = single(Stage.CLUSTER, Stage.CLUSTER).get(0).estHours();
		assertTrue(old > young * 3.9);
		assertTrue(young > 0);
	}

	@Test
	void duplicateBuddingPositionsAreCountedOnce() {
		Geode g = GeodeAnalyzer.analyze(List.of(p(0, 0, 0), p(0, 0, 0)), List.of(), x -> false, 4).get(0);
		assertEquals(1, g.budding());
	}
}
