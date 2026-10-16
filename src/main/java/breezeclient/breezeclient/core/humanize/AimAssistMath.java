package breezeclient.breezeclient.core.humanize;

/**
 * One tick of aim assist. Unlike a full "turn to target", it only nudges: a fraction of the remaining angle, capped, faded
 * out near the crosshair so it never fights you for the last degree, and snapped to real mouse-step sizes.
 */
public final class AimAssistMath {
	private AimAssistMath() {}

	/** Degrees to add to yaw and pitch this tick. */
	public record Delta(float yaw, float pitch) {
		public static final Delta NONE = new Delta(0f, 0f);

		public boolean isZero() { return yaw == 0f && pitch == 0f; }
	}

	/**
	 * @param smoothing      fraction of the remaining angle applied per tick (0.15 = 15%)
	 * @param maxStep        hard cap on degrees per tick per axis
	 * @param deadZone       no assist within this many degrees of the target; it fades in over the next {@code deadZone} degrees
	 * @param verticalFactor 0..1 scale for the pitch correction (pitch yanks feel worse than yaw)
	 * @param quantum        mouse step size in degrees (0 = no snapping)
	 */
	public static Delta step(float yaw, float pitch, float targetYaw, float targetPitch, double smoothing, float maxStep,
		float deadZone, float verticalFactor, float quantum) {
		float yawErr = Rotations.wrapDegrees(targetYaw - yaw);
		float pitchErr = targetPitch - pitch;
		double dist = Math.hypot(yawErr, pitchErr);
		if (dist <= deadZone) return Delta.NONE;
		double fade = deadZone <= 0 ? 1.0 : Math.min(1.0, (dist - deadZone) / deadZone);
		double k = Math.max(0, Math.min(1, smoothing)) * fade;
		float dy = clamp((float) (yawErr * k), maxStep);
		float dp = clamp((float) (pitchErr * k * Math.max(0, Math.min(1, verticalFactor))), maxStep);
		if (quantum > 0f) {
			dy = Math.round(dy / quantum) * quantum;
			dp = Math.round(dp / quantum) * quantum;
		}
		// snapping must never carry the view past the target
		if (Math.abs(dy) > Math.abs(yawErr)) dy = 0f;
		if (Math.abs(dp) > Math.abs(pitchErr)) dp = 0f;
		return new Delta(dy, dp);
	}

	/**
	 * The per-frame equivalent of a per-tick smoothing fraction: applying it every frame for one tick's worth of frames
	 * closes the same share of the angle as one tick at {@code perTick}.
	 *
	 * @param dtTicks frame length in ticks (1/20 s each)
	 */
	public static double perFrame(double perTick, double dtTicks) {
		double k = Math.max(0, Math.min(1, perTick));
		return 1 - Math.pow(1 - k, Math.max(0, dtTicks));
	}

	/**
	 * Snaps {@code wanted + carry[0]} down to whole mouse steps and keeps the remainder in {@code carry[0]}, so movements
	 * smaller than one step add up over frames instead of rounding to zero. Never returns more than {@code err}.
	 */
	public static float quantize(float wanted, float err, float quantum, float[] carry) {
		if (quantum <= 0f) return wanted;
		float total = wanted + carry[0];
		float out = (float) (Math.floor(Math.abs(total) / quantum) * quantum) * Math.signum(total);
		if (Math.abs(out) > Math.abs(err) || Math.signum(wanted) != Math.signum(total) && wanted != 0f) {
			carry[0] = 0f;
			return 0f;
		}
		carry[0] = total - out;
		return out;
	}

	private static float clamp(float v, float max) { return Math.max(-max, Math.min(max, v)); }
}
