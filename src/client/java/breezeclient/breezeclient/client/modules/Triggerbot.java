package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Random;

/** Attacks the moment your crosshair is on a valid target and the weapon is recharged, after a small random reaction delay. */
public class Triggerbot extends Module {
	private final BoolSetting players = add(new BoolSetting("Players", true), "Targets", "Attack players.");
	private final BoolSetting mobs = add(new BoolSetting("Mobs", false), "Targets", "Attack mobs and animals.");
	private final BoolSetting ignoreStaff = add(new BoolSetting("Ignore listed staff", true), "Targets", "Never attack players on the staff list.");
	private final NumberSetting cooldown = add(new NumberSetting("Min attack charge", 0.95, 0.1, 1), "Timing", "Wait until the attack bar is at least this full.");
	private final NumberSetting reaction = add(new NumberSetting("Reaction (ticks)", 2, 0, 10), "Timing", "Average delay between lining up and hitting.");
	private final BoolSetting critOnly = add(new BoolSetting("Wait for critical hits", false), "Timing", "Only hit while falling, for critical damage.");

	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private int wait = -1;

	public Triggerbot() { super("Triggerbot", Category.COMBAT); }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.gameMode == null || mc.screen != null) return;
		if (!(mc.hitResult instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity t) || !t.isAlive()) {
			wait = -1;
			return;
		}
		if (t instanceof Player pl) {
			if (!players.get() || (ignoreStaff.get() && StaffTracker.profile().isStaff(StaffTracker.nameOf(pl)))) return;
		} else if (!mobs.get()) return;
		if (p.getAttackStrengthScale(0.5f) < cooldown.get()) return;
		if (critOnly.get() && (p.onGround() || p.fallDistance <= 0)) return;
		if (wait < 0) wait = limiter.nextDelayTicks(reaction.get(), 1, 0);
		if (wait-- > 0) return;
		wait = -1;
		CombatUtil.attack("Triggerbot", t);
	}
}
