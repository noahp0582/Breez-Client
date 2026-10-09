package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.mixin.MinecraftAccessor;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;

/** Shortens the pause between item uses and block placements. */
public class FastUse extends Module {
	private final NumberSetting delay = add(new NumberSetting("Use delay (ticks)", 1, 0, 4), "General", "Vanilla is 4. 0 = no delay.");

	public FastUse() { super("FastUse", Category.MISC); }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		((MinecraftAccessor) mc).breez$setRightClickDelay(Math.min(delay.asInt(), 4));
	}
}
