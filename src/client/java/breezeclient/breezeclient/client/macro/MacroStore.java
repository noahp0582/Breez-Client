package breezeclient.breezeclient.client.macro;

import breezeclient.breezeclient.client.modules.MacroModule;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.macro.MacroParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** macros.json: the scripts. Keybinds and enabled state live in config.json like any module. */
public final class MacroStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final ModuleManager modules;
	private final List<MacroModule> macros = new ArrayList<>();

	public MacroStore(Path dir, ModuleManager modules) {
		this.file = dir.resolve("macros.json");
		this.modules = modules;
	}

	public List<MacroModule> all() { return macros; }

	/** Registers every saved macro as a module. Call before ConfigManager.load so keybinds are restored. */
	public void load() {
		if (!Files.isRegularFile(file)) return;
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file));
			if (!root.isJsonArray()) return;
			for (JsonElement e : root.getAsJsonArray()) {
				if (!e.isJsonObject()) continue;
				JsonObject o = e.getAsJsonObject();
				if (!o.has("name") || !o.has("script")) continue;
				String name = o.get("name").getAsString();
				if (modules.get(name) != null) continue;
				MacroModule m = new MacroModule(name, o.get("script").getAsString());
				macros.add(m);
				modules.register(m);
			}
		} catch (Exception ignored) {
			// corrupt macro file: ignore, user can re-add
		}
	}

	/** @return an error message, or null on success. */
	public String add(String name, String script) {
		if (name.isBlank() || name.contains(" ")) return "Macro names cannot contain spaces.";
		if (modules.get(name) != null) return "A module or macro called '" + name + "' already exists.";
		var parsed = MacroParser.parse(script);
		if (!parsed.errors().isEmpty()) return String.join("; ", parsed.errors());
		MacroModule m = new MacroModule(name, script);
		macros.add(m);
		modules.register(m);
		save();
		return null;
	}

	/** Replaces a macro's script, keeping its key. @return an error message, or null on success. */
	public String edit(String name, String script) {
		MacroModule old = find(name);
		if (old == null) return "No such macro.";
		var parsed = MacroParser.parse(script);
		if (!parsed.errors().isEmpty()) return String.join("; ", parsed.errors());
		MacroModule m = new MacroModule(old.name(), script);
		m.setKey(old.key());
		m.setChatFeedback(old.chatFeedback());
		macros.set(macros.indexOf(old), m);
		modules.unregister(old);
		modules.register(m);
		save();
		return null;
	}

	public boolean remove(String name) {
		for (MacroModule m : macros) {
			if (m.name().equalsIgnoreCase(name)) {
				macros.remove(m);
				modules.unregister(m);
				save();
				return true;
			}
		}
		return false;
	}

	public MacroModule find(String name) {
		for (MacroModule m : macros) if (m.name().equalsIgnoreCase(name)) return m;
		return null;
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());
			JsonArray a = new JsonArray();
			for (MacroModule m : macros) {
				JsonObject o = new JsonObject();
				o.addProperty("name", m.name());
				o.addProperty("script", m.script());
				a.add(o);
			}
			Files.writeString(file, GSON.toJson(a));
		} catch (IOException ignored) {
			// best effort
		}
	}

	public static boolean isMacro(Module m) { return m instanceof MacroModule; }
}
