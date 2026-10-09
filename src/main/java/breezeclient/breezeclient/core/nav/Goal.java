package breezeclient.breezeclient.core.nav;

import java.util.List;

/** Where the pathfinder should end up. {@link #heuristic} must not overestimate the remaining cost. */
public interface Goal {
	boolean isEnd(Pos p);

	double heuristic(Pos p);

	/** Stand exactly on this block position. */
	record GoalBlock(int x, int y, int z) implements Goal {
		public boolean isEnd(Pos p) { return p.x() == x && p.y() == y && p.z() == z; }

		public double heuristic(Pos p) { return Heuristics.octile(p.x() - x, p.z() - z) + Math.abs(p.y() - y) * 0.6; }
	}

	/** Reach any height at this x/z. */
	record GoalXZ(int x, int z) implements Goal {
		public boolean isEnd(Pos p) { return p.x() == x && p.z() == z; }

		public double heuristic(Pos p) { return Heuristics.octile(p.x() - x, p.z() - z); }
	}

	/** Get within {@code radius} (3D distance) of a point. */
	record GoalNear(int x, int y, int z, double radius) implements Goal {
		public boolean isEnd(Pos p) { return p.dist(x, y, z) <= radius; }

		public double heuristic(Pos p) {
			double h = Heuristics.octile(p.x() - x, p.z() - z) + Math.abs(p.y() - y) * 0.6;
			return Math.max(0, h - radius);
		}
	}

	/** Stand next to a block (up to one block away on any side, standing at its height or one lower/higher) so it can be reached. */
	record GoalGetToBlock(int x, int y, int z) implements Goal {
		public boolean isEnd(Pos p) {
			int dx = Math.abs(p.x() - x), dz = Math.abs(p.z() - z), dy = y - p.y();
			if (dx == 0 && dz == 0) return dy == 2 || dy == -1; // directly below or above (feet beside a pillar)
			return dx + dz == 1 && (dy == 0 || dy == 1); // beside it, feet at its height or one below
		}

		public double heuristic(Pos p) { return Math.max(0, Heuristics.octile(p.x() - x, p.z() - z) - 1) + Math.abs(p.y() - y) * 0.3; }
	}

	/** Any of several goals. */
	record GoalComposite(List<Goal> goals) implements Goal {
		public boolean isEnd(Pos p) {
			for (Goal g : goals) if (g.isEnd(p)) return true;
			return false;
		}

		public double heuristic(Pos p) {
			double best = Double.MAX_VALUE;
			for (Goal g : goals) best = Math.min(best, g.heuristic(p));
			return goals.isEmpty() ? 0 : best;
		}
	}

	/** Get at least {@code minDistance} blocks (horizontally) from every given point. */
	record GoalRunAway(List<Pos> from, double minDistance) implements Goal {
		public boolean isEnd(Pos p) {
			for (Pos f : from) if (Math.hypot(p.x() - f.x(), p.z() - f.z()) < minDistance) return false;
			return true;
		}

		public double heuristic(Pos p) {
			double nearest = Double.MAX_VALUE;
			for (Pos f : from) nearest = Math.min(nearest, Math.hypot(p.x() - f.x(), p.z() - f.z()));
			return from.isEmpty() ? 0 : Math.max(0, minDistance - nearest);
		}
	}

	final class Heuristics {
		private Heuristics() {}

		static double octile(int dx, int dz) {
			double ax = Math.abs(dx), az = Math.abs(dz);
			double diag = Math.min(ax, az);
			return diag * 1.414 + (Math.max(ax, az) - diag);
		}
	}
}
