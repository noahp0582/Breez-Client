package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.nav.Baritone;
import breezeclient.breezeclient.core.nav.Goal;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.util.Set;

/**
 * Developer test (BREEZ_SELFTEST=nav): lets the built-in pathing walk, mine, tunnel and explore in a singleplayer world and
 * writes positions and outcomes to breez-selftest.txt.
 */
public final class NavTest {
	private static int ticks = -1;
	private static final StringBuilder OUT = new StringBuilder();
	private static double sx, sz, tx, tz;
	private static int dirtBefore;

	private NavTest() {}

	public static boolean register() {
		if (!"nav".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> ticks = 0);
		ClientTickEvents.END_CLIENT_TICK.register(NavTest::tick);
		return true;
	}

	private static void cmd(Minecraft mc, String c) {
		var server = mc.getSingleplayerServer();
		if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c));
	}

	private static String pos(Minecraft mc) { return String.format("%.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()); }

	private static void tick(Minecraft mc) {
		if (ticks < 0 || mc.player == null || mc.level == null) return;
		ticks++;
		if (ticks == 20) {
			cmd(mc, "gamemode survival @a");
			cmd(mc, "give @a minecraft:diamond_pickaxe");
			cmd(mc, "give @a minecraft:cobblestone 64");
			cmd(mc, "effect give @a minecraft:resistance 1000 4 true");
			cmd(mc, "spreadplayers 0 0 0 10 false @a");
		}
		if (ticks == 160) {
			sx = mc.player.getX();
			sz = mc.player.getZ();
			tx = Math.floor(sx) + 30;
			tz = Math.floor(sz);
			OUT.append("start ").append(pos(mc)).append('\n');
			Baritone.goTo(new Goal.GoalXZ((int) tx, (int) tz), "test goto");
		}
		if (ticks == 160 + 20 * 50) {
			double d = Math.hypot(mc.player.getX() - tx, mc.player.getZ() - tz);
			OUT.append(String.format("goto 30 blocks: end %s, %.1f from target, mode=%s status=%s lastStop=%s%n", pos(mc), d, Baritone.mode(), Baritone.status(), Baritone.lastStop()));
			Baritone.stop(null);
			dirtBefore = 0;
			Baritone.mine(Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK), 3, "test mine");
		}
		if (ticks == 160 + 20 * 50 + 20 * 40) {
			OUT.append(String.format("mine 3 dirt/grass: mode=%s status=%s pos=%s lastStop=%s%n", Baritone.mode(), Baritone.status(), pos(mc), Baritone.lastStop()));
			Baritone.stop(null);
			Baritone.tunnel(mc.player.getDirection(), 8);
			sx = mc.player.getX();
			sz = mc.player.getZ();
		}
		if (ticks == 160 + 20 * 50 + 20 * 40 + 20 * 40) {
			OUT.append(String.format("tunnel 8: moved %.1f blocks, mode=%s status=%s lastStop=%s%n", Math.hypot(mc.player.getX() - sx, mc.player.getZ() - sz), Baritone.mode(), Baritone.status(), Baritone.lastStop()));
			Baritone.stop(null);
			sx = mc.player.getX();
			sz = mc.player.getZ();
			Baritone.explore();
		}
		if (ticks == 160 + 20 * 50 + 20 * 40 + 20 * 40 + 20 * 40) {
			OUT.append(String.format("explore 40s: moved %.1f blocks, status=%s%n", Math.hypot(mc.player.getX() - sx, mc.player.getZ() - sz), Baritone.status()));
			Baritone.stop(null);
			try {
				Files.writeString(FabricLoader.getInstance().getGameDir().resolve("breez-selftest.txt"), OUT.toString() + "moduleCrashes=" + breezeclient.breezeclient.core.ModuleManager.crashCount() + "\n");
			} catch (Exception ignored) {
				// nothing else to do
			}
			mc.stop();
		}
	}
}
