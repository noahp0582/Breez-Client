package breezeclient.breezeclient.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] constants;

	public EnumSetting(String name, E def) {
		super(name, def);
		this.constants = def.getDeclaringClass().getEnumConstants();
	}

	public void next() { value = constants[(value.ordinal() + 1) % constants.length]; }

	@Override public JsonElement toJson() { return new JsonPrimitive(value.name()); }

	@Override
	public void fromJson(JsonElement e) {
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return;
		String s = e.getAsString();
		for (E c : constants) if (c.name().equalsIgnoreCase(s)) { value = c; return; }
	}
}
