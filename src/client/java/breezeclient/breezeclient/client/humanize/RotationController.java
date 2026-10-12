package breezeclient.breezeclient.client.humanize;

import breezeclient.breezeclient.core.humanize.AimMotion;
import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.core.humanize.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Turns the camera toward a target for the modules that aim on their own. It moves every rendered frame (not twenty
 * times a second), so the turn is as smooth on screen as a hand-moved mouse: see {@link AimMotion} for the shape.
 */
public final class RotationController {
	private static final AimMotion MOTION = new AimMotion(new Random());
	private static Rotation target;
	private static float maxStep = 20f;
	private static long lastFrame;

	private RotationController() {}

	public static void register() {}

	/** Start (or retarget) a smooth turn toward the given absolute yaw/pitch. {@code maxStepDegrees} is the nominal speed in degrees per tick. */
	public static void aimAt(Rotation t, float maxStepDegrees) {
		target = new Rotation(t.yaw(), Math.max(-90f, Math.min(90f, t.pitch())));
		maxStep = maxStepDegrees;
	}

	public static void aimAtPoint(Vec3 point, float maxStepDegrees) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return;
		aimAt(rotationTo(p.getEyePosition(), point), maxStepDegrees);
	}

	public static Rotation rotationTo(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
		return new Rotation(yaw, pitch);
	}

	public static void clear() {
		target = null;
		MOTION.reset();
	}

	public static boolean busy() { return target != null; }

	public static Rotation current() {
		LocalPlayer p = Minecraft.getInstance().player;
		return p == null ? new Rotation(0, 0) : new Rotation(p.getYRot(), p.getXRot());
	}

	/** Called once per rendered frame, right after the game has applied the real mouse. */
	public static void frame() {
		long now = System.nanoTime();
		double dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1e9;
		lastFrame = now;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || target == null) {
			if (target == null && MOTION.active()) MOTION.reset();
			return;
		}
		if (mc.isPaused()) return;
		float q = Rotations.quantum(mc.options.sensitivity().get());
		Rotation cur = new Rotation(p.getYRot(), p.getXRot());
		double degPerSec = maxStep * 20.0 * breezeclient.breezeclient.client.gui.GuiState.CLIENT.rotationSpeed.get();
		Rotation next = MOTION.advance(cur, target, degPerSec, dt, q);
		p.setYRot(next.yaw());
		p.setXRot(next.pitch());
		if (AimMotion.arrived(next, target, q)) {
			target = null;
			MOTION.reset();
		}
	}
}
