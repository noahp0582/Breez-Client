package breezeclient.breezeclient.client.gui;

/** A collapsible group of widgets with a clickable header. Collapsed state is saved in gui.json. */
public class Section extends Container {
	private final String title;
	private final int[] state;

	public Section(String stateKey, String title) {
		this.title = title;
		this.state = GuiState.window("sec:" + stateKey, 0, 0);
	}

	private boolean collapsed() { return state[2] != 0; }

	@Override
	public int layout(Gfx g, int x, int y, int w) {
		this.x = x;
		this.y = y;
		this.w = w;
		int cy = y + Controls.ROW_H + 2;
		if (!collapsed()) {
			for (Widget k : kids) {
				int kh = k.layout(g, x + 4, cy, w - 4);
				if (kh > 0) cy += kh + gap;
			}
		} else {
			for (Widget k : kids) k.h = 0;
		}
		this.h = cy - y - (collapsed() ? 2 : 0);
		return h;
	}

	@Override
	public void render(Gfx g, int mx, int my) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + Controls.ROW_H;
		g.rrect(x, y, w, Controls.ROW_H, hov ? g.theme.accentFaded(0.45) : g.theme.accentFaded(0.28));
		g.text((collapsed() ? "+ " : "- ") + title, x + 4, y + 2, g.text());
		if (!collapsed()) super.render(g, mx, my);
	}

	@Override
	public boolean click(int mx, int my, int button) {
		if (mx >= x && mx < x + w && my >= y && my < y + Controls.ROW_H) {
			if (button == 0) state[2] = collapsed() ? 0 : 1;
			return true;
		}
		return !collapsed() && super.click(mx, my, button);
	}
}
