package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.mixin.GameRendererAccessor;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Motion blur by blending each frame with the previous one, as a post-processing effect (shader and chain files live in
 * this mod's assets). The strength is a preset 1-5 because the blend factor is baked into one chain per preset.
 */
public class MotionBlur extends Module {
	private static final String NAMESPACE = "breez-client";

	private final NumberSetting strength = add(new NumberSetting("Strength", 3, 1, 5), "General", "1 = light trails, 5 = heavy smear. Higher frame rates need a higher setting to look the same.");

	private int failures;

	public MotionBlur() { super("MotionBlur", Category.RENDER); }

	private Identifier wanted() {
		return Identifier.fromNamespaceAndPath(NAMESPACE, "motion_blur_" + Math.max(1, Math.min(5, strength.asInt())));
	}

	private static boolean ours(Identifier id) { return id != null && NAMESPACE.equals(id.getNamespace()); }

	@Override
	protected void onEnable() { failures = 0; }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		Identifier current = mc.gameRenderer.currentPostEffect();
		if (current != null && !ours(current)) return; // vanilla effect such as a spectated creeper: leave it
		Identifier want = wanted();
		if (want.equals(current)) {
			failures = 0;
			return;
		}
		((GameRendererAccessor) mc.gameRenderer).breez$setPostEffect(want);
		if (!want.equals(mc.gameRenderer.currentPostEffect()) && ++failures >= 3) {
			Toasts.show("MotionBlur", "The blur effect could not load; see the log");
			setEnabled(false);
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameRenderer != null && ours(mc.gameRenderer.currentPostEffect())) mc.gameRenderer.clearPostEffect();
	}
}
