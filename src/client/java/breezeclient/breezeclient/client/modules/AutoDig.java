package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.input.UseClicks;
import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.nav.LevelWorld;
import breezeclient.breezeclient.client.nav.WorkNavigator;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.dig.DigArea;
import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.core.humanize.Rotations;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Clears every block inside a box you mark with two corners, working down from the top. Like the schematic printers'
 * breaker, it keeps looking at every block of the box within reach, takes the one it can see that needs the smallest
 * turn of the head, and digs it; when nothing in reach can be seen, the pathfinder takes it to the nearest spot from
 * which something can. With a 3x3 pickaxe (the DonutSMP shard pickaxe) it counts on one hit taking a whole slab, and
 * swaps to an ordinary pickaxe where that slab would reach outside the box.
 */
public class AutoDig extends Module {
	public enum Wide { AUTO, ON, OFF }

	private enum State { SCAN, NAV, DIG }

	private final StringSetting corner1 = add(new StringSetting("Corner 1 (x y z)", ""), "Area", "One corner of the box to clear. Use Mark corners with right-click below, or .dig pos1 while looking at a block.");
	private final StringSetting corner2 = add(new StringSetting("Corner 2 (x y z)", ""), "Area", "The opposite corner. Use the button below or .dig pos2.");
	private final ColorSetting color = add(new ColorSetting("Outline color", 0xFFFFAA33), "Area", "Color of the box drawn around the area.");
	private final NumberSetting reach = add(new NumberSetting("Reach", 4.2, 3.0, 4.5), "Digging", "How far away it digs a block from.");
	private final NumberSetting layers = add(new NumberSetting("Layers at once", 2, 1, 4), "Digging",
		"How many layers below the top one it may dig at the same time. 2 clears head and foot height together, so it can walk through what it has dug.");
	private final EnumSetting<Wide> wide = add(new EnumSetting<>("3x3 pickaxe", Wide.AUTO), "3x3 pickaxe",
		"Auto: a hotbar pickaxe whose name contains the text below is treated as a 3x3 pickaxe. On: every pickaxe is. Off: none is.");
	private final StringSetting wideName = add(new StringSetting("3x3 pickaxe name contains", "shard"), "3x3 pickaxe",
		"Part of the item name of your 3x3 pickaxe (case ignored). The DonutSMP shard pickaxe matches 'shard'.", () -> wide.get() == Wide.AUTO);
	private final BoolSetting stayInside = add(new BoolSetting("Stay inside the area", true), "3x3 pickaxe",
		"Blocks whose 3x3 slab would reach outside the box are dug with an ordinary pickaxe from your hotbar instead. Without one they are left and counted.",
		() -> wide.get() != Wide.OFF);
	private final BoolSetting avoidLava = add(new BoolSetting("Leave blocks next to lava", true), "Safety", "Never opens a block that has lava on any side.");
	private final BoolSetting avoidDrops = add(new BoolSetting("Do not dig into a drop", true), "Safety", "Leaves the block under your feet when there is a fall of more than three blocks below it.");
	private final StringListSetting never = add(new StringListSetting("Never break", UtilityModules.NEVER_MINE).kind(StringListSetting.Kind.BLOCK), "Safety",
		"Blocks that are never dug. Anything with an inventory is protected too.");
	private final BoolSetting stopFull = add(new BoolSetting("Stop when inventory is full", false), "Safety", "Turns off once no inventory slot is free.");
	private final NumberSetting stopPlayer = add(new NumberSetting("Stop if player within (0=off)", 0, 0, 64), "Safety", "Turns off when another player comes this close.");
	private final NumberSetting minDurability = add(new NumberSetting("Keep tools above (uses)", 5, 0, 100), "Safety", "Do not use a tool with this few uses left (0 = use anything).");

	private final WorkNavigator nav = new WorkNavigator();
	/** Blocks left for the rest of the run, with the reason. */
	private final Map<Long, String> skipped = new HashMap<>();
	/** No way found to a spot that sees these: tried again once something else has been dug. */
	private final Set<Long> hidden = new HashSet<>();
	/** How often in a row the crosshair failed to reach a block. */
	private final Map<Long, Integer> aimFails = new HashMap<>();
	private DigArea area;
	private State state = State.SCAN;
	private BlockPos target;
	private Vec3 digPoint;
	private List<BlockPos> frontier = List.of();
	private int layer;
	private int digTicks;
	private int lookTicks;
	private int navTicks;
	private int navFails;
	private int idleArrivals;
	private int notReady;
	private int hiddenRounds;
	private int wait;
	private int broken;
	private boolean digging;
	private String lastResult = "";

