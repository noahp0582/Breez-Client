package breezeclient.breezeclient.client.gui;

public class ConfigScreen extends SettingsScreen {
	public ConfigScreen() {
		super("Config", "config", "Config", "Client-wide options. Module options live in the Modules tab (right click a module).",
			GuiState.CLIENT.settings());
	}
}
