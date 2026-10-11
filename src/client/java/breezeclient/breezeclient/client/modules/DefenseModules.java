package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.input.Keys;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

import java.util.Random;

/** Totem and knockback helpers. Each is a small module; they share this file. */
public final class DefenseModules {
	private DefenseModules() {}

	private static boolean isTotem(net.minecraft.world.item.ItemStack s) { return s.is(Items.TOTEM_OF_UNDYING); }

	/** First main-inventory or hotbar menu slot (9-44) holding a totem, or -1. Menu numbering: 9-35 inventory, 36-44 hotbar. */
	private static int menuTotem(LocalPlayer p, boolean includeHotbar) {
		for (int i = 9; i < 36; i++) if (isTotem(p.inventoryMenu.getSlot(i).getItem())) return i;
		if (includeHotbar) for (int i = 36; i < 45; i++) if (isTotem(p.inventoryMenu.getSlot(i).getItem())) return i;
		return -1;
	}

	/** When your health runs low and a totem is in the offhand, also holds a totem in the main hand, so a pop never leaves you empty. */
	public static class AutoDoubleHand extends Module {
		private final NumberSetting health = add(new NumberSetting("Health at or below", 8, 2, 20), "Trigger", "Health points (2 per heart), absorption included, at which the main hand takes a totem.");
		private final NumberSetting recover = add(new NumberSetting("Recover margin", 6, 1, 20), "Trigger", "Switch back once health is this far above the trigger.");
		private final BoolSetting restore = add(new BoolSetting("Restore slot afterwards", true), "Trigger", "Return to the slot you were holding once you are safe.");

		private int back = -1;
		private int ours = -1;
		/** You chose a slot yourself while low on health: leave it alone until the danger has passed. */
		private boolean manual;

		public AutoDoubleHand() { super("AutoDoubleHand", Category.COMBAT); }

