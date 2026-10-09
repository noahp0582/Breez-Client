package breezeclient.breezeclient.client.gui;

/** Base of every GUI element. Parents call {@link #layout} each frame, then render / forward input. */
public abstract class Widget {
	public int x, y, w, h;

	/** Currently focused text-like widget (receives key presses and typed characters). */
	private static Widget focus;
	/** Tooltip requested during this frame, drawn last by the screen. */
	public static String pendingTooltip;

	public static Widget focused() { return focus; }

	public static void setFocus(Widget wd) { focus = wd; }

	public static void clearFocus() { focus = null; }

	/** Positions the widget and returns the height it needs. */
	public int layout(Gfx g, int x, int y, int w) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = height(g, w);
		return h;
	}

	protected int height(Gfx g, int w) { return 13; }

	public abstract void render(Gfx g, int mx, int my);

	public boolean click(int mx, int my, int button) { return false; }

	public void drag(int mx, int my) {}

	public void release(int mx, int my) {}

	public boolean scroll(int mx, int my, double amount) { return false; }

	public boolean key(int key, int mods) { return false; }

	public boolean typed(int codepoint) { return false; }

	public boolean hit(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

	/** Called when the screen closes. */
	public void closed() {}
}
