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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Makes your crosshair "see" other entities as slightly wider than they are, so edge hits register. Only your own
 * targeting changes; the server still decides with the real hitbox and its own reach check, so large values just
 * produce swings the server rejects (and that anti-cheat can notice).
 */
public class HitBox extends Module {
	private static HitBox instance;

	private final NumberSetting expand = add(new NumberSetting("Expand (blocks)", 0.15, 0, 1), "General", "How much wider your targeting treats each hitbox. Keep it small: 0.1-0.3.");
	private final BoolSetting playersOnly = add(new BoolSetting("Players only", true), "General", "Only widen player hitboxes.");
	private final BoolSetting show = add(new BoolSetting("Show boxes", false), "Render", "Draw the widened boxes.");
	private final ColorSetting color = add(new ColorSetting("Box color", 0xFFFF8A3D), "Render", "Color of the widened boxes.", show::get);

	public HitBox() {
		super("HitBox", Category.COMBAT);
		instance = this;
	}

	/** Extra pick radius for an entity, or 0 when this module does not touch it. */
	public static float pickRadius(Entity e) {
		HitBox m = instance;
		if (m == null || !m.enabled() || e == null) return 0;
		if (!(e instanceof LivingEntity) || e == Minecraft.getInstance().player) return 0;
		if (m.playersOnly.get() && !(e instanceof Player)) return 0;
		return m.expand.asFloat();
	}

	@Override
	protected void onRender(RenderFrame f) {
		if (!show.get()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		float r = expand.asFloat();
		if (r <= 0) return;
		for (Player p : mc.level.players()) {
			if (p == mc.player || p == mc.getCameraEntity() || p.isSpectator()) continue;
			AABB b = p.getBoundingBox().inflate(r);
			f.drawBox(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, color.get());
		}
	}
}
