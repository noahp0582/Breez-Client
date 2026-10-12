package breezeclient.breezeclient.core.sus;

import breezeclient.breezeclient.core.nav.Pos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure logic for recognizing player bases. Instead of adding up "how many chests and block entities" (which every village,
 * mineshaft and ancient city passes), a chunk is flagged only when it shows a specific signature that natural generation
 * does not produce: tight spawner stacks, spawners next to hoppers, hopper-fed storage, big storage rooms away from any
 * natural structure, ender chests, beacons and so on. Natural structures are recognized from marker blocks so the weaker
 * signatures can be suppressed around them.
 */
public final class BaseSignatures {
	private BaseSignatures() {}

	public enum Signature {
		SPAWNER_STACK("Spawner stack", false),
		SPAWNER_FARM("Spawner with hoppers/redstone", false),
		LONE_SPAWNER("Spawner outside any structure", true),
		HOPPER_STORAGE("Hopper storage", false),
		STORAGE_ROOM("Storage room", true),
		ENDER_CHEST("Ender chest", false),
		REDSTONE("Redstone machine", true),
		VALUABLES("Valuable blocks", false),
		BUILDING_BLOCKS("Player building blocks", true),
		ACTIVITY("Lit furnace or campfire", true),
		ENTITY_CLUSTER("Entity cluster", false),
		TUNNEL("Straight tunnel", true),
		GRIEFED("Griefed or raided base", true),
		LAVACAST("Lavacast", false);

		private final String label;
		private final boolean weak;

		Signature(String label, boolean weak) {
			this.label = label;
			this.weak = weak;
		}

		public String label() { return label; }

		/** Weak signatures are ignored next to natural structures, because those can produce them by themselves. */
		public boolean weak() { return weak; }
	}

	/** Signatures that are about groups of blocks which can straddle a chunk border; see {@link #evaluateClusters}. */
	public static final Set<Signature> CLUSTER = Collections.unmodifiableSet(
		EnumSet.of(Signature.SPAWNER_STACK, Signature.SPAWNER_FARM, Signature.HOPPER_STORAGE));

	/** Hoppers closer than this (blocks) to each other belong to the same hopper group. */
	private static final double HOPPER_LINK = 6;

	public enum Preset {
		SPAWNER_BASES, ALL_BASES, STORAGE, FARMS, GRIEFED, CUSTOM;

		public Set<Signature> signatures() {
			return switch (this) {
				case SPAWNER_BASES -> EnumSet.of(Signature.SPAWNER_STACK, Signature.SPAWNER_FARM, Signature.LONE_SPAWNER,
					Signature.HOPPER_STORAGE, Signature.ENDER_CHEST, Signature.VALUABLES, Signature.BUILDING_BLOCKS,
					Signature.ACTIVITY, Signature.ENTITY_CLUSTER, Signature.TUNNEL, Signature.GRIEFED, Signature.LAVACAST);
				case ALL_BASES -> EnumSet.allOf(Signature.class);
				case STORAGE -> EnumSet.of(Signature.STORAGE_ROOM, Signature.HOPPER_STORAGE, Signature.ENDER_CHEST, Signature.VALUABLES);
				case FARMS -> EnumSet.of(Signature.SPAWNER_STACK, Signature.SPAWNER_FARM, Signature.HOPPER_STORAGE, Signature.REDSTONE,
					Signature.ENTITY_CLUSTER, Signature.ACTIVITY);
				case GRIEFED -> EnumSet.of(Signature.GRIEFED, Signature.LAVACAST);
				case CUSTOM -> EnumSet.noneOf(Signature.class);
			};
		}
	}

	public enum Natural { VILLAGE, MINESHAFT, DUNGEON, STRONGHOLD, ANCIENT_CITY, TRIAL_CHAMBER, NETHER_FORTRESS, BASTION, END_CITY, TEMPLE }

	/** Tunable limits. */
	public record Config(int minStack, int stackRadius, int minHoppers, int minContainers, int minRedstone, int farmRadius, int minBuilding) {
		public static Config defaults() { return new Config(3, 5, 4, 8, 6, 12, 8); }
	}

