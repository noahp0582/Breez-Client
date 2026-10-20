package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.Modules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer test (BREEZ_SELFTEST=pearl): a 5x5 stone tower 12 blocks tall stands built, and the schematic wants a two-high
 * ring of planks on top of it. The player starts on the ground in survival with planks and four ender pearls, so the
 * builder has to pearl up onto the tower to finish. Checks that a pearl was thrown, that it got up there and that the ring
 * is complete. Writes run/pearl-test.txt and quits.
 */
public final class PearlTest {
	private static final String NAME = "breez-test-tower";
	private static final int H = 12;
	private static final BlockPos ORIGIN = new BlockPos(3, 150, 3);
	private static final List<BuildTest.Want> WANTS = new ArrayList<>();
	private static final List<String> LOG = new ArrayList<>();
	private static int ticks = -1, startedAt = -1, maxY = Integer.MIN_VALUE, pearlsBefore;
	private static boolean finished;

	private PearlTest() {}

	public static boolean register() {
		if (!"pearl".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> ticks = 0);
		ClientTickEvents.END_CLIENT_TICK.register(PearlTest::tick);
		return true;
	}

	private static void cmd(Minecraft mc, String c) {
		var server = mc.getSingleplayerServer();
		if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c));
	}

	private static void design() {
		WANTS.clear();
		for (int y = 0; y < H; y++) for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) WANTS.add(new BuildTest.Want(x, y, z, "minecraft:stone", null));
		for (int y = H; y <= H + 1; y++) {
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					if (x == 0 || x == 4 || z == 0 || z == 4) WANTS.add(new BuildTest.Want(x, y, z, "minecraft:oak_planks", null));
				}
			}
		}
	}

	private static int count(Minecraft mc, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(item)) n += mc.player.getInventory().getItem(i).getCount();
		return n;
	}

	private static void tick(Minecraft mc) {
		if (finished || ticks < 0 || mc.player == null || mc.level == null) return;
		ticks++;
		mc.options.pauseOnLostFocus = false;
		if (ticks < 25) SelfTest.reviveAtStart(mc); // an earlier test may have left the player dead
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
		try {
			if (ticks == 10) {
				design();
				BuildTest.writeNbt(WANTS, 5, H + 2, 5, NAME);
				cmd(mc, "gamemode survival @a");
				cmd(mc, "clear @a");
				cmd(mc, "effect clear @a");
				cmd(mc, "time set day");
				cmd(mc, "fill -14 145 -14 14 149 14 minecraft:stone");
				cmd(mc, "fill -14 150 -14 14 175 14 minecraft:air");
				cmd(mc, "kill @e[type=!minecraft:player,x=-30,y=130,z=-30,dx=60,dy=60,dz=60]");
			}
			if (ticks == 30) {
				cmd(mc, "fill 3 150 3 7 " + (150 + H - 1) + " 7 minecraft:stone");
				cmd(mc, "execute as @a run tp @s 0.5 150 -5.5 0 0");
				cmd(mc, "give @a minecraft:oak_planks 64");
				cmd(mc, "give @a minecraft:ender_pearl 4");
				cmd(mc, "effect give @a minecraft:saturation 600 1 true");
			}
			if (ticks == 60) {
				mc.player.setHealth(20f);
				var b = Modules.builder;
				String err = b.load(NAME);
				LOG.add((err == null ? "PASS" : "FAIL") + " load " + NAME + (err == null ? "" : ": " + err));
				pearlsBefore = count(mc, Items.ENDER_PEARL);
				LOG.add("start: feet " + mc.player.blockPosition().toShortString() + ", " + pearlsBefore + " pearls, " + count(mc, Items.OAK_PLANKS) + " planks, health " + mc.player.getHealth());
				b.setOrigin(ORIGIN);
				if (b.enabled()) b.setEnabled(false);
				b.setEnabled(true);
				startedAt = ticks;
			}
			if (startedAt > 0) {
				maxY = Math.max(maxY, mc.player.blockPosition().getY());
				if (!Modules.builder.enabled() || ticks - startedAt > 4000) finish(mc);
			}
		} catch (Exception e) {
			LOG.add("FAIL test threw " + e);
			finish(mc);
		}
	}

	private static void finish(Minecraft mc) {
		if (finished) return;
		finished = true;
		var b = Modules.builder;
		if (b.enabled()) b.setEnabled(false);
		for (String e : b.events()) LOG.add("  event: " + e);
		for (String n : b.notes()) LOG.add("  skip: " + n);
		LOG.add("builder status: " + b.status() + " after " + (ticks - startedAt) + " ticks");
		int used = pearlsBefore - count(mc, Items.ENDER_PEARL);
		LOG.add((b.pearlsThrown() >= 1 && used >= 1 ? "PASS" : "FAIL") + " threw a pearl (" + b.pearlsThrown() + " counted, " + used + " used)");
		LOG.add((used <= 2 ? "PASS" : "FAIL") + " needed at most 2 pearls (" + used + ")");
		LOG.add((maxY >= ORIGIN.getY() + H ? "PASS" : "FAIL") + " got up onto the tower (highest feet y " + maxY + ", top " + (ORIGIN.getY() + H) + ")");
		int bad = 0, total = 0;
		List<String> missing = new ArrayList<>();
		for (BuildTest.Want w : WANTS) {
			total++;
			BlockPos p = ORIGIN.offset(w.x(), w.y(), w.z());
			var want = BuiltInRegistries.BLOCK.getValue(Identifier.parse(w.block()));
			if (mc.level.getBlockState(p).getBlock() != want) {
				bad++;
				if (missing.size() < 12) missing.add(w.block() + " at " + p.toShortString());
			}
		}
		LOG.add((bad == 0 ? "PASS" : "FAIL") + " built " + (total - bad) + "/" + total + " blocks" + (missing.isEmpty() ? "" : " missing: " + missing));
		LOG.add((mc.player.isAlive() ? "PASS" : "FAIL") + " still alive (health " + mc.player.getHealth() + ")");
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("pearl-test.txt"), String.join("\n", LOG) + "\n");
		} catch (Exception ignored) {
			// nothing else to do
		}
		mc.stop();
	}
}
