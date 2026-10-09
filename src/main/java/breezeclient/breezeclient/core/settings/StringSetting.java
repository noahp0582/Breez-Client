package breezeclient.breezeclient.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class StringSetting extends Setting<String> {
	public StringSetting(String name, String def) { super(name, def); }

	@Override public void set(String v) { value = v == null ? "" : v; }

	@Override public JsonElement toJson() { return new JsonPrimitive(value); }

	@Override
	public void fromJson(JsonElement e) {
		if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) value = e.getAsString();
	}
}
