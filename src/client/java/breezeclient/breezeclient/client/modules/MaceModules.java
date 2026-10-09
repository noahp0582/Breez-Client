package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Random;

/** Mace, axe and shield-break helpers. Each is a small module; they share this file. */
public final class MaceModules {
	private MaceModules() {}

	/** Switches to a mace when you attack something, then back to the previous slot. */
	public static class MaceSwap extends Module {
		private static MaceSwap instance;
		private final NumberSetting restoreDelay = add(new NumberSetting("Switch back after (ticks)", 6, 0, 40), "General", "How long to hold the mace before returning to the previous slot (0 = stay).");
		private final BoolSetting playersOnly = add(new BoolSetting("Players only", false), "General", "Only swap when attacking players.");
		private int back = -1, restoreTicks;

		public MaceSwap() {
			super("MaceSwap", Category.COMBAT);
			instance = this;
		}

		public static void registerEvents() {
			AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
				if (instance != null && instance.enabled() && level.isClientSide()) instance.onAttack(entity);
				return InteractionResult.PASS;
			});
		}

		private void onAttack(Entity e) {
			var p = Minecraft.getInstance().player;
			if (p == null || !(e instanceof LivingEntity) || (playersOnly.get() && !(e instanceof Player))) return;
			int mace = CombatUtil.hotbarSlot(p, Items.MACE);
			if (mace < 0 || mace == p.getInventory().getSelectedSlot()) return;
			if (back < 0) back = p.getInventory().getSelectedSlot();
			p.getInventory().setSelectedSlot(mace);
			restoreTicks = restoreDelay.asInt();
		}

		@Override
		protected void onTick() {
			var p = Minecraft.getInstance().player;
			if (p == null || back < 0) return;
			if (restoreDelay.asInt() > 0 && restoreTicks-- <= 0) {
				p.getInventory().setSelectedSlot(back);
				back = -1;
			} else if (restoreDelay.asInt() == 0) {
				back = -1;
			}
		}

		@Override
		protected void onDisable() { back = -1; }
	}

	/** While falling onto a target, equips the mace, lines up and smashes for the big fall bonus. */
	public static class MaceBomber extends Module {
		private final NumberSetting minFall = add(new NumberSetting("Min fall (blocks)", 4, 1.5, 30), "Trigger", "Only smash after falling at least this far.");
		private final NumberSetting range = add(new NumberSetting("Target range", 4.2, 2, 6), "Trigger", "Maximum distance to the target when smashing.");
		private final BoolSetting playersOnly = add(new BoolSetting("Players only", true), "Trigger", "Ignore mobs.");
		private final BoolSetting aim = add(new BoolSetting("Aim at target", true), "Trigger", "Turn toward the target while falling (eased, humanized).");
		private final BoolSetting restore = add(new BoolSetting("Restore slot afterwards", true), "Trigger", "Return to the slot you were holding.");

		private final ActionLimiter limiter = new ActionLimiter(new Random());
		private int back = -1;
		private int wait;

		public MaceBomber() { super("MaceBomber", Category.COMBAT); }

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			var p = mc.player;
			if (p == null || mc.gameMode == null || mc.level == null || mc.screen != null) return;
			if (p.onGround()) {
				if (back >= 0 && restore.get()) p.getInventory().setSelectedSlot(back);
				back = -1;
				return;
			}
			if (p.fallDistance < minFall.get()) return;
			int mace = CombatUtil.hotbarSlot(p, Items.MACE);
			if (mace < 0) return;
			LivingEntity target = null;
			double best = range.get() + 3;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || le.isSpectator()) continue;
				if (playersOnly.get() && !(e instanceof Player pl && !StaffTracker.profile().isStaff(StaffTracker.nameOf(pl)))) continue;
				if (e.getY() > p.getY() + 0.5) continue;
				double d = e.distanceTo(p);
				if (d < best) {
					best = d;
					target = le;
				}
			}
			if (target == null) return;
			if (p.getInventory().getSelectedSlot() != mace) {
				if (back < 0) back = p.getInventory().getSelectedSlot();
				p.getInventory().setSelectedSlot(mace);
				wait = limiter.nextDelayTicks(1, 0.5, 0);
				return;
			}
			if (aim.get()) RotationController.aimAtPoint(CombatUtil.aimPoint(target), 12f);
			if (wait-- > 0) return;
			if (best <= range.get() && mc.hitResult instanceof EntityHitResult eh && eh.getEntity() == target) {
				CombatUtil.attack("MaceBomber", target);
			}
		}

		@Override
		protected void onDisable() { back = -1; }
	}

	/** When the target under your crosshair is blocking with a shield, switches to an axe (and optionally hits). */
	public static class ShieldBreaker extends Module {
		private final BoolSetting attack = add(new BoolSetting("Attack automatically", false), "General", "Also hit once the axe is out (otherwise you hit yourself).");
		private final BoolSetting restore = add(new BoolSetting("Restore slot afterwards", true), "General", "Return to your previous slot when the target stops blocking.");
		private final NumberSetting delay = add(new NumberSetting("Reaction (ticks)", 2, 0, 10), "General", "Average delay before reacting.");

		private final ActionLimiter limiter = new ActionLimiter(new Random());
		private int back = -1, wait = -1;

		public ShieldBreaker() { super("ShieldBreaker", Category.COMBAT); }

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			var p = mc.player;
			if (p == null || mc.gameMode == null || mc.screen != null) return;
			boolean blocking = mc.hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le && le.isBlocking();
			if (!blocking) {
				wait = -1;
				if (back >= 0 && restore.get()) p.getInventory().setSelectedSlot(back);
				back = -1;
				return;
			}
			int axe = CombatUtil.hotbarSlot(p, s -> s.is(ItemTags.AXES));
			if (axe < 0) return;
			if (wait < 0) wait = limiter.nextDelayTicks(delay.get(), 1, 0);
			if (wait-- > 0) return;
			if (p.getInventory().getSelectedSlot() != axe) {
				if (back < 0) back = p.getInventory().getSelectedSlot();
				p.getInventory().setSelectedSlot(axe);
				return;
			}
			if (attack.get() && p.getAttackStrengthScale(0.5f) >= 0.95f) {
				CombatUtil.attack("ShieldBreaker", ((EntityHitResult) mc.hitResult).getEntity());
			}
		}

		@Override
		protected void onDisable() {
			back = -1;
			wait = -1;
		}
	}
}
