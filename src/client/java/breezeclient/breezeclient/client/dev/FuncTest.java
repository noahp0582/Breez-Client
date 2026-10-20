package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.debug.DebugLog;
import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.modules.AlertWebhook;
import breezeclient.breezeclient.client.modules.CombatTweaks;
import breezeclient.breezeclient.client.modules.CustomFov;
import breezeclient.breezeclient.client.modules.FreeLook;
import breezeclient.breezeclient.client.modules.Zoom;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.Setting;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Developer test (BREEZ_SELFTEST=func): checks that each module without a test of its own actually does its job in a
 * singleplayer world: ESP and finders find what was placed and draw it, camera modules change the camera, combat and
 * inventory modules click, swap and attack, automation digs, sells and teleports. Rendering is checked by rendering the
 * module into a frame that counts what it draws, the crosshair by reading the pixels of a screenshot. The last step lets
 * AutoLog disconnect. BREEZ_SELFTEST=killsw only checks that KillSwitch disconnects for another player. Writes
 * run/func-test.txt (or run/killsw-test.txt) and quits. Every line is PASS or FAIL.
 */
public final class FuncTest {
	private static String mode;
	private static int ticks = -1;
	private static boolean built, finished, expectDisconnect;
	private static final List<String> LOG = new ArrayList<>();
	private static final TreeMap<Integer, List<Runnable>> STEPS = new TreeMap<>();
	private static final List<Await> AWAITS = new ArrayList<>();
	private static final List<String> GAME_MSGS = new ArrayList<>();
	private static int attacks;
	private static int pass, fail;

	private record Await(String name, int deadline, BooleanSupplier ok, Supplier<String> detail) {}

	/** Counts what a module draws. */
	private static final class Counter implements RenderFrame {
		int boxes, lines, texts;

		@Override
		public void drawBox(double x1, double y1, double z1, double x2, double y2, double z2, int argb) { boxes++; }

		@Override
		public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) { lines++; }

		@Override
		public void drawText(double x, double y, double z, String text, int argb) { texts++; }

		int total() { return boxes + lines + texts; }

