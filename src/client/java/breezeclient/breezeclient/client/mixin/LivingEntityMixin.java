package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.Animations;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** Animations: your own swing animation runs faster or slower. */
	@Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
	private void breez$swing(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof LocalPlayer) cir.setReturnValue(Animations.swingDuration(cir.getReturnValue()));
	}
}
