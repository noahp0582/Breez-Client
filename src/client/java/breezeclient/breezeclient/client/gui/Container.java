package breezeclient.breezeclient.client.gui;

import java.util.ArrayList;
import java.util.List;

/** Stacks child widgets vertically and forwards input to them. */
public class Container extends Widget {
	public final List<Widget> kids = new ArrayList<>();
	public int gap = 2;
	private Widget active;

	public Container add(Widget w) {
		kids.add(w);
		return this;
	}

	@Override
	public int layout(Gfx g, int x, int y, int w) {
		this.x = x;
		this.y = y;
		this.w = w;
		int cy = y;
		for (Widget k : kids) {
			int kh = k.layout(g, x, cy, w);
			if (kh > 0) cy += kh + gap;
		}
		this.h = Math.max(0, cy - y - (kids.isEmpty() ? 0 : gap));
		return h;
	}

	@Override
	public void render(Gfx g, int mx, int my) {
		for (Widget k : kids) if (k.h > 0) k.render(g, mx, my);
	}

	@Override
	public boolean click(int mx, int my, int button) {
		active = null;
		boolean used = false;
		for (Widget k : kids) {
			if (k.h <= 0) continue;
			if (k.click(mx, my, button)) {
				active = k;
				used = true;
				break;
			}
		}
		return used;
	}

	@Override
	public void drag(int mx, int my) { if (active != null) active.drag(mx, my); }

	@Override
	public void release(int mx, int my) {
		if (active != null) active.release(mx, my);
		active = null;
	}

	@Override
	public boolean key(int key, int mods) {
		for (Widget k : kids) if (k.h > 0 && k.key(key, mods)) return true;
		return false;
	}

	@Override
	public boolean typed(int cp) {
		for (Widget k : kids) if (k.h > 0 && k.typed(cp)) return true;
		return false;
	}

	@Override
	public boolean scroll(int mx, int my, double amount) {
		for (Widget k : kids) if (k.h > 0 && k.hit(mx, my) && k.scroll(mx, my, amount)) return true;
		return false;
	}

	@Override
	public void closed() { for (Widget k : kids) k.closed(); }
}
