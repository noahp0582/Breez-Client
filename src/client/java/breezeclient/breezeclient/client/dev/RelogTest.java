package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.core.Guard;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Developer test (BREEZ_SELFTEST=relog): joins the singleplayer test world, leaves to the title screen, joins the same
 * world again and compares what the scanner knows about with the chunks the client actually has, both times.
 * Writes run/relog-test.txt.
 */
public final class RelogTest {
	private static final String WORLD = "breeztest";
	private static final int MEASURE_AT = 300;
	private static final int REJOIN_WAIT = 60;

	private static final List<String> LOG = new ArrayList<>();
	private static final ScanTarget PROBE = ScanTarget.ofBlocks(Set.of(Blocks.STONE, Blocks.DEEPSLATE));
	private static int joins;
	private static int ticks = -1;
	private static int wait = -1;
	private static boolean done;

	private RelogTest() {}

	public static boolean register() {
		if (!"relog".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> {
			joins++;
			ticks = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(RelogTest::tick);
		ChunkScanner.add(PROBE);
		return true;
	}

	private static void tick(Minecraft mc) {
		if (done) return;
		if (wait >= 0) {
			if (--wait == 0) {
				wait = -1;
				mc.createWorldOpenFlows().openWorld(WORLD, () -> {
					LOG.add("could not reopen " + WORLD);
					finish(mc);
				});
			}
			return;
		}
		if (ticks < 0 || mc.player == null || mc.level == null) return;
		if (++ticks != MEASURE_AT) return;
		int actual = mc.level.getChunkSource().getLoadedChunksCount();
		int known = ChunkScanner.loadedChunks().size();
		int withStone = PROBE.hitsByChunk().size();
		LOG.add("join " + joins + ": client chunks=" + actual + " scanner knows=" + known + " chunks with stone found=" + withStone
			+ " hook failures=" + Guard.failures()
			+ (known * 10 < actual * 9 || withStone * 10 < actual * 5 || Guard.failures() > 0 ? "  <-- PROBLEM" : "  ok"));
		if (joins >= 2) {
			finish(mc);
			return;
		}
		ticks = -1;
		mc.level.disconnect(Component.literal("relog test"));
		mc.disconnect(new TitleScreen(), false);
		wait = REJOIN_WAIT;
	}

	private static void finish(Minecraft mc) {
		done = true;
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("relog-test.txt"), String.join("\n", LOG) + "\n");
		} catch (Exception ignored) {
			// nothing else to do
		}
		mc.stop();
	}
}
