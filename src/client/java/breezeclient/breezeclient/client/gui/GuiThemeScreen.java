package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.core.gui.GuiTheme;

public class GuiThemeScreen extends SettingsScreen {
	/** name, accent, background, text */
	private static final Object[][] PRESETS = {
		{"Breez", 0xFF913DE2, 0xFF141414, 0xFFFFFFFF},
		{"Ocean", 0xFF2F9BFF, 0xFF0E141C, 0xFFEAF4FF},
		{"Mint", 0xFF2ECC8F, 0xFF0F1512, 0xFFEFFFF6},
		{"Sunset", 0xFFFF7A45, 0xFF181210, 0xFFFFF3EA},
		{"Rose", 0xFFFF4F8B, 0xFF171014, 0xFFFFEEF4},
		{"Mono", 0xFFB9BEC9, 0xFF101012, 0xFFF2F2F2},
	};

	public GuiThemeScreen() {
		super("GUI", "gui", "GUI", "Change how the GUI looks. Changes apply instantly and are saved when you close the screen.",
			GuiState.THEME.settings());
	}

	/** A row of color swatches; clicking one applies that preset's accent, background and text colors. */
	private static final class PresetRow extends Widget {
		private static final int SW = 40;

		@Override
		protected int height(Gfx g, int w) { return 24; }

		private int cols() { return Math.max(1, Math.min(PRESETS.length, w / (SW + 3))); }

		@Override
		public void render(Gfx g, int mx, int my) {
			GuiTheme t = g.theme;
			int n = cols();
			for (int i = 0; i < n; i++) {
				int sx = x + i * (SW + 3);
				int accent = (int) PRESETS[i][1], bg = (int) PRESETS[i][2];
				boolean hov = g.in(mx, my, sx, y, SW, h);
				boolean current = t.accent.get() == accent && (t.background.get() | 0xFF000000) == bg;
				g.rrect(sx, y, SW, h, bg);
				g.rrect(sx + 2, y + 2, SW - 4, 6, accent);
				g.textCentered(g.fit((String) PRESETS[i][0], SW - 4), sx + SW / 2, y + 12, (int) PRESETS[i][3]);
				if (current || hov) g.outline(sx, y, SW, h, current ? accent : 0x66FFFFFF);
				if (hov) Widget.pendingTooltip = "Apply the " + PRESETS[i][0] + " colors";
			}
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (button != 0 || !hit(mx, my)) return false;
			int i = (mx - x) / (SW + 3);
			if (i < 0 || i >= cols() || mx - x - i * (SW + 3) >= SW) return false;
			GuiTheme t = GuiState.THEME;
			t.accent.set((int) PRESETS[i][1]);
			t.background.set((int) PRESETS[i][2]);
			t.textColor.set((int) PRESETS[i][3]);
			return true;
		}
	}

	@Override
	protected void build() {
		super.build();
		if (windows.isEmpty()) return;
		Container c = windows.get(0).content;
		Section presets = new Section("gui:presets", "Presets");
		presets.add(new PresetRow());
		c.kids.add(Math.min(1, c.kids.size()), presets); // right under the blurb
		c.add(new Controls.Button("Reset window layout", () -> {
			GuiState.WINDOWS.clear();
			Toasts.show("GUI", "Window layout reset");
			Tabs.open("GUI");
		}));
	}
}
