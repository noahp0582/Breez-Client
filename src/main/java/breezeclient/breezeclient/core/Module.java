package breezeclient.breezeclient.core;

import breezeclient.breezeclient.core.settings.Setting;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
	private final String name;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;
	private int key = -1;

	protected Module(String name, Category category) {
		this.name = name;
		this.category = category;
	}

	public String name() { return name; }
	public Category category() { return category; }
	public boolean enabled() { return enabled; }
	public int key() { return key; }
	public void setKey(int key) { this.key = key; }
	public List<Setting<?>> settings() { return Collections.unmodifiableList(settings); }

	protected <S extends Setting<?>> S add(S s) {
		settings.add(s);
		return s;
	}

	public void toggle() { setEnabled(!enabled); }

	public void setEnabled(boolean on) {
		if (on == enabled) return;
		enabled = on;
		if (on) onEnable(); else onDisable();
	}

	protected void onEnable() {}
	protected void onDisable() {}
	protected void onTick() {}
	protected void onRender(RenderFrame frame) {}

	/** Invoked by ModuleManager, which isolates exceptions. */
	void tickInternal() { onTick(); }
	void renderInternal(RenderFrame frame) { onRender(frame); }

	/** Short status text shown next to the name in the HUD. */
	public String info() { return ""; }

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		o.addProperty("key", key);
		JsonObject s = new JsonObject();
		for (Setting<?> st : settings) s.add(st.name(), st.toJson());
		o.add("settings", s);
		return o;
	}

	public void fromJson(JsonObject o) {
		if (o == null) return;
		if (o.has("key") && o.get("key").isJsonPrimitive() && o.get("key").getAsJsonPrimitive().isNumber()) {
			key = o.get("key").getAsInt();
		}
		if (o.has("settings") && o.get("settings").isJsonObject()) {
			JsonObject s = o.getAsJsonObject("settings");
			for (Setting<?> st : settings) if (s.has(st.name())) st.fromJson(s.get(st.name()));
		}
		if (o.has("enabled") && o.get("enabled").isJsonPrimitive() && o.get("enabled").getAsJsonPrimitive().isBoolean()) {
			setEnabled(o.get("enabled").getAsBoolean());
		}
	}
}
