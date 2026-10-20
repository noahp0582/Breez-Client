package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.input.Keys;
import breezeclient.breezeclient.client.nav.PearlUp;
import breezeclient.breezeclient.client.nav.WorkNavigator;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.client.nav.LevelWorld;
import breezeclient.breezeclient.core.nav.Goal;
import breezeclient.breezeclient.core.nav.Pathfinder;
import breezeclient.breezeclient.core.nav.Pos;
import breezeclient.breezeclient.core.schem.SchematicData;
import breezeclient.breezeclient.core.schem.SchematicFiles;
import breezeclient.breezeclient.core.schem.SchematicReader;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import breezeclient.breezeclient.core.text.PriceParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Builds a loaded schematic by hand, the way the schematic "printers" work: instead of following a fixed list, it keeps
 * looking at every block of the build within reach, picks one that can be placed right now from where it stands (lowest
 * layer first, then the one nearest the crosshair), looks at the face it has to click and places it. When nothing in
 * reach can be worked on, the pathfinder takes it to the nearest spot where something can. Orientation is checked by
 * asking the block what a click would produce, so stairs, logs, slabs, hoppers and the like come out the right way round.
 */
public class SchematicBuilder extends Module {
	private enum State { IDLE, SCAN, NAV, CENTER, PEARL, DIG, AIM, PLACE, VERIFY, NEED_ITEMS, BUY, DONE }

	private enum Kind { PLACE, DIG }

	private record Target(BlockPos pos, BlockState want) {}

	/** One thing to do now: place the block of a target, or dig out what is in its place. */
	private record Job(BlockPos pos, BlockState want, Kind kind) {}

