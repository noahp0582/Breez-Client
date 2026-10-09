package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.GuiGraphics;

/** Replaces the vanilla crosshair with a configurable one. */
public class CustomCrosshair extends Module {
	public enum Shape { CROSS, DOT, SQUARE, T_SHAPE }

	private static CustomCrosshair instance;
	private final EnumSetting<Shape> shape = add(new EnumSetting<>("Shape", Shape.CROSS), "Look", "Crosshair shape.");
	private final NumberSetting size = add(new NumberSetting("Size", 5, 1, 20), "Look", "Arm length in pixels.");
	private final NumberSetting gap = add(new NumberSetting("Gap", 2, 0, 10), "Look", "Empty space at the center.");
	private final NumberSetting thickness = add(new NumberSetting("Thickness", 1, 1, 5), "Look", "Line width in pixels.");
	private final BoolSetting outline = add(new BoolSetting("Outline", true), "Look", "Dark outline so it shows on any background.");
	private final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFFFF), "Look", "Crosshair color.");

	public CustomCrosshair() {
		super("CustomCrosshair", Category.RENDER);
		instance = this;
	}

	public static void registerEvents() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, dt) -> {
			if (instance != null && instance.enabled()) instance.draw(g);
			else original.render(g, dt);
		});
	}

	private void rect(GuiGraphics g, int x, int y, int w, int h, int c) {
		if (outline.get()) g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xCC000000);
		g.fill(x, y, x + w, y + h, c);
	}

	private void draw(GuiGraphics g) {
		int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
		int t = thickness.asInt(), s = size.asInt(), gp = gap.asInt(), c = color.get();
		int half = t / 2;
		switch (shape.get()) {
			case DOT -> rect(g, cx - half, cy - half, t, t, c);
			case SQUARE -> {
				int r = gp + s;
				g.fill(cx - r, cy - r, cx + r + 1, cy - r + t, c);
				g.fill(cx - r, cy + r + 1 - t, cx + r + 1, cy + r + 1, c);
				g.fill(cx - r, cy - r, cx - r + t, cy + r + 1, c);
				g.fill(cx + r + 1 - t, cy - r, cx + r + 1, cy + r + 1, c);
			}
			default -> {
				rect(g, cx - gp - s, cy - half, s, t, c);
				rect(g, cx + gp + 1, cy - half, s, t, c);
				rect(g, cx - half, cy + gp + 1, t, s, c);
				if (shape.get() == Shape.CROSS) rect(g, cx - half, cy - gp - s, t, s, c);
			}
		}
	}
}
