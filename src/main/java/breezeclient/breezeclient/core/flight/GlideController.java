package breezeclient.breezeclient.core.flight;

/**
 * Elytra pitch cycling: dive to build speed, climb while the speed lasts, dive again. Pure state machine; the caller turns
 * the returned pitch into a camera move and passes in the current speed in blocks per tick.
 */
public final class GlideController {
	public enum Phase { DIVE, CLIMB }

	private final float divePitch;
	private final float climbPitch;
	private final double climbAtSpeed;
	private final double diveAtSpeed;
	private Phase phase = Phase.DIVE;

	/**
	 * @param divePitch    pitch while diving (positive looks down)
	 * @param climbPitch   pitch while climbing (negative looks up)
	 * @param climbAtSpeed switch from diving to climbing once the speed reaches this
	 * @param diveAtSpeed  switch from climbing to diving once the speed falls to this
	 */
	public GlideController(float divePitch, float climbPitch, double climbAtSpeed, double diveAtSpeed) {
		this.divePitch = divePitch;
		this.climbPitch = climbPitch;
		this.climbAtSpeed = climbAtSpeed;
		this.diveAtSpeed = Math.min(diveAtSpeed, climbAtSpeed);
	}

	public Phase phase() { return phase; }

	public void reset() { phase = Phase.DIVE; }

	/**
	 * @param speed      current speed in blocks per tick
	 * @param tooLow     true when the ground is close: forces a climb and never starts a dive
	 */
	public float nextPitch(double speed, boolean tooLow) {
		if (tooLow) {
			phase = Phase.CLIMB;
		} else if (phase == Phase.DIVE && speed >= climbAtSpeed) {
			phase = Phase.CLIMB;
		} else if (phase == Phase.CLIMB && speed <= diveAtSpeed) {
			phase = Phase.DIVE;
		}
		return phase == Phase.DIVE ? divePitch : climbPitch;
	}
}
