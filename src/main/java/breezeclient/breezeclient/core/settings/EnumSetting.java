package breezeclient.breezeclient.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] constants;

	public EnumSetting(String name, E def) {
		super(name, def);
		this.constants = def.getDeclaringClass().getEnumConstants();
	}

	public void next() { value = constants[(value.ordinal() + 1) % constants.length]; }

	/** Option names in declaration order, for dropdowns. */
	public List<String> names() {
		List<String> out = new ArrayList<>();
		for (E c : constants) out.add(c.name());
		return out;
	}

	/** @return true when a constant with that name (case-insensitive) exists and was selected */
	public boolean select(String name) {
		for (E c : constants) if (c.name().equalsIgnoreCase(name)) { value = c; return true; }
		return false;
	}

	@Override public JsonElement toJson() { return new JsonPrimitive(value.name()); }

	@Override
	public void fromJson(JsonElement e) {
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return;
		select(e.getAsString());
	}
}
