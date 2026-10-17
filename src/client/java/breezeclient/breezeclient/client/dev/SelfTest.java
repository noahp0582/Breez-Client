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
		if (GuiTour.register() || NavTest.register() || ExtraTest.register() || RelogTest.register() || ModuleBatchTest.register() || BuildTest.register() || DigTest.register() || AllModulesTest.register()) return;
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
			for (String n : List.of("StorageESP", "SpawnerESP", "AmethystDetector", "OreFinder", "Fullbright", "PlayerESP", "SusChunks", "SeedOres", "StaffList", "BlockESP", "ClusterESP", "NewChunks", "TrailFinder", "StashFinder", "BlockEntityESP", "EntityStorageESP", "PortalESP", "HoleESP", "SpawnerNametags", "MobESP", "WeatherNotifier", "CustomFOV", "Zoom", "FreeLook", "SkinProtect", "JumpCircles", "HitParticles", "NameProtect", "CustomCrosshair", "AimAssist", "Triggerbot", "HoverTotem", "AnchorMacro", "DoubleAnchor", "MaceSwap", "MaceBomber", "ShieldBreaker", "FastUse",
				"AutoDoubleHand", "AutoInvTotem", "TotemOffhand", "AutoJumpReset", "NoHitDelay", "CrystalOptimizer", "StaticHitBoxes", "SpearSwap", "AutoHitCrystal", "Sprint", "AutoFirework", "AutoTool",
				"Fakeplayer", "AntiTrap", "ChunkFinder", "NetheriteFinder", "PlayerChunks", "BlockNotifier", "PearlTrajectory", "RealHitBox", "TargetHUD", "MusicHUD",
				"BlockEntityDebug", "LightFinder", "SuspiciousESP", "TunnelBaseFinder", "PrimeChunkFinder", "SeedChunkFinder", "DiscordPresence", "Proxy")) {
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
			ageNear = ageSummary(mc);
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute as @a in minecraft:overworld run tp @s 20000 100 20000"));
		}
		if (ticks == 20 * 40 + 100 + 20 * 25) {
			ageFar = ageSummary(mc);
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute as @a in minecraft:the_nether run tp @s 100 80 100"));
		}
		if (ticks == 20 * 40 + 100 + 20 * 25 + 20 * 40) finish(mc);
	}

	private static String overworldReport = "", ageNear = "", ageFar = "";

	/** New/old chunk counts within 6 chunks of the player. */
	private static String ageSummary(Minecraft mc) {
		int n = 0, o = 0, u = 0;
		int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				switch (breezeclient.breezeclient.client.world.ChunkAgeTracker.state(pcx + dx, pcz + dz)) {
					case NEW -> n++;
					case OLD -> o++;
					default -> u++;
				}
			}
		}
		return "new=" + n + " old=" + o + " unknown=" + u;
	}

	private static void finish(Minecraft mc) {
		String nether = report(true);
		try {
			Path out = FabricLoader.getInstance().getGameDir().resolve("breez-selftest.txt");
			Files.writeString(out, overworldReport + "\nchunkAge near spawn (saved earlier, should be OLD): " + ageNear + "\nchunkAge 20000 blocks away (generated now, should be NEW): " + ageFar + "\n=== after teleport (dimension: " + (mc.level == null ? "?" : mc.level.dimension().identifier()) + ") ===\n" + nether);
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
		var geodes = breezeclient.breezeclient.client.modules.AmethystDetector.geodes();
		sb.append("geodes=").append(geodes.size()).append(" toastsQueued=").append(breezeclient.breezeclient.client.gui.Toasts.count()).append('\n');
		for (var g : geodes.subList(0, Math.min(6, geodes.size()))) {
			sb.append(String.format("  geode at %d %d %d: %.0f%% grown, %dC %dL %dM %dS, %d open, coverage %.0f%%, ~%.1fh, budding %d%n", g.center().x(), g.center().y(), g.center().z(), g.grownPercent(), g.cluster(), g.large(), g.medium(), g.small(), g.openSlots(), g.coverage(), g.estHours(), g.budding()));
		}
		sb.append(Modules.seedOres.verify());
		return sb.toString();
	}
}
