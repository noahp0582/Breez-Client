package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.NumberSetting;

/** Overrides the field of view, beyond the vanilla slider range. */
public class CustomFov extends Module {
	private static CustomFov instance;
	private final NumberSetting fov = add(new NumberSetting("FOV", 100, 30, 160), "General", "Field of view in degrees.");

	public CustomFov() {
		super("CustomFOV", Category.RENDER);
		instance = this;
	}

	public static float apply(float vanilla) { return instance != null && instance.enabled() ? instance.fov.asFloat() : vanilla; }
}
