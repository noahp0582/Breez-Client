package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.ScoreboardName;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	/** ScoreboardName can also replace your name in the tab list. */
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void breez$tab(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		Component in = cir.getReturnValue();
		Component out = ScoreboardName.tabName(in);
		if (out != in) cir.setReturnValue(out);
	}
}
