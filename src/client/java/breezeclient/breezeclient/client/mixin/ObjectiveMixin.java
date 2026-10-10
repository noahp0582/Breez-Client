package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.ScoreboardName;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Objective.class)
public abstract class ObjectiveMixin {
	/** ScoreboardName: the sidebar title can contain your name too. */
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void breez$title(CallbackInfoReturnable<Component> cir) {
		Component in = cir.getReturnValue();
		Component out = ScoreboardName.sidebarTitle(in);
		if (out != in) cir.setReturnValue(out);
	}
}
