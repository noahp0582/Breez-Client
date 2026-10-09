package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.StringSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Replaces your own name in chat with an alias (display only, so clips do not show it). */
public class NameProtect extends Module {
	private static NameProtect instance;
	private final StringSetting alias = add(new StringSetting("Alias", "Player"), "General", "Shown instead of your name.");

	public NameProtect() {
		super("NameProtect", Category.MISC);
		instance = this;
	}

	public static void registerEvents() {
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			if (instance == null || !instance.enabled()) return message;
			var mc = Minecraft.getInstance();
			if (mc.player == null) return message;
			String name = mc.player.getName().getString();
			String text = message.getString();
			if (name.isEmpty() || !text.contains(name)) return message;
			return Component.literal(text.replace(name, instance.alias.get()));
		});
	}
}
