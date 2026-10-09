package breezeclient.breezeclient.client.gui;

public class GuiThemeScreen extends SettingsScreen {
	public GuiThemeScreen() {
		super("GUI", "gui", "GUI", "Change how the GUI looks. Changes apply instantly and are saved when you close the screen.",
			GuiState.THEME.settings());
	}
}
