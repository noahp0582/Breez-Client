package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Stashes that are entities, not blocks: chest boats and minecarts, loaded donkeys and llamas, item frames, armor stands. */
public class EntityStorageEsp extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 128, 16, 512), "Render", "How far away entities are drawn.");
	private final BoolSetting tracers = add(new BoolSetting("Tracers", false), "Render", "Lines from the crosshair to each entity.");
	private final BoolSetting labels = add(new BoolSetting("Labels", true), "Render", "Show what each entity is.");
	private final BoolSetting boats = add(new BoolSetting("Chest boats", true), "Types", "Boats and rafts with chests.");
	private final BoolSetting minecarts = add(new BoolSetting("Chest and hopper minecarts", true), "Types", "Minecarts with chests or hoppers.");
	private final BoolSetting animals = add(new BoolSetting("Pack animals with chests", true), "Types", "Donkeys, mules and llamas carrying a chest.");
	private final BoolSetting frames = add(new BoolSetting("Item frames", false), "Types", "Item frames and glow item frames that hold an item or map.");
	private final BoolSetting stands = add(new BoolSetting("Armor stands", false), "Types", "Armor stands.");

	public EntityStorageEsp() { super("EntityStorageESP", Category.RENDER); }

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		var cam = mc.gameRenderer.getMainCamera();
		Vec3 cp = cam.position();
		var fwd = cam.forwardVector();
		double r2 = range.get() * range.get();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.distanceToSqr(cp) > r2) continue;
			String label = null;
			int color = 0xFFFFAA33;
			if (boats.get() && e instanceof AbstractChestBoat) label = "Chest boat";
			else if (minecarts.get() && e instanceof MinecartChest) label = "Chest minecart";
			else if (minecarts.get() && e instanceof MinecartHopper) label = "Hopper minecart";
			else if (animals.get() && e instanceof AbstractChestedHorse h && h.hasChest()) label = e.getType().getDescription().getString() + " (chest)";
			else if (frames.get() && e instanceof ItemFrame fr && !fr.getItem().isEmpty()) {
				label = fr.getItem().getHoverName().getString();
				color = 0xFF55DDFF;
			} else if (stands.get() && e instanceof ArmorStand) {
				label = "Armor stand";
				color = 0xFFCCCC66;
			}
			if (label == null) continue;
			AABB b = e.getBoundingBox();
			int c = color | 0xFF000000;
			f.drawBox(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, c);
			if (labels.get()) f.drawText(e.getX(), b.maxY + 0.4, e.getZ(), label, c);
			if (tracers.get()) f.drawLine(cp.x + fwd.x() * 0.3, cp.y + fwd.y() * 0.3, cp.z + fwd.z() * 0.3, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), c);
		}
	}
}
