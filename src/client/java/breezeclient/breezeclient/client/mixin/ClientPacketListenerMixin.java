package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.session.Guard;
import breezeclient.breezeclient.client.world.ChunkAgeTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/** Feeds fluid updates to the new-chunk detector. Handlers run once on the network thread and once on the client thread; only the latter counts. */
	@Inject(method = "handleBlockUpdate", at = @At("HEAD"))
	private void breez$blockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) Guard.run("block update", () -> ChunkAgeTracker.onBlockUpdate(packet.getPos(), packet.getBlockState()));
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
	private void breez$sectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) Guard.run("section update", () -> packet.runUpdates(ChunkAgeTracker::onBlockUpdate));
	}
}
