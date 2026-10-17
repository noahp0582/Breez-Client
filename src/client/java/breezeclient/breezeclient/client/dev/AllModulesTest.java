package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.debug.DebugLog;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Developer test (BREEZ_SELFTEST=all): switches every registered module on by itself for {@link #RUN} ticks, then off,
 * and records whether it crashed (ModuleManager disabled it) or logged a warning or error while it ran. The player is put
 * back at the same spot in creative between modules. Writes run/all-test.txt and quits.
 */
public final class AllModulesTest {
	private static final int RUN = 40, GAP = 10;
	// these drop or redirect the connection, talk to outside services or quit the game, so a sweep cannot run them blind
	private static final Set<String> SKIP = Set.of("killswitch", "autolog", "autorelog", "autoreconnect", "proxy", "discordpresence", "alertwebhook");
	private static final List<String> LOG = new ArrayList<>();
	private static List<Module> queue;
	private static int ticks = -1, index = -1, phaseStart, crashesBefore, entriesBefore;
	private static boolean finished;
	private static int pass, fail, skipped;

	private AllModulesTest() {}

	public static boolean register() {
		if (!"all".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> ticks = 0);
		ClientTickEvents.END_CLIENT_TICK.register(AllModulesTest::tick);
		return true;
	}

	private static void cmd(Minecraft mc, String c) {
		var server = mc.getSingleplayerServer();
		if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c));
	}

	private static void reset(Minecraft mc) {
		cmd(mc, "gamemode creative @a");
		cmd(mc, "effect give @a minecraft:resistance 600 4 true");
		cmd(mc, "execute as @a in minecraft:overworld run tp @s 0.5 150 0.5 0 0");
	}

	private static void tick(Minecraft mc) {
		if (finished || ticks < 0) return;
		if (mc.player == null || mc.level == null) {
			if (index >= 0) { // a module dropped us out of the world
				LOG.add("FAIL " + queue.get(index).name() + ": left the world");
				fail++;
				finish(mc);
			}
			return;
		}
		ticks++;
		mc.options.pauseOnLostFocus = false;
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
		if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) { mc.player.respawn(); mc.setScreen(null); }
		if (ticks == 10) {
			cmd(mc, "time set day");
			cmd(mc, "fill -6 148 -6 6 148 6 minecraft:stone");
			cmd(mc, "fill -6 149 -6 6 152 6 minecraft:air");
			reset(mc);
		}
		if (ticks < 60) return;
		if (queue == null) {
			queue = new ArrayList<>(BreezClientClient.MODULES.all());
			for (Module m : queue) if (m.enabled()) m.setEnabled(false);
			LOG.add("modules registered: " + queue.size());
			phaseStart = ticks;
		}
		if (index >= 0 && ticks - phaseStart == RUN) stop(mc);
		if (ticks - phaseStart == RUN + GAP || index < 0) {
			index++;
			if (index >= queue.size()) { finish(mc); return; }
			start(mc);
		}
	}

	private static void start(Minecraft mc) {
		Module m = queue.get(index);
		phaseStart = ticks;
		if (SKIP.contains(m.name().toLowerCase())) {
			LOG.add("SKIP " + m.name() + ": drops the connection or calls an outside service");
			skipped++;
			phaseStart = ticks - RUN - GAP + 1; // move on next tick
			return;
		}
		reset(mc);
		crashesBefore = ModuleManager.crashCount();
		entriesBefore = DebugLog.entries().size();
		try {
			m.setEnabled(true);
		} catch (Throwable t) {
			DebugLog.record(DebugLog.Kind.ERROR, m.name(), "onEnable threw", t);
		}
	}

	private static void stop(Minecraft mc) {
		Module m = queue.get(index);
		boolean selfOff = !m.enabled();
		try {
			m.setEnabled(false);
		} catch (Throwable t) {
			DebugLog.record(DebugLog.Kind.ERROR, m.name(), "onDisable threw", t);
		}
		if (mc.screen != null) mc.setScreen(null); // looters and builders may open containers
		int crashes = ModuleManager.crashCount() - crashesBefore;
		List<DebugLog.Entry> all = DebugLog.entries();
		List<DebugLog.Entry> mine = all.subList(Math.min(entriesBefore, all.size()), all.size());
		boolean error = crashes > 0 || mine.stream().anyMatch(e -> e.kind() == DebugLog.Kind.ERROR);
		StringBuilder sb = new StringBuilder(error ? "FAIL " : "PASS ").append(m.name());
		if (crashes > 0) sb.append(" crashed x").append(crashes);
		else if (selfOff) sb.append(" (turned itself off)");
		if (!m.info().isEmpty()) sb.append(" info=").append(m.info());
		for (DebugLog.Entry e : mine) {
			sb.append("\n    ").append(e.kind()).append(' ').append(e.message());
			String first = firstBreezFrame(e.stack());
			if (!first.isEmpty()) sb.append("\n      ").append(first);
		}
		LOG.add(sb.toString());
		if (error) fail++;
		else pass++;
	}

	private static String firstBreezFrame(String stack) {
		for (String line : stack.split("\\R")) if (line.contains("at breezeclient.")) return line.trim();
		return "";
	}

	private static void finish(Minecraft mc) {
		finished = true;
		LOG.add(0, "RESULT pass=" + pass + " fail=" + fail + " skipped=" + skipped);
		try {
			Files.write(FabricLoader.getInstance().getGameDir().resolve("all-test.txt"), LOG);
		} catch (Exception ignored) {
			// nothing else to do
		}
		mc.stop();
	}
}
