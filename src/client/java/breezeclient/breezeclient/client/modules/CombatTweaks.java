package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Random;

/** Small combat quality-of-life modules; they share this file. */
public final class CombatTweaks {
	private CombatTweaks() {}

	/** Removes the pause vanilla puts on your attack button after a swing at nothing (10 ticks). */
	public static class NoHitDelay extends Module {
		public NoHitDelay() { super("NoHitDelay", Category.COMBAT); }

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.screen != null) return; // vanilla parks the timer at 10000 while a screen is open
			mc.missTime = 0;
		}
	}

	/**
	 * Makes crystal fights faster: the moment you hit an end crystal it is removed on your screen, so you can place and
	 * target the next one without waiting for the server's answer. The server still decides what really happened.
	 */
	public static class CrystalOptimizer extends Module {
		private static CrystalOptimizer instance;

		public CrystalOptimizer() {
			super("CrystalOptimizer", Category.COMBAT);
			instance = this;
		}

		public static void registerEvents() {
			AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
				CrystalOptimizer m = instance;
				if (m != null && m.enabled() && level.isClientSide() && entity instanceof EndCrystal && player == Minecraft.getInstance().player) {
					int id = entity.getId();
					// next client task, after the attack packet for this click has been built and sent
					Minecraft.getInstance().schedule(() -> {
						var lvl = Minecraft.getInstance().level;
						if (lvl != null && lvl.getEntity(id) instanceof EndCrystal) lvl.removeEntity(id, Entity.RemovalReason.KILLED);
					});
				}
				return InteractionResult.PASS;
			});
		}
	}

	/**
	 * Your crosshair keeps seeing players at their standing height while they sneak, crawl or swim, which are the poses
	 * that shrink the box you have to hit. Targeting only: the server uses the real box.
	 */
	public static class StaticHitBoxes extends Module {
		private static StaticHitBoxes instance;
		private static final float STANDING = 1.8f;

		private final NumberSetting extra = add(new NumberSetting("Extra width (blocks)", 0, 0, 0.5), "General", "Additional margin on top of the standing size. Keep it small.");

		public StaticHitBoxes() {
			super("StaticHitBoxes", Category.COMBAT);
			instance = this;
		}

		/** Extra pick radius for an entity, or 0 when this module does not touch it. */
		public static float pickRadius(Entity e) {
			StaticHitBoxes m = instance;
			if (m == null || !m.enabled() || !(e instanceof Player) || e == Minecraft.getInstance().player) return 0;
			float lacking = Math.max(0f, (STANDING - e.getBbHeight()) / 2f);
			return lacking + m.extra.asFloat();
		}
	}

	/** Switches to a spear when you attack something, then back to the slot you had. */
	public static class SpearSwap extends Module {
		private static SpearSwap instance;
		private final NumberSetting restoreDelay = add(new NumberSetting("Switch back after (ticks)", 6, 0, 40), "General", "How long to hold the spear before returning to the previous slot (0 = stay).");
		private final BoolSetting playersOnly = add(new BoolSetting("Players only", false), "General", "Only swap when attacking players.");
		private int back = -1;
		private int restoreTicks;

		public SpearSwap() {
			super("SpearSwap", Category.COMBAT);
			instance = this;
		}

		public static void registerEvents() {
			AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
				if (instance != null && instance.enabled() && level.isClientSide() && player == Minecraft.getInstance().player) instance.onAttack(entity);
				return InteractionResult.PASS;
			});
		}

		private void onAttack(Entity e) {
			LocalPlayer p = Minecraft.getInstance().player;
			if (p == null || !(e instanceof LivingEntity) || (playersOnly.get() && !(e instanceof Player))) return;
			int spear = CombatUtil.hotbarSlot(p, s -> s.is(ItemTags.SPEARS));
			if (spear < 0 || spear == p.getInventory().getSelectedSlot()) return;
			if (back < 0) back = p.getInventory().getSelectedSlot();
			p.getInventory().setSelectedSlot(spear);
			restoreTicks = restoreDelay.asInt();
		}

		@Override
		protected void onTick() {
			LocalPlayer p = Minecraft.getInstance().player;
			if (p == null || back < 0) return;
			if (restoreDelay.asInt() > 0) {
				if (restoreTicks-- <= 0) {
					p.getInventory().setSelectedSlot(back);
					back = -1;
				}
			} else {
				back = -1;
			}
		}

		@Override
		protected void onDisable() { back = -1; }
	}

	/**
	 * Breaks any end crystal your crosshair is on as soon as it is in reach, after a short random delay. It never places
	 * anything and does not need the use key (CrystalMacro does both).
	 */
	public static class AutoHitCrystal extends Module {
		private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 2, 0, 10), "Timing", "Average pause between lining up and hitting.");
		private final BoolSetting needWeapon = add(new BoolSetting("Only with a weapon", false), "General", "Only hit while holding a sword, axe, mace or spear.");
		private final ActionLimiter limiter = new ActionLimiter(new Random());
		private int wait = -1;

		public AutoHitCrystal() { super("AutoHitCrystal", Category.COMBAT); }

		@Override
		protected void onDisable() { wait = -1; }

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.gameMode == null || mc.screen != null) return;
			if (!(mc.hitResult instanceof EntityHitResult eh) || !(eh.getEntity() instanceof EndCrystal crystal)) {
				wait = -1;
				return;
			}
			if (needWeapon.get()) {
				var h = p.getMainHandItem();
				if (!(h.is(ItemTags.SWORDS) || h.is(ItemTags.AXES) || h.is(ItemTags.SPEARS) || h.is(net.minecraft.world.item.Items.MACE))) return;
			}
			if (wait < 0) wait = limiter.nextDelayTicks(delay.get(), 1, 0);
			if (wait-- > 0) return;
			wait = -1;
			CombatUtil.attack("AutoHitCrystal", crystal);
		}
	}
}
