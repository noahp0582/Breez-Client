package breezeclient.breezeclient.client.session;

import breezeclient.breezeclient.client.Modules;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.Guard;
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
		// JOIN runs inside the login packet handler, so it must not throw (see Guard)
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> Guard.run("Session join", () -> {
			ServerData d = mc.getCurrentServer();
			serverKey = d == null || d.ip == null ? "singleplayer" : d.ip.trim().toLowerCase(Locale.ROOT);
			Waypoints.setServer(serverKey);
			if (d != null) config.setLastServer(serverKey);
			ChunkScanner.reset();
		}));
		// Fabric fires DISCONNECT on the network thread; module and scanner state belongs to the client thread.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> Guard.run("Session disconnect", () -> {
			// a quick relog can have the new level (and its first chunks) in place before this task runs; the scanner
			// notices a level change by itself, so only wipe it while there is no level
			if (mc.level == null) ChunkScanner.reset();
			Modules.stopAutomation();
		})));
	}

	public static boolean inGame() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.level != null;
	}
}
