package breezeclient.breezeclient.client.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Saved coordinates (finds). Persisted to waypoints.json and exported to finds.log-style text on request. */
public final class Waypoints {
	public record Waypoint(String name, int x, int y, int z, String server) {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<Waypoint> LIST = new ArrayList<>();
	private static Path file;
	private static String currentServer = "singleplayer";

	private Waypoints() {}

	public static void init() {
		file = FabricLoader.getInstance().getConfigDir().resolve("breez-client").resolve("waypoints.json");
		try {
			if (Files.isRegularFile(file)) {
				JsonArray a = JsonParser.parseString(Files.readString(file)).getAsJsonArray();
				for (var e : a) {
					JsonObject o = e.getAsJsonObject();
					LIST.add(new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
						o.has("server") ? o.get("server").getAsString() : "singleplayer"));
				}
			}
		} catch (Exception ignored) {
			// corrupt file: start empty
		}
	}

	public static void setServer(String key) { currentServer = key == null ? "singleplayer" : key.toLowerCase(Locale.ROOT); }

	public static List<Waypoint> all() { return Collections.unmodifiableList(LIST); }

	public static List<Waypoint> forCurrentServer() {
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : LIST) if (w.server().equals(currentServer)) out.add(w);
		return out;
	}

	public static boolean add(String name, BlockPos p) {
		for (Waypoint w : LIST) {
			if (w.server().equals(currentServer) && w.x() == p.getX() && w.y() == p.getY() && w.z() == p.getZ()) return false;
		}
		LIST.add(new Waypoint(name, p.getX(), p.getY(), p.getZ(), currentServer));
		save();
		return true;
	}

	public static boolean remove(int index) {
		List<Waypoint> cur = forCurrentServer();
		if (index < 0 || index >= cur.size()) return false;
		LIST.remove(cur.get(index));
		save();
		return true;
	}

	public static void clearCurrent() {
		LIST.removeIf(w -> w.server().equals(currentServer));
		save();
	}

	private static void save() {
		if (file == null) return;
		try {
			Files.createDirectories(file.getParent());
			JsonArray a = new JsonArray();
			for (Waypoint w : LIST) {
				JsonObject o = new JsonObject();
				o.addProperty("name", w.name());
				o.addProperty("x", w.x());
				o.addProperty("y", w.y());
				o.addProperty("z", w.z());
				o.addProperty("server", w.server());
				a.add(o);
			}
			Files.writeString(file, GSON.toJson(a));
		} catch (IOException ignored) {
			// best effort
		}
	}
}
