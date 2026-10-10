package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.ScoreboardName;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.PlayerScoreEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerScoreEntry.class)
public abstract class PlayerScoreEntryMixin {
	/** ScoreboardName: custom sidebars sometimes put your name in the score text. */
	@Inject(method = "formatValue", at = @At("RETURN"), cancellable = true)
	private void breez$value(NumberFormat format, CallbackInfoReturnable<MutableComponent> cir) {
		MutableComponent in = cir.getReturnValue();
		MutableComponent out = ScoreboardName.sidebarScoreText(in);
		if (out != in) cir.setReturnValue(out);
	}
}
