package breezeclient.breezeclient.client.safety;

import breezeclient.breezeclient.core.Guard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Remembers the current server and reconnects to it after a delay. Shared by AutoRelog and AutoReconnect. */
public final class Reconnect {
	/** A session shorter than this counts toward the attempt limit, so a server that kicks right after joining cannot loop forever. */
	private static final long STABLE_SESSION_MS = 60_000;
	/** A DisconnectedScreen only counts as "the server dropped us" when it appears this soon after the play session ended. */
	private static final long KICK_WINDOW_MS = 3_000;

	private static ServerData last;
	private static int countdown = -1;
	private static int attempts;
	private static boolean suppressed;
	private static long joinedAt;
	private static long disconnectedAt;

	/** Set by AutoReconnect: whether to reconnect after the server drops us, and how long to wait (ticks). */
	public static BooleanSupplier autoPolicy = () -> false;
	public static IntSupplier autoDelayTicks = () -> 100;
	public static IntSupplier autoMaxAttempts = () -> 5;

	private Reconnect() {}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> Guard.run("Reconnect join", () -> {
			ServerData d = mc.getCurrentServer();
			last = d; // singleplayer has no server data, so there is nothing to reconnect to
			joinedAt = System.currentTimeMillis();
			countdown = -1;
			suppressed = false;
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> disconnectedAt = System.currentTimeMillis());
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof DisconnectedScreen) {
				onDisconnectedScreen();
			} else if (countdown >= 0 && !(screen instanceof TitleScreen)) {
				countdown = -1; // the user moved on to another screen (server list, connecting elsewhere)
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(Reconnect::tick);
	}

	private static void onDisconnectedScreen() {
		long now = System.currentTimeMillis();
		boolean kickedFromPlay = disconnectedAt > 0 && now - disconnectedAt <= KICK_WINDOW_MS;
		if (!kickedFromPlay || !autoPolicy.getAsBoolean() || suppressed || countdown >= 0 || last == null) return;
		if (joinedAt > 0 && disconnectedAt - joinedAt >= STABLE_SESSION_MS) attempts = 0;
		if (attempts >= autoMaxAttempts.getAsInt()) return;
		attempts++;
		countdown = Math.max(20, autoDelayTicks.getAsInt());
	}

	public static boolean hasServer() { return last != null; }

	/** Stop AutoReconnect for the rest of this disconnect (used by the kill switch). */
	public static void suppress() {
		suppressed = true;
		countdown = -1;
	}

	public static void cancel() { countdown = -1; }

	public static boolean pending() { return countdown >= 0; }

	/**
	 * Leaves the current multiplayer server (closing the connection properly) and reconnects after {@code ticks}.
	 * Does nothing in singleplayer.
	 */
	public static void relog(int ticks) {
		Minecraft mc = Minecraft.getInstance();
		if (last == null || mc.isLocalServer() || mc.getCurrentServer() == null || mc.level == null) return;
		mc.level.disconnect(Component.literal("Relog"));
		mc.disconnect(new TitleScreen(), false);
		countdown = Math.max(20, ticks);
		suppressed = false;
	}

	private static void tick(Minecraft mc) {
		if (countdown < 0) return;
		if (--countdown > 0) return;
		countdown = -1;
		if (last == null) return;
		ServerData data = last;
		mc.execute(() -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(data.ip), data, false, null));
	}
}
