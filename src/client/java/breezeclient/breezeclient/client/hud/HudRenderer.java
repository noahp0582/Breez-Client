package breezeclient.breezeclient.client.hud;

import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HudRenderer {
	private HudRenderer() {}

	public static void register(ModuleManager modules) {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath("breez-client", "hud"),
			(GuiGraphics g, DeltaTracker dt) -> render(g, modules));
	}

	private static void render(GuiGraphics g, ModuleManager modules) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) return;
		Font font = mc.font;
		g.drawString(font, "Breez", 4, 4, 0xFF55FFFF, true);

		List<String> lines = new ArrayList<>();
		for (Module m : modules.all()) {
			if (!m.enabled()) continue;
			String info = m.info();
			lines.add(info.isEmpty() ? m.name() : m.name() + " " + info);
		}
		lines.sort(Comparator.comparingInt((String s) -> font.width(s)).reversed());
		int w = g.guiWidth();
		int y = 4;
		for (String s : lines) {
			g.drawString(font, s, w - font.width(s) - 4, y, 0xFFFFFFFF, true);
			y += font.lineHeight + 1;
		}
	}
}
