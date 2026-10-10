package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.text.IdPattern;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.List;

/** Highlights dropped items that match a list (spawners, shulker boxes, netherite and similar). */
public class ItemEsp extends Module {
	private final StringListSetting items = add(new StringListSetting("Items", List.of("minecraft:spawner", "minecraft:*_shulker_box",
		"minecraft:shulker_box", "minecraft:netherite_ingot", "minecraft:netherite_scrap", "minecraft:netherite_block", "minecraft:ancient_debris",
		"minecraft:elytra", "minecraft:enchanted_golden_apple", "minecraft:totem_of_undying", "minecraft:beacon", "minecraft:diamond_block",
		"minecraft:ender_chest", "minecraft:trial_key", "minecraft:ominous_trial_key")).kind(StringListSetting.Kind.ITEM),
		"Items", "Item ids to highlight. A * matches any run of characters, e.g. minecraft:*_shulker_box.");
	private final NumberSetting range = add(new NumberSetting("Range", 96, 8, 256), "Display", "Maximum distance in blocks.");
	private final BoolSetting boxes = add(new BoolSetting("Boxes", true), "Display", "Draw a box around each item.");
	private final BoolSetting tracers = add(new BoolSetting("Tracers", false), "Display", "Draw a line from the crosshair to each item.");
	private final BoolSetting labels = add(new BoolSetting("Labels", true), "Display", "Name and count above the item.");
	private final ColorSetting color = add(new ColorSetting("Color", 0xFFFFD866), "Display", "Highlight color.");

	public ItemEsp() { super("ItemESP", Category.RENDER); }

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		Camera cam = mc.gameRenderer.getMainCamera();
		Vec3 cp = cam.position();
		Vector3fc fwd = cam.forwardVector();
		Vec3 start = new Vec3(cp.x + fwd.x() * 0.3, cp.y + fwd.y() * 0.3, cp.z + fwd.z() * 0.3);
		double r2 = range.get() * range.get();
		List<String> patterns = items.get();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ItemEntity ie) || ie.getItem().isEmpty()) continue;
			if (mc.player.distanceToSqr(e) > r2) continue;
			String id = BuiltInRegistries.ITEM.getKey(ie.getItem().getItem()).toString();
			if (!IdPattern.matchesAny(patterns, id)) continue;
			if (boxes.get()) f.drawBox(e.getX() - 0.25, e.getY(), e.getZ() - 0.25, e.getX() + 0.25, e.getY() + 0.5, e.getZ() + 0.25, color.get());
			if (tracers.get()) f.drawLine(start.x, start.y, start.z, e.getX(), e.getY() + 0.25, e.getZ(), color.get());
			if (labels.get()) {
				int n = ie.getItem().getCount();
				f.drawText(e.getX(), e.getY() + 0.9, e.getZ(), ie.getItem().getHoverName().getString() + (n > 1 ? " x" + n : ""), color.get());
			}
		}
	}
}
