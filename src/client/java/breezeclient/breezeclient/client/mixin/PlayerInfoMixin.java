package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.SkinProtect;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
	/** SkinProtect shows default skins instead of real ones (local only). */
	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void breez$skin(CallbackInfoReturnable<PlayerSkin> cir) {
		if (SkinProtect.active()) cir.setReturnValue(DefaultPlayerSkin.get(((PlayerInfo) (Object) this).getProfile()));
	}
}
