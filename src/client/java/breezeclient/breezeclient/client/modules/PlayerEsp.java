package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/** Boxes, tracers and through-walls nametags for other players. */
public class PlayerEsp extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 256, 16, 512));
	private final BoolSetting boxes = add(new BoolSetting("Boxes", true));
	private final BoolSetting tracers = add(new BoolSetting("Tracers", true));
	private final BoolSetting nametags = add(new BoolSetting("Nametags", true));
	private final BoolSetting health = add(new BoolSetting("Health", true));
	private final BoolSetting distance = add(new BoolSetting("Distance", true));
	private final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFFFF));
	private final ColorSetting staffColor = add(new ColorSetting("Staff color", 0xFFFF2222));

	public PlayerEsp() { super("PlayerESP", Category.RENDER); }

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		Camera cam = mc.gameRenderer.getMainCamera();
		Vec3 cp = cam.position();
		Vector3fc fwd = cam.forwardVector();
		Vec3 start = new Vec3(cp.x + fwd.x() * 0.3, cp.y + fwd.y() * 0.3, cp.z + fwd.z() * 0.3);
		var profile = StaffTracker.profile();
		for (Player p : mc.level.players()) {
			if (p == mc.player || p == mc.getCameraEntity()) continue;
			double d = p.distanceTo(mc.player);
			if (d > range.get()) continue;
			String name = StaffTracker.nameOf(p);
			int c = profile.isStaff(name) ? staffColor.get() : color.get();
			AABB b = p.getBoundingBox();
			if (boxes.get()) f.drawBox(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, c);
			if (tracers.get()) f.drawLine(start.x, start.y, start.z, p.getX(), p.getY() + p.getBbHeight() / 2, p.getZ(), c);
			if (nametags.get()) {
				StringBuilder sb = new StringBuilder(name);
				if (health.get()) sb.append(' ').append(String.format("%.1f", p.getHealth() + p.getAbsorptionAmount())).append('♥');
				if (distance.get()) sb.append(' ').append((int) d).append('m');
				f.drawText(p.getX(), b.maxY + 0.45, p.getZ(), sb.toString(), c);
			}
		}
	}
}
