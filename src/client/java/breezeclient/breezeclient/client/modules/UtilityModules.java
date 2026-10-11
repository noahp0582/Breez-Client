package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.world.BlockIds;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/** Mining, tool and inventory helpers. Each is a small module; they share this file. */
public final class UtilityModules {
	private UtilityModules() {}

	/** Blocks no automatic mining module breaks, besides anything with an inventory. */
	static final List<String> NEVER_MINE = List.of("minecraft:spawner", "minecraft:ender_chest", "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
		"minecraft:beacon", "minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:respawn_anchor");

	private static boolean breakable(Minecraft mc, BlockPos pos, StringListSetting never) {
		BlockState s = mc.level.getBlockState(pos);
		if (s.isAir() || s.getDestroySpeed(mc.level, pos) < 0) return false;
		if (mc.level.getBlockEntity(pos) != null) return false; // containers, spawners, signs and anything else with data
		return !never.contains(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
	}

	/** Holds the attack button while your crosshair is on a block that is safe to break. */
	public static class AutoMine extends Module {
		private final StringListSetting never = add(new StringListSetting("Never break", NEVER_MINE).kind(StringListSetting.Kind.BLOCK), "Safety",
			"Blocks that are never mined. Anything with an inventory is protected too.");
		private final BoolSetting needTool = add(new BoolSetting("Only with an item in hand", false), "Safety", "Do nothing with an empty hand.");
		private boolean forced;

		public AutoMine() { super("AutoMine", Category.MISC); }

		@Override
		protected boolean persistsEnabled() { return false; }

		@Override
		protected void onDisable() { release(); }

		private void release() {
			if (forced) KeyMapping.setAll();
			forced = false;
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.level == null || mc.screen != null) {
				release();
				return;
			}
			boolean ok = mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && breakable(mc, bh.getBlockPos(), never)
				&& !(needTool.get() && p.getMainHandItem().isEmpty());
			if (ok) {
				mc.options.keyAttack.setDown(true);
				forced = true;
			} else {
				release();
			}
		}
	}

	/** Switches to the fastest tool in your hotbar for the block you are breaking, and back when you stop. */
	public static class AutoTool extends Module {
		private final BoolSetting restore = add(new BoolSetting("Restore slot afterwards", true), "General", "Return to the slot you held when you stop mining.");
		private final NumberSetting minDurability = add(new NumberSetting("Keep tools above (uses)", 0, 0, 100), "General",
			"Do not use a tool with this few uses left (0 = use anything).");
		private int back = -1;
		private int ours = -1;

		public AutoTool() { super("AutoTool", Category.MISC); }

