package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.CustomAccessories;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	/** CustomAccessories gives your own player the custom cape. */
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void breez$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		if ((Object) this instanceof LocalPlayer) {
			PlayerSkin out = CustomAccessories.skinFor(cir.getReturnValue());
			if (out != cir.getReturnValue()) cir.setReturnValue(out);
		}
	}
}
