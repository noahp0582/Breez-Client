package breezeclient.breezeclient.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A GLFW key code; -1 means unbound. */
public class KeySetting extends Setting<Integer> {
	public KeySetting(String name, int def) { super(name, def); }

	@Override public JsonElement toJson() { return new JsonPrimitive(value); }

	@Override
	public void fromJson(JsonElement e) {
		if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) value = Math.max(-1, e.getAsInt());
	}
}
