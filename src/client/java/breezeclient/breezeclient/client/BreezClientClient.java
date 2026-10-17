package breezeclient.breezeclient.client;

import breezeclient.breezeclient.client.chat.ChatCommands;
import breezeclient.breezeclient.client.dev.SelfTest;
import breezeclient.breezeclient.client.hud.HudRenderer;
import breezeclient.breezeclient.client.input.KeyPoller;
import breezeclient.breezeclient.client.macro.MacroStore;
import breezeclient.breezeclient.client.modules.KillSwitch;
import breezeclient.breezeclient.client.render.RenderHooks;
import breezeclient.breezeclient.client.safety.Reconnect;
import breezeclient.breezeclient.client.session.Session;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.command.CommandManager;
import breezeclient.breezeclient.core.config.ConfigManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public class BreezClientClient implements ClientModInitializer {
	public static final ModuleManager MODULES = new ModuleManager();
	public static ConfigManager config;
	public static CommandManager commands;
	public static MacroStore macros;

	private static void onToggled(Module m, boolean on) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) return;
		if (m.chatFeedback() || breezeclient.breezeclient.client.gui.GuiState.CLIENT.announceAll.get()) {
			breezeclient.breezeclient.client.gui.Toasts.show(m.name(), on ? "Enabled" : "Disabled", on ? 0xFF55FF55 : 0xFFFF5555);
		}
	}

	@Override
	public void onInitializeClient() {
		breezeclient.breezeclient.client.debug.DebugLog.install(); // first, so problems during startup are recorded too
		breezeclient.breezeclient.client.gui.GuiState.load();
		Modules.registerAll(MODULES);
		ModuleActions.install(MODULES);
		breezeclient.breezeclient.client.gui.GuiState.CLIENT.friends.options(breezeclient.breezeclient.client.modules.OnlinePlayers::names);
		var dir = FabricLoader.getInstance().getConfigDir().resolve("breez-client");
		macros = new MacroStore(dir, MODULES);
		macros.load();
		for (var mod : MODULES.all()) ModuleInfo.apply(mod);
		Module.setListener(BreezClientClient::onToggled);
		config = new ConfigManager(dir, MODULES);
		config.load();
		commands = new CommandManager(".", MODULES);
		Modules.registerCommands(commands, config);
		breezeclient.breezeclient.client.gui.GuiState.CLIENT.showAlerts.setAction(() -> breezeclient.breezeclient.client.chat.Cmd.run("alerts 10"));

		Waypoints.init();
		SelfTest.register();
		Reconnect.register();
		KillSwitch.registerEvents();
		ChunkScanner.register();
		breezeclient.breezeclient.client.world.ChunkAgeTracker.register();
		breezeclient.breezeclient.client.world.CoverageTracker.register();
		breezeclient.breezeclient.client.nav.Baritone.register();
		breezeclient.breezeclient.client.hud.HudElements.register(new breezeclient.breezeclient.client.hud.ChunkMapElement());
		Session.register(config);
		ChatCommands.register(commands);
		KeyPoller.register(MODULES, config);
		RenderHooks.register(MODULES);
		HudRenderer.register(MODULES);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player != null) MODULES.tick();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			config.save();
			breezeclient.breezeclient.client.gui.GuiState.save();
		});
	}
}
