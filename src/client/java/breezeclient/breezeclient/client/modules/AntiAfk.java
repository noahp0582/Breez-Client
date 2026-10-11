package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;

import java.util.Random;

/** Occasional small camera turns and hops at irregular intervals. */
public class AntiAfk extends Module {
	private final NumberSetting interval = add(new NumberSetting("Interval (s)", 30, 5, 300));
	private final BoolSetting jump = add(new BoolSetting("Jump", true));

	private final Random rng = new Random();
	private final ActionLimiter limiter = new ActionLimiter(rng);
	private int wait;
	private int jumpTicks;

	public AntiAfk() { super("AntiAFK", Category.MOVEMENT); }

	@Override
	protected void onEnable() { wait = next(); }

	@Override
	protected void onDisable() {
		if (jumpTicks > 0 && Minecraft.getInstance().options != null) net.minecraft.client.KeyMapping.setAll();
		jumpTicks = 0;
	}

	private int next() { return limiter.nextDelayTicks(interval.get() * 20, interval.get() * 5, 60); }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (jumpTicks > 0 && --jumpTicks == 0) net.minecraft.client.KeyMapping.setAll();
		if (--wait > 0 || RotationController.busy() || mc.screen != null) return;
		wait = next();
		if (jump.get() && limiter.chance(0.3)) {
			mc.options.keyJump.setDown(true);
			jumpTicks = 2;
			return;
		}
		Rotation cur = RotationController.current();
		float dyaw = (limiter.chance(0.5) ? 1 : -1) * limiter.between(8, 40);
		float dpitch = limiter.between(-6, 6);
		float pitch = Math.max(-60f, Math.min(60f, cur.pitch() + dpitch));
		RotationController.aimAt(new Rotation(cur.yaw() + dyaw, pitch), 6f);
	}
}
