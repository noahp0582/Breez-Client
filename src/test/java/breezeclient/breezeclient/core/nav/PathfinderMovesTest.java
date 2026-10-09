package breezeclient.breezeclient.core.nav;

import breezeclient.breezeclient.core.nav.Pathfinder.Move;
import breezeclient.breezeclient.core.nav.Pathfinder.Options;
import breezeclient.breezeclient.core.nav.Pathfinder.Result;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathfinderMovesTest {
	/** Floor at y = 0 for 0 <= x,z < size; extra solid blocks in {@code solid}; blocks in {@code protectedBlocks} can never be broken. */
	static class Grid implements Pathfinder.World {
		final Set<Pos> solid = new HashSet<>();
		final Set<Pos> unbreakable = new HashSet<>();
		boolean haveBlocks = true;
		double cost = 3.0;

		Grid(int size) {
			for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) solid.add(new Pos(x, 0, z));
		}

		void cut(int x1, int x2, int z1, int z2) { // remove floor => a gap
			for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) solid.remove(new Pos(x, 0, z));
		}

		void box(int x, int z, int height) { for (int y = 1; y <= height; y++) solid.add(new Pos(x, y, z)); }

		@Override public boolean passable(int x, int y, int z) { return !solid.contains(new Pos(x, y, z)) && y > -10 && y < 40; }

		@Override public boolean ground(int x, int y, int z) { return solid.contains(new Pos(x, y, z)); }

		@Override public double breakCost(int x, int y, int z) { return solid.contains(new Pos(x, y, z)) && !unbreakable.contains(new Pos(x, y, z)) && y > 0 ? cost : -1; }

		@Override public boolean placeable(int x, int y, int z) { return haveBlocks && passable(x, y, z); }
	}

	private static final Options WALK = Options.walking(3);
	private static final Options ALL = new Options(true, true, true, 3, 1.0, 2.0);
	private static final Options PARKOUR = new Options(false, false, true, 3, 1.0, 2.0);
	private static final Options BREAK = new Options(true, false, false, 3, 1.0, 2.0);
	private static final Options PLACE = new Options(false, true, false, 3, 1.0, 2.0);

	private static boolean uses(Result r, Move m) { return r.steps().stream().anyMatch(s -> s.move() == m); }

	// goals -------------------------------------------------------------------------------------------------------------

	@Test
	void goalBlockEndsOnlyOnExactCell() {
		Goal g = new Goal.GoalBlock(3, 1, 4);
		assertTrue(g.isEnd(new Pos(3, 1, 4)));
		assertFalse(g.isEnd(new Pos(3, 2, 4)));
		assertEquals(0.0, g.heuristic(new Pos(3, 1, 4)), 1e-9);
	}

	@Test
	void goalXZIgnoresHeight() {
		Goal g = new Goal.GoalXZ(5, 5);
		assertTrue(g.isEnd(new Pos(5, 70, 5)));
		assertFalse(g.isEnd(new Pos(5, 70, 6)));
	}

	@Test
	void goalNearUsesThreeDimensionalRadius() {
		Goal g = new Goal.GoalNear(0, 0, 0, 3);
		assertTrue(g.isEnd(new Pos(2, 2, 0)));
		assertFalse(g.isEnd(new Pos(3, 3, 0)));
		assertTrue(g.heuristic(new Pos(10, 0, 0)) < new Goal.GoalNear(0, 0, 0, 0).heuristic(new Pos(10, 0, 0)));
	}

	@Test
	void goalGetToBlockAcceptsSidesAbovAndBelow() {
		Goal g = new Goal.GoalGetToBlock(10, 5, 10);
		assertTrue(g.isEnd(new Pos(11, 5, 10)), "beside at the same height");
		assertTrue(g.isEnd(new Pos(10, 4, 11)), "beside, block at head height");
		assertTrue(g.isEnd(new Pos(10, 3, 10)), "standing below, block two above the feet");
		assertTrue(g.isEnd(new Pos(10, 6, 10)), "standing on top");
		assertFalse(g.isEnd(new Pos(12, 5, 10)));
		assertFalse(g.isEnd(new Pos(10, 5, 10)));
	}

	@Test
	void goalCompositeAndRunAway() {
		Goal c = new Goal.GoalComposite(List.of(new Goal.GoalBlock(0, 0, 0), new Goal.GoalBlock(9, 0, 9)));
		assertTrue(c.isEnd(new Pos(9, 0, 9)));
		assertFalse(c.isEnd(new Pos(1, 0, 1)));
		assertEquals(0.0, c.heuristic(new Pos(0, 0, 0)), 1e-9);
		Goal run = new Goal.GoalRunAway(List.of(new Pos(0, 0, 0)), 10);
		assertFalse(run.isEnd(new Pos(3, 0, 4)));
		assertTrue(run.isEnd(new Pos(8, 0, 8)));
		assertTrue(run.heuristic(new Pos(1, 0, 0)) > run.heuristic(new Pos(9, 0, 0)));
	}

	// moves ---------------------------------------------------------------------------------------------------------------

	@Test
	void searchToGoalBlockAtStartIsEmptyAndReached() {
		Grid g = new Grid(5);
		Result r = Pathfinder.search(g, new Pos(1, 1, 1), new Goal.GoalBlock(1, 1, 1), WALK, 100);
		assertTrue(r.reachedGoal());
		assertTrue(r.steps().isEmpty());
	}

	@Test
	void parkourClearsGapsUpToThreeBlocks() {
		for (int gap = 1; gap <= 3; gap++) {
			Grid g = new Grid(14);
			g.cut(4, 3 + gap, 0, 13); // a gap across the whole width
			Result r = Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(8, 1, 0), PARKOUR, 20000);
			assertTrue(r.reachedGoal(), "gap " + gap);
			assertTrue(uses(r, Move.PARKOUR), "gap " + gap);
		}
	}

	@Test
	void parkourCannotClearFourBlockGaps() {
		Grid g = new Grid(14);
		g.cut(4, 7, 0, 13);
		assertFalse(Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(10, 1, 0), PARKOUR, 20000).reachedGoal());
	}

	@Test
	void parkourOffMeansNoJumps() {
		Grid g = new Grid(14);
		g.cut(4, 4, 0, 13);
		assertFalse(Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(8, 1, 0), WALK, 20000).reachedGoal());
	}

	@Test
	void parkourNeedsHeadroomToJump() {
		Grid g = new Grid(14);
		g.cut(4, 4, 0, 13);
		for (int x = 0; x < 14; x++) for (int z = 0; z < 14; z++) g.solid.add(new Pos(x, 3, z)); // ceiling at y = 3
		assertFalse(Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(8, 1, 0), PARKOUR, 20000).reachedGoal());
	}

	@Test
	void breakingThroughAThinWallBeatsAHugeDetour() {
		Grid g = new Grid(30);
		for (int z = 0; z < 30; z++) g.box(5, z, 2); // full-width wall, 2 high
		Result r = Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(9, 1, 0), BREAK, 50000);
		assertTrue(r.reachedGoal());
		assertTrue(uses(r, Move.BREAK));
		long breaks = r.steps().stream().mapToLong(s -> s.breaks().size()).sum();
		assertTrue(breaks >= 1 && breaks <= 2, "cheapest way through: break the top block and hop onto the bottom one, or a 1x2 hole; was " + breaks);
	}

	@Test
	void walkingAroundBeatsBreakingWhenTheDetourIsShort() {
		Grid g = new Grid(30);
		for (int z = 0; z < 4; z++) g.box(5, z, 2);
		Result r = Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(9, 1, 0), BREAK, 50000);
		assertTrue(r.reachedGoal());
		assertFalse(uses(r, Move.BREAK));
	}

	@Test
	void unbreakableBlocksAreNeverBroken() {
		Grid g = new Grid(30);
		for (int z = 0; z < 30; z++) {
			g.box(5, z, 2);
			g.unbreakable.add(new Pos(5, 1, z));
			g.unbreakable.add(new Pos(5, 2, z));
		}
		assertFalse(Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(9, 1, 0), BREAK, 50000).reachedGoal());
	}

	@Test
	void breakOffMeansWallsStayWalls() {
		Grid g = new Grid(30);
		for (int z = 0; z < 30; z++) g.box(5, z, 2);
		assertFalse(Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(9, 1, 0), WALK, 50000).reachedGoal());
	}

	@Test
	void stairsUpThroughSolidRockWhenBreakingAllowed() {
		Grid g = new Grid(20);
		for (int x = 4; x < 20; x++) for (int z = 0; z < 20; z++) for (int y = 1; y <= 3; y++) g.solid.add(new Pos(x, y, z));
		Result r = Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(8, 4, 0), BREAK, 50000);
		assertTrue(r.reachedGoal());
		assertTrue(uses(r, Move.BREAK));
	}

	@Test
	void digDownOnlyOntoSolidGround() {
		Grid g = new Grid(6);
		for (int y = 1; y <= 3; y++) for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) g.solid.add(new Pos(x, y, z));
		Result r = Pathfinder.search(g, new Pos(2, 4, 2), new Goal.GoalBlock(2, 1, 2), new Options(true, false, false, 3, 1.0, 2.0), 20000);
		assertTrue(r.reachedGoal());
		assertTrue(uses(r, Move.DIG_DOWN) || uses(r, Move.DESCEND));
		// over a void: the block below is not ground, so there is nothing to dig
		Grid v = new Grid(6);
		v.solid.removeIf(p -> p.y() == 0);
		v.solid.add(new Pos(2, 0, 2));
		v.solid.add(new Pos(2, -1, 2));
		assertFalse(Pathfinder.search(v, new Pos(2, 1, 2), new Goal.GoalBlock(2, -1, 2), new Options(true, false, false, 0, 1.0, 2.0), 2000).reachedGoal()
			&& Pathfinder.search(v, new Pos(2, 1, 2), new Goal.GoalBlock(2, -2, 2), new Options(true, false, false, 0, 1.0, 2.0), 2000).reachedGoal());
	}

	@Test
	void pillarsUpToAHighPlatform() {
		Grid g = new Grid(8);
		for (int x = 5; x < 8; x++) for (int z = 0; z < 8; z++) for (int y = 1; y <= 3; y++) g.solid.add(new Pos(x, y, z));
		Result r = Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(6, 4, 0), PLACE, 50000);
		assertTrue(r.reachedGoal());
		assertTrue(uses(r, Move.PILLAR));
	}

	@Test
	void noBlocksMeansNoPillarsOrBridges() {
		Grid g = new Grid(8);
		g.haveBlocks = false;
		for (int x = 5; x < 8; x++) for (int z = 0; z < 8; z++) for (int y = 1; y <= 3; y++) g.solid.add(new Pos(x, y, z));
		assertFalse(Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(6, 4, 0), PLACE, 50000).reachedGoal());
	}

	@Test
	void bridgesAcrossAWideGap() {
		Grid g = new Grid(16);
		g.cut(4, 9, 0, 15); // 6 wide, too far to jump
		Result r = Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(12, 1, 0), new Options(false, true, true, 0, 1.0, 2.0), 50000);
		assertTrue(r.reachedGoal());
		assertTrue(uses(r, Move.BRIDGE));
	}

	@Test
	void partialPathLeadsTowardTheGoalWhenBudgetRunsOut() {
		Grid g = new Grid(80);
		Result r = Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(79, 1, 0), WALK, 60);
		assertFalse(r.reachedGoal());
		assertFalse(r.steps().isEmpty());
		Pos end = r.steps().get(r.steps().size() - 1).pos();
		assertTrue(end.x() > 5, "ends closer to the goal: " + end);
	}

	@Test
	void everyStepIsAdjacentToThePreviousOne() {
		Grid g = new Grid(30);
		g.cut(10, 11, 0, 29);
		Result r = Pathfinder.search(g, new Pos(0, 1, 0), new Goal.GoalBlock(20, 1, 3), ALL, 80000);
		assertTrue(r.reachedGoal());
		Pos prev = new Pos(0, 1, 0);
		for (var s : r.steps()) {
			assertTrue(Math.abs(s.pos().x() - prev.x()) <= 4 && Math.abs(s.pos().z() - prev.z()) <= 4 && Math.abs(s.pos().y() - prev.y()) <= 3, s.toString());
			prev = s.pos();
		}
		assertEquals(new Pos(20, 1, 3), prev);
	}

	@Test
	void unstandableStartIsRejected() {
		Grid g = new Grid(5);
		assertNull(Pathfinder.search(g, new Pos(2, 7, 2), new Goal.GoalXZ(4, 4), WALK, 100));
	}

	@Test
	void runAwayGoalMovesAwayFromThreat() {
		Grid g = new Grid(40);
		Result r = Pathfinder.search(g, new Pos(20, 1, 20), new Goal.GoalRunAway(List.of(new Pos(20, 1, 20)), 12), WALK, 20000);
		assertTrue(r.reachedGoal());
		Pos end = r.steps().get(r.steps().size() - 1).pos();
		assertTrue(Math.hypot(end.x() - 20, end.z() - 20) >= 12);
	}
}
