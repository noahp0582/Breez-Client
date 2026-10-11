package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.modules.Animations;
import breezeclient.breezeclient.client.modules.ArmorTrimHider;
import breezeclient.breezeclient.client.modules.HitBox;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.settings.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer test (BREEZ_SELFTEST=extra) for the first batch of extra modules: enables the new modules in a singleplayer world,
 * takes screenshots in first and third person, checks that each one actually took effect and writes extra-test.txt.
 */
public final class ExtraTest {
	private static int ticks = -1;
	private static final List<String> LOG = new ArrayList<>();

	private ExtraTest() {}

	public static boolean register() {
		if (!"extra".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> ticks = 0);
		ClientTickEvents.END_CLIENT_TICK.register(ExtraTest::tick);
		return true;
	}

	private static void cmd(Minecraft mc, String c) {
		var server = mc.getSingleplayerServer();
		if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c));
	}

	private static void shot(Minecraft mc) {
		Screenshot.grab(FabricLoader.getInstance().getGameDir().toFile(), mc.getMainRenderTarget(), c -> {});
	}

	private static void set(String module, String setting, Object value) {
		Module m = BreezClientClient.MODULES.get(module);
		if (m == null) return;
		for (Setting<?> s : m.settings()) {
			if (s.name().equals(setting)) {
				@SuppressWarnings("unchecked") Setting<Object> o = (Setting<Object>) s;
				o.set(value);
			}
		}
	}

	private static void tick(Minecraft mc) {
		if (ticks < 0 || mc.player == null || mc.level == null) return;
		ticks++;
		if (ticks == 20) {
			cmd(mc, "execute as @a in minecraft:overworld run tp @s 0 100 0");
			cmd(mc, "gamemode creative @a");
		}
		if (ticks == 50) {
			cmd(mc, "execute as @a at @s run summon item ^ ^ ^3 {Item:{id:\"minecraft:diamond\",count:5}}");
			cmd(mc, "execute as @a at @s run summon zombie ^ ^ ^5 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}");
			cmd(mc, "item replace entity @a armor.chest with minecraft:netherite_chestplate[trim={pattern:\"minecraft:sentry\",material:\"minecraft:gold\"}]");
			cmd(mc, "item replace entity @a weapon.mainhand with minecraft:diamond_sword[enchantments={\"minecraft:sharpness\":5}]");
		}
		if (ticks == 70) {
			set("SusChunks", "Mode", breezeclient.breezeclient.client.modules.SusChunks.Mode.SIGNATURES);
			set("ArmorTrimHider", "Mode", breezeclient.breezeclient.client.modules.ArmorTrimHider.Mode.HIDE);
			set("TunnelFinder", "Chunks per second", 10.0);
			breezeclient.breezeclient.client.gui.GuiState.CLIENT.friends.add("Steve");
			set("HitBox", "Players only", false);
			set("HitBox", "Show boxes", true);
			set("CustomAccessories", "Trail", true);
			set("CustomAccessories", "Aura", true);
			set("CustomAccessories", "Crown", true);
			set("RegionMap", "Regions", List.of("spawn -50 -50 50 50"));
			set("Animations", "Offset X", 0.1);
			for (String n : List.of("ArmorTrimHider", "MotionBlur", "CustomGlint", "HitBox", "NameTags", "CreamyKeys", "CustomAccessories", "Animations", "RegionMap", "SusChunks", "TunnelFinder", "CoverageMap", "ItemESP", "ChestLooter", "PlayerLogger")) {
				Module m = BreezClientClient.MODULES.get(n);
				if (m == null) LOG.add("MISSING module " + n);
				else m.setEnabled(true);
			}
		}
		if (ticks == 40) net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(344));
		if (ticks == 43) {
			LOG.add("rightShiftOpensGui=" + (mc.screen instanceof breezeclient.breezeclient.client.gui.ModulesScreen));
			mc.setScreen(null);
			boolean listed = false;
			for (var k : mc.options.keyMappings) if (k.getName().equals("key.breez-client.open_gui")) listed = true;
			LOG.add("keyListedInControls=" + listed);
		}
		if (ticks == 52) {
			cmd(mc, "scoreboard objectives add breeztest dummy {\"text\":\"Leaderboard\"}");
			cmd(mc, "scoreboard objectives setdisplay sidebar breeztest");
			cmd(mc, "scoreboard players set @a breeztest 1234");
			cmd(mc, "scoreboard players set Alex breeztest 999");
			cmd(mc, "scoreboard players set Steve breeztest 500");
			String me = mc.player.getName().getString();
			cmd(mc, "scoreboard objectives modify breeztest displayname {\"text\":\"" + me + " Stats\"}");
			cmd(mc, "scoreboard players display name Alex breeztest {\"text\":\"Team: " + me + "\"}");
			cmd(mc, "scoreboard players display numberformat Steve breeztest fixed {\"text\":\"owner " + me + "\"}");
		}
		if (ticks == 115) {
			var pos = chestPos;
			var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
			mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		}
		if (ticks == 150) {
			LOG.add("chestLooter(block chest) netherite=" + count(mc, net.minecraft.world.item.Items.NETHERITE_INGOT) + " (expect 5)");
			mc.player.closeContainer();
		}
		if (ticks == 160) {
			for (var e : mc.level.entitiesForRendering()) {
				if (e.getType() == EntityType.CHEST_MINECART) {
					mc.gameMode.interact(mc.player, e, net.minecraft.world.InteractionHand.MAIN_HAND);
					break;
				}
			}
		}
		if (ticks == 202) {
			for (var st : BreezClientClient.MODULES.get("StaffList").settings()) {
				if (st instanceof breezeclient.breezeclient.core.settings.StringListSetting l && st.name().equals("Staff names")) {
					l.add("Herobrine");
					LOG.add("staffListSetting added herobrine -> profile says " + breezeclient.breezeclient.client.safety.StaffTracker.profile().isStaff("herobrine"));
				}
			}
			mc.setScreen(new breezeclient.breezeclient.client.gui.ModuleScreen(BreezClientClient.MODULES.get("StaffList")));
		}
		if (ticks == 214) shot(mc);
		if (ticks == 218) mc.setScreen(null);
		if (ticks == 222) {
			StringBuilder sb = new StringBuilder("actions per module:");
			for (var mod : BreezClientClient.MODULES.all()) {
				int n = 0;
				for (var st : mod.settings()) if (st instanceof breezeclient.breezeclient.core.settings.ActionSetting) n++;
				if (n > 0) sb.append(' ').append(mod.name()).append('=').append(n);
			}
			LOG.add(sb.toString());
			for (var st : BreezClientClient.MODULES.get("NewChunks").settings()) {
				if (st instanceof breezeclient.breezeclient.core.settings.ActionSetting a && a.name().equals("Show counts")) a.run();
			}
			mc.setScreen(new breezeclient.breezeclient.client.gui.ModuleScreen(BreezClientClient.MODULES.get("Baritone")));
		}
		if (ticks == 232) shot(mc);
		if (ticks == 234) mc.setScreen(new breezeclient.breezeclient.client.gui.ModuleScreen(BreezClientClient.MODULES.get("SchematicBuilder")));
		if (ticks == 244) shot(mc);
		if (ticks == 246) mc.setScreen(null);
		if (ticks == 250) {
			breezeclient.breezeclient.client.gui.Controls.ListEditor.devOpen = true;
			mc.setScreen(new breezeclient.breezeclient.client.gui.ModuleScreen(BreezClientClient.MODULES.get("BlockESP")));
		}
		if (ticks == 256) {
			var ed = breezeclient.breezeclient.client.gui.Controls.ListEditor.last;
			var blocks = breezeclient.breezeclient.client.Modules.blockEsp.blocks;
			int before = blocks.get().size();
			LOG.add("picker entries=" + (ed == null ? -1 : ed.devEntries()) + " blocksBefore=" + before);
			if (ed != null) ed.devPick(5);
			LOG.add("picker after click blocks=" + blocks.get().size());
		}
		if (ticks == 266) shot(mc);
		if (ticks == 268) {
			breezeclient.breezeclient.client.gui.Controls.ListEditor.devOpen = false;
			mc.setScreen(null);
		}
		if (ticks == 200) {
			LOG.add("chestLooter(minecart menu) netherite=" + count(mc, net.minecraft.world.item.Items.NETHERITE_INGOT) + " (expect still 5: refused)");
			mc.player.closeContainer();
		}
		if (ticks == 120) cmd(mc, "tellraw @a {\"text\":\"" + mc.player.getName().getString() + " received a payment\"}");
		if (ticks == 124) for (Module m : BreezClientClient.MODULES.all()) m.setVisibleInHud(false);
		if (ticks == 126) {
			set("ScoreboardName", "Text", "&a&lSecret &7Name");
			BreezClientClient.MODULES.get("ScoreboardName").setEnabled(true);
		}
		if (ticks == 128) shot(mc);
		if (ticks == 130) set("ScoreboardName", "Mode", breezeclient.breezeclient.client.modules.ScoreboardName.Mode.HIDE_LINE);
		if (ticks == 134) shot(mc);
		if (ticks == 150) set("ScoreboardName", "Mode", breezeclient.breezeclient.client.modules.ScoreboardName.Mode.REPLACE);
		if (ticks == 90) {
			// positive control: a spawner pile and a hopper-fed storage row far enough away to be evaluated
			cmd(mc, "forceload add 200 0");
			cmd(mc, "forceload add -200 0");
			cmd(mc, "forceload add 100 40");
		}
		if (ticks == 100) {
			// batch 1 controls in chunk (6,2): building blocks, lit campfire, an animal pen, armor stands and a corridor
			cmd(mc, "fill 96 70 40 107 70 40 minecraft:white_concrete");
			cmd(mc, "setblock 100 70 44 minecraft:campfire[lit=true]");
			for (int i = 0; i < 14; i++) cmd(mc, "summon minecraft:cow " + (97 + i % 4) + " 72 " + (40 + i / 4) + " {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
			for (int i = 0; i < 3; i++) cmd(mc, "summon minecraft:armor_stand " + (110 + i) + " 72 41 {NoGravity:1b}");
			cmd(mc, "fill 94 28 38 111 33 42 minecraft:stone");
			cmd(mc, "fill 96 30 40 111 31 40 minecraft:air");
			// batch 3: a chest with items for ChestLooter, and a chest minecart that must be refused
			var pp = mc.player.blockPosition();
			chestPos = pp.offset(2, 0, 1);
			cmd(mc, "setblock " + chestPos.getX() + " " + chestPos.getY() + " " + chestPos.getZ() + " minecraft:chest{Items:[{Slot:0b,id:\"minecraft:netherite_ingot\",count:5},{Slot:1b,id:\"minecraft:dirt\",count:10}]}");
			cmd(mc, "summon minecraft:chest_minecart " + (pp.getX() - 2) + " " + pp.getY() + " " + (pp.getZ() + 1) + " {NoGravity:1b,Items:[{Slot:0b,id:\"minecraft:netherite_ingot\",count:3}]}");
			for (String c : List.of("200 40 0", "201 40 0", "200 40 1", "201 40 1", "202 40 0")) cmd(mc, "setblock " + c + " minecraft:spawner");
			for (String c : List.of("-200 40 0", "-199 40 0", "-198 40 0", "-197 40 0")) cmd(mc, "setblock " + c + " minecraft:hopper");
			for (String c : List.of("-200 41 0", "-199 41 0", "-198 41 0")) cmd(mc, "setblock " + c + " minecraft:chest");
		}
		// the scanner refreshes already-loaded chunks round-robin, so allow a full pass before expecting the control to show up
		if (ticks == 300) susReport("SIGNATURES, positive control placed at tick 100");
		if (ticks == 130) shot(mc);
		if (ticks == 140) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		if (ticks == 175) shot(mc);
		if (ticks == 310) {
			check(mc);
			autoLogProbe(mc);
		}
		if (ticks == 400) {
			append("autoLogDisconnected=false");
			mc.stop();
		}
	}

	private static net.minecraft.core.BlockPos chestPos;

	private static int count(Minecraft mc, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
			var st = mc.player.getInventory().getItem(i);
			if (st.is(item)) n += st.getCount();
		}
		return n;
	}

	private static void append(String line) {
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("extra-test.txt"), line + "\n", java.nio.file.StandardOpenOption.APPEND);
		} catch (Exception ignored) {
			// nothing else to do
		}
	}

	/** Last step: a totem pop with AutoLog (pops=1) must disconnect. Reported through the disconnect event. */
	private static void autoLogProbe(Minecraft mc) {
		append("webhookUrl good=" + breezeclient.breezeclient.client.modules.AlertWebhook.validUrl("https://discord.com/api/webhooks/1/abc")
			+ " other-host=" + breezeclient.breezeclient.client.modules.AlertWebhook.validUrl("https://example.com/api/webhooks/1/abc")
			+ " http=" + breezeclient.breezeclient.client.modules.AlertWebhook.validUrl("http://discord.com/api/webhooks/1/abc"));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
			append("autoLogDisconnected=true");
			Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
		});
		set("AutoLog", "Health at or below", 0.0);
		set("AutoLog", "Totem pops", 1.0);
		set("AutoLog", "In lava", false);
		BreezClientClient.MODULES.get("AutoLog").setEnabled(true);
		mc.gameRenderer.displayItemActivation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
	}

	private static void susReport(String mode) {
		LOG.add("tunnelRuns in chunk (6,2) = " + breezeclient.breezeclient.client.modules.TunnelFinder.runsIn(6, 2).size()
			+ ", coverage chunks=" + breezeclient.breezeclient.client.world.CoverageTracker.count()
			+ ", friend steve=" + breezeclient.breezeclient.client.modules.Friends.is("Steve"));
		var top = breezeclient.breezeclient.client.modules.SusChunks.top(500);
		LOG.add("SusChunks[" + mode + "] flagged " + top.size() + " chunks");
		for (var r : top.subList(0, Math.min(8, top.size()))) {
			LOG.add(String.format("  %.0f  %s  chunk (%d,%d)", r.score(), r.label(), r.chunkX(), r.chunkZ()));
			for (String a : r.all()) LOG.add("      - " + a);
		}
	}

	private static void check(Minecraft mc) {
		var gr = mc.gameRenderer;
		LOG.add("postEffect=" + gr.currentPostEffect());
		var glint = mc.getTextureManager().getTexture(Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png"));
		LOG.add("glintTexture=" + glint.getClass().getSimpleName() + " dynamic=" + (glint instanceof DynamicTexture));
		var skin = mc.player.getSkin();
		LOG.add("cape=" + (skin.cape() == null ? "none" : skin.cape().texturePath()));
		ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
		LOG.add("chest=" + chest + " trimBefore=" + chest.get(DataComponents.TRIM));
		LOG.add("trimFiltered(HIDE)=" + ArmorTrimHider.filter(chest).get(DataComponents.TRIM));
		set("ArmorTrimHider", "Mode", breezeclient.breezeclient.client.modules.ArmorTrimHider.Mode.RANDOMIZE);
		LOG.add("trimFiltered(RANDOM)=" + ArmorTrimHider.filter(chest).get(DataComponents.TRIM));
		for (var e : mc.level.entitiesForRendering()) {
			if (e.getType() == EntityType.ZOMBIE) LOG.add("zombie pickRadius=" + e.getPickRadius() + " extra=" + HitBox.pickRadius(e));
		}
		LOG.add("handTransform=" + java.util.Arrays.toString(Animations.handTransform()));
		LOG.add("moduleCrashes=" + ModuleManager.crashCount());
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("extra-test.txt"), String.join("\n", LOG) + "\n");
		} catch (Exception ignored) {
			// nothing else to do
		}
	}
}
