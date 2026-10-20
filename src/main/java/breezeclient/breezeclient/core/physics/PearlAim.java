package breezeclient.breezeclient.core.physics;

import java.util.function.Predicate;

/**
 * Aiming an ender pearl at a spot: the flight follows vanilla's throwable tick exactly (gravity, then drag, then the
 * move), and the pitch is searched until the simulated landing is as close to the spot as it gets.
 */
public final class PearlAim {
	private PearlAim() {}

	public static final double GRAVITY = 0.03, DRAG = 0.99, POWER = 1.5;
	/** Ticks of flight followed; a pearl thrown at anything in sight lands well before this. */
	public static final int MAX_TICKS = 160;

	/** {@code error} is the horizontal distance from the landing to the spot aimed at. */
	public record Shot(float yaw, float pitch, Trajectory.Point landing, int ticks, double error) {}

	/** Launch velocity for a pitch and yaw in degrees, the way {@code shootFromRotation} turns them into a direction. */
	public static Trajectory.Point velocity(float yaw, float pitch) {
		double y = Math.toRadians(yaw), x = Math.toRadians(pitch);
		return new Trajectory.Point(-Math.sin(y) * Math.cos(x) * POWER, -Math.sin(x) * POWER, Math.cos(y) * Math.cos(x) * POWER);
	}

	/** Vanilla order per tick: gravity, drag, then the move (with the collision check along it). */
	public static Trajectory.Result fly(Trajectory.Point start, Trajectory.Point velocity, int maxTicks, Trajectory.Collider collider) {
		java.util.List<Trajectory.Point> points = new java.util.ArrayList<>();
		Trajectory.Point pos = start;
		double vx = velocity.x(), vy = velocity.y(), vz = velocity.z();
		for (int i = 0; i < maxTicks; i++) {
			vy -= GRAVITY;
			vx *= DRAG;
			vy *= DRAG;
			vz *= DRAG;
			Trajectory.Point next = new Trajectory.Point(pos.x() + vx, pos.y() + vy, pos.z() + vz);
			Trajectory.Point hit = collider == null ? null : collider.hit(pos, next);
			if (hit != null) {
				points.add(hit);
				return new Trajectory.Result(points, hit);
			}
			points.add(next);
			pos = next;
		}
		return new Trajectory.Result(points, null);
	}

	/** Yaw (degrees) that faces from {@code start} toward x/z. */
	public static float yawTo(Trajectory.Point start, double x, double z) {
		return (float) Math.toDegrees(Math.atan2(-(x - start.x()), z - start.z()));
	}

	/**
	 * The throw from {@code start} whose landing comes closest to (x, z), among the landings {@code accept} takes.
	 *
	 * @return the best throw, or null when no accepted landing is within {@code maxError} blocks of the spot
	 */
	public static Shot solve(Trajectory.Point start, double x, double z, Trajectory.Collider collider, Predicate<Trajectory.Point> accept, double maxError) {
		float yaw = yawTo(start, x, z);
		Shot best = null;
		for (int pitch = -89; pitch <= 89; pitch++) best = better(best, shot(start, yaw, pitch, x, z, collider, accept));
		if (best == null) return null;
		float around = best.pitch();
		for (float d = -1f; d <= 1f; d += 0.05f) best = better(best, shot(start, yaw, around + d, x, z, collider, accept));
		return best.error() <= maxError ? best : null;
	}

	private static Shot shot(Trajectory.Point start, float yaw, float pitch, double x, double z, Trajectory.Collider collider, Predicate<Trajectory.Point> accept) {
		if (pitch < -90f || pitch > 90f) return null;
		Trajectory.Result r = fly(start, velocity(yaw, pitch), MAX_TICKS, collider);
		Trajectory.Point l = r.landing();
		if (l == null || !accept.test(l)) return null;
		return new Shot(yaw, pitch, l, r.points().size(), Math.hypot(l.x() - x, l.z() - z));
	}

	/** Closer wins; a shorter flight (less time for the random spread to add up) breaks near-ties. */
	private static Shot better(Shot a, Shot b) {
		if (b == null) return a;
		if (a == null) return b;
		return b.error() + b.ticks() * 0.004 < a.error() + a.ticks() * 0.004 ? b : a;
	}
}
