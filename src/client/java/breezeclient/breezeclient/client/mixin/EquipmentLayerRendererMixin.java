package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.ArmorTrimHider;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {
	/** ArmorTrimHider swaps in a copy of the stack without (or with a different) trim, for drawing only. */
	@ModifyVariable(method = "renderLayers", at = @At("HEAD"), argsOnly = true)
	private ItemStack breez$trim(ItemStack stack) {
		return ArmorTrimHider.filter(stack);
	}
}
