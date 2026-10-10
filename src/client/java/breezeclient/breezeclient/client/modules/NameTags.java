package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.gui.ColorMath;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Nametags drawn on the HUD instead of in the world: a flat panel with name, health bar, distance and gear for players,
 * and name x count for dropped items. Scales with a setting rather than with distance, and shows through walls.
 */
public class NameTags extends Module {
	private static NameTags instance;
	private static float lastFov = 70f;

	private final BoolSetting players = add(new BoolSetting("Players", true), "Targets", "Draw tags for other players.");
	private final BoolSetting items = add(new BoolSetting("Items", true), "Targets", "Draw tags for dropped items.");
	private final NumberSetting range = add(new NumberSetting("Range", 96, 8, 256), "Targets", "Maximum distance in blocks.");
	private final NumberSetting maxTags = add(new NumberSetting("Max tags", 24, 4, 80), "Targets", "Only the nearest tags are drawn.");
	private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 2.5), "Look", "Size of the tags.");
	private final BoolSetting background = add(new BoolSetting("Background", true), "Look", "Dark panel behind the text.");
	private final BoolSetting healthBar = add(new BoolSetting("Health bar", true), "Look", "Bar under the name.");
	private final BoolSetting distance = add(new BoolSetting("Distance", true), "Look", "Show the distance in meters.");
	private final BoolSetting gear = add(new BoolSetting("Gear", true), "Look", "Show held items and armor above the tag.");
	private final BoolSetting hideVanilla = add(new BoolSetting("Hide vanilla tags", true), "Look", "Do not draw the normal in-world nametag of players.");
	private final ColorSetting playerColor = add(new ColorSetting("Player color", 0xFFFFFFFF), "Colors", "Name color.");
	private final ColorSetting staffColor = add(new ColorSetting("Staff color", 0xFFFF4444), "Colors", "Name color for listed staff.");
	private final ColorSetting itemColor = add(new ColorSetting("Item color", 0xFFFFD866), "Colors", "Item name color.");

	/** Entity ids of the players whose HUD tag was drawn in the last frame. */
	private final java.util.Set<Integer> drawnPlayers = new java.util.HashSet<>();

	private record Tag(double dist, float x, float y, Entity entity) {}

	public NameTags() {
		super("NameTags", Category.RENDER);
		instance = this;
	}

	public static void registerEvents() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("breez-client", "nametags"),
			(g, dt) -> {
				if (instance != null && instance.enabled()) instance.draw(g);
			});
	}

	/** Remembered from the game's own FOV calculation so projection matches what is on screen (sprinting, effects, zoom). */
	public static void noteFov(float fov) { lastFov = fov; }

	public static boolean hidesVanilla(Entity e) {
		NameTags m = instance;
		// only where a HUD tag was actually drawn for that player last frame, so nobody ends up with no label at all
		return m != null && m.enabled() && m.players.get() && m.hideVanilla.get() && e instanceof Player && e != Minecraft.getInstance().player
			&& m.drawnPlayers.contains(e.getId());
	}

	/** Screen position (in GUI pixels) of a world point, or null when it is behind the camera. */
	private static float[] project(Camera cam, Vec3 p, int sw, int sh, double aspect) {
		Vec3 c = cam.position();
		double dx = p.x - c.x, dy = p.y - c.y, dz = p.z - c.z;
		Vector3fc f = cam.forwardVector(), u = cam.upVector(), l = cam.leftVector();
		double depth = dx * f.x() + dy * f.y() + dz * f.z();
		if (depth < 0.1) return null;
		double up = dx * u.x() + dy * u.y() + dz * u.z();
		double left = dx * l.x() + dy * l.y() + dz * l.z();
		double tan = Math.tan(Math.toRadians(lastFov) / 2.0);
		double nx = -(left / depth) / (tan * aspect);
		double ny = -(up / depth) / tan;
		return new float[] {(float) ((nx + 1) * 0.5 * sw), (float) ((ny + 1) * 0.5 * sh)};
	}

	private void draw(GuiGraphics g) {
		drawnPlayers.clear();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;
		Camera cam = mc.gameRenderer.getMainCamera();
		int sw = g.guiWidth(), sh = g.guiHeight();
		double aspect = sw / (double) sh;
		Vec3 eye = cam.position();
		double max = range.get();

		List<Tag> tags = new ArrayList<>();
		if (players.get()) {
			for (Player p : mc.level.players()) {
				if (p == mc.player || p == mc.getCameraEntity() || p.isSpectator()) continue;
				double d = p.position().distanceTo(eye);
				if (d > max) continue;
				float[] s = project(cam, new Vec3(p.getX(), p.getBoundingBox().maxY + 0.3, p.getZ()), sw, sh, aspect);
				if (s != null) tags.add(new Tag(d, s[0], s[1], p));
			}
		}
		if (items.get()) {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (!(e instanceof ItemEntity ie) || ie.getItem().isEmpty()) continue;
				double d = e.position().distanceTo(eye);
				if (d > max) continue;
				float[] s = project(cam, new Vec3(e.getX(), e.getY() + 0.55, e.getZ()), sw, sh, aspect);
				if (s != null) tags.add(new Tag(d, s[0], s[1], e));
			}
		}
		tags.sort(Comparator.comparingDouble(Tag::dist));
		int n = Math.min(tags.size(), maxTags.asInt());
		Font font = mc.font;
		for (int i = n - 1; i >= 0; i--) { // far ones first so near tags end up on top
			Tag t = tags.get(i);
			if (t.x() < -80 || t.x() > sw + 80 || t.y() < -40 || t.y() > sh + 40) continue;
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(t.x(), t.y());
			float sc = scale.asFloat();
			pose.scale(sc, sc);
			if (t.entity() instanceof Player p) {
				drawnPlayers.add(p.getId());
				drawPlayer(g, font, p, t.dist());
			}
			else if (t.entity() instanceof ItemEntity ie) drawItem(g, font, ie);
			pose.popMatrix();
		}
	}

	private void panel(GuiGraphics g, int x, int y, int w, int h) {
		if (background.get()) g.fill(x, y, x + w, y + h, 0x90000000);
	}

	private void drawItem(GuiGraphics g, Font font, ItemEntity ie) {
		ItemStack s = ie.getItem();
		String text = s.getHoverName().getString() + (s.getCount() > 1 ? " x" + s.getCount() : "");
		int w = font.width(text);
		panel(g, -w / 2 - 2, -11, w + 4, 11);
		g.drawString(font, text, -w / 2, -9, itemColor.get() | 0xFF000000, false);
	}

	private void drawPlayer(GuiGraphics g, Font font, Player p, double dist) {
		String name = StaffTracker.nameOf(p);
		boolean staff = StaffTracker.profile().isStaff(name);
		float hp = p.getHealth() + p.getAbsorptionAmount();
		StringBuilder sb = new StringBuilder(name);
		sb.append(' ').append(String.format("%.1f", hp));
		if (distance.get()) sb.append("  ").append((int) dist).append('m');
		String text = sb.toString();
		int w = font.width(text);
		int h = healthBar.get() ? 14 : 11;
		panel(g, -w / 2 - 2, -h, w + 4, h);
		int nameColor = (staff ? staffColor.get() : playerColor.get()) | 0xFF000000;
		g.drawString(font, text, -w / 2, -h + 2, nameColor, false);
		if (healthBar.get()) {
			float frac = Math.max(0f, Math.min(1f, hp / Math.max(1f, p.getMaxHealth())));
			int bw = w + 4;
			g.fill(-bw / 2, -3, bw / 2, -1, 0xAA202020);
			int color = ColorMath.mix(0xFFE03030, 0xFF40E060, frac);
			g.fill(-bw / 2, -3, -bw / 2 + Math.round(bw * frac), -1, color | 0xFF000000);
		}
		if (gear.get()) {
			List<ItemStack> stacks = new ArrayList<>();
			stacks.add(p.getMainHandItem());
			stacks.add(p.getItemBySlot(EquipmentSlot.HEAD));
			stacks.add(p.getItemBySlot(EquipmentSlot.CHEST));
			stacks.add(p.getItemBySlot(EquipmentSlot.LEGS));
			stacks.add(p.getItemBySlot(EquipmentSlot.FEET));
			stacks.add(p.getOffhandItem());
			int shown = 0;
			for (ItemStack s : stacks) if (!s.isEmpty()) shown++;
			int x = -shown * 9;
			for (ItemStack s : stacks) {
				if (s.isEmpty()) continue;
				var pose = g.pose();
				pose.pushMatrix();
				pose.translate(x, -h - 17);
				pose.scale(0.9f, 0.9f);
				g.renderItem(s, 0, 0);
				pose.popMatrix();
				x += 18;
			}
		}
	}
}
