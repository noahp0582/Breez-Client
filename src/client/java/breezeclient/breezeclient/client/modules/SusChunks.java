package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.world.BlockIds;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.sus.SusScore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores loaded chunks by how much player-built stuff they contain (pistons, chests, hoppers, block entities...)
 * and highlights the ones above a threshold. Weights and threshold are configurable.
 */
public class SusChunks extends Module {
	public record Result(int chunkX, int chunkZ, double score, BlockPos min, BlockPos max) {
		public BlockPos center() { return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2); }
	}

	private static final List<String> DEFAULTS = defaults();

	private final StringListSetting indicators = add(new StringListSetting("Indicators", DEFAULTS));
	private final NumberSetting blockEntityWeight = add(new NumberSetting("Block entity weight", 0.25, 0, 5));
	private final NumberSetting threshold = add(new NumberSetting("Threshold", 12, 1, 500));
	private final NumberSetting minDistance = add(new NumberSetting("Ignore chunks within", 4, 0, 32));
	private final NumberSetting range = add(new NumberSetting("Highlight range", 512, 32, 2048));
	private final BoolSetting notify = add(new BoolSetting("Notify", true));
	private final BoolSetting waypoint = add(new BoolSetting("Waypoint", false));

	private static SusChunks instance;
	private ScanTarget target;
	private List<String> applied = List.of();
	private Map<String, Double> weights = Map.of();
	private final List<Result> results = new ArrayList<>();
	private final Set<Long> announced = new HashSet<>();
	private int ticks;

	public SusChunks() {
		super("SusChunks", Category.WORLD);
		instance = this;
	}

	private static List<String> defaults() {
		List<String> l = new ArrayList<>(List.of(
			"minecraft:piston=3", "minecraft:sticky_piston=3", "minecraft:observer=2", "minecraft:hopper=2",
			"minecraft:dispenser=1.5", "minecraft:dropper=1.5", "minecraft:chest=1", "minecraft:trapped_chest=1.5",
			"minecraft:barrel=1", "minecraft:ender_chest=2", "minecraft:furnace=0.5", "minecraft:blast_furnace=0.5",
			"minecraft:beacon=10", "minecraft:shulker_box=3", "minecraft:redstone_lamp=1", "minecraft:comparator=1",
			"minecraft:repeater=0.5", "minecraft:crafting_table=0.25", "minecraft:enchanting_table=2"));
		for (String c : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
			l.add("minecraft:" + c + "_shulker_box=3");
		}
		return l;
	}

	public static List<Result> top(int n) {
		if (instance == null) return List.of();
		List<Result> copy = new ArrayList<>(instance.results);
		copy.sort(Comparator.comparingDouble(Result::score).reversed());
		return copy.subList(0, Math.min(n, copy.size()));
	}

	public static void resetAnnounced() {
		if (instance != null) instance.announced.clear();
	}

	private void rebuild() {
		applied = List.copyOf(indicators.get());
		weights = SusScore.parseWeights(applied);
		Set<Block> blocks = new HashSet<>();
		for (String id : weights.keySet()) {
			Block b = BlockIds.parse(id);
			if (b != null) blocks.add(b);
		}
		if (target == null) {
			target = ScanTarget.ofBlocks(blocks);
			ChunkScanner.add(target);
		} else {
			target.setPredicate(s -> blocks.contains(s.getBlock()));
		}
	}

	@Override
	protected void onEnable() {
		target = null;
		results.clear();
		rebuild();
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
		if (!applied.equals(indicators.get())) rebuild();
		if (++ticks % 40 != 0) return;
		evaluate();
	}

	private void evaluate() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) return;
		ChunkPos mine = mc.player.chunkPosition();
		results.clear();
		for (Map.Entry<Long, List<BlockPos>> en : target.hitsByChunk().entrySet()) {
			int cx = ChunkPos.getX(en.getKey()), cz = ChunkPos.getZ(en.getKey());
			if (Math.max(Math.abs(cx - mine.x), Math.abs(cz - mine.z)) <= minDistance.asInt()) continue;
			Map<String, Integer> counts = new HashMap<>();
			int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
			for (BlockPos p : en.getValue()) {
				counts.merge(BlockIds.idOf(level.getBlockState(p).getBlock()), 1, Integer::sum);
				minX = Math.min(minX, p.getX());
				minY = Math.min(minY, p.getY());
				minZ = Math.min(minZ, p.getZ());
				maxX = Math.max(maxX, p.getX());
				maxY = Math.max(maxY, p.getY());
				maxZ = Math.max(maxZ, p.getZ());
			}
			int be = level.getChunk(cx, cz).getBlockEntities().size();
			double score = SusScore.score(counts, weights, be, blockEntityWeight.get());
			if (score < threshold.get()) continue;
			Result r = new Result(cx, cz, score, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
			results.add(r);
			if (announced.add(en.getKey())) {
				BlockPos c = r.center();
				if (notify.get()) EspModule.say(String.format("Suspicious chunk (%d, %d) score %.1f near %d %d %d", cx, cz, score, c.getX(), c.getY(), c.getZ()));
				if (waypoint.get()) Waypoints.add("Sus chunk", c);
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
			int g = (int) (255 * (1.0 - t));
			int color = 0xFF000000 | (255 << 16) | (g << 8);
			f.drawBox(res.min().getX() - 1, res.min().getY() - 1, res.min().getZ() - 1, res.max().getX() + 2, res.max().getY() + 2, res.max().getZ() + 2, color);
			f.drawText(c.getX() + 0.5, res.max().getY() + 3, c.getZ() + 0.5, String.format("%.0f (%d,%d)", res.score(), res.chunkX(), res.chunkZ()), color);
		}
	}
}
