package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.input.UseClicks;
import breezeclient.breezeclient.client.modules.Freecam;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** No attacking, breaking or placing from the real player while the camera is detached. */
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void breez$noAttack(CallbackInfoReturnable<Boolean> cir) {
		if (Freecam.active()) cir.setReturnValue(false);
	}

	/** Right-clicks a module takes (Freecam, AutoDig's corner marking) never reach the game. */
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void breez$noUse(CallbackInfo ci) {
		if (UseClicks.intercept((Minecraft) (Object) this)) ci.cancel();
	}

	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void breez$keybinds(CallbackInfo ci) {
		UseClicks.keyState(((Minecraft) (Object) this).options.keyUse.isDown());
	}

	@Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
	private void breez$noPick(CallbackInfo ci) {
		if (Freecam.active()) ci.cancel();
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void breez$noContinue(boolean bl, CallbackInfo ci) {
		if (Freecam.active()) ci.cancel();
	}
}
