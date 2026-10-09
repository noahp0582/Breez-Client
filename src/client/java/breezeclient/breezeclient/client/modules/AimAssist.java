package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Gently pulls your aim toward the closest target inside a view cone, using the same eased turning as real mouse input. */
public class AimAssist extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 4.5, 2, 8), "Targeting", "Maximum distance to a target.");
	private final NumberSetting fov = add(new NumberSetting("FOV", 70, 10, 180), "Targeting", "Only targets within this many degrees of where you look.");
	private final BoolSetting players = add(new BoolSetting("Players", true), "Targeting", "Assist toward players.");
	private final BoolSetting mobs = add(new BoolSetting("Mobs", false), "Targeting", "Assist toward mobs and animals.");
	private final BoolSetting ignoreStaff = add(new BoolSetting("Ignore listed staff", true), "Targeting", "Never aim at players on the staff list.");
	private final BoolSetting needLos = add(new BoolSetting("Require line of sight", true), "Targeting", "Do not aim through walls.");
	private final NumberSetting speed = add(new NumberSetting("Strength (degrees/tick)", 4, 0.5, 15), "Feel", "Maximum turn per tick. Lower feels more natural.");
	private final BoolSetting onlyAttacking = add(new BoolSetting("Only while holding attack", true), "Feel", "Assist only while the attack button is down.");
	private final BoolSetting aimChest = add(new BoolSetting("Aim at chest", true), "Feel", "Aim at the chest instead of the eyes.");

	public AimAssist() { super("AimAssist", Category.COMBAT); }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.level == null || mc.screen != null) return;
		if (onlyAttacking.get() && !mc.options.keyAttack.isDown()) return;
		LivingEntity best = null;
		double bestAngle = fov.get();
		var staff = StaffTracker.profile();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || le.isSpectator()) continue;
			if (e instanceof Player pl) {
				if (!players.get() || (ignoreStaff.get() && staff.isStaff(StaffTracker.nameOf(pl)))) continue;
			} else if (!mobs.get()) continue;
			if (e.distanceTo(p) > range.get() || (needLos.get() && !p.hasLineOfSight(e))) continue;
			double a = CombatUtil.angleTo(p, CombatUtil.aimPoint(le));
			if (a < bestAngle) {
				bestAngle = a;
				best = le;
			}
		}
		if (best == null) return;
		var point = aimChest.get() ? CombatUtil.aimPoint(best) : best.getEyePosition();
		RotationController.aimAtPoint(point, speed.asFloat());
	}
}
