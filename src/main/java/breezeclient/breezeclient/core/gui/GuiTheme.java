package breezeclient.breezeclient.core.gui;

import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.Setting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;

/** Look of the GUI. Every field is a Setting, so the GUI tab edits them with the normal widgets. */
public final class GuiTheme {
	public enum Side { RIGHT, LEFT }

	public enum ToastPosition { TOP_CENTER, TOP_RIGHT, BOTTOM_RIGHT }

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public final ColorSetting accent = new ColorSetting("Accent color", 0xFF913DE2);
	public final ColorSetting textColor = new ColorSetting("Text color", 0xFFFFFFFF);
	public final ColorSetting background = new ColorSetting("Background color", 0xFF141414);
	public final NumberSetting backgroundAlpha = new NumberSetting("Background opacity", 0.88, 0.2, 1.0);
	public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.6, 2.0);
	public final BoolSetting rounded = new BoolSetting("Rounded corners", true);
	public final BoolSetting dimBackground = new BoolSetting("Dim game behind GUI", true);
	public final BoolSetting shadows = new BoolSetting("Window shadows", true);
	public final NumberSetting animationSpeed = new NumberSetting("Animation speed", 1.0, 0.0, 3.0);
	public final EnumSetting<Side> moduleListSide = new EnumSetting<>("HUD module list side", Side.RIGHT);
	public final BoolSetting hudShadow = new BoolSetting("HUD text shadow", true);
	public final BoolSetting toastsEnabled = new BoolSetting("Toast notifications", true);
	public final NumberSetting toastSeconds = new NumberSetting("Toast duration (s)", 4.0, 1.0, 15.0);
	public final EnumSetting<ToastPosition> toastPosition = new EnumSetting<>("Toast position", ToastPosition.TOP_CENTER);

	public GuiTheme() {
		accent.setMeta("Colors", "Highlight color for enabled modules, sliders and toggles.");
		textColor.setMeta("Colors", "Main text color.");
		background.setMeta("Colors", "Window background color.");
		backgroundAlpha.setMeta("Colors", "How see-through windows are.");
		scale.setMeta("Layout", "Scales the whole GUI independently of Minecraft's GUI scale.");
		rounded.setMeta("Layout", "Round the corners of windows and buttons.");
		dimBackground.setMeta("Layout", "Darken the world while a GUI screen is open.");
		shadows.setMeta("Layout", "Soft shadow under windows, tooltips and toasts.");
		animationSpeed.setMeta("Layout", "Speed of GUI animations. 0 disables them.");
		moduleListSide.setMeta("HUD", "Which side of the screen the enabled-module list uses.");
		hudShadow.setMeta("HUD", "Draw a shadow behind HUD text.");
		toastsEnabled.setMeta("Toasts", "Show pop-up notifications instead of chat messages.");
		toastSeconds.setMeta("Toasts", "How long a toast stays on screen.");
		toastPosition.setMeta("Toasts", "Where toasts appear.");
	}

	public List<Setting<?>> settings() {
		return List.of(accent, textColor, background, backgroundAlpha, scale, rounded, dimBackground, shadows, animationSpeed, moduleListSide, hudShadow,
			toastsEnabled, toastSeconds, toastPosition);
	}

	public int panelColor() { return ColorMath.withAlpha(background.get(), (int) Math.round(255 * backgroundAlpha.get())); }

	public int titleColor() { return ColorMath.mix(background.get() | 0xFF000000, 0xFF000000, 0.35); }

	public int accentFaded(double f) { return ColorMath.fade(accent.get(), f); }

	public String toJsonString() {
		JsonObject o = new JsonObject();
		for (Setting<?> s : settings()) o.add(s.name(), s.toJson());
		return GSON.toJson(o);
	}

	/** Unknown or invalid entries keep their defaults; never throws. */
	public void fromJsonString(String text) {
		try {
			JsonElement e = JsonParser.parseString(text);
			if (!e.isJsonObject()) return;
			JsonObject o = e.getAsJsonObject();
			for (Setting<?> s : settings()) if (o.has(s.name())) s.fromJson(o.get(s.name()));
		} catch (RuntimeException ignored) {
			// corrupt theme file: defaults stay
		}
	}
}
