package breezeclient.breezeclient.client.session;

import breezeclient.breezeclient.client.Modules;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.config.ConfigManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

/** Tracks which server we are on and keeps per-server state in sync. */
public final class Session {
	private static String serverKey = "singleplayer";

	private Session() {}

	/** "host:port" lowercase, or "singleplayer". */
	public static String serverKey() { return serverKey; }

	public static void register(ConfigManager config) {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			ServerData d = mc.getCurrentServer();
			serverKey = d == null || d.ip == null ? "singleplayer" : d.ip.trim().toLowerCase(Locale.ROOT);
			Waypoints.setServer(serverKey);
			if (d != null) config.setLastServer(serverKey);
			ChunkScanner.reset();
		});
		// Fabric fires DISCONNECT on the network thread; module and scanner state belongs to the client thread.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
			ChunkScanner.reset();
			Modules.stopAutomation();
		}));
	}

	public static boolean inGame() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.level != null;
	}
}
