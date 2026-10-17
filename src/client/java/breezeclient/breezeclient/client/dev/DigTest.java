package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.Modules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer test (BREEZ_SELFTEST=dig): marks a 5x3x5 box of stone with a chest inside, gives the player a pickaxe named
 * like the shard pickaxe and an ordinary one, runs AutoDig and checks that the box is empty, the chest is still there and
 * nothing outside the box was touched. Writes run/dig-test.txt and quits.
 */
public final class DigTest {
	private static final BlockPos A = new BlockPos(-2, 147, -2), B = new BlockPos(2, 149, 2), CHEST = new BlockPos(1, 148, 1);
	private static final List<String> LOG = new ArrayList<>();
	private static int ticks = -1;
	private static int startedAt = -1;
	private static boolean finished;

	private DigTest() {}

	public static boolean register() {
		if (!"dig".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> ticks = 0);
		ClientTickEvents.END_CLIENT_TICK.register(DigTest::tick);
		return true;
	}

	private static void cmd(Minecraft mc, String c) {
		var server = mc.getSingleplayerServer();
		if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c));
	}

	private static void tick(Minecraft mc) {
		if (ticks < 0 || mc.player == null || mc.level == null) return;
		ticks++;
		mc.options.pauseOnLostFocus = false;
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
		if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) { // an earlier survival test can leave the player dead
			mc.player.respawn();
			mc.setScreen(null);
			if (startedAt < 0) ticks = 0; // set the scene again from the top once we are back
			return;
		}
		try {
			if (ticks == 10) {
				cmd(mc, "gamemode survival @a");
				cmd(mc, "clear @a");
				cmd(mc, "effect give @a minecraft:instant_health 1 10 true");
				cmd(mc, "effect give @a minecraft:saturation 10 20 true");
				cmd(mc, "effect give @a minecraft:resistance 600 4 true"); // night in the test world: a zombie must not end the run
				cmd(mc, "time set day");
				cmd(mc, "fill -14 140 -14 14 149 14 minecraft:stone");
				cmd(mc, "fill -14 150 -14 14 160 14 minecraft:air");
				cmd(mc, "kill @e[type=!minecraft:player,x=-20,y=140,z=-20,dx=40,dy=30,dz=40]"); // a mob left from the night would block the crosshair
				cmd(mc, "setblock " + CHEST.getX() + " " + CHEST.getY() + " " + CHEST.getZ() + " minecraft:chest");
				cmd(mc, "give @a minecraft:netherite_pickaxe[minecraft:custom_name=\"Shard Pickaxe\",minecraft:enchantments={\"minecraft:efficiency\":5}]");
				cmd(mc, "give @a minecraft:diamond_pickaxe[minecraft:enchantments={\"minecraft:efficiency\":5}]");
			}
			if (ticks == 40) cmd(mc, "execute as @a run tp @s 0.5 150 0.5");
			if (ticks == 80) {
				List<String> out = new ArrayList<>();
				mc.player.setXRot(90);
				Modules.digger.setCorner(1, A);
				Modules.digger.setCorner(2, B);
				BreezClientClient.commands.handle(".dig start", out::add);
				LOG.add("start: " + out);
				LOG.add((Modules.digger.enabled() ? "PASS" : "FAIL") + " AutoDig started on the marked box");
				LOG.add("hotbar 0: " + mc.player.getInventory().getItem(0).getHoverName().getString() + ", 1: " + mc.player.getInventory().getItem(1).getHoverName().getString());
				startedAt = ticks;
			}
			if (startedAt > 0 && (!Modules.digger.enabled() || ticks - startedAt > 5000)) finish(mc);
		} catch (Exception e) {
			LOG.add("FAIL test threw " + e);
			finish(mc);
		}
	}

	private static void finish(Minecraft mc) {
		if (finished) return;
		finished = true;
		LOG.add("status: " + Modules.digger.status() + " after " + (ticks - startedAt) + " ticks, enabled=" + Modules.digger.enabled());
		int left = 0, total = 0, outside = 0;
		for (BlockPos p : BlockPos.betweenClosed(A.offset(-1, -1, -1), B.offset(1, 0, 1))) {
			boolean in = p.getX() >= A.getX() && p.getX() <= B.getX() && p.getY() >= A.getY() && p.getZ() >= A.getZ() && p.getZ() <= B.getZ();
			var st = mc.level.getBlockState(p);
			if (in) {
				if (p.equals(CHEST)) continue;
				total++;
				if (!st.isAir()) left++;
			} else if (!st.is(Blocks.STONE)) {
				outside++;
			}
		}
		LOG.add((left == 0 ? "PASS" : "FAIL") + " cleared " + (total - left) + "/" + total + " blocks of the box");
		LOG.add((mc.level.getBlockState(CHEST).is(Blocks.CHEST) ? "PASS" : "FAIL") + " the chest inside the box was left alone");
		LOG.add((outside == 0 ? "PASS" : "FAIL") + " nothing outside the box was dug (" + outside + " blocks changed)");
		LOG.add((!Modules.digger.enabled() ? "PASS" : "FAIL") + " AutoDig switched itself off when done");
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("dig-test.txt"), String.join("\n", LOG) + "\n");
		} catch (Exception ignored) {
			// nothing else to do
		}
		mc.stop();
	}
}
