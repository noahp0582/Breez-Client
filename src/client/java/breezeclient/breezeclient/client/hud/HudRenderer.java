package breezeclient.breezeclient.client.hud;

import breezeclient.breezeclient.client.gui.Gfx;
import breezeclient.breezeclient.client.gui.GuiState;
import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.core.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public final class HudRenderer {
	private HudRenderer() {}

	public static void register(ModuleManager modules) {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath("breez-client", "hud"),
			(GuiGraphics g, DeltaTracker dt) -> render(g));
	}

	private static void render(GuiGraphics graphics) {
		Minecraft mc = Minecraft.getInstance();
		Gfx g = new Gfx(graphics, GuiState.THEME);
		boolean hidden = mc.options.hideGui && GuiState.CLIENT.hudOnlyInGame.get();
		if (!hidden && !(mc.screen instanceof breezeclient.breezeclient.client.gui.BreezScreen)) {
			int sw = graphics.guiWidth();
			for (HudElements.Element e : HudElements.all()) {
				int[] pos = HudElements.position(e, sw);
				e.draw(g, pos[0], pos[1], false);
			}
		}
		// toasts are drawn by BreezScreen while a Breez screen is open
		if (!(mc.screen instanceof breezeclient.breezeclient.client.gui.BreezScreen)) Toasts.render(g, graphics.guiWidth(), graphics.guiHeight());
	}
}
