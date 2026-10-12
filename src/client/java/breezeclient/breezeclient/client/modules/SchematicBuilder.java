package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.input.Keys;
import breezeclient.breezeclient.client.nav.PathWalker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.schem.SchematicData;
import breezeclient.breezeclient.core.schem.SchematicReader;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import breezeclient.breezeclient.core.text.PriceParser;
import breezeclient.breezeclient.client.nav.LevelWorld;
import net.minecraft.world.phys.AABB;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Builds a loaded schematic layer by layer in straight sweeps. Walks into reach, eases the camera onto a real block
 * face, and places at randomized intervals, like a player would. Orientation is handled for axis blocks, slabs and
 * half-stairs; other facing-dependent blocks are placed but may need manual rotation (counted in status).
 */
public class SchematicBuilder extends Module {
	private enum State { IDLE, PICK, WALK, DIG, AIM, PLACE, VERIFY, NEED_ITEMS, BUY, DONE }

	private record Target(BlockPos pos, BlockState want) {}

	private final StringSetting file = add(new StringSetting("File", ""));
	private final NumberSetting reach = add(new NumberSetting("Reach", 4.0, 2.5, 4.5));
	private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 4, 1, 20));
	private final BoolSetting autoDig = add(new BoolSetting("Auto dig", true), "General",
		"Break the blocks that are in the way of the build with the best pickaxe in your hotbar, looking at each one first. Works with 3x3 pickaxes: blocks they clear are simply skipped. Containers, spawners and unbreakable blocks are never dug.");
	private final BoolSetting creativeSupply = add(new BoolSetting("Creative: take missing blocks", true), "General",
		"In creative mode, a block you do not have is taken from the creative inventory into your hotbar, the way pick block does.");

	private final BoolSetting reposition = add(new BoolSetting("Move to a better spot", true), "General",
		"When a block cannot be placed from where it stands (no face in sight, or it is standing in the way), it walks to a spot that can see one before trying again.");
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
	private final AuctionBuyer buyer = new AuctionBuyer();
	private double spentTotal;
	private boolean buyBroken;
	private long buyRetryAt;
	private Item buyRetryItem;
	private int moves;
	private int settle;
	/** Why blocks were skipped in this run (for .build status and the self-test). */
	private final List<String> notes = new ArrayList<>();
	private String debug = "";

	public List<String> notes() { return List.copyOf(notes); }
	private final ActionLimiter limiter = new ActionLimiter(rng);
	private final PathWalker walker = new PathWalker();

	private List<Target> all = List.of();
	private int digTicks;
	private int lookTicks;
	private List<Target> queue = List.of();
	private final List<Target> deferred = new ArrayList<>();
	private int idx;
	private int pass;
	private State state = State.IDLE;
	private int timer;
	private int stage;
	private int retries;
	private BlockPos origin;
	private SchematicData loaded;
	private int placed;
	private int skipped;
	private int orientationUnsure;
	private BlockHitResult plannedHit;
	private int plannedSlot = -1;

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
		List<Path> existing = new ArrayList<>();
		for (Path p : out) if (Files.isDirectory(p)) existing.add(p);
		return existing;
	}

	private static boolean isSchematic(String n) {
		String l = n.toLowerCase(Locale.ROOT);
		return l.endsWith(".litematic") || l.endsWith(".schem") || l.endsWith(".nbt");
	}

	/** Every schematic file in the search folders, first folder wins when a name appears twice. */
	public static List<Path> found() {
		java.util.Map<String, Path> byName = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Path d : searchDirs()) {
			try (Stream<Path> s = Files.list(d)) {
				s.filter(Files::isRegularFile).filter(p -> isSchematic(p.getFileName().toString()))
					.forEach(p -> byName.putIfAbsent(p.getFileName().toString(), p));
			} catch (IOException ignored) {
				// an unreadable folder is skipped
			}
		}
		return new ArrayList<>(byName.values());
	}

	public static List<String> files() {
		List<String> out = new ArrayList<>();
		for (Path p : found()) out.add(p.getFileName().toString());
		return out;
	}

	/** Finds a schematic by file name (extension optional, case ignored) in the search folders. */
	public static Path locate(String name) {
		String want = name.trim();
		if (want.isEmpty()) return null;
		for (Path p : found()) {
			String n = p.getFileName().toString();
			if (n.equalsIgnoreCase(want)) return p;
		}
		for (Path p : found()) {
			String n = p.getFileName().toString();
			int dot = n.lastIndexOf('.');
			if (dot > 0 && n.substring(0, dot).equalsIgnoreCase(want)) return p;
		}
		return null;
	}

	/** The most recently changed schematic found, or null. */
	public static Path newest() {
		Path best = null;
		long bestTime = Long.MIN_VALUE;
		for (Path p : found()) {
			try {
				long t = Files.getLastModifiedTime(p).toMillis();
				if (t > bestTime) {
					bestTime = t;
					best = p;
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
		Path p = locate(name);
		if (p == null) return "no schematic named '" + name + "'. Put .litematic, .schem or .nbt files next to the mod jar (searched: " + searched() + ")";
		try {
			loaded = SchematicReader.read(Files.readAllBytes(p), p.getFileName().toString());
			file.set(p.getFileName().toString());
			return null;
		} catch (IOException e) {
			loaded = null;
			return p.getFileName() + ": " + e.getMessage();
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
			Path n = newest();
			if (n == null) return "no schematics found. Put .litematic, .schem or .nbt files next to the mod jar (searched: " + searched() + ")";
			pick = n.getFileName().toString();
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

	/** One line per folder searched with how many schematics each holds, for .build files and the GUI. */
	public static List<String> describeFolders() {
		List<String> out = new ArrayList<>();
		List<Path> all = found();
		for (Path d : searchDirs()) {
			int n = 0;
			for (Path p : all) if (d.equals(p.getParent())) n++;
			out.add(d + "  (" + n + " schematic" + (n == 1 ? "" : "s") + ")");
		}
		return out;
	}

	public void setOrigin(BlockPos p) { origin = p; }

	public BlockPos origin() { return origin; }

	public boolean loaded() { return loaded != null; }

	public String status() {
		if (loaded == null) return "No schematic loaded. Put files next to the mod jar and use .build auto [file] (searched: " + searched() + ").";
		return String.format("%s: %d/%d placed, %d skipped, %d deferred, state %s%s", file.get(), placed, loaded.blocks().size(), skipped,
			deferred.size(), state.name().toLowerCase(), orientationUnsure > 0 ? ", " + orientationUnsure + " orientation-dependent blocks may need rotating" : "");
	}

	@Override
	public String info() { return loaded == null ? "" : placed + "/" + loaded.blocks().size(); }

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
		int unsure = 0;
		List<Target> q = new ArrayList<>();
		for (SchematicData.Block b : loaded.linearOrder()) {
			BlockState st = parse(b.state());
			if (st == null || st.isAir()) continue;
			if (st.hasProperty(BlockStateProperties.FACING) || st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) unsure++;
			q.add(new Target(origin.offset(b.x(), b.y(), b.z()), st));
		}
		// Within a layer: blocks that must be clicked on one particular face (logs, pillars) go first, while that face is still
		// open; top slabs and upside-down stairs go last, because they hang off the side of a neighbour.
		q.sort(java.util.Comparator.comparingInt((Target t) -> t.pos().getY()).thenComparingInt(t -> placeRank(t.want())));
		orientationUnsure = unsure;
		spentTotal = 0;
		buyBroken = false;
		buyRetryItem = null;
		moves = 0;
		all = List.copyOf(q);
		queue = q;
		deferred.clear();
		idx = 0;
		pass = 0;
		placed = skipped = 0;
		state = State.PICK;
		timer = 10;
	}

	@Override
	protected void onDisable() {
		if (state == State.BUY) buyer.abort(Minecraft.getInstance());
		if (state == State.DIG && Minecraft.getInstance().gameMode != null) Minecraft.getInstance().gameMode.stopDestroyBlock(); // never leave a half-dug block cracking
		Keys.release(Minecraft.getInstance().options.keyShift);
		walker.cancel();
		RotationController.clear();
		state = State.IDLE;
	}

	private static int placeRank(BlockState s) {
		if (s.hasProperty(BlockStateProperties.AXIS)) return 0;
		boolean top = (s.hasProperty(BlockStateProperties.SLAB_TYPE) && s.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP)
			|| (s.hasProperty(BlockStateProperties.HALF) && s.getValue(BlockStateProperties.HALF) == Half.TOP);
		return top ? 2 : 1;
	}

	private static BlockState parse(String s) {
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
		} catch (CommandSyntaxException e) {
			return null;
		}
	}

	// main loop --------------------------------------------------------------------------------------------------

	private Target current() { return idx < queue.size() ? queue.get(idx) : null; }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.gameMode == null) return;
		walker.tick();
		if (state == State.BUY) {
			Keys.release(mc.options.keyShift);
			buy(mc, p);
			return;
		}
		if (p.containerMenu != p.inventoryMenu) p.closeContainer(); // a click opened a chest/crafting table: never stay inside it
		// sneak so clicks do not use containers or doors
		if (state == State.AIM || state == State.PLACE || state == State.VERIFY) Keys.press(mc.options.keyShift);
		else Keys.release(mc.options.keyShift);
		if (timer > 0) {
			timer--;
			return;
		}
		switch (state) {
			case PICK -> pick(mc, p);
			case WALK -> walk(mc, p);
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

	private void advance() {
		idx++;
		retries = 0;
		moves = 0;
		state = State.PICK;
	}

	private void skip(String why) {
		skipped++;
		Target t = current();
		if (t != null && why != null) EspModule.say("SchematicBuilder: skipping " + t.pos().toShortString() + " (" + why + ")");
		if (t != null && notes.size() < 200) notes.add(t.pos().toShortString() + " " + why + debug);
		advance();
	}

	private void pick(Minecraft mc, LocalPlayer p) {
		while (idx < queue.size()) {
			Target t = queue.get(idx);
			if (!mc.level.getChunkSource().hasChunk(t.pos().getX() >> 4, t.pos().getZ() >> 4)) {
				deferred.add(t); // revisit once the chunk loads instead of dropping it
				idx++;
				continue;
			}
			if (mc.level.getBlockState(t.pos()).getBlock() == t.want().getBlock()) {
				placed++;
				idx++;
				continue;
			}
			break;
		}
		if (idx >= queue.size()) {
			// one more look at the whole build: a 3x3 pickaxe (or anything else) may have taken built blocks with it
			List<Target> missing = new ArrayList<>();
			for (Target a : all) {
				if (!mc.level.getChunkSource().hasChunk(a.pos().getX() >> 4, a.pos().getZ() >> 4)) continue;
				if (mc.level.getBlockState(a.pos()).getBlock() != a.want().getBlock()) missing.add(a);
			}
			if (!missing.isEmpty() && pass < 4) {
				pass++;
				queue = missing;
				deferred.clear();
				idx = 0;
				return;
			}
			placed = all.size() - missing.size();
			state = State.DONE;
			return;
		}
		Target t = queue.get(idx);
		Vec3 eye = p.getEyePosition();
		if (eye.distanceTo(Vec3.atCenterOf(t.pos())) > reach.get() - 0.2) {
			if (!walker.start(PathWalker.posOf(t.pos()), Math.max(2.0, reach.get() - 0.8))) {
				skip("no walkable route");
				return;
			}
			state = State.WALK;
			return;
		}
		BlockState inWay = mc.level.getBlockState(t.pos());
		if (!inWay.isAir() && !inWay.canBeReplaced() && inWay.getBlock() != t.want().getBlock()) {
			if (!autoDig.get()) {
				skip("something else is there (turn on Auto dig)");
				return;
			}
			if (!diggable(mc, t.pos())) {
				skip("cannot dig " + inWay.getBlock().getName().getString());
				return;
			}
			state = State.DIG;
			digTicks = 0;
			return;
		}
		// standing in the block's own space: nothing can be placed there until we step out
		if (p.getBoundingBox().intersects(new AABB(t.pos())) && moveToVantage(mc, p, t)) return;
		state = State.AIM;
		stage = 0;
		plannedHit = null;
	}

	private static boolean standable(LevelWorld w, int x, int y, int z) {
		return w.passable(x, y, z) && w.passable(x, y + 1, z) && w.ground(x, y - 1, z);
	}

	/**
	 * Walks to the nearest spot from which a face next to the target is in reach and in sight, and which does not put us
	 * in the target's own space. Tried a few times per block.
	 *
	 * @return true when a walk was started
	 */
	private boolean moveToVantage(Minecraft mc, LocalPlayer p, Target t) {
		if (!reposition.get() || moves >= 3) return false;
		LevelWorld w = new LevelWorld(mc.level);
		BlockPos tp = t.pos(), here = p.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				for (int dy = -3; dy <= 2; dy++) {
					int x = tp.getX() + dx, y = tp.getY() + dy, z = tp.getZ() + dz;
					if (dx == 0 && dz == 0 && (dy == 0 || dy == -1)) continue; // feet or head would be inside the target
					if (x == here.getX() && y == here.getY() && z == here.getZ()) continue; // where we already are did not work
					double d = p.distanceToSqr(x + 0.5, y, z + 0.5);
					if (d >= bestD || !standable(w, x, y, z)) continue;
					if (findFace(mc, p, t, new Vec3(x + 0.5, y + p.getEyeHeight(net.minecraft.world.entity.Pose.CROUCHING), z + 0.5)) == null) continue;
					bestD = d;
					best = new BlockPos(x, y, z);
				}
			}
		}
		if (best == null || !walker.start(PathWalker.posOf(best), 0.5)) return false;
		moves++;
		plannedHit = null;
		state = State.WALK;
		return true;
	}

	private static boolean diggable(Minecraft mc, BlockPos pos) {
		BlockState s = mc.level.getBlockState(pos);
		if (s.getDestroySpeed(mc.level, pos) < 0 || mc.level.getBlockEntity(pos) != null) return false; // unbreakable, containers, spawners
		return !UtilityModules.NEVER_MINE.contains(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
	}

	/** Digs the block in the way: best pickaxe, looking at it first, one block at a time. */
	private void dig(Minecraft mc, LocalPlayer p) {
		Target t = current();
		if (t == null) {
			state = State.PICK;
			return;
		}
		BlockPos pos = t.pos();
		BlockState cur = mc.level.getBlockState(pos);
		if (cur.isAir() || cur.canBeReplaced() || cur.getBlock() == t.want().getBlock()) { // gone, maybe with a 3x3 pickaxe
			mc.gameMode.stopDestroyBlock();
			state = State.PICK;
			return;
		}
		if (++digTicks > 400) {
			mc.gameMode.stopDestroyBlock();
			skip("digging timed out");
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
			RotationController.aimAtPoint(Vec3.atCenterOf(pos), 14f);
			return;
		}
		if (mc.gameMode.continueDestroyBlock(pos, bh.getDirection())) p.swing(InteractionHand.MAIN_HAND);
	}

	private void walk(Minecraft mc, LocalPlayer p) {
		switch (walker.state()) {
			case WALKING -> { }
			case ARRIVED -> {
				RotationController.clear();
				state = State.PICK;
				timer = limiter.between(2, 6);
			}
			default -> {
				if (retries++ < 2 && current() != null) {
					state = State.PICK;
					return;
				}
				skip("could not get close enough");
			}
		}
	}

	private void aim(Minecraft mc, LocalPlayer p) {
		Target t = current();
		if (t == null) {
			state = State.DONE;
			return;
		}
		if (plannedHit == null) {
			if (!p.isCrouching() && settle++ < 6) return; // it places while sneaking: plan from the eye height it will have then
			settle = 0;
			plannedHit = findFace(mc, p, t, p.getEyePosition());
			if (plannedHit == null) {
				if (moveToVantage(mc, p, t)) return;
				deferred.add(t);
				advance();
				return;
			}
			plannedSlot = prepareItem(mc, p, t.want());
			if (plannedSlot == -4) {
				plannedHit = null;
				skip("block has no item form");
				return;
			}
			if (plannedSlot == -2) {
				state = State.NEED_ITEMS;
				return;
			}
			if (plannedSlot == -3) {
				plannedHit = null; // swap in progress; re-plan once the stack has moved
				return;
			}
			RotationController.aimAtPoint(plannedHit.getLocation(), 12f);
			stage = 0;
		}
		if (RotationController.busy() && stage++ < 60) return;
		state = State.PLACE;
		timer = limiter.nextDelayTicks(delay.get() * 0.5, 1, 0);
	}

	/** Finds a neighbouring support block and a point on its face that is in reach and in line of sight. */
	private BlockHitResult findFace(Minecraft mc, LocalPlayer p, Target t, Vec3 eye) {
		var want = t.want();
		Direction.Axis wantAxis = want.hasProperty(BlockStateProperties.AXIS) ? want.getValue(BlockStateProperties.AXIS) : null;
		double yFrac = 0.5;
		if (want.hasProperty(BlockStateProperties.SLAB_TYPE) && want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP) yFrac = 0.8;
		else if (want.hasProperty(BlockStateProperties.SLAB_TYPE) && want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM) yFrac = 0.2;
		if (want.hasProperty(BlockStateProperties.HALF)) yFrac = want.getValue(BlockStateProperties.HALF) == Half.TOP ? 0.8 : 0.2;

		BlockHitResult best = null;
		double bestD = Double.MAX_VALUE;
		for (Direction d : Direction.values()) {
			BlockPos support = t.pos().relative(d);
			BlockState ss = mc.level.getBlockState(support);
			if (ss.isAir() || !ss.getFluidState().isEmpty() || ss.canBeReplaced()) continue;
			Direction face = d.getOpposite(); // face of the support block that touches our target
			if (wantAxis != null && face.getAxis() != wantAxis) continue;
			double fy = face.getAxis() == Direction.Axis.Y ? 0.5 : yFrac;
			Vec3 hit = new Vec3(support.getX() + 0.5 + face.getStepX() * 0.5, support.getY() + (face.getAxis() == Direction.Axis.Y ? (face == Direction.UP ? 1.0 : 0.0) : fy),
				support.getZ() + 0.5 + face.getStepZ() * 0.5);
			double dist = eye.distanceTo(hit);
			if (dist > reach.get()) continue;
			var clip = mc.level.clip(new ClipContext(eye, hit.add(hit.subtract(eye).normalize().scale(0.05)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			// the ray has to land on that very face: a face on the far side of the block is hit through a nearer one, and place() would wait for it forever
			if (clip.getType() != HitResult.Type.BLOCK || !clip.getBlockPos().equals(support) || clip.getDirection() != face) continue;
			if (dist < bestD) {
				bestD = dist;
				best = new BlockHitResult(hit, face, support, false);
			}
		}
		return best;
	}

	/** @return hotbar slot holding the item (selected), -2 when missing, -3 while a swap is in progress. */
	private int prepareItem(Minecraft mc, LocalPlayer p, BlockState want) {
		Item item = want.getBlock().asItem();
		if (item == net.minecraft.world.item.Items.AIR) return -4; // no item form: cannot be placed by hand
		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item)) {
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
		Target t = current();
		if (t == null || plannedHit == null) {
			state = State.PICK;
			return;
		}
		// it only places while the crosshair really is on the face it planned: no clicking at a spot you are not looking at
		if (!(mc.hitResult instanceof BlockHitResult cross) || !cross.getBlockPos().equals(plannedHit.getBlockPos()) || cross.getDirection() != plannedHit.getDirection()) {
			if (++lookTicks > 50) { // could not get the crosshair there: try again from scratch
				lookTicks = 0;
				debug = " [planned " + plannedHit.getBlockPos().toShortString() + " " + plannedHit.getDirection() + " at " + plannedHit.getLocation() + ", crosshair "
					+ (mc.hitResult instanceof BlockHitResult c2 ? c2.getBlockPos().toShortString() + " " + c2.getDirection() + " " + c2.getType() : String.valueOf(mc.hitResult))
					+ ", eye " + p.getEyePosition() + ", rot " + p.getYRot() + "/" + p.getXRot() + ", busy " + RotationController.busy() + "]";
				plannedHit = null;
				if (++retries >= 3) skip("could not look at the place");
				else state = State.AIM;
				return;
			}
			RotationController.aimAtPoint(plannedHit.getLocation(), 12f);
			return;
		}
		lookTicks = 0;
		BlockHitResult use = new BlockHitResult(cross.getLocation(), cross.getDirection(), cross.getBlockPos(), false);
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, use);
		p.swing(InteractionHand.MAIN_HAND);
		state = State.VERIFY;
		timer = limiter.nextDelayTicks(delay.get(), delay.get() * 0.3, 1);
	}

	private void verify(Minecraft mc, LocalPlayer p) {
		Target t = current();
		if (t == null) {
			state = State.PICK;
			return;
		}
		BlockState now = mc.level.getBlockState(t.pos());
		if (now.getBlock() == t.want().getBlock()) {
			placed++;
			advance();
			timer = limiter.nextDelayTicks(delay.get(), delay.get() * 0.3, 1);
			return;
		}
		if (++retries >= 3) {
			skip("placement was rejected");
			return;
		}
		plannedHit = null;
		state = State.AIM;
		timer = limiter.between(2, 6);
	}

	private void needItems(Minecraft mc, LocalPlayer p) {
		Target t = current();
		if (t == null) {
			state = State.PICK;
			return;
		}
		Item item = t.want().getBlock().asItem();
		for (int i = 0; i < 36; i++) {
			if (p.getInventory().getItem(i).is(item)) {
				state = State.AIM;
				plannedHit = null;
				return;
			}
		}
		if (startBuying(mc, p, t, item)) return;
		if (stage++ % 100 == 0) EspModule.say("SchematicBuilder: out of " + t.want().getBlock().getName().getString() + ". Add some to your inventory (waiting).");
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
	private int stillNeeded(Minecraft mc, Item item) {
		int n = 0;
		for (int i = idx; i < queue.size(); i++) {
			Target q = queue.get(i);
			if (q.want().getBlock().asItem() == item && mc.level.getBlockState(q.pos()).getBlock() != q.want().getBlock()) n++;
		}
		for (Target q : deferred) if (q.want().getBlock().asItem() == item) n++;
		return n;
	}

	private boolean startBuying(Minecraft mc, LocalPlayer p, Target t, Item item) {
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
		int want = Math.max(1, Math.min(stillNeeded(mc, item), 27 * item.getDefaultMaxStackSize())); // never more than fits
		walker.cancel();
		RotationController.clear();
		buyer.start(new AuctionBuyer.Config(ahCommand.get(), signSearch.get(), searchLabel.get(), confirmLabel.get(), delay.get()), item, want, each, total - spentTotal);
		EspModule.say("SchematicBuilder: buying " + t.want().getBlock().getName().getString() + " (up to " + PriceParser.format(each) + " each)");
		state = State.BUY;
		return true;
	}

	private void buy(Minecraft mc, LocalPlayer p) {
		AuctionBuyer.Result r = buyer.tick(mc, p);
		if (r == AuctionBuyer.Result.WORKING) return;
		spentTotal += buyer.spent();
		Target t = current();
		Item item = t == null ? null : t.want().getBlock().asItem();
		switch (r) {
			case BOUGHT -> EspModule.say("SchematicBuilder: " + buyer.note() + " (" + PriceParser.format(spentTotal) + " spent on this build)");
			case NOTHING -> {
				buyRetryItem = item;
				buyRetryAt = System.currentTimeMillis() + 120_000; // look again in two minutes
				EspModule.say("SchematicBuilder: nothing to buy, " + buyer.note() + ". Looking again in 2 minutes.");
			}
			default -> {
				buyBroken = true; // something about the auction window is not what we expect: stop spending
				EspModule.say("SchematicBuilder: auction buying stopped, " + buyer.note() + ".");
			}
		}
		plannedHit = null;
		stage = 1; // the "out of" reminder waits its turn
		timer = limiter.between(6, 12);
		state = State.NEED_ITEMS;
	}
}