	/** One recognized signature in one chunk, with the blocks that triggered it. */
	public record Finding(Signature signature, double score, String detail, Pos min, Pos max) {}

	// ---- block id groups ----

	public static final String SPAWNER = "minecraft:spawner";
	public static final String HOPPER = "minecraft:hopper";
	public static final String ENDER_CHEST_ID = "minecraft:ender_chest";

	private static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

	public static final Set<String> CONTAINERS = containers();
	private static final Set<String> SHULKERS = shulkers();
	public static final Set<String> REDSTONE_PARTS = Set.of("minecraft:piston", "minecraft:sticky_piston", "minecraft:observer",
		"minecraft:comparator", "minecraft:repeater", "minecraft:dropper", "minecraft:dispenser", "minecraft:redstone_lamp",
		"minecraft:redstone_block", "minecraft:daylight_detector");
	/** Blocks that never generate naturally and that spawner farms are built from. */
	public static final Set<String> FARM_PARTS = Set.of(HOPPER, "minecraft:observer", "minecraft:piston", "minecraft:sticky_piston",
		"minecraft:dropper", "minecraft:comparator", "minecraft:repeater", "minecraft:redstone_block");
	/** Block ids the cluster signatures need, for callers that merge neighbouring chunks. */
	public static final Set<String> CLUSTER_BLOCKS = clusterBlocks();

	private static Set<String> clusterBlocks() {
		Set<String> s = new LinkedHashSet<>(List.of(SPAWNER, HOPPER));
		s.addAll(FARM_PARTS);
		s.addAll(CONTAINERS);
		return Collections.unmodifiableSet(s);
	}

	/** Suffix added to an activity block id when its LIT state is set, e.g. "minecraft:furnace[lit]". */
	public static final String LIT = "[lit]";

	/** Blocks that are lit while someone is using them. */
	public static final Set<String> ACTIVITY_BASE = Set.of("minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
		"minecraft:campfire", "minecraft:soul_campfire", "minecraft:redstone_lamp");

	/** Building blocks that natural generation (outside a few structures) does not make. */
	public static final Set<String> BUILDING_BLOCKS = buildingBlocks();

	private static Set<String> buildingBlocks() {
		Set<String> s = new LinkedHashSet<>();
		for (String c : COLORS) {
			s.add("minecraft:" + c + "_concrete");
			s.add("minecraft:" + c + "_stained_glass");
			s.add("minecraft:" + c + "_glazed_terracotta");
		}
		s.addAll(List.of("minecraft:quartz_block", "minecraft:quartz_bricks", "minecraft:quartz_pillar", "minecraft:chiseled_quartz_block",
			"minecraft:smooth_quartz"));
		return Collections.unmodifiableSet(s);
	}

	/** What a natural spawner is surrounded by (dungeon, mineshaft, nether fortress). A spawner without any of these is player-placed. */
	private static final Set<String> SPAWNER_NATURAL_MARKERS = Set.of("minecraft:mossy_cobblestone", "minecraft:cobweb",
		"minecraft:nether_bricks", "minecraft:nether_brick_fence", "minecraft:end_portal_frame");
	private static final Set<String> VALUABLE_BLOCKS = Set.of("minecraft:netherite_block", "minecraft:diamond_block", "minecraft:emerald_block");
	private static final Set<String> JOBSITES = Set.of("minecraft:bell", "minecraft:composter", "minecraft:lectern", "minecraft:cartography_table",
		"minecraft:fletching_table", "minecraft:loom", "minecraft:grindstone", "minecraft:smithing_table", "minecraft:stonecutter");
	private static final Set<String> FENCES = Set.of("minecraft:oak_fence", "minecraft:dark_oak_fence");
	private static final Set<String> NATURAL_MARKERS = Set.of("minecraft:hay_block", "minecraft:dirt_path", "minecraft:cobweb", "minecraft:rail",
		"minecraft:mossy_cobblestone", "minecraft:end_portal_frame", "minecraft:mossy_stone_bricks", "minecraft:iron_bars",
		"minecraft:reinforced_deepslate", "minecraft:sculk_shrieker", "minecraft:sculk_catalyst", "minecraft:sculk", "minecraft:trial_spawner",
		"minecraft:vault", "minecraft:nether_bricks", "minecraft:nether_brick_fence", "minecraft:gilded_blackstone", "minecraft:purpur_block",
		"minecraft:tnt", "minecraft:chiseled_sandstone", "minecraft:tripwire_hook");

