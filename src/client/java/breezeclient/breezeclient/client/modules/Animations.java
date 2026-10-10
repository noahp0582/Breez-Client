package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;

/** Steadier first-person hand and item motion: no equip dip, no cooldown dip, swing speed, hand offset and size. Visual only. */
public class Animations extends Module {
	private static Animations instance;

	private final BoolSetting noEquip = add(new BoolSetting("No equip animation", true), "Motion", "Items appear in your hand instantly instead of rising into view.");
	private final BoolSetting noCooldownDip = add(new BoolSetting("No cooldown dip", true), "Motion", "The held item stays up after a swing instead of dropping while the attack recharges.");
	private final NumberSetting swingSpeed = add(new NumberSetting("Swing speed", 1.0, 0.5, 3), "Motion", "Multiplier on the first-person swing animation. Only the animation changes; attack timing is unaffected.");
	private final NumberSetting x = add(new NumberSetting("Offset X", 0, -1, 1), "Hand", "Move the hands sideways.");
	private final NumberSetting y = add(new NumberSetting("Offset Y", 0, -1, 1), "Hand", "Move the hands up or down.");
	private final NumberSetting z = add(new NumberSetting("Offset Z", 0, -1, 1), "Hand", "Move the hands toward or away from the camera.");
	private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.3, 1.6), "Hand", "Size of the hands and held items.");

	public Animations() {
		super("Animations", Category.RENDER);
		instance = this;
	}

	private static Animations on() { return instance != null && instance.enabled() ? instance : null; }

	public static boolean noEquip() { Animations m = on(); return m != null && m.noEquip.get(); }

	public static boolean noCooldownDip() { Animations m = on(); return m != null && m.noCooldownDip.get(); }

	public static int swingDuration(int vanilla) {
		Animations m = on();
		if (m == null || m.swingSpeed.get() == 1.0) return vanilla;
		return Math.max(1, (int) Math.round(vanilla / m.swingSpeed.get()));
	}

	/** {x, y, z, scale} or null when nothing changes. */
	public static float[] handTransform() {
		Animations m = on();
		if (m == null) return null;
		float s = m.scale.asFloat();
		if (m.x.get() == 0 && m.y.get() == 0 && m.z.get() == 0 && s == 1f) return null;
		return new float[] {m.x.asFloat(), m.y.asFloat(), m.z.asFloat(), s};
	}
}
