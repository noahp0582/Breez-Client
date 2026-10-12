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
	/** Small dim text at the right of the title bar (e.g. "3/27"); null for none. */
	public java.util.function.Supplier<String> badge;
	/** Not drawn and not clickable (a category with no search match). */
	public boolean hidden;

	private final int[] state; // x, y, collapsed, moved by hand
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
	/** True once you have dragged it; until then a screen may place it wherever its layout wants. */
	public boolean movedByHand() { return state[3] != 0; }
	public boolean collapsed() { return state[2] != 0; }
	public void setCollapsed(boolean c) { state[2] = c ? 1 : 0; }
	/** 0 collapsed .. 1 open, eased so collapsing slides instead of snapping. */
	private double open = -1;

	public int height() { return HEADER + (int) Math.round((viewH + PAD) * Math.max(0, open)); }

	public void layout(Gfx g) {
		if (hidden) return;
		open = open < 0 ? (collapsed() ? 0 : 1) : Anim.to(this, collapsed() ? 0 : 1);
		if (open <= 0) return; // fully collapsed: the last size is kept for the next opening
		contentH = content.layout(g, x() + PAD, y() + HEADER + PAD - (int) scroll, w - PAD * 2 - 4);
		int avail = Math.max(14, maxH - HEADER - PAD);
		viewH = Math.min(contentH + PAD, avail);
		scroll = ScrollMath.clamp(scroll, contentH + PAD, viewH);
		Widget r = Widget.reveal;
		if (r != null && !collapsed() && r.h > 0 && r.x >= x() && r.x < x() + w) {
			int top = y() + HEADER, bottom = top + viewH;
			if (r.y + r.h > bottom) scroll += r.h <= viewH ? r.y + r.h - bottom : r.y - top;
			else if (r.y < top) scroll -= top - r.y;
			scroll = ScrollMath.clamp(scroll, contentH + PAD, viewH);
			Widget.reveal = null;
		}
	}

	public boolean hit(int mx, int my) { return !hidden && mx >= x() && mx < x() + w && my >= y() && my < y() + height(); }

	private boolean inContent(int mx, int my) {
		return !collapsed() && mx >= x() && mx < x() + w && my >= y() + HEADER && my < y() + HEADER + viewH + PAD;
	}

	public void render(Gfx g, int mx, int my) {
		if (hidden) return;
		int x = x(), y = y();
		int h = height();
		boolean headHover = mx >= x && mx < x + w && my >= y && my < y + HEADER;
		g.shadow(x, y, w, h);
		g.rrect(x, y, w, h, g.panel());
		int head = g.title();
		g.rgradient(x, y, w, HEADER, Gfx.shade(head, headHover ? 0.16 : 0.09), head);
		g.rect(x, y + HEADER - 1, w, 1, g.theme.accentFaded(0.35));
		g.rect(x, y + HEADER - 1, Math.max(8, (int) (w * (0.3 + 0.7 * open))), 1, g.accent());
		String b = badge == null ? "" : badge.get();
		int bw = b.isEmpty() ? 0 : g.width(b) + 4;
		g.text(g.fit(title, w - 22 - bw), x + 6, y + 4, titleColorOverride != 0 ? titleColorOverride : g.text());
		if (!b.isEmpty()) g.text(b, x + w - 14 - g.width(b), y + 4, g.dim());
		g.text(collapsed() ? "+" : "-", x + w - 10, y + 4, headHover ? g.text() : g.dim());
		if (open <= 0) return;
		g.scissor(x, y + HEADER, w, h - HEADER);
		content.render(g, inContent(mx, my) ? mx : -1000, inContent(mx, my) ? my : -1000);
		g.endScissor();
		if (contentH + PAD > viewH && !collapsed()) {
			int track = viewH;
			double thumb = ScrollMath.thumbHeight(contentH + PAD, viewH, track, 12);
			double off = ScrollMath.thumbOffset(scroll, contentH + PAD, viewH, track, thumb);
			g.rect(x + w - 3, y + HEADER + 1, 2, track - 2, 0x18FFFFFF);
			g.rect(x + w - 3, y + HEADER + (int) off, 2, (int) thumb, g.theme.accentFaded(scrollDrag ? 1.0 : 0.75));
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
			state[3] = 1;
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
