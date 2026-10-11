package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.input.Keys;
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
		Keys.press(mc.options.keyUp);
		if (sprint.get()) Keys.press(mc.options.keySprint);
		if (jump.get()) {
			if (mc.player.horizontalCollision && mc.player.onGround()) Keys.press(mc.options.keyJump);
			else Keys.release(mc.options.keyJump);
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) return;
		Keys.release(mc.options.keyUp); // back to what your fingers are actually holding
		Keys.release(mc.options.keySprint);
		Keys.release(mc.options.keyJump);
	}
}
