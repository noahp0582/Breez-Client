package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.AimAssistMath;
import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.core.humanize.Rotations;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Nudges your aim toward the target nearest your crosshair. It adds a small correction each tick (a fraction of the angle
 * left, capped, faded out near the crosshair) on top of what your mouse does, so you stay in control and it never snaps.
 */
public class AimAssist extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 4.5, 2, 8), "Targeting", "Maximum distance to a target.");
	private final NumberSetting fov = add(new NumberSetting("FOV", 60, 10, 180), "Targeting", "Only targets within this many degrees of where you look.");
	private final BoolSetting players = add(new BoolSetting("Players", true), "Targeting", "Assist toward players.");
	private final BoolSetting mobs = add(new BoolSetting("Mobs", false), "Targeting", "Assist toward mobs and animals.");
	private final BoolSetting ignoreStaff = add(new BoolSetting("Ignore listed staff", true), "Targeting", "Never aim at players on the staff list.");
	private final BoolSetting ignoreFriends = add(new BoolSetting("Ignore friends", true), "Targeting", "Never aim at players on your friends list.");
	private final BoolSetting needLos = add(new BoolSetting("Require line of sight", true), "Targeting", "Do not aim through walls.");
	private final NumberSetting smoothing = add(new NumberSetting("Smoothing (% of angle per tick)", 14, 2, 60), "Feel",
		"How much of the remaining angle is corrected each tick. Lower is gentler.");
	private final NumberSetting maxSpeed = add(new NumberSetting("Max speed (degrees/tick)", 2.5, 0.5, 12), "Feel", "Hard cap on one tick's correction.");
	private final NumberSetting deadZone = add(new NumberSetting("Dead zone (degrees)", 1.2, 0, 6), "Feel",
		"No assist this close to the target, so it never fights you for the last bit.");
	private final NumberSetting vertical = add(new NumberSetting("Vertical assist (%)", 40, 0, 100), "Feel", "Strength of the up/down correction; vertical pulls feel worse than sideways.");
	private final BoolSetting onlyAttacking = add(new BoolSetting("Only while holding attack", true), "Feel", "Assist only while the attack button is down.");
	private final BoolSetting onlyMoving = add(new BoolSetting("Only while I move the mouse", false), "Feel", "Assist only on ticks where you turned the camera yourself.");
	private final BoolSetting weaponOnly = add(new BoolSetting("Only with a weapon", true), "Feel", "Assist only while holding a sword, axe, mace or trident.");
	private final BoolSetting aimChest = add(new BoolSetting("Aim at chest", true), "Feel", "Aim at the chest instead of the eyes.");

	private LivingEntity current;
	private boolean haveExpected;
	private float expectedYaw, expectedPitch;

	public AimAssist() { super("AimAssist", Category.COMBAT); }

	@Override
	protected void onDisable() {
		current = null;
		haveExpected = false;
	}

	private static boolean weapon(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT);
	}

	private boolean eligible(LocalPlayer p, Entity e) {
		if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || le.isSpectator()) return false;
		if (e instanceof Player pl) {
			String name = StaffTracker.nameOf(pl);
			if (!players.get()) return false;
			if (ignoreStaff.get() && StaffTracker.profile().isStaff(name)) return false;
			if (ignoreFriends.get() && Friends.is(name)) return false;
		} else if (!mobs.get()) {
			return false;
		}
		return true;
	}

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.screen != null || mc.getCameraEntity() != p) return;
		if (onlyAttacking.get() && !mc.options.keyAttack.isDown()) {
			current = null;
			haveExpected = false;
			return;
		}
		if (weaponOnly.get() && !weapon(p.getMainHandItem())) return;

		// did the player turn the camera themselves since our last correction?
		boolean userMoved = !haveExpected
			|| Math.abs(Rotations.wrapDegrees(p.getYRot() - expectedYaw)) > 0.05f || Math.abs(p.getXRot() - expectedPitch) > 0.05f;
		haveExpected = false;
		if (onlyMoving.get() && !userMoved) return;

		// keep the current target while it stays reasonable, so the assist does not flip between two players
		if (current != null) {
			boolean ok = eligible(p, current) && current.distanceTo(p) <= range.get() * 1.15
				&& CombatUtil.angleTo(p, CombatUtil.aimPoint(current)) <= fov.get() * 1.3 && (!needLos.get() || p.hasLineOfSight(current));
			if (!ok) current = null;
		}
		if (current == null) {
			double bestAngle = fov.get();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (!eligible(p, e) || e.distanceTo(p) > range.get() || (needLos.get() && !p.hasLineOfSight(e))) continue;
				double a = CombatUtil.angleTo(p, CombatUtil.aimPoint((LivingEntity) e));
				if (a < bestAngle) {
					bestAngle = a;
					current = (LivingEntity) e;
				}
			}
		}
		if (current == null) return;
		// already on target: leave the camera alone
		if (mc.hitResult instanceof EntityHitResult eh && eh.getEntity() == current) return;

		var point = aimChest.get() ? CombatUtil.aimPoint(current) : current.getEyePosition();
		Rotation want = RotationController.rotationTo(p.getEyePosition(), point);
		float q = Rotations.quantum(mc.options.sensitivity().get());
		AimAssistMath.Delta d = AimAssistMath.step(p.getYRot(), p.getXRot(), want.yaw(), want.pitch(), smoothing.get() / 100.0,
			maxSpeed.asFloat(), deadZone.asFloat(), vertical.asFloat() / 100f, q);
		if (!d.isZero()) {
			p.setYRot(p.getYRot() + d.yaw());
			p.setXRot(Math.max(-90f, Math.min(90f, p.getXRot() + d.pitch())));
		}
		expectedYaw = p.getYRot();
		expectedPitch = p.getXRot();
		haveExpected = true;
	}
}
