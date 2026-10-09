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
		if (out != fov) cir.setReturnValue(out);
	}
}
