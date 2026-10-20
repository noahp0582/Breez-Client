package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer test (BREEZ_SELFTEST=mp) for the modules that only work on a real server: join a local dedicated server
 * (gradlew runServer, offline mode, the test player an op) with --quickPlayMultiplayer, then AutoRelog has to leave and
 * come back, and AutoReconnect has to come back after the server kicks us. Writes run/mp-test.txt and quits.
 */
public final class MpTest {
	private static final List<String> LOG = new ArrayList<>();
	private static int ticks = -1, joins, joinedAt, kickAt = -1;
	private static boolean finished;

	private MpTest() {}

	public static boolean register() {
		if (!"mp".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> {
			if (ticks < 0) ticks = 0;
			joins++;
			joinedAt = ticks;
			LOG.add("joined " + (mc.getCurrentServer() == null ? "?" : mc.getCurrentServer().ip) + " (join " + joins + ", tick " + ticks + ")");
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> LOG.add("disconnected (tick " + ticks + ")"));
		ClientTickEvents.END_CLIENT_TICK.register(MpTest::tick);
		return true;
	}

	private static Module mod(String name) { return BreezClientClient.MODULES.get(name); }

	@SuppressWarnings("unchecked")
	private static void set(String module, String setting, Object value) {
		for (Setting<?> s : mod(module).settings()) if (s.name().equals(setting)) ((Setting<Object>) s).set(value);
	}

	private static void check(String name, boolean ok, String detail) { LOG.add((ok ? "PASS " : "FAIL ") + name + (detail.isEmpty() ? "" : " (" + detail + ")")); }

	private static void tick(Minecraft mc) {
		if (finished || ticks < 0) return;
		ticks++;
		mc.options.pauseOnLostFocus = false;
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
		int since = ticks - joinedAt;
		if (joins == 1 && since == 40 && mc.player != null) {
			for (Module m : BreezClientClient.MODULES.all()) if (m.enabled()) m.setEnabled(false);
			set("AutoRelog", "Y level", 320.0); // anywhere counts as "low enough"
			set("AutoRelog", "Cooldown (s)", 10.0);
			set("AutoRelog", "Rejoin delay (s)", 1.0);
			mod("AutoRelog").setEnabled(true);
			LOG.add("AutoRelog on at y " + (int) mc.player.getY());
		}
		if (joins == 2 && since == 1) {
			mod("AutoRelog").setEnabled(false);
			check("AutoRelog leaves and joins the server again", true, "");
		}
		if (joins == 2 && since == 60 && mc.player != null) {
			set("AutoReconnect", "Delay (s)", 2.0);
			mod("AutoReconnect").setEnabled(true);
			kickAt = ticks;
			mc.player.connection.sendCommand("kick @s Breez test kick");
		}
		if (joins == 3 && since == 20) {
			check("AutoReconnect joins again after the server kicks you", kickAt > 0, "");
			mod("AutoReconnect").setEnabled(false);
			finish(mc);
		}
		if (ticks > 3600) {
			check("multiplayer steps finished in time", false, joins + " joins");
			finish(mc);
		}
	}

	private static void finish(Minecraft mc) {
		if (finished) return;
		finished = true;
		try {
			Files.write(FabricLoader.getInstance().getGameDir().resolve("mp-test.txt"), LOG);
		} catch (Exception ignored) {
			// nothing else to do
		}
		mc.stop();
	}
}
