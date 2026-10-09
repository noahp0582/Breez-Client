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
 * A* over a grid of "feet positions". A feet position is valid when the two blocks of body space are passable and the
 * block below is solid ground. Moves: 8 horizontal directions, 1-block step up, drops of up to {@code maxDrop}.
 */
public final class Pathfinder {
	private Pathfinder() {}

	public interface World {
		/** Body can occupy this cell (no collision, not a hazard, chunk loaded). */
		boolean passable(int x, int y, int z);

		/** A player can stand on top of this block. */
		boolean ground(int x, int y, int z);
	}

	private record Node(Pos pos, double g, double f) {}

	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	/**
	 * @param goalCenter  point to approach
	 * @param goalRadius  search ends at the first valid cell within this 3D distance of {@code goalCenter}
	 * @return the cells to walk through (excluding the start), or null when no route exists within {@code maxNodes}
	 */
	public static List<Pos> find(World w, Pos start, Pos goalCenter, double goalRadius, int maxDrop, int maxNodes) {
		if (!standable(w, start.x(), start.y(), start.z())) return null;
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
		Map<Pos, Double> best = new HashMap<>();
		Map<Pos, Pos> from = new HashMap<>();
		Set<Pos> closed = new HashSet<>();
		open.add(new Node(start, 0, h(start, goalCenter)));
		best.put(start, 0.0);
		int expanded = 0;
		while (!open.isEmpty() && expanded < maxNodes) {
			Node n = open.poll();
			if (!closed.add(n.pos())) continue;
			expanded++;
			if (n.pos().dist(goalCenter.x(), goalCenter.y(), goalCenter.z()) <= goalRadius) return rebuild(from, n.pos(), start);
			for (int[] d : DIRS) {
				boolean diag = d[0] != 0 && d[1] != 0;
				int nx = n.pos().x() + d[0], nz = n.pos().z() + d[1];
				if (diag && !(clearColumn(w, n.pos().x() + d[0], n.pos().y(), n.pos().z()) && clearColumn(w, n.pos().x(), n.pos().y(), n.pos().z() + d[1]))) continue;
				Pos next = move(w, n.pos(), nx, nz, maxDrop);
				if (next == null || closed.contains(next)) continue;
				double step = (diag ? 1.414 : 1.0) + (next.y() > n.pos().y() ? 0.6 : 0) + (next.y() < n.pos().y() ? 0.25 * (n.pos().y() - next.y()) : 0);
				double g = n.g() + step;
				if (g < best.getOrDefault(next, Double.MAX_VALUE)) {
					best.put(next, g);
					from.put(next, n.pos());
					open.add(new Node(next, g, g + h(next, goalCenter)));
				}
			}
		}
		return null;
	}

	private static boolean standable(World w, int x, int y, int z) {
		return w.passable(x, y, z) && w.passable(x, y + 1, z) && w.ground(x, y - 1, z);
	}

	private static boolean clearColumn(World w, int x, int y, int z) {
		return w.passable(x, y, z) && w.passable(x, y + 1, z);
	}

	/** Cell reached by moving one step toward (nx, nz) from p, or null when blocked. */
	private static Pos move(World w, Pos p, int nx, int nz, int maxDrop) {
		int x = p.x(), y = p.y(), z = p.z();
		// level
		if (standable(w, nx, y, nz)) return new Pos(nx, y, nz);
		// step up: needs headroom above the current cell and a free target cell one higher
		if (w.passable(x, y + 2, z) && standable(w, nx, y + 1, nz) && w.ground(nx, y, nz)) return new Pos(nx, y + 1, nz);
		// drop: walk off the edge into free space and land below
		if (w.passable(nx, y, nz) && w.passable(nx, y + 1, nz)) {
			for (int d = 1; d <= maxDrop; d++) {
				if (!w.passable(nx, y - d, nz)) break;
				if (standable(w, nx, y - d, nz)) return new Pos(nx, y - d, nz);
			}
		}
		return null;
	}

	private static double h(Pos a, Pos b) {
		double dx = Math.abs(a.x() - b.x()), dz = Math.abs(a.z() - b.z()), dy = Math.abs(a.y() - b.y());
		double diag = Math.min(dx, dz), straight = Math.max(dx, dz) - diag;
		return diag * 1.414 + straight + dy * 0.5;
	}

	private static List<Pos> rebuild(Map<Pos, Pos> from, Pos end, Pos start) {
		List<Pos> out = new ArrayList<>();
		Pos cur = end;
		while (!cur.equals(start)) {
			out.add(cur);
			cur = from.get(cur);
		}
		Collections.reverse(out);
		return out;
	}
}
