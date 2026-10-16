package breezeclient.breezeclient.core;

import breezeclient.breezeclient.core.settings.ActionSetting;
import breezeclient.breezeclient.core.settings.Setting;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public abstract class Module {
	private final String name;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;
	private int key = -1;
	private String description = "";
	private Detection detection = Detection.UNDETECTED;
	private boolean chatFeedback;
	private boolean visibleInHud = true;
	private boolean toggleOnBindRelease;
	private boolean enabling;

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

	public String description() { return description; }
	public void setDescription(String d) { this.description = d == null ? "" : d; }

	/** How noticeable this module is to anti-cheat and staff. Shown as a badge in the GUI; changes nothing about behavior. */
	public Detection detection() { return detection; }
	public void setDetection(Detection d) { this.detection = d == null ? Detection.UNDETECTED : d; }

	/** Announce toggles (toast/chat). */
	public boolean chatFeedback() { return chatFeedback; }
	public void setChatFeedback(boolean b) { this.chatFeedback = b; }

	/** Show in the HUD module list while enabled. */
	public boolean visibleInHud() { return visibleInHud; }
	public void setVisibleInHud(boolean b) { this.visibleInHud = b; }

	/** Hold-to-enable: the module turns off again when its key is released. */
	public boolean toggleOnBindRelease() { return toggleOnBindRelease; }
	public void setToggleOnBindRelease(boolean b) { this.toggleOnBindRelease = b; }

	protected <S extends Setting<?>> S add(S s) {
		settings.add(s);
		return s;
	}

	protected <S extends Setting<?>> S add(S s, String group, String description) {
		s.setMeta(group, description);
		settings.add(s);
		return s;
	}

	protected <S extends Setting<?>> S add(S s, String group, String description, BooleanSupplier visibleWhen) {
		s.setVisibleWhen(visibleWhen);
		return add(s, group, description);
	}

	/** Adds a setting after construction (used to give a module's page the fields its chat commands needed). */
	public <S extends Setting<?>> S addSetting(S s, String group, String description) { return add(s, group, description); }

	/** Adds a button to the "Actions" section of this module's settings page. */
	public ActionSetting addAction(String name, String description, Runnable action) {
		return add(new ActionSetting(name, action), "Actions", description);
	}

	public void toggle() { setEnabled(!enabled); }

	public void setEnabled(boolean on) {
		if (on == enabled) return;
		enabled = on;
		boolean nested = enabling;
		if (on) {
			enabling = true;
			try {
				onEnable();
			} finally {
				enabling = false;
			}
		} else {
			onDisable();
		}
		// A one-shot module (CoordSnapper, KeyPearl...) or a failed start switches itself off from inside onEnable: that
		// is not a toggle the user should be told about, and announcing it would say "Disabled" and then "Enabled".
		if (nested || (on && !enabled)) return;
		ModuleListener l = listener;
		if (l != null) l.toggled(this, on);
	}

	/** Receives toggle events (the GUI uses it for toasts). */
	public interface ModuleListener { void toggled(Module m, boolean on); }

	private static volatile ModuleListener listener;

	public static void setListener(ModuleListener l) { listener = l; }

	/** Modules that act on their own (pathing, mining, macros) return false so they never restart by themselves on the next launch. */
	protected boolean persistsEnabled() { return true; }

	protected void onEnable() {}
	protected void onDisable() {}
	protected void onTick() {}
	protected void onRender(RenderFrame frame) {}

	/** True while a switched-off module still has something to draw (e.g. a selection being marked). */
	protected boolean rendersWhileDisabled() { return false; }

	/** Invoked by ModuleManager, which isolates exceptions. */
	void tickInternal() { onTick(); }
	void renderInternal(RenderFrame frame) { onRender(frame); }

	/** Short status text shown next to the name in the HUD. */
	public String info() { return ""; }

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled && persistsEnabled());
		o.addProperty("key", key);
		o.addProperty("chatFeedback", chatFeedback);
		o.addProperty("visibleInHud", visibleInHud);
		o.addProperty("toggleOnBindRelease", toggleOnBindRelease);
		JsonObject s = new JsonObject();
		for (Setting<?> st : settings) if (!(st instanceof ActionSetting)) s.add(st.name(), st.toJson());
		o.add("settings", s);
		return o;
	}

	private static boolean bool(JsonObject o, String key, boolean fallback) {
		if (o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean()) return o.get(key).getAsBoolean();
		return fallback;
	}

	public void fromJson(JsonObject o) {
		if (o == null) return;
		if (o.has("key") && o.get("key").isJsonPrimitive() && o.get("key").getAsJsonPrimitive().isNumber()) {
			key = o.get("key").getAsInt();
		}
		chatFeedback = bool(o, "chatFeedback", chatFeedback);
		visibleInHud = bool(o, "visibleInHud", visibleInHud);
		toggleOnBindRelease = bool(o, "toggleOnBindRelease", toggleOnBindRelease);
		if (o.has("settings") && o.get("settings").isJsonObject()) {
			JsonObject s = o.getAsJsonObject("settings");
			for (Setting<?> st : settings) if (s.has(st.name())) st.fromJson(s.get(st.name()));
		}
		if (persistsEnabled() && o.has("enabled") && o.get("enabled").isJsonPrimitive() && o.get("enabled").getAsJsonPrimitive().isBoolean()) {
			setEnabled(o.get("enabled").getAsBoolean());
		}
	}
}
