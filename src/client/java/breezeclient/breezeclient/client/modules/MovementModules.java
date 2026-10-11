package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.input.Keys;
import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.flight.GlideController;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Random;

/** Sprint, elytra helpers and the "press a key to throw X" modules. They share this file. */
public final class MovementModules {
	private MovementModules() {}

	/** Holds sprint while you move forward. */
	public static class Sprint extends Module {
		private final BoolSetting whenHungry = add(new BoolSetting("Sprint while hungry", false), "General", "Keep sprinting at 6 food or less (vanilla stops you; the server may stop it too).");
		private boolean forced;

		public Sprint() { super("Sprint", Category.MOVEMENT); }

		@Override
		protected void onDisable() {
			if (forced) Keys.release(Minecraft.getInstance().options.keySprint);
			forced = false;
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.screen != null) return;
			boolean moving = mc.options.keyUp.isDown() && !mc.options.keyDown.isDown();
			boolean hungry = p.getFoodData().getFoodLevel() <= 6 && !p.getAbilities().mayfly;
			if (moving && !p.isShiftKeyDown() && !p.isUsingItem() && (whenHungry.get() || !hungry)) {
				Keys.press(mc.options.keySprint);
				forced = true;
			} else if (forced) {
				Keys.release(mc.options.keySprint); // hand sprint back to the physical key
				forced = false;
			}
		}
	}

	/**
	 * While gliding, cycles the pitch: dive to build speed, climb while it lasts, dive again. The climb is forced when the
	 * ground is close. Turns the camera with the same eased steps as the other aim helpers.
	 */
	public static class ElytraGlide extends Module {
		private final NumberSetting divePitch = add(new NumberSetting("Dive pitch", 35, 10, 60), "Pitch", "Degrees below the horizon while diving.");
		private final NumberSetting climbPitch = add(new NumberSetting("Climb pitch", 40, 10, 60), "Pitch", "Degrees above the horizon while climbing.");
		private final NumberSetting climbSpeed = add(new NumberSetting("Climb at speed", 1.6, 0.8, 3.0), "Speed", "Switch from diving to climbing at this speed (blocks per tick).");
		private final NumberSetting diveSpeed = add(new NumberSetting("Dive below speed", 0.9, 0.3, 2.5), "Speed", "Switch from climbing back to diving at this speed.");
		private final NumberSetting minHeight = add(new NumberSetting("Min height (blocks)", 12, 3, 60), "Safety", "Climb whenever the ground is closer than this.");
		private final NumberSetting turnSpeed = add(new NumberSetting("Turn speed", 5, 1, 15), "General", "Camera degrees per tick toward the target pitch.");

		private GlideController controller;

		public ElytraGlide() { super("ElytraGlide", Category.MOVEMENT); }

		@Override
		protected boolean persistsEnabled() { return false; }

		@Override
		protected void onEnable() { controller = null; }

		@Override
		protected void onDisable() { RotationController.clear(); } // otherwise the camera keeps easing to the last pitch

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.level == null || mc.screen != null) return;
			if (!p.isFallFlying()) {
				if (controller != null) controller.reset();
				return;
			}
			controller = controllerFor(controller); // also picks up settings changed while gliding
			double speed = p.getDeltaMovement().length();
			boolean low = groundWithin(p, minHeight.asInt());
			float pitch = controller.nextPitch(speed, low);
			RotationController.aimAt(new Rotation(p.getYRot(), pitch), turnSpeed.asFloat());
		}

		private double lastDive, lastClimb, lastUp, lastDown;

		private GlideController controllerFor(GlideController c) {
			if (c != null && lastDive == divePitch.get() && lastClimb == climbPitch.get() && lastUp == climbSpeed.get() && lastDown == diveSpeed.get()) return c;
			lastDive = divePitch.get();
			lastClimb = climbPitch.get();
			lastUp = climbSpeed.get();
			lastDown = diveSpeed.get();
			return new GlideController(divePitch.asFloat(), -climbPitch.asFloat(), climbSpeed.get(), diveSpeed.get());
		}

		private static boolean groundWithin(LocalPlayer p, int blocks) {
			var level = p.level();
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			int x = p.getBlockX(), z = p.getBlockZ();
			for (int dy = 1; dy <= blocks; dy++) {
				int y = p.getBlockY() - dy;
				if (y < level.getMinY()) return false;
				m.set(x, y, z);
				if (!level.getBlockState(m).isAir()) return true;
			}
			return false;
		}
	}

	/** While gliding, fires a firework rocket from your hotbar or offhand whenever you slow down. */
	public static class AutoFirework extends Module {
		private final NumberSetting minSpeed = add(new NumberSetting("Boost below speed", 1.0, 0.3, 2.5), "Trigger", "Use a rocket when your speed (blocks per tick) drops below this.");
		private final NumberSetting interval = add(new NumberSetting("Min interval (ticks)", 40, 10, 200), "Trigger", "Shortest time between rockets.");
		private final BoolSetting restore = add(new BoolSetting("Restore slot afterwards", true), "General", "Return to the slot you held before.");

		private int cooldown;
		private int back = -1;

		public AutoFirework() { super("AutoFirework", Category.MISC); }

		@Override
		protected void onDisable() { back = -1; }

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.gameMode == null) return;
			if (back >= 0) { // second tick: go back to the slot you had
				if (restore.get()) p.getInventory().setSelectedSlot(back);
				back = -1;
			}
			if (cooldown > 0) cooldown--;
			if (cooldown > 0 || mc.screen != null || !p.isFallFlying() || p.getDeltaMovement().length() >= minSpeed.get()) return;
			if (p.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
				mc.gameMode.useItem(p, InteractionHand.OFF_HAND);
				p.swing(InteractionHand.OFF_HAND);
			} else {
				int slot = CombatUtil.hotbarSlot(p, Items.FIREWORK_ROCKET);
				if (slot < 0) return;
				int cur = p.getInventory().getSelectedSlot();
				if (slot != cur) {
					p.getInventory().setSelectedSlot(slot);
					back = cur;
				}
				mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
				p.swing(InteractionHand.MAIN_HAND);
			}
			cooldown = interval.asInt();
		}
	}

	/** Base for the modules you bind to a key: each press uses one item from the hotbar (or offhand), then returns to your slot. */
	private abstract static class KeyUse extends Module {
		private final Item item;
		private final String noun;
		private final BoolSetting restore = add(new BoolSetting("Restore slot afterwards", true), "General", "Return to the slot you held before.");
		private final BoolSetting offhand = add(new BoolSetting("Use offhand copy", true), "General", "If the item is in your offhand, throw that one without switching.");
		private final ActionLimiter limiter = new ActionLimiter(new Random());
		private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 1, 0, 10), "Timing", "Average pause between selecting the item and using it.");
		private int back = -1;
		private int wait;

		KeyUse(String name, Item item, String noun) {
			super(name, Category.MISC);
			this.item = item;
			this.noun = noun;
		}

		@Override
		protected boolean persistsEnabled() { return false; }

		@Override
		protected void onEnable() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.gameMode == null || mc.screen != null) {
				setEnabled(false);
				return;
			}
			if (offhand.get() && p.getOffhandItem().is(item)) {
				mc.gameMode.useItem(p, InteractionHand.OFF_HAND);
				p.swing(InteractionHand.OFF_HAND);
				setEnabled(false);
				return;
			}
			int slot = CombatUtil.hotbarSlot(p, item);
			if (slot < 0) {
				Toasts.show(name(), "No " + noun + " in your hotbar", 0xFFFF5555);
				setEnabled(false);
				return;
			}
			back = p.getInventory().getSelectedSlot();
			if (slot != back) p.getInventory().setSelectedSlot(slot);
			wait = limiter.nextDelayTicks(delay.get(), 0.5, 0);
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.gameMode == null) {
				setEnabled(false);
				return;
			}
			if (wait-- > 0) return;
			if (p.getMainHandItem().is(item)) {
				mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
				p.swing(InteractionHand.MAIN_HAND);
			}
			if (restore.get() && back >= 0) p.getInventory().setSelectedSlot(back);
			back = -1;
			setEnabled(false);
		}
	}

	/** Bind it to a key: each press throws an ender pearl from your hotbar. */
	public static class KeyPearl extends KeyUse {
		public KeyPearl() { super("KeyPearl", Items.ENDER_PEARL, "ender pearl"); }
	}

	/** Bind it to a key: each press throws a wind charge from your hotbar. */
	public static class KeyWindCharge extends KeyUse {
		public KeyWindCharge() { super("KeyWindCharge", Items.WIND_CHARGE, "wind charge"); }
	}
}
