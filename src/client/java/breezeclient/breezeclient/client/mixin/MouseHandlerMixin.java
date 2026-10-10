package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.CreamyKeys;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/** CreamyKeys listens to mouse buttons and the scroll wheel. */
	@Inject(method = "onButton", at = @At("HEAD"))
	private void breez$button(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		CreamyKeys.onMouse(action, info.button());
	}

	@Inject(method = "onScroll", at = @At("HEAD"))
	private void breez$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		CreamyKeys.onScroll(yOffset);
	}
}
