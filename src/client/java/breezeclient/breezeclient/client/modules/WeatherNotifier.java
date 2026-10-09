package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import net.minecraft.client.Minecraft;

/** Toast when the weather changes. */
public class WeatherNotifier extends Module {
	private int last = -1; // 0 clear, 1 rain, 2 thunder

	public WeatherNotifier() { super("WeatherNotifier", Category.MISC); }

	@Override
	protected void onEnable() { last = -1; }

	@Override
	protected void onTick() {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		int now = level.getThunderLevel(1f) > 0.9f ? 2 : (level.getRainLevel(1f) > 0.2f ? 1 : 0);
		if (last >= 0 && now != last) {
			String msg = switch (now) {
				case 2 -> "A thunderstorm started";
				case 1 -> last == 2 ? "The thunder stopped, still raining" : "It started raining";
				default -> "The weather cleared";
			};
			Toasts.show("Weather", msg, now == 2 ? 0xFFFFD23F : 0);
		}
		last = now;
	}
}
