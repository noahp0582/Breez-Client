package breezeclient.breezeclient.core.text;

import java.util.List;
import java.util.Locale;

/** Matches registry ids against patterns where '*' stands for any run of characters, e.g. "minecraft:*_shulker_box". */
public final class IdPattern {
	private IdPattern() {}

	public static boolean matches(String pattern, String id) {
		String p = withNamespace(pattern.trim().toLowerCase(Locale.ROOT));
		String s = withNamespace(id.toLowerCase(Locale.ROOT));
		return glob(p, 0, s, 0);
	}

	public static boolean matchesAny(List<String> patterns, String id) {
		for (String p : patterns) if (matches(p, id)) return true;
		return false;
	}

	private static String withNamespace(String s) { return s.contains(":") ? s : "minecraft:" + s; }

	private static boolean glob(String p, int pi, String s, int si) {
		while (pi < p.length()) {
			char c = p.charAt(pi);
			if (c == '*') {
				while (pi < p.length() && p.charAt(pi) == '*') pi++;
				if (pi == p.length()) return true;
				for (int k = si; k <= s.length(); k++) if (glob(p, pi, s, k)) return true;
				return false;
			}
			if (si >= s.length() || s.charAt(si) != c) return false;
			pi++;
			si++;
		}
		return si == s.length();
	}
}
