package breezeclient.breezeclient.core.tunnel;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds long straight 1-wide, 2-high corridors in a block grid: the shape of a strip mine or a dug hallway, which natural
 * caves do not have. Pure logic over a boolean grid so it can be tested without a world.
 */
public final class TunnelScan {
	private TunnelScan() {}

	/** A corridor cell run. (x, y, z) is the first cell in grid coordinates; the run continues along +x or +z. */
	public record Segment(int x, int y, int z, boolean alongX, int length) {}

	/** Solid and empty flags for a w x d x h block volume; x, z index the horizontal plane. Out-of-range reads are "neither". */
	public static final class Grid {
		public final int w, d, h;
		private final boolean[] solid, empty;

		public Grid(int w, int d, int h) {
			this.w = w;
			this.d = d;
			this.h = h;
			this.solid = new boolean[w * d * h];
			this.empty = new boolean[w * d * h];
		}

		private int idx(int x, int y, int z) { return (x * d + z) * h + y; }

		private boolean in(int x, int y, int z) { return x >= 0 && z >= 0 && y >= 0 && x < w && z < d && y < h; }

		public void set(int x, int y, int z, boolean isSolid, boolean isEmpty) {
			if (!in(x, y, z)) return;
			solid[idx(x, y, z)] = isSolid;
			empty[idx(x, y, z)] = isEmpty;
		}

		public boolean solid(int x, int y, int z) { return in(x, y, z) && solid[idx(x, y, z)]; }

		public boolean empty(int x, int y, int z) { return in(x, y, z) && empty[idx(x, y, z)]; }
	}

	/** A cell where a 1x2 corridor passes along the given axis: open two high, floor and ceiling solid, both walls solid. */
	private static boolean corridorCell(Grid g, int x, int y, int z, boolean alongX) {
		if (!g.empty(x, y, z) || !g.empty(x, y + 1, z) || !g.solid(x, y - 1, z) || !g.solid(x, y + 2, z)) return false;
		if (alongX) return g.solid(x, y, z - 1) && g.solid(x, y + 1, z - 1) && g.solid(x, y, z + 1) && g.solid(x, y + 1, z + 1);
		return g.solid(x - 1, y, z) && g.solid(x - 1, y + 1, z) && g.solid(x + 1, y, z) && g.solid(x + 1, y + 1, z);
	}

	/**
	 * All corridor runs of at least {@code minLen} cells whose cells all lie inside the grid margin (so their walls are
	 * readable): x and z from 1 to size - 2.
	 */
	public static List<Segment> find(Grid g, int minLen) {
		List<Segment> out = new ArrayList<>();
		for (int y = 1; y < g.h - 2; y++) {
			for (int z = 1; z < g.d - 1; z++) {
				int run = 0;
				for (int x = 1; x <= g.w - 1; x++) {
					boolean ok = x < g.w - 1 && corridorCell(g, x, y, z, true);
					if (ok) {
						run++;
					} else {
						if (run >= minLen) out.add(new Segment(x - run, y, z, true, run));
						run = 0;
					}
				}
			}
			for (int x = 1; x < g.w - 1; x++) {
				int run = 0;
				for (int z = 1; z <= g.d - 1; z++) {
					boolean ok = z < g.d - 1 && corridorCell(g, x, y, z, false);
					if (ok) {
						run++;
					} else {
						if (run >= minLen) out.add(new Segment(x, y, z - run, false, run));
						run = 0;
					}
				}
			}
		}
		return out;
	}
}
