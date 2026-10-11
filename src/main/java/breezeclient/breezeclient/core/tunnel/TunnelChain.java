package breezeclient.breezeclient.core.tunnel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Joins the per-chunk straight corridor runs found by TunnelFinder into whole tunnels. A run is measured inside one
 * chunk (at most 16 blocks), so a long tunnel arrives as several runs on the same line; this puts them back together
 * and reports both ends, which is where a tunnel leads to something.
 */
public final class TunnelChain {
	private TunnelChain() {}

	/** A run: it starts at (x, y, z) and continues {@code length} blocks along +x or +z. */
	public record Run(int x, int y, int z, boolean alongX, int length) {}

	/** A merged tunnel. (x1, z1) is the first block, (x2, z2) the last (inclusive); y is the floor level. */
	public record Chain(int x1, int z1, int x2, int z2, int y, boolean alongX, int length, int runs) {}

	/**
	 * @param gap how many blocks may separate two runs on one line and still count as one tunnel (runs that touch or
	 *            overlap always join, even with 0; larger values bridge a gap where a corner or a door interrupted the scan)
	 */
	public static List<Chain> merge(List<Run> runs, int gap) {
		// one group per line: same direction, same height and the same fixed coordinate
		Map<Long, List<Run>> lines = new HashMap<>();
		for (Run r : runs) {
			if (r.length() <= 0) continue;
			long fixed = r.alongX() ? r.z() : r.x();
			long key = ((long) r.y() & 0xFFFFL) << 48 | (fixed & 0xFFFFFFFFL) << 1 | (r.alongX() ? 1L : 0L);
			lines.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
		}
		List<Chain> out = new ArrayList<>();
		for (List<Run> line : lines.values()) {
			boolean alongX = line.get(0).alongX();
			line.sort(Comparator.comparingInt(r -> alongX ? r.x() : r.z()));
			int start = 0, end = 0, count = 0;
			boolean open = false;
			for (Run r : line) {
				int s = alongX ? r.x() : r.z();
				int e = s + r.length(); // exclusive
				if (open && s <= end + gap) {
					end = Math.max(end, e);
					count++;
					continue;
				}
				if (open) out.add(chain(line.get(0), alongX, start, end, count));
				start = s;
				end = e;
				count = 1;
				open = true;
			}
			if (open) out.add(chain(line.get(0), alongX, start, end, count));
		}
		out.sort(Comparator.comparingInt(Chain::length).reversed());
		return out;
	}

	private static Chain chain(Run any, boolean alongX, int start, int endExclusive, int runs) {
		int len = endExclusive - start;
		return alongX
			? new Chain(start, any.z(), endExclusive - 1, any.z(), any.y(), true, len, runs)
			: new Chain(any.x(), start, any.x(), endExclusive - 1, any.y(), false, len, runs);
	}
}
