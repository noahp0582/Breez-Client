package breezeclient.breezeclient.client.chat;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.gui.Toasts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs a chat command from a GUI button, exactly as if it had been typed. One reply line becomes a pop-up; several lines go
 * to chat (so lists stay readable) with a pop-up saying how many.
 */
public final class Cmd {
	private Cmd() {}

	/** @param line the command without the prefix, e.g. "spawner chest" */
	public static void run(String line) {
		var commands = BreezClientClient.commands;
		if (commands == null) return;
		List<String> out = new ArrayList<>();
		commands.handle(commands.prefix() + line, out::add);
		if (out.isEmpty()) return;
		String name = line.trim().split("\\s+")[0];
		String title = name.isEmpty() ? "Breez" : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
		if (out.size() == 1) {
			Toasts.show(title, out.get(0));
			return;
		}
		for (String s : out) ChatCommands.print(s);
		Toasts.show(title, out.get(0) + " (+" + (out.size() - 1) + " more lines in chat)");
	}
}
