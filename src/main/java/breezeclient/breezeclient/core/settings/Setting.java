package breezeclient.breezeclient.core.settings;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
	private final String name;
	private final T defaultValue;
	protected T value;
	private String description = "";
	private String group = "General";
	private BooleanSupplier visibleWhen = () -> true;

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

	public String description() { return description; }
	public String group() { return group; }

	/** Whether the GUI should show this setting right now (e.g. only when a parent toggle is on). */
	public boolean visible() {
		try {
			return visibleWhen.getAsBoolean();
		} catch (RuntimeException e) {
			return true;
		}
	}

	/** Sets GUI metadata (section name and tooltip text). */
	public void setMeta(String group, String description) {
		this.group = group;
		this.description = description;
	}

	public void setVisibleWhen(BooleanSupplier condition) { this.visibleWhen = condition; }

	public boolean isDefault() { return java.util.Objects.equals(value, defaultValue); }

	public abstract JsonElement toJson();

	/** Must never throw: bad input is ignored. */
	public abstract void fromJson(JsonElement e);
}
