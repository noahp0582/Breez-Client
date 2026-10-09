package breezeclient.breezeclient.core.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* over "feet positions". A cell is valid when both body blocks are passable and the block below is solid ground.
 * Moves: walk, diagonal, ascend one block, descend, sprint-jump over gaps (parkour), break through blocks, dig down,
 * pillar up and bridge across gaps. The expensive moves only exist when the {@link Options} allow them.
 */
public final class Pathfinder {
	private Pathfinder() {}

	public interface World {
		/** Body can occupy this cell (no collision, not a hazard, chunk loaded). */
		boolean passable(int x, int y, int z);

		/** A player can stand on top of this block. */
		boolean ground(int x, int y, int z);

		/** Extra cost of breaking this block (in "ticks of walking"), or a negative number when it must never be broken. */
		default double breakCost(int x, int y, int z) { return -1; }

		/** Whether a block may be placed into this cell (needs placeable blocks in the inventory and a free cell). */
		default boolean placeable(int x, int y, int z) { return false; }
	}

	public enum Move { WALK, DIAGONAL, ASCEND, DESCEND, PARKOUR, BREAK, DIG_DOWN, PILLAR, BRIDGE }

	/** One step of a path: arrive at {@code pos} using {@code move}; {@code breaks} lists the blocks to mine first. */
	public record Step(Pos pos, Move move, List<Pos> breaks) {}

	public record Options(boolean allowBreak, boolean allowPlace, boolean allowParkour, int maxDrop, double breakWeight, double placeCost) {
		public static Options walking(int maxDrop) { return new Options(false, false, false, maxDrop, 1.0, 2.0); }
	}

	/** @param reachedGoal false when the node budget ran out; the steps then lead toward the closest point found */
	public record Result(List<Step> steps, boolean reachedGoal) {}

	private record Candidate(Pos to, Move move, double cost, List<Pos> breaks) {}

	private record Edge(Pos parent, Step step) {}

	private record Node(Pos pos, double g, double f) {}

	private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
	private static final double INF = Double.MAX_VALUE / 4;

	/** Old API: walk to within {@code goalRadius} of a point. Returns null when unreachable. */
	public static List<Pos> find(World w, Pos start, Pos goalCenter, double goalRadius, int maxDrop, int maxNodes) {
		Result r = search(w, start, new Goal.GoalNear(goalCenter.x(), goalCenter.y(), goalCenter.z(), goalRadius), Options.walking(maxDrop), maxNodes);
		if (r == null || !r.reachedGoal()) return null;
		List<Pos> out = new ArrayList<>();
		for (Step s : r.steps()) out.add(s.pos());
		return out;
	}

	/** @return null when the start cell is not standable; otherwise the path (possibly partial) */
	public static Result search(World w, Pos start, Goal goal, Options o, int maxNodes) {
		if (!standable(w, start.x(), start.y(), start.z())) return null;
		if (goal.isEnd(start)) return new Result(List.of(), true);
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
		Map<Pos, Double> best = new HashMap<>();
		Map<Pos, Edge> from = new HashMap<>();
		Set<Pos> closed = new HashSet<>();
		double startH = goal.heuristic(start);
		open.add(new Node(start, 0, startH));
		best.put(start, 0.0);
		Pos bestPos = start;
		double bestH = startH;
		int expanded = 0;
		while (!open.isEmpty() && expanded < maxNodes) {
			Node n = open.poll();
			if (!closed.add(n.pos())) continue;
			expanded++;
			if (goal.isEnd(n.pos())) return new Result(rebuild(from, n.pos(), start), true);
			double h = n.f() - n.g();
			if (h < bestH - 1e-9) {
				bestH = h;
				bestPos = n.pos();
			}
			Edge into = from.get(n.pos());
			for (Candidate c : neighbors(w, n.pos(), o, into == null ? null : into.step().move())) {
				if (closed.contains(c.to())) continue;
				double g = n.g() + c.cost();
				if (g < best.getOrDefault(c.to(), Double.MAX_VALUE)) {
					best.put(c.to(), g);
					from.put(c.to(), new Edge(n.pos(), new Step(c.to(), c.move(), c.breaks())));
					open.add(new Node(c.to(), g, g + goal.heuristic(c.to())));
				}
			}
		}
		if (bestPos.equals(start) || bestH > startH - 1.0) return new Result(List.of(), false);
		return new Result(rebuild(from, bestPos, start), false);
	}

	// moves ----------------------------------------------------------------------------------------------------------

	private static boolean standable(World w, int x, int y, int z) {
		return w.passable(x, y, z) && w.passable(x, y + 1, z) && w.ground(x, y - 1, z);
	}

	private static boolean clearColumn(World w, int x, int y, int z) { return w.passable(x, y, z) && w.passable(x, y + 1, z); }

	/** Cost to make this cell passable: 0 when it already is, the (weighted) break cost, or INF. */
	private static double open(World w, Options o, int x, int y, int z, List<Pos> breaks) {
		if (w.passable(x, y, z)) return 0;
		if (!o.allowBreak()) return INF;
		double c = w.breakCost(x, y, z);
		if (c < 0) return INF;
		breaks.add(new Pos(x, y, z));
		return c * o.breakWeight();
	}

