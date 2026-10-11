package breezeclient.breezeclient.client.mixin;

import breezeclient.breezeclient.client.integration.ProxyRelay;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.net.InetSocketAddress;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
	/** Proxy module: the connection to the server is pointed at a local relay that tunnels through the proxy. */
	@ModifyVariable(method = "connect", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static InetSocketAddress breez$proxy(InetSocketAddress address) {
		return ProxyRelay.redirect(address);
	}
}
