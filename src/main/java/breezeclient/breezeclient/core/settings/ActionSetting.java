package breezeclient.breezeclient.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A button in a module's settings: not a value, just something to run. Never saved and never shown as changed. */
public class ActionSetting extends Setting<Boolean> {
	private Runnable action;

	public ActionSetting(String name, Runnable action) {
		super(name, false);
		this.action = action;
	}

	public void setAction(Runnable action) { this.action = action; }

	/** Runs the action; a failing action never takes the GUI down. */
	public void run() {
		try {
			action.run();
		} catch (RuntimeException ignored) {
			// the action reports its own problems
		}
	}

	@Override public boolean isDefault() { return true; }

	@Override public void reset() {}

	@Override public JsonElement toJson() { return JsonNull.INSTANCE; }

	@Override public void fromJson(JsonElement e) {}
}
