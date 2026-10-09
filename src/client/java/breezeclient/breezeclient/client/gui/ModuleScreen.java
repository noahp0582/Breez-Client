package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.Setting;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Settings of one module: description, grouped settings, then the bind / feedback / visibility toggles. */
public class ModuleScreen extends BreezScreen {
	private final Module module;

	public ModuleScreen(Module module) {
		super("Modules");
		this.module = module;
	}

	/** Adds a row per setting, grouped into collapsible sections. Shared with the generic settings screens. */
	static void addSettings(Container into, String keyPrefix, List<Setting<?>> settings) {
		Map<String, Section> groups = new LinkedHashMap<>();
		for (Setting<?> s : settings) {
			Section sec = groups.computeIfAbsent(s.group(), g -> new Section(keyPrefix + ":" + g, g));
			sec.add(new SettingRow(s));
		}
		for (Section s : groups.values()) into.add(s);
	}

	@Override
	protected void build() {
		int w = 280;
		Window win = new Window("module:" + module.name(), module.name(), w, (vw - w) / 2, TAB_H + 14);
		win.maxH = vh - TAB_H - 24;
		win.moveTo((vw - w) / 2, TAB_H + 14);
		if (!module.description().isBlank()) win.content.add(new Paragraph(module.description(), true));
		win.content.add(new Controls.Checkbox(module::enabled, b -> module.setEnabled(b)) {
			@Override
			public void render(Gfx g, int mx, int my) {
				g.text("Active", x + 2, y + 2, g.text());
				super.render(g, mx, my);
			}
		});
		addSettings(win.content, "m:" + module.name(), module.settings());

		Section mod = new Section("m:" + module.name() + ":module", "Module");
		mod.add(labeled("Bind", new Controls.KeyBind(module::key, module::setKey)));
		mod.add(checkRow("Toggle on bind release", "Hold the key to enable, release to disable.", module::toggleOnBindRelease, module::setToggleOnBindRelease));
		mod.add(checkRow("Chat feedback", "Show a toast when this module is toggled.", module::chatFeedback, module::setChatFeedback));
		mod.add(checkRow("Visible", "Show in the HUD module list while enabled.", module::visibleInHud, module::setVisibleInHud));
		win.content.add(mod);
		win.content.add(new Controls.Button("Reset all settings", () -> {
			for (Setting<?> s : module.settings()) s.reset();
		}));
		win.content.add(new Controls.Button("Back to modules", () -> Minecraft.getInstance().setScreen(new ModulesScreen())));
		windows.add(win);
	}

	private static Widget labeled(String label, Widget control) {
		return new Widget() {
			@Override
			public int layout(Gfx g, int x, int y, int w) {
				int h = Math.max(Controls.ROW_H, control.layout(g, x + (int) (w * 0.4), y, w - (int) (w * 0.4) - 12));
				this.x = x;
				this.y = y;
				this.w = w;
				this.h = h;
				return h;
			}

			@Override
			public void render(Gfx g, int mx, int my) {
				g.text(label, x + 2, y + 2, g.text());
				control.render(g, mx, my);
			}

			@Override
			public boolean click(int mx, int my, int button) { return control.click(mx, my, button); }

			@Override
			public boolean key(int key, int mods) { return control.key(key, mods); }
		};
	}

	private static Widget checkRow(String label, String tip, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
		Controls.Checkbox cb = new Controls.Checkbox(get::getAsBoolean, set);
		return new Widget() {
			@Override
			public int layout(Gfx g, int x, int y, int w) {
				cb.layout(g, x, y, w - 12);
				this.x = x;
				this.y = y;
				this.w = w;
				this.h = Controls.ROW_H;
				return h;
			}

			@Override
			public void render(Gfx g, int mx, int my) {
				boolean hov = mx >= x && mx < x + w * 0.6 && my >= y && my < y + h;
				g.text(label, x + 2, y + 2, hov ? g.accent() : g.text());
				if (hov) Widget.pendingTooltip = tip;
				cb.render(g, mx, my);
			}

			@Override
			public boolean click(int mx, int my, int button) { return cb.click(mx, my, button); }
		};
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(new ModulesScreen());
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
		Widget f = Widget.focused();
		if (f == null && e.key() == 256) {
			GuiState.save();
			Minecraft.getInstance().setScreen(new ModulesScreen());
			return true;
		}
		return super.keyPressed(e);
	}
}
