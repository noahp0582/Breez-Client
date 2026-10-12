package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.nav.LevelWorld;
import breezeclient.breezeclient.client.nav.PathWalker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.dig.DigArea;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import net.minecraft.client.Minecraft;
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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Clears every block inside a box you mark with two corners, top layer first. It walks into reach, looks at each block
 * and holds the dig the way a player does. With a 3x3 pickaxe (the DonutSMP shard pickaxe) it counts on one hit taking a
 * whole slab, and swaps to an ordinary pickaxe where that slab would reach outside the box.
 */
public class AutoDig extends Module {
	public enum Wide { AUTO, ON, OFF }

	private enum State { PICK, WALK, DIG }

	private final StringSetting corner1 = add(new StringSetting("Corner 1 (x y z)", ""), "Area", "One corner of the box to clear. Use the button below or .dig pos1 while looking at a block.");
	private final StringSetting corner2 = add(new StringSetting("Corner 2 (x y z)", ""), "Area", "The opposite corner. Use the button below or .dig pos2.");
	private final ColorSetting color = add(new ColorSetting("Outline color", 0xFFFFAA33), "Area", "Color of the box drawn around the area.");
	private final NumberSetting reach = add(new NumberSetting("Reach", 4.2, 3.0, 4.5), "Digging", "How close it gets before digging a block.");
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

	private final PathWalker walker = new PathWalker();
	private final Set<Long> skipped = new HashSet<>();
	/** Could not be seen from where we stood: tried again once something else has been dug. */
	private final Set<Long> hidden = new HashSet<>();
	private DigArea area;
	private State state = State.PICK;
	private BlockPos target;
	private int layer;
	private int digTicks;
	private int lookTicks;
	private int walkTries;
	private int broken;
	private int edgeLeft;
	private boolean digging;

	public AutoDig() { super("AutoDig", Category.WORLD); }

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

	/** The marked box, or null while a corner is missing or unreadable. */
	public DigArea area() {
		int[] a = DigArea.parseCorner(corner1.get()), b = DigArea.parseCorner(corner2.get());
		if (a == null || b == null) return null;
		return DigArea.of(a[0], a[1], a[2], b[0], b[1], b[2]);
	}

	public String status() {
		DigArea a = area();
		if (a == null) return "No area yet. Look at a block and use .dig pos1, then .dig pos2 at the opposite corner.";
		String size = a.sizeX() + "x" + a.sizeY() + "x" + a.sizeZ();
		if (!enabled()) return "Area " + size + " (" + a.volume() + " blocks). .dig start to begin.";
		return "Area " + size + ": " + broken + " dug, layer y=" + layer + (skipped.isEmpty() ? "" : ", " + skipped.size() + " left alone");
	}

	// lifecycle --------------------------------------------------------------------------------------------------

	@Override
	protected void onEnable() {
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
		broken = edgeLeft = 0;
		layer = area.maxY();
		target = null;
		state = State.PICK;
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		stopDig(mc);
		walker.cancel();
		RotationController.clear();
		target = null;
	}

