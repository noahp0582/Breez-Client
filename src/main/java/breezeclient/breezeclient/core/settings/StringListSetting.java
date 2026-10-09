package breezeclient.breezeclient.core.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/** Case-insensitive set of strings, stored lowercase, insertion ordered. {@link Kind} tells the GUI what to offer when adding. */
public class StringListSetting extends Setting<List<String>> {
	public enum Kind { PLAIN, BLOCK, ITEM, ENTITY }

	private Kind kind = Kind.PLAIN;

	public StringListSetting(String name, List<String> def) {
		super(name, normalize(def));
		this.value = new ArrayList<>(defaultValue());
	}

	public StringListSetting kind(Kind k) {
		this.kind = k;
		return this;
	}

	public Kind kind() { return kind; }

	private static List<String> normalize(List<String> in) {
		List<String> out = new ArrayList<>();
		for (String s : in) {
			String l = s.toLowerCase(java.util.Locale.ROOT);
			if (!out.contains(l)) out.add(l);
		}
		return out;
	}

	public boolean contains(String s) { return value.contains(s.toLowerCase(java.util.Locale.ROOT)); }

	public boolean add(String s) {
		String l = s.toLowerCase(java.util.Locale.ROOT);
		if (value.contains(l)) return false;
		value.add(l);
		return true;
	}

	public boolean remove(String s) { return value.remove(s.toLowerCase(java.util.Locale.ROOT)); }

	@Override public void set(List<String> v) { value = normalize(v); }

	@Override public void reset() { value = new ArrayList<>(defaultValue()); }

	@Override public boolean isDefault() { return value.equals(defaultValue()); }

	@Override
	public JsonElement toJson() {
		JsonArray a = new JsonArray();
		for (String s : value) a.add(s);
		return a;
	}

	@Override
	public void fromJson(JsonElement e) {
		if (e == null || !e.isJsonArray()) return;
		List<String> out = new ArrayList<>();
		for (JsonElement el : e.getAsJsonArray()) {
			if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) out.add(el.getAsString());
		}
		value = normalize(out);
	}
}
