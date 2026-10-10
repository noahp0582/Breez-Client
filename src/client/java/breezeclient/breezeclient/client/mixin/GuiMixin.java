package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.ScoreboardName;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
	/** ScoreboardName only acts while the sidebar is being built. */
	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
	private void breez$sidebarBegin(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
		ScoreboardName.sidebarBegin();
	}

	@Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
	private void breez$sidebarEnd(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
		ScoreboardName.sidebarEnd();
	}
}
