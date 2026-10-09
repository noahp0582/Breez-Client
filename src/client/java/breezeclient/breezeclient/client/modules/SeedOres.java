package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.client.seed.OreKind;
import breezeclient.breezeclient.client.seed.SeedOreSim;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seed-based ore finder: shows where world generation put ores in chunks the server has sent, even when anti-xray
 * hides them. Needs the real world seed (.seed &lt;value&gt;).
 */
public class SeedOres extends Module {
	private final StringSetting seedOverride = add(new StringSetting("Seed (blank = per-server .seed)", ""));
	private final NumberSetting range = add(new NumberSetting("Range", 96, 16, 256));
	private final NumberSetting simRadius = add(new NumberSetting("Simulate radius (chunks)", 8, 2, 16));
	private final BoolSetting onlyHidden = add(new BoolSetting("Only hidden ores", true));
	private final BoolSetting tracers = add(new BoolSetting("Tracers", false));
	private final EnumMap<OreKind, BoolSetting> kinds = new EnumMap<>(OreKind.class);

	private final Map<Long, Map<BlockPos, BlockState>> sims = new HashMap<>();
	private Long activeSeed;
	private ResourceKey<Level> activeDim;
	private int ticks;
	private String warned = "";

	public SeedOres() {
		super("SeedOres", Category.WORLD);
		for (OreKind k : OreKind.values()) kinds.put(k, add(new BoolSetting(k.label, k.defaultOn)));
	}

	/** The seed in effect, or null when none is set. */
	public Long seed() {
		String s = seedOverride.get().isBlank() ? StaffTracker.profile().seed() : seedOverride.get();
		if (s == null || s.isBlank()) return null;
		return SeedOreSim.parseSeed(s);
	}

	public Map<Long, Map<BlockPos, BlockState>> simulated() { return sims; }

	@Override
	public String info() {
		int n = 0;
		for (var m : sims.values()) n += m.size();
		return String.valueOf(n);
	}

