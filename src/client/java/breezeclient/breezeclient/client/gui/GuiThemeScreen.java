package breezeclient.breezeclient.client.gui;

public class GuiThemeScreen extends SettingsScreen {
	public GuiThemeScreen() {
		super("GUI", "gui", "GUI", "Change how the GUI looks. Changes apply instantly and are saved when you close the screen.",
			GuiState.THEME.settings());
	}

	@Override
	protected void build() {
		super.build();
		if (windows.isEmpty()) return;
		windows.get(0).content.add(new Controls.Button("Reset window layout", () -> {
			GuiState.WINDOWS.clear();
			Toasts.show("GUI", "Window layout reset");
			Tabs.open("GUI");
		}));
	}
}
