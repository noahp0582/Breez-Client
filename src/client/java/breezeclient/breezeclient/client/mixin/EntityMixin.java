package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.Freecam;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
	/** Mouse look turns the freecam camera instead of the real player. */
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void breez$turn(double yRot, double xRot, CallbackInfo ci) {
		if (Freecam.active() && (Object) this instanceof LocalPlayer) {
			Freecam.turn(yRot, xRot);
			ci.cancel();
		}
	}
}