		@Override
		public String toString() { return boxes + " boxes, " + lines + " lines, " + texts + " labels"; }
	}

	private FuncTest() {}

	public static boolean register() {
		String m = System.getenv("BREEZ_SELFTEST");
		if (!"func".equalsIgnoreCase(m) && !"killsw".equalsIgnoreCase(m)) return false;
		mode = m.toLowerCase(java.util.Locale.ROOT);
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> {
			ticks = 0;
			if (!built) {
				built = true;
				if (mode.equals("func")) build();
				else buildKillSwitch();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
			if (finished) return;
			check(mode.equals("func") ? "AutoLog disconnects at low health" : "KillSwitch disconnects when a player comes near", expectDisconnect,
				expectDisconnect ? "" : "disconnected before the last step");
			finish(mc);
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) GAME_MSGS.add(message.getString()); });
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) attacks++;
			return InteractionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(FuncTest::tick);
		return true;
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	private static Minecraft mc() { return Minecraft.getInstance(); }

	private static void cmd(String c) {
		var server = mc().getSingleplayerServer();
		if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c));
	}

	private static void as(String c) { cmd("execute as @a at @s run " + c); }

	private static Module mod(String name) {
		Module m = BreezClientClient.MODULES.get(name);
		if (m == null) check("module " + name + " exists", false, "");
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
		for (Setting<?> s : m.settings()) {
			if (s.name().equals(setting)) {
				((Setting<Object>) s).set(value);
				return;
			}
		}
		check(module + " has a setting '" + setting + "'", false, "");
	}

	private static String info(String name) {
		Module m = mod(name);
		return m == null ? "" : m.info();
	}

	private static int infoInt(String name) {
		String s = info(name).replaceAll("[^0-9].*$", "");
		try {
			return s.isEmpty() ? 0 : Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static void check(String name, boolean ok, String detail) {
		LOG.add((ok ? "PASS " : "FAIL ") + name + (detail == null || detail.isEmpty() ? "" : " (" + detail + ")"));
		if (ok) pass++;
		else fail++;
	}

	private static void at(int tick, Runnable r) { STEPS.computeIfAbsent(tick, k -> new ArrayList<>()).add(r); }

	/** Passes as soon as {@code ok} holds, fails if it still does not after {@code within} ticks. */
	private static void await(int from, int within, String name, BooleanSupplier ok, Supplier<String> detail) {
		at(from, () -> AWAITS.add(new Await(name, ticks + within, ok, detail)));
	}

	private static Counter render() {
		Counter c = new Counter();
		BreezClientClient.MODULES.render(c);
		return c;
	}

	private static Object field(Object owner, String name) {
		Class<?> c = owner instanceof Class<?> k ? k : owner.getClass();
		Object target = owner instanceof Class<?> ? null : owner;
		while (c != null) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(target);
			} catch (NoSuchFieldException e) {
				c = c.getSuperclass();
			} catch (IllegalAccessException e) {
				return null;
			}
		}
		return null;
	}

	private static void setField(Object owner, String name, Object value) {
		Class<?> c = owner.getClass();
		while (c != null) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				f.set(owner, value);
				return;
			} catch (NoSuchFieldException e) {
				c = c.getSuperclass();
			} catch (IllegalAccessException e) {
				return;
			}
		}
	}

	/** LivingEntity's own flag setter (1 = using an item, 2 = with the offhand), which only the server normally calls. */
	private static void setLivingFlag(Entity e, int flag, boolean on) {
		try {
			var m = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("setLivingEntityFlag", int.class, boolean.class);
			m.setAccessible(true);
			m.invoke(e, flag, on);
		} catch (ReflectiveOperationException ex) {
			LOG.add("note: could not set the living flags: " + ex);
		}
	}

	private static int count(Item item) {
		var inv = mc().player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
		return n;
	}

	private static void give(String where, String item) { as("item replace entity @s " + where + " with " + item); }

	private static boolean toasted(String text) {
		for (String t : Toasts.history()) if (t.contains(text)) return true;
		return false;
	}

	private static Zombie zombie() {
		Zombie best = null;
		for (Entity e : mc().level.entitiesForRendering()) {
			if (e instanceof Zombie z && z.isAlive() && z.distanceTo(mc().player) < 6 && (best == null || z.distanceTo(mc().player) < best.distanceTo(mc().player))) best = z;
		}
		return best;
	}

	private static void look(float yaw, float pitch) {
		var p = mc().player;
		p.setYRot(yaw);
		p.setXRot(pitch);
		p.yRotO = yaw;
		p.xRotO = pitch;
	}

	/** Turns to face the middle of an entity. */
	private static void lookAt(Entity e) {
		var p = mc().player;
		Vec3 d = e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(p.getEyePosition());
		look((float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
	}

	private static RemotePlayer otherPlayer(String name, int id, boolean spectator, Vec3 at) {
		var level = mc().level;
		RemotePlayer rp = new RemotePlayer(level, new GameProfile(UUID.randomUUID(), name)) {
			@Override
			public boolean isSpectator() { return spectator; }
		};
		rp.setId(id);
		rp.snapTo(at.x, at.y, at.z, 0, 0);
		rp.setHealth(20f);
		level.addEntity(rp);
		return rp;
	}

	private static void removeEntity(Entity e) {
		if (e != null && mc().level != null) mc().level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
	}

	/** One ESP or finder: enable it with nothing else on, then see that it found what was placed and draws it. */
	private static void esp(int t, String name, int minInfo, boolean mustDraw) {
		int[] base = new int[1];
		at(t, () -> {
			base[0] = render().total();
			on(name);
		});
		await(t + 2, 60, name + " finds the placed blocks" + (mustDraw ? " and draws them" : ""), () -> {
			if (minInfo > 0 && infoInt(name) < minInfo) return false;
			return !mustDraw || render().total() > base[0];
		}, () -> "info '" + info(name) + "', drew " + render() + " (with it off: " + base[0] + " things)");
		at(t + 64, () -> off(name));
	}

	private static void arena() {
		cmd("gamemode creative @a");
		cmd("effect clear @a");
		cmd("clear @a");
		cmd("time set 6000");
		cmd("weather clear");
		cmd("difficulty easy");
		cmd("forceload add -32 -32 48 48");
		cmd("fill -20 140 -20 30 149 30 minecraft:stone");
		// one fill may change at most 32768 blocks
		cmd("fill -20 150 -20 30 158 30 minecraft:air");
		cmd("fill -20 159 -20 30 167 30 minecraft:air");
		cmd("fill -20 168 -20 30 175 30 minecraft:air");
		cmd("kill @e[type=!minecraft:player,x=-40,y=120,z=-40,dx=90,dy=80,dz=90]");
	}

	// ---- the scenario -------------------------------------------------------------------------------------------------

	private static final String ZOMBIE = "{NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}";

	private static void build() {
		at(5, () -> {
			for (Module m : BreezClientClient.MODULES.all()) if (m.enabled()) m.setEnabled(false);
			cmd("gamemode creative @a");
			as("tp @s 0.5 176 0.5"); // above the arena while it is built
		});
		at(20, FuncTest::arena);
		at(45, () -> {
			as("tp @s 0.5 150 0.5 0 0");
			// things to find: storage, a spawner, a portal, ores, a geode, a hole, entities
			cmd("setblock 5 150 8 minecraft:chest");
			cmd("setblock 6 150 8 minecraft:barrel");
			cmd("setblock 7 150 8 minecraft:chest");
			cmd("setblock 8 150 8 minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\"}}}");
			cmd("setblock 10 150 8 minecraft:nether_portal[axis=x]");
			cmd("setblock 12 150 8 minecraft:ancient_debris");
			cmd("setblock 13 150 8 minecraft:diamond_ore");
			cmd("setblock 15 151 8 minecraft:budding_amethyst");
			cmd("setblock 15 150 8 minecraft:stone");
			cmd("setblock 15 152 8 minecraft:amethyst_cluster");
			cmd("setblock 16 151 8 minecraft:large_amethyst_bud[facing=east]");
			for (String c : new String[] {"2 150 12", "4 150 12", "3 150 11", "3 150 13"}) cmd("setblock " + c + " minecraft:obsidian");
			cmd("summon minecraft:chest_minecart 8.5 150 12.5");
			cmd("summon minecraft:zombie 0.5 150 3.5 " + ZOMBIE);
		});

		// --- finders and ESP --------------------------------------------------------------------------------------
		int t = 80;
		esp(t, "StorageESP", 3, true);
		esp(t += 70, "SpawnerESP", 1, true);
		await(t + 2, 70, "SpawnerESP tells you where the spawner is", () -> toasted("Spawner at 8 150 8"), () -> "alerts " + Toasts.history());
		esp(t += 70, "BlockEntityESP", 0, true);
		esp(t += 70, "PortalESP", 1, true);
		esp(t += 70, "OreFinder", 1, true);
		esp(t += 70, "NetheriteFinder", 1, true);
		esp(t += 70, "ClusterESP", 1, true);
		esp(t += 70, "AmethystDetector", 0, true);
		esp(t += 70, "HoleESP", 1, true);
		esp(t += 70, "MobESP", 0, true);
		esp(t += 70, "EntityStorageESP", 0, true);
		esp(t += 70, "SpawnerNametags", 0, true);
		esp(t += 70, "StashFinder", 1, false);
		at(t += 70, () -> on("BlockEntityDebug"));
		await(t + 2, 60, "BlockEntityDebug counts the block entities in your chunk", () -> !info("BlockEntityDebug").isEmpty(), () -> "info '" + info("BlockEntityDebug") + "'");
		at(t + 64, () -> off("BlockEntityDebug"));

		// --- other players: a client-side fake stands where we were --------------------------------------------------
		at(t += 70, () -> {
			set("Fakeplayer", "Name", "Dummy");
			on("Fakeplayer");
			as("tp @s 0.5 150 -3.5 0 0");
		});
		esp(t += 20, "PlayerESP", 0, true);
		esp(t += 70, "RealHitBox", 0, true);
		at(t += 70, () -> {
			Entity fake = null;
			for (var p : mc().level.players()) if (p != mc().player && p.getName().getString().equals("Dummy")) fake = p;
			if (fake == null) {
				check("StaticHitBoxes keeps a sneaking player's standing height", false, "no fake player");
				return;
			}
			fake.setShiftKeyDown(true);
			fake.setPose(Pose.CROUCHING);
			fake.refreshDimensions();
			float offR = CombatTweaks.StaticHitBoxes.pickRadius(fake);
			on("StaticHitBoxes");
			float onR = CombatTweaks.StaticHitBoxes.pickRadius(fake);
			off("StaticHitBoxes");
			check("StaticHitBoxes keeps a sneaking player's standing height", offR == 0 && onR > 0.1f, "height " + fake.getBbHeight() + ", extra pick radius off " + offR + " on " + onR);
			on("PlayerChunks");
		});
		await(t + 2, 40, "PlayerChunks remembers the chunk the other player is in", () -> infoInt("PlayerChunks") >= 1, () -> "info '" + info("PlayerChunks") + "'");
		at(t + 45, () -> {
			off("PlayerChunks");
			off("Fakeplayer");
			as("tp @s 0.5 150 0.5 0 0");
		});

		// --- camera and screen -----------------------------------------------------------------------------------
		at(t += 60, () -> on("Fullbright"));
		await(t + 1, 20, "Fullbright gives local night vision", () -> mc().player.hasEffect(MobEffects.NIGHT_VISION), () -> "");
		at(t + 25, () -> {
			off("Fullbright");
			check("Fullbright takes it away again", !mc().player.hasEffect(MobEffects.NIGHT_VISION), "");
			float before = CustomFov.apply(70f);
			set("CustomFOV", "FOV", 120.0);
			on("CustomFOV");
			float after = CustomFov.apply(70f);
			off("CustomFOV");
			check("CustomFOV sets the field of view", before == 70f && after == 120f, before + " -> " + after);
			on("Zoom");
			float z = 70f;
			for (int i = 0; i < 60; i++) z = Zoom.apply(70f);
			off("Zoom");
			float back = 70f;
			for (int i = 0; i < 80; i++) back = Zoom.apply(70f);
			check("Zoom narrows the view and eases back", z < 70f / 3 && back == 70f, "zoomed " + z + ", after " + back);
			mc().options.setCameraType(CameraType.FIRST_PERSON);
			on("FreeLook");
			boolean third = !mc().options.getCameraType().isFirstPerson() && FreeLook.active();
			float yaw = mc().player.getYRot();
			FreeLook.turn(400, 0);
			boolean bodyStill = mc().player.getYRot() == yaw && FreeLook.yaw() != yaw;
			off("FreeLook");
			check("FreeLook turns the camera, not the player, in third person", third && bodyStill && mc().options.getCameraType().isFirstPerson(),
				"third person " + third + ", body still " + bodyStill + ", back to " + mc().options.getCameraType());
			var info = mc().getConnection().getPlayerInfo(mc().player.getUUID());
			on("SkinProtect");
			boolean defaultSkin = info != null && info.getSkin().equals(net.minecraft.client.resources.DefaultPlayerSkin.get(info.getProfile()));
			off("SkinProtect");
			check("SkinProtect shows the default skin", defaultSkin, info == null ? "no player info" : "");
		});

		// the crosshair: compare the pixel in the middle of the screen with and without the module
		int[] centre = {0, 0};
		at(t += 40, () -> {
			look(0, -60); // the sky behind the crosshair
			Screenshot.takeScreenshot(mc().getMainRenderTarget(), img -> {
				centre[0] = img.getPixel(img.getWidth() / 2, img.getHeight() / 2);
				img.close();
			});
		});
		at(t += 5, () -> {
			set("CustomCrosshair", "Shape", breezeclient.breezeclient.client.modules.CustomCrosshair.Shape.DOT);
			set("CustomCrosshair", "Thickness", 5.0);
			set("CustomCrosshair", "Outline", false);
			set("CustomCrosshair", "Color", 0xFFFF0000);
			on("CustomCrosshair");
		});
		at(t += 5, () -> Screenshot.takeScreenshot(mc().getMainRenderTarget(), img -> {
			centre[1] = img.getPixel(img.getWidth() / 2, img.getHeight() / 2);
			img.close();
		}));
		at(t += 5, () -> {
			off("CustomCrosshair");
			int c = centre[1];
			int a = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF; // red sits in one of the outer bytes, depending on the image format
			boolean red = g < 60 && ((a > 200 && b < 60) || (b > 200 && a < 60));
			check("CustomCrosshair draws its own crosshair (red dot in the middle)", red && centre[0] != c,
				String.format("pixel without %08X, with %08X", centre[0], c));
			look(0, 0);
		});

		at(t += 5, () -> {
			give("weapon.mainhand", "minecraft:ender_pearl 4");
			on("PearlTrajectory");
		});
		await(t + 2, 20, "PearlTrajectory draws the throw", () -> render().lines > 5, () -> "drew " + render());
		at(t + 25, () -> {
			off("PearlTrajectory");
			give("weapon.mainhand", "minecraft:air");
			on("JumpCircles");
			mc().options.keyJump.setDown(true);
		});
		at(t + 28, () -> mc().options.keyJump.setDown(false));
		await(t + 29, 15, "JumpCircles draws a ring where you jumped", () -> render().lines >= 20, () -> "drew " + render());
		at(t + 50, () -> off("JumpCircles"));

		int[] particles = new int[1];
		at(t += 60, () -> {
			Zombie z = zombie();
			if (z == null) return;
			lookAt(z);
			on("HitParticles");
			on("TargetHUD");
			set("HitParticles", "Amount", 40.0);
			particles[0] = Integer.parseInt(mc().particleEngine.countParticles().trim());
			mc().gameMode.attack(mc().player, z);
		});
		at(t + 2, () -> {
			int now = Integer.parseInt(mc().particleEngine.countParticles().trim());
			check("HitParticles bursts particles on a hit", now >= particles[0] + 20, particles[0] + " -> " + now + " particles");
			Object target = field(breezeclient.breezeclient.client.modules.RenderModules.TargetHud.class, "target");
			check("TargetHUD picks up what you hit", target instanceof Zombie, "target " + target);
			off("HitParticles");
			off("TargetHUD");
		});

		at(t += 10, () -> {
			on("MusicHUD");
			mc().getSoundManager().play(SimpleSoundInstance.forJukeboxSong(SoundEvents.MUSIC_DISC_CAT.value(), mc().player.position()));
		});
		await(t + 2, 60, "MusicHUD shows the record that is playing", () -> !info("MusicHUD").isEmpty(), () -> "info '" + info("MusicHUD") + "'");
		at(t + 65, () -> {
			mc().getSoundManager().stop();
			off("MusicHUD");
			set("NameProtect", "Alias", "HiddenAlias");
			on("NameProtect");
			GAME_MSGS.clear();
			cmd("tellraw @a {\"text\":\"hello " + mc().player.getName().getString() + "\"}");
		});
		await(t + 66, 40, "NameProtect replaces your name in chat", () -> GAME_MSGS.stream().anyMatch(s -> s.contains("hello HiddenAlias")),
			() -> "got " + GAME_MSGS);
		at(t + 110, () -> {
			boolean leaked = GAME_MSGS.stream().anyMatch(s -> s.contains(mc().player.getName().getString()));
			check("NameProtect leaves no trace of the real name", !leaked, "got " + GAME_MSGS);
			off("NameProtect");
			on("WeatherNotifier");
		});
		at(t + 115, () -> cmd("weather thunder"));
		await(t + 116, 200, "WeatherNotifier tells you when it starts raining", () -> toasted("Weather: It started raining") || toasted("Weather: A thunderstorm"), () -> "alerts " + Toasts.history());
		at(t += 320, () -> {
			off("WeatherNotifier");
			cmd("weather clear");
			on("Debug");
			DebugLog.record(DebugLog.Kind.ERROR, "FuncTest", "probe error for the Debug module", null);
		});
		await(t + 1, 20, "Debug pops up errors as they happen", () -> toasted("Error in FuncTest"), () -> "alerts " + Toasts.history());
		at(t + 25, () -> {
			off("Debug");
			look(0, 90); // straight down at the floor
		});
		at(t + 30, () -> {
			mc().keyboardHandler.setClipboard("");
			on("CoordSnapper");
			String clip = mc().keyboardHandler.getClipboard();
			BlockPos under = mc().hitResult instanceof BlockHitResult bh ? bh.getBlockPos() : null;
			check("CoordSnapper copies the block you look at", under != null && clip.equals(under.getX() + " " + under.getY() + " " + under.getZ()), "clipboard '" + clip + "', looking at " + under);
			look(0, 0);
			check("AlertWebhook accepts only Discord webhook addresses", AlertWebhook.validUrl("https://discord.com/api/webhooks/1/abc") && !AlertWebhook.validUrl("http://discord.com/api/webhooks/1/abc")
				&& !AlertWebhook.validUrl("https://evil.example/api/webhooks/1"), "");
			on("AlertWebhook");
			Toasts.show("SpawnerESP", "probe find");
			Object pending = field(mod("AlertWebhook"), "pending");
			check("AlertWebhook queues finds to post", pending != null && pending.toString().contains("probe find"), "queued '" + pending + "'");
			off("AlertWebhook");
		});

		// --- combat and inventory (creative) ----------------------------------------------------------------------
		at(t += 40, () -> {
			give("hotbar.0", "minecraft:diamond_sword");
			mc().player.getInventory().setSelectedSlot(0);
			Zombie z = zombie();
			if (z != null) lookAt(z);
			set("AutoClicker", "Min CPS", 10.0);
			set("AutoClicker", "Max CPS", 14.0);
			on("AutoClicker");
			attacks = 0;
			mc().options.keyAttack.setDown(true);
		});
		await(t + 1, 60, "AutoClicker attacks while you hold attack", () -> attacks >= 2, () -> attacks + " attacks");
		at(t + 62, () -> {
			mc().options.keyAttack.setDown(false);
			off("AutoClicker");
			set("Triggerbot", "Mobs", true);
			// creative hits kill even invulnerable mobs: a fresh one for each check
			cmd("kill @e[type=minecraft:zombie]");
			cmd("summon minecraft:zombie 0.5 150 3.5 " + ZOMBIE);
		});
		at(t + 70, () -> {
			Zombie z = zombie();
			if (z != null) lookAt(z);
			attacks = 0;
			on("Triggerbot");
		});
		await(t + 71, 60, "Triggerbot attacks what the crosshair is on", () -> attacks >= 1, () -> attacks + " attacks");
		at(t + 133, () -> {
			off("Triggerbot");
			cmd("kill @e[type=minecraft:zombie]");
			give("hotbar.2", "minecraft:mace");
			cmd("summon minecraft:zombie 0.5 150 3.5 " + ZOMBIE);
		});
		at(t + 141, () -> {
			mc().player.getInventory().setSelectedSlot(0);
			on("MaceSwap");
			Zombie z = zombie();
			if (z != null) mc().gameMode.attack(mc().player, z);
			check("MaceSwap switches to the mace when you attack", mc().player.getInventory().getSelectedSlot() == 2, "slot " + mc().player.getInventory().getSelectedSlot() + (z == null ? ", no zombie to hit" : ""));
		});
		await(t + 143, 30, "MaceSwap switches back afterwards", () -> mc().player.getInventory().getSelectedSlot() == 0, () -> "slot " + mc().player.getInventory().getSelectedSlot());
		at(t += 180, () -> {
			off("MaceSwap");
			cmd("summon minecraft:end_crystal 3.5 150 0.5 {ShowBottom:0b}");
		});
		int[] crystalId = {-1};
		at(t += 15, () -> {
			on("CrystalOptimizer");
			for (Entity e : mc().level.entitiesForRendering()) if (e instanceof EndCrystal) crystalId[0] = e.getId();
			Entity c = crystalId[0] < 0 ? null : mc().level.getEntity(crystalId[0]);
			if (c != null) mc().gameMode.attack(mc().player, c);
		});
		await(t + 1, 3, "CrystalOptimizer removes a hit crystal at once", () -> crystalId[0] >= 0 && mc().level.getEntity(crystalId[0]) == null, () -> "crystal id " + crystalId[0]);
		at(t + 6, () -> {
			off("CrystalOptimizer");
			cmd("kill @e[type=minecraft:end_crystal]");
			cmd("kill @e[type=minecraft:zombie]");
		});
		// CrystalMacro: look at the floor in front, hold use: obsidian goes down, then a crystal on it
		at(t += 10, () -> {
			give("hotbar.3", "minecraft:end_crystal 8");
			give("hotbar.4", "minecraft:obsidian 8");
			mc().player.getInventory().setSelectedSlot(3);
			look(0, 60);
			on("CrystalMacro");
			mc().options.keyUse.setDown(true);
		});
		await(t + 1, 80, "CrystalMacro lays obsidian and places a crystal on it", () -> {
			for (Entity e : mc().level.entitiesForRendering()) if (e instanceof EndCrystal) return true;
			return false;
		}, () -> "looking at " + mc().hitResult + ", slot " + mc().player.getInventory().getSelectedSlot());
		at(t + 85, () -> {
			mc().options.keyUse.setDown(false);
			off("CrystalMacro");
			cmd("kill @e[type=minecraft:end_crystal]");
			cmd("fill -3 150 -3 3 150 5 minecraft:air");
			look(0, 0);
		});

		// ShieldBreaker: another player blocks with a shield in front of us
		RemotePlayer[] blocker = new RemotePlayer[1];
		at(t += 95, () -> {
			blocker[0] = otherPlayer("Blocker", -2_000_001, false, new Vec3(0.5, 150, 2.5));
			blocker[0].setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			blocker[0].setYRot(180);
			blocker[0].startUsingItem(InteractionHand.OFF_HAND);
			// on the client the "using an item" flags come from the server; set them the way its data update would
			setLivingFlag(blocker[0], 1, true);
			setLivingFlag(blocker[0], 2, true);
			give("hotbar.5", "minecraft:diamond_axe");
			mc().player.getInventory().setSelectedSlot(0);
		});
		at(t += 12, () -> {
			lookAt(blocker[0]);
			if (!blocker[0].isBlocking()) LOG.add("note: the fake player is not blocking (using " + blocker[0].isUsingItem() + ")");
			on("ShieldBreaker");
		});
		await(t + 1, 40, "ShieldBreaker takes out the axe against a blocking shield", () -> mc().player.getInventory().getSelectedSlot() == 5,
			() -> "slot " + mc().player.getInventory().getSelectedSlot() + ", blocking " + blocker[0].isBlocking() + ", crosshair " + mc().hitResult.getType());
		at(t + 45, () -> {
			removeEntity(blocker[0]);
			blocker[0] = null;
		});
		await(t + 46, 20, "ShieldBreaker goes back to your slot afterwards", () -> mc().player.getInventory().getSelectedSlot() == 0, () -> "slot " + mc().player.getInventory().getSelectedSlot());
		at(t + 70, () -> off("ShieldBreaker"));

		// totems: TotemMacro refills from the hotbar, HoverTotem from the open inventory
		at(t += 75, () -> {
			give("weapon.offhand", "minecraft:air");
			give("hotbar.6", "minecraft:totem_of_undying");
			on("TotemMacro");
		});
		await(t + 2, 40, "TotemMacro puts a totem in the offhand", () -> mc().player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), () -> "offhand " + mc().player.getOffhandItem());
		at(t + 45, () -> {
			off("TotemMacro");
			cmd("gamemode survival @a"); // in creative the inventory opens as the creative screen instead
			cmd("effect give @a minecraft:resistance 60 4 true");
			give("weapon.offhand", "minecraft:air");
			give("hotbar.6", "minecraft:air");
			give("inventory.10", "minecraft:totem_of_undying");
			set("HoverTotem", "Mode", breezeclient.breezeclient.client.modules.HoverTotem.Mode.INVENTORY);
			on("HoverTotem");
		});
		at(t + 50, () -> mc().setScreen(new InventoryScreen(mc().player)));
		await(t + 51, 40, "HoverTotem moves a totem to the offhand from the open inventory", () -> mc().player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), () -> "offhand " + mc().player.getOffhandItem());
		at(t + 95, () -> {
			mc().setScreen(null);
			off("HoverTotem");
			give("armor.chest", "minecraft:air");
			give("inventory.12", "minecraft:elytra");
		});
		at(t + 101, () -> on("ElytraSwap")); // once the server has sent the new inventory
		await(t + 102, 40, "ElytraSwap puts the elytra on", () -> mc().player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), () -> "chest " + mc().player.getItemBySlot(EquipmentSlot.CHEST));

		// small automation: FastUse, AutoTPA, AutoWalk, AntiAFK
		at(t += 140, () -> {
			cmd("gamemode creative @a");
			on("FastUse");
			setField(mc(), "rightClickDelay", 4);
		});
		at(t + 2, () -> {
			Object d = field(mc(), "rightClickDelay");
			check("FastUse shortens the use delay", d instanceof Integer i && i <= 1, "rightClickDelay " + d);
			off("FastUse");
			set("AutoTPA", "Target player", "Nobody");
			set("AutoTPA", "Command", "/say tpa");
			set("AutoTPA", "Max requests", 1.0);
			GAME_MSGS.clear();
			on("AutoTPA");
		});
		await(t + 3, 100, "AutoTPA sends the request", () -> GAME_MSGS.stream().anyMatch(s -> s.contains("tpa Nobody")), () -> "chat " + GAME_MSGS);
		double[] start = new double[3];
		at(t += 110, () -> {
			off("AutoTPA");
			as("tp @s 0.5 150 10.5 180 0");
		});
		at(t += 5, () -> {
			start[0] = mc().player.getX();
			start[1] = mc().player.getZ();
			on("AutoWalk");
		});
		await(t + 1, 40, "AutoWalk walks you forward", () -> Math.hypot(mc().player.getX() - start[0], mc().player.getZ() - start[1]) > 2,
			() -> String.format("moved %.2f", Math.hypot(mc().player.getX() - start[0], mc().player.getZ() - start[1])));
		at(t + 45, () -> {
			off("AutoWalk");
			start[2] = mc().player.getYRot() * 1000 + mc().player.getXRot();
			set("AntiAFK", "Interval (s)", 5.0);
			on("AntiAFK");
		});
		double[] y0 = new double[1];
		at(t + 46, () -> y0[0] = mc().player.getY());
		await(t + 47, 300, "AntiAFK turns or hops now and then", () -> mc().player.getYRot() * 1000 + mc().player.getXRot() != start[2] || mc().player.getY() > y0[0] + 0.3,
			() -> "yaw " + mc().player.getYRot() + ", pitch " + mc().player.getXRot());
		at(t += 350, () -> off("AntiAFK"));

		// --- survival: eating, digging out, spawners, selling, flying ----------------------------------------------------
		int[] food = {20, 20};
		at(t += 5, () -> {
			cmd("gamemode survival @a");
			cmd("effect give @a minecraft:resistance 600 4 true");
			cmd("effect give @a minecraft:hunger 4 255 true");
			cmd("clear @a");
		});
		at(t += 100, () -> {
			food[0] = mc().player.getFoodData().getFoodLevel();
			give("hotbar.6", "minecraft:bread 8");
			set("AutoEat", "Eat below", 19.0);
			mc().player.getInventory().setSelectedSlot(0);
			on("AutoEat");
		});
		await(t + 1, 160, "AutoEat eats when hungry", () -> count(Items.BREAD) < 8 && mc().player.getFoodData().getFoodLevel() > food[0],
			() -> "food " + food[0] + " -> " + mc().player.getFoodData().getFoodLevel() + ", bread " + count(Items.BREAD));
		at(t + 165, () -> {
			off("AutoEat");
			check("AutoEat goes back to the slot you had", mc().player.getInventory().getSelectedSlot() == 0, "slot " + mc().player.getInventory().getSelectedSlot());
			cmd("effect give @a minecraft:saturation 30 10 true");
			// boxed into a 1x2 cell of dirt
			cmd("fill 4 149 -11 6 152 -9 minecraft:dirt");
			cmd("fill 5 150 -10 5 151 -10 minecraft:air");
		});
		at(t + 170, () -> {
			as("tp @s 5.5 150 -9.5 0 0");
			on("AntiTrap");
		});
		await(t + 171, 200, "AntiTrap digs you out of a 1x2 box", () -> {
			BlockPos f = new BlockPos(5, 150, -10);
			for (Direction d : Direction.Plane.HORIZONTAL) if (mc().level.getBlockState(f.relative(d)).isAir() || mc().level.getBlockState(f.above().relative(d)).isAir()) return true;
			return mc().level.getBlockState(f.above(2)).isAir();
		}, () -> "player at " + mc().player.blockPosition().toShortString());
		at(t += 380, () -> {
			off("AntiTrap");
			cmd("fill 4 149 -11 6 152 -9 minecraft:air");
			cmd("fill 4 149 -11 6 149 -9 minecraft:stone");
			as("tp @s 0.5 150 -6.5 0 0");
			cmd("setblock 6 150 -2 minecraft:spawner{SpawnData:{entity:{id:\"minecraft:pig\"}}}");
			give("hotbar.0", "minecraft:diamond_pickaxe[minecraft:enchantments={\"minecraft:silk_touch\":1}]");
			mc().player.getInventory().setSelectedSlot(0);
			set("SpawnerPickup", "Home command", "/say home");
			set("SpawnerPickup", "Return command", "/say back");
			set("SpawnerPickup", "Deposit every N spawners", 64.0);
		});
		at(t += 10, () -> on("SpawnerPickup"));
		await(t + 1, 900, "SpawnerPickup walks to the spawner and mines it", () -> mc().level.getBlockState(new BlockPos(6, 150, -2)).isAir(),
			() -> "block now " + mc().level.getBlockState(new BlockPos(6, 150, -2)) + ", player at " + mc().player.blockPosition().toShortString());
		RemotePlayer[] stranger = new RemotePlayer[1];
		at(t += 905, () -> {
			off("SpawnerPickup");
			cmd("clear @a minecraft:spawner");
			cmd("setblock 6 150 -2 minecraft:spawner{SpawnData:{entity:{id:\"minecraft:pig\"}}}");
			as("tp @s 0.5 150 -6.5 0 0");
			set("SpawnerProtect", "Disconnect afterwards", false);
		});
		at(t += 20, () -> {
			stranger[0] = otherPlayer("Stranger", 777_001, false, new Vec3(0.5, 150, 12.5));
			on("SpawnerProtect");
		});
		await(t + 1, 900, "SpawnerProtect mines the spawner when a stranger comes", () -> mc().level.getBlockState(new BlockPos(6, 150, -2)).isAir(),
			() -> "block now " + mc().level.getBlockState(new BlockPos(6, 150, -2)));
		at(t += 905, () -> {
			off("SpawnerProtect");
			off("SpawnerPickup");
			removeEntity(stranger[0]);
			// AutoSell: a chest named "Sell" stands in for the shop window
			cmd("setblock 2 150 -6 minecraft:chest{CustomName:\"Sell\"}");
			as("tp @s 0.5 150 -6.5 -90 0");
			cmd("clear @a");
			give("inventory.9", "minecraft:cobblestone 16");
			set("AutoSell", "Command", "/say selling");
			set("AutoSell", "Interval (s)", 10.0);
			set("AutoSell", "Confirm button contains", "");
		});
		at(t += 10, () -> on("AutoSell"));
		at(t + 1, () -> AWAITS.add(new Await("AutoSell runs the command and moves items into the sell window", ticks + 400, () -> {
			// open the "shop" once the module has sent its command, as a server would
			if (mc().screen == null && GAME_MSGS.stream().anyMatch(s -> s.contains("selling")) && count(Items.COBBLESTONE) > 0) {
				var hit = new BlockHitResult(new Vec3(2.5, 150.5, -5.5), Direction.SOUTH, new BlockPos(2, 150, -6), false);
				mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, hit);
			}
			return count(Items.COBBLESTONE) == 0 && infoInt("AutoSell") > 0;
		}, () -> "cobblestone left " + count(Items.COBBLESTONE) + ", sold '" + info("AutoSell") + "', screen " + mc().screen)));
		at(t += 420, () -> {
			off("AutoSell");
			if (mc().screen != null) mc().player.closeContainer();
		});

		// gliding: AutoFirework boosts when slow, ElytraGlide steers the pitch
		at(t += 5, () -> {
			cmd("clear @a");
			give("armor.chest", "minecraft:elytra");
			give("hotbar.7", "minecraft:firework_rocket 16");
			mc().player.getInventory().setSelectedSlot(0);
			as("tp @s 0.5 230 0.5 0 0");
		});
		at(t += 20, () -> {
			var p = mc().player;
			if (p.tryToStartFallFlying()) p.connection.send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
			on("AutoFirework");
		});
		await(t + 1, 60, "AutoFirework fires a rocket while gliding slowly", () -> count(Items.FIREWORK_ROCKET) < 16,
			() -> "gliding " + mc().player.isFallFlying() + ", speed " + mc().player.getDeltaMovement().length());
		float[] pitch0 = new float[1];
		at(t + 62, () -> {
			off("AutoFirework");
			pitch0[0] = mc().player.getXRot();
			on("ElytraGlide");
		});
		await(t + 63, 40, "ElytraGlide steers the pitch while gliding", () -> Math.abs(mc().player.getXRot() - pitch0[0]) > 5,
			() -> "pitch " + pitch0[0] + " -> " + mc().player.getXRot() + ", gliding " + mc().player.isFallFlying());
		at(t += 110, () -> {
			off("ElytraGlide");
			cmd("gamemode creative @a");
			as("tp @s 0.5 150 0.5 0 0");
		});

		// --- seed and chunk finders ---------------------------------------------------------------------------------
		at(t += 20, () -> {
			List<String> out = new ArrayList<>();
			BreezClientClient.commands.handle(".seed 8675309", out::add);
			on("SeedOres");
		});
		await(t + 1, 300, "SeedOres predicts ores from the seed", () -> infoInt("SeedOres") > 0, () -> "info '" + info("SeedOres") + "'");
		at(t + 305, () -> {
			off("SeedOres");
			// a chunk dug out completely, well away from us: every ore the seed put there is gone
			cmd("fill 64 -60 64 79 60 79 minecraft:air");
			on("SeedChunkFinder");
		});
		await(t + 306, 400, "SeedChunkFinder flags chunks whose ores are gone", () -> infoInt("SeedChunkFinder") > 0, () -> "info '" + info("SeedChunkFinder") + "'");
		at(t += 710, () -> {
			off("SeedChunkFinder");
			// a long 1x2 tunnel through the stone under the arena
			// below y 56, the depth TunnelFinder looks at, in solid rock (a natural cave across it would break the walls)
			cmd("fill -12 18 -19 -8 23 29 minecraft:stone");
			cmd("fill -10 20 -19 -10 21 29 minecraft:air");
			on("TunnelFinder");
			on("TunnelBaseFinder");
		});
		await(t + 1, 300, "TunnelBaseFinder joins the tunnel into one", () -> infoInt("TunnelBaseFinder") > 0, () -> "TunnelFinder '" + info("TunnelFinder") + "', TunnelBaseFinder '" + info("TunnelBaseFinder") + "'");
		at(t + 305, () -> {
			off("TunnelBaseFinder");
			off("TunnelFinder");
			on("NewChunks");
			on("TrailFinder");
			on("PrimeChunkFinder");
		});
		at(t += 410, () -> {
			LOG.add("info: NewChunks '" + info("NewChunks") + "', TrailFinder '" + info("TrailFinder") + "', PrimeChunkFinder '" + info("PrimeChunkFinder") + "' (these need old and new chunks next to each other; a test world has few)");
			off("TrailFinder");
			off("PrimeChunkFinder");
			off("NewChunks");
		});

		// --- SpectatorAlert ----------------------------------------------------------------------------------------
		RemotePlayer[] spec = new RemotePlayer[1];
		at(t += 5, () -> {
			on("SpectatorAlert");
			spec[0] = otherPlayer("Watcher", 777_002, true, new Vec3(4.5, 152, 4.5));
		});
		await(t + 1, 20, "SpectatorAlert notices a spectator nearby", () -> {
			Object title = field(mc().gui, "title");
			return infoInt("SpectatorAlert") == 1 && title != null && title.toString().contains("Spectator nearby");
		}, () -> "info '" + info("SpectatorAlert") + "', title " + field(mc().gui, "title"));
		at(t + 25, () -> {
			removeEntity(spec[0]);
			off("SpectatorAlert");
		});

		// --- RTPBaseFinder: a teleport command stands in for /rtp ---------------------------------------------------
		double[] from = new double[2];
		at(t += 30, () -> {
			from[0] = mc().player.getX();
			from[1] = mc().player.getZ();
			set("RTPBaseFinder", "Command", "/rtp");
			GAME_MSGS.clear();
			set("RTPBaseFinder", "Cooldown (s)", 3.0);
			set("RTPBaseFinder", "Scan time (s)", 2.0);
			set("RTPBaseFinder", "Max teleports (0 = no limit)", 1.0);
			on("RTPBaseFinder");
		});
		// the test world has no /rtp: answer the command the way a server would
		boolean[] rtpDone = {false};
		await(t + 1, 400, "RTPBaseFinder sends the command, waits for the teleport and counts it", () -> {
			if (!rtpDone[0] && GAME_MSGS.stream().anyMatch(s -> s.contains("rtp"))) {
				rtpDone[0] = true;
				as("tp @s 400 200 400");
			}
			return info("RTPBaseFinder").startsWith("1") && Math.hypot(mc().player.getX() - from[0], mc().player.getZ() - from[1]) > 100;
		},
			() -> "info '" + info("RTPBaseFinder") + "', at " + mc().player.blockPosition().toShortString());
		at(t += 420, () -> {
			off("RTPBaseFinder");
			off("SusChunks");
			off("StashFinder");
			as("tp @s 0.5 150 0.5 0 0");
		});

		// --- last: AutoLog drops the connection at low health -----------------------------------------------------------
		at(t += 40, () -> {
			cmd("gamemode survival @a");
			cmd("effect clear @a");
			set("AutoLog", "Health at or below", 14.0);
			on("AutoLog");
		});
		at(t += 10, () -> {
			expectDisconnect = true;
			cmd("damage @p 8 minecraft:generic");
		});
		at(t + 200, () -> {
			check("AutoLog disconnects at low health", false, "still connected, health " + mc().player.getHealth());
			finish(mc());
		});
	}

	/** BREEZ_SELFTEST=killsw: another (not client-only) player appears nearby and KillSwitch must leave the server. */
	private static void buildKillSwitch() {
		at(5, () -> {
			for (Module m : BreezClientClient.MODULES.all()) if (m.enabled()) m.setEnabled(false);
			cmd("gamemode creative @a");
			as("tp @s 0.5 176 0.5");
		});
		at(20, FuncTest::arena);
		at(45, () -> as("tp @s 0.5 150 0.5 0 0"));
		at(60, () -> {
			set("KillSwitch", "Any player", true);
			on("KillSwitch");
		});
		at(80, () -> {
			check("KillSwitch stays connected while nobody is near", mc().getConnection() != null, "");
			expectDisconnect = true;
			otherPlayer("Stranger", 777_003, false, new Vec3(5.5, 150, 5.5));
		});
		at(140, () -> {
			check("KillSwitch disconnects when a player comes near", false, "still connected");
			finish(mc());
		});
	}

	// ---- driver -----------------------------------------------------------------------------------------------------

	private static void tick(Minecraft mc) {
		if (finished || ticks < 0) return;
		ticks++;
		mc.options.pauseOnLostFocus = false;
		if (ticks < 25) SelfTest.reviveAtStart(mc);
		if (mc.player == null || mc.level == null) return;
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
		if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
			mc.player.respawn();
			mc.setScreen(null);
		}
		try {
			List<Runnable> now = STEPS.remove(ticks);
			if (now != null) for (Runnable r : now) r.run();
			for (Iterator<Await> it = AWAITS.iterator(); it.hasNext(); ) {
				Await a = it.next();
				if (a.ok().getAsBoolean()) {
					check(a.name(), true, "");
					it.remove();
				} else if (ticks >= a.deadline()) {
					check(a.name(), false, a.detail().get());
					it.remove();
				}
			}
		} catch (Throwable t) {
			check("test step at tick " + ticks + " ran", false, t.toString());
		}
	}

	private static void finish(Minecraft mc) {
		if (finished) return;
		finished = true;
		for (Await a : AWAITS) check(a.name(), false, "test ended first: " + a.detail().get());
		LOG.add(0, "RESULT pass=" + pass + " fail=" + fail);
		try {
			Files.write(FabricLoader.getInstance().getGameDir().resolve(mode.equals("func") ? "func-test.txt" : "killsw-test.txt"), LOG);
		} catch (Exception ignored) {
			// nothing else to do
		}
		mc.stop();
	}

}
