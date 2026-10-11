package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.integration.ProxyRelay;
import breezeclient.breezeclient.client.integration.RadioPlayer;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.net.ProxyHandshake;
import breezeclient.breezeclient.core.settings.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Developer test (BREEZ_SELFTEST=batch): drives the second batch of modules in a singleplayer world and
 * checks that each did what it says, then writes run/batch-test.txt and quits. Every line is PASS or FAIL.
 */
public final class ModuleBatchTest {
	private static int ticks = -1;
	private static final List<String> LOG = new ArrayList<>();
	private static final TreeMap<Integer, List<Runnable>> STEPS = new TreeMap<>();
	private static boolean built;
	private static volatile String proxyResult = "not run";
	private static volatile String radioResult = "not run";

	private ModuleBatchTest() {}

	public static boolean register() {
		if (!"batch".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> {
			ticks = 0;
			if (!built) {
				built = true;
				build();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ModuleBatchTest::tick);
		return true;
	}

	// ---- helpers --------------------------------------------------------------------------------------------------

	private static Minecraft mc() { return Minecraft.getInstance(); }

	private static void cmd(String c) {
		var server = mc().getSingleplayerServer();
		if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c));
	}

	private static void as(String c) { cmd("execute as @a at @s run " + c); }

	private static Module mod(String name) {
		Module m = BreezClientClient.MODULES.get(name);
		if (m == null) LOG.add("FAIL missing module " + name);
		return m;
	}

	private static void on(String name) {
		Module m = mod(name);
		if (m != null) m.setEnabled(true);
	}

	private static void off(String name) {
		Module m = mod(name);
		if (m != null) m.setEnabled(false);
	}

	@SuppressWarnings("unchecked")
	private static void set(String module, String setting, Object value) {
		Module m = mod(module);
		if (m == null) return;
		for (Setting<?> s : m.settings()) if (s.name().equals(setting)) ((Setting<Object>) s).set(value);
	}

	private static void check(String name, boolean ok, String detail) { LOG.add((ok ? "PASS " : "FAIL ") + name + (detail.isEmpty() ? "" : " (" + detail + ")")); }

	private static void at(int tick, Runnable r) { STEPS.computeIfAbsent(tick, k -> new ArrayList<>()).add(r); }

	private static int count(Item item) {
		int n = 0;
		var p = mc().player;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
		return n;
	}

	private static int slot() { return mc().player.getInventory().getSelectedSlot(); }

	private static void give(String where, String item) { as("item replace entity @s " + where + " with " + item); }

	// ---- the scenario ----------------------------------------------------------------------------------------------

