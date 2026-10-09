package breezeclient.breezeclient.client.seed;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Predicts where vanilla world generation put ore veins (overworld ores, nether ancient debris) from the world seed.
 *
 * <p>It does not reimplement the decoration pipeline: it runs the game's own {@link FeatureSorter}, placement modifiers and
 * {@link OreConfiguration}s, and ports the two ore feature algorithms ({@code OreFeature}, {@code ScatteredOreFeature})
 * so they run against the client's loaded blocks instead of a generating chunk. Noise-based ore veins (the huge copper
 * and iron veins) and overlap order between neighbouring chunks are not modelled.
 */
public final class SeedOreSim {
	private static final Logger LOG = Logger.getLogger("breez-client");

	private record Dim(List<FeatureSorter.StepFeatureData> steps, ChunkGenerator generator) {}

	private static HolderLookup.Provider lookup;
	private static final Map<ResourceKey<Level>, Dim> DIMS = new HashMap<>();
	private static final Map<ResourceKey<Biome>, Holder<Biome>> BIOME_CACHE = new HashMap<>();

	public static final long[] STATS = new long[8]; // positions, heightFail, veins, claims, biomeNull, chunks, featuresRun, noBiomes

	private SeedOreSim() {}

	/** Minecraft's rule for the seed field: a number is used as-is, any other text is hashed. */
	public static long parseSeed(String text) {
		String t = text.trim();
		try {
			return Long.parseLong(t);
		} catch (NumberFormatException e) {
			return t.hashCode();
		}
	}

	public static boolean supports(ResourceKey<Level> dim) { return dim == Level.OVERWORLD || dim == Level.NETHER; }

	private static synchronized HolderLookup.Provider lookup() {
		if (lookup == null) lookup = VanillaRegistries.createLookup();
		return lookup;
	}

