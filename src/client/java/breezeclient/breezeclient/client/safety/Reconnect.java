package breezeclient.breezeclient.client.safety;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Remembers the current server and reconnects to it after a delay. Shared by AutoRelog and AutoReconnect. */
public final class Reconnect {
	private static ServerData last;
	private static int countdown = -1;
	private static int attempts;
	private static boolean suppressed;

	/** Set by AutoReconnect: whether to reconnect after the server drops us, and how long to wait (ticks). */
	public static BooleanSupplier autoPolicy = () -> false;
	public static IntSupplier autoDelayTicks = () -> 100;
	public static IntSupplier autoMaxAttempts = () -> 5;

	private Reconnect() {}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			ServerData d = mc.getCurrentServer();
			if (d != null) last = d;
			attempts = 0;
			countdown = -1;
			suppressed = false;
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof DisconnectedScreen && autoPolicy.getAsBoolean() && !suppressed && countdown < 0 && last != null
				&& attempts < autoMaxAttempts.getAsInt()) {
				attempts++;
				countdown = Math.max(20, autoDelayTicks.getAsInt());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(Reconnect::tick);
	}

	public static boolean hasServer() { return last != null; }

	/** Stop AutoReconnect for the rest of this disconnect (used by the kill switch). */
	public static void suppress() {
		suppressed = true;
		countdown = -1;
	}

	public static void cancel() { countdown = -1; }

	public static boolean pending() { return countdown >= 0; }

	/** Gracefully leave the current server and reconnect after {@code ticks}. */
	public static void relog(int ticks) {
		Minecraft mc = Minecraft.getInstance();
		if (last == null) return;
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
