package breezeclient.breezeclient.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opens the screen behind each tab name. */
public final class Tabs {
	private Tabs() {}

	public static Screen create(String tab) {
		return switch (tab) {
			case "Config" -> new ConfigScreen();
			case "GUI" -> new GuiThemeScreen();
			case "Macros" -> new MacrosScreen();
			case "Profiles" -> new ProfilesScreen();
			case "Waypoints" -> new WaypointsScreen();
			case "HUD" -> new HudEditorScreen();
			default -> new ModulesScreen();
		};
	}

	public static void open(String tab) { Minecraft.getInstance().setScreen(create(tab)); }

	public static void openLast() { open(GuiState.lastTab); }
}