	private static final List<String> OXIDATION = List.of("", "exposed_", "weathered_", "oxidized_");

	/** Copper bulbs and grates in any oxidation and waxed state: how trial chambers are built, and rare anywhere else. */
	public static final Set<String> TRIAL_MARKERS = trialMarkers();

	private static Set<String> trialMarkers() {
		Set<String> s = new LinkedHashSet<>();
		for (String ox : OXIDATION) {
			for (String wax : List.of("", "waxed_")) {
				s.add("minecraft:" + wax + ox + "copper_bulb");
				s.add("minecraft:" + wax + ox + "copper_grate");
			}
		}
		return Collections.unmodifiableSet(s);
	}

	/** Structures that cover several chunks and have long stretches without their tell-tale blocks. */
	public static boolean isBig(Natural n) {
		return n == Natural.TRIAL_CHAMBER || n == Natural.ANCIENT_CITY || n == Natural.STRONGHOLD;
	}

	/** Near a big natural structure these need much more before they count (trial chambers do contain hoppers and dispensers). */
	private static final int STRICT_HOPPERS = 10;
	private static final int STRICT_FARM_PARTS = 5;

	private static Set<String> shulkers() {
		Set<String> s = new LinkedHashSet<>();
		s.add("minecraft:shulker_box");
		for (String c : COLORS) s.add("minecraft:" + c + "_shulker_box");
		return s;
	}

