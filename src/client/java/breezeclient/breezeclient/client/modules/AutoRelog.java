package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.safety.Reconnect;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.Random;

/** Leaves and rejoins the server when the player's Y reaches the threshold, so the server re-sends nearby chunks. */
public class AutoRelog extends Module {
	private final NumberSetting yLevel = add(new NumberSetting("Y level", -3, -64, 320));
	private final NumberSetting cooldown = add(new NumberSetting("Cooldown (s)", 60, 10, 600));
	private final NumberSetting delay = add(new NumberSetting("Rejoin delay (s)", 3, 1, 30));
	private final BoolSetting overworldOnly = add(new BoolSetting("Overworld only", true));

	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private int cooldownTicks;

	public AutoRelog() { super("AutoRelog", Category.SAFETY); }

	@Override
	protected void onTick() {
		var p = Minecraft.getInstance().player;
		var level = Minecraft.getInstance().level;
		if (p == null || level == null) return;
		if (cooldownTicks > 0) {
			cooldownTicks--;
			return;
		}
		if (overworldOnly.get() && level.dimension() != Level.OVERWORLD) return;
		if (p.getY() > yLevel.get() || !Reconnect.hasServer()) return;
		cooldownTicks = cooldown.asInt() * 20;
		int wait = limiter.nextDelayTicks(delay.get() * 20, delay.get() * 4, 20);
		Reconnect.relog(wait);
	}
}
