package breezeclient.breezeclient.core;

/**
 * How likely a module is to be noticed by a server's anti-cheat or by staff. This is a rating of the module's behavior
 * (what it sends and how it looks from the outside), not a guarantee: servers differ and change.
 */
public enum Detection {
	/** Local only (rendering, sounds, UI) or indistinguishable from ordinary play. */
	UNDETECTED("Undetected", 0xFF55FF55),
	/** Acts on its own in ways a strict anti-cheat or an attentive moderator can notice. */
	USE_AT_RISK("Use at Risk", 0xFFFFAA00),
	/** Known to be flagged by common anti-cheats. */
	DETECTED("Detected", 0xFFFF5555);

	private final String label;
	private final int color;

	Detection(String label, int color) {
		this.label = label;
		this.color = color;
	}

	public String label() { return label; }

	/** ARGB badge color. */
	public int color() { return color; }

	/** Case-insensitive; accepts "use at risk", "use_at_risk", "risk" and the labels. Null when nothing matches. */
	public static Detection parse(String s) {
		if (s == null) return null;
		String k = s.trim().toLowerCase(java.util.Locale.ROOT).replace('_', ' ').replace('-', ' ');
		for (Detection d : values()) if (d.label.toLowerCase(java.util.Locale.ROOT).equals(k)) return d;
		return switch (k) {
			case "safe", "ok", "u" -> UNDETECTED;
			case "risk", "risky", "r" -> USE_AT_RISK;
			case "flagged", "d" -> DETECTED;
			default -> null;
		};
	}
}
