package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.settings.StringSetting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** The world seed of the server you are on, as a text setting. It lives in the per-server profile, like .seed and the Profiles tab. */
public class ProfileSeedSetting extends StringSetting {
	public ProfileSeedSetting() { super("World seed", ""); }

	@Override
	public String get() {
		String s = StaffTracker.profile().seed();
		return s == null ? "" : s;
	}

	@Override
	public void set(String v) {
		StaffTracker.profile().setSeed(v == null || v.isBlank() ? null : v.trim());
		if (BreezClientClient.config != null) BreezClientClient.config.save();
	}

	@Override
	public boolean isDefault() { return StaffTracker.profile().seed() == null; }

	@Override
	public void reset() { set(null); }

	/** Persisted by the server profile, not by the module. */
	@Override
	public JsonElement toJson() { return new JsonPrimitive(""); }

	@Override
	public void fromJson(JsonElement e) {
		// nothing: the profile loads its own seed
	}
}
