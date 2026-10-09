package breezeclient.breezeclient.core.geode;

import breezeclient.breezeclient.core.nav.Pos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Groups budding amethyst into geodes and measures how grown their crystals are.
 *
 * <p>Only crystals touching a budding block can still grow (small -> medium -> large -> cluster), so only those count:
 * crystals on plain amethyst blocks are frozen at whatever stage world generation gave them.
 */
public final class GeodeAnalyzer {
	private GeodeAnalyzer() {}

	public enum Stage {
		SMALL(1), MEDIUM(2), LARGE(3), CLUSTER(4);

		public final int steps;

		Stage(int steps) { this.steps = steps; }
	}

	public record Bud(Pos pos, Stage stage) {}

	/**
	 * @param anchor      lowest budding block, a stable identifier while chunks load
	 * @param grownPercent clusters / all growing crystals, 0-100 (100 when there are none)
	 * @param coverage     growing crystals / (growing crystals + open slots), 0-100
	 * @param estHours     rough hours of loaded-chunk time the crystals have been growing, from the vanilla random tick rate
	 */
	public record Geode(Pos anchor, Pos center, int budding, int small, int medium, int large, int cluster, int openSlots,
						double grownPercent, double coverage, double estHours) {
		public int buds() { return small + medium + large + cluster; }

		public boolean fullyGrown(double thresholdPercent, int minBuds) { return buds() >= minBuds && grownPercent >= thresholdPercent; }
	}

	/**
	 * Expected random ticks between two growth steps on one face: a budding block is random-ticked 3/4096 per game tick
	 * (default randomTickSpeed), grows with chance 1/5, and picks one of 6 faces.
	 */
	public static final double TICKS_PER_STEP = 4096.0 / 3.0 * 5.0 * 6.0;

	private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	/**
	 * @param budding   positions of budding amethyst blocks
	 * @param buds      every bud / cluster found nearby
	 * @param openSlot  true when a bud could start growing at that position (air or a water source)
	 * @param linkDist  budding blocks closer than this (per axis) belong to the same geode
	 */
	public static List<Geode> analyze(Collection<Pos> budding, Collection<Bud> buds, Predicate<Pos> openSlot, int linkDist) {
		List<Pos> list = new ArrayList<>(new HashSet<>(budding));
		if (list.isEmpty()) return List.of();
		int n = list.size();
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) parent[i] = i;

		// spatial hash so linking is near-linear
		int cell = Math.max(1, linkDist);
		Map<Long, List<Integer>> grid = new HashMap<>();
		for (int i = 0; i < n; i++) grid.computeIfAbsent(key(list.get(i), cell), k -> new ArrayList<>()).add(i);
		for (int i = 0; i < n; i++) {
			Pos p = list.get(i);
			int cx = Math.floorDiv(p.x(), cell), cy = Math.floorDiv(p.y(), cell), cz = Math.floorDiv(p.z(), cell);
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				List<Integer> bucket = grid.get(pack(cx + dx, cy + dy, cz + dz));
				if (bucket == null) continue;
				for (int j : bucket) {
					if (j <= i) continue;
					Pos q = list.get(j);
					if (Math.abs(p.x() - q.x()) <= linkDist && Math.abs(p.y() - q.y()) <= linkDist && Math.abs(p.z() - q.z()) <= linkDist) union(parent, i, j);
				}
			}
		}

		Map<Pos, Stage> budAt = new HashMap<>();
		for (Bud b : buds) budAt.put(b.pos(), b.stage());

		Map<Integer, List<Pos>> groups = new HashMap<>();
		for (int i = 0; i < n; i++) groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(list.get(i));

		List<Geode> out = new ArrayList<>();
		for (List<Pos> g : groups.values()) {
			Set<Pos> seenBuds = new HashSet<>();
			Set<Pos> seenOpen = new HashSet<>();
			int[] count = new int[5];
			long sx = 0, sy = 0, sz = 0;
			Pos anchor = g.get(0);
			for (Pos b : g) {
				sx += b.x();
				sy += b.y();
				sz += b.z();
				if (compare(b, anchor) < 0) anchor = b;
				for (int[] d : DIRS) {
					Pos f = new Pos(b.x() + d[0], b.y() + d[1], b.z() + d[2]);
					Stage st = budAt.get(f);
					if (st != null) {
						if (seenBuds.add(f)) count[st.steps]++;
					} else if (!budAt.containsKey(f) && openSlot.test(f)) {
						seenOpen.add(f);
					}
				}
			}
			int buds2 = count[1] + count[2] + count[3] + count[4];
			double grown = buds2 == 0 ? 100.0 : 100.0 * count[4] / buds2;
			double coverage = buds2 + seenOpen.size() == 0 ? 0.0 : 100.0 * buds2 / (buds2 + seenOpen.size());
			double steps = count[1] + 2.0 * count[2] + 3.0 * count[3] + 4.0 * count[4];
			double estHours = buds2 == 0 ? 0.0 : steps / buds2 * TICKS_PER_STEP / 72000.0;
			Pos center = new Pos((int) Math.round(sx / (double) g.size()), (int) Math.round(sy / (double) g.size()), (int) Math.round(sz / (double) g.size()));
			out.add(new Geode(anchor, center, g.size(), count[1], count[2], count[3], count[4], seenOpen.size(), grown, coverage, estHours));
		}
		out.sort(Comparator.comparingInt((Geode g) -> g.anchor().x()).thenComparingInt(g -> g.anchor().y()).thenComparingInt(g -> g.anchor().z()));
		return out;
	}

	private static int compare(Pos a, Pos b) {
		if (a.x() != b.x()) return Integer.compare(a.x(), b.x());
		if (a.y() != b.y()) return Integer.compare(a.y(), b.y());
		return Integer.compare(a.z(), b.z());
	}

	private static long key(Pos p, int cell) { return pack(Math.floorDiv(p.x(), cell), Math.floorDiv(p.y(), cell), Math.floorDiv(p.z(), cell)); }

	private static long pack(int x, int y, int z) { return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF); }

	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	private static void union(int[] parent, int a, int b) {
		int ra = find(parent, a), rb = find(parent, b);
		if (ra != rb) parent[ra] = rb;
	}
}
