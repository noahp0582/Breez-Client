package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.NameTags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	/** NameTags replaces the in-world player nametag with its HUD tag. */
	@Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
	private void breez$name(Entity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
		if (NameTags.hidesVanilla(entity)) cir.setReturnValue(false);
	}
}
