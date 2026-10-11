package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.settings.StringListSetting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.List;

/**
 * The staff names of the server you are on, shown as an ordinary list setting. The data lives in the per-server profile
 * (the same list as .staff and the Profiles tab), not in the module's own settings, so it is not saved twice.
 */
public class StaffNamesSetting extends StringListSetting {
	public StaffNamesSetting() {
		super("Staff names", List.of());
		options(OnlinePlayers::names);
	}

	private void save() {
		if (BreezClientClient.config != null) BreezClientClient.config.save();
	}

	@Override
	public List<String> get() { return StaffTracker.profile().staff(); }

	@Override
	public boolean add(String s) {
		boolean added = StaffTracker.profile().addStaff(s);
		if (added) save();
		return added;
	}

	@Override
	public boolean remove(String s) {
		boolean removed = StaffTracker.profile().removeStaff(s);
		if (removed) save();
		return removed;
	}

	@Override
	public boolean contains(String s) { return StaffTracker.profile().isStaff(s); }

	@Override
	public void set(List<String> v) {
		var profile = StaffTracker.profile();
		for (String s : List.copyOf(profile.staff())) profile.removeStaff(s);
		for (String s : v) profile.addStaff(s);
		save();
	}

	@Override
	public boolean isDefault() { return StaffTracker.profile().staff().isEmpty(); }

	@Override
	public void reset() { set(List.of()); }

	/** Persisted by the server profile, not by the module. */
	@Override
	public JsonElement toJson() { return new JsonArray(); }

	@Override
	public void fromJson(JsonElement e) {
		// nothing: the profile loads its own staff list
	}
}
