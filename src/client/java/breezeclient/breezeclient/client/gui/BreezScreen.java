package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.core.gui.GuiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Base of every Breez screen: scaling, tab bar, window management, tooltips and toasts. */
public abstract class BreezScreen extends Screen {
	public static final String[] TABS = {"Modules", "Config", "GUI", "Macros", "Profiles", "Waypoints", "HUD"};
	public static final int TAB_H = 18;
	/** When the GUI was last opened from the game (not from another tab), for the open animation. */
	private static long openedAt;
	private static final Object TAB_X = new Object(), TAB_W = new Object();

	protected final List<Window> windows = new ArrayList<>();
	protected final String tab;
	protected int vw, vh;
	private Window dragWindow;
	private boolean built;

	protected BreezScreen(String tab) {
		super(Component.literal("Breez Client"));
		this.tab = tab;
		if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof BreezScreen)) openedAt = System.currentTimeMillis();
	}

	protected GuiTheme theme() { return GuiState.THEME; }

	protected double scale() { return theme().scale.get(); }

	/** (Re)creates windows. Called when the screen opens and whenever the window size changes. */
	protected abstract void build();

	/** Extra drawing under windows (default none). */
	protected void renderExtra(Gfx g, int mx, int my) {}

	/** Called every frame before layout; lets screens react to state (e.g. search text). */
	protected void tick(Gfx g) {}

	@Override
	protected void init() {
		super.init();
		vw = (int) (width / scale());
		vh = (int) (height / scale());
		windows.clear();
		build();
		built = true;
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
		if (!theme().dimBackground.get()) return;
		int a = (int) (opening() * 0x70), b = (int) (opening() * 0xA8);
		g.fillGradient(0, 0, width, height, a << 24, b << 24);
	}

	/** 0 just opened .. 1 settled. */
	private double opening() {
		double speed = theme().animationSpeed.get();
		if (speed <= 0) return 1;
		return Anim.easeOut((System.currentTimeMillis() - openedAt) / (170.0 / speed));
	}

	protected int mouseX(double x) { return (int) Math.floor(x / scale()); }

	@Override
	public void render(GuiGraphics graphics, int rawX, int rawY, float delta) {
		if (!built) return;
		double s = scale();
		int newVw = (int) (width / s), newVh = (int) (height / s);
		if (newVw != vw || newVh != vh) init();
		renderBackground(graphics, rawX, rawY, delta);
		int mx = (int) (rawX / s), my = (int) (rawY / s);
		graphics.pose().pushMatrix();
		graphics.pose().scale((float) s, (float) s);
		Gfx g = new Gfx(graphics, theme());
		Widget.pendingTooltip = null;
		tick(g);
		renderExtra(g, mx, my);
		// front window is last in the list
		for (Window w : windows) w.layout(g);
		float drop = (float) ((1 - opening()) * -7);
		graphics.pose().pushMatrix();
		graphics.pose().translate(0, drop);
		// hover only reaches the top-most window under the mouse
		for (Window w : windows) w.render(g, w.hit(mx, my) && !coveredBy(w, mx, my) ? mx : -1000, w.hit(mx, my) && !coveredBy(w, mx, my) ? my : -1000);
		graphics.pose().popMatrix();
		renderTabs(g, mx, my);
		renderOverlay(g, mx, my);
		if (Widget.pendingTooltip != null) drawTooltip(g, Widget.pendingTooltip, mx, my);
		Toasts.render(g, vw, vh, true);
		graphics.pose().popMatrix();
	}

	private boolean coveredBy(Window w, int mx, int my) {
		boolean after = false;
		for (Window o : windows) {
			if (o == w) {
				after = true;
				continue;
			}
			if (after && o.hit(mx, my)) return true;
		}
		return false;
	}

	/** Drawn above windows but below the tooltip. */
	protected void renderOverlay(Gfx g, int mx, int my) {}

	/** Brand and tab padding shrink on narrow screens so every tab stays reachable. */
	private int[] tabMetrics(net.minecraft.client.gui.Font font) {
		int tabsW = 0;
		for (String t : TABS) tabsW += font.width(t);
		int brand = font.width("Breez") + 12;
		int pad = 14;
		if (8 + brand + tabsW + TABS.length * pad > vw) brand = 0;
		while (pad > 6 && 8 + brand + tabsW + TABS.length * pad > vw) pad--;
		return new int[] {6 + brand, pad};
	}

	private void renderTabs(Gfx g, int mx, int my) {
		int bar = g.title() | 0xFF000000; // opaque: HUD elements being edited must not show through the tabs
		g.gradient(0, 0, vw, TAB_H, Gfx.shade(bar, 0.12), bar);
		g.rect(0, TAB_H - 1, vw, 1, g.theme.accentFaded(0.45));
		int[] m = tabMetrics(g.font);
		if (m[0] > 6) {
			g.text("Breez", 7, 5, g.accent());
			g.rect(m[0] - 4, 4, 1, TAB_H - 9, 0x30FFFFFF);
		}
		int x = m[0];
		for (String t : TABS) {
			int tw = g.width(t) + m[1];
			boolean active = t.equals(tab);
			boolean hov = mx >= x && mx < x + tw && my >= 0 && my < TAB_H;
			double glow = Anim.to(t, hov && !active);
			if (glow > 0.01) g.rrect(x + 1, 2, tw - 2, TAB_H - 5, ((int) (glow * 0x24) << 24) | 0xFFFFFF);
			if (active) {
				// the marker slides from the tab that was open before
				int ix = (int) Math.round(Anim.to(TAB_X, x + 2)), iw = (int) Math.round(Anim.to(TAB_W, tw - 4));
				g.rrect(ix, 2, iw, TAB_H - 5, g.theme.accentFaded(0.22));
				g.rect(ix, TAB_H - 2, iw, 2, g.accent());
			}
			g.text(t, x + m[1] / 2, 5, active || hov ? g.text() : g.dim());
			x += tw;
		}
	}

	protected int tabsEnd(Gfx g) {
		int[] m = tabMetrics(g.font);
		int x = m[0];
		for (String t : TABS) x += g.width(t) + m[1];
		return x;
	}

	private void drawTooltip(Gfx g, String text, int mx, int my) {
		int maxW = 200;
		List<String> lines = Paragraph.wrap(g, text, maxW);
		int w = 0;
		for (String l : lines) w = Math.max(w, g.width(l));
		int h = lines.size() * 10 + 6;
		int tx = Math.max(2, Math.min(mx + 10, vw - w - 12)), ty = Math.min(my + 12, vh - h - 4);
		if (ty < my + 4 && my - h - 4 >= 0) ty = my - h - 4; // no room below the pointer: go above it instead of covering it
		g.shadow(tx, ty, w + 10, h);
		g.rrect(tx, ty, w + 10, h, 0xF4101014);
		g.rect(tx, ty + 2, 2, h - 4, g.accent());
		int yy = ty + 3;
		for (String l : lines) {
			g.text(l, tx + 6, yy, g.text());
			yy += 10;
		}
	}

	// input ---------------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		double s = scale();
		int mx = (int) (e.x() / s), my = (int) (e.y() / s);
		int button = e.button();
		Widget f = Widget.focused();
		if (f != null && !f.hit(mx, my)) Widget.clearFocus();
		if (my < TAB_H && button == 0) {
			int[] m = tabMetrics(Minecraft_font());
			int x = m[0];
			for (String t : TABS) {
				int tw = Minecraft_font().width(t) + m[1];
				if (mx >= x && mx < x + tw) {
					if (!t.equals(tab)) Tabs.open(t);
					return true;
				}
				x += tw;
			}
			return onTabBarClick(mx, my, button);
		}
		for (int i = windows.size() - 1; i >= 0; i--) {
			Window w = windows.get(i);
			if (w.click(mx, my, button)) {
				if (i != windows.size() - 1) {
					windows.remove(i);
					windows.add(w);
				}
				dragWindow = w;
				return true;
			}
		}
		return onBackgroundClick(mx, my, button) || super.mouseClicked(e, dbl);
	}

	private static net.minecraft.client.gui.Font Minecraft_font() { return net.minecraft.client.Minecraft.getInstance().font; }

	protected boolean onTabBarClick(int mx, int my, int button) { return false; }

	protected boolean onBackgroundClick(int mx, int my, int button) { return false; }

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		double s = scale();
		if (dragWindow != null) {
			dragWindow.drag((int) (e.x() / s), (int) (e.y() / s), vw, vh);
			return true;
		}
		return onDrag((int) (e.x() / s), (int) (e.y() / s)) || super.mouseDragged(e, dx, dy);
	}

	protected boolean onDrag(int mx, int my) { return false; }

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		double s = scale();
		int mx = (int) (e.x() / s), my = (int) (e.y() / s);
		if (dragWindow != null) dragWindow.release(mx, my);
		dragWindow = null;
		onRelease(mx, my);
		return super.mouseReleased(e);
	}

	protected void onRelease(int mx, int my) {}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		double s = scale();
		int mx = (int) (x / s), my = (int) (y / s);
		for (int i = windows.size() - 1; i >= 0; i--) if (windows.get(i).scroll(mx, my, sy)) return true;
		return super.mouseScrolled(x, y, sx, sy);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		Widget f = Widget.focused();
		if (f != null && f.key(e.key(), e.modifiers())) return true;
		if (breezeclient.breezeclient.client.input.KeyPoller.isGuiKey(e) && f == null) { // the GUI key closes it again
			onClose();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		Widget f = Widget.focused();
		if (f != null && f.typed(e.codepoint())) return true;
		return super.charTyped(e);
	}

	@Override
	public void onClose() {
		Widget.clearFocus();
		GuiState.lastTab = tab;
		GuiState.save();
		if (BreezClientClient.config != null) BreezClientClient.config.save();
		super.onClose();
	}
}
