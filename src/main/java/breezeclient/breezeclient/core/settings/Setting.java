package breezeclient.breezeclient.core.settings;

import com.google.gson.JsonElement;

public abstract class Setting<T> {
	private final String name;
	private final T defaultValue;
	protected T value;

	protected Setting(String name, T defaultValue) {
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String name() { return name; }
	public T get() { return value; }
	public void set(T v) { this.value = v; }
	public void reset() { this.value = defaultValue; }
	public T defaultValue() { return defaultValue; }

	public abstract JsonElement toJson();

	/** Must never throw: bad input is ignored. */
	public abstract void fromJson(JsonElement e);
}
