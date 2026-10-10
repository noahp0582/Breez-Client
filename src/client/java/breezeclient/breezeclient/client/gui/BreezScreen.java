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
	public static final int TAB_H = 16;

	protected final List<Window> windows = new ArrayList<>();
	protected final String tab;
	protected int vw, vh;
	private Window dragWindow;
	private boolean built;

	protected BreezScreen(String tab) {
		super(Component.literal("Breez Client"));
		this.tab = tab;
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
		if (theme().dimBackground.get()) g.fill(0, 0, width, height, 0x88000000);
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
		// hover only reaches the top-most window under the mouse
		for (Window w : windows) w.render(g, w.hit(mx, my) && !coveredBy(w, mx, my) ? mx : -1000, w.hit(mx, my) && !coveredBy(w, mx, my) ? my : -1000);
		renderTabs(g, mx, my);
		renderOverlay(g, mx, my);
		if (Widget.pendingTooltip != null) drawTooltip(g, Widget.pendingTooltip, mx, my);
		Toasts.render(g, vw, vh);
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

	private void renderTabs(Gfx g, int mx, int my) {
		g.rect(0, 0, vw, TAB_H, g.title());
		g.rect(0, TAB_H - 1, vw, 1, g.theme.accentFaded(0.9));
		int x = 6;
		for (String t : TABS) {
			int tw = g.width(t) + 14;
			boolean active = t.equals(tab);
			boolean hov = mx >= x && mx < x + tw && my >= 0 && my < TAB_H;
			if (active) g.rect(x, TAB_H - 3, tw, 2, g.accent());
			g.text(t, x + 7, 4, active ? g.text() : (hov ? g.text() : g.dim()));
			x += tw;
		}
		g.text("Breez Client", vw - g.width("Breez Client") - 6, 4, g.accent());
	}

	protected int tabsEnd(Gfx g) {
		int x = 6;
		for (String t : TABS) x += g.width(t) + 14;
		return x;
	}

	private void drawTooltip(Gfx g, String text, int mx, int my) {
		int maxW = 200;
		List<String> lines = Paragraph.wrap(g, text, maxW);
		int w = 0;
		for (String l : lines) w = Math.max(w, g.width(l));
		int h = lines.size() * 10 + 6;
		int tx = Math.min(mx + 10, vw - w - 10), ty = Math.min(my + 12, vh - h - 4);
		g.rrect(tx, ty, w + 8, h, 0xF0101010);
		g.outline(tx, ty, w + 8, h, g.theme.accentFaded(0.8));
		int yy = ty + 3;
		for (String l : lines) {
			g.text(l, tx + 4, yy, g.text());
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
			int x = 6;
			for (String t : TABS) {
				int tw = Minecraft_font().width(t) + 14;
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