	private static List<Candidate> neighbors(World w, Pos p, Options o, Move arrivedBy) {
		List<Candidate> out = new ArrayList<>();
		int x = p.x(), y = p.y(), z = p.z();
		// a block we placed on the way counts as ground under our feet even though the world does not show it yet
		boolean placedGround = arrivedBy == Move.BRIDGE || arrivedBy == Move.PILLAR;

		for (int[] d : CARDINAL) {
			int nx = x + d[0], nz = z + d[1];
			// walk
			if (standable(w, nx, y, nz)) out.add(new Candidate(new Pos(nx, y, nz), Move.WALK, 1.0, List.of()));
			// ascend one block
			else if (w.passable(x, y + 2, z) && standable(w, nx, y + 1, nz) && w.ground(nx, y, nz)) out.add(new Candidate(new Pos(nx, y + 1, nz), Move.ASCEND, 1.6, List.of()));
			// descend
			else if (w.passable(nx, y, nz) && w.passable(nx, y + 1, nz)) {
				boolean landed = false;
				for (int dd = 1; dd <= o.maxDrop(); dd++) {
					if (!w.passable(nx, y - dd, nz)) break;
					if (standable(w, nx, y - dd, nz)) {
						out.add(new Candidate(new Pos(nx, y - dd, nz), Move.DESCEND, 1.0 + 0.25 * dd, List.of()));
						landed = true;
						break;
					}
				}
				if (!landed && o.allowPlace() && w.passable(nx, y - 1, nz) && (placedGround || w.ground(x, y - 1, z)) && w.placeable(nx, y - 1, nz)) {
					out.add(new Candidate(new Pos(nx, y, nz), Move.BRIDGE, 2.0 + o.placeCost(), List.of()));
				}
			}
			if (o.allowParkour()) parkour(w, p, d, o, out);
			if (o.allowBreak()) breakMoves(w, p, d, o, out);
		}

		for (int[] d : DIAGONAL) {
			int nx = x + d[0], nz = z + d[1];
			if (!clearColumn(w, nx, y, z) || !clearColumn(w, x, y, nz)) continue;
			if (standable(w, nx, y, nz)) out.add(new Candidate(new Pos(nx, y, nz), Move.DIAGONAL, 1.414, List.of()));
		}

		if (o.allowBreak()) {
			// dig straight down, only onto solid ground (never into a void or liquid)
			if (!w.passable(x, y - 1, z) && w.ground(x, y - 2, z)) {
				double c = w.breakCost(x, y - 1, z);
				if (c >= 0) out.add(new Candidate(new Pos(x, y - 1, z), Move.DIG_DOWN, 1.4 + c * o.breakWeight(), List.of(new Pos(x, y - 1, z))));
			}
		}
		if (o.allowPlace() && w.passable(x, y + 2, z) && w.placeable(x, y, z)) {
			out.add(new Candidate(new Pos(x, y + 1, z), Move.PILLAR, 3.0 + o.placeCost(), List.of()));
		}
		return out;
	}

	private static void parkour(World w, Pos p, int[] d, Options o, List<Candidate> out) {
		int x = p.x(), y = p.y(), z = p.z();
		if (w.ground(x + d[0], y - 1, z + d[1])) return; // no gap, a normal walk covers it
		if (!w.passable(x, y + 2, z)) return; // need room to jump
		for (int dist = 2; dist <= 4; dist++) {
			boolean arcClear = true;
			for (int i = 1; i < dist; i++) {
				int cx = x + d[0] * i, cz = z + d[1] * i;
				if (!clearColumn(w, cx, y, cz) || !w.passable(cx, y + 2, cz)) {
					arcClear = false;
					break;
				}
			}
			if (!arcClear) break;
			int lx = x + d[0] * dist, lz = z + d[1] * dist;
			if (standable(w, lx, y, lz)) out.add(new Candidate(new Pos(lx, y, lz), Move.PARKOUR, 1.0 + dist * 1.3, List.of()));
			if (dist <= 3 && standable(w, lx, y + 1, lz) && w.passable(lx, y + 2, lz) && w.passable(x + d[0] * (dist - 1), y + 2, z + d[1] * (dist - 1)) && w.ground(lx, y, lz)) {
				out.add(new Candidate(new Pos(lx, y + 1, lz), Move.PARKOUR, 2.5 + dist * 1.3, List.of()));
			}
			if (standable(w, lx, y - 1, lz)) out.add(new Candidate(new Pos(lx, y - 1, lz), Move.PARKOUR, 1.5 + dist * 1.3, List.of()));
		}
	}

	private static void breakMoves(World w, Pos p, int[] d, Options o, List<Candidate> out) {
		int x = p.x(), y = p.y(), z = p.z();
		int nx = x + d[0], nz = z + d[1];
		// same level: dig a 1x2 tunnel forward, needs something to stand on
		if (w.ground(nx, y - 1, nz)) {
			List<Pos> br = new ArrayList<>();
			double c = open(w, o, nx, y, nz, br) + open(w, o, nx, y + 1, nz, br);
			if (c < INF && !br.isEmpty()) out.add(new Candidate(new Pos(nx, y, nz), Move.BREAK, 1.0 + c, br));
		}
		// up: climb a staircase through solid blocks
		if (w.ground(nx, y, nz)) {
			List<Pos> br = new ArrayList<>();
			double c = open(w, o, x, y + 2, z, br) + open(w, o, nx, y + 1, nz, br) + open(w, o, nx, y + 2, nz, br);
			if (c < INF && !br.isEmpty()) out.add(new Candidate(new Pos(nx, y + 1, nz), Move.BREAK, 1.6 + c, br));
		}
	}

	private static List<Step> rebuild(Map<Pos, Edge> from, Pos end, Pos start) {
		List<Step> out = new ArrayList<>();
		Pos cur = end;
		while (!cur.equals(start)) {
			Edge e = from.get(cur);
			out.add(e.step());
			cur = e.parent();
		}
		Collections.reverse(out);
		return out;
	}
}
