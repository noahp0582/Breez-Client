package breezeclient.breezeclient.core.sus;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure scoring for the sus chunk finder. */
public final class SusScore {
	private SusScore() {}

	/**
	 * Parses "block_id=weight" entries. A bare id gets weight 1. Ids without a namespace get "minecraft:".
	 * Malformed entries are skipped.
	 */
	public static Map<String, Double> parseWeights(List<String> entries) {
		Map<String, Double> out = new HashMap<>();
		for (String raw : entries) {
			String e = raw.trim().toLowerCase(Locale.ROOT);
			if (e.isEmpty()) continue;
			String id = e;
			double weight = 1.0;
			int eq = e.indexOf('=');
			if (eq >= 0) {
				id = e.substring(0, eq).trim();
				String w = e.substring(eq + 1).trim();
				if (w.isEmpty()) continue;
				try {
					weight = Double.parseDouble(w);
				} catch (NumberFormatException ex) {
					continue;
				}
			}
			if (id.isEmpty()) continue;
			if (!id.contains(":")) id = "minecraft:" + id;
			out.put(id, weight);
		}
		return out;
	}

	public static double score(Map<String, Integer> counts, Map<String, Double> weights, int blockEntities, double blockEntityWeight) {
		double s = blockEntities * blockEntityWeight;
		for (Map.Entry<String, Integer> en : counts.entrySet()) {
			Double w = weights.get(en.getKey());
			if (w != null) s += w * en.getValue();
		}
		return s;
	}
}