	private static void build() {
		// build the platform first and only then stand on it: a player inside a fill suffocates
		at(20, () -> {
			cmd("gamemode survival @a");
			cmd("clear @a");
			cmd("fill -6 145 -6 6 149 6 minecraft:stone");
			cmd("fill -6 150 -6 6 153 6 minecraft:air");
		});
		at(35, () -> {
			as("tp @s 0.5 150 0.5");
			give("hotbar.0", "minecraft:diamond_sword");
			give("hotbar.1", "minecraft:diamond_pickaxe");
			give("hotbar.3", "minecraft:ender_pearl 4");
			give("hotbar.4", "minecraft:totem_of_undying");
			give("hotbar.5", "minecraft:totem_of_undying");
			give("hotbar.6", "minecraft:iron_spear");
			give("hotbar.7", "minecraft:wind_charge 4");
		});
		at(50, () -> {
			mc().player.getInventory().setSelectedSlot(0);
			mc().player.setXRot(90);
		});

		// Keys helper: mouse-bound bindings must be released too (KeyMapping.setAll() skips them)
		at(55, () -> {
			var o = mc().options;
			breezeclient.breezeclient.client.input.Keys.press(o.keyUse);
			boolean pressed = o.keyUse.isDown();
			breezeclient.breezeclient.client.input.Keys.release(o.keyUse);
			check("Keys.press/release handle the use button", pressed && !o.keyUse.isDown(), "pressed " + pressed + " afterwards " + o.keyUse.isDown());
		});

		// KeyPearl + KeyWindCharge
		at(60, () -> {
			mc().player.setXRot(-30);
			pearlsBefore = count(Items.ENDER_PEARL);
			on("KeyPearl");
		});
		at(72, () -> {
			check("KeyPearl throws one pearl", count(Items.ENDER_PEARL) == pearlsBefore - 1, "before " + pearlsBefore + " after " + count(Items.ENDER_PEARL));
			check("KeyPearl returns to the held slot", slot() == 0, "slot " + slot());
			boolean flying = false;
			for (Entity e : mc().level.entitiesForRendering()) flying |= e instanceof ThrownEnderpearl;
			check("KeyPearl: a pearl is in the world", flying, "");
			check("KeyPearl switches itself off", !mod("KeyPearl").enabled(), "");
			windBefore = count(Items.WIND_CHARGE);
			on("KeyWindCharge");
		});
		at(84, () -> {
			check("KeyWindCharge throws one charge", count(Items.WIND_CHARGE) == windBefore - 1, "before " + windBefore + " after " + count(Items.WIND_CHARGE));
			check("KeyWindCharge returns to the held slot", slot() == 0, "slot " + slot());
			as("tp @s 0.5 150 0.5");
		});

		// AutoTool, then AutoMine (looking straight down at the stone platform)
		at(100, () -> {
			mc().player.setXRot(90);
			on("AutoTool");
		});
		at(103, () -> mc().options.keyAttack.setDown(true));
		at(110, () -> {
			check("AutoTool picks the pickaxe", slot() == 1, "slot " + slot() + ", hit " + mc().hitResult + ", attackDown " + mc().options.keyAttack.isDown() + ", screen " + mc().screen + ", enabled " + mod("AutoTool").enabled());
			mc().options.keyAttack.setDown(false);
		});
		at(114, () -> check("AutoTool restores the slot", slot() == 0, "slot " + slot()));
		at(115, () -> off("AutoTool"));

		at(120, () -> {
			cmd("fill 0 144 0 0 144 0 minecraft:chest");
			mc().player.getInventory().setSelectedSlot(1);
			mc().player.setXRot(90);
			on("AutoMine");
		});
		at(200, () -> {
			var level = mc().level;
			boolean stoneGone = level.getBlockState(new net.minecraft.core.BlockPos(0, 149, 0)).isAir() && level.getBlockState(new net.minecraft.core.BlockPos(0, 148, 0)).isAir();
			check("AutoMine breaks the stone under the crosshair", stoneGone, "149=" + level.getBlockState(new net.minecraft.core.BlockPos(0, 149, 0)).getBlock() + " 148=" + level.getBlockState(new net.minecraft.core.BlockPos(0, 148, 0)).getBlock() + " 147=" + level.getBlockState(new net.minecraft.core.BlockPos(0, 147, 0)).getBlock() + " attackDown=" + mc().options.keyAttack.isDown() + " grabbed=" + mc().mouseHandler.isMouseGrabbed() + " screen=" + mc().screen + " hit=" + mc().hitResult + " enabled=" + mod("AutoMine").enabled() + " playerY=" + mc().player.getY());
			boolean chest = level.getBlockState(new net.minecraft.core.BlockPos(0, 144, 0)).is(net.minecraft.world.level.block.Blocks.CHEST);
			// the column is 145-149 stone; the chest sits below it, so it must survive however far the mining went
			check("AutoMine never breaks a container", chest, "");
			off("AutoMine");
			check("AutoMine lets go of the attack button", !mc().options.keyAttack.isDown(), "keyAttack down");
			mc().player.getInventory().setSelectedSlot(0);
			as("tp @s 3.5 150 -4.5");
		});

		// Sprint
		at(215, () -> {
			mc().options.keyUp.setDown(true);
			on("Sprint");
		});
		at(230, () -> {
			check("Sprint sprints while moving forward", mc().player.isSprinting(), "");
			mc().options.keyUp.setDown(false);
			off("Sprint");
		});
		at(232, () -> as("tp @s -3.5 150 -3.5"));

		// ItemDropper
		at(235, () -> {
			cmd("clear @a minecraft:cobblestone"); // what AutoMine collected
			give("inventory.5", "minecraft:cobblestone 64");
			give("inventory.6", "minecraft:cobblestone 64");
			give("inventory.7", "minecraft:dirt 10");
		});
		at(245, () -> {
			set("ItemDropper", "Items", List.of("minecraft:cobblestone"));
			on("ItemDropper");
		});
		at(272, () -> {
			check("ItemDropper drops the listed items", count(Items.COBBLESTONE) == 0, "left " + count(Items.COBBLESTONE));
			check("ItemDropper keeps the rest", count(Items.DIRT) == 10, "dirt " + count(Items.DIRT));
			check("ItemDropper switches itself off when done", !mod("ItemDropper").enabled(), "");
		});

		// AutoInvTotem
		at(295, () -> {
			give("weapon.offhand", "minecraft:air");
			give("inventory.11", "minecraft:totem_of_undying");
		});
		at(305, () -> {
			mc().setScreen(new InventoryScreen(mc().player));
			set("AutoInvTotem", "Delay (ticks)", 1.0);
			on("AutoInvTotem");
		});
		at(335, () -> {
			check("AutoInvTotem fills the offhand", mc().player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "offhand " + mc().player.getOffhandItem());
			mc().setScreen(null);
			off("AutoInvTotem");
		});

		// TotemOffhand
		at(340, () -> {
			give("weapon.offhand", "minecraft:air");
			give("inventory.11", "minecraft:air");
			mc().player.getInventory().setSelectedSlot(0);
		});
		at(350, () -> {
			set("TotemOffhand", "Delay (ticks)", 1.0);
			on("TotemOffhand");
		});
		at(385, () -> {
			check("TotemOffhand fills the offhand", mc().player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "offhand " + mc().player.getOffhandItem());
			check("TotemOffhand returns to the held slot", slot() == 0, "slot " + slot());
			off("TotemOffhand");
		});

		// AutoDoubleHand (offhand now holds a totem; hotbar.5 holds one)
		at(390, () -> {
			give("hotbar.5", "minecraft:totem_of_undying");
			on("AutoDoubleHand");
			as("damage @s 13 minecraft:generic");
		});
		at(400, () -> {
			check("AutoDoubleHand holds a totem at low health", mc().player.getMainHandItem().is(Items.TOTEM_OF_UNDYING), "main " + mc().player.getMainHandItem() + " hp " + mc().player.getHealth());
			as("effect give @s minecraft:instant_health 1 5");
		});
		at(430, () -> {
			check("AutoDoubleHand goes back once healthy", slot() == 0, "slot " + slot() + " hp " + mc().player.getHealth());
			off("AutoDoubleHand");
		});

		// AutoJumpReset
		at(435, () -> {
			on("AutoJumpReset");
			jumped = false;
		});
		at(440, () -> as("damage @s 1 minecraft:generic"));
		for (int t = 441; t <= 450; t++) at(t, () -> {
			var p = mc().player;
			if (!p.onGround() || p.getDeltaMovement().y > 0.1) jumped = true;
		});
		at(452, () -> {
			check("AutoJumpReset jumps when hit", jumped, "");
			off("AutoJumpReset");
		});

		// NoHitDelay
		at(455, () -> {
			mc().missTime = 10;
			on("NoHitDelay");
		});
		at(458, () -> {
			check("NoHitDelay clears the miss timer", mc().missTime == 0, "missTime " + mc().missTime);
			off("NoHitDelay");
		});

		// SpearSwap
		at(460, () -> {
			give("hotbar.0", "minecraft:diamond_sword");
			mc().player.getInventory().setSelectedSlot(0);
			as("summon zombie ^ ^ ^2 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}");
			on("SpearSwap");
		});
		at(475, () -> {
			var zs = mc().level.getEntitiesOfClass(Zombie.class, mc().player.getBoundingBox().inflate(6));
			if (zs.isEmpty()) {
				LOG.add("FAIL SpearSwap: no zombie to hit");
				return;
			}
			mc().gameMode.attack(mc().player, zs.get(0));
		});
		at(478, () -> check("SpearSwap switches to the spear", slot() == 6, "slot " + slot()));
		at(492, () -> {
			check("SpearSwap switches back", slot() == 0, "slot " + slot());
			off("SpearSwap");
			as("kill @e[type=zombie,distance=..8]");
		});

		// Fakeplayer
		at(495, () -> on("Fakeplayer"));
		at(500, () -> {
			boolean found = false;
			for (Entity e : mc().level.entitiesForRendering()) found |= e instanceof RemotePlayer r && r.getName().getString().equals("Fakeplayer");
			check("Fakeplayer is in the world", found, "");
			off("Fakeplayer");
		});
		at(505, () -> {
			boolean found = false;
			for (Entity e : mc().level.entitiesForRendering()) found |= e instanceof RemotePlayer r && r.getName().getString().equals("Fakeplayer");
			check("Fakeplayer is removed on disable", !found, "");
		});

		// BlockNotifier
		at(510, () -> {
			cmd("setblock 6 150 0 minecraft:diamond_ore");
			on("BlockNotifier");
		});
		// a block change is picked up by the scanner's round-robin refresh, which takes a while in a world with many chunks
		at(705, () -> {
			boolean said = false;
			for (String s : Toasts.history()) said |= s.toLowerCase().contains("diamond ore at 6 150 0");
			var hist = Toasts.history();
			check("BlockNotifier announces a listed block", said, "enabled " + mod("BlockNotifier").enabled() + ", info '" + mod("BlockNotifier").info() + "', last toasts " + hist.subList(Math.max(0, hist.size() - 3), hist.size()));
			off("BlockNotifier");
		});

		// ChunkFinder, LightFinder, SuspiciousESP share one batch of blocks far away in one chunk
		at(565, () -> {
			cmd("forceload add 192 192 223 223");
			as("tp @s 205.5 130 205.5");
		});
		at(600, () -> {
			for (int i = 0; i < 3; i++) cmd("setblock " + (200 + i) + " 100 200 minecraft:spawner");
			for (int i = 0; i < 4; i++) cmd("setblock " + (200 + i) + " 100 204 minecraft:stone");
			for (int i = 0; i < 2; i++) cmd("setblock " + (200 + i) + " 100 208 minecraft:hopper");
		});
		at(610, () -> {
			for (int i = 0; i < 4; i++) cmd("setblock " + (200 + i) + " 101 204 minecraft:torch");
		});
		at(620, () -> {
			set("ChunkFinder", "Ignore chunks within", 0.0);
			set("ChunkFinder", "Min per chunk", 3.0);
			set("LightFinder", "Highest Y", 200.0);
			set("LightFinder", "Min per chunk", 3.0);
			on("ChunkFinder");
			on("LightFinder");
			on("SuspiciousESP");
		});
		at(840, () -> {
			check("ChunkFinder flags a chunk with 3 spawners", !breezeclient.breezeclient.client.modules.DonutModules.ChunkFinder.top(5).isEmpty(), "info " + mod("ChunkFinder").info());
			check("LightFinder counts the torches", !mod("LightFinder").info().isEmpty(), "info " + mod("LightFinder").info());
			check("SuspiciousESP finds hoppers", !mod("SuspiciousESP").info().isEmpty() && !mod("SuspiciousESP").info().equals("0"), "info " + mod("SuspiciousESP").info());
			off("ChunkFinder");
			off("LightFinder");
			off("SuspiciousESP");
		});

		// AutoHitCrystal (creative, so the blast cannot hurt)
		at(695, () -> {
			cmd("gamemode creative @a");
			as("tp @s -3.5 150 -3.5");
		});
		at(720, () -> {
			mc().player.setXRot(0);
		});
		at(726, () -> as("summon end_crystal ^ ^0.62 ^3 {ShowBottom:0b}"));
		at(735, () -> {
			set("AutoHitCrystal", "Delay (ticks)", 0.0);
			on("AutoHitCrystal");
		});
		at(790, () -> {
			boolean gone = mc().level.getEntitiesOfClass(EndCrystal.class, mc().player.getBoundingBox().inflate(10)).isEmpty();
			check("AutoHitCrystal breaks the crystal under the crosshair", gone, "");
			off("AutoHitCrystal");
		});

		// AutoSpawnerSell on a renamed chest
		at(795, () -> {
			cmd("gamemode survival @a");
			as("tp @s -3.5 150 -3.5");
			cmd("setblock -3 150 -1 minecraft:chest{CustomName:\"Spawner Storage\",Items:[{Slot:0b,id:\"minecraft:stick\",count:1,components:{\"minecraft:custom_name\":\"Sell all\"}},{Slot:1b,id:\"minecraft:dirt\",count:5}]}");
		});
		at(815, () -> {
			on("AutoSpawnerSell");
			set("AutoSpawnerSell", "Click delay (ticks)", 1.0);
		});
		at(820, () -> {
			var p = mc().player;
			var pos = new net.minecraft.core.BlockPos(-3, 150, -1);
			mc().gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
		});
		at(860, () -> {
			check("AutoSpawnerSell presses the sell button", "1".equals(mod("AutoSpawnerSell").info()), "info '" + mod("AutoSpawnerSell").info() + "'");
			check("AutoSpawnerSell closes the window", mc().screen == null, "screen " + mc().screen);
			off("AutoSpawnerSell");
		});

		// ProxyRelay against a tiny local SOCKS5 server, and the radio decoder against a live stream
		at(865, ModuleBatchTest::proxyTest);
		at(866, ModuleBatchTest::radioTest);

		at(1100, () -> {
			LOG.add("proxy relay: " + proxyResult);
			LOG.add("radio: " + radioResult);
			int crashes = breezeclient.breezeclient.core.ModuleManager.crashCount();
			check("no module crashed", crashes == 0, "moduleCrashes=" + crashes);
			try {
				Files.writeString(FabricLoader.getInstance().getGameDir().resolve("batch-test.txt"), String.join("\n", LOG) + "\n");
			} catch (Exception ignored) {
				// nothing else to do
			}
			mc().stop();
		});
	}

