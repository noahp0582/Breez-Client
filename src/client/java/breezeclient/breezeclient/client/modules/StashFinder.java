package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.session.Session;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.gui.ColorMath;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.sus.SusScore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds stashes and bases by what is in a chunk. Presets pick what counts (storage, redstone, decoration, spawners), natural
 * dungeons and mineshaft spawners are ignored, and every find is logged to stashes.csv.
 */
public class StashFinder extends Module {
	public enum Preset { STORAGE, REDSTONE, DECORATION, SPAWNERS, EVERYTHING }

	public record Result(int chunkX, int chunkZ, double score, BlockPos min, BlockPos max, String summary) {
		public BlockPos center() { return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2); }
	}

	private final EnumSetting<Preset> preset = add(new EnumSetting<>("Preset", Preset.EVERYTHING), "Detection", "What kind of builds count as a stash.");
	private final NumberSetting threshold = add(new NumberSetting("Threshold", 15, 1, 500), "Detection", "Minimum score for a chunk to be reported.");
	private final NumberSetting blockEntityWeight = add(new NumberSetting("Block entity weight", 0.25, 0, 5), "Detection", "Extra score per block entity in the chunk.");
	private final BoolSetting ignoreNatural = add(new BoolSetting("Ignore natural structures", true), "Detection", "Skip dungeon and mineshaft spawners and the chests around them.");
	private final NumberSetting minDistance = add(new NumberSetting("Ignore chunks within", 3, 0, 32), "Detection", "Do not report chunks this close to you (your own base).");
	private final BoolSetting toast = add(new BoolSetting("Toast on find", true), "Alerts", "Pop-up notification for each new stash.");
	private final BoolSetting waypoint = add(new BoolSetting("Waypoint on find", true), "Alerts", "Save a waypoint for each new stash.");
	private final BoolSetting log = add(new BoolSetting("Log to stashes.csv", true), "Alerts", "Append every find to config/breez-client/stashes.csv.");
	private final NumberSetting range = add(new NumberSetting("Highlight range", 512, 32, 2048), "Render", "How far away stashes are drawn.");

	private static StashFinder instance;
	private ScanTarget target;
	private Map<Block, Double> weights = Map.of();
	private Preset applied;
	private final List<Result> results = new ArrayList<>();
	private final Set<Long> announced = new HashSet<>();
	private int ticks;

	public StashFinder() {
		super("StashFinder", Category.BASE_FINDING);
		instance = this;
	}

	public static List<Result> top(int n) {
		if (instance == null) return List.of();
		List<Result> copy = new ArrayList<>(instance.results);
		copy.sort(Comparator.comparingDouble(Result::score).reversed());
		return copy.subList(0, Math.min(n, copy.size()));
	}

	public static void clear() {
		if (instance == null) return;
		instance.announced.clear();
		instance.results.clear();
		try {
			Files.deleteIfExists(csv());
		} catch (IOException ignored) {
			// nothing to delete
		}
	}

	public static Path csv() { return FabricLoader.getInstance().getConfigDir().resolve("breez-client").resolve("stashes.csv"); }

	private static Map<Block, Double> weightsFor(Preset p) {
		Map<Block, Double> w = new HashMap<>();
		boolean all = p == Preset.EVERYTHING;
		if (all || p == Preset.STORAGE) {
			w.put(Blocks.CHEST, 1.0);
			w.put(Blocks.TRAPPED_CHEST, 1.5);
			w.put(Blocks.BARREL, 1.0);
			w.put(Blocks.ENDER_CHEST, 0.5);
			w.put(Blocks.HOPPER, 1.0);
			w.put(Blocks.DROPPER, 1.0);
			w.put(Blocks.DISPENSER, 1.0);
			w.put(Blocks.FURNACE, 0.4);
			w.put(Blocks.BLAST_FURNACE, 0.4);
			w.put(Blocks.SMOKER, 0.4);
			for (Block b : BuiltInRegistries.BLOCK) if (BuiltInRegistries.BLOCK.getKey(b).getPath().endsWith("shulker_box")) w.put(b, 3.0);
		}
		if (all || p == Preset.REDSTONE) {
			w.put(Blocks.PISTON, 3.0);
			w.put(Blocks.STICKY_PISTON, 3.0);
			w.put(Blocks.OBSERVER, 2.0);
			w.put(Blocks.REPEATER, 0.5);
			w.put(Blocks.COMPARATOR, 1.0);
			w.put(Blocks.REDSTONE_LAMP, 1.0);
			w.put(Blocks.TNT, 1.0);
			w.put(Blocks.DAYLIGHT_DETECTOR, 1.0);
			w.merge(Blocks.HOPPER, 1.0, Double::sum);
			w.merge(Blocks.DROPPER, 1.0, Double::sum);
			w.merge(Blocks.DISPENSER, 1.0, Double::sum);
		}
		if (all || p == Preset.DECORATION) {
			for (Block b : BuiltInRegistries.BLOCK) {
				String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
				if (path.endsWith("_concrete") || path.endsWith("_glazed_terracotta")) w.put(b, 0.3);
				else if (path.endsWith("_stained_glass") || path.endsWith("_stained_glass_pane")) w.put(b, 0.2);
			}
			w.put(Blocks.BOOKSHELF, 0.4);
			w.put(Blocks.CRYING_OBSIDIAN, 0.5);
			w.put(Blocks.OBSIDIAN, 0.15);
			w.put(Blocks.GLOWSTONE, 0.2);
			w.put(Blocks.SEA_LANTERN, 0.3);
			w.put(Blocks.BEACON, 10.0);
			w.put(Blocks.ENCHANTING_TABLE, 1.5);
			w.put(Blocks.ANVIL, 0.5);
		}
		if (all || p == Preset.SPAWNERS) {
			w.put(Blocks.SPAWNER, 5.0);
			w.put(Blocks.TRIAL_SPAWNER, 2.0);
		}
		return w;
	}

	@Override
	protected void onEnable() {
		applied = null;
		rebuild();
	}

	private void rebuild() {
		applied = preset.get();
		weights = weightsFor(applied);
		Set<Block> set = weights.keySet();
		if (target == null) {
			target = new ScanTarget(s -> set.contains(s.getBlock()));
			ChunkScanner.add(target);
		} else {
			target.setPredicate(s -> set.contains(s.getBlock()));
		}
		announced.clear();
		results.clear();
	}

	@Override
	protected void onDisable() {
		if (target != null) ChunkScanner.remove(target);
		target = null;
		results.clear();
	}

	@Override
	public String info() { return results.isEmpty() ? "" : String.valueOf(results.size()); }

	@Override
	protected void onTick() {
		if (target == null) return;
		if (applied != preset.get()) rebuild();
		if (++ticks % 60 != 0) return;
		evaluate();
	}

	private boolean naturalSpawner(ClientLevel level, BlockPos p) {
		int moss = 0, webs = 0;
		for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++) {
			var s = level.getBlockState(p.offset(x, y, z));
			if (s.is(Blocks.MOSSY_COBBLESTONE)) moss++;
			else if (s.is(Blocks.COBWEB)) webs++;
		}
		return moss >= 8 || webs >= 6;
	}

	private void evaluate() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) return;
		ChunkPos mine = mc.player.chunkPosition();
		results.clear();
		for (var en : target.hitsByChunk().entrySet()) {
			int cx = ChunkPos.getX(en.getKey()), cz = ChunkPos.getZ(en.getKey());
			if (Math.max(Math.abs(cx - mine.x), Math.abs(cz - mine.z)) <= minDistance.asInt()) continue;
			List<BlockPos> natural = new ArrayList<>();
			if (ignoreNatural.get()) {
				for (BlockPos p : en.getValue()) if (level.getBlockState(p).is(Blocks.SPAWNER) && naturalSpawner(level, p)) natural.add(p);
			}
			Map<String, Integer> counts = new HashMap<>();
			Map<String, Double> w = new HashMap<>();
			int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
			for (BlockPos p : en.getValue()) {
				boolean skip = false;
				for (BlockPos n : natural) {
					if (p.equals(n) || (level.getBlockState(p).is(Blocks.CHEST) && p.distSqr(n) <= 64)) {
						skip = true;
						break;
					}
				}
				if (skip) continue;
				Block b = level.getBlockState(p).getBlock();
				String id = BuiltInRegistries.BLOCK.getKey(b).toString();
				counts.merge(id, 1, Integer::sum);
				w.put(id, weights.getOrDefault(b, 0.0));
				minX = Math.min(minX, p.getX());
				minY = Math.min(minY, p.getY());
				minZ = Math.min(minZ, p.getZ());
				maxX = Math.max(maxX, p.getX());
				maxY = Math.max(maxY, p.getY());
				maxZ = Math.max(maxZ, p.getZ());
			}
			if (counts.isEmpty()) continue;
			int be = level.getChunk(cx, cz).getBlockEntities().size();
			double score = SusScore.score(counts, w, be, blockEntityWeight.get());
			if (score < threshold.get()) continue;
			String summary = summarize(counts);
			Result r = new Result(cx, cz, score, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), summary);
			results.add(r);
			if (announced.add(en.getKey())) found(r);
		}
	}

	private static String summarize(Map<String, Integer> counts) {
		List<Map.Entry<String, Integer>> l = new ArrayList<>(counts.entrySet());
		l.sort((a, b) -> b.getValue() - a.getValue());
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(4, l.size()); i++) sb.append(i > 0 ? ", " : "").append(l.get(i).getValue()).append(' ').append(l.get(i).getKey().replace("minecraft:", ""));
		return sb.toString();
	}

	private void found(Result r) {
		BlockPos c = r.center();
		if (toast.get()) Toasts.show("Stash found", String.format("score %.0f at %d %d %d: %s", r.score(), c.getX(), c.getY(), c.getZ(), r.summary()), 0xFFFFD23F);
		if (waypoint.get()) Waypoints.add("Stash " + Math.round(r.score()), c);
		if (log.get()) {
			try {
				Path f = csv();
				Files.createDirectories(f.getParent());
				boolean fresh = !Files.exists(f);
				String dim = Minecraft.getInstance().level == null ? "?" : Minecraft.getInstance().level.dimension().identifier().toString();
				String line = LocalDateTime.now() + "," + Session.serverKey() + "," + dim + "," + r.chunkX() + "," + r.chunkZ() + "," + c.getX() + "," + c.getY() + "," + c.getZ()
					+ "," + String.format("%.1f", r.score()) + ",\"" + r.summary() + "\"" + System.lineSeparator();
				if (fresh) Files.writeString(f, "time,server,dimension,chunkX,chunkZ,x,y,z,score,contents" + System.lineSeparator(), StandardOpenOption.CREATE);
				Files.writeString(f, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (IOException ignored) {
				// logging is best effort
			}
		}
	}

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		double r = range.get();
		for (Result res : results) {
			BlockPos c = res.center();
			if (mc.player.distanceToSqr(c.getX(), c.getY(), c.getZ()) > r * r) continue;
			double t = Math.min(1.0, (res.score() - threshold.get()) / Math.max(1.0, threshold.get() * 2));
			int color = ColorMath.mix(0xFFFFD23F, 0xFFFF3030, t);
			f.drawBox(res.min().getX() - 1, res.min().getY() - 1, res.min().getZ() - 1, res.max().getX() + 2, res.max().getY() + 2, res.max().getZ() + 2, color);
			f.drawText(c.getX() + 0.5, res.max().getY() + 3, c.getZ() + 0.5, String.format("%.0f: %s", res.score(), res.summary()), color);
		}
	}
}
