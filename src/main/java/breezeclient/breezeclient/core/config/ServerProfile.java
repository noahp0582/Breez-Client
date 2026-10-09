package breezeclient.breezeclient.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Per-server data: world seed and staff names. */
public class ServerProfile {
	private String seed;
	private final List<String> staff = new ArrayList<>();

	public String seed() { return seed; }
	public void setSeed(String seed) { this.seed = seed; }
	public List<String> staff() { return Collections.unmodifiableList(staff); }

	public boolean addStaff(String name) {
		String l = name.toLowerCase();
		if (staff.contains(l)) return false;
		staff.add(l);
		return true;
	}

	public boolean removeStaff(String name) { return staff.remove(name.toLowerCase()); }

	public boolean isStaff(String name) { return name != null && staff.contains(name.toLowerCase()); }

	JsonObject toJson() {
		JsonObject o = new JsonObject();
		if (seed != null) o.addProperty("seed", seed);
		JsonArray a = new JsonArray();
		for (String s : staff) a.add(s);
		o.add("staff", a);
		return o;
	}

	static ServerProfile fromJson(JsonElement e) {
		ServerProfile p = new ServerProfile();
		if (e == null || !e.isJsonObject()) return p;
		JsonObject o = e.getAsJsonObject();
		if (o.has("seed") && o.get("seed").isJsonPrimitive()) p.seed = o.get("seed").getAsString();
		if (o.has("staff") && o.get("staff").isJsonArray()) {
			for (JsonElement s : o.getAsJsonArray("staff")) {
				if (s.isJsonPrimitive() && s.getAsJsonPrimitive().isString()) p.addStaff(s.getAsString());
			}
		}
		return p;
	}
}
