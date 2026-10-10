package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.CreamyKeys;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	/** CreamyKeys listens to every key event. */
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void breez$key(long window, int action, KeyEvent event, CallbackInfo ci) {
		CreamyKeys.onKey(action, event.key());
	}
}