	private static int pearlsBefore, windBefore;
	private static boolean jumped;

	private static void tick(Minecraft mc) {
		if (ticks < 0 || mc.player == null || mc.level == null) return;
		ticks++;
		mc.options.pauseOnLostFocus = false; // the window is not focused during an unattended run
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
		var now = STEPS.get(ticks);
		if (now == null) return;
		for (Runnable r : now) {
			try {
				r.run();
			} catch (RuntimeException e) {
				LOG.add("FAIL step at tick " + ticks + " threw " + e);
			}
		}
	}

	// ---- network tests (run on their own threads) -----------------------------------------------------------------------

	/** A one-shot SOCKS5 server that answers the handshake, then echoes what it receives. */
	private static void proxyTest() {
		var previous = ProxyRelay.source();
		Thread t = new Thread(() -> {
			try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
				int proxyPort = server.getLocalPort();
				Thread fake = new Thread(() -> {
					try (Socket s = server.accept()) {
						InputStream in = s.getInputStream();
						OutputStream out = s.getOutputStream();
						in.readNBytes(3); // greeting
						out.write(new byte[] {5, 0});
						byte[] req = in.readNBytes(10); // 5 1 0 1 + 4 address bytes + 2 port bytes
						out.write(new byte[] {5, 0, 0, 1, 0, 0, 0, 0, 0, 0});
						out.flush();
						byte[] data = in.readNBytes(5);
						out.write(data);
						out.flush();
						proxyTarget = (req[4] & 0xFF) + "." + (req[5] & 0xFF) + "." + (req[6] & 0xFF) + "." + (req[7] & 0xFF) + ":" + (((req[8] & 0xFF) << 8) | (req[9] & 0xFF));
					} catch (Exception e) {
						proxyResult = "FAIL fake proxy " + e;
					}
				}, "batch-fake-proxy");
				fake.setDaemon(true);
				fake.start();
				ProxyRelay.setSource(() -> new ProxyRelay.Config(ProxyHandshake.Type.SOCKS5, "127.0.0.1", proxyPort, "", ""));
				InetSocketAddress local = ProxyRelay.redirect(new InetSocketAddress(InetAddress.getByAddress(new byte[] {(byte) 203, 0, 113, 7}), 25565));
				try (Socket c = new Socket(local.getAddress(), local.getPort())) {
					c.setSoTimeout(5000);
					c.getOutputStream().write("hello".getBytes());
					c.getOutputStream().flush();
					String echo = new String(c.getInputStream().readNBytes(5));
					fake.join(2000);
					boolean ok = echo.equals("hello") && "203.0.113.7:25565".equals(proxyTarget);
					proxyResult = (ok ? "PASS" : "FAIL") + " echo=" + echo + " target=" + proxyTarget;
				}
			} catch (Exception e) {
				proxyResult = "FAIL " + e;
			} finally {
				ProxyRelay.setSource(previous); // hand the real Proxy module its hook back
			}
		}, "batch-proxy-test");
		t.setDaemon(true);
		t.start();
	}

	private static volatile String proxyTarget = "";

	/** Plays the Radio Paradise MP3 stream for a few seconds; it reports "Playing" once frames decode and the line opens. */
	private static void radioTest() {
		StringBuilder sb = new StringBuilder();
		RadioPlayer p = new RadioPlayer(s -> {
			synchronized (sb) {
				sb.append(s).append(" | ");
			}
		});
		p.setVolume(0);
		p.play("https://stream.radioparadise.com/mp3-128");
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(9000);
			} catch (InterruptedException ignored) {
				// finish early
			}
			p.stop();
			synchronized (sb) {
				radioResult = (sb.indexOf("Playing") >= 0 ? "PASS " : "FAIL ") + sb;
			}
		}, "batch-radio-test");
		t.setDaemon(true);
		t.start();
	}
}