		@Override
		protected void onDisable() {
			back = -1;
			ours = -1;
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.screen != null) return;
			double hp = p.getHealth() + p.getAbsorptionAmount();
			boolean low = hp <= health.get();
			if (ours >= 0 && p.getInventory().getSelectedSlot() != ours) { // you picked another slot yourself: leave it alone
				back = -1;
				ours = -1;
				manual = true;
			}
			if (!low) manual = false;
			if (low) {
				if (manual || !isTotem(p.getOffhandItem()) || isTotem(p.getMainHandItem())) return;
				int slot = CombatUtil.hotbarSlot(p, Items.TOTEM_OF_UNDYING);
				if (slot < 0) return;
				if (back < 0) back = p.getInventory().getSelectedSlot();
				p.getInventory().setSelectedSlot(slot);
				ours = slot;
			} else if (back >= 0 && hp > health.get() + recover.get()) {
				if (restore.get()) p.getInventory().setSelectedSlot(back);
				back = -1;
				ours = -1;
			}
		}
	}

	/** While your inventory is open, moves a totem into the offhand (and optionally a hotbar slot) with ordinary clicks. */
	public static class AutoInvTotem extends Module {
		private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 3, 1, 20), "Timing", "Average pause before each move.");
		private final NumberSetting hotbarSlot = add(new NumberSetting("Fill hotbar slot (0 = off)", 0, 0, 9), "Slots", "Also keep a totem in this hotbar slot (1-9).");

		private final ActionLimiter limiter = new ActionLimiter(new Random());
		private int wait = -1;

		public AutoInvTotem() { super("AutoInvTotem", Category.COMBAT); }

		@Override
		protected void onDisable() { wait = -1; }

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.gameMode == null || !(mc.screen instanceof InventoryScreen)) {
				wait = -1;
				return;
			}
			int id = p.inventoryMenu.containerId;
			boolean needOffhand = !isTotem(p.getOffhandItem());
			int slotIdx = hotbarSlot.asInt() - 1;
			boolean needHotbar = slotIdx >= 0 && !isTotem(p.getInventory().getItem(slotIdx));
			if (!needOffhand && !needHotbar) {
				wait = -1;
				return;
			}
			if (wait < 0) wait = limiter.nextDelayTicks(delay.get(), delay.get() * 0.3, 1);
			if (--wait > 0) return;
			wait = -1;
			int src = menuTotem(p, false);
			if (needOffhand) {
				if (src < 0) src = menuTotem(p, true);
				if (src < 0) return;
				mc.gameMode.handleInventoryMouseClick(id, src, 40, ClickType.SWAP, p);
			} else {
				if (src < 0) return;
				mc.gameMode.handleInventoryMouseClick(id, src, slotIdx, ClickType.SWAP, p);
			}
		}
	}

	/**
	 * Puts a hotbar totem into the offhand with the swap-hands action instead of inventory clicks: selects the totem slot,
	 * waits two ticks for the server to learn of it, swaps, and returns to your slot.
	 */
	public static class TotemOffhand extends Module {
		private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 3, 1, 20), "Timing", "Average pause before starting after the offhand empties.");
		private final BoolSetting restore = add(new BoolSetting("Restore slot afterwards", true), "General", "Return to the slot you held before.");
		private final BoolSetting replaceHeld = add(new BoolSetting("Replace a held item", false), "General",
			"Also swap when the offhand holds something else (a shield, food). Off: only fill an empty offhand.");

		private final ActionLimiter limiter = new ActionLimiter(new Random());
		private int wait = -1;
		private int step;
		private int back = -1;
		private int settle;

		public TotemOffhand() { super("TotemOffhand", Category.COMBAT); }

		@Override
		protected void onDisable() {
			wait = -1;
			step = 0;
			back = -1;
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.gameMode == null || mc.getConnection() == null) return;
			if (step == 1) { // the totem slot is selected; give the server two ticks to learn of it, then swap
				if (--settle > 0) return;
				if (p.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
					mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
				}
				if (restore.get() && back >= 0) p.getInventory().setSelectedSlot(back);
				step = 0;
				back = -1;
				return;
			}
			if (mc.screen != null || isTotem(p.getOffhandItem()) || (!replaceHeld.get() && !p.getOffhandItem().isEmpty())) {
				wait = -1;
				return;
			}
			int slot = CombatUtil.hotbarSlot(p, Items.TOTEM_OF_UNDYING);
			if (slot < 0) return;
			if (wait < 0) wait = limiter.nextDelayTicks(delay.get(), delay.get() * 0.3, 1);
			if (--wait > 0) return;
			wait = -1;
			back = p.getInventory().getSelectedSlot();
			if (back != slot) p.getInventory().setSelectedSlot(slot);
			settle = back == slot ? 0 : 2;
			step = 1;
		}
	}

	/** Jumps on the tick you take a hit while on the ground: the jump replaces some of the knockback with upward motion. */
	public static class AutoJumpReset extends Module {
		private final BoolSetting onlyWhileMoving = add(new BoolSetting("Only while moving", false), "General", "Skip the reset when you are standing still.");
		private final ActionLimiter limiter = new ActionLimiter(new Random());
		private final NumberSetting chance = add(new NumberSetting("Chance (%)", 100, 10, 100), "General", "Chance to jump on each hit; below 100 looks less mechanical.");
		private int lastHurt;
		private boolean pressed;

		public AutoJumpReset() { super("AutoJumpReset", Category.COMBAT); }

		@Override
		protected void onDisable() {
			if (pressed) Keys.release(Minecraft.getInstance().options.keyJump);
			pressed = false;
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null) return;
			if (pressed) { // one tick of jump, then back to what your hand is doing
				pressed = false;
				Keys.release(mc.options.keyJump);
			}
			int hurt = p.hurtTime;
			boolean fresh = hurt > 0 && lastHurt == 0;
			lastHurt = hurt;
			if (!fresh || mc.screen != null || !p.onGround() || p.isUsingItem()) return;
			if (onlyWhileMoving.get() && p.getDeltaMovement().horizontalDistanceSqr() < 1e-4) return;
			if (!limiter.chance(chance.get() / 100.0)) return;
			Keys.press(mc.options.keyJump);
			pressed = true;
		}
	}
}