	private final StringSetting file = add(new StringSetting("File", ""));
	private final NumberSetting reach = add(new NumberSetting("Reach", 4.0, 2.5, 4.5));
	private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 4, 1, 20));
	private final NumberSetting layers = add(new NumberSetting("Layers at once", 1, 1, 8), "General",
		"How many layers it works on at the same time, counted up from the lowest unfinished one. 1 builds strictly bottom-up, which is the surest; more saves walking on open builds.");
	private final BoolSetting autoDig = add(new BoolSetting("Auto dig", true), "General",
		"Break the blocks that are in the way of the build with the best tool in your hotbar, looking at each one first. Works with 3x3 pickaxes: blocks they clear are simply skipped. Containers, spawners and unbreakable blocks are never dug.");
	private final BoolSetting scaffold = add(new BoolSetting("Scaffold when stuck", true), "General",
		"When walking and jumping cannot reach a spot to build from, pillar up or bridge with a spare block from your hotbar (the Baritone module's Placeable blocks list). Those blocks are taken down again at the end.");
	private final BoolSetting pearlUp = add(new BoolSetting("Pearl up to high work", true), "General",
		"On tall builds, throws an ender pearl onto the build to get up to the work instead of pillaring a long way (or when there is no way up at all). It aims at flat ground near the work, simulating the pearl's flight first, and needs more than 5 hearts in survival (a pearl costs 2.5).");
	private final NumberSetting pearlRise = add(new NumberSetting("Pearl when work is this high", 6, 3, 40), "General",
		"How many blocks above your feet the work has to be before a pearl is thrown instead of pillaring. With no route at all it throws for anything 2 or more blocks up.", pearlUp::get);
	private final BoolSetting sealIn = add(new BoolSetting("May shut itself in", false), "General",
		"Off: a block that would leave it no way to walk out of the build is put off until it can be placed from a spot that has one (so walls are closed from outside, ceilings from the doorway). On: it places whatever it can.");
	private final BoolSetting strict = add(new BoolSetting("Exact orientation only", false), "General",
		"On: a block it cannot place the right way round is left out. Off: after trying every spot it can stand on, it places it anyway and tells you how many may need turning.");
	private final BoolSetting dirtForGrass = add(new BoolSetting("Dirt for grass blocks", true), "General",
		"Places dirt where the schematic has grass, podzol, mycelium, a path or farmland.");
	private final BoolSetting creativeSupply = add(new BoolSetting("Creative: take missing blocks", true), "General",
		"In creative mode, a block you do not have is taken from the creative inventory into your hotbar, the way pick block does.");
	private final BoolSetting autoBuy = add(new BoolSetting("Buy missing blocks", false), "Auction",
		"When it runs out of a block, opens the auction house, searches for it and buys the cheapest listings within the limits below. Off: it waits for you to add the block.");
	private final StringSetting maxEach = add(new StringSetting("Max price per block", "100"), "Auction",
		"The most it pays for one block: a listing costs its price divided by its stack size. Accepts 250, 1.5k, 2m.", autoBuy::get);
	private final StringListSetting limits = add(new StringListSetting("Price limit per block type", List.of()), "Auction",
		"Overrides for single blocks, written as block=price, e.g. obsidian=2k or minecraft:sea_lantern=900.", autoBuy::get);
	private final StringSetting budget = add(new StringSetting("Max total spend", "50k"), "Auction",
		"The most one build may spend in all. Buying stops for the rest of the build once it is reached.", autoBuy::get);
	private final StringSetting ahCommand = add(new StringSetting("Auction command", "/ah"), "Auction", "Chat command that opens the auction house.", autoBuy::get);
	private final BoolSetting signSearch = add(new BoolSetting("Search with the sign button", true), "Auction",
		"Clicks the search sign in the auction window and types the block name on it. Off (or when there is no such button): searches with the command, like /ah oak planks.", autoBuy::get);
	private final StringSetting searchLabel = add(new StringSetting("Search button contains", "search"), "Auction", "Name of the search button (case ignored). Any sign item is used when nothing has this name.", () -> autoBuy.get() && signSearch.get());
	private final StringSetting confirmLabel = add(new StringSetting("Confirm button contains", "confirm"), "Auction", "Name of the button that confirms a purchase (case ignored).", autoBuy::get);

	private final Random rng = new Random();
	private final ActionLimiter limiter = new ActionLimiter(rng);
	private final WorkNavigator nav = new WorkNavigator();
	private final AuctionBuyer buyer = new AuctionBuyer();
	private final PearlUp pearl = new PearlUp();
	/** Landing cells a pearl did not get it to; not aimed at again in this build. */
	private final Set<Long> badLandings = new HashSet<>();
	private int pearlFails;
	private int pearlsThrown;

	private SchematicData loaded;
	private BlockPos origin;
	private State state = State.IDLE;
	private int timer;

	/** Every block of the build by packed position. */
	private final Map<Long, BlockState> plan = new HashMap<>();
	private int total;
	/** Footprint of the build: min x, min z, max x, max z. */
	private final int[] box = new int[4];
	/** Not right yet and not given up. */
	private final Set<Long> pending = new HashSet<>();
	/** No spot found to work on these from; tried again whenever something new has been placed. */
	private final Set<Long> stuck = new HashSet<>();
	/** May be placed without the exact orientation: no spot gives it. */
	private final Set<Long> relaxed = new HashSet<>();
	private final Map<Long, String> givenUp = new HashMap<>();
	private final Map<Long, Integer> tries = new HashMap<>();
	/** Spare blocks the pathing put down, to be dug out once the build is done. */
	private final Set<Long> clear = new HashSet<>();
	private boolean cleaning;
	private boolean faceFirst;
	private boolean innerFirst;
	/** The last scan found something it could only not place because it was standing partly in that block's space. */
	private boolean inOwnWay;
	private int centerTicks;
	private int centerTries;
	private int rechecks;
	/** Items it has run out of: their blocks do not hold the face-first phase up. */
	private final Set<Item> givenUpItems = new HashSet<>();
	/** The layer limit is off: the lowest layers have nothing that can be done right now. */
	private boolean anyLayer;
	private int done;
	private int wrongWay;

	private Job job;
	private Placer.Plan aim;
	private Vec3 digPoint;
	private Item missing;
	private List<BlockPos> frontier = List.of();
	private final Map<Long, Kind> frontierKind = new HashMap<>();
	private int settle;
	private int lookTicks;
	private int digTicks;
	private int navTicks;
	private int navFails;
	private int idleArrivals;
	private int notReady;
	private int stage;
	/** Walks toward blocks in chunks that are not loaded yet; counted so an unreachable one cannot be chased forever. */
	private int farTries;

	private double spentTotal;
	private boolean buyBroken;
	private long buyRetryAt;
	private Item buyRetryItem;

	public SchematicBuilder() { super("SchematicBuilder", Category.WORLD); }

	/** Breez's own schematics folder (kept for people who already use it). */
	public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("breez-client").resolve("schematics"); }

	/**
	 * Folders searched for schematics, in order: the folder this mod's jar sits in, the mods folder, Breez's own folder
	 * and Litematica's {@code schematics} folder. Only folders that exist are returned (Breez's own is created).
	 */
	public static List<Path> searchDirs() {
		var loader = FabricLoader.getInstance();
		LinkedHashSet<Path> out = new LinkedHashSet<>();
		try {
			loader.getModContainer("breez-client").ifPresent(c -> {
				try {
					for (Path root : c.getOrigin().getPaths()) {
						Path abs = root.toAbsolutePath().normalize();
						if (Files.isRegularFile(abs) && abs.getParent() != null) out.add(abs.getParent()); // the jar's folder
					}
				} catch (UnsupportedOperationException ignored) {
					// a nested or generated mod has no path of its own
				}
			});
		} catch (RuntimeException ignored) {
			// fall back to the folders below
		}
		out.add(loader.getGameDir().resolve("mods").toAbsolutePath().normalize());
		try {
			Files.createDirectories(dir());
		} catch (IOException ignored) {
			// listing simply skips it
		}
		out.add(dir().toAbsolutePath().normalize());
		out.add(loader.getGameDir().resolve("schematics").toAbsolutePath().normalize());
		out.add(loader.getConfigDir().resolve("worldedit").resolve("schematics").toAbsolutePath().normalize());
		List<Path> existing = new ArrayList<>();
		for (Path p : out) if (Files.isDirectory(p)) existing.add(p);
		return existing;
	}

	/** Every schematic in the search folders and their subfolders; one walk of the folders per call. */
	public static List<SchematicFiles.Entry> found() {
		return SchematicFiles.scan(searchDirs());
	}

	/** The names to type for {@code all}: the path below its search folder, e.g. "v2/house.litematic". */
	public static List<String> files(List<SchematicFiles.Entry> all) {
		List<String> out = new ArrayList<>();
		for (SchematicFiles.Entry e : all) out.add(e.name());
		return out;
	}

	/** Finds a schematic by name or file name (extension optional, case ignored) in the search folders. */
	public static SchematicFiles.Entry locate(String name) {
		return SchematicFiles.find(found(), name);
	}

	/** The most recently changed schematic found, or null. */
	public static SchematicFiles.Entry newest() {
		SchematicFiles.Entry best = null;
		long bestTime = Long.MIN_VALUE;
		for (SchematicFiles.Entry e : found()) {
			try {
				long t = Files.getLastModifiedTime(e.path()).toMillis();
				if (t > bestTime) {
					bestTime = t;
					best = e;
				}
			} catch (IOException ignored) {
				// skip it
			}
		}
		return best;
	}

	private static String searched() {
		StringBuilder sb = new StringBuilder();
		for (Path d : searchDirs()) sb.append(sb.length() == 0 ? "" : "; ").append(d);
		return sb.toString();
	}

	/** @return an error message, or null when the file loaded. */
	public String load(String name) {
		SchematicFiles.Entry e = locate(name);
		if (e == null) return "no schematic named '" + name + "'. Put .litematic, .schem, .schematic or .nbt files next to the mod jar (subfolders count) (searched: " + searched() + ")";
		try {
			loaded = SchematicReader.read(Files.readAllBytes(e.path()), e.fileName());
			file.set(e.name());
			return null;
		} catch (IOException ex) {
			loaded = null;
			return e.fileName() + ": " + ex.getMessage();
		}
	}

	/**
	 * Loads a schematic (the named one, else the File setting, else the newest one found), puts its minimum corner where
	 * you are looking (or two blocks ahead of you) and starts building.
	 *
	 * @return an error message, or null when the build started
	 */
	public String buildNow(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return "join a world first";
		String pick = name == null || name.isBlank() ? file.get() : name;
		if (pick == null || pick.isBlank()) {
			SchematicFiles.Entry n = newest();
			if (n == null) return "no schematics found. Put .litematic, .schem, .schematic or .nbt files next to the mod jar (subfolders count) (searched: " + searched() + ")";
			pick = n.name();
		}
		String err = load(pick);
		if (err != null) return err;
		BlockPos at;
		if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
			at = bh.getBlockPos().relative(bh.getDirection()); // the spot on the face you are looking at
		} else {
			Direction f = mc.player.getDirection();
			at = mc.player.blockPosition().relative(f, 2);
		}
		origin = at;
		if (enabled()) setEnabled(false);
		setEnabled(true);
		return enabled() ? null : "could not start";
	}

	/** One line per folder searched with how many of {@code all} (from {@link #found()}) it holds. */
	public static List<String> describeFolders(List<SchematicFiles.Entry> all) {
		List<String> out = new ArrayList<>();
		for (Path d : searchDirs()) {
			int n = 0;
			for (SchematicFiles.Entry e : all) if (e.path().startsWith(d)) n++;
			out.add(d + "  (" + n + " schematic" + (n == 1 ? "" : "s") + ")");
		}
		return out;
	}


	public void setOrigin(BlockPos p) { origin = p; }

	public BlockPos origin() { return origin; }

	public boolean loaded() { return loaded != null; }

	public String status() {
		if (loaded == null) return "No schematic loaded. Put files next to the mod jar and use .build auto [file] (searched: " + searched() + ").";
		return String.format("%s: %d/%d placed, %d to go, state %s%s%s", file.get(), done, total, pending.size(), state.name().toLowerCase(Locale.ROOT),
			givenUp.isEmpty() ? "" : ", " + leftOut(), wrongWay > 0 ? ", " + wrongWay + " may need turning" : "")
			+ (pearlsThrown > 0 ? ", " + pearlsThrown + " pearl" + (pearlsThrown == 1 ? "" : "s") + " thrown" : "");
	}

	/** Pearls thrown in this build (for the self-test). */
	public int pearlsThrown() { return pearlsThrown; }

	private final java.util.ArrayDeque<String> events = new java.util.ArrayDeque<>();

	/** The last things it decided (pearls thrown and how they went), newest last: the quickest way to see a loop. */
	public List<String> events() { return new ArrayList<>(events); }

	private static String feetText() {
		var p = Minecraft.getInstance().player;
		return p == null ? "?" : p.blockPosition().toShortString();
	}

	private void event(String what) {
		if (events.size() >= 80) events.removeFirst();
		var mc = Minecraft.getInstance();
		events.addLast((mc.level == null ? "" : mc.level.getGameTime() + " ") + what);
	}

	/** "4 left out (3 nowhere to stand to place it, 1 cannot dig Chest)". */
	private String leftOut() {
		Map<String, Integer> by = new java.util.TreeMap<>();
		for (String why : givenUp.values()) by.merge(why, 1, Integer::sum);
		StringBuilder sb = new StringBuilder();
		for (var e : by.entrySet()) sb.append(sb.length() == 0 ? "" : ", ").append(e.getValue()).append(' ').append(e.getKey());
		return givenUp.size() + " left out (" + sb + ")";
	}

	/** Why blocks were left out in this run, one line each (for the self-test). */
	public List<String> notes() {
		List<String> out = new ArrayList<>();
		for (var e : givenUp.entrySet()) out.add(BlockPos.of(e.getKey()).toShortString() + " " + e.getValue());
		return out;
	}

	@Override
	public String info() { return loaded == null ? "" : done + "/" + total; }

	// lifecycle --------------------------------------------------------------------------------------------------

	@Override
	protected boolean persistsEnabled() { return false; }

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (loaded == null || origin == null || mc.level == null) {
			EspModule.say("SchematicBuilder: nothing to build yet. Use .build auto [file] (or the 'Load and build' button) to load a schematic and start at your crosshair.");
			setEnabled(false);
			return;
		}
		plan.clear();
		pending.clear();
		box[0] = box[1] = Integer.MAX_VALUE;
		box[2] = box[3] = Integer.MIN_VALUE;
		for (SchematicData.Block b : loaded.linearOrder()) {
			BlockState st = parse(b.state());
			if (st == null || st.isAir()) continue;
			if (dirtForGrass.get() && (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM) || st.is(Blocks.DIRT_PATH) || st.is(Blocks.FARMLAND))) {
				st = Blocks.DIRT.defaultBlockState();
			}
			long key = origin.offset(b.x(), b.y(), b.z()).asLong();
			box[0] = Math.min(box[0], BlockPos.getX(key));
			box[1] = Math.min(box[1], BlockPos.getZ(key));
			box[2] = Math.max(box[2], BlockPos.getX(key));
			box[3] = Math.max(box[3], BlockPos.getZ(key));
			plan.put(key, st);
			pending.add(key);
		}
		total = plan.size();
		stuck.clear();
		relaxed.clear();
		givenUp.clear();
		tries.clear();
		clear.clear();
		cleaning = anyLayer = false;
		farTries = 0;
		rechecks = 0;
		givenUpItems.clear();
		done = wrongWay = 0;
		job = null;
		aim = null;
		missing = null;
		navFails = idleArrivals = notReady = 0;
		spentTotal = 0;
		buyBroken = false;
		buyRetryItem = null;
		badLandings.clear();
		pearlFails = pearlsThrown = 0;
		state = State.SCAN;
		timer = 10;
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (state == State.BUY) buyer.abort(mc);
		if (state == State.DIG && mc.gameMode != null) mc.gameMode.stopDestroyBlock(); // never leave a half-dug block cracking
		Keys.release(mc.options.keyShift);
		nav.cancel();
		pearl.cancel();
		RotationController.clear();
		job = null;
		state = State.IDLE;
	}

	private static BlockState parse(String s) {
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
		} catch (CommandSyntaxException e) {
			return null;
		}
	}

	// bookkeeping ------------------------------------------------------------------------------------------------

	private void markDone(long key) {
		if (pending.remove(key)) done++;
		farTries = 0;
		stuck.remove(key);
		tries.remove(key);
	}

	private void giveUp(long key, String why) {
		if (pending.contains(key)) event("give up " + BlockPos.of(key).toShortString() + ": " + why + " (feet " + feetText() + ")");
		if (pending.remove(key)) givenUp.put(key, why);
		stuck.remove(key);
		tries.remove(key);
	}

	/** Another failed attempt at this block; the third one leaves it out. */
	private void failed(long key, String why) {
		if (tries.merge(key, 1, Integer::sum) >= 3) giveUp(key, why);
	}

	private static boolean diggable(Minecraft mc, BlockPos pos) {
		BlockState s = mc.level.getBlockState(pos);
		if (s.getDestroySpeed(mc.level, pos) < 0 || mc.level.getBlockEntity(pos) != null) return false; // unbreakable, containers, spawners
		return !UtilityModules.NEVER_MINE.contains(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
	}

	private boolean have(LocalPlayer p, Item item) {
		if (creativeSupply.get() && p.hasInfiniteMaterials()) return true;
		for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).is(item)) return true;
		return false;
	}

	/** Where its eyes are while it places: it sneaks, so a little lower than standing. */
	private static Vec3 workEye(LocalPlayer p) { return p.position().add(0, p.getEyeHeight(Pose.CROUCHING), 0); }

	/** Highest layer it may work on now: a few above the lowest one that still has something to do. */
	private int topLayer() {
		int low = Integer.MAX_VALUE;
		for (long key : pending) {
			if (stuck.contains(key)) continue;
			int y = BlockPos.getY(key);
			if (y < low) low = y;
		}
		int top = low == Integer.MAX_VALUE ? Integer.MAX_VALUE : low + layers.asInt() - 1;
		// Blocks that must be clicked on one particular face (logs, pillars) go first in their layers: once the blocks around
		// them are in, that face is often walled off.
		// The rim of the footprint waits for what is inside it: a wall closed too early shuts the work behind it away.
		faceFirst = innerFirst = false;
		for (long key : pending) {
			if (stuck.contains(key) || BlockPos.getY(key) > top || givenUpItems.contains(plan.get(key).getBlock().asItem())) continue;
			if (rank(plan.get(key)) == 0) faceFirst = true;
			if (!innerFirst && !onRim(BlockPos.getX(key), BlockPos.getZ(key)) && standsNow(key)) innerFirst = true;
			if (faceFirst && innerFirst) break;
		}
		return top;
	}

	/** The order inside the working layers: face-critical blocks, then everything inside the footprint, then its rim. Digging any time. */
	private boolean onRim(int x, int z) { return x == box[0] || x == box[2] || z == box[1] || z == box[3]; }

	/** False for a block that hangs off something not built yet (a torch waiting for its wall): it must not hold the rim back. */
	private boolean standsNow(long key) {
		var level = Minecraft.getInstance().level;
		return level == null || plan.get(key).canSurvive(level, BlockPos.of(key));
	}

	private boolean allowedNow(Job j) {
		if (j.kind() == Kind.PLACE && !cleaning && sealsCorner(Minecraft.getInstance(), j.pos())) return false;
		if (cleaning || anyLayer || j.kind() == Kind.DIG) return true;
		if (faceFirst) return rank(j.want()) == 0;
		return !innerFirst || !onRim(j.pos().getX(), j.pos().getZ());
	}

	/**
	 * What there is to do at one position right now: place its block, dig what is in the way, or nothing (it is done, it
	 * cannot be done yet, or it has just been given up).
	 */
	private Job classify(Minecraft mc, LocalPlayer p, BlockPos pos) {
		long key = pos.asLong();
		if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return null;
		BlockState cur = mc.level.getBlockState(pos);
		BlockState want = plan.get(key);
		if (want == null || !pending.contains(key)) { // a spare block of ours to take down
			if (!clear.contains(key) || !cleaning) return null;
			if (cur.isAir() || !diggable(mc, pos)) {
				clear.remove(key);
				return null;
			}
			return new Job(pos, Blocks.AIR.defaultBlockState(), Kind.DIG);
		}
		if (Placer.halfOfDouble(cur, want)) return itemJob(p, pos, want, key);
		if (cur.getBlock() == want.getBlock()) {
			if (Placer.hasOrientation(want) && !Placer.oriented(cur, want)) wrongWay++;
			markDone(key);
			return null;
		}
		if (cur.isAir() || cur.canBeReplaced()) {
			if (!want.canSurvive(mc.level, pos)) return null; // what it stands on or hangs from is not there yet
			return itemJob(p, pos, want, key);
		}
		if (!autoDig.get()) {
			giveUp(key, "something else is there (turn on Auto dig)");
			return null;
		}
		if (!diggable(mc, pos)) {
			giveUp(key, "cannot dig " + cur.getBlock().getName().getString());
			return null;
		}
		return new Job(pos, want, Kind.DIG);
	}

	private Job itemJob(LocalPlayer p, BlockPos pos, BlockState want, long key) {
		Item item = want.getBlock().asItem();
		if (item == Items.AIR) {
			giveUp(key, "block has no item form");
			return null;
		}
		if (!have(p, item)) {
			givenUpItems.add(item);
			if (missing == null) missing = item;
			return null;
		}
		return new Job(pos, want, Kind.PLACE);
	}

	/** Can this job be done from an eye at {@code eye}, with feet in {@code feet} (null = where the player really is)? */
	private boolean workable(Minecraft mc, LocalPlayer p, Job j, Vec3 eye, Pos feet, boolean keep) {
		if (j.kind() == Kind.DIG) {
			Vec3 point = Placer.visiblePoint(mc, p, j.pos(), eye, reach.get());
			if (keep) digPoint = point;
			return point != null;
		}
		boolean half = Placer.halfOfDouble(mc.level.getBlockState(j.pos()), j.want());
		if (!half) { // standing in the block's own space: nothing can be placed there
			if (feet == null) {
				var shape = j.want().getCollisionShape(mc.level, j.pos());
				if (!shape.isEmpty() && p.getBoundingBox().deflate(0.005).intersects(shape.bounds().move(j.pos()))) {
					inOwnWay = true;
					return false;
				}
			} else if (j.pos().getX() == feet.x() && j.pos().getZ() == feet.z() && (j.pos().getY() == feet.y() || j.pos().getY() == feet.y() + 1)) {
				return false;
			}
		}
		Placer.Plan plan = Placer.find(mc, p, j.pos(), j.want(), j.want().getBlock().asItem(), eye, reach.get(), !relaxed.contains(j.pos().asLong()));
		if (keep) aim = plan;
		return plan != null;
	}

	private boolean outside(int x, int z) { return x < box[0] - 1 || x > box[2] + 1 || z < box[1] - 1 || z > box[3] + 1; }

	/** Can a player with feet in {@code from} walk clear of the build, counting {@code filled} (if any) as a solid block? */
	private boolean canLeave(Minecraft mc, Pos from, BlockPos filled) {
		if (outside(from.x(), from.z())) return true;
		LevelWorld base = new LevelWorld(mc.level);
		Pathfinder.World w = new Pathfinder.World() {
			private boolean isFilled(int x, int y, int z) { return filled != null && filled.getX() == x && filled.getY() == y && filled.getZ() == z; }

			@Override
			public boolean passable(int x, int y, int z) { return !isFilled(x, y, z) && base.passable(x, y, z); }

			@Override
			public boolean ground(int x, int y, int z) { return isFilled(x, y, z) || base.ground(x, y, z); }
		};
		Goal clear = new Goal() {
			@Override
			public boolean isEnd(Pos p) { return outside(p.x(), p.z()); }

			@Override
			public double heuristic(Pos p) { // steps to the nearest edge of the footprint
				return Math.max(0, Math.min(Math.min(p.x() - box[0], box[2] - p.x()), Math.min(p.z() - box[1], box[3] - p.z())) + 2);
			}
		};
		// a block in the column we stand in leaves no room for the body: that is shut in, whatever the search says
		if (filled != null && filled.getX() == from.x() && filled.getZ() == from.z() && (filled.getY() == from.y() || filled.getY() == from.y() + 1)) return false;
		Pathfinder.Result r = Pathfinder.search(LevelWorld.fromCrouch(w, mc.level, from.x(), from.y(), from.z()), from, clear, Pathfinder.Options.walking(3), 4000);
		return r == null || r.reachedGoal() || r.outOfBudget(); // only a search that saw every reachable cell says "shut in"
	}

	/** True when placing this block would take away the last way out for a player standing at {@code feet}. */
	private boolean wouldTrap(Minecraft mc, Pos feet, Job j) {
		if (sealIn.get() || j.kind() != Kind.PLACE || outside(feet.x(), feet.z())) return false;
		return canLeave(mc, feet, null) && !canLeave(mc, feet, j.pos());
	}

	private static int rank(BlockState s) {
		if (s.hasProperty(BlockStateProperties.AXIS)) return 0; // needs one particular face: while it is still open
		boolean top = (s.hasProperty(BlockStateProperties.SLAB_TYPE) && s.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP)
			|| (s.hasProperty(BlockStateProperties.HALF) && s.getValue(BlockStateProperties.HALF) == Half.TOP);
		return top ? 2 : 1; // hangs off the side of a neighbour: after the neighbours
	}

	/** The build has blocks on two sides of this one that meet at a right angle: an inside corner, hidden once both are in. */
	private boolean corner(BlockPos pos) {
		boolean x = plan.containsKey(pos.east().asLong()) || plan.containsKey(pos.west().asLong());
		boolean z = plan.containsKey(pos.north().asLong()) || plan.containsKey(pos.south().asLong());
		return x && z && !(plan.containsKey(pos.east().asLong()) && plan.containsKey(pos.west().asLong())) && !(plan.containsKey(pos.north().asLong()) && plan.containsKey(pos.south().asLong()));
	}

	/**
	 * Placing here would close the last open side of a corner of the build that is still to come: once both of its side
	 * neighbours are in, it can no longer be seen from inside. It waits until that corner is placed (or given up).
	 */
	private boolean sealsCorner(Minecraft mc, BlockPos pos) {
		if (mc.level == null) return false;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos c = pos.relative(d);
			if (!pending.contains(c.asLong()) || !corner(c)) continue;
			BlockState cur = mc.level.getBlockState(c);
			if (!cur.isAir() && !cur.canBeReplaced()) continue; // something to dig there first
			for (Direction side : new Direction[] {d.getClockWise(), d.getCounterClockWise()}) {
				BlockPos o = c.relative(side);
				BlockState s = mc.level.getBlockState(o);
				if (plan.containsKey(o.asLong()) && !s.isAir() && !s.canBeReplaced()) return true;
			}
		}
		return false;
	}

	/** How many of the four side neighbours hold a solid block right now. */
	private static int filledSides(Minecraft mc, BlockPos pos) {
		int n = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState s = mc.level.getBlockState(pos.relative(d));
			if (!s.isAir() && !s.canBeReplaced()) n++;
		}
		return n;
	}

	/** Looks at everything of the build within reach and returns the first thing that can be done from here, or null. */
	private Job scan(Minecraft mc, LocalPlayer p) {
		inOwnWay = false;
		missing = null;
		Vec3 eye = workEye(p);
		double r = reach.get();
		int span = (int) Math.ceil(r) + 1;
		int top = cleaning || anyLayer ? Integer.MAX_VALUE : topLayer();
		BlockPos c = BlockPos.containing(eye);
		List<Job> found = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dy = -span; dy <= span; dy++) {
			int y = c.getY() + dy;
			if (y > top) continue;
			for (int dx = -span; dx <= span; dx++) {
				for (int dz = -span; dz <= span; dz++) {
					m.set(c.getX() + dx, y, c.getZ() + dz);
					long key = m.asLong();
					if (!pending.contains(key) && !(cleaning && clear.contains(key))) continue;
					if (eye.distanceToSqr(m.getX() + 0.5, y + 0.5, m.getZ() + 0.5) > (r + 0.9) * (r + 0.9)) continue;
					Job j = classify(mc, p, m.immutable());
					if (j != null && allowedNow(j)) found.add(j);
				}
			}
		}
		if (found.isEmpty()) return null;
		// lowest layer first, face-critical blocks before the rest, then whatever needs the smallest turn of the head
		Rotation look = RotationController.current();
		// then the ones with the fewest sides already filled: a corner whose two neighbours are in can no longer be seen from inside
		found.sort(java.util.Comparator.comparingInt((Job j) -> j.pos().getY()).thenComparingInt(j -> j.kind() == Kind.DIG ? -1 : rank(j.want()))
			.thenComparingInt(j -> j.kind() == Kind.DIG ? 0 : corner(j.pos()) ? 0 : 1 + Math.min(2, filledSides(mc, j.pos())))
			.thenComparingDouble(j -> {
				Rotation to = RotationController.rotationTo(eye, Vec3.atCenterOf(j.pos()));
				return Math.hypot(breezeclient.breezeclient.core.humanize.Rotations.wrapDegrees(to.yaw() - look.yaw()), to.pitch() - look.pitch());
			}));
		Pos feet = WorkNavigator.feet(p);
		for (Job j : found) if (!wouldTrap(mc, feet, j) && workable(mc, p, j, eye, null, true)) return j;
		return null;
	}

	// main loop --------------------------------------------------------------------------------------------------

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.gameMode == null) return;
		if (state == State.BUY) {
			Keys.release(mc.options.keyShift);
			buy(mc, p);
			return;
		}
		if (p.containerMenu != p.inventoryMenu) p.closeContainer(); // a click opened a chest/crafting table: never stay inside it
		nav.tick();
		// sneak so clicks do not use containers or doors; while walking the pathing decides
		if (state == State.AIM || state == State.PLACE || state == State.VERIFY || state == State.CENTER) Keys.press(mc.options.keyShift);
		else if (state != State.NAV) Keys.release(mc.options.keyShift);
		if (timer > 0) {
			timer--;
			return;
		}
		switch (state) {
			case SCAN -> {
				Job j = scan(mc, p);
				if (j != null) {
					begin(j);
				} else if (inOwnWay && centerTries < 2 && p.onGround() && offCentre(p) > 0.15) {
					centerTries++; // half a step into a neighbouring cell: stand in the middle of this one and look again
					centerTicks = 0;
					state = State.CENTER;
				} else {
					navigate(mc, p);
				}
			}
			case CENTER -> centre(mc, p);
			case PEARL -> pearling(mc, p);
			case NAV -> walking(mc, p);
			case DIG -> dig(mc, p);
			case AIM -> aim(mc, p);
			case PLACE -> place(mc, p);
			case VERIFY -> verify(mc, p);
			case NEED_ITEMS -> needItems(mc, p);
			case DONE -> {
				EspModule.say("SchematicBuilder finished. " + status());
				setEnabled(false);
			}
			default -> { }
		}
	}

	private static double offCentre(LocalPlayer p) {
		return Math.hypot(Math.floor(p.getX()) + 0.5 - p.getX(), Math.floor(p.getZ()) + 0.5 - p.getZ());
	}

	/** Sneaks to the middle of the cell it stands in, so its body is out of the cells next to it. */
	private void centre(Minecraft mc, LocalPlayer p) {
		double dx = Math.floor(p.getX()) + 0.5 - p.getX(), dz = Math.floor(p.getZ()) + 0.5 - p.getZ();
		if (Math.hypot(dx, dz) < 0.1 || ++centerTicks > 30) {
			mc.options.keyUp.setDown(false);
			RotationController.clear();
			rescan(2);
			return;
		}
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		RotationController.aimAt(new Rotation(yaw, p.getXRot()), 14f);
		mc.options.keyUp.setDown(Math.abs(breezeclient.breezeclient.core.humanize.Rotations.wrapDegrees(yaw - p.getYRot())) < 20f);
	}

	private void begin(Job j) {
		job = j;
		centerTries = 0;
		idleArrivals = navFails = 0;
		settle = lookTicks = digTicks = 0;
		state = j.kind() == Kind.DIG ? State.DIG : State.AIM;
	}

	private void rescan(int wait) {
		job = null;
		aim = null;
		state = State.SCAN;
		timer = wait;
	}

	// getting there ----------------------------------------------------------------------------------------------

	/** The nearest things still to do (at most 48), wherever they are; the pathfinder looks for a spot to do one of them from. */
	private List<BlockPos> nearestWork(Minecraft mc, LocalPlayer p) {
		int top = cleaning || anyLayer ? Integer.MAX_VALUE : topLayer();
		List<Long> keys = new ArrayList<>();
		for (long key : pending) if (!stuck.contains(key) && BlockPos.getY(key) <= top) keys.add(key);
		if (cleaning) for (long key : clear) if (!stuck.contains(key)) keys.add(key);
		double px = p.getX(), py = p.getY(), pz = p.getZ();
		keys.sort(java.util.Comparator.comparingDouble(k -> {
			double dx = BlockPos.getX(k) + 0.5 - px, dy = BlockPos.getY(k) + 0.5 - py, dz = BlockPos.getZ(k) + 0.5 - pz;
			return dx * dx + dy * dy * 4 + dz * dz; // height counts double: stay on a level while there is work on it
		}));
		List<BlockPos> out = new ArrayList<>();
		frontierKind.clear();
		for (long key : keys) {
			Job j = classify(mc, p, BlockPos.of(key));
			if (j == null || !allowedNow(j)) continue;
			out.add(j.pos());
			frontierKind.put(key, j.kind());
			if (out.size() >= 48) break;
		}
		return out;
	}

	private void navigate(Minecraft mc, LocalPlayer p) {
		Item lacking = missing; // what the scan around us ran out of
		frontier = nearestWork(mc, p);
		if (lacking == null) lacking = missing;
		if (frontier.isEmpty() && !anyLayer && !cleaning) {
			anyLayer = true; // nothing workable in the lowest layers (no blocks for them, nothing to place against): look higher
			frontier = nearestWork(mc, p);
			if (lacking == null) lacking = missing;
		}
		if (frontier.isEmpty()) {
			conclude(mc, p, lacking);
			return;
		}
		if (scaffold.get() && !cleaning) spareBlock(mc, p);
		double r = reach.get();
		WorkNavigator.Spot spot = (feet, eye) -> {
			int tried = 0;
			for (BlockPos t : frontier) {
				if (eye.distanceToSqr(Vec3.atCenterOf(t)) > (r + 0.9) * (r + 0.9)) continue;
				if (++tried > 10) break; // the frontier is nearest-first: ten in reach is plenty to tell whether this cell is any use
				Kind k = frontierKind.get(t.asLong());
				BlockState want = plan.get(t.asLong());
				if (k == null) continue;
				Job j = new Job(t, want == null ? Blocks.AIR.defaultBlockState() : want, k);
				if (workable(mc, p, j, eye, feet, false) && !wouldTrap(mc, feet, j)) return true;
			}
			return false;
		};
		// far below the work: a pearl beats pillaring all the way up
		if (startPearl(mc, p, spot, false)) return;
		WorkNavigator.Start s = nav.start(mc, frontier, r, p.getEyeHeight(Pose.CROUCHING), spot, scaffold.get() && !cleaning, plan::containsKey);
		if (s == WorkNavigator.Start.NO_ROUTE && startPearl(mc, p, spot, true)) return; // no way up on foot at all
		event("nav " + s + (s == WorkNavigator.Start.OK ? (nav.partial() ? " partial" : "") : "") + " to " + frontier.size() + " targets from " + feetText());
		switch (s) {
			case OK -> {
				notReady = 0;
				navTicks = 0;
				state = State.NAV;
			}
			case NOT_READY -> {
				timer = 5;
				if (++notReady > 20) { // cannot even start a search from here: nothing more to be done for these
					notReady = 0;
					for (BlockPos t : frontier) stuck.add(t.asLong());
				}
			}
			case NO_ROUTE -> {
				for (BlockPos t : frontier) stuck.add(t.asLong()); // the next scan looks at the ones after these
				timer = 1;
			}
		}
	}

	/**
	 * Throws a pearl up to a spot to work from when all the work in front of it is at least the set height above its feet
	 * ({@code noRoute}: walking found no way at all, so 2 blocks up is enough).
	 */
	private boolean startPearl(Minecraft mc, LocalPlayer p, WorkNavigator.Spot spot, boolean noRoute) {
		if (!pearlUp.get() || cleaning || frontier.isEmpty() || pearlFails >= 4 || !p.onGround()) return false;
		if (!PearlUp.havePearl(p, creativeSupply.get()) || !PearlUp.healthy(p)) return false;
		int feetY = WorkNavigator.feet(p).y();
		int lowest = Integer.MAX_VALUE;
		for (BlockPos t : frontier) lowest = Math.min(lowest, t.getY());
		int rise = noRoute ? 2 : pearlRise.asInt();
		if (lowest - feetY < rise) return false;
		PearlUp.Landing l = PearlUp.find(mc, frontier, reach.get(), spot, k -> pending.contains(k), 2, badLandings);
		if (l == null) return false;
		nav.cancel();
		Keys.release(mc.options.keyShift);
		pearl.start(l, creativeSupply.get());
		state = State.PEARL;
		event("pearl to " + l.feet().toShortString() + " pitch " + String.format(Locale.ROOT, "%.1f", l.shot().pitch()) + (noRoute ? " (no route)" : ""));
		return true;
	}

	private void pearling(Minecraft mc, LocalPlayer p) {
		boolean thrown = pearl.landing() != null && pearl.status() == PearlUp.Status.WORKING;
		switch (pearl.tick(mc)) {
			case LANDED -> {
				pearlsThrown++;
				pearlFails = 0;
				stuck.clear(); // everything up here is new ground
				event("pearl landed at " + p.blockPosition().toShortString());
				pearl.cancel();
				rescan(limiter.between(3, 6));
			}
			case FAILED -> {
				if (thrown && pearl.failReason().startsWith("the pearl")) pearlsThrown++;
				pearlFails++;
				badLandings.add(pearl.landing().feet().asLong());
				event("pearl failed: " + pearl.failReason());
				pearl.cancel();
				rescan(limiter.between(5, 10));
			}
			default -> { }
		}
	}

	/** In creative mode with nothing to scaffold with, takes a stack of cobblestone the way the build blocks are taken. */
	private void spareBlock(Minecraft mc, LocalPlayer p) {
		var navModule = breezeclient.breezeclient.client.nav.NavModule.instance;
		if (navModule == null || navModule.hasPlaceable() || !creativeSupply.get() || !p.hasInfiniteMaterials()) return;
		for (int i = 8; i >= 0; i--) {
			if (!p.getInventory().getItem(i).isEmpty()) continue;
			ItemStack stack = new ItemStack(Items.COBBLESTONE, 64);
			p.getInventory().setItem(i, stack);
			mc.gameMode.handleCreativeModeItemAdd(stack, 36 + i);
			return;
		}
	}

	private void collectScaffold(Minecraft mc) {
		for (BlockPos b : nav.scaffold()) {
			var navModule = breezeclient.breezeclient.client.nav.NavModule.instance;
			String id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(b).getBlock()).toString();
			// the cell was empty when the route was planned; it is ours only if it now holds one of the blocks the pathing places
			if (!plan.containsKey(b.asLong()) && navModule != null && navModule.placeable.contains(id)) clear.add(b.asLong());
		}
		nav.scaffold().clear();
	}

	private void walking(Minecraft mc, LocalPlayer p) {
		switch (nav.state()) {
			case WALKING -> {
				// the walker only fails when the player stops moving; jumping or digging in place forever keeps it busy
				if (++navTicks > 20 * 60) {
					walkFailed(mc);
					return;
				}
				// like a printer: whatever comes within reach on the way gets done on the way
				if (navTicks % 8 == 0 && p.onGround()) {
					Job j = scan(mc, p);
					if (j != null) {
						nav.cancel();
						collectScaffold(mc);
						begin(j);
					}
				}
			}
			case ARRIVED -> {
				collectScaffold(mc);
				RotationController.clear();
				// arriving and still finding nothing to do means the spot was not as good as it looked: do not come back forever
				if (++idleArrivals >= (nav.partial() ? 6 : 3)) { // a route that only led toward the work gets a few more goes
					for (BlockPos t : frontier) stuck.add(t.asLong());
					idleArrivals = 0;
				}
				nav.cancel();
				rescan(limiter.between(2, 5));
			}
			default -> walkFailed(mc);
		}
	}

	private void walkFailed(Minecraft mc) {
		collectScaffold(mc);
		if (++navFails >= 3) {
			for (BlockPos t : frontier) stuck.add(t.asLong());
			navFails = 0;
		}
		nav.cancel();
		rescan(5);
	}

	/**
	 * Heads for the nearest wanted block whose chunk the client does not have, so the chunk loads and the block can be
	 * worked on. The search cannot reach into unloaded chunks, so the route only leads toward it; that is enough.
	 */
	private boolean walkToUnloaded(Minecraft mc, LocalPlayer p) {
		if (farTries >= 12) return false;
		BlockPos far = null;
		double best = Double.MAX_VALUE;
		for (long key : pending) {
			int x = BlockPos.getX(key), z = BlockPos.getZ(key);
			if (mc.level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
			double d = p.distanceToSqr(x + 0.5, BlockPos.getY(key), z + 0.5);
			if (d < best) {
				best = d;
				far = BlockPos.of(key);
			}
		}
		if (far == null) return false;
		farTries++;
		frontier = List.of();
		WorkNavigator.Start s = nav.start(mc, List.of(far), reach.get(), p.getEyeHeight(Pose.CROUCHING), (feet, eye) -> true, false, key -> true);
		if (s == WorkNavigator.Start.NOT_READY) {
			timer = 5;
			return true;
		}
		if (s != WorkNavigator.Start.OK || nav.state() != WorkNavigator.State.WALKING) return false;
		navTicks = 0;
		state = State.NAV;
		return true;
	}

	/** Nothing left that it can get to: wait for items, loosen up, take the scaffolding down, or finish. */
	private void conclude(Minecraft mc, LocalPlayer p, Item lacking) {
		if (!stuck.isEmpty()) {
			boolean loosened = false;
			if (!strict.get()) {
				for (long key : stuck) {
					BlockState want = plan.get(key);
					if (want != null && Placer.hasOrientation(want) && !Placer.halfMatters(want) && relaxed.add(key)) loosened = true;
				}
			}
			if (loosened) { // one more round, this time accepting a placement that may face the wrong way
				stuck.clear();
				return;
			}
			for (long key : new ArrayList<>(stuck)) {
				if (plan.containsKey(key)) giveUp(key, "nowhere to stand to place it");
				else clear.remove(key);
			}
			stuck.clear();
			return;
		}
		if (lacking != null) {
			missing = lacking;
			stage = 0;
			state = State.NEED_ITEMS;
			return;
		}
		if (!pending.isEmpty() && walkToUnloaded(mc, p)) return;
		if (!pending.isEmpty()) { // still wanted, but nothing can be done about them any more
			for (long key : new ArrayList<>(pending)) {
				BlockPos pos = BlockPos.of(key);
				giveUp(key, mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4) ? "nothing to place it against" : "chunk not loaded");
			}
			return;
		}
		// One more look at the whole build before calling it done: a placement the server turned down comes back as air a
		// moment after it looked fine here, and a 3x3 pickaxe may have taken built blocks with it.
		if (rechecks < 3) {
			rechecks++;
			int back = 0;
			for (var e : plan.entrySet()) {
				long key = e.getKey();
				if (givenUp.containsKey(key)) continue;
				BlockPos pos = BlockPos.of(key);
				if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
				if (mc.level.getBlockState(pos).getBlock() != e.getValue().getBlock() && pending.add(key)) {
					done--;
					back++;
				}
			}
			if (back > 0) return;
		}
		if (!clear.isEmpty() && !cleaning) {
			cleaning = true;
			return;
		}
		state = State.DONE;
	}

	// digging ----------------------------------------------------------------------------------------------------

	/** Digs the block in the way: best tool, looking at it first, one block at a time. */
	private void dig(Minecraft mc, LocalPlayer p) {
		if (job == null) {
			rescan(0);
			return;
		}
		BlockPos pos = job.pos();
		BlockState cur = mc.level.getBlockState(pos);
		if (cur.isAir() || cur.canBeReplaced() || cur.getBlock() == job.want().getBlock()) { // gone, maybe with a 3x3 pickaxe
			mc.gameMode.stopDestroyBlock();
			stuck.clear();
			anyLayer = false;
			rescan(limiter.between(1, 3));
			return;
		}
		if (++digTicks > 400) {
			mc.gameMode.stopDestroyBlock();
			if (plan.containsKey(pos.asLong())) giveUp(pos.asLong(), "digging timed out");
			else clear.remove(pos.asLong());
			rescan(2);
			return;
		}
		int bestSlot = p.getInventory().getSelectedSlot();
		float best = p.getInventory().getItem(bestSlot).isEmpty() ? 1f : p.getInventory().getItem(bestSlot).getDestroySpeed(cur);
		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			float v = s.isEmpty() ? 1f : s.getDestroySpeed(cur);
			if (v > best + 0.01f) {
				best = v;
				bestSlot = i;
			}
		}
		if (bestSlot != p.getInventory().getSelectedSlot()) p.getInventory().setSelectedSlot(bestSlot);
		// it has to be the block under the crosshair before the first hit lands
		if (!(mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(pos))) {
			if (++lookTicks > 50) { // lost sight of it (we were moved, something fell in front): look again from scratch
				mc.gameMode.stopDestroyBlock();
				failed(pos.asLong(), "could not look at the block in the way");
				rescan(2);
				return;
			}
			if (digPoint == null || (!RotationController.busy() && lookTicks % 10 == 0)) digPoint = Placer.visiblePoint(mc, p, pos, p.getEyePosition(), reach.get() + 0.5);
			if (digPoint != null) RotationController.aimAtPoint(digPoint, 14f);
			return;
		}
		lookTicks = 0;
		if (mc.gameMode.continueDestroyBlock(pos, bh.getDirection())) p.swing(InteractionHand.MAIN_HAND);
	}

	// placing ----------------------------------------------------------------------------------------------------

	private void aim(Minecraft mc, LocalPlayer p) {
		if (job == null) {
			rescan(0);
			return;
		}
		// it places while sneaking and standing still: plan from the eye position it has then
		if ((!p.isCrouching() || p.getDeltaMovement().horizontalDistanceSqr() > 1e-4 || !p.onGround()) && settle++ < 12) return;
		long key = job.pos().asLong();
		int slot = prepareItem(mc, p, job.want());
		if (slot == -3) return; // the stack is on its way to the hotbar
		if (slot < 0) {
			rescan(1);
			return;
		}
		aim = Placer.find(mc, p, job.pos(), job.want(), job.want().getBlock().asItem(), p.getEyePosition(), reach.get(), !relaxed.contains(key));
		if (aim == null) { // it could be done a moment ago: we moved, or something changed
			failed(key, "could not line up the placement");
			rescan(2);
			return;
		}
		RotationController.aimAtPoint(aim.hit().getLocation(), 12f);
		lookTicks = 0;
		state = State.PLACE;
	}

	/** @return hotbar slot holding the item (selected), -2 when missing, -3 while a swap is in progress. */
	private int prepareItem(Minecraft mc, LocalPlayer p, BlockState want) {
		Item item = want.getBlock().asItem();
		for (int i = 0; i < 9; i++) {
			if (p.getInventory().getItem(i).is(item)) {
				p.getInventory().setSelectedSlot(i);
				return i;
			}
		}
		for (int i = 9; i < 36; i++) {
			if (p.getInventory().getItem(i).is(item)) {
				int hot = p.getInventory().getSelectedSlot();
				mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, i, hot, ClickType.SWAP, p);
				timer = limiter.nextDelayTicks(4, 1, 2);
				return -3;
			}
		}
		if (creativeSupply.get() && p.hasInfiniteMaterials()) {
			int hot = p.getInventory().getSelectedSlot();
			for (int i = 0; i < 9; i++) {
				if (p.getInventory().getItem(i).isEmpty()) {
					hot = i;
					break;
				}
			}
			p.getInventory().setSelectedSlot(hot);
			ItemStack stack = new ItemStack(item, item.getDefaultMaxStackSize());
			p.getInventory().setItem(hot, stack); // shown at once; the server learns of it from the packet below
			mc.gameMode.handleCreativeModeItemAdd(stack, 36 + hot);
			timer = limiter.nextDelayTicks(3, 1, 2);
			return -3;
		}
		return -2;
	}

	private void place(Minecraft mc, LocalPlayer p) {
		if (job == null || aim == null) {
			rescan(0);
			return;
		}
		long key = job.pos().asLong();
		BlockHitResult planned = aim.hit();
		// it only places while the crosshair really is on the face it planned: no clicking at a spot you are not looking at
		boolean onFace = mc.hitResult instanceof BlockHitResult c && c.getType() == HitResult.Type.BLOCK && c.getBlockPos().equals(planned.getBlockPos())
			&& c.getDirection() == planned.getDirection();
		boolean ready = false;
		if (onFace) {
			BlockHitResult cross = (BlockHitResult) mc.hitResult;
			if (!aim.exact() || !Placer.hasOrientation(job.want()) || Placer.halfOfDouble(mc.level.getBlockState(job.pos()), job.want())) {
				ready = true;
			} else { // the same question the plan asked, now with the real crosshair and the real camera
				BlockState got = Placer.simulate(p, job.want(), job.want().getBlock().asItem(), cross, RotationController.current());
				ready = Placer.oriented(got, job.want());
			}
		}
		if (!ready) {
			if (++lookTicks > 50) { // could not get the crosshair there: plan again from scratch
				failed(key, "could not look at the place");
				if (pending.contains(key)) {
					lookTicks = 0;
					state = State.AIM;
				} else {
					rescan(2);
				}
				return;
			}
			if (!RotationController.busy()) {
				if (lookTicks % 8 == 0) { // the camera is there and the crosshair is not: look at the block afresh from where we are now
					Placer.Plan again = Placer.find(mc, p, job.pos(), job.want(), job.want().getBlock().asItem(), p.getEyePosition(), reach.get(), !relaxed.contains(key));
					if (again != null) aim = again;
				}
				RotationController.aimAtPoint(aim.hit().getLocation(), 12f);
			}
			return;
		}
		BlockHitResult cross = (BlockHitResult) mc.hitResult;
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(cross.getLocation(), cross.getDirection(), cross.getBlockPos(), false));
		p.swing(InteractionHand.MAIN_HAND);
		state = State.VERIFY;
		timer = limiter.nextDelayTicks(delay.get(), delay.get() * 0.3, 1);
	}

	private void verify(Minecraft mc, LocalPlayer p) {
		if (job == null) {
			rescan(0);
			return;
		}
		long key = job.pos().asLong();
		BlockState now = mc.level.getBlockState(job.pos());
		if (now.getBlock() == job.want().getBlock()) {
			tries.remove(key);
			stuck.clear(); // a new block is a new thing to click on and to stand on
			anyLayer = false; // progress: back to working bottom-up (not on finding a block already there, which happens mid-scan)
			event("placed " + job.pos().toShortString() + " from " + feetText());
			if (!Placer.halfOfDouble(now, job.want())) { // a double slab stays on the list for its second half
				if (Placer.hasOrientation(job.want()) && !Placer.oriented(now, job.want())) wrongWay++;
				markDone(key);
			}
			rescan(limiter.nextDelayTicks(delay.get(), delay.get() * 0.3, 1));
			return;
		}
		failed(key, "placement was rejected");
		rescan(limiter.between(2, 6));
	}

	private void needItems(Minecraft mc, LocalPlayer p) {
		Item item = missing;
		if (item == null || have(p, item)) {
			givenUpItems.clear();
			rescan(2);
			return;
		}
		if (startBuying(mc, p, item)) return;
		if (stage++ % 100 == 0) EspModule.say("SchematicBuilder: out of " + new ItemStack(item).getHoverName().getString() + ". Add some to your inventory (waiting).");
		// anything else it does have the blocks for may have come within reach of the route in the meantime
		if (stage % 40 == 0) {
			Item keep = missing;
			Job j = scan(mc, p);
			if (j != null) begin(j);
			else missing = keep;
		}
	}

	// auction house ----------------------------------------------------------------------------------------------

	/** The most to pay for one of this block: its own entry in the per-type list, else the general limit. NaN when unreadable. */
	private double limitFor(Item item) {
		var id = BuiltInRegistries.ITEM.getKey(item);
		for (String entry : limits.get()) {
			int eq = entry.indexOf('=');
			if (eq <= 0) continue;
			String key = entry.substring(0, eq).trim();
			if (key.equals(id.toString()) || key.equals(id.getPath()) || key.replace(' ', '_').equals(id.getPath())) return PriceParser.parse(entry.substring(eq + 1));
		}
		return PriceParser.parse(maxEach.get());
	}

	/** How many of this block the rest of the build still needs. */
	private int stillNeeded(Item item) {
		int n = 0;
		for (long key : pending) {
			BlockState want = plan.get(key);
			if (want != null && want.getBlock().asItem() == item) n++;
		}
		return n;
	}

	private boolean startBuying(Minecraft mc, LocalPlayer p, Item item) {
		if (!autoBuy.get() || buyBroken || p.hasInfiniteMaterials() || mc.screen != null) return false;
		if (item == buyRetryItem && System.currentTimeMillis() < buyRetryAt) return false;
		double each = limitFor(item), total = PriceParser.parse(budget.get());
		if (Double.isNaN(each) || Double.isNaN(total) || each <= 0) {
			buyBroken = true;
			EspModule.say("SchematicBuilder: cannot read the price limits (Max price per block / Max total spend). Not buying.");
			return false;
		}
		if (total - spentTotal <= 0) {
			buyBroken = true;
			EspModule.say("SchematicBuilder: the spending limit of " + PriceParser.format(total) + " is used up. Not buying any more.");
			return false;
		}
		int want = Math.max(1, Math.min(stillNeeded(item), 27 * item.getDefaultMaxStackSize())); // never more than fits
		nav.cancel();
		RotationController.clear();
		buyer.start(new AuctionBuyer.Config(ahCommand.get(), signSearch.get(), searchLabel.get(), confirmLabel.get(), delay.get()), item, want, each, total - spentTotal);
		EspModule.say("SchematicBuilder: buying " + new ItemStack(item).getHoverName().getString() + " (up to " + PriceParser.format(each) + " each)");
		state = State.BUY;
		return true;
	}

	private void buy(Minecraft mc, LocalPlayer p) {
		AuctionBuyer.Result r = buyer.tick(mc, p);
		if (r == AuctionBuyer.Result.WORKING) return;
		spentTotal += buyer.spent();
		switch (r) {
			case BOUGHT -> EspModule.say("SchematicBuilder: " + buyer.note() + " (" + PriceParser.format(spentTotal) + " spent on this build)");
			case NOTHING -> {
				buyRetryItem = missing;
				buyRetryAt = System.currentTimeMillis() + 120_000; // look again in two minutes
				EspModule.say("SchematicBuilder: nothing to buy, " + buyer.note() + ". Looking again in 2 minutes.");
			}
			default -> {
				buyBroken = true; // something about the auction window is not what we expect: stop spending
				EspModule.say("SchematicBuilder: auction buying stopped, " + buyer.note() + ".");
			}
		}
		stage = 1; // the "out of" reminder waits its turn
		timer = limiter.between(6, 12);
		state = State.NEED_ITEMS;
	}

	// render -----------------------------------------------------------------------------------------------------

	@Override
	protected void onRender(RenderFrame f) {
		Job j = job;
		if (j == null) return;
		BlockPos t = j.pos();
		f.drawBox(t.getX(), t.getY(), t.getZ(), t.getX() + 1, t.getY() + 1, t.getZ() + 1, j.kind() == Kind.DIG ? 0xFFFF7755 : 0xFF66CCFF);
	}
}
