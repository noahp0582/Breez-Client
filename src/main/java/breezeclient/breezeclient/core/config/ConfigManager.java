package breezeclient.breezeclient.core.config;

import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigManager {
	private static final Logger LOG = Logger.getLogger("breez-client");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path dir;
	private final ModuleManager modules;
	private final Map<String, ServerProfile> profiles = new HashMap<>();
	private String lastServer;

	public ConfigManager(Path dir, ModuleManager modules) {
		this.dir = dir;
		this.modules = modules;
	}

	public static String normalizeKey(String key) {
		return key == null ? "singleplayer" : key.trim().toLowerCase(Locale.ROOT);
	}

	public ServerProfile profile(String serverKey) {
		return profiles.computeIfAbsent(normalizeKey(serverKey), k -> new ServerProfile());
	}

	public String lastServer() { return lastServer; }
	public void setLastServer(String s) { this.lastServer = s; }

	public void load() {
		JsonObject cfg = readObject(dir.resolve("config.json"));
		if (cfg != null) {
			for (Module m : modules.all()) {
				JsonElement e = cfg.get(m.name());
				if (e != null && e.isJsonObject()) {
					try {
						m.fromJson(e.getAsJsonObject());
					} catch (RuntimeException ex) {
						LOG.log(Level.WARNING, "Bad config for module " + m.name(), ex);
					}
				}
			}
		}
		JsonObject prof = readObject(dir.resolve("profiles.json"));
		if (prof != null) {
			if (prof.has("lastServer") && prof.get("lastServer").isJsonPrimitive()) lastServer = prof.get("lastServer").getAsString();
			if (prof.has("profiles") && prof.get("profiles").isJsonObject()) {
				for (Map.Entry<String, JsonElement> en : prof.getAsJsonObject("profiles").entrySet()) {
					profiles.put(normalizeKey(en.getKey()), ServerProfile.fromJson(en.getValue()));
				}
			}
		}
	}

	public void save() {
		try {
			Files.createDirectories(dir);
			JsonObject cfg = new JsonObject();
			for (Module m : modules.all()) cfg.add(m.name(), m.toJson());
			writeAtomic(dir.resolve("config.json"), GSON.toJson(cfg));

			JsonObject prof = new JsonObject();
			if (lastServer != null) prof.addProperty("lastServer", lastServer);
			JsonObject ps = new JsonObject();
			for (Map.Entry<String, ServerProfile> en : profiles.entrySet()) ps.add(en.getKey(), en.getValue().toJson());
			prof.add("profiles", ps);
			writeAtomic(dir.resolve("profiles.json"), GSON.toJson(prof));
		} catch (IOException e) {
			LOG.log(Level.WARNING, "Could not save config", e);
		}
	}

	/** Returns the parsed object, or null when the file is missing or corrupt (corrupt files are kept as .bad). */
	private JsonObject readObject(Path file) {
		if (!Files.isRegularFile(file)) return null;
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			JsonElement e = JsonParser.parseString(text);
			if (e.isJsonObject()) return e.getAsJsonObject();
			throw new IllegalStateException("root is not an object");
		} catch (Exception ex) {
			LOG.log(Level.WARNING, "Corrupt config " + file + ", using defaults", ex);
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// keep going with defaults
			}
			return null;
		}
	}

	private static void writeAtomic(Path target, String text) throws IOException {
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.writeString(tmp, text, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (java.nio.file.AtomicMoveNotSupportedException e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
