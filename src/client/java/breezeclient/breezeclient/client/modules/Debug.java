package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.debug.DebugLog;
import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Shows Breez Client's warnings and errors as they happen so they can be reported. Everything is recorded from startup
 * whether or not this module is on (see {@link DebugLog}); turning it on only adds the live chat lines and pop-ups.
 * {@code .debug copy} puts a full report with stack traces on the clipboard.
 */
public class Debug extends Module {
	private final BoolSetting chat = add(new BoolSetting("Chat", true), "Show", "Print each warning and error in chat (only you see it).");
	private final BoolSetting toasts = add(new BoolSetting("Pop-ups", true), "Show", "Pop-up for errors.");
	private final BoolSetting warnings = add(new BoolSetting("Warnings", true), "Show", "Show warnings as well as errors.");
	private final BoolSetting game = add(new BoolSetting("Minecraft warnings", false), "Show",
		"Also catch warnings and errors from Minecraft itself and other mods. Vanilla logs a lot of harmless ones, so this is off by default.");

	public Debug() {
		super("Debug", Category.CLIENT);
		addAction("Copy report", "Copy versions, enabled modules and every recorded problem with stack traces to the clipboard.", () -> {
			Minecraft.getInstance().keyboardHandler.setClipboard(DebugLog.report());
			Toasts.show("Debug", DebugLog.entries().size() + " entries copied to the clipboard");
		});
		addAction("Clear", "Forget the recorded problems (the log file keeps them).", () -> {
			DebugLog.clear();
			Toasts.show("Debug", "Cleared");
		});
		DebugLog.listen(this::onEntry);
	}

	@Override
	public String info() {
		int errors = DebugLog.count(DebugLog.Kind.ERROR), warns = DebugLog.count(DebugLog.Kind.WARN);
		if (errors == 0 && warns == 0) return "";
		return errors + "E " + warns + "W";
	}

	@Override
	protected void onEnable() {
		DebugLog.setGameWarnings(game.get());
		int n = DebugLog.entries().size();
		if (n > 0) breezeclient.breezeclient.client.chat.ChatCommands.print(n + " problems recorded so far. .debug lists them, .debug copy copies a report.");
	}

	@Override
	protected void onTick() { DebugLog.setGameWarnings(game.get()); }

	@Override
	protected void onDisable() { DebugLog.setGameWarnings(false); }

	private void onEntry(DebugLog.Entry e) {
		if (!enabled() || (e.kind() == DebugLog.Kind.WARN && !warnings.get())) return;
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> { // entries can arrive from network, render or worker threads
			if (mc.player == null || mc.gui == null) return;
			boolean error = e.kind() == DebugLog.Kind.ERROR;
			if (chat.get()) {
				String msg = e.message().length() > 220 ? e.message().substring(0, 220) + "..." : e.message();
				mc.gui.getChat().addMessage(Component.literal((error ? "§c[Breez ERROR] " : "§e[Breez WARN] ") + "§7[" + e.source() + "] §f" + msg));
			}
			if (toasts.get() && error) Toasts.show("Error in " + e.source(), e.message(), 0xFFFF5555);
		});
	}
}
