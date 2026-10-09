package breezeclient.breezeclient.client.chat;

import breezeclient.breezeclient.core.command.CommandManager;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ChatCommands {
	private ChatCommands() {}

	public static void register(CommandManager commands) {
		ClientSendMessageEvents.ALLOW_CHAT.register(message -> !commands.handle(message, ChatCommands::print));
	}

	public static void print(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui != null) mc.gui.getChat().addMessage(Component.literal("[Breez] " + text));
	}
}
