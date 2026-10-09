package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.gui.ColorMath;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** A ring that expands and fades where you jump. */
public class JumpCircles extends Module {
	private record Ring(double x, double y, double z, long born) {}

	private final NumberSetting seconds = add(new NumberSetting("Lifetime (s)", 1.5, 0.3, 5), "General", "How long a ring lasts.");
	private final NumberSetting radius = add(new NumberSetting("Max radius", 1.6, 0.5, 4), "General", "Radius the ring grows to.");
	private final ColorSetting color = add(new ColorSetting("Color", 0xFF913DE2), "General", "Ring color.");

	private final List<Ring> rings = new ArrayList<>();
	private boolean wasOnGround = true;

	public JumpCircles() { super("JumpCircles", Category.RENDER); }

	@Override
	protected void onTick() {
		var p = Minecraft.getInstance().player;
		if (p == null) return;
		boolean ground = p.onGround();
		if (wasOnGround && !ground && p.getDeltaMovement().y > 0.2) rings.add(new Ring(p.getX(), p.getY() + 0.02, p.getZ(), System.currentTimeMillis()));
		wasOnGround = ground;
		long now = System.currentTimeMillis();
		rings.removeIf(r -> now - r.born() > seconds.get() * 1000);
	}

	@Override
	protected void onRender(RenderFrame f) {
		long now = System.currentTimeMillis();
		for (Ring r : rings) {
			double t = (now - r.born()) / (seconds.get() * 1000);
			double rad = 0.3 + (radius.get() - 0.3) * (1 - Math.pow(1 - t, 3));
			int c = ColorMath.fade(color.get() | 0xFF000000, 1 - t);
			int seg = 28;
			for (int i = 0; i < seg; i++) {
				double a1 = i * 2 * Math.PI / seg, a2 = (i + 1) * 2 * Math.PI / seg;
				f.drawLine(r.x() + Math.cos(a1) * rad, r.y(), r.z() + Math.sin(a1) * rad, r.x() + Math.cos(a2) * rad, r.y(), r.z() + Math.sin(a2) * rad, c);
			}
		}
	}
}
