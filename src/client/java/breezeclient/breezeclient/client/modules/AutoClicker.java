package breezeclient.breezeclient.client.modules;

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

/** Attacks the entity under your crosshair at a randomized click rate. */
public class AutoClicker extends Module {
	private final NumberSetting minCps = add(new NumberSetting("Min CPS", 8, 1, 20), "Rate", "Slowest clicks per second.");
	private final NumberSetting maxCps = add(new NumberSetting("Max CPS", 12, 1, 20), "Rate", "Fastest clicks per second.");
	private final BoolSetting holdOnly = add(new BoolSetting("Only while holding attack", true), "Behavior", "Click only while you hold the attack button.");
	private final BoolSetting respectCooldown = add(new BoolSetting("Wait for attack cooldown", true), "Behavior", "Do not swing before the weapon has recharged (full-damage hits).");
	private final BoolSetting playersOnly = add(new BoolSetting("Players only", false), "Behavior", "Ignore mobs and animals.");

	private final Random rng = new Random();
	private final ActionLimiter limiter = new ActionLimiter(rng);
	private int wait;

	public AutoClicker() { super("AutoClicker", Category.COMBAT); }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.gameMode == null || mc.screen != null) return;
		if (wait > 0) {
			wait--;
			return;
		}
		if (holdOnly.get() && !mc.options.keyAttack.isDown()) return;
		if (!(mc.hitResult instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity target) || !target.isAlive()) return;
		if (playersOnly.get() && !(target instanceof Player)) return;
		if (respectCooldown.get() && p.getAttackStrengthScale(0.5f) < 0.95f) return;
		if (!CombatUtil.attack("AutoClicker", target)) return;
		double lo = Math.min(minCps.get(), maxCps.get()), hi = Math.max(minCps.get(), maxCps.get());
		double cps = lo + rng.nextDouble() * (hi - lo);
		wait = Math.max(0, limiter.nextDelayTicks(20.0 / cps - 1, 0.6, 0));
	}
}
