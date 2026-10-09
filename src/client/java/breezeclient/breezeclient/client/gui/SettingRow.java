package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.KeySetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.Setting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.settings.StringSetting;

/** One setting: label on the left, matching control on the right, reset button when changed. */
public class SettingRow extends Widget {
	private final Setting<?> setting;
	private final Widget control;
	private final boolean wide;

	public SettingRow(Setting<?> s) {
		this.setting = s;
		if (s instanceof BoolSetting b) {
			control = new Controls.Checkbox(b);
			wide = false;
		} else if (s instanceof NumberSetting n) {
			control = new Controls.Slider(n);
			wide = false;
		} else if (s instanceof EnumSetting<?> e) {
			control = new Controls.Dropdown(e);
			wide = false;
		} else if (s instanceof ColorSetting c) {
			control = new Controls.ColorPicker(c);
			wide = false;
		} else if (s instanceof KeySetting k) {
			control = new Controls.KeyBind(k);
			wide = false;
		} else if (s instanceof StringSetting str) {
			control = new Controls.StringField(str);
			wide = false;
		} else if (s instanceof StringListSetting l) {
			control = new Controls.ListEditor(l);
			wide = true;
		} else {
			control = new Controls.Label(() -> String.valueOf(s.get()), true);
			wide = false;
		}
	}

	private int labelW() { return wide ? w : (int) (w * 0.4); }

	@Override
	public int layout(Gfx g, int x, int y, int w) {
		this.x = x;
		this.y = y;
		this.w = w;
		if (!setting.visible()) {
			this.h = 0;
			return 0;
		}
		if (wide) {
			int ch = control.layout(g, x + 2, y + Controls.ROW_H, w - 2);
			this.h = Controls.ROW_H + ch;
		} else {
			int cx = x + labelW();
			int ch = control.layout(g, cx, y, w - labelW() - 12);
			this.h = Math.max(Controls.ROW_H, ch);
		}
		return h;
	}

	@Override
	public void render(Gfx g, int mx, int my) {
		boolean labelHover = mx >= x && mx < x + labelW() && my >= y && my < y + Controls.ROW_H;
		g.text(g.fit(setting.name(), labelW() - 4), x + 2, y + 2, labelHover ? g.accent() : g.text());
		if (labelHover && !setting.description().isBlank()) Widget.pendingTooltip = setting.description();
		if (!setting.isDefault()) {
			boolean hov = mx >= x + w - 11 && mx < x + w && my >= y && my < y + Controls.ROW_H;
			g.text("r", x + w - 8, y + 2, hov ? g.accent() : g.dim());
			if (hov) Widget.pendingTooltip = "Reset to default";
		}
		control.render(g, mx, my);
	}

	@Override
	public boolean click(int mx, int my, int button) {
		if (!setting.isDefault() && button == 0 && mx >= x + w - 11 && mx < x + w && my >= y && my < y + Controls.ROW_H) {
			setting.reset();
			return true;
		}
		return control.click(mx, my, button);
	}

	@Override
	public void drag(int mx, int my) { control.drag(mx, my); }

	@Override
	public void release(int mx, int my) { control.release(mx, my); }

	@Override
	public boolean key(int key, int mods) { return control.key(key, mods); }

	@Override
	public boolean typed(int cp) { return control.typed(cp); }
}
