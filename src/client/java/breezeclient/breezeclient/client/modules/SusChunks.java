package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.render.RenderUtil;
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
import breezeclient.breezeclient.core.sus.GriefSignatures;
import breezeclient.breezeclient.core.sus.SusColor;
import breezeclient.breezeclient.core.sus.SusScore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
	public record Result(int chunkX, int chunkZ, double score, BlockPos min, BlockPos max, String label, List<String> all) {
		public BlockPos center() { return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2); }
	}

	private static final List<String> DEFAULTS = defaults();

	public enum Mode { SIGNATURES, WEIGHTED }

	private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.SIGNATURES), "Detection",
		"SIGNATURES looks for specific base features and skips natural structures. WEIGHTED adds up block weights per chunk (old behavior).");
	private final EnumSetting<BaseSignatures.Preset> preset = add(new EnumSetting<>("Looking for", BaseSignatures.Preset.SPAWNER_BASES), "Detection",
		"SPAWNER_BASES: spawner stacks and farms, hopper storage, ender chests, beacons, plus griefed bases and lavacasts. ALL_BASES adds big storage rooms and redstone. GRIEFED shows only wrecked or raided bases and lavacasts. STORAGE, FARMS and CUSTOM are narrower or hand-picked.",
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

	private final StringListSetting indicators = add(new StringListSetting("Indicators", DEFAULTS).kind(StringListSetting.Kind.BLOCK).optionFormat("%s=1"), "Weighted mode",
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
	private final BoolSetting fillChunk = add(new BoolSetting("Fill chunk", true), "Display",
		"Tint the whole flagged chunk. Chunks run from green (weakest find) to red (strongest) as their score rises.");
	private final BoolSetting tallOutline = add(new BoolSetting("Outline the whole chunk", true), "Display",
		"Draws the border of a flagged chunk as a tall frame with a ring at your own height, so you can see which chunk it is from any distance and from inside it. Off: only a short column around what was found.");
	private final BoolSetting notify = add(new BoolSetting("Notify", true), "Alerts", "Pop-up the first time a chunk is flagged.");
	private final BoolSetting waypoint = add(new BoolSetting("Waypoint", false), "Alerts", "Save a waypoint for each flagged chunk.");

	private static SusChunks instance;
	private ScanTarget target;
	private List<String> applied = List.of();
	private Map<String, Double> weights = Map.of();
	private final List<Result> results = new ArrayList<>();
	private final Set<Long> announced = new HashSet<>();
	private int ticks;
	private ClientLevel seenLevel;

	public SusChunks() {
		super("SusChunks", Category.BASE_FINDING);
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
	private boolean appliedGrief;
	private Object appliedDim;

	private boolean griefWanted() {
		Set<Signature> on = activeSignatures();
		return on.contains(Signature.GRIEFED) || on.contains(Signature.LAVACAST);
	}

	private void rebuild() {
		applied = List.copyOf(indicators.get());
		appliedSignatures = mode.get() == Mode.SIGNATURES;
		Set<Block> blocks = new HashSet<>();
		if (appliedSignatures) {
			for (String id : BaseSignatures.scanIds()) {
				Block b = BlockIds.parse(id);
				if (b != null) blocks.add(b);
			}
			// The grief blocks are common in places where they mean nothing: only scan them when those signatures are on, and
			// leave out End obsidian (the pillars are thousands of blocks a chunk) and Nether fire (it burns everywhere).
			var dim = seenLevel == null ? null : seenLevel.dimension();
			appliedGrief = griefWanted();
			appliedDim = dim;
			if (appliedGrief) {
				for (String id : GriefSignatures.scanIds()) {
					if (dim == net.minecraft.world.level.Level.END && (id.equals(GriefSignatures.OBSIDIAN) || id.equals(GriefSignatures.COBBLESTONE))) continue;
					if (dim == net.minecraft.world.level.Level.NETHER && GriefSignatures.FIRE.contains(id)) continue;
					Block b = BlockIds.parse(id);
					if (b != null) blocks.add(b);
				}
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
		ClientLevel level = Minecraft.getInstance().level;
		if (level != seenLevel) { // relog or dimension change: what was flagged belongs to the old world
			seenLevel = level;
			results.clear();
			snapshots.clear();
		}
		if (target == null) return;
		if (!applied.equals(indicators.get()) || appliedSignatures != (mode.get() == Mode.SIGNATURES)
			|| (appliedSignatures && (appliedGrief != griefWanted() || appliedDim != (level == null ? null : level.dimension())))) {
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
			Result r = new Result(cx, cz, score, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), "weighted score", List.of());
			results.add(r);
			if (announced.add(en.getKey())) {
				BlockPos c = r.center();
				if (notify.get()) EspModule.say(String.format("Suspicious chunk (%d, %d) score %.1f near %d %d %d", cx, cz, score, c.getX(), c.getY(), c.getZ()));
				if (waypoint.get()) Waypoints.add("Sus chunk", c);
			}
		}
	}

	/** Animals, villagers, armor stands, item frames and named mobs, grouped by the chunk they stand in. */
	private static Map<Long, List<BaseSignatures.EntityPoint>> gatherEntities(ClientLevel level) {
		Map<Long, List<BaseSignatures.EntityPoint>> out = new HashMap<>();
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof Player || !(e instanceof LivingEntity || e instanceof ItemFrame)) continue;
			String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
			long key = ChunkPos.asLong(e.getBlockX() >> 4, e.getBlockZ() >> 4);
			out.computeIfAbsent(key, k -> new ArrayList<>())
				.add(new BaseSignatures.EntityPoint(type, e.hasCustomName(), new Pos(e.getBlockX(), e.getBlockY(), e.getBlockZ())));
		}
		return out;
	}

	private Set<Signature> activeSignatures() {
		if (preset.get() != BaseSignatures.Preset.CUSTOM) return preset.get().signatures();
		Set<Signature> out = EnumSet.noneOf(Signature.class);
		for (Map.Entry<Signature, BoolSetting> e : custom.entrySet()) if (e.getValue().get()) out.add(e.getKey());
		return out;
	}

	/** What one chunk's hits contained when they were last read; reused while the hit positions are unchanged. */
	private record Snapshot(long sig, Map<String, List<Pos>> byId, boolean natural, boolean big, List<ActivityPos> activity) {}

	/** A furnace, campfire or lamp: its lit state changes without the position set changing, so it is re-read each pass. */
	private record ActivityPos(BlockPos pos, String id) {}

	private final Map<Long, Snapshot> snapshots = new HashMap<>();

	private void evaluateSignatures(ClientLevel level, ChunkPos mine) {
		Map<Long, Snapshot> perChunk = new HashMap<>();
		Set<Long> natural = new HashSet<>();
		Set<Long> bigNatural = new HashSet<>();
		for (Map.Entry<Long, List<BlockPos>> en : target.hitsByChunk().entrySet()) {
			// the scanner hands over a fresh list on every refresh, so compare positions, not identity; block states are
			// only read when the positions changed (a block swapped for another at the same spot shows up on the next change)
			long sig = en.getValue().size();
			for (BlockPos p : en.getValue()) sig = sig * 31 + p.asLong();
			Snapshot snap = snapshots.get(en.getKey());
			if (snap == null || snap.sig() != sig) {
				Map<String, List<Pos>> byId = new HashMap<>();
				List<ActivityPos> activity = new ArrayList<>();
				for (BlockPos p : en.getValue()) {
					String id = BlockIds.idOf(level.getBlockState(p).getBlock());
					byId.computeIfAbsent(id, k -> new ArrayList<>()).add(new Pos(p.getX(), p.getY(), p.getZ()));
					if (BaseSignatures.ACTIVITY_BASE.contains(id)) activity.add(new ActivityPos(p, id));
				}
				Map<String, Integer> counts = new HashMap<>();
				for (Map.Entry<String, List<Pos>> e : byId.entrySet()) counts.put(e.getKey(), e.getValue().size());
				var kinds = BaseSignatures.naturalKinds(counts);
				boolean big = false;
				for (var k : kinds) big |= BaseSignatures.isBig(k);
				snap = new Snapshot(sig, byId, !kinds.isEmpty(), big, activity);
				snapshots.put(en.getKey(), snap);
			}
			perChunk.put(en.getKey(), snap);
			if (snap.natural()) natural.add(en.getKey());
			if (snap.big()) bigNatural.add(en.getKey());
		}
		snapshots.keySet().retainAll(perChunk.keySet());
		BaseSignatures.Config cfg = new BaseSignatures.Config(minStack.asInt(), stackRadius.asInt(), minHoppers.asInt(),
			minContainers.asInt(), minRedstone.asInt(), BaseSignatures.Config.defaults().farmRadius(), BaseSignatures.Config.defaults().minBuilding());
		Set<Signature> enabled = activeSignatures();
		Set<Signature> local = EnumSet.noneOf(Signature.class);
		local.addAll(enabled);
		local.removeAll(BaseSignatures.CLUSTER); // those are evaluated across chunk borders below
		List<Result> fresh = new ArrayList<>();
		Map<Long, List<BaseSignatures.EntityPoint>> entities = enabled.contains(Signature.ENTITY_CLUSTER) ? gatherEntities(level) : Map.of();
		// chunks can matter for entities or tunnels alone, without any scanned block
		Set<Long> keys = new HashSet<>(perChunk.keySet());
		keys.addAll(entities.keySet());
		if (enabled.contains(Signature.TUNNEL)) keys.addAll(TunnelFinder.chunkKeys());
		for (long key : keys) {
			Snapshot snap = perChunk.get(key);
			int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
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
			Map<String, List<Pos>> own = snap == null ? Map.of() : snap.byId();
			if (snap != null && !snap.activity().isEmpty() && local.contains(Signature.ACTIVITY)) {
				Map<String, List<Pos>> view = new HashMap<>(own);
				for (ActivityPos a : snap.activity()) {
					var st = level.getBlockState(a.pos());
					if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT)) {
						view.computeIfAbsent(a.id() + BaseSignatures.LIT, k -> new ArrayList<>()).add(new Pos(a.pos().getX(), a.pos().getY(), a.pos().getZ()));
					}
				}
				own = view;
			}
			// trial chambers, ancient cities and strongholds are big and patchy: look two chunks around them, and be stricter
			boolean nearBig = false;
			if (ignoreNatural.get()) {
				for (int dx = -2; dx <= 2 && !nearBig; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (bigNatural.contains(ChunkPos.asLong(cx + dx, cz + dz))) {
							nearBig = true;
							break;
						}
					}
				}
			}
			nearNatural |= nearBig;
			var findings = new ArrayList<>(BaseSignatures.evaluate(own, nearNatural, nearBig, local, cfg));
			if (own.containsKey(BaseSignatures.SPAWNER) || own.containsKey(BaseSignatures.HOPPER)) {
				Map<String, List<Pos>> merged = new HashMap<>();
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						Snapshot nb = perChunk.get(ChunkPos.asLong(cx + dx, cz + dz));
						if (nb == null) continue;
						for (String id : BaseSignatures.CLUSTER_BLOCKS) {
							List<Pos> l = nb.byId().get(id);
							if (l != null) merged.computeIfAbsent(id, k -> new ArrayList<>()).addAll(l);
						}
					}
				}
				findings.addAll(BaseSignatures.evaluateClusters(merged, enabled, cfg, cx, cz, nearBig));
			}
			if (enabled.contains(Signature.ENTITY_CLUSTER) && entities.containsKey(key)) {
				List<BaseSignatures.EntityPoint> merged = new ArrayList<>();
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) merged.addAll(entities.getOrDefault(ChunkPos.asLong(cx + dx, cz + dz), List.of()));
				}
				findings.addAll(BaseSignatures.evaluateEntities(merged, nearNatural, cx, cz));
			}
			findings.addAll(GriefSignatures.evaluate(own, nearNatural, level.dimension() == net.minecraft.world.level.Level.NETHER, enabled, level.getSeaLevel()));
			if (enabled.contains(Signature.TUNNEL) && !nearNatural) {
				List<TunnelFinder.Run> runs = TunnelFinder.runsIn(cx, cz);
				if (!runs.isEmpty()) {
					int longest = 0, x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
					for (TunnelFinder.Run r : runs) {
						longest = Math.max(longest, r.length());
						int ex = r.alongX() ? r.x() + r.length() : r.x() + 1, ez = r.alongX() ? r.z() + 1 : r.z() + r.length();
						x1 = Math.min(x1, r.x());
						y1 = Math.min(y1, r.y());
						z1 = Math.min(z1, r.z());
						x2 = Math.max(x2, ex);
						y2 = Math.max(y2, r.y() + 2);
						z2 = Math.max(z2, ez);
					}
					findings.add(BaseSignatures.tunnelFinding(longest, runs.size(), new Pos(x1, y1, z1), new Pos(x2, y2, z2)));
				}
			}
			if (findings.isEmpty()) continue;
			double score = BaseSignatures.combine(findings);
			if (score < sigThreshold.get()) continue;
			var best = BaseSignatures.strongest(findings);
			String label = best.signature().label() + ": " + best.detail();
			if (findings.size() > 1) label += " (+" + (findings.size() - 1) + " more)";
			Result r = new Result(cx, cz, score, new BlockPos(best.min().x(), best.min().y(), best.min().z()),
				new BlockPos(best.max().x(), best.max().y(), best.max().z()), label,
				findings.stream().map(f -> f.signature().label() + ": " + f.detail()).toList());
			fresh.add(r);
			if (announced.add(key)) {
				BlockPos c = r.center();
				if (notify.get()) EspModule.say(String.format("Base signature at %d %d %d: %s", c.getX(), c.getY(), c.getZ(), label));
				if (waypoint.get()) Waypoints.add("Base: " + best.signature().label(), c);
			}
		}
		Set<Long> flagged = new HashSet<>();
		for (Result r : fresh) flagged.add(ChunkPos.asLong(r.chunkX(), r.chunkZ()));
		results.clear();
		for (Result r : fresh) {
			int touching = 0;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx != 0 || dz != 0) && flagged.contains(ChunkPos.asLong(r.chunkX() + dx, r.chunkZ() + dz))) touching++;
				}
			}
			if (touching == 0) {
				results.add(r);
			} else {
				double boost = 1 + 0.1 * Math.min(3, touching);
				results.add(new Result(r.chunkX(), r.chunkZ(), r.score() * boost, r.min(), r.max(), r.label() + " [" + (touching + 1) + " flagged chunks together]", r.all()));
			}
		}
	}

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		double r = range.get();
		double base = appliedSignatures ? sigThreshold.get() : threshold.get();
		List<Result> sorted = new ArrayList<>(results);
		sorted.sort(Comparator.comparingDouble(Result::score).reversed());
		List<Result> shown = new ArrayList<>();
		for (Result res : sorted) {
			if (shown.size() >= maxHighlights.asInt()) break;
			BlockPos c = res.center();
			if (mc.player.distanceToSqr(c.getX(), c.getY(), c.getZ()) > r * r) continue;
			shown.add(res);
		}
		if (shown.isEmpty()) return;
		// green at the flagging threshold, red at the strongest chunk on screen; never less than three times the threshold,
		// so a lone weak find is not painted red just because nothing else is around
		double top = Math.max(base * 3, shown.get(0).score());
		boolean fill = fillChunk.get() && f instanceof RenderUtil;
		for (Result res : shown) {
			BlockPos c = res.center();
			int color = SusColor.gradient(SusColor.fraction(res.score(), base, top));
			// the flagged chunk itself, as a column around what was found
			double x0 = res.chunkX() * 16.0, z0 = res.chunkZ() * 16.0;
			double y0 = res.min().getY() - 1, y1 = Math.max(res.max().getY() + 2, y0 + 8);
			f.drawBox(x0, y0, z0, x0 + 16, y1, z0 + 16, color);
			if (tallOutline.get()) {
				// the chunk's border from well below to well above both the find and you, and a ring at your feet
				double py = mc.player.getY();
				double lo = Math.min(y0, py - 6), hi = Math.max(y1, py + 28);
				f.drawBox(x0, lo, z0, x0 + 16, hi, z0 + 16, color);
				f.drawBox(x0, py, z0, x0 + 16, py + 0.05, z0 + 16, color);
				f.drawBox(x0 + 0.1, py + 2, z0 + 0.1, x0 + 15.9, py + 2.05, z0 + 15.9, (color & 0xFFFFFF) | 0x90000000);
			}
			if (fill) ((RenderUtil) f).drawFilledBox(x0, y0, z0, x0 + 16, y1, z0 + 16, (color & 0xFFFFFF) | 0x30000000);
			// and the exact spot inside it
			f.drawBox(res.min().getX() - 1, res.min().getY() - 1, res.min().getZ() - 1, res.max().getX() + 2, res.max().getY() + 2, res.max().getZ() + 2, color);
			f.drawText(c.getX() + 0.5, y1 + 1, c.getZ() + 0.5, String.format("%s  %.0f", res.label(), res.score()), color);
		}
	}
}
