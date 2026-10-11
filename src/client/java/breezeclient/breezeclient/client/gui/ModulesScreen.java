package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Detection;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.gui.Fuzzy;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The main screen: one window per category, click a module to toggle it, right click for its settings. */
public class ModulesScreen extends BreezScreen {
	private static final int WIN_W = 112;
	private final Controls.TextBox search = new Controls.TextBox();
	private final List<ModuleButton> buttons = new ArrayList<>();
	private String lastQuery = "";

	public ModulesScreen() {
		super("Modules");
		search.placeholder = "Search modules...";
	}

	static final class ModuleButton extends Widget {
		final Module module;
		boolean shown = true;

		ModuleButton(Module m) { this.module = m; }

		@Override
		public int layout(Gfx g, int x, int y, int w) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = shown ? Controls.ROW_H : 0;
			return h;
		}

		@Override
		public void render(Gfx g, int mx, int my) {
			boolean hov = hit(mx, my);
			if (module.enabled()) g.rrect(x, y, w, h, g.theme.accentFaded(hov ? 0.9 : 0.7));
			else if (hov) g.rrect(x, y, w, h, g.hover());
			String key = module.key() >= 0 ? Controls.KeyBind.keyName(module.key()) : "";
			int keyW = key.isEmpty() ? 0 : g.width(key) + 6;
			g.rect(x, y + 1, 2, h - 2, module.detection().color()); // detection badge: green / amber / red
			g.text(g.fit(module.name(), w - keyW - 8), x + 6, y + 2, module.enabled() ? g.text() : g.dim() | 0xFF000000);
			if (!key.isEmpty()) g.text(key, x + w - g.width(key) - 3, y + 2, module.enabled() ? g.text() : g.dim());
			if (hov) {
				String d = module.description();
				String status = "[" + module.detection().label() + "] ";
				Widget.pendingTooltip = status + (d.isBlank() ? module.name() : d);
			}
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (!shown || !hit(mx, my)) return false;
			if (button == 0) module.toggle();
			else if (button == 1) Minecraft.getInstance().setScreen(new ModuleScreen(module));
			return true;
		}
	}

	@Override
	protected void build() {
		buttons.clear();
		// Default layout: columns; each window goes under the shortest column and scrolls when it would run off the screen.
		int colW = WIN_W + 8;
		int cols = Math.max(1, (vw - 8) / colW);
		int[] colY = new int[cols];
		java.util.Arrays.fill(colY, TAB_H + 8);
		for (Category c : Category.values()) {
			List<Module> mods = new ArrayList<>();
			for (Module m : BreezClientClient.MODULES.all()) if (m.category() == c) mods.add(m);
			if (mods.isEmpty()) continue;
			mods.sort(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER));
			int est = Window.HEADER + Window.PAD * 2 + mods.size() * 14;
			int col = 0;
			for (int i = 1; i < cols; i++) if (colY[i] < colY[col]) col = i;
			int x = 8 + col * colW, y = colY[col];
			int room = Math.max(90, vh - 8 - y);
			Window w = new Window("cat:" + c.name(), c.label(), WIN_W, x, y);
			w.maxH = Math.min(est, Math.max(room, 90));
			if (GuiState.WINDOWS.containsKey("cat:" + c.name()) && GuiState.WINDOWS.get("cat:" + c.name())[1] != y) w.maxH = Math.max(120, vh - 40);
			for (Module m : mods) {
				ModuleButton b = new ModuleButton(m);
				buttons.add(b);
				w.content.add(b);
			}
			w.content.gap = 1;
			windows.add(w);
			colY[col] += w.maxH + 8;
		}
	}

	@Override
	protected void tick(Gfx g) {
		search.layout(g, tabsEnd(g) + 6, 2, Math.max(60, vw - tabsEnd(g) - 120));
		search.h = 12;
		if (!search.text.equals(lastQuery)) {
			lastQuery = search.text;
			for (ModuleButton b : buttons) {
				String q = search.text.trim();
				b.shown = q.isEmpty() || Fuzzy.score(q, b.module.name()) >= 0 || Fuzzy.score(q, b.module.description()) >= 400;
			}
		}
	}

	@Override
	protected void renderOverlay(Gfx g, int mx, int my) {
		search.render(g, mx, my);
		// legend for the colored strip on every module
		int lx = Math.max(8, (vw - 230) / 2), ly = vh - 11;
		g.rect(lx - 6, ly - 2, 242, 12, 0xC0101018); // backing, so the labels stay readable over module windows
		for (Detection d : Detection.values()) {
			g.rect(lx, ly + 1, 6, 6, d.color());
			g.text(d.label(), lx + 9, ly, g.dim());
			lx += 9 + g.width(d.label()) + 12;
		}
	}

	@Override
	protected boolean onTabBarClick(int mx, int my, int button) { return search.click(mx, my, button); }

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
		if (e.key() == 70 && (e.modifiers() & 2) != 0) { // ctrl+F focuses search
			Widget.setFocus(search);
			return true;
		}
		return super.keyPressed(e);
	}
}
