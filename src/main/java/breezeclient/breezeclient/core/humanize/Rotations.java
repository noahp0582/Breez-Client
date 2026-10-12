package breezeclient.breezeclient.core.humanize;

/** Pure camera math shared by the aiming code; the movement itself is in {@link AimMotion}. */
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
}
