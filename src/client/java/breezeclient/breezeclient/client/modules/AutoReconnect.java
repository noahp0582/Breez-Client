package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.safety.Reconnect;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.NumberSetting;

/** Reconnects after the server drops the connection (not after a manual disconnect). */
public class AutoReconnect extends Module {
	private final NumberSetting delay = add(new NumberSetting("Delay (s)", 8, 2, 120));
	private final NumberSetting attempts = add(new NumberSetting("Max attempts", 5, 1, 50));

	public AutoReconnect() {
		super("AutoReconnect", Category.SAFETY);
		Reconnect.autoPolicy = this::enabled;
		Reconnect.autoDelayTicks = () -> delay.asInt() * 20;
		Reconnect.autoMaxAttempts = attempts::asInt;
	}
}
