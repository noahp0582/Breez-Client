package breezeclient.breezeclient.core.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

	/** Case-insensitive replace of {@code target} in {@code text}; returns {@code text} itself when nothing matches. */
	public static String replaceIgnoreCase(String text, String target, String with) {
		if (target.isEmpty()) return text;
		String lower = text.toLowerCase(Locale.ROOT), t = target.toLowerCase(Locale.ROOT);
		int idx = lower.indexOf(t);
		if (idx < 0) return text;
		StringBuilder sb = new StringBuilder();
		int from = 0;
		while (idx >= 0) {
			sb.append(text, from, idx).append(with);
			from = idx + t.length();
			idx = lower.indexOf(t, from);
		}
		return sb.append(text, from, text.length()).toString();
	}
}
