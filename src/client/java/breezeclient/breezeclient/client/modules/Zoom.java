package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.NumberSetting;

/** Optical zoom. Bind it; hold-to-zoom is on by default, so the zoom lasts while the key is held. */
public class Zoom extends Module {
	private static Zoom instance;
	private final NumberSetting factor = add(new NumberSetting("Zoom factor", 4, 1.2, 20), "General", "How much the view is magnified.");
	private final NumberSetting smoothing = add(new NumberSetting("Smoothing", 0.25, 0.02, 1), "General", "How fast the zoom eases in and out (1 = instant).");
	private double current = 1.0;

	public Zoom() {
		super("Zoom", Category.RENDER);
		instance = this;
		setToggleOnBindRelease(true);
	}

	@Override
	protected boolean persistsEnabled() { return false; }

	/** Called every frame by the camera mixin; eases in while enabled and back out afterwards. */
	public static float apply(float fov) {
		if (instance == null) return fov;
		double target = instance.enabled() ? instance.factor.get() : 1.0;
		instance.current += (target - instance.current) * instance.smoothing.get();
		if (!instance.enabled() && instance.current < 1.001) instance.current = 1.0;
		return instance.current <= 1.0 ? fov : (float) (fov / instance.current);
	}
}