	private static synchronized Dim dim(ResourceKey<Level> d) {
		Dim cached = DIMS.get(d);
		if (cached != null) return cached;
		var key = d == Level.NETHER ? MultiNoiseBiomeSourceParameterLists.NETHER : MultiNoiseBiomeSourceParameterLists.OVERWORLD;
		var settingsKey = d == Level.NETHER ? NoiseGeneratorSettings.NETHER : NoiseGeneratorSettings.OVERWORLD;
		var preset = lookup().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(key);
		MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(preset);
		List<Holder<Biome>> biomes = List.copyOf(source.possibleBiomes());
		var steps = FeatureSorter.buildFeaturesPerStep(biomes, b -> b.value().getGenerationSettings().features(), true);
		var gen = new NoiseBasedChunkGenerator(source, lookup().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settingsKey));
		Dim dim = new Dim(steps, gen);
		DIMS.put(d, dim);
		return dim;
	}

	private static synchronized Holder<Biome> ours(Holder<Biome> clientBiome) {
		var key = clientBiome.unwrapKey().orElse(null);
		if (key == null) return null;
		return BIOME_CACHE.computeIfAbsent(key, k -> lookup().lookupOrThrow(Registries.BIOME).get(k).orElse(null));
	}

	// terrain model --------------------------------------------------------------------------------------------------

	private static boolean isOre(BlockState s) {
		return s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.GOLD_ORES) || s.is(BlockTags.DIAMOND_ORES)
			|| s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES) || s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.EMERALD_ORES)
			|| s.is(Blocks.NETHER_GOLD_ORE) || s.is(Blocks.NETHER_QUARTZ_ORE) || s.is(Blocks.ANCIENT_DEBRIS);
	}

	/** The block that was at this spot when the ore feature ran: ores already in the world stand for their host rock. */
	/** Vegetation, snow and similar decoration that is generated after ores; it was air when the ore features ran. */
	private static boolean laterDecoration(BlockState s) {
		if (s.isAir() || !s.getFluidState().isEmpty()) return false;
		return s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.LOGS)
			|| s.is(Blocks.SNOW) || s.is(Blocks.MOSS_CARPET) || s.is(Blocks.GLOW_LICHEN) || s.is(Blocks.SPORE_BLOSSOM) || s.is(Blocks.HANGING_ROOTS)
			|| s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT) || s.is(Blocks.BIG_DRIPLEAF) || s.is(Blocks.BIG_DRIPLEAF_STEM) || s.is(Blocks.SMALL_DRIPLEAF)
			|| s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS)
			|| s.is(Blocks.BAMBOO) || s.is(Blocks.BAMBOO_SAPLING) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.AZALEA) || s.is(Blocks.FLOWERING_AZALEA)
			|| s.is(Blocks.POINTED_DRIPSTONE) || s.is(Blocks.SCULK_VEIN);
	}

	private static BlockState atGeneration(BlockState s) {
		if (laterDecoration(s)) return Blocks.AIR.defaultBlockState();
		if (!isOre(s)) return s;
		if (s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) || s.is(Blocks.DEEPSLATE_COAL_ORE) || s.is(Blocks.DEEPSLATE_IRON_ORE) || s.is(Blocks.DEEPSLATE_GOLD_ORE)
			|| s.is(Blocks.DEEPSLATE_DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_REDSTONE_ORE) || s.is(Blocks.DEEPSLATE_LAPIS_ORE)
			|| s.is(Blocks.DEEPSLATE_COPPER_ORE) || s.is(Blocks.DEEPSLATE_EMERALD_ORE)) {
			return Blocks.DEEPSLATE.defaultBlockState();
		}
		if (s.is(Blocks.NETHER_GOLD_ORE) || s.is(Blocks.NETHER_QUARTZ_ORE) || s.is(Blocks.ANCIENT_DEBRIS)) return Blocks.NETHERRACK.defaultBlockState();
		return Blocks.STONE.defaultBlockState();
	}

	private static final class Run {
		final ClientLevel level;
		final Map<BlockPos, BlockState> claimed = new HashMap<>();
		final RandomSource random;

		Run(ClientLevel level, RandomSource random) {
			this.level = level;
			this.random = random;
		}

		BlockState state(BlockPos p) {
			BlockState c = claimed.get(p);
			return c != null ? c : atGeneration(level.getBlockState(p));
		}
	}

	// public entry point ---------------------------------------------------------------------------------------------

	/** Ore blocks predicted for veins that originate in chunk {@code cp}, keyed by position. May spill into neighbours. */
	public static Map<BlockPos, BlockState> simulate(ClientLevel level, ChunkPos cp, long seed) {
		if (level != heightsLevel) { // column heights belong to one level; never reuse them across dimensions or servers
			heightsLevel = level;
			HEIGHTS.clear();
		}
		if (!supports(level.dimension())) return Map.of();
		Dim dim = dim(level.dimension());
		int ox = cp.getMinBlockX(), oz = cp.getMinBlockZ();
		BlockPos origin = new BlockPos(ox, level.getMinY(), oz);
		WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
		long decoration = random.setDecorationSeed(seed, ox, oz);
		Set<Holder<Biome>> biomes = biomesAround(level, cp);
		STATS[5]++;
		if (biomes.isEmpty()) STATS[7]++;
		WorldGenLevel proxy = proxy(level, seed);
		Run run = new Run(level, random);

		for (int step = 0; step < dim.steps().size(); step++) {
			FeatureSorter.StepFeatureData data = dim.steps().get(step);
			IntArraySet indices = new IntArraySet();
			for (Holder<Biome> b : biomes) {
				List<HolderSet<PlacedFeature>> perStep = b.value().getGenerationSettings().features();
				if (step < perStep.size()) perStep.get(step).stream().map(Holder::value).forEach(pf -> indices.add(data.indexMapping().applyAsInt(pf)));
			}
			int[] sorted = indices.toIntArray();
			java.util.Arrays.sort(sorted);
			for (int index : sorted) {
				PlacedFeature pf = data.features().get(index);
				ConfiguredFeature<?, ?> cf = pf.feature().value();
				if (cf.feature() != Feature.ORE && cf.feature() != Feature.SCATTERED_ORE) continue;
				random.setFeatureSeed(decoration, index, step);
				STATS[6]++;
				try {
					place(run, dim, proxy, pf, cf, origin);
				} catch (RuntimeException e) {
					LOG.log(java.util.logging.Level.WARNING, "seed ore sim failed for " + pf, e);
				}
			}
		}
		return run.claimed;
	}

	private static Set<Holder<Biome>> biomesAround(ClientLevel level, ChunkPos cp) {
		Set<Holder<Biome>> out = new HashSet<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				LevelChunk c = level.getChunkSource().getChunk(cp.x + dx, cp.z + dz, false);
				if (c == null) continue;
				for (LevelChunkSection s : c.getSections()) {
					s.getBiomes().getAll(h -> {
						Holder<Biome> o = ours(h);
						if (o != null) out.add(o);
					});
				}
			}
		}
		return out;
	}

	/** True when this chunk and all 8 neighbours are loaded, so the simulation sees the same terrain the generator did. */
	public static boolean ready(ClientLevel level, ChunkPos cp) {
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (level.getChunkSource().getChunk(cp.x + dx, cp.z + dz, false) == null) return false;
		return true;
	}

	// feature placement ----------------------------------------------------------------------------------------------

	private static void place(Run run, Dim dim, WorldGenLevel proxy, PlacedFeature pf, ConfiguredFeature<?, ?> cf, BlockPos origin) {
		PlacementContext ctx = new PlacementContext(proxy, dim.generator(), Optional.of(pf));
		Stream<BlockPos> stream = Stream.of(origin);
		for (PlacementModifier m : pf.placement()) stream = stream.flatMap(p -> m.getPositions(ctx, run.random, p));
		OreConfiguration cfg = (OreConfiguration) cf.config();
		boolean scattered = cf.feature() == Feature.SCATTERED_ORE;
		stream.forEach(p -> {
			STATS[0]++;
			if (scattered) placeScattered(run, cfg, p);
			else placeVein(run, cfg, p);
		});
	}

	/** Height of the first free block above the highest motion-blocking block of a column (scanned from loaded chunk data, because the client does not reliably prime its own heightmaps). */
	private static final Map<Long, int[]> HEIGHTS = new HashMap<>();

	private static ClientLevel heightsLevel;

	public static void clearCaches() { HEIGHTS.clear(); }

	static int height(ClientLevel level, int x, int z) {
		long key = ChunkPos.asLong(x >> 4, z >> 4);
		int[] cols = HEIGHTS.get(key);
		if (cols == null) {
			LevelChunk c = level.getChunkSource().getChunk(x >> 4, z >> 4, false);
			if (c == null) return level.getMinY();
			if (HEIGHTS.size() > 4000) HEIGHTS.clear();
			cols = new int[256];
			LevelChunkSection[] sections = c.getSections();
			int minSection = c.getMinY() >> 4;
			for (int cx = 0; cx < 16; cx++) {
				for (int cz = 0; cz < 16; cz++) {
					int h = c.getMinY();
					outer:
					for (int i = sections.length - 1; i >= 0; i--) {
						if (sections[i].hasOnlyAir()) continue;
						for (int y = 15; y >= 0; y--) {
							BlockState st = sections[i].getBlockState(cx, y, cz);
							if (st.blocksMotion() || !st.getFluidState().isEmpty()) {
								h = ((minSection + i) << 4) + y + 1;
								break outer;
							}
						}
					}
					cols[cx + cz * 16] = h;
				}
			}
			HEIGHTS.put(key, cols);
		}
		return cols[(x & 15) + (z & 15) * 16];
	}

	/** Port of ScatteredOreFeature.place. */
	private static void placeScattered(Run run, OreConfiguration cfg, BlockPos origin) {
		RandomSource random = run.random;
		int count = random.nextInt(cfg.size + 1);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int j = 0; j < count; j++) {
			int spread = Math.min(j, 7);
			int dx = Math.round((random.nextFloat() - random.nextFloat()) * spread);
			int dy = Math.round((random.nextFloat() - random.nextFloat()) * spread);
			int dz = Math.round((random.nextFloat() - random.nextFloat()) * spread);
			pos.setWithOffset(origin, dx, dy, dz);
			BlockState state = run.state(pos);
			for (OreConfiguration.TargetBlockState target : cfg.targetStates) {
				if (OreFeature.canPlaceOre(state, run::state, random, cfg, target, pos)) {
					run.claimed.put(pos.immutable(), target.state);
					break;
				}
			}
		}
	}

	/** Port of OreFeature.place + doPlace. */
	private static void placeVein(Run run, OreConfiguration cfg, BlockPos origin) {
		RandomSource random = run.random;
		float f = random.nextFloat() * (float) Math.PI;
		float g = cfg.size / 8.0F;
		int i = Mth.ceil((cfg.size / 16.0F * 2.0F + 1.0F) / 2.0F);
		double x1 = origin.getX() + Math.sin(f) * g;
		double x2 = origin.getX() - Math.sin(f) * g;
		double z1 = origin.getZ() + Math.cos(f) * g;
		double z2 = origin.getZ() - Math.cos(f) * g;
		double y1 = origin.getY() + random.nextInt(3) - 2;
		double y2 = origin.getY() + random.nextInt(3) - 2;
		int minX = origin.getX() - Mth.ceil(g) - i;
		int minY = origin.getY() - 2 - i;
		int minZ = origin.getZ() - Mth.ceil(g) - i;
		int w = 2 * (Mth.ceil(g) + i);
		int h = 2 * (2 + i);
		for (int s = minX; s <= minX + w; s++) {
			for (int t = minZ; t <= minZ + w; t++) {
				if (minY <= height(run.level, s, t)) {
					STATS[2]++;
					doPlace(run, cfg, x1, x2, z1, z2, y1, y2, minX, minY, minZ, w, h);
					return;
				}
			}
		}
		STATS[1]++;
	}

	private static void doPlace(Run run, OreConfiguration cfg, double d, double e, double f, double g, double h, double i, int j, int k, int l, int m, int n) {
		RandomSource random = run.random;
		BitSet bitSet = new BitSet(m * n * m);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int p = cfg.size;
		double[] ds = new double[p * 4];
		for (int q = 0; q < p; q++) {
			float r = (float) q / p;
			double s = Mth.lerp(r, d, e);
			double t = Mth.lerp(r, h, i);
			double u = Mth.lerp(r, f, g);
			double v = random.nextDouble() * p / 16.0;
			double w = ((Mth.sin((float) Math.PI * r) + 1.0F) * v + 1.0) / 2.0;
			ds[q * 4] = s;
			ds[q * 4 + 1] = t;
			ds[q * 4 + 2] = u;
			ds[q * 4 + 3] = w;
		}
		for (int q = 0; q < p - 1; q++) {
			if (!(ds[q * 4 + 3] <= 0.0)) {
				for (int x = q + 1; x < p; x++) {
					if (!(ds[x * 4 + 3] <= 0.0)) {
						double s = ds[q * 4] - ds[x * 4];
						double t = ds[q * 4 + 1] - ds[x * 4 + 1];
						double u = ds[q * 4 + 2] - ds[x * 4 + 2];
						double v = ds[q * 4 + 3] - ds[x * 4 + 3];
						if (v * v > s * s + t * t + u * u) {
							if (v > 0.0) ds[x * 4 + 3] = -1.0;
							else ds[q * 4 + 3] = -1.0;
						}
					}
				}
			}
		}
		for (int x = 0; x < p; x++) {
			double s = ds[x * 4 + 3];
			if (s < 0.0) continue;
			double t = ds[x * 4];
			double u = ds[x * 4 + 1];
			double v = ds[x * 4 + 2];
			int y = Math.max(Mth.floor(t - s), j);
			int z = Math.max(Mth.floor(u - s), k);
			int aa = Math.max(Mth.floor(v - s), l);
			int ab = Math.max(Mth.floor(t + s), y);
			int ac = Math.max(Mth.floor(u + s), z);
			int ad = Math.max(Mth.floor(v + s), aa);
			for (int ae = y; ae <= ab; ae++) {
				double af = (ae + 0.5 - t) / s;
				if (af * af >= 1.0) continue;
				for (int ag = z; ag <= ac; ag++) {
					double ah = (ag + 0.5 - u) / s;
					if (af * af + ah * ah >= 1.0) continue;
					for (int ai = aa; ai <= ad; ai++) {
						double aj = (ai + 0.5 - v) / s;
						if (af * af + ah * ah + aj * aj < 1.0 && !run.level.isOutsideBuildHeight(ag)) {
							int ak = ae - j + (ag - k) * m + (ai - l) * m * n;
							if (bitSet.get(ak)) continue;
							bitSet.set(ak);
							pos.set(ae, ag, ai);
							BlockState state = run.state(pos);
							for (OreConfiguration.TargetBlockState target : cfg.targetStates) {
								if (OreFeature.canPlaceOre(state, run::state, random, cfg, target, pos)) {
									run.claimed.put(pos.immutable(), target.state);
									break;
								}
							}
						}
					}
				}
			}
		}
	}

	// minimal WorldGenLevel -----------------------------------------------------------------------------------------------

	private static WorldGenLevel proxy(ClientLevel level, long seed) {
		return (WorldGenLevel) Proxy.newProxyInstance(SeedOreSim.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class}, (self, method, args) -> {
			String n = method.getName();
			int argc = args == null ? 0 : args.length;
			switch (n) {
				case "getMinY": return level.getMinY();
				case "getMaxY": return level.getMaxY();
				case "getHeight":
					if (argc == 0) return level.getHeight();
					return height(level, (Integer) args[1], (Integer) args[2]);
				case "getMinSectionY": return level.getMinY() >> 4;
				case "getMaxSectionY": return (level.getMaxY() - 1) >> 4;
				case "getSectionsCount": return level.getSectionsCount();
				case "isOutsideBuildHeight": return args[0] instanceof Integer y ? level.isOutsideBuildHeight(y) : level.isOutsideBuildHeight((BlockPos) args[0]);
				case "getBiome": {
					Holder<Biome> h = ours(level.getBiome((BlockPos) args[0]));
					if (h == null) STATS[4]++;
					return h != null ? h : level.getBiome((BlockPos) args[0]);
				}
				case "getBlockState": return atGeneration(level.getBlockState((BlockPos) args[0]));
				case "getSeed": return seed;
				case "hashCode": return System.identityHashCode(self);
				case "equals": return self == args[0];
				case "toString": return "SeedOreSim.proxy";
				default: throw new UnsupportedOperationException("SeedOreSim: WorldGenLevel." + n + " is not modelled");
			}
		});
	}

	/** Used by tests/diagnostics: list of every ore state the simulator may produce. */
	public static List<BlockState> oreStatesOf(Map<BlockPos, BlockState> predicted) { return new ArrayList<>(predicted.values()); }
}
