package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import net.minecraft.client.Minecraft;

/** Holds the forward key. */
public class AutoWalk extends Module {
	private final BoolSetting sprint = add(new BoolSetting("Sprint", false), "General", "Also hold sprint.");
	private final BoolSetting jump = add(new BoolSetting("Jump over obstacles", true), "General", "Hop automatically when something blocks the way.");

	public AutoWalk() { super("AutoWalk", Category.MOVEMENT); }

	@Override
	protected boolean persistsEnabled() { return false; }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null || mc.player == null) return;
		mc.options.keyUp.setDown(true);
		if (sprint.get()) mc.options.keySprint.setDown(true);
		if (jump.get()) mc.options.keyJump.setDown(mc.player.horizontalCollision && mc.player.onGround());
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) return;
		net.minecraft.client.KeyMapping.setAll(); // back to what your fingers are actually holding
	}
}