	private void stopDig(Minecraft mc) {
		if (digging && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
		digging = false;
	}

	private void finish(String message) {
		Toasts.show("AutoDig", message);
		setEnabled(false);
	}

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
			walker.releaseKeys();
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
		walker.tick();
		switch (state) {
			case PICK -> pick(mc, p);
			case WALK -> walk(mc, p);
			case DIG -> dig(mc, p);
		}
	}

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
		if (!pos.equals(p.blockPosition().below())) return false;
		for (int i = 1; i <= 3; i++) {
			BlockState below = mc.level.getBlockState(pos.below(i));
			if (LevelWorld.hazard(below)) return true;
			if (!below.getCollisionShape(mc.level, pos.below(i)).isEmpty()) return false;
		}
		return true;
	}

	private void pick(Minecraft mc, LocalPlayer p) {
		stopDig(mc);
		Vec3 eye = p.getEyePosition();
		boolean haveWide = wideSlot(p) >= 0;
		while (layer >= area.minY()) {
			BlockPos best = null;
			double bestScore = Double.MAX_VALUE;
			boolean anyHidden = false;
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int x = area.minX(); x <= area.maxX(); x++) {
				for (int z = area.minZ(); z <= area.maxZ(); z++) {
					m.set(x, layer, z);
					if (!mc.level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
					long key = m.asLong();
					if (skipped.contains(key) || !diggable(mc, m)) continue;
					if (avoidLava.get() && touchesLava(mc, m)) {
						skipped.add(key);
						continue;
					}
					if (hidden.contains(key)) {
						anyHidden = true;
						continue;
					}
					double score = eye.distanceToSqr(x + 0.5, layer + 0.5, z + 0.5);
					// a 3x3 pickaxe does the most from the middle of a slab, so the rim waits until the middle is gone
					if (haveWide && !area.planeInside(x, layer, z, 1)) score += 64;
					if (score < bestScore) {
						bestScore = score;
						best = m.immutable();
					}
				}
			}
			if (best != null) {
				begin(mc, p, best);
				return;
			}
			if (anyHidden) { // everything left on this layer was out of sight: one more look from here, then give up on it
				for (int x = area.minX(); x <= area.maxX(); x++) {
					for (int z = area.minZ(); z <= area.maxZ(); z++) {
						long key = BlockPos.asLong(x, layer, z);
						if (hidden.remove(key)) skipped.add(key);
					}
				}
			}
			layer--;
		}
		String left = skipped.isEmpty() ? "" : ", " + skipped.size() + " left alone"
			+ (edgeLeft > 0 ? " (" + edgeLeft + " on the edge need an ordinary pickaxe in your hotbar)" : "");
		finish("Done: " + broken + " blocks dug" + left);
	}

	private void begin(Minecraft mc, LocalPlayer p, BlockPos pos) {
		target = pos;
		digTicks = lookTicks = 0;
		if (p.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > reach.get() - 0.2) {
			if (!walker.start(PathWalker.posOf(pos), Math.max(2.0, reach.get() - 1.0))) {
				giveUp(pos, walkTries++ < 2);
				return;
			}
			state = State.WALK;
			return;
		}
		walkTries = 0;
		state = State.DIG;
	}

	/** Leaves a block: for now (retried when the view changes) or for the rest of the run. */
	private void giveUp(BlockPos pos, boolean retryLater) {
		(retryLater ? hidden : skipped).add(pos.asLong());
		target = null;
		state = State.PICK;
	}

	private void walk(Minecraft mc, LocalPlayer p) {
		switch (walker.state()) {
			case WALKING -> { }
			case ARRIVED -> {
				RotationController.clear();
				walkTries = 0;
				state = State.DIG;
			}
			default -> {
				if (target != null) giveUp(target, false);
				else state = State.PICK;
			}
		}
	}

	private void dig(Minecraft mc, LocalPlayer p) {
		if (target == null) {
			state = State.PICK;
			return;
		}
		if (!diggable(mc, target)) { // gone: we dug it, or a 3x3 hit took it
			if (digging) broken++;
			hidden.clear(); // the view changed: blocks that were out of sight get another try
			stopDig(mc);
			state = State.PICK;
			return;
		}
		if (avoidDrops.get() && opensDrop(mc, p, target)) {
			stopDig(mc);
			giveUp(target, false);
			return;
		}
		if (++digTicks > 600) {
			stopDig(mc);
			giveUp(target, false);
			return;
		}
		if (p.getEyePosition().distanceTo(Vec3.atCenterOf(target)) > reach.get() + 0.3) { // we fell or were pushed away
			stopDig(mc);
			state = State.PICK;
			return;
		}
		// it has to be the block under the crosshair before the first hit lands
		if (!(mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(target))) {
			stopDig(mc);
			if (mc.hitResult instanceof BlockHitResult other && other.getType() == HitResult.Type.BLOCK && !RotationController.busy() && lookTicks > 6) {
				BlockPos inWay = other.getBlockPos();
				if (area.contains(inWay.getX(), inWay.getY(), inWay.getZ()) && diggable(mc, inWay) && !skipped.contains(inWay.asLong())
					&& !(avoidLava.get() && touchesLava(mc, inWay))) {
					target = inWay; // something else in the box is in front of it: take that first
					digTicks = lookTicks = 0;
					return;
				}
			}
			if (++lookTicks > 40) {
				giveUp(target, true);
				return;
			}
			RotationController.aimAtPoint(Vec3.atCenterOf(target), 14f);
			return;
		}
		lookTicks = 0;
		int slot = toolFor(mc, p, target, bh.getDirection().getAxis().ordinal());
		if (slot < 0) {
			edgeLeft++;
			stopDig(mc);
			giveUp(target, false);
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
	protected void onRender(RenderFrame f) {
		DigArea a = enabled() && area != null ? area : null;
		if (a == null) return;
		int c = color.get() | 0xFF000000;
		f.drawBox(a.minX(), a.minY(), a.minZ(), a.maxX() + 1, a.maxY() + 1, a.maxZ() + 1, c);
		BlockPos t = target;
		if (t != null) f.drawBox(t.getX(), t.getY(), t.getZ(), t.getX() + 1, t.getY() + 1, t.getZ() + 1, 0xFFFFFFFF);
	}
}
