package breezeclient.breezeclient.client;

import breezeclient.breezeclient.client.modules.TestModules;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.command.CommandManager;
import breezeclient.breezeclient.core.config.ConfigManager;

/** Single place where every module and command is registered. */
public final class Modules {
	private Modules() {}

	public static void registerAll(ModuleManager m) {
		TestModules.register(m);
	}

	public static void registerCommands(CommandManager c, ConfigManager config) {
	}
}
