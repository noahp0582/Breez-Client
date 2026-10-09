package breezeclient.breezeclient.core.humanize;

import java.util.Random;

/** Randomized, non-periodic timing for automated actions. */
public class ActionLimiter {
	private final Random rng;

	public ActionLimiter(Random rng) { this.rng = rng; }

	public int nextDelayTicks(double meanTicks, double stdDevTicks, int minTicks) {
		long v = Math.round(meanTicks + rng.nextGaussian() * stdDevTicks);
		return (int) Math.max(minTicks, v);
	}

	public boolean chance(double p) { return rng.nextDouble() < p; }

	/** Uniform integer in [lo, hi]. */
	public int between(int lo, int hi) { return lo + rng.nextInt(hi - lo + 1); }
}
