package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Detection;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.gui.ColorMath;
import breezeclient.breezeclient.core.gui.Fuzzy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The main screen: one window per category, click a module to toggle it, right click (or the dots) for its settings. */
public class ModulesScreen extends BreezScreen {
	private static final int MIN_W = 112, MAX_W = 156, GAP = 6, BAR_H = 15, ROW = 14;
	/** Bumped when the default layout changes, so positions saved by an older layout are not reused. */
	private static final int LAYOUT_VERSION = 3;

	private final Controls.TextBox search = new Controls.TextBox();
	private final List<ModuleButton> buttons = new ArrayList<>();
	private String lastQuery = "";
	private int shownCount;

	public ModulesScreen() {
		super("Modules");
		search.placeholder = "Search (just type)";
	}

	static final class ModuleButton extends Widget {
		final Module module;
		final Window window;
		boolean shown = true;

		ModuleButton(Module m, Window window) {
			this.module = m;
			this.window = window;
		}

		@Override
		public int layout(Gfx g, int x, int y, int w) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = shown ? ROW : 0;
			return h;
		}

		private boolean onDots(int mx, int my) { return hit(mx, my) && mx >= x + w - 12; }

		@Override
		public void render(Gfx g, int mx, int my) {
			boolean hovered = hit(mx, my);
			double on = Anim.to(module, module.enabled());
			double hov = Anim.to(this, hovered);
			if (on > 0.01) g.rgradient(x, y, w, h, ColorMath.fade(Gfx.shade(g.accent(), 0.14), 0.9 * on), ColorMath.fade(g.accent(), 0.62 * on));
			if (hov > 0.01) g.rrect(x, y, w, h, ((int) (hov * 0x26) << 24) | 0xFFFFFF);
			g.rect(x + 1, y + 2, 2, h - 4, module.detection().color()); // detection badge: green / amber / red
			String key = module.key() >= 0 ? Controls.KeyBind.keyName(module.key()) : "";
			int rightW = hovered ? 12 : key.isEmpty() ? 0 : g.width(key) + 6;
			int nameColor = ColorMath.mix(g.dim(), g.text(), Math.max(on, hov * 0.6)) | 0xFF000000;
			g.text(g.fit(module.name(), w - rightW - 8), x + 6, y + 3, nameColor);
			if (hovered) {
				int c = onDots(mx, my) ? g.text() : g.dim();
				for (int i = 0; i < 3; i++) g.rect(x + w - 7, y + 3 + i * 3, 2, 2, c); // the "more" dots: settings
			} else if (!key.isEmpty()) {
				g.text(key, x + w - g.width(key) - 3, y + 3, module.enabled() ? g.text() : g.dim());
			}
			if (onDots(mx, my)) {
				Widget.pendingTooltip = "Settings for " + module.name() + " (right click works too)";
			} else if (hovered) {
				String d = module.description();
				Widget.pendingTooltip = "[" + module.detection().label() + "] " + (d.isBlank() ? module.name() : d);
			}
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (!shown || !hit(mx, my)) return false;
			if (button == 1 || (button == 0 && onDots(mx, my))) Minecraft.getInstance().setScreen(new ModuleScreen(module));
			else if (button == 0) module.toggle();
			return true;
		}
	}

	@Override
	protected void build() {
		buttons.clear();
		List<Category> cats = new ArrayList<>();
		List<List<Module>> lists = new ArrayList<>();
		for (Category c : Category.values()) {
			List<Module> mods = new ArrayList<>();
			for (Module m : BreezClientClient.MODULES.all()) if (m.category() == c) mods.add(m);
			if (mods.isEmpty()) continue;
			mods.sort(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER));
			cats.add(c);
			lists.add(mods);
		}
		int n = cats.size();
		if (n == 0) return;

		// Positions saved for another screen size (or by an older layout) would overlap here: start from the grid again.
		int[] sig = GuiState.window("layout:modules", 0, 0);
		if (sig[0] != vw || sig[1] != vh || sig[2] != LAYOUT_VERSION) {
			GuiState.WINDOWS.keySet().removeIf(k -> k.startsWith("cat:"));
			sig[0] = vw;
			sig[1] = vh;
			sig[2] = LAYOUT_VERSION;
		}

		// A grid of columns that fills the width; the windows in a column share its height and scroll when they must shrink.
		int top = TAB_H + 6, availH = Math.max(60, vh - BAR_H - 4 - top);
		int cols = Math.max(1, Math.min(n, (vw - GAP) / (MIN_W + GAP)));
		int rows = (n + cols - 1) / cols;
		cols = (n + rows - 1) / rows; // even out the rows: 9 windows on a 7-wide screen become 5 + 4, not 7 + 2
		int winW = Math.max(MIN_W - 20, Math.min(MAX_W, (vw - GAP) / cols - GAP));
		int startX = Math.max(GAP, (vw - (cols * (winW + GAP) - GAP)) / 2);
		for (int col = 0; col < cols; col++) {
			int inCol = 0, sum = 0;
			for (int k = col; k < n; k += cols) {
				inCol++;
				sum += est(lists.get(k).size());
			}
			int room = availH - (inCol - 1) * GAP;
			// too tall for the column: every window shrinks in proportion, but never below two rows; what that costs comes off the tallest
			int minH = Window.HEADER + Window.PAD + ROW * 2;
			int[] heights = new int[inCol];
			int total = 0;
			for (int i = 0, k = col; k < n; k += cols, i++) {
				int want = est(lists.get(k).size());
				heights[i] = sum <= room ? want : Math.max(minH, (int) ((long) want * room / sum));
				total += heights[i];
			}
			while (total > room) {
				int tallest = 0;
				for (int i = 1; i < inCol; i++) if (heights[i] > heights[tallest]) tallest = i;
				if (heights[tallest] <= minH) break; // the screen is simply too small
				heights[tallest]--;
				total--;
			}
			int y = top;
			for (int i = 0, k = col; k < n; k += cols, i++) {
				Category c = cats.get(k);
				List<Module> mods = lists.get(k);
				int want = est(mods.size());
				int h = heights[i];
				String id = "cat:" + c.name();
				int gx = startX + col * (winW + GAP);
				Window w = new Window(id, c.label(), winW, gx, y);
				// a window you dragged keeps its place and gets the room below it; the others follow the grid, whatever was saved
				if (w.movedByHand()) {
					w.maxH = Math.max(minH, Math.min(want, vh - BAR_H - 4 - w.y()));
				} else {
					w.moveTo(gx, y);
					w.maxH = h;
				}
				w.badge = () -> {
					int onCount = 0;
					for (Module m : mods) if (m.enabled()) onCount++;
					return onCount + "/" + mods.size();
				};
				for (Module m : mods) {
					ModuleButton b = new ModuleButton(m, w);
					buttons.add(b);
					w.content.add(b);
				}
				w.content.gap = 1;
				windows.add(w);
				y += h + GAP;
			}
		}
		lastQuery = ""; // re-apply the filter to the new buttons
		shownCount = buttons.size();
	}

	private static int est(int modules) { return Window.HEADER + Window.PAD * 2 + modules * (ROW + 1); }

	private int searchW() { return Math.max(70, Math.min(150, vw / 3)); }

	@Override
	protected void tick(Gfx g) {
		search.layout(g, 5, vh - BAR_H + 2, searchW());
		search.h = 11;
		if (!search.text.equals(lastQuery)) {
			lastQuery = search.text;
			String q = search.text.trim();
			shownCount = 0;
			for (Window w : windows) w.hidden = !q.isEmpty();
			for (ModuleButton b : buttons) {
				b.shown = q.isEmpty() || Fuzzy.score(q, b.module.name()) >= 0 || Fuzzy.score(q, b.module.description()) >= 400;
				if (b.shown) {
					shownCount++;
					b.window.hidden = false; // a category with no match is hidden while searching
					if (!q.isEmpty()) b.window.scroll = 0;
				}
			}
		}
	}

	@Override
	protected void renderOverlay(Gfx g, int mx, int my) {
		int by = vh - BAR_H;
		int bar = g.title();
		g.gradient(0, by, vw, BAR_H, bar, Gfx.shade(bar, 0.1));
		g.rect(0, by, vw, 1, g.theme.accentFaded(0.45));
		search.render(g, mx, my);
		if (shownCount == 0) g.textCentered("No module matches \"" + search.text.trim() + "\"", vw / 2, vh / 2 - 4, g.dim());

		// right side: what the colored strip on every module means, then how many are on
		int enabled = 0;
		for (ModuleButton b : buttons) if (b.module.enabled()) enabled++;
		String count = enabled + " of " + buttons.size() + " on";
		int rx = vw - 6 - g.width(count);
		int limit = 5 + searchW() + 8;
		if (rx > limit) g.text(count, rx, by + 4, g.dim());
		rx -= 10;
		Detection[] ds = Detection.values();
		for (int i = ds.length - 1; i >= 0; i--) {
			int lw = 9 + g.width(ds[i].label());
			if (rx - lw < limit) break; // narrow screen: the legend gives way to the search box
			rx -= lw;
			g.rect(rx, by + 5, 6, 6, ds[i].color());
			g.text(ds[i].label(), rx + 9, by + 4, g.dim());
			rx -= 10;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		double s = scale();
		int mx = (int) (e.x() / s), my = (int) (e.y() / s);
		if (my >= vh - BAR_H) { // the bottom bar is above the windows
			if (!search.click(mx, my, e.button())) Widget.clearFocus();
			return true;
		}
		return super.mouseClicked(e, dbl);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (Widget.focused() == null && e.codepoint() > 32) Widget.setFocus(search); // typing anywhere starts a search
		return super.charTyped(e);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
		if (e.key() == 70 && (e.modifiers() & 2) != 0) { // ctrl+F focuses search
			Widget.setFocus(search);
			return true;
		}
		if (e.key() == 256 && !search.text.isEmpty()) { // escape clears a search before it closes the screen
			search.setText("");
			search.cursor = 0;
			Widget.clearFocus();
			return true;
		}
		return super.keyPressed(e);
	}
}
