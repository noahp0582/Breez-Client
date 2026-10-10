package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.CustomFov;
import breezeclient.breezeclient.client.modules.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** CustomFOV replaces the field of view, Zoom divides it. */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void breez$fov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		float fov = cir.getReturnValue();
		float out = Zoom.apply(CustomFov.apply(fov));
		if (useFovSetting) breezeclient.breezeclient.client.modules.NameTags.noteFov(out);
		if (out != fov) cir.setReturnValue(out);
	}

	/** AutoLog counts your own totem pops (the game shows the totem animation only for you). */
	@Inject(method = "displayItemActivation", at = @At("HEAD"))
	private void breez$totem(net.minecraft.world.item.ItemStack stack, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) breezeclient.breezeclient.client.modules.AutoLog.onTotemPop();
	}
}
