package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Look around in third person without turning your character. Works best bound to a key with "Toggle on bind release". */
public class FreeLook extends Module {
	private static FreeLook instance;
	private static float yaw, pitch;
	private CameraType previous = CameraType.FIRST_PERSON;

	public FreeLook() {
		super("FreeLook", Category.RENDER);
		instance = this;
	}

	public static boolean active() { return instance != null && instance.enabled() && !Freecam.active(); }

	public static float yaw() { return yaw; }

	public static float pitch() { return pitch; }

	public static void turn(double dYaw, double dPitch) {
		yaw += (float) dYaw * 0.15f;
		pitch = Mth.clamp(pitch + (float) dPitch * 0.15f, -90f, 90f);
	}

	@Override
	protected boolean persistsEnabled() { return false; }

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			setEnabled(false);
			return;
		}
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		previous = mc.options.getCameraType();
		if (previous.isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options != null) mc.options.setCameraType(previous);
	}
}
