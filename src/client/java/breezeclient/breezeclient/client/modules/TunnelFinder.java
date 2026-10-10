package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.tunnel.TunnelScan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds long straight 1x2 corridors underground (strip mines and dug hallways), which natural caves do not have, one
 * loaded chunk at a time at a gentle rate. Also feeds the "Straight tunnel" signature of SusChunks.
 */
public class TunnelFinder extends Module {
	/** A corridor run in world coordinates; it continues from (x, y, z) along +x or +z. */
	public record Run(int x, int y, int z, boolean alongX, int length) {}

	private static TunnelFinder instance;

	private final NumberSetting minLength = add(new NumberSetting("Min length (blocks)", 12, 6, 16), "Detection",
		"Shortest straight run that counts. A run is measured inside one chunk, so 16 is the most it can be.");
	private final NumberSetting maxY = add(new NumberSetting("Highest Y scanned", 56, -50, 120), "Detection",
		"Corridors above this height are ignored, which keeps surface roads and paths out.");
	private final NumberSetting rate = add(new NumberSetting("Chunks per second", 2, 1, 10), "Performance",
		"How many chunks are scanned per second. Each scan reads about 40,000 blocks.");
	private final NumberSetting range = add(new NumberSetting("Draw range", 160, 32, 512), "Display", "How far away tunnels are drawn.");
	private final NumberSetting maxDrawn = add(new NumberSetting("Max drawn", 40, 5, 200), "Display", "Only the nearest tunnels are drawn.");
	private final BoolSetting boxes = add(new BoolSetting("Draw boxes", true), "Display", "Draw a box along each tunnel.");
	private final ColorSetting color = add(new ColorSetting("Color", 0xFFFFA500), "Display", "Box color.", boxes::get);

	private final Map<Long, List<Run>> byChunk = new HashMap<>();
	private final Set<Long> scanned = new HashSet<>();
	private Object lastLevel;
	private int ticks;

	public TunnelFinder() {
		super("TunnelFinder", Category.WORLD);
		instance = this;
	}

	/** Runs found in a chunk; empty while the module is off. */
	public static List<Run> runsIn(int cx, int cz) {
		TunnelFinder m = instance;
		if (m == null || !m.enabled()) return List.of();
		return m.byChunk.getOrDefault(ChunkPos.asLong(cx, cz), List.of());
	}

	public static Set<Long> chunkKeys() {
		TunnelFinder m = instance;
		if (m == null || !m.enabled()) return Set.of();
		return new HashSet<>(m.byChunk.keySet());
	}

	@Override
	public String info() {
		int n = 0;
		for (List<Run> l : byChunk.values()) n += l.size();
		return n == 0 ? "" : String.valueOf(n);
	}

	@Override
	protected void onEnable() { reset(); }

	@Override
	protected void onDisable() { reset(); }

	private void reset() {
		byChunk.clear();
		scanned.clear();
		lastLevel = null;
	}

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) return;
		if (level != lastLevel) {
			reset();
			lastLevel = level;
		}
		ticks++;
		if (ticks % 100 == 0) {
			Set<Long> loaded = ChunkScanner.loadedChunks();
			byChunk.keySet().retainAll(loaded);
			scanned.retainAll(loaded);
		}
		int every = Math.max(1, 20 / Math.max(1, rate.asInt()));
		if (ticks % every != 0) return;
		ChunkPos me = mc.player.chunkPosition();
		long best = 0;
		long bestD = Long.MAX_VALUE;
		boolean found = false;
		for (long key : ChunkScanner.loadedChunks()) {
			if (scanned.contains(key)) continue;
			long dx = ChunkPos.getX(key) - me.x, dz = ChunkPos.getZ(key) - me.z, d = dx * dx + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = key;
				found = true;
			}
		}
		if (!found) return;
		scanned.add(best);
		scan(level, ChunkPos.getX(best), ChunkPos.getZ(best));
	}

	private void scan(ClientLevel level, int cx, int cz) {
		LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
		if (chunk == null) return;
		int minY = level.getMinY();
		int top = Math.min(maxY.asInt(), level.getMaxY() - 1);
		int h = top - minY + 1;
		if (h < 6) return;
		TunnelScan.Grid g = new TunnelScan.Grid(18, 18, h);
		BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
		for (int gx = 0; gx < 18; gx++) {
			for (int gz = 0; gz < 18; gz++) {
				boolean inside = gx >= 1 && gx <= 16 && gz >= 1 && gz <= 16;
				int wx = cx * 16 + gx - 1, wz = cz * 16 + gz - 1;
				for (int gy = 0; gy < h; gy++) {
					mp.set(wx, minY + gy, wz);
					BlockState st = inside ? chunk.getBlockState(mp) : level.getBlockState(mp);
					g.set(gx, gy, gz, st.isSolidRender(), st.isAir());
				}
			}
		}
		List<Run> runs = new ArrayList<>();
		for (TunnelScan.Segment s : TunnelScan.find(g, minLength.asInt())) {
			runs.add(new Run(cx * 16 + s.x() - 1, minY + s.y(), cz * 16 + s.z() - 1, s.alongX(), s.length()));
		}
		if (runs.isEmpty()) byChunk.remove(ChunkPos.asLong(cx, cz));
		else byChunk.put(ChunkPos.asLong(cx, cz), runs);
	}

	@Override
	protected void onRender(RenderFrame f) {
		if (!boxes.get()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		double r2 = range.get() * range.get();
		List<Run> near = new ArrayList<>();
		for (List<Run> l : byChunk.values()) {
			for (Run r : l) {
				double d = mc.player.distanceToSqr(r.x() + (r.alongX() ? r.length() / 2.0 : 0.5), r.y() + 1, r.z() + (r.alongX() ? 0.5 : r.length() / 2.0));
				if (d <= r2) near.add(r);
			}
		}
		near.sort((a, b) -> Double.compare(mc.player.distanceToSqr(a.x(), a.y(), a.z()), mc.player.distanceToSqr(b.x(), b.y(), b.z())));
		for (int i = 0; i < Math.min(near.size(), maxDrawn.asInt()); i++) {
			Run r = near.get(i);
			double x2 = r.alongX() ? r.x() + r.length() : r.x() + 1, z2 = r.alongX() ? r.z() + 1 : r.z() + r.length();
			f.drawBox(r.x(), r.y(), r.z(), x2, r.y() + 2, z2, color.get());
		}
	}
}
