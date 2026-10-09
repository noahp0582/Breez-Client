package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.modules.MacroModule;
import breezeclient.breezeclient.core.macro.MacroParser;

/** Create, edit, bind, run and delete macros. Script steps: say, wait, slot, use, attack, look, turn, sneak, jump, loop. */
public class MacrosScreen extends BreezScreen {
	private final Controls.TextBox newName = new Controls.TextBox();
	private final Controls.TextBox newScript = new Controls.TextBox();

	public MacrosScreen() {
		super("Macros");
		newName.placeholder = "name (no spaces)";
		newScript.placeholder = "say hi; wait 20; slot 2; use 5; loop";
	}

	@Override
	protected void build() {
		int w = 340;
		Window win = new Window("macros", "Macros", w, 14, TAB_H + 10);
		win.maxH = vh - TAB_H - 20;
		win.content.add(new Paragraph("Steps are separated by ; : say <text>, wait <ticks>, slot <1-9>, use [ticks], attack [ticks], "
			+ "look <yaw> <pitch>, turn <dyaw> <dpitch>, sneak on|off, jump, loop (last).", true));

		for (MacroModule m : BreezClientClient.macros.all()) {
			Section sec = new Section("macro:" + m.name(), m.name());
			Controls.TextBox script = new Controls.TextBox(m.script());
			script.onCommit = t -> {
				String err = BreezClientClient.macros.edit(m.name(), t);
				if (err != null) Toasts.show("Macro " + m.name(), err, 0xFFFF5555);
				else {
					Toasts.show("Macro " + m.name(), "Script updated");
					rebuild();
				}
			};
			sec.add(script);
			sec.add(new Controls.Label(() -> {
				var r = MacroParser.parse(m.script());
				return r.errors().isEmpty() ? r.steps().size() + " steps" + (r.loop() ? ", loops" : "") : "Error: " + r.errors().get(0);
			}, true));
			sec.add(new Controls.Button("Run / stop", () -> m.toggle()));
			sec.add(new SettingRow(new KeyProxy(m)));
			sec.add(new Controls.Button("Delete", () -> {
				BreezClientClient.macros.remove(m.name());
				rebuild();
			}));
			win.content.add(sec);
		}

		Section add = new Section("macro:new", "New macro");
		add.add(newName);
		add.add(newScript);
		add.add(new Controls.Button("Create", () -> {
			String err = BreezClientClient.macros.add(newName.text.trim(), newScript.text.trim());
			if (err != null) Toasts.show("Macro not created", err, 0xFFFF5555);
			else {
				Toasts.show("Macro created", newName.text.trim() + " - bind it in its section");
				newName.setText("");
				newScript.setText("");
				rebuild();
			}
		}));
		win.content.add(add);
		windows.add(win);
	}

	private void rebuild() {
		var old = windows.isEmpty() ? null : windows.get(0);
		double sc = old == null ? 0 : old.scroll;
		windows.clear();
		build();
		if (!windows.isEmpty()) windows.get(0).scroll = sc;
	}

	/** Adapts a module's key to a KeySetting row. */
	private static final class KeyProxy extends breezeclient.breezeclient.core.settings.KeySetting {
		private final MacroModule m;

		KeyProxy(MacroModule m) {
			super("Key", -1);
			this.m = m;
			setMeta("Macro", "Press this key in game to run the macro.");
		}

		@Override
		public Integer get() { return m.key(); }

		@Override
		public void set(Integer v) { m.setKey(v); }

		@Override
		public boolean isDefault() { return m.key() < 0; }

		@Override
		public void reset() { m.setKey(-1); }
	}
}
