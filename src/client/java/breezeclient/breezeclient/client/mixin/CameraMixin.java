package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.FreeLook;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	/** FreeLook: look around in third person without turning the player. */
	@Inject(method = "setup", at = @At("TAIL"))
	private void breez$freeLook(Level level, Entity entity, boolean detached, boolean reversed, float partialTick, CallbackInfo ci) {
		if (FreeLook.active()) setRotation(FreeLook.yaw(), FreeLook.pitch());
	}
}
