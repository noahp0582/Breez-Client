package breezeclient.breezeclient.core.physics;

import java.util.ArrayList;
import java.util.List;

/** Pure projectile flight: gravity and drag per tick, with the world's collision supplied by the caller. */
public final class Trajectory {
	private Trajectory() {}

	public record Point(double x, double y, double z) {
		public Point plus(Point o) { return new Point(x + o.x, y + o.y, z + o.z); }
	}

	/** Finds where the straight segment from {@code from} to {@code to} first enters something solid. */
	public interface Collider {
		/** @return the hit position, or null when the segment is clear */
		Point hit(Point from, Point to);
	}

	/** {@code points} start with the first step (not the start position); {@code landing} is null when nothing was hit. */
	public record Result(List<Point> points, Point landing) {}

	/**
	 * Same order as vanilla throwables: move by the velocity, then scale the velocity by the drag, then subtract gravity.
	 *
	 * @param gravity blocks per tick squared, subtracted from the vertical velocity (0.03 for pearls and snowballs)
	 * @param drag    velocity multiplier per tick (0.99 in air)
	 */
	public static Result simulate(Point start, Point velocity, double gravity, double drag, int maxSteps, Collider collider) {
		List<Point> points = new ArrayList<>();
		Point pos = start;
		double vx = velocity.x(), vy = velocity.y(), vz = velocity.z();
		for (int i = 0; i < maxSteps; i++) {
			Point next = new Point(pos.x() + vx, pos.y() + vy, pos.z() + vz);
			Point hit = collider == null ? null : collider.hit(pos, next);
			if (hit != null) {
				points.add(hit);
				return new Result(points, hit);
			}
			points.add(next);
			pos = next;
			vx *= drag;
			vy = vy * drag - gravity;
			vz *= drag;
		}
		return new Result(points, null);
	}
}
