package breezeclient.breezeclient.core.gui;

/** Color helpers for the GUI color picker. Colors are ARGB ints. */
public final class ColorMath {
	private ColorMath() {}

	public static int argb(int a, int r, int g, int b) {
		return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

	public static int alpha(int argb) { return argb >>> 24; }
	public static int red(int argb) { return (argb >> 16) & 0xFF; }
	public static int green(int argb) { return (argb >> 8) & 0xFF; }
	public static int blue(int argb) { return argb & 0xFF; }

	public static int withAlpha(int argb, int a) { return (clamp(a) << 24) | (argb & 0xFFFFFF); }

	/** Multiplies the alpha channel by {@code f} (0..1). */
	public static int fade(int argb, double f) { return withAlpha(argb, (int) Math.round(alpha(argb) * Math.max(0, Math.min(1, f)))); }

	/** Linear blend of two colors, t = 0 gives a, t = 1 gives b. */
	public static int mix(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		return argb((int) Math.round(alpha(a) + (alpha(b) - alpha(a)) * t), (int) Math.round(red(a) + (red(b) - red(a)) * t),
			(int) Math.round(green(a) + (green(b) - green(a)) * t), (int) Math.round(blue(a) + (blue(b) - blue(a)) * t));
	}

	/** @return {hue 0..360, saturation 0..1, value 0..1} */
	public static double[] toHsv(int argb) {
		double r = red(argb) / 255.0, g = green(argb) / 255.0, b = blue(argb) / 255.0;
		double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		double h;
		if (d == 0) h = 0;
		else if (max == r) h = 60 * (((g - b) / d) % 6);
		else if (max == g) h = 60 * ((b - r) / d + 2);
		else h = 60 * ((r - g) / d + 4);
		if (h < 0) h += 360;
		return new double[] {h, max == 0 ? 0 : d / max, max};
	}

	public static int fromHsv(double h, double s, double v, int alpha) {
		h = ((h % 360) + 360) % 360;
		s = Math.max(0, Math.min(1, s));
		v = Math.max(0, Math.min(1, v));
		double c = v * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c;
		double r, g, b;
		if (h < 60) { r = c; g = x; b = 0; }
		else if (h < 120) { r = x; g = c; b = 0; }
		else if (h < 180) { r = 0; g = c; b = x; }
		else if (h < 240) { r = 0; g = x; b = c; }
		else if (h < 300) { r = x; g = 0; b = c; }
		else { r = c; g = 0; b = x; }
		return argb(alpha, (int) Math.round((r + m) * 255), (int) Math.round((g + m) * 255), (int) Math.round((b + m) * 255));
	}

	/** "#RRGGBB" or "#AARRGGBB". */
	public static String toHex(int argb) {
		return alpha(argb) == 255 ? String.format("#%06X", argb & 0xFFFFFF) : String.format("#%08X", argb);
	}

	/** Accepts RRGGBB, #RRGGBB, AARRGGBB, 0x-prefixed. Returns null when invalid. 6 digits get full alpha. */
	public static Integer parseHex(String text) {
		if (text == null) return null;
		String t = text.trim();
		if (t.startsWith("#")) t = t.substring(1);
		else if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
		if (t.length() != 6 && t.length() != 8) return null;
		try {
			long v = Long.parseLong(t, 16);
			return (int) (t.length() == 6 ? (0xFF000000L | v) : v);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
