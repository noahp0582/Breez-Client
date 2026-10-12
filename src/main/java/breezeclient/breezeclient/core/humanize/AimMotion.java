package breezeclient.breezeclient.core.humanize;

import java.util.Random;

/**
 * Camera movement toward a target, advanced by real time so it can run every frame. One turn has three parts, like a hand
 * on a mouse: it speeds up, travels at a steady speed, and slows into the target. The steady speed, how quickly it gets
 * there and a slight bow in the path are drawn fresh for every turn, so no two turns look the same. Applied deltas are
 * whole mouse steps; what does not fit in a step is carried into the next frame, so slow parts stay smooth.
 */
public final class AimMotion {
	/** A new turn starts when the target jumps by more than this (degrees); smaller changes are the same turn tracking. */
	private static final double NEW_TURN_ANGLE = 9.0;
	/** Slowest the end of a turn creeps (degrees per second), so it always finishes. */
	private static final double CREEP = 22.0;

	private final Random rng;
	private boolean active;
	private Rotation lastTarget;
	/** Current speed along the path, degrees per second. */
	private double speed;
	/** Share of the allowed speed this turn cruises at. */
	private double cruiseShare = 1;
	/** Seconds this turn takes to reach its cruise speed. */
	private double rampSeconds = 0.12;
	private double brake = 1;
	/** Sideways bow of the path, radians at its widest. */
	private double bow;
	private double startDistance;
	private double carryYaw, carryPitch;

	public AimMotion(Random rng) { this.rng = rng; }

	/** Forget the current turn; the next call starts a new one from rest. */
	public void reset() {
		active = false;
		speed = 0;
		carryYaw = carryPitch = 0;
		lastTarget = null;
	}

	public boolean active() { return active; }

	/** Speed right now, degrees per second. */
	public double speed() { return speed; }

	/** Cruise speed of the current turn as a share of the speed it was allowed. */
	public double cruiseShare() { return cruiseShare; }

	private static double angleBetween(Rotation a, Rotation b) {
		return Math.hypot(Rotations.wrapDegrees(a.yaw() - b.yaw()), a.pitch() - b.pitch());
	}

	private void beginTurn(double distance) {
		cruiseShare = 0.5 + 0.8 * rng.nextDouble(); // half to 1.3 times the nominal speed
		rampSeconds = 0.07 + 0.11 * rng.nextDouble();
		brake = 0.75 + 0.6 * rng.nextDouble();
		bow = (rng.nextDouble() - 0.5) * 0.3;
		startDistance = Math.max(distance, 1e-6);
		active = true;
	}

	/**
	 * Moves the camera for {@code dt} seconds.
	 *
	 * @param maxDegPerSec nominal top speed for this caller; the turn cruises at a random share of it
	 * @param quantum      smallest step a mouse can make at the current sensitivity
	 * @return the new rotation, or {@code target}-aligned {@code current} unchanged when it is already there (see {@link #arrived})
	 */
	public Rotation advance(Rotation current, Rotation target, double maxDegPerSec, double dt, float quantum) {
		double dyaw = Rotations.wrapDegrees(target.yaw() - current.yaw());
		double dpitch = target.pitch() - current.pitch();
		double dist = Math.hypot(dyaw, dpitch);
		if (!active || lastTarget == null || angleBetween(target, lastTarget) > NEW_TURN_ANGLE) {
			boolean moving = active && speed > 1;
			beginTurn(dist);
			if (!moving) speed = 0; // a turn that interrupts another keeps its momentum
		}
		lastTarget = target;
		if (arrived(current, target, quantum)) {
			speed = 0;
			carryYaw = carryPitch = 0;
			return current;
		}
		dt = Math.max(0, Math.min(0.1, dt));
		double cruise = Math.max(CREEP, maxDegPerSec * cruiseShare);
		double accel = cruise / rampSeconds;
		// the fastest we may go and still stop on the target, never slower than the creep
		double stopSpeed = Math.sqrt(2 * accel * brake * dist);
		double want = Math.max(Math.min(cruise, stopSpeed), Math.min(cruise, CREEP));
		speed = want > speed ? Math.min(want, speed + accel * dt) : want;
		double step = Math.min(dist, speed * dt);

		// straight at the target, bent a little sideways in the middle of the turn
		double progress = Math.max(0, Math.min(1, 1 - dist / startDistance));
		double bend = bow * Math.sin(Math.PI * progress);
		double ux = dyaw / dist, uy = dpitch / dist;
		double cos = Math.cos(bend), sin = Math.sin(bend);
		double mx = (ux * cos - uy * sin) * step + carryYaw;
		double my = (ux * sin + uy * cos) * step + carryPitch;

		float ay = snapToward(mx, dyaw, quantum);
		float ap = snapToward(my, dpitch, quantum);
		// what did not fit in a whole step waits for the next frame (at most one step of it, or an axis that is done would hoard it)
		carryYaw = Math.max(-quantum, Math.min(quantum, mx - ay));
		carryPitch = Math.max(-quantum, Math.min(quantum, my - ap));
		float pitch = Math.max(-90f, Math.min(90f, current.pitch() + ap));
		return new Rotation(current.yaw() + ay, pitch);
	}

	/** Whole mouse steps of {@code delta}, never past what is left on that axis. */
	private static float snapToward(double delta, double remaining, float quantum) {
		if (quantum <= 0f) return (float) delta;
		double steps = Math.rint(delta / quantum);
		double limit = Math.floor(Math.abs(remaining) / quantum + 1e-6);
		if (Math.signum(steps) == Math.signum(remaining)) steps = Math.signum(steps) * Math.min(Math.abs(steps), limit);
		return (float) (steps * quantum);
	}

	/** Within one mouse step of the target on both axes. */
	public static boolean arrived(Rotation current, Rotation target, float quantum) {
		float q = Math.max(quantum, 1e-4f);
		return Math.abs(Rotations.wrapDegrees(target.yaw() - current.yaw())) < q && Math.abs(target.pitch() - current.pitch()) < q;
	}
}
