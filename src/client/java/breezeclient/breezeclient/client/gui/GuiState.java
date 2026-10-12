package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.core.gui.GuiTheme;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Persistent GUI state: the theme, window positions, collapsed sections and HUD element positions (gui.json). */
public final class GuiState {
	public static final GuiTheme THEME = new GuiTheme();
	public static final breezeclient.breezeclient.core.gui.ClientSettings CLIENT = new breezeclient.breezeclient.core.gui.ClientSettings();
	/** window id -> {x, y, collapsed(0/1), moved by hand(0/1)} */
	public static final Map<String, int[]> WINDOWS = new HashMap<>();
	/** hud element id -> {x, y} (x, y relative to its anchor corner) */
	public static final Map<String, int[]> HUD = new HashMap<>();
	public static String lastTab = "Modules";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private GuiState() {}

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("breez-client").resolve("gui.json"); }

	public static void load() {
		try {
			if (!Files.isRegularFile(file())) return;
			JsonElement root = JsonParser.parseString(Files.readString(file()));
			if (!root.isJsonObject()) return;
			JsonObject o = root.getAsJsonObject();
			if (o.has("theme") && o.get("theme").isJsonObject()) THEME.fromJsonString(o.get("theme").toString());
			if (o.has("client")) CLIENT.fromJson(o.get("client"));
			readMap(o, "windows", WINDOWS);
			readMap(o, "hud", HUD);
			if (o.has("lastTab") && o.get("lastTab").isJsonPrimitive()) lastTab = o.get("lastTab").getAsString();
		} catch (Exception ignored) {
			// corrupt gui.json: defaults stay
		}
	}

	private static void readMap(JsonObject o, String key, Map<String, int[]> into) {
		if (!o.has(key) || !o.get(key).isJsonObject()) return;
		for (var en : o.getAsJsonObject(key).entrySet()) {
			if (!en.getValue().isJsonArray()) continue;
			JsonArray a = en.getValue().getAsJsonArray();
			int[] v = new int[Math.max(3, a.size())];
			for (int i = 0; i < a.size(); i++) if (a.get(i).isJsonPrimitive() && a.get(i).getAsJsonPrimitive().isNumber()) v[i] = a.get(i).getAsInt();
			into.put(en.getKey(), v);
		}
	}

	public static void save() {
		try {
			JsonObject o = new JsonObject();
			o.add("theme", JsonParser.parseString(THEME.toJsonString()));
			o.add("client", CLIENT.toJson());
			o.add("windows", writeMap(WINDOWS));
			o.add("hud", writeMap(HUD));
			o.addProperty("lastTab", lastTab);
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(o));
		} catch (IOException ignored) {
			// best effort
		}
	}

	private static JsonObject writeMap(Map<String, int[]> m) {
		JsonObject o = new JsonObject();
		for (var en : m.entrySet()) {
			JsonArray a = new JsonArray();
			for (int v : en.getValue()) a.add(v);
			o.add(en.getKey(), a);
		}
		return o;
	}

	/** Returns the saved window entry, creating it from the given defaults. */
	public static int[] window(String id, int defX, int defY) {
		int[] s = WINDOWS.computeIfAbsent(id, k -> new int[] {defX, defY, 0, 0});
		if (s.length < 4) { // saved by a version without the "moved by hand" flag
			s = java.util.Arrays.copyOf(s, 4);
			WINDOWS.put(id, s);
		}
		return s;
	}
}
