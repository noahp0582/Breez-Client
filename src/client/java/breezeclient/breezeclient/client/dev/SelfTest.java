package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.Modules;
import breezeclient.breezeclient.client.modules.Freecam;
import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Developer self-test, active only when the BREEZ_SELFTEST environment variable is set. After joining a singleplayer
 * world it switches on every passive module, lets chunks load, verifies the seed ore simulator against the real world,
 * writes breez-selftest.txt next to the config folder and quits the game.
 */
public final class SelfTest {
	private static int ticks = -1;

	private SelfTest() {}

	public static void register() {
		if (System.getenv("BREEZ_SELFTEST") == null) return;
		if (GuiTour.register()) return;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> ticks = 0);
		ClientTickEvents.END_CLIENT_TICK.register(SelfTest::tick);
	}

	private static void tick(Minecraft mc) {
		if (ticks < 0 || mc.player == null || mc.level == null) return;
		ticks++;
		var server = mc.getSingleplayerServer();
		if (ticks == 20 && server != null) {
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute as @a in minecraft:overworld run tp @s 0 100 0"));
		}
		if (ticks == 60) {
			if (server != null) StaffTracker.profile().setSeed(String.valueOf(server.getWorldData().worldGenOptions().seed()));
			for (String n : List.of("StorageESP", "SpawnerESP", "AmethystDetector", "OreFinder", "Fullbright", "PlayerESP", "SusChunks", "SeedOres", "StaffList", "BlockESP")) {
				Module m = BreezClientClient.MODULES.get(n);
				if (m != null) m.setEnabled(true);
			}
			Modules.seedOres.setEnabled(true);
		}
		if (ticks == 20 * 40) BreezClientClient.MODULES.get("Freecam").setEnabled(true);
		if (ticks == 20 * 40 + 60) BreezClientClient.MODULES.get("Freecam").setEnabled(false);
		if (ticks == 20 * 40 + 100) {
			overworldReport = report(server != null);
			if (server == null) {
				finish(mc);
				return;
			}
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute as @a in minecraft:the_nether run tp @s 100 80 100"));
		}
		if (ticks == 20 * 40 + 100 + 20 * 40) finish(mc);
	}

	private static String overworldReport = "";

	private static void finish(Minecraft mc) {
		String nether = report(true);
		try {
			Path out = FabricLoader.getInstance().getGameDir().resolve("breez-selftest.txt");
			Files.writeString(out, overworldReport + "\n=== after teleport (dimension: " + (mc.level == null ? "?" : mc.level.dimension().identifier()) + ") ===\n" + nether);
		} catch (Exception ignored) {
			// nothing else to do
		}
		mc.stop();
	}

	private static String report(boolean singleplayer) {
		StringBuilder sb = new StringBuilder();
		sb.append("singleplayer=").append(singleplayer).append('\n');
		sb.append("moduleCrashes=").append(ModuleManager.crashCount()).append('\n');
		sb.append("freecamLeftActive=").append(Freecam.active()).append('\n');
		for (Module m : BreezClientClient.MODULES.all()) if (m.enabled()) sb.append("  ").append(m.name()).append(" info=").append(m.info()).append('\n');
		sb.append(Modules.seedOres.verify());
		return sb.toString();
	}
}
