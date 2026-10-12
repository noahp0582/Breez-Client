package breezeclient.breezeclient.client.gui;

import java.util.WeakHashMap;

/**
 * Eased values for hover, toggle and open/close effects. Each key (usually the widget itself) owns one value that moves
 * toward its target a little every frame. The GUI tab's "Animation speed" scales it; 0 turns animation off.
 */
public final class Anim {
	/** key -> {value, time of the last frame in nanoseconds} */
	private static final WeakHashMap<Object, double[]> VALUES = new WeakHashMap<>();

	private Anim() {}

	/** The key's value after moving it toward {@code target}. A key seen for the first time starts at the target. */
	public static double to(Object key, double target) {
		double speed = GuiState.THEME.animationSpeed.get();
		long now = System.nanoTime();
		double[] v = VALUES.get(key);
		if (v == null || speed <= 0) {
			VALUES.put(key, new double[] {target, now});
			return target;
		}
		double dt = Math.min(0.1, (now - v[1]) / 1e9);
		v[1] = now;
		v[0] += (target - v[0]) * (1 - Math.exp(-dt * 16 * speed));
		if (Math.abs(target - v[0]) < 0.004) v[0] = target;
		return v[0];
	}

	public static double to(Object key, boolean on) { return to(key, on ? 1 : 0); }

	/** Starts the key from a given value, so the next frames ease from there (used for things that just appeared). */
	public static void set(Object key, double value) { VALUES.put(key, new double[] {value, System.nanoTime()}); }

	public static double easeOut(double t) {
		t = Math.max(0, Math.min(1, t));
		return 1 - Math.pow(1 - t, 3);
	}
}
