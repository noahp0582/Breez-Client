package breezeclient.breezeclient.core.nav;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathfinderTest {
	/** Floor of solid blocks at y = 0 for x,z in [0, size); extra solid blocks listed in {@code solid}. */
	static class Grid implements Pathfinder.World {
		final Set<Pos> solid = new HashSet<>();
		final Set<Pos> hazard = new HashSet<>();

		Grid(int size) {
			for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) solid.add(new Pos(x, 0, z));
		}

		void wall(int x, int z, int height) {
			for (int y = 1; y <= height; y++) solid.add(new Pos(x, y, z));
		}

		@Override
		public boolean passable(int x, int y, int z) {
			Pos p = new Pos(x, y, z);
			return !solid.contains(p) && !hazard.contains(p) && y >= -10 && y < 40;
		}

		@Override
		public boolean ground(int x, int y, int z) {
			return solid.contains(new Pos(x, y, z));
		}
	}

	@Test
	void straightLine() {
		Grid g = new Grid(12);
		List<Pos> path = Pathfinder.find(g, new Pos(0, 1, 0), new Pos(8, 1, 0), 0.5, 3, 5000);
		assertNotNull(path);
		assertEquals(new Pos(8, 1, 0), path.get(path.size() - 1));
		assertEquals(8, path.size());
	}

	@Test
	void detoursAroundWall() {
		Grid g = new Grid(12);
		for (int z = 0; z < 8; z++) g.wall(4, z, 3);
		List<Pos> path = Pathfinder.find(g, new Pos(0, 1, 0), new Pos(8, 1, 0), 0.5, 3, 5000);
		assertNotNull(path);
		assertTrue(path.stream().noneMatch(p -> p.x() == 4 && p.z() < 8));
	}

	@Test
	void stepsUpOneBlock() {
		Grid g = new Grid(12);
		for (int x = 3; x < 12; x++) for (int z = 0; z < 12; z++) g.solid.add(new Pos(x, 1, z)); // raised floor
		List<Pos> path = Pathfinder.find(g, new Pos(0, 1, 1), new Pos(5, 2, 1), 0.5, 3, 5000);
		assertNotNull(path);
		assertTrue(path.stream().anyMatch(p -> p.y() == 2));
	}

	@Test
	void cannotClimbTwoBlocks() {
		Grid g = new Grid(12);
		for (int z = 0; z < 12; z++) g.wall(5, z, 2);
		assertNull(Pathfinder.find(g, new Pos(0, 1, 0), new Pos(8, 1, 0), 0.5, 3, 20000));
	}

	@Test
	void dropsDownUpToMaxDrop() {
		Grid g = new Grid(6);
		for (int x = 0; x < 3; x++) for (int z = 0; z < 6; z++) for (int y = 1; y <= 3; y++) g.solid.add(new Pos(x, y, z));
		List<Pos> path = Pathfinder.find(g, new Pos(0, 4, 0), new Pos(5, 1, 0), 0.5, 3, 5000);
		assertNotNull(path);
		assertEquals(new Pos(5, 1, 0), path.get(path.size() - 1));
	}

	@Test
	void refusesDropsLargerThanMax() {
		Grid g = new Grid(6);
		for (int x = 0; x < 3; x++) for (int z = 0; z < 6; z++) for (int y = 1; y <= 5; y++) g.solid.add(new Pos(x, y, z));
		assertNull(Pathfinder.find(g, new Pos(0, 6, 0), new Pos(5, 1, 0), 0.5, 3, 20000));
	}

	@Test
	void avoidsHazardCells() {
		Grid g = new Grid(12);
		for (int z = 0; z < 5; z++) g.hazard.add(new Pos(4, 1, z));
		List<Pos> path = Pathfinder.find(g, new Pos(0, 1, 0), new Pos(8, 1, 0), 0.5, 3, 5000);
		assertNotNull(path);
		assertTrue(path.stream().noneMatch(g.hazard::contains));
	}

	@Test
	void noDiagonalCornerCutting() {
		Grid g = new Grid(6);
		g.wall(1, 0, 2);
		g.wall(0, 1, 2);
		assertNull(Pathfinder.find(g, new Pos(0, 1, 0), new Pos(3, 1, 3), 0.5, 3, 5000));
	}

	@Test
	void respectsNodeBudget() {
		Grid g = new Grid(60);
		assertNull(Pathfinder.find(g, new Pos(0, 1, 0), new Pos(59, 1, 59), 0.5, 3, 10));
	}

	@Test
	void goalRadiusStopsEarly() {
		Grid g = new Grid(12);
		List<Pos> path = Pathfinder.find(g, new Pos(0, 1, 0), new Pos(10, 1, 0), 3.0, 3, 5000);
		assertNotNull(path);
		assertTrue(path.size() <= 7);
	}

	@Test
	void startNotStandableReturnsNull() {
		Grid g = new Grid(5);
		assertNull(Pathfinder.find(g, new Pos(2, 5, 2), new Pos(4, 1, 4), 0.5, 3, 100));
	}

	@Test
	void headroomRequired() {
		Grid g = new Grid(8);
		for (int z = 0; z < 8; z++) g.solid.add(new Pos(3, 2, z)); // 1-high tunnel ceiling (feet y=1 needs y=2 free)
		assertNull(Pathfinder.find(g, new Pos(0, 1, 0), new Pos(6, 1, 0), 0.5, 3, 20000));
	}
}
