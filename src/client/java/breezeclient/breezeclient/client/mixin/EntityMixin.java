package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.Freecam;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	/** Mouse look turns the freecam camera instead of the real player. */
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void breez$turn(double yRot, double xRot, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer)) return;
		if (Freecam.active()) {
			Freecam.turn(yRot, xRot);
			ci.cancel();
		} else if (breezeclient.breezeclient.client.modules.FreeLook.active()) {
			breezeclient.breezeclient.client.modules.FreeLook.turn(yRot, xRot);
			ci.cancel();
		}
	}

	/** HitBox widens the targeting radius of other entities (your own crosshair only). */
	@Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true)
	private void breez$pick(CallbackInfoReturnable<Float> cir) {
		float extra = breezeclient.breezeclient.client.modules.HitBox.pickRadius((Entity) (Object) this);
		if (extra > 0) cir.setReturnValue(cir.getReturnValue() + extra);
	}
}
