package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.Animations;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Shadow private ItemStack mainHandItem;
	@Shadow private ItemStack offHandItem;
	@Shadow private float mainHandHeight;
	@Shadow private float oMainHandHeight;
	@Shadow private float offHandHeight;
	@Shadow private float oOffHandHeight;

	/** Animations: keep the hands raised and swap items instantly. */
	@Inject(method = "tick", at = @At("RETURN"))
	private void breez$tick(CallbackInfo ci) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return;
		if (Animations.noEquip()) {
			mainHandItem = p.getMainHandItem();
			offHandItem = p.getOffhandItem();
			mainHandHeight = oMainHandHeight = 1f;
			offHandHeight = oOffHandHeight = 1f;
		} else if (Animations.noCooldownDip()) {
			if (ItemStack.matches(mainHandItem, p.getMainHandItem())) mainHandHeight = oMainHandHeight = 1f;
			if (ItemStack.matches(offHandItem, p.getOffhandItem())) offHandHeight = oOffHandHeight = 1f;
		}
	}

	@Inject(method = "renderHandsWithItems", at = @At("HEAD"))
	private void breez$begin(float partialTick, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		pose.pushPose();
		float[] t = Animations.handTransform();
		if (t != null) {
			pose.translate(t[0], t[1], t[2]);
			pose.scale(t[3], t[3], t[3]);
		}
	}

	@Inject(method = "renderHandsWithItems", at = @At("RETURN"))
	private void breez$end(float partialTick, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		pose.popPose();
	}
}
