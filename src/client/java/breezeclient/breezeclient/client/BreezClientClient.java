package breezeclient.breezeclient.client;

import breezeclient.breezeclient.client.chat.ChatCommands;
import breezeclient.breezeclient.client.hud.HudRenderer;
import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.input.KeyPoller;
import breezeclient.breezeclient.client.render.RenderHooks;
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

	@Override
	public void onInitializeClient() {
		Modules.registerAll(MODULES);
		config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve("breez-client"), MODULES);
		config.load();
		commands = new CommandManager(".", MODULES);
		Modules.registerCommands(commands, config);

		ChatCommands.register(commands);
		KeyPoller.register(MODULES, config);
		RenderHooks.register(MODULES);
		HudRenderer.register(MODULES);
		RotationController.register();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player != null) MODULES.tick();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> config.save());
	}
}
