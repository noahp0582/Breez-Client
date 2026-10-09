package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.session.Session;
import breezeclient.breezeclient.core.config.ServerProfile;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.settings.StringSetting;

import java.util.List;

/** Per-server data: world seed (for SeedOres) and the staff list used by KillSwitch / StaffList. */
public class ProfilesScreen extends BreezScreen {
	public ProfilesScreen() { super("Profiles"); }

	@Override
	protected void build() {
		int w = 320;
		Window win = new Window("profiles", "Server profile: " + Session.serverKey(), w, 14, TAB_H + 10);
		win.maxH = vh - TAB_H - 20;
		ServerProfile p = BreezClientClient.config.profile(Session.serverKey());
		win.content.add(new Paragraph("Settings stored per server address. Join a server to edit its profile; singleplayer has its own.", true));

		StringSetting seed = new StringSetting("World seed", "") {
			@Override
			public String get() { return p.seed() == null ? "" : p.seed(); }

			@Override
			public void set(String v) { p.setSeed(v == null || v.isBlank() ? null : v.trim()); }

			@Override
			public boolean isDefault() { return p.seed() == null; }

			@Override
			public void reset() { p.setSeed(null); }
		};
		seed.setMeta("World", "The server's real world seed. Enables SeedOres (ore and ancient debris prediction). Numbers or any text.");

		StringListSetting staff = new StringListSetting("Staff names", List.of()) {
			@Override
			public List<String> get() { return p.staff(); }

			@Override
			public boolean add(String s) {
				boolean r = p.addStaff(s);
				return r;
			}

			@Override
			public boolean remove(String s) { return p.removeStaff(s); }

			@Override
			public boolean contains(String s) { return p.isStaff(s); }

			@Override
			public boolean isDefault() { return p.staff().isEmpty(); }

			@Override
			public void reset() { for (String s : List.copyOf(p.staff())) p.removeStaff(s); }
		};
		staff.setMeta("Staff", "Names the KillSwitch and StaffList treat as staff on this server.");

		Section world = new Section("profiles:world", "World");
		world.add(new SettingRow(seed));
		Section st = new Section("profiles:staff", "Staff");
		st.add(new SettingRow(staff));
		win.content.add(world);
		win.content.add(st);
		windows.add(win);
	}
}
