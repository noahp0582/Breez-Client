package breezeclient.breezeclient.core.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Small search ranking used by the module search box and the block/item picker. */
public final class Fuzzy {
	private Fuzzy() {}

	/**
	 * @return a score (higher is better) or -1 when {@code query} does not match {@code text}.
	 *         Exact > prefix > word-prefix > substring > in-order subsequence. Case and "minecraft:" / "_" differences are ignored.
	 */
	public static int score(String query, String text) {
		String q = norm(query), t = norm(text);
		if (q.isEmpty()) return 0;
		if (t.equals(q)) return 1000;
		if (t.startsWith(q)) return 800 - Math.min(100, t.length() - q.length());
		int words = t.indexOf(' ' + q);
		if (words >= 0) return 600 - Math.min(100, words);
		int sub = t.indexOf(q);
		if (sub >= 0) return 400 - Math.min(100, sub);
		int ti = 0, gaps = 0;
		for (int qi = 0; qi < q.length(); qi++) {
			int found = t.indexOf(q.charAt(qi), ti);
			if (found < 0) return -1;
			gaps += found - ti;
			ti = found + 1;
		}
		return Math.max(1, 200 - gaps);
	}

	private static String norm(String s) {
		String l = s.toLowerCase(Locale.ROOT).trim();
		if (l.startsWith("minecraft:")) l = l.substring("minecraft:".length());
		return l.replace('_', ' ');
	}

	/** Items matching the query, best first; ties keep the original order. */
	public static List<String> rank(String query, List<String> items, int limit) {
		record Hit(String s, int score, int idx) {}
		List<Hit> hits = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			int sc = score(query, items.get(i));
			if (sc >= 0) hits.add(new Hit(items.get(i), sc, i));
		}
		hits.sort(Comparator.comparingInt(Hit::score).reversed().thenComparingInt(Hit::idx));
		List<String> out = new ArrayList<>();
		for (Hit h : hits) {
			if (out.size() >= limit) break;
			out.add(h.s());
		}
		return out;
	}
}
