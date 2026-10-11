package breezeclient.breezeclient.core.sus;

/** Pure colouring for the sus chunk highlights: the least suspicious chunk is green and it turns red as the score rises. */
public final class SusColor {
	private SusColor() {}

	/** Where {@code score} sits between the flagging threshold ({@code low}) and the strongest score on screen ({@code high}), 0..1. */
	public static double fraction(double score, double low, double high) {
		if (!(high > low)) return 0;
		return clamp((score - low) / (high - low));
	}

	/** Opaque ARGB: 0 is green, 0.5 is yellow, 1 is red, with the other colour channel fading linearly in between. */
	public static int gradient(double t) {
		double x = clamp(t);
		int r = (int) Math.round(255 * Math.min(1.0, x * 2));
		int g = (int) Math.round(255 * Math.min(1.0, (1.0 - x) * 2));
		return 0xFF000000 | (r << 16) | (g << 8);
	}

	private static double clamp(double v) {
		if (Double.isNaN(v)) return 0;
		return Math.max(0, Math.min(1, v));
	}
}
