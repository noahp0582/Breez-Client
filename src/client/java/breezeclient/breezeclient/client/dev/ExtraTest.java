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
			set("HitBox", "Players only", false);
			set("HitBox", "Show boxes", true);
			set("CustomAccessories", "Trail", true);
			set("CustomAccessories", "Aura", true);
			set("CustomAccessories", "Crown", true);
			set("RegionMap", "Regions", List.of("spawn -50 -50 50 50"));
			set("Animations", "Offset X", 0.1);
			for (String n : List.of("ArmorTrimHider", "MotionBlur", "CustomGlint", "HitBox", "NameTags", "CreamyKeys", "CustomAccessories", "Animations", "RegionMap")) {
				Module m = BreezClientClient.MODULES.get(n);
				if (m == null) LOG.add("MISSING module " + n);
				else m.setEnabled(true);
			}
		}
		if (ticks == 130) shot(mc);
		if (ticks == 140) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		if (ticks == 175) shot(mc);
		if (ticks == 190) {
			check(mc);
			mc.stop();
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
