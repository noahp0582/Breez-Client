package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.core.gui.ScrollMath;

/** A draggable, collapsible, scrollable panel with a title bar. Position and collapsed state persist in gui.json. */
public class Window {
	public static final int HEADER = 15, PAD = 4;

	public final String id;
	public String title;
	public int w;
	public int maxH = 400;
	public final Container content = new Container();
	public double scroll;
	public int titleColorOverride;

	private final int[] state; // x, y, collapsed
	private boolean dragging;
	private int dragDx, dragDy;
	private boolean scrollDrag;
	private int viewH, contentH;

	public Window(String id, String title, int w, int defX, int defY) {
		this.id = id;
		this.title = title;
		this.w = w;
		this.state = GuiState.window(id, defX, defY);
	}

	public int x() { return state[0]; }
	public int y() { return state[1]; }
	public void moveTo(int x, int y) {
		state[0] = x;
		state[1] = y;
	}
	public boolean collapsed() { return state[2] != 0; }
	public void setCollapsed(boolean c) { state[2] = c ? 1 : 0; }
	public int height() { return HEADER + (collapsed() ? 0 : viewH + PAD); }

	public void layout(Gfx g) {
		contentH = collapsed() ? 0 : content.layout(g, x() + PAD, y() + HEADER + PAD - (int) scroll, w - PAD * 2 - 4);
		int avail = Math.max(40, maxH - HEADER - PAD * 2);
		viewH = Math.min(contentH + PAD, avail);
		scroll = ScrollMath.clamp(scroll, contentH + PAD, viewH);
	}

	public boolean hit(int mx, int my) { return mx >= x() && mx < x() + w && my >= y() && my < y() + height(); }

	private boolean inContent(int mx, int my) {
		return !collapsed() && mx >= x() && mx < x() + w && my >= y() + HEADER && my < y() + HEADER + viewH + PAD;
	}

	public void render(Gfx g, int mx, int my) {
		int x = x(), y = y();
		g.rrect(x, y, w, height(), g.panel());
		g.rrect(x, y, w, HEADER, g.title());
		g.rect(x + 3, y + HEADER - 1, w - 6, 1, g.accent());
		g.text(g.fit(title, w - 24), x + 5, y + 4, titleColorOverride != 0 ? titleColorOverride : g.text());
		g.text(collapsed() ? "+" : "-", x + w - 10, y + 4, g.dim());
		if (collapsed()) return;
		g.scissor(x, y + HEADER, w, viewH + PAD);
		content.render(g, inContent(mx, my) ? mx : -1000, inContent(mx, my) ? my : -1000);
		g.endScissor();
		if (contentH + PAD > viewH) {
			int track = viewH;
			double thumb = ScrollMath.thumbHeight(contentH + PAD, viewH, track, 12);
			double off = ScrollMath.thumbOffset(scroll, contentH + PAD, viewH, track, thumb);
			g.rect(x + w - 4, y + HEADER + (int) off, 3, (int) thumb, g.theme.accentFaded(0.7));
		}
	}

	/** @return true when the click landed on this window. */
	public boolean click(int mx, int my, int button) {
		if (!hit(mx, my)) return false;
		if (my < y() + HEADER) {
			if (button == 0) {
				dragging = true;
				dragDx = mx - x();
				dragDy = my - y();
			} else if (button == 1) {
				setCollapsed(!collapsed());
			}
			return true;
		}
		if (mx >= x() + w - 5 && contentH + PAD > viewH && button == 0) {
			scrollDrag = true;
			return true;
		}
		if (inContent(mx, my)) content.click(mx, my, button);
		return true;
	}

	public void drag(int mx, int my, int screenW, int screenH) {
		if (dragging) {
			moveTo(Math.max(-w + 30, Math.min(screenW - 30, mx - dragDx)), Math.max(0, Math.min(screenH - HEADER, my - dragDy)));
		} else if (scrollDrag) {
			double thumb = ScrollMath.thumbHeight(contentH + PAD, viewH, viewH, 12);
			scroll = ScrollMath.scrollForThumb(my - y() - HEADER - thumb / 2, contentH + PAD, viewH, viewH, thumb);
		} else {
			content.drag(mx, my);
		}
	}

	public void release(int mx, int my) {
		dragging = false;
		scrollDrag = false;
		content.release(mx, my);
	}

	public boolean scroll(int mx, int my, double amount) {
		if (!hit(mx, my) || collapsed()) return false;
		if (content.scroll(mx, my, amount)) return true;
		if (contentH + PAD > viewH) {
			scroll = ScrollMath.clamp(scroll - amount * 14, contentH + PAD, viewH);
			return true;
		}
		return false;
	}
}
