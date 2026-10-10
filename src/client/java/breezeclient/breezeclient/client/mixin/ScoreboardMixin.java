package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.ScoreboardName;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(Scoreboard.class)
public abstract class ScoreboardMixin {
	/** ScoreboardName HIDE_LINE: drop your own entry while the sidebar is built. */
	@Inject(method = "listPlayerScores(Lnet/minecraft/world/scores/Objective;)Ljava/util/Collection;", at = @At("RETURN"), cancellable = true)
	private void breez$entries(Objective objective, CallbackInfoReturnable<Collection<PlayerScoreEntry>> cir) {
		Collection<PlayerScoreEntry> in = cir.getReturnValue();
		Collection<PlayerScoreEntry> out = ScoreboardName.sidebarEntries(in);
		if (out != in) cir.setReturnValue(out);
	}
}
