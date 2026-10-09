package breezeclient.breezeclient.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;

	public NumberSetting(String name, double def, double min, double max) {
		super(name, clamp(def, min, max));
		this.min = min;
		this.max = max;
	}

	private static double clamp(double v, double min, double max) {
		if (Double.isNaN(v)) return min;
		return Math.max(min, Math.min(max, v));
	}

	public double min() { return min; }
	public double max() { return max; }
	public int asInt() { return (int) Math.round(value); }
	public float asFloat() { return value.floatValue(); }

	@Override public void set(Double v) { value = clamp(v == null ? min : v, min, max); }

	@Override public JsonElement toJson() { return new JsonPrimitive(value); }

	@Override
	public void fromJson(JsonElement e) {
		if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) set(e.getAsDouble());
	}
}