		@Override
		protected void onDisable() {
			back = -1;
			ours = -1;
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.level == null) return;
			boolean mining = mc.screen == null && mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK;
			if (!mining) {
				if (back >= 0 && restore.get() && p.getInventory().getSelectedSlot() == ours) p.getInventory().setSelectedSlot(back);
				back = -1;
				ours = -1;
				return;
			}
			BlockState state = mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos());
			if (state.isAir()) return;
			int cur = p.getInventory().getSelectedSlot();
			if (ours >= 0 && cur != ours) { // you chose a slot yourself while mining: do not fight it
				back = -1;
				ours = -1;
				return;
			}
			int best = cur;
			float bestSpeed = speed(p.getInventory().getItem(cur), state);
			for (int i = 0; i < 9; i++) {
				ItemStack s = p.getInventory().getItem(i);
				if (minDurability.asInt() > 0 && s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= minDurability.asInt()) continue;
				float v = speed(s, state);
				if (v > bestSpeed + 0.01f) {
					bestSpeed = v;
					best = i;
				}
			}
			if (best != cur) {
				if (back < 0) back = cur;
				p.getInventory().setSelectedSlot(best);
				ours = best;
			}
		}

		private static float speed(ItemStack s, BlockState state) { return s.isEmpty() ? 1f : s.getDestroySpeed(state); }
	}

	/** A client-side copy of a player that stands where you stood. Nothing is sent to the server and nobody else sees it. */
	public static class Fakeplayer extends Module {
		private static int counter;
		private final StringSetting name = add(new StringSetting("Name", "Fakeplayer"), "General", "The name shown above it.");
		private final BoolSetting copyGear = add(new BoolSetting("Copy my gear", true), "General", "Wears the armor and holds the items you have on.");
		private final BoolSetting copyPose = add(new BoolSetting("Copy my pose", true), "General", "Sneaks when you were sneaking at the time.");
		private RemotePlayer fake;
		private Object level;

		public Fakeplayer() { super("Fakeplayer", Category.MISC); }

		@Override
		protected boolean persistsEnabled() { return false; }

		@Override
		public String info() { return fake == null ? "" : name.get(); }

		@Override
		protected void onEnable() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.level == null) {
				setEnabled(false);
				return;
			}
			String n = name.get().isBlank() ? "Fakeplayer" : name.get().trim();
			if (n.length() > 16) n = n.substring(0, 16);
			fake = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), n));
			fake.setId(-1_000_000 - (counter++ % 1000)); // client-only id: a real entity can never share it
			fake.snapTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
			fake.setYHeadRot(p.getYHeadRot());
			fake.setYBodyRot(p.yBodyRot);
			if (copyGear.get()) {
				for (EquipmentSlot slot : EquipmentSlot.values()) fake.setItemSlot(slot, p.getItemBySlot(slot).copy());
			}
			if (copyPose.get()) fake.setShiftKeyDown(p.isShiftKeyDown());
			fake.setHealth(20f);
			mc.level.addEntity(fake);
			level = mc.level;
			Toasts.show("Fakeplayer", "Spawned " + n + " (only you can see it)");
		}

		@Override
		protected void onDisable() {
			Minecraft mc = Minecraft.getInstance();
			if (fake != null && mc.level != null && mc.level == level) mc.level.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
			fake = null;
			level = null;
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.level != level || fake == null || fake.isRemoved()) setEnabled(false); // world changed or something removed it
		}
	}

	/** Drops items from your inventory at a steady pace: everything on the list (or everything, if you allow it). */
	public static class ItemDropper extends Module {
		private final StringListSetting items = add(new StringListSetting("Items", List.of()).kind(StringListSetting.Kind.ITEM), "Targets",
			"Items to drop. Empty drops nothing unless 'Drop everything' is on.");
		private final BoolSetting everything = add(new BoolSetting("Drop everything", false), "Targets", "Ignore the list and drop every item.");
		private final StringListSetting keep = add(new StringListSetting("Keep", List.of("minecraft:totem_of_undying", "minecraft:elytra", "minecraft:netherite_ingot",
			"minecraft:enchanted_golden_apple", "minecraft:ender_pearl")).kind(StringListSetting.Kind.ITEM), "Targets", "Never dropped, even with 'Drop everything'.");
		private final BoolSetting hotbar = add(new BoolSetting("Include hotbar", false), "Targets", "Also drop from the hotbar.");
		private final BoolSetting wholeStacks = add(new BoolSetting("Whole stacks", true), "Targets", "Drop the full stack in one go instead of one item at a time.");
		private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 3, 1, 20), "Timing", "Average pause between drops.");

		private final ActionLimiter limiter = new ActionLimiter(new Random());
		private int wait;
		private int dropped;

		public ItemDropper() { super("ItemDropper", Category.DONUT); }

		@Override
		protected boolean persistsEnabled() { return false; }

		@Override
		protected void onEnable() {
			dropped = 0;
			wait = 0;
			if (items.get().isEmpty() && !everything.get()) {
				Toasts.show("ItemDropper", "Add items to the list first (or turn on Drop everything)", 0xFFFF5555);
				setEnabled(false);
			}
		}

		@Override
		public String info() { return dropped == 0 ? "" : String.valueOf(dropped); }

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.gameMode == null) {
				setEnabled(false);
				return;
			}
			if (mc.screen != null || wait-- > 0) return;
			int slot = next(p);
			if (slot < 0) {
				Toasts.show("ItemDropper", "Done: dropped " + dropped + " stack" + (dropped == 1 ? "" : "s"));
				setEnabled(false);
				return;
			}
			// THROW: button 0 drops one item, button 1 the whole stack
			mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, slot, wholeStacks.get() ? 1 : 0, ClickType.THROW, p);
			dropped++;
			wait = limiter.nextDelayTicks(delay.get(), delay.get() * 0.3, 1);
		}

		/** Menu slot (9-35 inventory, 36-44 hotbar) of the next stack to drop, or -1. */
		private int next(LocalPlayer p) {
			for (int i = 9; i < (hotbar.get() ? 45 : 36); i++) {
				ItemStack s = p.inventoryMenu.getSlot(i).getItem();
				if (s.isEmpty()) continue;
				String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
				if (keep.contains(id)) continue;
				if (everything.get() || items.contains(id)) return i;
			}
			return -1;
		}
	}

	/**
	 * Notices when you are walled into a one by two cell and digs out through the softest block. It never touches
	 * containers, spawners or anything unbreakable.
	 */
	public static class AntiTrap extends Module {
		private final NumberSetting enclosedTicks = add(new NumberSetting("Enclosed for (ticks)", 15, 5, 100), "Trigger",
			"How long you must be boxed in before digging out, so a hole you dug yourself does not set it off.");
		private final BoolSetting notify = add(new BoolSetting("Notify", true), "Alerts", "Pop-up when you are trapped.");
		private final StringListSetting never = add(new StringListSetting("Never break", NEVER_MINE).kind(StringListSetting.Kind.BLOCK), "Safety",
			"Blocks that are never mined. Anything with an inventory is protected too.");

		private int boxed;
		private boolean warned;
		private BlockPos digging;

		public AntiTrap() { super("AntiTrap", Category.DONUT); }

		@Override
		protected void onDisable() {
			stopDigging(Minecraft.getInstance());
			boxed = 0;
			warned = false;
		}

		private void stopDigging(Minecraft mc) {
			if (digging != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
			digging = null;
		}

		/** The four side cells at head height, the cell above, and whether feet and head are open and boxed in. */
		private static boolean isBoxed(Minecraft mc, BlockPos feet) {
			var level = mc.level;
			if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return false;
			for (BlockPos base : new BlockPos[] {feet, feet.above()}) {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos n = base.relative(d);
					BlockState s = level.getBlockState(n);
					if (s.getCollisionShape(level, n).isEmpty() || !s.getFluidState().isEmpty()) return false;
				}
			}
			BlockPos up = feet.above(2);
			return !level.getBlockState(up).getCollisionShape(level, up).isEmpty();
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
			BlockPos feet = p.blockPosition();
			if (!isBoxed(mc, feet)) {
				stopDigging(mc);
				boxed = 0;
				warned = false;
				return;
			}
			if (++boxed < enclosedTicks.asInt()) return;
			if (notify.get() && !warned) {
				warned = true;
				Toasts.show("AntiTrap", "You are boxed in: digging out", 0xFFFFAA00);
			}
			if (digging == null || !breakable(mc, digging, never) || mc.level.getBlockState(digging).isAir()) {
				digging = softest(mc, feet);
				if (digging == null) return;
				mc.gameMode.startDestroyBlock(digging, Direction.UP);
			}
			Vec3 c = Vec3.atCenterOf(digging);
			RotationController.aimAtPoint(c, 14f);
			Direction face = Direction.getApproximateNearest(c.x - p.getX(), c.y - p.getEyeY(), c.z - p.getZ()).getOpposite();
			if (mc.gameMode.continueDestroyBlock(digging, face)) p.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		}

		/** The adjacent breakable block (sides at head height, feet level sides, or above) with the lowest hardness. */
		private BlockPos softest(Minecraft mc, BlockPos feet) {
			BlockPos best = null;
			float bestHard = Float.MAX_VALUE;
			List<BlockPos> candidates = new java.util.ArrayList<>();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				candidates.add(feet.above().relative(d));
				candidates.add(feet.relative(d));
			}
			candidates.add(feet.above(2));
			for (BlockPos c : candidates) {
				if (!breakable(mc, c, never)) continue;
				float h = mc.level.getBlockState(c).getDestroySpeed(mc.level, c);
				if (h < bestHard) {
					bestHard = h;
					best = c;
				}
			}
			return best;
		}
	}

	/** Parses the id of a block for display. */
	static String pretty(BlockState s) { return BlockIds.idOf(s.getBlock()).replace("minecraft:", "").replace('_', ' ').toLowerCase(Locale.ROOT); }
}
