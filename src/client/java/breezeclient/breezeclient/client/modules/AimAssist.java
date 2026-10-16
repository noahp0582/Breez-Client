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
import net.minecraft.world.phys.Vec3;

/**
 * Nudges your aim toward the target nearest your crosshair. It adds a small correction (a fraction of the angle left,
 * capped, faded out near the crosshair) on top of what your mouse does, so you stay in control and it never snaps.
 * The target is picked each tick; the camera moves every rendered frame so the pull is smooth at any frame rate.
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
	private final BoolSetting onlyMoving = add(new BoolSetting("Only while I move the mouse", false), "Feel", "Assist only while you are turning the camera yourself.");
	private final BoolSetting weaponOnly = add(new BoolSetting("Only with a weapon", true), "Feel", "Assist only while holding a sword, axe, mace or trident.");
	private final BoolSetting aimChest = add(new BoolSetting("Aim at chest", true), "Feel", "Aim at the chest instead of the eyes.");

	/** How long after your own mouse movement "Only while I move the mouse" keeps assisting. */
	private static final long USER_TURN_GRACE_NANOS = 150_000_000L;
	/** Longest frame we act on, in ticks; a hitch should not turn into one big jump. */
	private static final double MAX_FRAME_TICKS = 2.0;
	private static AimAssist instance;

	private LivingEntity current;
	/** The target the sub-step carries were collected for; a new target starts from zero. */
	private LivingEntity carryTarget;
	private boolean haveExpected;
	private float expectedYaw, expectedPitch;
	private long lastUserTurn;
	private final float[] yawCarry = {0f}, pitchCarry = {0f};

	public AimAssist() {
		super("AimAssist", Category.COMBAT);
		instance = this;
	}

	@Override
	protected void onDisable() {
		reset();
	}

	private void reset() {
		current = carryTarget = null;
		haveExpected = false;
		yawCarry[0] = pitchCarry[0] = 0f;
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

	/** Assist only applies when this holds; checked on ticks (to pick a target) and on frames (to turn). */
	private boolean gatesOpen(Minecraft mc, LocalPlayer p) {
		if (p == null || mc.level == null || mc.screen != null || mc.isPaused() || mc.getCameraEntity() != p) return false;
		if (onlyAttacking.get() && !mc.options.keyAttack.isDown()) return false;
		return !weaponOnly.get() || weapon(p.getMainHandItem());
	}

	/** Target selection runs twenty times a second; the camera itself moves in {@link #frame()}. */
	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (!gatesOpen(mc, p)) {
			reset();
			return;
		}

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
	}

	/** Called once per rendered frame, right after the game has applied the real mouse. */
	public static void frame() {
		AimAssist a = instance;
		if (a != null && a.enabled()) a.onFrame();
	}

	private void onFrame() {
		long now = System.nanoTime();
		double dtTicks = Math.min(MAX_FRAME_TICKS, RotationController.frameSeconds() * 20.0);

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (!gatesOpen(mc, p) || RotationController.busy()) {
			haveExpected = false;
			return;
		}

		// did the player turn the camera themselves since our last frame? The first frame after a pause only sets the
		// baseline, so opening a gate (pressing attack, another module finishing its aim) is not mistaken for a turn.
		if (haveExpected && (Math.abs(Rotations.wrapDegrees(p.getYRot() - expectedYaw)) > 0.01f
			|| Math.abs(p.getXRot() - expectedPitch) > 0.01f)) {
			lastUserTurn = now;
		}
		expectedYaw = p.getYRot();
		expectedPitch = p.getXRot();
		haveExpected = true;

		LivingEntity t = current;
		if (t != carryTarget) {
			carryTarget = t;
			yawCarry[0] = pitchCarry[0] = 0f;
		}
		if (t == null || dtTicks <= 0) return;
		if (onlyMoving.get() && now - lastUserTurn > USER_TURN_GRACE_NANOS) return;
		// already on target: leave the camera alone
		if (mc.hitResult instanceof EntityHitResult eh && eh.getEntity() == t) return;

		// aim where the target is drawn this frame, not where it was on the last tick
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 pos = t.getPosition(pt);
		double h = aimChest.get() ? t.getBbHeight() * 0.65 : t.getEyeHeight();
		Rotation want = RotationController.rotationTo(p.getEyePosition(pt), pos.add(0, h, 0));

		AimAssistMath.Delta d = AimAssistMath.frameStep(p.getYRot(), p.getXRot(), want.yaw(), want.pitch(), smoothing.get() / 100.0,
			maxSpeed.asFloat(), dtTicks, deadZone.asFloat(), vertical.asFloat() / 100f, Rotations.quantum(mc.options.sensitivity().get()),
			yawCarry, pitchCarry);
		if (d.isZero()) return;
		p.setYRot(p.getYRot() + d.yaw());
		p.setXRot(Math.max(-90f, Math.min(90f, p.getXRot() + d.pitch())));
		expectedYaw = p.getYRot();
		expectedPitch = p.getXRot();
	}
}
