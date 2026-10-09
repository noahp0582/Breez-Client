package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Detached camera. The real player stands still and sends nothing different to the server. */
public class Freecam extends Module {
	private static Freecam instance;
	private static RemotePlayer cam;

	private final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.1, 5.0));

	public Freecam() {
		super("Freecam", Category.RENDER);
		instance = this;
	}

	public static boolean active() { return instance != null && instance.enabled() && cam != null; }

	/** Same math as Entity.turn, applied to the camera. */
	public static void turn(double yRot, double xRot) {
		if (cam == null) return;
		float dx = (float) xRot * 0.15f, dy = (float) yRot * 0.15f;
		cam.setXRot(Mth.clamp(cam.getXRot() + dx, -90f, 90f));
		cam.setYRot(cam.getYRot() + dy);
		cam.xRotO = Mth.clamp(cam.xRotO + dx, -90f, 90f);
		cam.yRotO += dy;
	}

	@Override
	protected boolean persistsEnabled() { return false; }

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			setEnabled(false);
			return;
		}
		cam = new RemotePlayer(mc.level, p.getGameProfile());
		cam.noPhysics = true;
		cam.setPos(p.getX(), p.getY(), p.getZ());
		cam.setYRot(p.getYRot());
		cam.setXRot(p.getXRot());
		cam.xo = cam.getX();
		cam.yo = cam.getY();
		cam.zo = cam.getZ();
		cam.xRotO = cam.getXRot();
		cam.yRotO = cam.getYRot();
		mc.setCameraEntity(cam);
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.setCameraEntity(mc.player);
		cam = null;
	}

	/** Called on disconnect/world change so a stale camera entity is never kept. */
	public static void reset() {
		if (instance != null && instance.enabled()) instance.setEnabled(false);
		cam = null;
	}

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (cam == null || mc.player == null || mc.level == null || cam.level() != mc.level || mc.getCameraEntity() != cam) {
			setEnabled(false);
			return;
		}
		var o = mc.options;
		double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
		double s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
		double v = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
		if (mc.screen != null) f = s = v = 0;

		cam.xo = cam.getX();
		cam.yo = cam.getY();
		cam.zo = cam.getZ();
		float yaw = cam.getYRot() * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		double sx = Mth.cos(yaw), sz = Mth.sin(yaw);
		Vec3 move = new Vec3(fx * f + sx * s, v, fz * f + sz * s);
		if (move.lengthSqr() > 0) move = move.normalize().scale(speed.get());
		cam.setPos(cam.getX() + move.x, cam.getY() + move.y, cam.getZ() + move.z);
	}
}
