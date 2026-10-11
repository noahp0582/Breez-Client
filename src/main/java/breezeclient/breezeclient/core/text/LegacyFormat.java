package breezeclient.breezeclient.core.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Minecraft's classic "&amp;a" / "§a" formatting codes into styled text segments, so a custom name can be colored
 * and styled from a plain settings box. Pure logic: the client turns the segments into chat components.
 */
public final class LegacyFormat {
	private LegacyFormat() {}

	/** color is 0xRRGGBB or -1 for "inherit". */
	public record Segment(String text, int color, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated) {}

	private static final int[] COLORS = {
		0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	public static List<Segment> parse(String input) {
		List<Segment> out = new ArrayList<>();
		if (input == null || input.isEmpty()) return out;
		StringBuilder cur = new StringBuilder();
		int color = -1;
		boolean bold = false, italic = false, underlined = false, strike = false, obf = false;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if ((c == '&' || c == '§') && i + 1 < input.length()) {
				char code = Character.toLowerCase(input.charAt(i + 1));
				int hex = Character.digit(code, 16);
				boolean isFormat = hex >= 0 || "klmnor".indexOf(code) >= 0;
				if (isFormat) {
					if (cur.length() > 0) {
						out.add(new Segment(cur.toString(), color, bold, italic, underlined, strike, obf));
						cur.setLength(0);
					}
					if (hex >= 0) { // a color code also clears the styles, like the game does
						color = COLORS[hex];
						bold = italic = underlined = strike = obf = false;
					} else {
						switch (code) {
							case 'l' -> bold = true;
							case 'o' -> italic = true;
							case 'n' -> underlined = true;
							case 'm' -> strike = true;
							case 'k' -> obf = true;
							default -> { // r
								color = -1;
								bold = italic = underlined = strike = obf = false;
							}
						}
					}
					i++;
					continue;
				}
			}
			cur.append(c);
		}
		if (cur.length() > 0) out.add(new Segment(cur.toString(), color, bold, italic, underlined, strike, obf));
		return out;
	}

	/** The text without any formatting codes. */
	public static String strip(String input) {
		StringBuilder sb = new StringBuilder();
		for (Segment s : parse(input)) sb.append(s.text());
		return sb.toString();
	}

	private static boolean isNameChar(char c) { return Character.isLetterOrDigit(c) || c == '_'; }

	/**
	 * Index of the first case-insensitive occurrence of {@code name} at or after {@code from} that is a whole name, i.e. not
	 * glued to more name characters (letters, digits, underscore): "Bob" matches in "Bob joined" but not in "Bobby".
	 * Returns -1 when there is none.
	 */
	public static int indexOfName(String text, String name, int from) {
		if (name == null || name.isEmpty()) return -1;
		// compared in place: lower-casing the whole text can change its length ("İ" becomes two chars) and shift every index
		int n = name.length();
		for (int idx = Math.max(0, from); idx + n <= text.length(); idx++) {
			if (!text.regionMatches(true, idx, name, 0, n)) continue;
			boolean leftOk = idx == 0 || !isNameChar(text.charAt(idx - 1));
			boolean rightOk = idx + n >= text.length() || !isNameChar(text.charAt(idx + n));
			if (leftOk && rightOk) return idx;
		}
		return -1;
	}

	/** Replaces every whole-name occurrence (see {@link #indexOfName}); returns {@code text} itself when nothing matches. */
	public static String replaceName(String text, String name, String with) {
		int idx = indexOfName(text, name, 0);
		if (idx < 0) return text;
		StringBuilder sb = new StringBuilder();
		int from = 0;
		while (idx >= 0) {
			sb.append(text, from, idx).append(with);
			from = idx + name.length();
			idx = indexOfName(text, name, from);
		}
		return sb.append(text, from, text.length()).toString();
	}
}
