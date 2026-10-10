package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.world.BlockIds;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.nav.Pos;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.sus.BaseSignatures;
import breezeclient.breezeclient.core.sus.BaseSignatures.Signature;
import breezeclient.breezeclient.core.sus.SusScore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds player bases. The default mode (SIGNATURES) looks for specific things natural generation does not make: tight
 * spawner stacks, spawners beside hoppers, hopper-fed storage, big storage rooms, ender chests, beacons, with villages,
 * mineshafts, dungeons and other natural structures recognized and ignored. The older WEIGHTED mode adds up configurable
 * block weights per chunk and is kept for people who want full control.
 */
public class SusChunks extends Module {
	public record Result(int chunkX, int chunkZ, double score, BlockPos min, BlockPos max, String label) {
		public BlockPos center() { return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2); }
	}

	private static final List<String> DEFAULTS = defaults();

	public enum Mode { SIGNATURES, WEIGHTED }

	private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.SIGNATURES), "Detection",
		"SIGNATURES looks for specific base features and skips natural structures. WEIGHTED adds up block weights per chunk (old behavior).");
	private final EnumSetting<BaseSignatures.Preset> preset = add(new EnumSetting<>("Looking for", BaseSignatures.Preset.SPAWNER_BASES), "Detection",
		"SPAWNER_BASES: spawner stacks and farms, hopper storage, ender chests, beacons. ALL_BASES adds big storage rooms and redstone. STORAGE, FARMS and CUSTOM are narrower or hand-picked.",
		() -> mode.get() == Mode.SIGNATURES);
	private final Map<Signature, BoolSetting> custom = new java.util.EnumMap<>(Signature.class);
	private final NumberSetting minStack = add(new NumberSetting("Min spawners in a stack", 3, 2, 20), "Detection",
		"How many spawners packed together count as a spawner stack.", () -> mode.get() == Mode.SIGNATURES);
	private final NumberSetting stackRadius = add(new NumberSetting("Stack radius", 5, 1, 16), "Detection",
		"Spawners closer than this to each other count as one pile.", () -> mode.get() == Mode.SIGNATURES);
	private final NumberSetting minHoppers = add(new NumberSetting("Min hoppers", 4, 1, 32), "Detection",
		"Hoppers never generate naturally; this many in a chunk is a farm or sorter.", () -> mode.get() == Mode.SIGNATURES);
	private final NumberSetting minContainers = add(new NumberSetting("Min storage blocks", 8, 3, 100), "Detection",
		"Chests, barrels and shulker boxes (shulkers count double) needed for a storage room.", () -> mode.get() == Mode.SIGNATURES);
	private final NumberSetting minRedstone = add(new NumberSetting("Min redstone parts", 6, 2, 60), "Detection",
		"Pistons, observers, comparators and similar needed for a machine.", () -> mode.get() == Mode.SIGNATURES);
	private final BoolSetting ignoreNatural = add(new BoolSetting("Ignore natural structures", true), "Detection",
		"Weaker signatures (storage rooms, redstone, lone spawners) are skipped in and next to villages, mineshafts, dungeons, strongholds, ancient cities, trial chambers, fortresses and bastions.",
		() -> mode.get() == Mode.SIGNATURES);
	private final NumberSetting sigThreshold = add(new NumberSetting("Signature threshold", 30, 5, 150), "Scoring",
		"Minimum score to flag a chunk. 30 keeps only clear signatures; lower it to see weaker ones.", () -> mode.get() == Mode.SIGNATURES);

	private final StringListSetting indicators = add(new StringListSetting("Indicators", DEFAULTS), "Weighted mode",
		"Blocks that count, written as block_id=weight.", () -> mode.get() == Mode.WEIGHTED);
	private final NumberSetting blockEntityWeight = add(new NumberSetting("Block entity weight", 0.25, 0, 5), "Weighted mode",
		"Extra score per block entity in the chunk.", () -> mode.get() == Mode.WEIGHTED);
	private final NumberSetting threshold = add(new NumberSetting("Threshold", 12, 1, 500), "Weighted mode",
		"Minimum weighted score to flag a chunk.", () -> mode.get() == Mode.WEIGHTED);

	private final NumberSetting minDistance = add(new NumberSetting("Ignore chunks within", 4, 0, 32), "Display",
		"Chunks this close to you are skipped, so your own base is not flagged.");
	private final NumberSetting range = add(new NumberSetting("Highlight range", 512, 32, 2048), "Display", "How far away highlights are drawn.");
	private final NumberSetting maxHighlights = add(new NumberSetting("Max highlights", 12, 1, 100), "Display",
		"Only the highest scoring chunks are drawn, so a busy area does not fill the screen.");
	private final BoolSetting notify = add(new BoolSetting("Notify", true), "Alerts", "Pop-up the first time a chunk is flagged.");
	private final BoolSetting waypoint = add(new BoolSetting("Waypoint", false), "Alerts", "Save a waypoint for each flagged chunk.");

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
		for (Signature sig : Signature.values()) {
			custom.put(sig, add(new BoolSetting("Find: " + sig.label(), sig != Signature.REDSTONE && sig != Signature.STORAGE_ROOM), "Custom signatures",
				"Look for this signature when Looking for is CUSTOM.", () -> mode.get() == Mode.SIGNATURES && preset.get() == BaseSignatures.Preset.CUSTOM));
		}
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

	private boolean appliedSignatures;

	private void rebuild() {
		applied = List.copyOf(indicators.get());
		appliedSignatures = mode.get() == Mode.SIGNATURES;
		Set<Block> blocks = new HashSet<>();
		if (appliedSignatures) {
			for (String id : BaseSignatures.scanIds()) {
				Block b = BlockIds.parse(id);
				if (b != null) blocks.add(b);
			}
		} else {
			weights = SusScore.parseWeights(applied);
			for (String id : weights.keySet()) {
				Block b = BlockIds.parse(id);
				if (b != null) blocks.add(b);
			}
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
		if (!applied.equals(indicators.get()) || appliedSignatures != (mode.get() == Mode.SIGNATURES)) {
			rebuild();
			results.clear();
		}
		if (++ticks % 40 != 0) return;
		evaluate();
	}

	private void evaluate() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) return;
		ChunkPos mine = mc.player.chunkPosition();
		if (appliedSignatures) {
			evaluateSignatures(level, mine);
			return;
		}
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
			Result r = new Result(cx, cz, score, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), "weighted score");
			results.add(r);
			if (announced.add(en.getKey())) {
				BlockPos c = r.center();
				if (notify.get()) EspModule.say(String.format("Suspicious chunk (%d, %d) score %.1f near %d %d %d", cx, cz, score, c.getX(), c.getY(), c.getZ()));
				if (waypoint.get()) Waypoints.add("Sus chunk", c);
			}
		}
	}

	private Set<Signature> activeSignatures() {
		if (preset.get() != BaseSignatures.Preset.CUSTOM) return preset.get().signatures();
		Set<Signature> out = EnumSet.noneOf(Signature.class);
		for (Map.Entry<Signature, BoolSetting> e : custom.entrySet()) if (e.getValue().get()) out.add(e.getKey());
		return out;
	}

	private void evaluateSignatures(ClientLevel level, ChunkPos mine) {
		Map<Long, Map<String, List<Pos>>> perChunk = new HashMap<>();
		Set<Long> natural = new HashSet<>();
		for (Map.Entry<Long, List<BlockPos>> en : target.hitsByChunk().entrySet()) {
			Map<String, List<Pos>> byId = new HashMap<>();
			for (BlockPos p : en.getValue()) {
				String id = BlockIds.idOf(level.getBlockState(p).getBlock());
				byId.computeIfAbsent(id, k -> new ArrayList<>()).add(new Pos(p.getX(), p.getY(), p.getZ()));
			}
			perChunk.put(en.getKey(), byId);
			Map<String, Integer> counts = new HashMap<>();
			for (Map.Entry<String, List<Pos>> e : byId.entrySet()) counts.put(e.getKey(), e.getValue().size());
			if (!BaseSignatures.naturalKinds(counts).isEmpty()) natural.add(en.getKey());
		}
		BaseSignatures.Config cfg = new BaseSignatures.Config(minStack.asInt(), stackRadius.asInt(), minHoppers.asInt(),
			minContainers.asInt(), minRedstone.asInt(), BaseSignatures.Config.defaults().farmRadius());
		Set<Signature> enabled = activeSignatures();
		List<Result> fresh = new ArrayList<>();
		for (Map.Entry<Long, Map<String, List<Pos>>> en : perChunk.entrySet()) {
			int cx = ChunkPos.getX(en.getKey()), cz = ChunkPos.getZ(en.getKey());
			if (Math.max(Math.abs(cx - mine.x), Math.abs(cz - mine.z)) <= minDistance.asInt()) continue;
			boolean nearNatural = false;
			if (ignoreNatural.get()) {
				for (int dx = -1; dx <= 1 && !nearNatural; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (natural.contains(ChunkPos.asLong(cx + dx, cz + dz))) {
							nearNatural = true;
							break;
						}
					}
				}
			}
			var findings = BaseSignatures.evaluate(en.getValue(), nearNatural, enabled, cfg);
			if (findings.isEmpty()) continue;
			double score = BaseSignatures.combine(findings);
			if (score < sigThreshold.get()) continue;
			var best = BaseSignatures.strongest(findings);
			String label = best.signature().label() + ": " + best.detail();
			if (findings.size() > 1) label += " (+" + (findings.size() - 1) + " more)";
			Result r = new Result(cx, cz, score, new BlockPos(best.min().x(), best.min().y(), best.min().z()),
				new BlockPos(best.max().x(), best.max().y(), best.max().z()), label);
			fresh.add(r);
			if (announced.add(en.getKey())) {
				BlockPos c = r.center();
				if (notify.get()) EspModule.say(String.format("Base signature at %d %d %d: %s", c.getX(), c.getY(), c.getZ(), label));
				if (waypoint.get()) Waypoints.add("Base: " + best.signature().label(), c);
			}
		}
		results.clear();
		results.addAll(fresh);
	}

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		double r = range.get();
		double base = appliedSignatures ? sigThreshold.get() : threshold.get();
		List<Result> sorted = new ArrayList<>(results);
		sorted.sort(Comparator.comparingDouble(Result::score).reversed());
		int drawn = 0;
		for (Result res : sorted) {
			if (drawn >= maxHighlights.asInt()) break;
			BlockPos c = res.center();
			if (mc.player.distanceToSqr(c.getX(), c.getY(), c.getZ()) > r * r) continue;
			drawn++;
			double t = Math.min(1.0, (res.score() - base) / Math.max(1.0, base * 2));
			int g = (int) (255 * (1.0 - t));
			int color = 0xFF000000 | (255 << 16) | (g << 8);
			f.drawBox(res.min().getX() - 1, res.min().getY() - 1, res.min().getZ() - 1, res.max().getX() + 2, res.max().getY() + 2, res.max().getZ() + 2, color);
			f.drawText(c.getX() + 0.5, res.max().getY() + 3, c.getZ() + 0.5, String.format("%s  %.0f", res.label(), res.score()), color);
		}
	}
}
