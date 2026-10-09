package breezeclient.breezeclient.core.humanize;

import java.util.Random;

/** Pure camera math: eased, mouse-quantized rotation toward a target. */
public final class Rotations {
	private Rotations() {}

	public static float wrapDegrees(float deg) {
		float d = deg % 360f;
		if (d >= 180f) d -= 360f;
		if (d < -180f) d += 360f;
		return d;
	}

	/** Smallest angle in degrees a real mouse can turn the camera at this sensitivity (0..1 option value). */
	public static float quantum(double sensitivity) {
		double f = sensitivity * 0.6 + 0.2;
		return (float) (f * f * f * 8.0 * 0.15);
	}

	public static float snap(float delta, float quantum) {
		if (quantum <= 0f) return delta;
		return Math.round(delta / quantum) * quantum;
	}

	/**
	 * One tick of movement from current toward target. Never overshoots, moves along the shortest yaw path,
	 * every applied delta is a multiple of quantum, pitch stays within [-90, 90].
	 */
	public static Rotation step(Rotation current, Rotation target, float maxStep, float quantum, Random rng) {
		float dyaw = wrapDegrees(target.yaw() - current.yaw());
		float dpitch = target.pitch() - current.pitch();
		float ay = stepAxis(dyaw, maxStep, quantum, rng);
		float ap = stepAxis(dpitch, maxStep, quantum, rng);
		float pitch = Math.max(-90f, Math.min(90f, current.pitch() + ap));
		return new Rotation(current.yaw() + ay, pitch);
	}

	private static float stepAxis(float remaining, float maxStep, float quantum, Random rng) {
		float abs = Math.abs(remaining);
		if (abs < quantum) return 0f;
		float want = abs * (0.25f + 0.15f * rng.nextFloat());
		want = Math.max(quantum, Math.min(maxStep, want));
		want = Math.min(want, abs);
		float snapped = snap(want, quantum);
		while (snapped > abs && snapped >= quantum) snapped -= quantum;
		if (snapped <= 0f) snapped = quantum <= abs ? quantum : 0f;
		return Math.signum(remaining) * snapped;
	}
}
