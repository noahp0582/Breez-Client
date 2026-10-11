package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.Modules;
import breezeclient.breezeclient.client.gui.Gfx;
import breezeclient.breezeclient.client.hud.HudElements;
import breezeclient.breezeclient.client.world.BlockIds;
import breezeclient.breezeclient.client.world.ChunkAgeTracker;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import breezeclient.breezeclient.core.sus.BaseSignatures;
import breezeclient.breezeclient.core.sus.SusColor;
import breezeclient.breezeclient.core.tunnel.TunnelChain;
import breezeclient.breezeclient.core.world.ChunkAge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/** Base-hunting modules that build on the scanners and finders the client already has. They share this file. */
public final class BaseFindingModules {
	private BaseFindingModules() {}

	private static void ensureOn(String name) {
		Module m = BreezClientClient.MODULES.get(name);
		if (m != null && !m.enabled()) m.setEnabled(true);
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** A HUD panel and `.bedebug` listing of the block entities in the chunk you stand in, by type. */
	public static class BlockEntityDebug extends Module {
		private static BlockEntityDebug instance;
		private final NumberSetting radius = add(new NumberSetting("Chunk radius", 0, 0, 4), "General", "0 = only your chunk; 1 = the 3x3 chunks around you, and so on.");

		private final TreeMap<String, Integer> counts = new TreeMap<>();
		private int chunks;
		private int ticks;

		public BlockEntityDebug() {
			super("BlockEntityDebug", Category.BASE_FINDING);
			instance = this;
			addAction("Print the counts to chat", "Same list as the HUD panel.", () -> {
				if (counts.isEmpty()) breezeclient.breezeclient.client.chat.ChatCommands.print("No block entities counted yet.");
				for (var e : counts.entrySet()) breezeclient.breezeclient.client.chat.ChatCommands.print(e.getValue() + "  " + e.getKey());
			});
		}

		public static void registerHud() {
			HudElements.register(new HudElements.Element() {
				public String id() { return "bedebug"; }
				public String label() { return "Block entity debug"; }
				public int[] defaultPos() { return new int[] {4, 70}; }
				public boolean rightAnchored() { return false; }
				public int[] draw(Gfx g, int x, int y, boolean preview) {
					BlockEntityDebug m = instance;
					if (m == null || !m.enabled() && !preview) return new int[] {0, 0};
					List<String> lines = new ArrayList<>();
					if (m.counts.isEmpty()) lines.add(preview ? "chest 12" : "no block entities");
					for (var e : m.counts.entrySet()) lines.add(e.getValue() + "  " + e.getKey());
					int w = g.width("Block entities (" + m.chunks + " chunks)");
					int yy = y;
					HudElementsText.draw(g, "Block entities (" + m.chunks + " chunks)", x, yy, g.accent());
					yy += 10;
					for (int i = 0; i < Math.min(14, lines.size()); i++) {
						HudElementsText.draw(g, lines.get(i), x + 4, yy, g.text());
						w = Math.max(w, g.width(lines.get(i)) + 4);
						yy += 10;
					}
					return new int[] {w, yy - y};
				}
			});
		}

		@Override
		public String info() {
			int n = 0;
			for (int c : counts.values()) n += c;
			return n == 0 ? "" : String.valueOf(n);
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			ClientLevel level = mc.level;
			if (level == null || mc.player == null || ++ticks % 20 != 0) return;
			counts.clear();
			chunks = 0;
			ChunkPos me = mc.player.chunkPosition();
			int r = radius.asInt();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					LevelChunk c = level.getChunkSource().getChunk(me.x + dx, me.z + dz, false);
					if (c == null) continue;
					chunks++;
					for (BlockEntity be : c.getBlockEntities().values()) {
						String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).getPath();
						counts.merge(id, 1, Integer::sum);
					}
				}
			}
		}
	}

	/** Text drawing that follows the HUD shadow setting (the HUD helper is package-private). */
	private static final class HudElementsText {
		static void draw(Gfx g, String s, int x, int y, int color) {
			if (breezeclient.breezeclient.client.gui.GuiState.THEME.hudShadow.get()) g.textShadow(s, x, y, color);
			else g.text(s, x, y, color);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** Player-placed light sources (torches, lanterns, lamps...) deep underground, where nothing natural lights a room. */
	public static class LightFinder extends Module {
		private final StringListSetting blocks = add(new StringListSetting("Light blocks", List.of("minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch",
			"minecraft:soul_wall_torch", "minecraft:redstone_torch", "minecraft:redstone_wall_torch", "minecraft:lantern", "minecraft:soul_lantern", "minecraft:glowstone",
			"minecraft:sea_lantern", "minecraft:redstone_lamp", "minecraft:jack_o_lantern", "minecraft:end_rod", "minecraft:campfire", "minecraft:soul_campfire",
			"minecraft:verdant_froglight", "minecraft:pearlescent_froglight", "minecraft:ochre_froglight", "minecraft:shroomlight", "minecraft:copper_bulb")
			).kind(StringListSetting.Kind.BLOCK), "Detection", "Light sources that count (only while they actually give light).");
		private final NumberSetting maxY = add(new NumberSetting("Highest Y", 40, -60, 200), "Detection", "Ignore light sources above this height.");
		private final NumberSetting minPerChunk = add(new NumberSetting("Min per chunk", 3, 1, 50), "Detection", "A chunk is flagged when it holds at least this many.");
		private final BoolSetting overworldOnly = add(new BoolSetting("Overworld only", true), "Detection", "Nether and End lighting is mostly natural.");
		private final BoolSetting ignoreNatural = add(new BoolSetting("Ignore natural structures", true), "Detection",
			"Skip chunks in and next to mineshafts, strongholds, villages, dungeons and the other structures that light themselves with torches.");
		private final NumberSetting range = add(new NumberSetting("Range", 128, 16, 512), "Display", "How far away lights are drawn.");
		private final ColorSetting color = add(new ColorSetting("Color", 0xFFFFE066), "Display", "Box color.");
		private final BoolSetting notify = add(new BoolSetting("Notify", true), "Alerts", "Pop-up the first time a chunk is flagged.");

		private ScanTarget target;
		/** The blocks BaseSignatures uses to recognize natural structures; their chunks are not reported. */
		private ScanTarget natural;
		private List<String> applied = List.of();
		private final List<BlockPos> flagged = new ArrayList<>();
		private final Set<Long> announced = new HashSet<>();
		private int ticks;

		public LightFinder() { super("LightFinder", Category.BASE_FINDING); }

		@Override
		public String info() { return flagged.isEmpty() ? "" : String.valueOf(flagged.size()); }

		@Override
		protected void onEnable() {
			applied = List.copyOf(blocks.get());
			target = new ScanTarget(predicate(BlockIds.resolve(applied)));
			ChunkScanner.add(target);
			natural = ScanTarget.ofBlocks(BlockIds.resolve(new ArrayList<>(BaseSignatures.scanIds())));
			ChunkScanner.add(natural);
			announced.clear();
		}

		@Override
		protected void onDisable() {
			if (target != null) ChunkScanner.remove(target);
			if (natural != null) ChunkScanner.remove(natural);
			target = null;
			natural = null;
			flagged.clear();
		}

		private static java.util.function.Predicate<BlockState> predicate(Set<net.minecraft.world.level.block.Block> set) {
			return s -> set.contains(s.getBlock()) && s.getLightEmission() > 0;
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (target == null || mc.level == null || mc.player == null) return;
			if (!applied.equals(blocks.get())) {
				applied = List.copyOf(blocks.get());
				target.setPredicate(predicate(BlockIds.resolve(applied)));
			}
			if (++ticks % 20 != 0) return;
			flagged.clear();
			if (overworldOnly.get() && mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
			int top = maxY.asInt();
			Set<Long> skip = ignoreNatural.get() ? naturalChunks(mc.level) : Set.of();
			for (Map.Entry<Long, List<BlockPos>> en : target.hitsByChunk().entrySet()) {
				int kx = ChunkPos.getX(en.getKey()), kz = ChunkPos.getZ(en.getKey());
				boolean nearNatural = false;
				for (int dx = -1; dx <= 1 && !nearNatural; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (skip.contains(ChunkPos.asLong(kx + dx, kz + dz))) {
							nearNatural = true;
							break;
						}
					}
				}
				if (nearNatural) continue;
				List<BlockPos> low = new ArrayList<>();
				for (BlockPos p : en.getValue()) if (p.getY() <= top) low.add(p);
				if (low.size() < minPerChunk.asInt()) continue;
				flagged.addAll(low);
				if (notify.get() && announced.add(en.getKey())) {
					BlockPos c = low.get(0);
					EspModule.say(low.size() + " lights underground in chunk (" + ChunkPos.getX(en.getKey()) + ", " + ChunkPos.getZ(en.getKey()) + ") near " + c.getX() + " " + c.getY() + " " + c.getZ());
				}
			}
		}

		/** Chunks whose blocks identify a village, mineshaft, stronghold or another natural structure. */
		private Set<Long> naturalChunks(ClientLevel level) {
			Set<Long> out = new HashSet<>();
			if (natural == null) return out;
			for (Map.Entry<Long, List<BlockPos>> en : natural.hitsByChunk().entrySet()) {
				Map<String, Integer> counts = new HashMap<>();
				for (BlockPos p : en.getValue()) counts.merge(BlockIds.idOf(level.getBlockState(p).getBlock()), 1, Integer::sum);
				if (!BaseSignatures.naturalKinds(counts).isEmpty()) out.add(en.getKey());
			}
			return out;
		}

		@Override
		protected void onRender(RenderFrame f) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return;
			double r2 = range.get() * range.get();
			int c = color.get() | 0xFF000000, drawn = 0;
			for (BlockPos p : flagged) {
				if (mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) > r2) continue;
				f.drawBox(p.getX() + 0.2, p.getY() + 0.1, p.getZ() + 0.2, p.getX() + 0.8, p.getY() + 0.9, p.getZ() + 0.8, c);
				if (++drawn >= 300) break;
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** Individual blocks that natural generation does not make where you are: the pieces players build and wire with. */
	public static class SuspiciousEsp extends EspModule {
		private final StringListSetting blocks = add(new StringListSetting("Blocks", List.of("minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:respawn_anchor",
			"minecraft:ender_chest", "minecraft:beacon", "minecraft:hopper", "minecraft:dropper", "minecraft:dispenser", "minecraft:observer", "minecraft:piston",
			"minecraft:sticky_piston", "minecraft:comparator", "minecraft:repeater", "minecraft:tnt", "minecraft:enchanting_table", "minecraft:brewing_stand",
			"minecraft:anvil", "minecraft:netherite_block", "minecraft:diamond_block", "minecraft:emerald_block", "minecraft:slime_block", "minecraft:honey_block")
			).kind(StringListSetting.Kind.BLOCK), "Detection", "Blocks that are suspicious on their own.");
		private final NumberSetting maxY = add(new NumberSetting("Highest Y", 320, -64, 320), "Detection", "Ignore blocks above this height.");
		private final ColorSetting color = add(new ColorSetting("Color", 0xFFFF55FF), "Display", "Box color.");
		private List<String> applied = List.of();

		public SuspiciousEsp() { super("SuspiciousESP", Category.BASE_FINDING); }

		@Override
		protected ScanTarget createTarget() {
			applied = List.copyOf(blocks.get());
			return ScanTarget.ofBlocks(BlockIds.resolve(applied));
		}

		@Override
		protected int colorOf(BlockState s) { return color.get(); }

		@Override
		protected boolean accept(BlockPos p, BlockState s) { return p.getY() <= maxY.asInt(); }

		@Override
		protected void onTick() {
			super.onTick();
			if (target != null && !applied.equals(blocks.get())) {
				applied = List.copyOf(blocks.get());
				var set = BlockIds.resolve(applied);
				target.setPredicate(s -> set.contains(s.getBlock()));
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** Joins TunnelFinder's runs into whole tunnels and marks both ends: a long straight tunnel leads somewhere. */
	public static class TunnelBaseFinder extends Module {
		private final NumberSetting minLength = add(new NumberSetting("Min tunnel length", 32, 16, 400), "Detection", "Shortest joined tunnel that counts.");
		private final NumberSetting gap = add(new NumberSetting("Join gaps up to", 2, 1, 8), "Detection", "Runs this close on the same line count as one tunnel.");
		private final NumberSetting range = add(new NumberSetting("Draw range", 256, 32, 1024), "Display", "How far away tunnels are drawn.");
		private final NumberSetting maxDrawn = add(new NumberSetting("Max drawn", 12, 1, 50), "Display", "Only the longest tunnels are drawn.");
		private final ColorSetting color = add(new ColorSetting("Tunnel color", 0xFFFFA500), "Display", "Line along the tunnel.");
		private final ColorSetting endColor = add(new ColorSetting("End color", 0xFFFF3333), "Display", "Box at each end.");
		private final BoolSetting notify = add(new BoolSetting("Notify", true), "Alerts", "Pop-up for each new tunnel.");

		private final List<TunnelChain.Chain> chains = new ArrayList<>();
		private final Set<Long> announced = new HashSet<>();
		private int ticks;

		public TunnelBaseFinder() { super("TunnelBaseFinder", Category.BASE_FINDING); }

		@Override
		public String info() { return chains.isEmpty() ? "" : String.valueOf(chains.size()); }

		@Override
		protected void onEnable() {
			ensureOn("TunnelFinder");
			announced.clear();
		}

		@Override
		protected void onDisable() { chains.clear(); }

		/** Chains found so far, longest first. */
		public List<TunnelChain.Chain> chains() { return chains; }

		@Override
		protected void onTick() {
			if (++ticks % 40 != 0) return;
			List<TunnelChain.Run> runs = new ArrayList<>();
			for (long key : TunnelFinder.chunkKeys()) {
				for (TunnelFinder.Run r : TunnelFinder.runsIn(ChunkPos.getX(key), ChunkPos.getZ(key))) {
					runs.add(new TunnelChain.Run(r.x(), r.y(), r.z(), r.alongX(), r.length()));
				}
			}
			chains.clear();
			for (TunnelChain.Chain c : TunnelChain.merge(runs, gap.asInt())) {
				if (c.length() < minLength.asInt()) continue;
				chains.add(c);
				long id = ((long) c.x1() * 31 + c.z1()) * 31 + c.y();
				if (notify.get() && announced.add(id)) {
					EspModule.say("Tunnel " + c.length() + " blocks long: " + c.x1() + " " + c.y() + " " + c.z1() + " to " + c.x2() + " " + c.y() + " " + c.z2());
				}
			}
		}

		@Override
		protected void onRender(RenderFrame f) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return;
			double r2 = range.get() * range.get();
			int shown = 0;
			for (TunnelChain.Chain c : chains) {
				double mx = (c.x1() + c.x2()) / 2.0, mz = (c.z1() + c.z2()) / 2.0;
				if (mc.player.distanceToSqr(mx, c.y(), mz) > r2) continue;
				if (shown++ >= maxDrawn.asInt()) break;
				int tc = color.get() | 0xFF000000, ec = endColor.get() | 0xFF000000;
				f.drawLine(c.x1() + 0.5, c.y() + 1, c.z1() + 0.5, c.x2() + 0.5, c.y() + 1, c.z2() + 0.5, tc);
				f.drawBox(c.x1() - 0.2, c.y(), c.z1() - 0.2, c.x1() + 1.2, c.y() + 2.4, c.z1() + 1.2, ec);
				f.drawBox(c.x2() - 0.2, c.y(), c.z2() - 0.2, c.x2() + 1.2, c.y() + 2.4, c.z2() + 1.2, ec);
				f.drawText(c.x1() + 0.5, c.y() + 3, c.z1() + 0.5, "Tunnel end (" + c.length() + ")", ec);
				f.drawText(c.x2() + 0.5, c.y() + 3, c.z2() + 0.5, "Tunnel end (" + c.length() + ")", ec);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Combines the other finders: a chunk is "prime" when several independent signals agree (a base signature, a stash,
	 * a straight tunnel, an old chunk standing in new terrain). One strong signal alone is not enough.
	 */
	public static class PrimeChunkFinder extends Module {
		public record Result(int chunkX, int chunkZ, int score, List<String> reasons, int y) {}

		private static PrimeChunkFinder instance;

		private final NumberSetting threshold = add(new NumberSetting("Min score", 4, 2, 12), "Detection",
			"Points needed: base signature 3, stash 2, old chunk in new terrain 2, tunnel 1. Four needs at least two signals.");
		private final NumberSetting minDistance = add(new NumberSetting("Ignore chunks within", 4, 0, 32), "Detection", "Skip chunks this close to you.");
		private final NumberSetting maxDrawn = add(new NumberSetting("Max drawn", 8, 1, 50), "Display", "Only the best chunks are drawn.");
		private final NumberSetting range = add(new NumberSetting("Draw range", 512, 32, 2048), "Display", "How far away chunks are drawn.");
		private final BoolSetting helpers = add(new BoolSetting("Turn on the finders it needs", true), "General",
			"Switch on SusChunks, StashFinder, TunnelFinder and NewChunks when this module is enabled.");
		private final BoolSetting notify = add(new BoolSetting("Notify", true), "Alerts", "Pop-up for each new prime chunk.");
		private final BoolSetting waypoint = add(new BoolSetting("Waypoint", false), "Alerts", "Save a waypoint for each prime chunk.");

		private final List<Result> results = new ArrayList<>();
		private final Set<Long> announced = new HashSet<>();
		private int ticks;

		public PrimeChunkFinder() {
			super("PrimeChunkFinder", Category.BASE_FINDING);
			instance = this;
		}

		public static List<Result> top(int n) {
			if (instance == null) return List.of();
			return new ArrayList<>(instance.results.subList(0, Math.min(n, instance.results.size())));
		}

		@Override
		public String info() { return results.isEmpty() ? "" : String.valueOf(results.size()); }

		@Override
		protected void onEnable() {
			announced.clear();
			if (helpers.get()) {
				ensureOn("SusChunks");
				ensureOn("StashFinder");
				ensureOn("TunnelFinder");
				ensureOn("NewChunks");
			}
		}

		@Override
		protected void onDisable() { results.clear(); }

		private static long key(int cx, int cz) { return ChunkPos.asLong(cx, cz); }

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.level == null || ++ticks % 40 != 0) return;
			Map<Long, Integer> score = new HashMap<>();
			Map<Long, List<String>> why = new HashMap<>();
			Map<Long, Integer> ys = new HashMap<>();
			for (SusChunks.Result r : SusChunks.top(200)) {
				long k = key(r.chunkX(), r.chunkZ());
				score.merge(k, 3, Integer::sum);
				why.computeIfAbsent(k, x -> new ArrayList<>()).add("base signature: " + r.label());
				ys.put(k, r.center().getY());
			}
			for (StashFinder.Result r : StashFinder.top(200)) {
				long k = key(r.chunkX(), r.chunkZ());
				score.merge(k, 2, Integer::sum);
				why.computeIfAbsent(k, x -> new ArrayList<>()).add("stash: " + r.summary());
				ys.putIfAbsent(k, r.center().getY());
			}
			for (long k : TunnelFinder.chunkKeys()) {
				if (TunnelFinder.runsIn(ChunkPos.getX(k), ChunkPos.getZ(k)).isEmpty()) continue;
				score.merge(k, 1, Integer::sum);
				why.computeIfAbsent(k, x -> new ArrayList<>()).add("straight tunnel");
				ys.putIfAbsent(k, TunnelFinder.runsIn(ChunkPos.getX(k), ChunkPos.getZ(k)).get(0).y());
			}
			// an old chunk with new neighbours: someone has been here and the area around it is untouched
			Set<Long> loaded = ChunkScanner.loadedChunks();

			for (long k : loaded) {
				int cx = ChunkPos.getX(k), cz = ChunkPos.getZ(k);
				if (ChunkAgeTracker.state(cx, cz) != ChunkAge.State.OLD) continue;
				int fresh = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if ((dx != 0 || dz != 0) && ChunkAgeTracker.state(cx + dx, cz + dz) == ChunkAge.State.NEW) fresh++;
					}
				}
				if (fresh >= 3) {
					score.merge(k, 2, Integer::sum);
					why.computeIfAbsent(k, x -> new ArrayList<>()).add("old chunk in new terrain");
				}
			}
			ChunkPos me = mc.player.chunkPosition();
			results.clear();
			for (Map.Entry<Long, Integer> en : score.entrySet()) {
				if (en.getValue() < threshold.asInt()) continue;
				int cx = ChunkPos.getX(en.getKey()), cz = ChunkPos.getZ(en.getKey());
				if (Math.max(Math.abs(cx - me.x), Math.abs(cz - me.z)) <= minDistance.asInt()) continue;
				int y = ys.getOrDefault(en.getKey(), mc.player.getBlockY());
				results.add(new Result(cx, cz, en.getValue(), why.get(en.getKey()), y));
			}
			results.sort(Comparator.comparingInt(Result::score).reversed());
			for (Result r : results) {
				if (!announced.add(key(r.chunkX(), r.chunkZ()))) continue;
				if (notify.get()) EspModule.say("Prime chunk (" + r.chunkX() + ", " + r.chunkZ() + ") score " + r.score() + ": " + String.join(", ", r.reasons()));
				if (waypoint.get()) Waypoints.add("Prime " + r.score(), new BlockPos(r.chunkX() * 16 + 8, r.y(), r.chunkZ() * 16 + 8));
			}
		}

		@Override
		protected void onRender(RenderFrame f) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || results.isEmpty()) return;
			double r2 = range.get() * range.get();
			int best = results.get(0).score(), shown = 0;
			for (Result r : results) {
				double dx = r.chunkX() * 16 + 8 - mc.player.getX(), dz = r.chunkZ() * 16 + 8 - mc.player.getZ();
				if (dx * dx + dz * dz > r2) continue;
				if (shown++ >= maxDrawn.asInt()) break;
				int c = SusColor.gradient(SusColor.fraction(r.score(), threshold.get(), Math.max(best, threshold.get() + 1))) | 0xFF000000;
				DonutModules.drawChunk(f, r.chunkX(), r.chunkZ(), r.y() - 2, r.y() + 6, c);
				f.drawText(r.chunkX() * 16 + 8, r.y() + 8, r.chunkZ() * 16 + 8, "Prime " + r.score(), c);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Teleports with your random-teleport command, waits for the area to load, checks the finders, and repeats until
	 * something worth a look turns up. It stops by itself, and when the world changes.
	 */
	public static class RtpBaseFinder extends Module {
		private enum State { IDLE, SENT, SCAN, COOLDOWN }

		private final StringSetting command = add(new StringSetting("Command", "/rtp"), "Command", "The command that teleports you to a random place.");
		private final NumberSetting cooldown = add(new NumberSetting("Cooldown (s)", 15, 3, 600), "Command", "Minimum time between teleports (many servers enforce their own).");
		private final NumberSetting scanSeconds = add(new NumberSetting("Scan time (s)", 8, 2, 60), "Scan", "How long to let chunks load and the finders run after each teleport.");
		private final NumberSetting radius = add(new NumberSetting("Check radius (blocks)", 160, 32, 600), "Scan", "Only finds within this distance of where you land count.");
		private final NumberSetting minScore = add(new NumberSetting("Min SusChunks score", 30, 5, 150), "Scan", "Smallest base score that stops the search.");
		private final BoolSetting stashes = add(new BoolSetting("Stashes count too", true), "Scan", "Also stop for StashFinder results.");
		private final NumberSetting maxTeleports = add(new NumberSetting("Max teleports (0 = no limit)", 100, 0, 1000), "Safety", "Stop after this many teleports.");
		private final BoolSetting waypoint = add(new BoolSetting("Waypoint on find", true), "Alerts", "Save a waypoint where it stopped.");

		private final ActionLimiter limiter = new ActionLimiter(new Random());
		private State state = State.IDLE;
		private int timer;
		private int count;
		private Vec3 sentFrom;
		private Object level;

		public RtpBaseFinder() { super("RTPBaseFinder", Category.BASE_FINDING); }

		@Override
		protected boolean persistsEnabled() { return false; }

		@Override
		public String info() { return count == 0 ? "" : count + " tp"; }

		@Override
		protected void onEnable() {
			ensureOn("SusChunks");
			if (stashes.get()) ensureOn("StashFinder");
			state = State.IDLE;
			count = 0;
			timer = 20;
			level = Minecraft.getInstance().level;
		}

		@Override
		protected void onDisable() { state = State.IDLE; }

		private void finish(String why) {
			breezeclient.breezeclient.client.gui.Toasts.show("RTPBaseFinder", why);
			setEnabled(false);
		}


		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.level == null || mc.getConnection() == null) {
				setEnabled(false);
				return;
			}
			switch (state) {
				case IDLE -> {
					if (--timer > 0 || mc.screen != null) return;
					if (maxTeleports.asInt() > 0 && count >= maxTeleports.asInt()) {
						finish("Stopped after " + count + " teleports, nothing found");
						return;
					}
					String c = command.get().trim();
					if (c.isEmpty()) {
						finish("No command set");
						return;
					}
					sentFrom = p.position();
					if (c.startsWith("/")) mc.getConnection().sendCommand(c.substring(1));
					else mc.getConnection().sendChat(c);
					count++;
					state = State.SENT;
					timer = 20 * 20; // twenty seconds for the teleport to happen
				}
				case SENT -> {
					boolean moved = p.position().distanceToSqr(sentFrom) > 50 * 50;
					if (moved || mc.level != level) {
						level = mc.level;
						state = State.SCAN;
						timer = scanSeconds.asInt() * 20;
						SusChunks.resetAnnounced();
					} else if (--timer <= 0) { // never teleported (cooldown or no permission): try again later
						state = State.COOLDOWN;
						timer = cooldown.asInt() * 20;
					}
				}
				case SCAN -> {
					if (--timer > 0) return;
					Vec3 here = p.position();
					double r2 = radius.get() * radius.get();
					for (SusChunks.Result r : SusChunks.top(20)) {
						var c = r.center();
						if (r.score() >= minScore.get() && here.distanceToSqr(c.getX(), here.y, c.getZ()) <= r2) {
							if (waypoint.get()) Waypoints.add("RTP find", c);
							finish("Found " + r.label() + " (score " + (int) r.score() + ") at " + c.getX() + " " + c.getY() + " " + c.getZ());
							return;
						}
					}
					if (stashes.get()) {
						for (StashFinder.Result r : StashFinder.top(10)) {
							var c = r.center();
							if (here.distanceToSqr(c.getX(), here.y, c.getZ()) <= r2) {
								if (waypoint.get()) Waypoints.add("RTP stash", c);
								finish("Found a stash at " + c.getX() + " " + c.getY() + " " + c.getZ() + ": " + r.summary());
								return;
							}
						}
					}
					state = State.COOLDOWN;
					timer = limiter.nextDelayTicks(cooldown.get() * 20, cooldown.get() * 2, 60);
				}
				case COOLDOWN -> {
					if (--timer > 0) return;
					state = State.IDLE;
					timer = 1;
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * With the world seed set, finds chunks where many of the ores world generation put are gone: somebody has been mining
	 * there. Caves also remove ores, so only chunks with a large share missing are flagged.
	 */
	public static class SeedChunkFinder extends Module {
		public record Result(int chunkX, int chunkZ, int predicted, int missing, BlockPos sample) {
			public double ratio() { return predicted == 0 ? 0 : (double) missing / predicted; }
		}

		private final NumberSetting minOres = add(new NumberSetting("Min predicted ores", 10, 3, 100), "Detection", "Chunks with fewer predicted ores are not judged.");
		private final NumberSetting minRatio = add(new NumberSetting("Min share missing (%)", 55, 20, 100), "Detection",
			"How much of the predicted ore must be gone (air or player blocks in its place). Natural caves rarely remove more than a third.");
		private final NumberSetting minDistance = add(new NumberSetting("Ignore chunks within", 3, 0, 32), "Detection", "Skip chunks this close to you.");
		private final NumberSetting maxDrawn = add(new NumberSetting("Max drawn", 10, 1, 50), "Display", "Only the most mined chunks are drawn.");
		private final BoolSetting notify = add(new BoolSetting("Notify", true), "Alerts", "Pop-up the first time a chunk is flagged.");

		private final List<Result> results = new ArrayList<>();
		private final Set<Long> announced = new HashSet<>();
		private int ticks;

		public SeedChunkFinder() { super("SeedChunkFinder", Category.BASE_FINDING); }

		@Override
		public String info() { return results.isEmpty() ? "" : String.valueOf(results.size()); }

		@Override
		protected void onEnable() {
			announced.clear();
			ensureOn("SeedOres");
		}

		@Override
		protected void onDisable() { results.clear(); }

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			ClientLevel level = mc.level;
			if (level == null || mc.player == null || Modules.seedOres == null || ++ticks % 60 != 0) return;
			results.clear();
			ChunkPos me = mc.player.chunkPosition();
			for (Map.Entry<Long, Map<BlockPos, BlockState>> en : Modules.seedOres.simulated().entrySet()) {
				int cx = ChunkPos.getX(en.getKey()), cz = ChunkPos.getZ(en.getKey());
				if (Math.max(Math.abs(cx - me.x), Math.abs(cz - me.z)) <= minDistance.asInt()) continue;
				if (level.getChunkSource().getChunk(cx, cz, false) == null) continue; // unloaded chunks read as air, not as mined
				int predicted = 0, missing = 0;
				BlockPos sample = null;
				for (BlockPos p : en.getValue().keySet()) {
					predicted++;
					BlockState actual = level.getBlockState(p);
					if (actual.isAir() || BaseSignatures.BUILDING_BLOCKS.contains(BlockIds.idOf(actual.getBlock()))) {
						missing++;
						if (sample == null) sample = p;
					}
				}
				if (predicted < minOres.asInt() || sample == null) continue;
				Result r = new Result(cx, cz, predicted, missing, sample);
				if (r.ratio() * 100 >= minRatio.get()) results.add(r);
			}
			results.sort(Comparator.comparingDouble(Result::ratio).reversed());
			for (Result r : results) {
				if (!announced.add(ChunkPos.asLong(r.chunkX(), r.chunkZ())) || !notify.get()) continue;
				EspModule.say(String.format(Locale.ROOT, "Mined-out chunk (%d, %d): %d of %d predicted ores gone, near %d %d %d",
					r.chunkX(), r.chunkZ(), r.missing(), r.predicted(), r.sample().getX(), r.sample().getY(), r.sample().getZ()));
			}
		}

		@Override
		protected void onRender(RenderFrame f) {
			int shown = 0;
			for (Result r : results) {
				if (shown++ >= maxDrawn.asInt()) break;
				int c = SusColor.gradient(Math.min(1.0, r.ratio())) | 0xFF000000;
				DonutModules.drawChunk(f, r.chunkX(), r.chunkZ(), r.sample().getY() - 3, r.sample().getY() + 4, c);
				f.drawText(r.chunkX() * 16 + 8, r.sample().getY() + 5, r.chunkZ() * 16 + 8, (int) (r.ratio() * 100) + "% mined", c);
			}
		}
	}
}
