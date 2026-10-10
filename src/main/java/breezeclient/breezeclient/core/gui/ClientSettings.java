package breezeclient.breezeclient.core.gui;

import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.Setting;
import breezeclient.breezeclient.core.settings.StringSetting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

/** Client-wide options shown in the Config tab. */
public final class ClientSettings {
	public final StringSetting prefix = new StringSetting("Command prefix", ".");
	public final NumberSetting humanization = new NumberSetting("Humanization delay scale", 1.0, 0.5, 2.5);
	public final NumberSetting rotationSpeed = new NumberSetting("Camera turn speed", 1.0, 0.3, 3.0);
	public final BoolSetting announceAll = new BoolSetting("Announce every module toggle", false);
	public final BoolSetting autoSave = new BoolSetting("Save config when closing screens", true);
	public final BoolSetting hudOnlyInGame = new BoolSetting("Hide HUD in F1 mode", true);
	public final BoolSetting alertsInChat = new BoolSetting("Alerts in chat", false);

	public ClientSettings() {
		prefix.setMeta("Chat", "Messages starting with this are Breez commands and are never sent to the server. 1-3 characters.");
		humanization.setMeta("Automation", "Multiplies every randomized delay used by automation. Higher = slower and less robotic.");
		rotationSpeed.setMeta("Automation", "Scales how fast the camera turns toward targets during automation.");
		announceAll.setMeta("Notifications", "Show a toast whenever any module is toggled, regardless of its own Chat feedback setting.");
		autoSave.setMeta("General", "Write config files when a Breez screen closes.");
		hudOnlyInGame.setMeta("General", "Do not draw the Breez HUD when Minecraft's HUD is hidden (F1).");
		alertsInChat.setMeta("Notifications", "Send finds (spawners, bases, stashes) to chat instead of the merged pop-ups. Chat fills up fast when many are found.");
	}

	public List<Setting<?>> settings() { return List.of(prefix, humanization, rotationSpeed, announceAll, autoSave, hudOnlyInGame, alertsInChat); }

	/** A valid prefix is 1-3 visible characters without spaces. */
	public String safePrefix() {
		String p = prefix.get().trim();
		return p.isEmpty() || p.length() > 3 || p.contains(" ") || p.startsWith("/") ? "." : p;
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		for (Setting<?> s : settings()) o.add(s.name(), s.toJson());
		return o;
	}

	public void fromJson(JsonElement e) {
		if (e == null || !e.isJsonObject()) return;
		JsonObject o = e.getAsJsonObject();
		for (Setting<?> s : settings()) if (o.has(s.name())) s.fromJson(o.get(s.name()));
	}
}
