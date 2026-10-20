package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer test (BREEZ_SELFTEST=automsg): kills a 1-health zombie and checks AutoMessage types the kill message after its
 * delay, kills two more at once and checks the cooldown lets only one message through, then dies and checks the death
 * message. Writes run/automsg-test.txt and quits.
 */
public final class AutoMessageTest {
	private static final String ZOMBIE = "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:1f}";
	private static final List<String> LOG = new ArrayList<>();
	/** Chat lines the client received, with the tick they arrived on. */
	private static final List<String> CHAT = new ArrayList<>();
	private static final List<Integer> CHAT_AT = new ArrayList<>();
	private static int ticks = -1;
	private static int killAt, doubleAt, deathAt;
	private static boolean finished;

	private AutoMessageTest() {}

	public static boolean register() {
		if (!"automsg".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> ticks = 0);
		ClientTickEvents.END_CLIENT_TICK.register(AutoMessageTest::tick);
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> {
			CHAT.add(message.getString());
			CHAT_AT.add(ticks);
		});
		return true;
	}

	private static void cmd(Minecraft mc, String c) {
		var server = mc.getSingleplayerServer();
		if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c));
	}

	@SuppressWarnings("unchecked")
	private static void set(Module m, String setting, Object value) {
		for (Setting<?> s : m.settings()) if (s.name().equals(setting)) ((Setting<Object>) s).set(value);
	}

	private static void check(boolean ok, String what) { LOG.add((ok ? "PASS " : "FAIL ") + what); }

	/** Hits every zombie within reach once. */
	private static int hitZombies(Minecraft mc) {
		int n = 0;
		for (Entity e : mc.level.entitiesForRendering())
			if (e instanceof Zombie z && z.isAlive() && z.distanceTo(mc.player) < 3.5) {
				mc.gameMode.attack(mc.player, z);
				n++;
			}
		return n;
	}

	private static int zombiesNear(Minecraft mc) {
		int n = 0;
		for (Entity e : mc.level.entitiesForRendering()) if (e instanceof Zombie z && z.isAlive() && z.distanceTo(mc.player) < 3.5) n++;
		return n;
	}

	/** Chat lines containing {@code text} that arrived after tick {@code from}. */
	private static List<Integer> arrivals(String text, int from) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < CHAT.size(); i++) if (CHAT_AT.get(i) > from && CHAT.get(i).contains(text)) out.add(CHAT_AT.get(i));
		return out;
	}

	private static void tick(Minecraft mc) {
		if (ticks < 0 || mc.player == null || mc.level == null || finished) return;
		ticks++;
		mc.options.pauseOnLostFocus = false;
		if (ticks < 25) SelfTest.reviveAtStart(mc); // an earlier test may have left the player dead
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
		var p = mc.player;
		Module am = BreezClientClient.MODULES.get("AutoMessage");
		try {
			if (ticks == 10) {
				cmd(mc, "gamemode survival @a");
				cmd(mc, "clear @a");
				cmd(mc, "time set day");
				cmd(mc, "fill -6 140 -6 6 149 6 minecraft:stone");
				cmd(mc, "fill -6 150 -6 6 160 6 minecraft:air");
				cmd(mc, "fill -6 154 -6 6 154 6 minecraft:stone"); // a roof: the 1-health zombies would otherwise burn in the sun before they are hit
				cmd(mc, "kill @e[type=!minecraft:player,x=-20,y=130,z=-20,dx=40,dy=50,dz=40]");
				cmd(mc, "give @a minecraft:diamond_sword");
			}
			if (ticks == 30) {
				cmd(mc, "execute as @a run tp @s 0.5 150 0.5 0 0");
				p.getInventory().setSelectedSlot(0);
				set(am, "Players only", false); // no second player in singleplayer: kill zombies instead
				set(am, "Kill message", "gg {player}");
				set(am, "On death", true);
				set(am, "Death message", "rip{killer}");
				set(am, "Delay (s)", 2.0);
				set(am, "Random extra delay (s)", 0.0);
				set(am, "Cooldown (s)", 3.0);
				am.setEnabled(true);
			}

			// 1. one kill: "gg Zombie" about 2 s (40 ticks) later
			if (ticks == 40) cmd(mc, "summon minecraft:zombie 0.5 150 2.5 " + ZOMBIE);
			if (ticks == 60) {
				killAt = ticks;
				LOG.add("hit " + hitZombies(mc) + " zombie(s) at tick " + killAt);
			}
			if (ticks == 140) {
				var got = arrivals("gg Zombie", killAt);
				LOG.add("chat so far: " + CHAT);
				check(got.size() == 1, "one kill message was typed (" + got.size() + ")");
				int lag = got.isEmpty() ? -1 : got.get(0) - killAt;
				check(lag >= 36 && lag <= 50, "it came after the 2 s delay (" + lag + " ticks)");
			}

			// 2. two kills at once: the cooldown lets one message through
			if (ticks == 150) {
				cmd(mc, "summon minecraft:zombie 1.5 150 2.5 " + ZOMBIE);
				cmd(mc, "summon minecraft:zombie -0.5 150 2.5 " + ZOMBIE);
			}
			// both have to be on the client first: a busy server can send them a few ticks late
			if (ticks >= 170 && ticks <= 190 && doubleAt == 0 && (zombiesNear(mc) >= 2 || ticks == 190)) {
				doubleAt = ticks;
				LOG.add("hit " + hitZombies(mc) + " zombie(s) at tick " + doubleAt);
			}
			if (ticks == 260) {
				var got = arrivals("gg Zombie", doubleAt);
				check(got.size() == 1, "two kills at once typed one message, the cooldown skipped the other (" + got.size() + ")");
			}

			// 3. death: "rip" (no player killed us, so {killer} is empty)
			if (ticks == 270) {
				deathAt = ticks;
				cmd(mc, "kill @a");
			}
			if (ticks == 290) LOG.add("death screen up: " + (mc.screen instanceof DeathScreen));
			if (ticks == 360) {
				var got = arrivals("rip", deathAt);
				LOG.add("chat so far: " + CHAT);
				check(got.size() == 1, "death message was typed (" + got.size() + ")");
				int lag = got.isEmpty() ? -1 : got.get(0) - deathAt;
				check(lag >= 36 && lag <= 50, "it came after the 2 s delay (" + lag + " ticks)");
				check(CHAT.stream().noneMatch(s -> s.contains("{")), "no {placeholder} was left in any message");
				p.respawn();
				mc.setScreen(null);
				LOG.add("kills/deaths shown in the module list: " + am.info());
			}
			if (ticks == 380) finish(mc);
		} catch (Exception e) {
			LOG.add("FAIL test threw " + e);
			finish(mc);
		}
	}

	private static void finish(Minecraft mc) {
		if (finished) return;
		finished = true;
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("automsg-test.txt"), String.join("\n", LOG) + "\n");
		} catch (Exception ignored) {
			// nothing else to do
		}
		mc.stop();
	}
}
