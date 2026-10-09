package breezeclient.breezeclient.client.humanize;

import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.core.humanize.Rotations;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Applies eased, mouse-quantized camera movement toward a target every client tick. */
public final class RotationController {
	private static final Random RNG = new Random();
	private static Rotation target;
	private static float maxStep = 20f;

	private RotationController() {}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(RotationController::tick);
	}

	/** Start (or retarget) a smooth turn toward the given absolute yaw/pitch. */
	public static void aimAt(Rotation t, float maxStepDegrees) {
		target = t;
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

	public static void clear() { target = null; }

	public static boolean busy() { return target != null; }

	public static Rotation current() {
		LocalPlayer p = Minecraft.getInstance().player;
		return p == null ? new Rotation(0, 0) : new Rotation(p.getYRot(), p.getXRot());
	}

	private static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || target == null) return;
		float q = Rotations.quantum(mc.options.sensitivity().get());
		Rotation cur = new Rotation(p.getYRot(), p.getXRot());
		Rotation next = Rotations.step(cur, target, maxStep, q, RNG);
		float dy = Math.abs(Rotations.wrapDegrees(target.yaw() - next.yaw()));
		float dp = Math.abs(target.pitch() - next.pitch());
		p.setYRot(next.yaw());
		p.setXRot(next.pitch());
		if (dy < q && dp < q) target = null;
	}
}
