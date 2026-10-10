package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.modules.ScoreboardName;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTeam.class)
public abstract class PlayerTeamMixin {
	/** ScoreboardName replaces your name in the sidebar entries (the flag keeps it away from nametags and chat). */
	@Inject(method = "formatNameForTeam", at = @At("RETURN"), cancellable = true)
	private static void breez$name(Team team, Component name, CallbackInfoReturnable<MutableComponent> cir) {
		MutableComponent in = cir.getReturnValue();
		MutableComponent out = ScoreboardName.sidebarName(in);
		if (out != in) cir.setReturnValue(out);
	}
}
