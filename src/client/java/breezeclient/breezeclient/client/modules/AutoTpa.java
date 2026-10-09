package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import net.minecraft.client.Minecraft;

import java.util.Random;

/** Sends teleport requests to one player on a humanized timer, up to a limit. */
public class AutoTpa extends Module {
	private final StringSetting target = add(new StringSetting("Target player", ""), "General", "Who to send the request to.");
	private final StringSetting command = add(new StringSetting("Command", "/tpa"), "General", "Command to send; the player name is appended.");
	private final NumberSetting interval = add(new NumberSetting("Interval (s)", 12, 3, 120), "Timing", "Average seconds between requests.");
	private final NumberSetting limit = add(new NumberSetting("Max requests", 5, 1, 50), "Timing", "Stop after this many requests.");

	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private int wait;
	private int sent;

	public AutoTpa() { super("AutoTPA", Category.MISC); }

	@Override
	protected boolean persistsEnabled() { return false; }

	@Override
	protected void onEnable() {
		sent = 0;
		wait = limiter.between(20, 60);
		if (target.get().isBlank()) {
			Toasts.show("AutoTPA", "Set a target player first in the module settings", 0xFFFF5555);
			setEnabled(false);
		}
	}

	@Override
	protected void onTick() {
		var p = Minecraft.getInstance().player;
		if (p == null || --wait > 0) return;
		String cmd = command.get().trim();
		if (cmd.startsWith("/")) cmd = cmd.substring(1);
		p.connection.sendCommand(cmd + " " + target.get().trim());
		if (++sent >= limit.asInt()) {
			Toasts.show("AutoTPA", "Sent " + sent + " requests");
			setEnabled(false);
			return;
		}
		wait = limiter.nextDelayTicks(interval.get() * 20, interval.get() * 4, 40);
	}
}