	private static Set<String> containers() {
		Set<String> s = new LinkedHashSet<>(List.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel"));
		s.addAll(shulkers());
		return s;
	}

	/** Every block id the scanner must report for evaluate() and naturalKinds() to work. */
	public static Set<String> scanIds() {
		Set<String> s = new LinkedHashSet<>();
		s.add(SPAWNER);
		s.add(HOPPER);
		s.add(ENDER_CHEST_ID);
		s.add("minecraft:beacon");
		s.addAll(CONTAINERS);
		s.addAll(REDSTONE_PARTS);
		s.addAll(FARM_PARTS);
		s.addAll(VALUABLE_BLOCKS);
		s.addAll(JOBSITES);
		s.addAll(FENCES);
		s.addAll(NATURAL_MARKERS);
		s.addAll(SPAWNER_NATURAL_MARKERS);
		s.addAll(BUILDING_BLOCKS);
		s.addAll(ACTIVITY_BASE);
		s.addAll(TRIAL_MARKERS);
		return s;
	}

	// ---- natural structures ----

	/** Which natural structures a chunk's marker blocks point to. {@code counts} maps block id to how many were found. */
	public static Set<Natural> naturalKinds(Map<String, Integer> counts) {
		Set<Natural> out = EnumSet.noneOf(Natural.class);
		int jobsites = 0;
		for (String j : JOBSITES) if (counts.getOrDefault(j, 0) > 0) jobsites++;
		int paths = counts.getOrDefault("minecraft:dirt_path", 0);
		if (jobsites >= 3 || paths >= 8 || (counts.getOrDefault("minecraft:hay_block", 0) >= 2 && jobsites >= 1)) out.add(Natural.VILLAGE);

		int fences = 0;
		for (String f : FENCES) fences += counts.getOrDefault(f, 0);
		if (counts.getOrDefault("minecraft:cobweb", 0) >= 3 && (counts.getOrDefault("minecraft:rail", 0) >= 2 || fences >= 3)) out.add(Natural.MINESHAFT);

		if (counts.getOrDefault("minecraft:mossy_cobblestone", 0) >= 6) out.add(Natural.DUNGEON);

		if (counts.getOrDefault("minecraft:end_portal_frame", 0) >= 1
			|| (counts.getOrDefault("minecraft:mossy_stone_bricks", 0) >= 8 && counts.getOrDefault("minecraft:iron_bars", 0) >= 2)) out.add(Natural.STRONGHOLD);

		if (counts.getOrDefault("minecraft:reinforced_deepslate", 0) >= 1 || counts.getOrDefault("minecraft:sculk_shrieker", 0) >= 1
			|| counts.getOrDefault("minecraft:sculk_catalyst", 0) >= 2 || counts.getOrDefault("minecraft:sculk", 0) >= 20) out.add(Natural.ANCIENT_CITY);

		int bulbs = 0, grates = 0;
		for (String id : TRIAL_MARKERS) {
			if (id.endsWith("copper_bulb")) bulbs += counts.getOrDefault(id, 0);
			else grates += counts.getOrDefault(id, 0);
		}
		if (counts.getOrDefault("minecraft:trial_spawner", 0) >= 1 || counts.getOrDefault("minecraft:vault", 0) >= 1 || bulbs >= 2 || grates >= 4) {
			out.add(Natural.TRIAL_CHAMBER);
		}

		if (counts.getOrDefault("minecraft:nether_bricks", 0) >= 10 || counts.getOrDefault("minecraft:nether_brick_fence", 0) >= 6) out.add(Natural.NETHER_FORTRESS);

		if (counts.getOrDefault("minecraft:gilded_blackstone", 0) >= 1) out.add(Natural.BASTION);

		if (counts.getOrDefault("minecraft:purpur_block", 0) >= 10) out.add(Natural.END_CITY);

		if (counts.getOrDefault("minecraft:tnt", 0) >= 4 && counts.getOrDefault("minecraft:chiseled_sandstone", 0) >= 2) out.add(Natural.TEMPLE);
		return out;
	}

	// ---- evaluation ----

	private static List<Pos> positions(Map<String, List<Pos>> blocks, Collection<String> ids) {
		List<Pos> out = new ArrayList<>();
		for (String id : ids) {
			List<Pos> l = blocks.get(id);
			if (l != null) out.addAll(l);
		}
		return out;
	}

	private static double d2(Pos a, Pos b) {
		double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}

	private static Finding box(Signature s, double score, String detail, Collection<Pos> ps) {
		int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
		for (Pos p : ps) {
			x1 = Math.min(x1, p.x());
			y1 = Math.min(y1, p.y());
			z1 = Math.min(z1, p.z());
			x2 = Math.max(x2, p.x());
			y2 = Math.max(y2, p.y());
			z2 = Math.max(z2, p.z());
		}
		return new Finding(s, score, detail, new Pos(x1, y1, z1), new Pos(x2, y2, z2));
	}

	/** Groups of positions chained together by gaps of at most {@code radius}. */
	static List<List<Pos>> clusters(List<Pos> ps, double radius) {
		int n = ps.size();
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) parent[i] = i;
		double r2 = radius * radius;
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				if (d2(ps.get(i), ps.get(j)) <= r2) union(parent, i, j);
			}
		}
		Map<Integer, List<Pos>> groups = new java.util.HashMap<>();
		for (int i = 0; i < n; i++) groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(ps.get(i));
		return new ArrayList<>(groups.values());
	}

	private static Pos corner(List<Pos> ps) {
		int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
		for (Pos p : ps) {
			x = Math.min(x, p.x());
			y = Math.min(y, p.y());
			z = Math.min(z, p.z());
		}
		return new Pos(x, y, z);
	}

	/** Spawner stacks, spawners with farm parts and hopper groups. {@code anchorOk} decides which group is "ours". */
	private static List<Finding> clusterFindings(Map<String, List<Pos>> blocks, Set<Signature> enabled, Config cfg, Predicate<Pos> anchorOk, boolean strict) {
		List<Finding> out = new ArrayList<>();
		List<Pos> spawners = blocks.getOrDefault(SPAWNER, List.of());

		if (enabled.contains(Signature.SPAWNER_STACK) && spawners.size() >= cfg.minStack()) {
			List<Pos> best = List.of();
			for (List<Pos> c : clusters(spawners, cfg.stackRadius())) {
				if (c.size() > best.size() && anchorOk.test(corner(c))) best = c;
			}
			if (best.size() >= cfg.minStack()) {
				double score = Math.min(100, 50 + 8.0 * (best.size() - cfg.minStack()));
				out.add(box(Signature.SPAWNER_STACK, score, best.size() + " spawners within " + cfg.stackRadius() + " blocks", best));
			}
		}

		if (enabled.contains(Signature.SPAWNER_FARM) && !spawners.isEmpty()) {
			List<Pos> support = positions(blocks, FARM_PARTS);
			double r2 = (double) cfg.farmRadius() * cfg.farmRadius();
			for (Pos s : spawners) {
				if (!anchorOk.test(s)) continue;
				List<Pos> near = new ArrayList<>();
				for (Pos p : support) if (d2(s, p) <= r2) near.add(p);
				if (near.size() >= (strict ? STRICT_FARM_PARTS : 2)) {
					near.add(s);
					out.add(box(Signature.SPAWNER_FARM, 38, "spawner with " + (near.size() - 1) + " hopper/redstone parts nearby", near));
					break;
				}
			}
		}

		List<Pos> hoppers = blocks.getOrDefault(HOPPER, List.of());
		int minHoppers = strict ? Math.max(cfg.minHoppers(), STRICT_HOPPERS) : cfg.minHoppers();
		if (enabled.contains(Signature.HOPPER_STORAGE) && hoppers.size() >= minHoppers) {
			List<Pos> best = List.of();
			for (List<Pos> c : clusters(hoppers, HOPPER_LINK)) {
				if (c.size() > best.size() && anchorOk.test(corner(c))) best = c;
			}
			if (best.size() >= minHoppers) {
				double score = Math.min(50, 30 + 2.0 * (best.size() - minHoppers));
				List<Pos> fedPos = new ArrayList<>();
				for (Pos c : positions(blocks, CONTAINERS)) {
					for (Pos h : best) {
						if (d2(c, h) <= 64) {
							fedPos.add(c);
							break;
						}
					}
				}
				if (fedPos.size() >= 2) score += 8;
				List<Pos> all = new ArrayList<>(best);
				all.addAll(fedPos);
				out.add(box(Signature.HOPPER_STORAGE, score, best.size() + " hoppers" + (fedPos.size() >= 2 ? " feeding " + fedPos.size() + " containers" : ""), all));
			}
		}
		return out;
	}

	/**
	 * The cluster signatures on blocks merged from a chunk and its neighbours, so a pile or hopper group that straddles a
	 * chunk border is counted whole. Only groups anchored in chunk (cx, cz) are returned, so each is reported by one chunk.
	 */
	public static List<Finding> evaluateClusters(Map<String, List<Pos>> merged, Set<Signature> enabled, Config cfg, int cx, int cz) {
		return evaluateClusters(merged, enabled, cfg, cx, cz, false);
	}

	/** @param strict true near a big natural structure (see {@link #isBig}): hopper groups and spawner farms need much more. */
	public static List<Finding> evaluateClusters(Map<String, List<Pos>> merged, Set<Signature> enabled, Config cfg, int cx, int cz, boolean strict) {
		Set<Signature> wanted = EnumSet.noneOf(Signature.class);
		for (Signature s : enabled) if (CLUSTER.contains(s)) wanted.add(s);
		if (wanted.isEmpty()) return List.of();
		return clusterFindings(merged, wanted, cfg, p -> Math.floorDiv(p.x(), 16) == cx && Math.floorDiv(p.z(), 16) == cz, strict);
	}

	private static int find(int[] p, int i) {
		while (p[i] != i) {
			p[i] = p[p[i]];
			i = p[i];
		}
		return i;
	}

	private static void union(int[] p, int a, int b) { p[find(p, a)] = find(p, b); }

	/**
	 * Evaluates one chunk.
	 *
	 * @param blocks        block id to positions found in the chunk (only ids from {@link #scanIds()} matter)
	 * @param naturalNearby true when this chunk or a neighbour looks like (part of) a natural structure
	 * @param enabled       which signatures to look for
	 */
	public static List<Finding> evaluate(Map<String, List<Pos>> blocks, boolean naturalNearby, Set<Signature> enabled, Config cfg) {
		return evaluate(blocks, naturalNearby, false, enabled, cfg);
	}

	/** @param strict true near a big natural structure (see {@link #isBig}): hopper groups and spawner farms need much more. */
	public static List<Finding> evaluate(Map<String, List<Pos>> blocks, boolean naturalNearby, boolean strict, Set<Signature> enabled, Config cfg) {
		List<Finding> out = new ArrayList<>(clusterFindings(blocks, enabled, cfg, p -> true, strict));
		List<Pos> spawners = blocks.getOrDefault(SPAWNER, List.of());
		List<Pos> containerPos = positions(blocks, CONTAINERS);

		if (enabled.contains(Signature.LONE_SPAWNER) && !naturalNearby && !spawners.isEmpty()) {
			List<Pos> markers = positions(blocks, SPAWNER_NATURAL_MARKERS);
			for (Pos s : spawners) {
				boolean natural = false;
				for (Pos m : markers) {
					if (d2(s, m) <= 64) {
						natural = true;
						break;
					}
				}
				if (!natural) {
					out.add(box(Signature.LONE_SPAWNER, 32, "spawner with no dungeon, mineshaft or fortress blocks around it", List.of(s)));
					break;
				}
			}
		}

		if (enabled.contains(Signature.BUILDING_BLOCKS) && !naturalNearby) {
			List<Pos> b = positions(blocks, BUILDING_BLOCKS);
			if (b.size() >= cfg.minBuilding()) {
				double score = Math.min(45, 25 + (b.size() - cfg.minBuilding()));
				out.add(box(Signature.BUILDING_BLOCKS, score, b.size() + " concrete, glass or quartz blocks", b));
			}
		}

		if (enabled.contains(Signature.ACTIVITY) && !naturalNearby) {
			List<Pos> lit = new ArrayList<>();
			for (String id : ACTIVITY_BASE) lit.addAll(blocks.getOrDefault(id + LIT, List.of()));
			if (!lit.isEmpty()) {
				out.add(box(Signature.ACTIVITY, Math.min(45, 30 + 4.0 * (lit.size() - 1)), lit.size() + " lit furnace, campfire or lamp", lit));
			}
		}

		if (enabled.contains(Signature.STORAGE_ROOM) && !naturalNearby) {
			int weighted = 0;
			for (Map.Entry<String, List<Pos>> e : blocks.entrySet()) {
				if (!CONTAINERS.contains(e.getKey())) continue;
				weighted += e.getValue().size() * (SHULKERS.contains(e.getKey()) ? 2 : 1);
			}
			if (weighted >= cfg.minContainers()) {
				double score = Math.min(50, 20 + 2.0 * (weighted - cfg.minContainers()));
				out.add(box(Signature.STORAGE_ROOM, score, containerPos.size() + " storage blocks", containerPos));
			}
		}

		if (enabled.contains(Signature.ENDER_CHEST)) {
			List<Pos> e = blocks.getOrDefault(ENDER_CHEST_ID, List.of());
			if (!e.isEmpty()) out.add(box(Signature.ENDER_CHEST, 12, e.size() + " ender chest" + (e.size() > 1 ? "s" : ""), e));
		}

		if (enabled.contains(Signature.REDSTONE) && !naturalNearby) {
			List<Pos> r = positions(blocks, REDSTONE_PARTS);
			if (r.size() >= cfg.minRedstone()) {
				out.add(box(Signature.REDSTONE, Math.min(35, 15 + (r.size() - cfg.minRedstone())), r.size() + " redstone parts", r));
			}
		}

		if (enabled.contains(Signature.VALUABLES)) {
			List<Pos> beacons = blocks.getOrDefault("minecraft:beacon", List.of());
			List<Pos> valuables = positions(blocks, VALUABLE_BLOCKS);
			if (!beacons.isEmpty()) {
				List<Pos> all = new ArrayList<>(beacons);
				all.addAll(valuables);
				out.add(box(Signature.VALUABLES, 35, beacons.size() + " beacon" + (beacons.size() > 1 ? "s" : ""), all));
			} else if (valuables.size() >= 4) {
				out.add(box(Signature.VALUABLES, 30, valuables.size() + " blocks of netherite/diamond/emerald", valuables));
			}
		}
		return out;
	}

	// ---- entities and tunnels (fed by the client, which owns the world access) ----

	/** One entity the client saw: its type id, whether it carries a custom name, and where it is. */
	public record EntityPoint(String type, boolean named, Pos pos) {}

	private static final Set<String> HERD_TYPES = Set.of("minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken",
		"minecraft:rabbit", "minecraft:iron_golem", "minecraft:mooshroom", "minecraft:goat");
	private static final Set<String> FRAME_TYPES = Set.of("minecraft:item_frame", "minecraft:glow_item_frame");
	private static final double ENTITY_LINK = 6;

	/**
	 * Packed-together entities: animal pens and farms (12+ of one herd type), villager cells (5+), armor stand displays (3+),
	 * item frame walls (6+) and groups of named mobs (3+). {@code points} may cover a chunk and its neighbours; only groups
	 * anchored in chunk (cx, cz) are returned so each is reported once. Villager cells are skipped next to natural structures.
	 */
	public static List<Finding> evaluateEntities(List<EntityPoint> points, boolean naturalNearby, int cx, int cz) {
		List<Finding> out = new ArrayList<>();
		Predicate<Pos> mine = p -> Math.floorDiv(p.x(), 16) == cx && Math.floorDiv(p.z(), 16) == cz;
		Map<String, List<Pos>> byType = new java.util.HashMap<>();
		List<Pos> named = new ArrayList<>();
		for (EntityPoint e : points) {
			String t = FRAME_TYPES.contains(e.type()) ? "frames" : e.type();
			byType.computeIfAbsent(t, k -> new ArrayList<>()).add(e.pos());
			if (e.named()) named.add(e.pos());
		}
		for (Map.Entry<String, List<Pos>> en : byType.entrySet()) {
			String type = en.getKey();
			int min;
			double base;
			String what;
			if (HERD_TYPES.contains(type)) {
				min = 12;
				base = 30;
				what = type.substring(type.indexOf(':') + 1).replace('_', ' ') + "s packed together";
			} else if (type.equals("minecraft:villager")) {
				if (naturalNearby) continue;
				min = 5;
				base = 35;
				what = "villagers in a tight space";
			} else if (type.equals("minecraft:armor_stand")) {
				min = 3;
				base = 30;
				what = "armor stands together";
			} else if (type.equals("frames")) {
				min = 6;
				base = 30;
				what = "item frames together";
			} else {
				continue;
			}
			List<Pos> best = List.of();
			for (List<Pos> c : clusters(en.getValue(), ENTITY_LINK)) if (c.size() > best.size() && mine.test(corner(c))) best = c;
			if (best.size() >= min) {
				out.add(box(Signature.ENTITY_CLUSTER, Math.min(50, base + 2.0 * (best.size() - min)), best.size() + " " + what, best));
			}
		}
		if (named.size() >= 3) {
			List<Pos> best = List.of();
			for (List<Pos> c : clusters(named, ENTITY_LINK * 2)) if (c.size() > best.size() && mine.test(corner(c))) best = c;
			if (best.size() >= 3) out.add(box(Signature.ENTITY_CLUSTER, 30, best.size() + " named mobs together", best));
		}
		return out;
	}

	/** A straight-tunnel finding from the longest run (in blocks) and how many runs a chunk has. */
	public static Finding tunnelFinding(int longest, int runs, Pos min, Pos max) {
		double score = Math.min(50, 25 + Math.max(0, longest - 12) * 2.5 + 5.0 * Math.max(0, runs - 1));
		return new Finding(Signature.TUNNEL, score, runs + " straight tunnel" + (runs > 1 ? "s" : "") + ", longest " + longest, min, max);
	}

	/** Chunk score: the strongest signature plus a quarter of the others, so chunks that show several rank above ones that show one. */
	public static double combine(List<Finding> findings) {
		if (findings.isEmpty()) return 0;
		double best = 0, sum = 0;
		for (Finding f : findings) {
			best = Math.max(best, f.score());
			sum += f.score();
		}
		return best + 0.25 * (sum - best);
	}

	public static Finding strongest(List<Finding> findings) {
		Finding best = null;
		for (Finding f : findings) if (best == null || f.score() > best.score()) best = f;
		return best;
	}
}