	@Override
	protected void onEnable() {
		sims.clear();
		activeSeed = null;
	}

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) return;
		Long seed = seed();
		if (seed == null) {
			if (!"seed".equals(warned)) {
				EspModule.say("SeedOres needs the world seed: .seed <number or text>");
				warned = "seed";
			}
			return;
		}
		if (!SeedOreSim.supports(level.dimension())) return;
		if (!seed.equals(activeSeed) || level.dimension() != activeDim) {
			sims.clear();
			activeSeed = seed;
			activeDim = level.dimension();
		}
		if (++ticks % 100 == 0) sims.keySet().removeIf(k -> !ChunkScanner.loadedChunks().contains(k));

		ChunkPos mine = mc.player.chunkPosition();
		int r = simRadius.asInt();
		long deadline = System.nanoTime() + 6_000_000L;
		List<Long> todo = new ArrayList<>();
		for (long key : ChunkScanner.loadedChunks()) {
			if (sims.containsKey(key)) continue;
			int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
			if (Math.max(Math.abs(cx - mine.x), Math.abs(cz - mine.z)) > r) continue;
			todo.add(key);
		}
		todo.sort((a, b) -> Long.compare(dist(a, mine), dist(b, mine)));
		for (long key : todo) {
			if (System.nanoTime() > deadline) break;
			ChunkPos cp = new ChunkPos(key);
			if (!SeedOreSim.ready(level, cp)) continue;
			sims.put(key, SeedOreSim.simulate(level, cp, seed));
		}
	}

	private static long dist(long key, ChunkPos mine) {
		long dx = ChunkPos.getX(key) - mine.x, dz = ChunkPos.getZ(key) - mine.z;
		return dx * dx + dz * dz;
	}

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		var cam = mc.gameRenderer.getMainCamera();
		var pos = cam.position();
		var fwd = cam.forwardVector();
		double r2 = range.get() * range.get();
		for (var chunk : sims.values()) {
			for (var en : chunk.entrySet()) {
				BlockPos p = en.getKey();
				OreKind kind = OreKind.of(en.getValue());
				if (kind == null || !kinds.get(kind).get()) continue;
				double dx = p.getX() + 0.5 - pos.x, dy = p.getY() + 0.5 - pos.y, dz = p.getZ() + 0.5 - pos.z;
				if (dx * dx + dy * dy + dz * dz > r2) continue;
				BlockState actual = mc.level.getBlockState(p);
				if (actual.isAir() || !actual.getFluidState().isEmpty()) continue; // carved away or flooded after generation
				if (onlyHidden.get() && OreKind.of(actual) != null) continue; // server already shows it
				f.drawBox(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, kind.color);
				if (tracers.get()) f.drawLine(pos.x + fwd.x() * 0.3, pos.y + fwd.y() * 0.3, pos.z + fwd.z() * 0.3, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, kind.color);
			}
		}
	}

	/** Compares predictions with the ore blocks actually present in the simulated chunks. Only meaningful without anti-xray. */
	public String verify() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) return "not in a world";
		Map<OreKind, int[]> stats = new EnumMap<>(OreKind.class); // {predicted, predictedAndActual, actual, actualAndPredicted}
		for (OreKind k : OreKind.values()) stats.put(k, new int[4]);
		Map<BlockPos, BlockState> predicted = new HashMap<>();
		int[][] missByY = new int[OreKind.values().length][14];
		int[][] hitByY = new int[OreKind.values().length][14];
		for (var m : sims.values()) predicted.putAll(m);
		int chunks = 0;
		for (long key : sims.keySet()) {
			LevelChunk c = level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), false);
			if (c == null) continue;
			chunks++;
			int minSection = c.getMinY() >> 4;
			LevelChunkSection[] sections = c.getSections();
			for (int i = 0; i < sections.length; i++) {
				if (sections[i].hasOnlyAir()) continue;
				for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
					BlockState s = sections[i].getBlockState(x, y, z);
					OreKind k = OreKind.of(s);
					if (k == null) continue;
					BlockPos p = new BlockPos(c.getPos().getMinBlockX() + x, ((minSection + i) << 4) + y, c.getPos().getMinBlockZ() + z);
					stats.get(k)[2]++;
					BlockState pr = predicted.get(p);
					int bucket = Math.max(0, Math.min(13, (p.getY() + 64) / 32));
					if (pr != null && pr.getBlock() == s.getBlock()) { stats.get(k)[3]++; hitByY[k.ordinal()][bucket]++; }
					else missByY[k.ordinal()][bucket]++;
				}
			}
		}
		for (var en : predicted.entrySet()) {
			OreKind k = OreKind.of(en.getValue());
			if (k == null) continue;
			ChunkPos cp = new ChunkPos(en.getKey());
			if (!sims.containsKey(cp.toLong())) continue; // only judge inside simulated chunks
			stats.get(k)[0]++;
			BlockState actual = level.getBlockState(en.getKey());
			if (actual.getBlock() == en.getValue().getBlock()) stats.get(k)[1]++;
		}
		var st = SeedOreSim.STATS;
		StringBuilder sb = new StringBuilder("SIM positions=" + st[0] + " heightFail=" + st[1] + " veins=" + st[2] + " biomeNull=" + st[4] + " chunks=" + st[5] + " featuresRun=" + st[6] + " noBiomes=" + st[7] + "\n" + "SeedOres check over " + chunks + " chunks (predicted: matched/total, actual: found/total):\n");
		for (OreKind k : OreKind.values()) {
			int[] s = stats.get(k);
			if (s[0] == 0 && s[2] == 0) continue;
			sb.append(String.format("  %-15s predicted %d/%d (%.0f%%)  actual %d/%d (%.0f%%)%n", k.label, s[1], s[0], s[0] == 0 ? 0.0 : 100.0 * s[1] / s[0],
				s[3], s[2], s[2] == 0 ? 0.0 : 100.0 * s[3] / s[2]));
		}
		for (OreKind k : new OreKind[] {OreKind.DIAMOND, OreKind.IRON, OreKind.COAL}) {
			sb.append("  " + k.label + " by Y (bucket = 32 blocks from -64): hit/miss\n   ");
			for (int b = 0; b < 14; b++) sb.append(String.format(" %d:%d/%d", b, hitByY[k.ordinal()][b], missByY[k.ordinal()][b]));
			sb.append('\n');
		}
		return sb.toString();
	}
}