	public AutoDig() {
		super("AutoDig", Category.WORLD);
		UseClicks.register(new UseClicks.Interceptor() {
			@Override public boolean onUse(Minecraft mc) { return onUseClick(mc); }

			@Override public void useKeyState(boolean down) { if (!down) useReleased = true; }
		});
	}

	@Override
	protected boolean persistsEnabled() { return false; }

	@Override
	public String info() { return area == null || !enabled() ? "" : broken + " dug"; }

	// area -------------------------------------------------------------------------------------------------------

	public void setCorner(int n, BlockPos p) { (n == 1 ? corner1 : corner2).set(p.getX() + " " + p.getY() + " " + p.getZ()); }

	public void clearCorners() {
		corner1.set("");
		corner2.set("");
	}

	/** Which corner the next right-click on a block sets: 1 or 2, or 0 when not marking. */
	private int marking;
	/** The world marking started in; leaving it (disconnect, dimension change) stops marking. Weak so an old world is not kept. */
	/** How long the box stays drawn after the second corner, so a misclick can be seen before starting. */
	private static final long SHOW_MARKED_MS = 15_000;
	private long showMarkedUntil;
	private WeakReference<ClientLevel> markLevel = new WeakReference<>(null);
	/** The use key has been let go since the last marked corner, so holding it does not mark both at once. */
	private boolean useReleased = true;

	public boolean marking() { return marking != 0; }

	/** Starts right-click marking (corner 1, then corner 2), or stops it when it is already on. */
	public void toggleMarking() {
		if (marking != 0) {
			marking = 0;
			Toasts.show("AutoDig", "Stopped marking corners.");
			return;
		}
		if (enabled()) setEnabled(false);
		markLevel = new WeakReference<>(Minecraft.getInstance().level);
		marking = 1;
		Toasts.show("AutoDig", "Right-click the first corner block.");
	}

	/**
	 * A right-click while marking: sets the next corner to the block under the crosshair. Returns true when the click was
	 * taken, so the game neither places nor opens anything and nothing is sent to the server.
	 */
	public boolean onUseClick(Minecraft mc) {
		if (marking == 0 || mc.player == null) return false;
		if (mc.level != markLevel.get()) {
			marking = 0; // armed in a world we have since left
			return false;
		}
		if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return false;
		if (!useReleased) return true; // the same press repeating while held
		useReleased = false;
		BlockPos at = bh.getBlockPos();
		setCorner(marking, at);
		if (marking == 1) {
			marking = 2;
			Toasts.show("AutoDig", "Corner 1 set to " + at.toShortString() + ". Right-click the opposite corner.");
		} else {
			marking = 0;
			showMarkedUntil = System.currentTimeMillis() + SHOW_MARKED_MS;
			Toasts.show("AutoDig", "Corner 2 set to " + at.toShortString() + ". " + status());
		}
		return true;
	}

	/** The marked box, or null while a corner is missing or unreadable. */
	public DigArea area() {
		int[] a = DigArea.parseCorner(corner1.get()), b = DigArea.parseCorner(corner2.get());
		if (a == null || b == null) return null;
		return DigArea.of(a[0], a[1], a[2], b[0], b[1], b[2]);
	}

	public String status() {
		DigArea a = area();
		if (a == null) return "No area yet. Use Mark corners with right-click (or .dig mark), then right-click two opposite corners.";
		String size = a.sizeX() + "x" + a.sizeY() + "x" + a.sizeZ();
		if (!enabled()) return "Area " + size + " (" + a.volume() + " blocks). .dig start to begin." + (lastResult.isEmpty() ? "" : " Last run: " + lastResult);
		return "Area " + size + ": " + broken + " dug, layer y=" + layer + (skipped.isEmpty() ? "" : ", " + leftAlone());
	}

	/** "4 left alone (3 out of reach, 1 next to lava)". */
	public String leftAlone() {
		Map<String, Integer> by = new TreeMap<>();
		for (String why : skipped.values()) by.merge(why, 1, Integer::sum);
		StringBuilder sb = new StringBuilder();
		for (var e : by.entrySet()) sb.append(sb.length() == 0 ? "" : ", ").append(e.getValue()).append(' ').append(e.getKey());
		return skipped.size() + " left alone (" + sb + ")";
	}

