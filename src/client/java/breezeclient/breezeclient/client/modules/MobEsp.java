package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Highlights hostile mobs (and optionally passive ones) through walls. */
public class MobEsp extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 64, 8, 256), "Render", "How far away mobs are drawn.");
	private final BoolSetting tracers = add(new BoolSetting("Tracers", false), "Render", "Lines from the crosshair to each mob.");
	private final BoolSetting hostile = add(new BoolSetting("Hostile", true), "Types", "Monsters.");
	private final BoolSetting passive = add(new BoolSetting("Passive", false), "Types", "Animals, villagers and other non-hostile mobs.");
	private final ColorSetting hostileColor = add(new ColorSetting("Hostile color", 0xFFFF4444), "Colors", "Color of hostile mobs.");
	private final ColorSetting passiveColor = add(new ColorSetting("Passive color", 0xFF55DD77), "Colors", "Color of passive mobs.");

	public MobEsp() { super("MobESP", Category.RENDER); }

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		var cam = mc.gameRenderer.getMainCamera();
		Vec3 cp = cam.position();
		var fwd = cam.forwardVector();
		double r2 = range.get() * range.get();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity) || e instanceof Player || e == mc.player || e.distanceToSqr(cp) > r2) continue;
			boolean enemy = e instanceof Enemy;
			if (enemy ? !hostile.get() : !passive.get()) continue;
			int c = (enemy ? hostileColor.get() : passiveColor.get()) | 0xFF000000;
			AABB b = e.getBoundingBox();
			f.drawBox(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, c);
			if (tracers.get()) f.drawLine(cp.x + fwd.x() * 0.3, cp.y + fwd.y() * 0.3, cp.z + fwd.z() * 0.3, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), c);
		}
	}
}