	// lifecycle --------------------------------------------------------------------------------------------------

	@Override
	protected void onEnable() {
		marking = 0; // corners must not change under a running dig
		Minecraft mc = Minecraft.getInstance();
		area = area();
		String problem = mc.player == null || mc.level == null ? "join a world first"
			: area == null ? "mark two corners first (.dig pos1 and .dig pos2, or the buttons on this page)"
			: area.tooBig() ? "the area is too big (at most " + DigArea.MAX_SIDE + " blocks a side and " + DigArea.MAX_VOLUME + " blocks in all)" : null;
		if (problem != null) {
			Toasts.show("AutoDig", "Cannot start: " + problem, 0xFFFF5555);
			setEnabled(false);
			return;
		}
		skipped.clear();
		hidden.clear();
		aimFails.clear();
		broken = hiddenRounds = navFails = idleArrivals = notReady = wait = 0;
		lastResult = "";
		layer = area.maxY();
		target = null;
		state = State.SCAN;
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		stopDig(mc);
		nav.cancel();
		RotationController.clear();
		target = null;
	}

	private void stopDig(Minecraft mc) {
		if (digging && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
		digging = false;
	}

	private void finish(String message) {
		lastResult = message;
		Toasts.show("AutoDig", message);
		setEnabled(false);
	}

	// what may be dug --------------------------------------------------------------------------------------------

	private boolean playerNear(Minecraft mc, LocalPlayer p) {
		double r2 = stopPlayer.get() * stopPlayer.get();
		for (Player o : mc.level.players()) {
			if (o == p || o.isSpectator() || Friends.is(o.getName().getString())) continue;
			if (o.distanceToSqr(p) <= r2) return true;
		}
		return false;
	}

	private boolean diggable(Minecraft mc, BlockPos pos) {
		BlockState s = mc.level.getBlockState(pos);
		if (s.isAir() || s.getBlock() instanceof LiquidBlock || s.getDestroySpeed(mc.level, pos) < 0) return false;
		if (mc.level.getBlockEntity(pos) != null) return false; // containers, spawners, signs and anything else with data
		return !never.contains(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
	}

	/** A block that is there but must stay: protected, unbreakable, or something with data. */
	private boolean protectedBlock(Minecraft mc, BlockPos pos) {
		BlockState s = mc.level.getBlockState(pos);
		return !s.isAir() && !(s.getBlock() instanceof LiquidBlock) && !diggable(mc, pos);
	}

	private boolean touchesLava(Minecraft mc, BlockPos pos) {
		for (Direction d : Direction.values()) if (mc.level.getFluidState(pos.relative(d)).is(FluidTags.LAVA)) return true;
		return false;
	}

	/** True when digging this block would drop the player more than three blocks. */
	private boolean opensDrop(Minecraft mc, LocalPlayer p, BlockPos pos) {
		return pos.equals(p.blockPosition().below()) && dropBelow(mc, pos);
	}

	/** True when there is nothing to land on within three blocks under this block. */
	private boolean dropBelow(Minecraft mc, BlockPos pos) {
		for (int i = 1; i <= 3; i++) {
			BlockState below = mc.level.getBlockState(pos.below(i));
			if (LevelWorld.hazard(below)) return true;
			if (!below.getCollisionShape(mc.level, pos.below(i)).isEmpty()) return false;
		}
		return true;
	}

	/** Still to be dug: in a loaded chunk, diggable, not left alone and not next to lava (those are left alone here). */
	private boolean wanted(Minecraft mc, BlockPos pos) {
		long key = pos.asLong();
		if (skipped.containsKey(key) || !mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || !diggable(mc, pos)) return false;
		if (avoidLava.get() && touchesLava(mc, pos)) {
			skipped.put(key, "next to lava");
			return false;
		}
		return true;
	}

	/** Moves {@link #layer} down to the highest layer that still has something to dig. False when the box is done. */
	private boolean findLayer(Minecraft mc) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (; layer >= area.minY(); layer--) {
			for (int x = area.minX(); x <= area.maxX(); x++) {
				for (int z = area.minZ(); z <= area.maxZ(); z++) {
					if (wanted(mc, m.set(x, layer, z))) return true;
				}
			}
		}
		return false;
	}

	private int lowestNow() { return Math.max(area.minY(), layer - layers.asInt() + 1); }

	// main loop --------------------------------------------------------------------------------------------------

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.gameMode == null || area == null) {
			setEnabled(false);
			return;
		}
		if (mc.screen != null) { // a menu is open: wait, and never keep a dig going behind it
			stopDig(mc);
			nav.releaseKeys();
			return;
		}
		if (stopFull.get() && p.getInventory().getFreeSlot() < 0) {
			finish("Inventory full after " + broken + " blocks");
			return;
		}
		if (stopPlayer.get() > 0 && playerNear(mc, p)) {
			finish("A player came close: stopped");
			return;
		}
		nav.tick();
		if (wait > 0) {
			wait--;
			return;
		}
		switch (state) {
			case SCAN -> {
				stopDig(mc);
				if (!findLayer(mc)) {
					finish("Done: " + broken + " blocks dug" + (skipped.isEmpty() ? "" : ", " + leftAlone()));
					return;
				}
				BlockPos b = scan(mc, p);
				if (b != null) begin(b);
				else navigate(mc, p);
			}
			case NAV -> walking(mc, p);
			case DIG -> dig(mc, p);
		}
	}

	private void begin(BlockPos pos) {
		target = pos;
		digTicks = lookTicks = 0;
		idleArrivals = navFails = 0;
		state = State.DIG;
	}

	/** Every block of the working layers within reach that the eye can see; returns the one needing the smallest turn. */
	private BlockPos scan(Minecraft mc, LocalPlayer p) {
		Vec3 eye = p.getEyePosition();
		double r = reach.get();
		int span = (int) Math.ceil(r) + 1;
		BlockPos c = BlockPos.containing(eye);
		boolean haveWide = wideSlot(p) >= 0;
		Rotation look = RotationController.current();
		List<BlockPos> found = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = Math.min(layer, c.getY() + span); y >= Math.max(lowestNow(), c.getY() - span); y--) {
			for (int x = Math.max(area.minX(), c.getX() - span); x <= Math.min(area.maxX(), c.getX() + span); x++) {
				for (int z = Math.max(area.minZ(), c.getZ() - span); z <= Math.min(area.maxZ(), c.getZ() + span); z++) {
					m.set(x, y, z);
					if (eye.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) > (r + 0.9) * (r + 0.9) || !wanted(mc, m)) continue;
					if (avoidDrops.get() && opensDrop(mc, p, m)) continue;
					found.add(m.immutable());
				}
			}
		}
		// top layer first; with a 3x3 pickaxe the middle of a slab before its rim; then the smallest turn of the head
		found.sort(Comparator.comparingInt((BlockPos b) -> -b.getY())
			.thenComparingInt(b -> haveWide && !area.planeInside(b.getX(), b.getY(), b.getZ(), 1) ? 1 : 0)
			.thenComparingDouble(b -> {
				Rotation to = RotationController.rotationTo(eye, Vec3.atCenterOf(b));
				return Math.hypot(Rotations.wrapDegrees(to.yaw() - look.yaw()), to.pitch() - look.pitch());
			}));
		for (BlockPos b : found) {
			Vec3 point = Placer.visiblePoint(mc, p, b, eye, r);
			if (point != null) {
				digPoint = point;
				return b;
			}
		}
		return null;
	}

	// getting there ----------------------------------------------------------------------------------------------

	/** The nearest blocks of the working layers still to dig (at most 48). */
	private List<BlockPos> nearestWork(Minecraft mc, LocalPlayer p) {
		List<BlockPos> all = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = layer; y >= lowestNow(); y--) {
			for (int x = area.minX(); x <= area.maxX(); x++) {
				for (int z = area.minZ(); z <= area.maxZ(); z++) {
					m.set(x, y, z);
					if (!hidden.contains(m.asLong()) && wanted(mc, m)) all.add(m.immutable());
				}
			}
		}
		Vec3 at = p.position();
		all.sort(Comparator.comparingDouble(b -> b.distToCenterSqr(at)));
		return all.size() > 48 ? new ArrayList<>(all.subList(0, 48)) : all;
	}

	private void navigate(Minecraft mc, LocalPlayer p) {
		frontier = nearestWork(mc, p);
		if (frontier.isEmpty()) { // everything left in these layers had no way to it
			if (!hidden.isEmpty() && hiddenRounds++ < 2) {
				hidden.clear(); // one more try from where we are now
				return;
			}
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (long key : hidden) if (wanted(mc, m.set(key))) skipped.put(key, "out of reach");
			hidden.clear();
			hiddenRounds = 0;
			return;
		}
		double r = reach.get();
		WorkNavigator.Start s = nav.start(mc, frontier, r, p.getEyeHeight(), (feet, eye) -> {
			for (BlockPos t : frontier) {
				if (eye.distanceToSqr(Vec3.atCenterOf(t)) > (r + 0.9) * (r + 0.9)) continue;
				// standing on it is the one place it must not be dug from: look for a spot beside it
				if (avoidDrops.get() && t.getX() == feet.x() && t.getZ() == feet.z() && t.getY() == feet.y() - 1 && dropBelow(mc, t)) continue;
				if (Placer.visiblePoint(mc, p, t, eye, r) != null) return true;
			}
			return false;
		}, false, key -> true);
		switch (s) {
			case OK -> {
				notReady = 0;
				navTicks = 0;
				state = State.NAV;
			}
			case NOT_READY -> {
				wait = 5;
				if (++notReady > 20) {
					notReady = 0;
					for (BlockPos t : frontier) hidden.add(t.asLong());
				}
			}
			case NO_ROUTE -> {
				for (BlockPos t : frontier) hidden.add(t.asLong());
			}
		}
	}

	private void walking(Minecraft mc, LocalPlayer p) {
		switch (nav.state()) {
			case WALKING -> {
				// whatever comes within reach and sight on the way gets dug on the way
				if (++navTicks % 8 == 0 && p.onGround()) {
					BlockPos b = scan(mc, p);
					if (b != null) {
						nav.cancel();
						begin(b);
					}
				}
			}
			case ARRIVED -> {
				RotationController.clear();
				if (!nav.partial() && ++idleArrivals >= 3) { // arriving and still seeing nothing: do not come back forever
					for (BlockPos t : frontier) hidden.add(t.asLong());
					idleArrivals = 0;
				}
				nav.cancel();
				state = State.SCAN;
				wait = 2;
			}
			default -> {
				if (++navFails >= 3) {
					for (BlockPos t : frontier) hidden.add(t.asLong());
					navFails = 0;
				}
				nav.cancel();
				state = State.SCAN;
				wait = 5;
			}
		}
	}

	// digging ----------------------------------------------------------------------------------------------------

	private void leave(BlockPos pos, String why) {
		skipped.put(pos.asLong(), why);
		target = null;
		state = State.SCAN;
	}

	private void dig(Minecraft mc, LocalPlayer p) {
		if (target == null) {
			state = State.SCAN;
			return;
		}
		if (!diggable(mc, target)) { // gone: we dug it, or a 3x3 hit took it
			if (digging) broken++;
			aimFails.remove(target.asLong());
			hidden.clear(); // the view changed: blocks there was no way to get another try
			hiddenRounds = 0;
			stopDig(mc);
			state = State.SCAN;
			return;
		}
		if (avoidDrops.get() && opensDrop(mc, p, target)) {
			stopDig(mc);
			leave(target, "a drop below it");
			return;
		}
		if (++digTicks > 600) {
			stopDig(mc);
			leave(target, "took too long to break");
			return;
		}
		if (p.getEyePosition().distanceTo(Vec3.atCenterOf(target)) > reach.get() + 1.2) { // we fell or were pushed away
			stopDig(mc);
			state = State.SCAN;
			return;
		}
		// it has to be the block under the crosshair before the first hit lands
		if (!(mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(target))) {
			stopDig(mc);
			if (++lookTicks > 45) { // lost sight of it: look at everything again
				// three times in a row on the same block means the crosshair will never land on it (a mob in the way, a sliver of a view)
				if (aimFails.merge(target.asLong(), 1, Integer::sum) >= 3) skipped.put(target.asLong(), "could not get the crosshair on it");
				target = null;
				state = State.SCAN;
				return;
			}
			if (digPoint == null || (!RotationController.busy() && lookTicks % 10 == 0)) digPoint = Placer.visiblePoint(mc, p, target, p.getEyePosition(), reach.get() + 0.5);
			if (digPoint != null) RotationController.aimAtPoint(digPoint, 14f);
			return;
		}
		lookTicks = 0;
		int slot = toolFor(mc, p, target, bh.getDirection().getAxis().ordinal());
		if (slot < 0) {
			stopDig(mc);
			leave(target, "on the edge, needs an ordinary pickaxe");
			return;
		}
		if (slot != p.getInventory().getSelectedSlot()) {
			stopDig(mc);
			p.getInventory().setSelectedSlot(slot);
			return; // the next tick digs with the new tool
		}
		if (mc.gameMode.continueDestroyBlock(target, bh.getDirection())) p.swing(InteractionHand.MAIN_HAND);
		digging = true;
	}

	// tools ------------------------------------------------------------------------------------------------------

	private boolean isWide(ItemStack s) {
		if (s.isEmpty() || wide.get() == Wide.OFF) return false;
		String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		if (!id.endsWith("_pickaxe")) return false;
		if (wide.get() == Wide.ON) return true;
		String want = wideName.get().trim().toLowerCase(Locale.ROOT);
		return !want.isEmpty() && s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(want);
	}

	private boolean worn(ItemStack s) {
		return minDurability.asInt() > 0 && s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= minDurability.asInt();
	}

	/** Hotbar slot of a usable 3x3 pickaxe, or -1. */
	private int wideSlot(LocalPlayer p) {
		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (isWide(s) && !worn(s)) return i;
		}
		return -1;
	}

	/** Whether a 3x3 hit on this block, across the face on {@code axis}, takes nothing it should not. */
	private boolean wideAllowed(Minecraft mc, BlockPos pos, int axis) {
		if (stayInside.get() && !area.planeInside(pos.getX(), pos.getY(), pos.getZ(), axis)) return false;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				m.set(pos.getX() + (axis == 0 ? 0 : a), pos.getY() + (axis == 1 ? 0 : axis == 0 ? a : b), pos.getZ() + (axis == 2 ? 0 : b));
				if (protectedBlock(mc, m)) return false; // a chest or spawner in the slab: one block at a time here
				if (avoidLava.get() && !m.equals(pos) && touchesLava(mc, m) && !mc.level.getBlockState(m).isAir()) return false;
			}
		}
		return true;
	}

	/**
	 * The hotbar slot to dig this block with: the 3x3 pickaxe where its slab is allowed, otherwise the fastest ordinary
	 * tool. -1 when only a 3x3 pickaxe could dig it and it must not be used here.
	 */
	private int toolFor(Minecraft mc, LocalPlayer p, BlockPos pos, int axis) {
		BlockState st = mc.level.getBlockState(pos);
		boolean wideOk = wideAllowed(mc, pos, axis);
		if (wideOk) {
			int w = wideSlot(p);
			if (w >= 0) return w;
		}
		int best = -1;
		float bestSpeed = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (worn(s) || (!wideOk && isWide(s))) continue;
			float v = s.isEmpty() ? 1f : s.getDestroySpeed(st);
			if (v > bestSpeed + 0.01f || (v > bestSpeed - 0.01f && i == p.getInventory().getSelectedSlot())) {
				bestSpeed = v;
				best = i;
			}
		}
		// bare hands on stone would take minutes a block: when a 3x3 pickaxe was the only real tool, leave the block instead
		if (!wideOk && bestSpeed <= 1.01f && st.requiresCorrectToolForDrops() && wideSlot(p) >= 0) return -1;
		return best;
	}

	// render -----------------------------------------------------------------------------------------------------

	@Override
	protected boolean rendersWhileDisabled() { return marking != 0 || System.currentTimeMillis() < showMarkedUntil; }

	@Override
	protected void onRender(RenderFrame f) {
		int c = color.get() | 0xFF000000;
		if (!enabled()) {
			// marking: the box once both corners are set, otherwise just the first corner
			DigArea m = area();
			int[] one = DigArea.parseCorner(corner1.get());
			if (m != null) f.drawBox(m.minX(), m.minY(), m.minZ(), m.maxX() + 1, m.maxY() + 1, m.maxZ() + 1, c);
			else if (one != null) f.drawBox(one[0], one[1], one[2], one[0] + 1, one[1] + 1, one[2] + 1, c);
			return;
		}
		DigArea a = area;
		if (a == null) return;
		f.drawBox(a.minX(), a.minY(), a.minZ(), a.maxX() + 1, a.maxY() + 1, a.maxZ() + 1, c);
		BlockPos t = target;
		if (t != null) f.drawBox(t.getX(), t.getY(), t.getZ(), t.getX() + 1, t.getY() + 1, t.getZ() + 1, 0xFFFFFFFF);
	}
}
