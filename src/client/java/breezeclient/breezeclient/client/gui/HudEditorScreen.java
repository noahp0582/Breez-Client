package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.client.hud.HudElements;

import java.util.HashMap;
import java.util.Map;

/** Drag HUD elements around. Positions are saved in gui.json. Drawn at Minecraft's own GUI scale so it matches the real HUD. */
public class HudEditorScreen extends BreezScreen {
	private HudElements.Element dragging;
	private int dragDx, dragDy;
	private final Map<String, int[]> sizes = new HashMap<>();

	public HudEditorScreen() { super("HUD"); }

	@Override
	protected void build() {}

	@Override
	protected double scale() { return 1.0; }

	private int[] box(HudElements.Element e) {
		int[] pos = HudElements.position(e, vw);
		int[] size = sizes.getOrDefault(e.id(), new int[] {40, 9});
		int bw = Math.max(size[0], 40), bh = Math.max(size[1], 9);
		int bx = e.rightAnchored() ? pos[0] - bw : pos[0];
		return new int[] {bx, pos[1], bw, bh};
	}

	@Override
	protected void renderExtra(Gfx g, int mx, int my) {
		g.text("Drag elements to move them. Right click an element to reset it. The module list side is in the GUI tab.", 8, TAB_H + 6, g.dim());
		for (HudElements.Element e : HudElements.all()) {
			int[] pos = HudElements.position(e, vw);
			sizes.put(e.id(), e.draw(g, pos[0], pos[1], true));
			int[] b = box(e);
			boolean hov = g.in(mx, my, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4);
			g.outline(b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, hov || e == dragging ? g.accent() : g.dim());
			g.text(e.label(), b[0], b[1] + b[3] + 3, g.dim());
		}
	}

	private HudElements.Element elementAt(int mx, int my) {
		var all = HudElements.all();
		for (int i = all.size() - 1; i >= 0; i--) {
			int[] b = box(all.get(i));
			if (mx >= b[0] - 2 && mx < b[0] + b[2] + 2 && my >= b[1] - 2 && my < b[1] + b[3] + 2) return all.get(i);
		}
		return null;
	}

	@Override
	protected boolean onBackgroundClick(int mx, int my, int button) {
		HudElements.Element e = elementAt(mx, my);
		if (e == null) return false;
		if (button == 1) {
			GuiState.HUD.put(e.id(), e.defaultPos().clone());
			return true;
		}
		int[] pos = HudElements.position(e, vw);
		dragging = e;
		dragDx = mx - pos[0];
		dragDy = my - pos[1];
		return true;
	}

	@Override
	protected boolean onDrag(int mx, int my) {
		if (dragging == null) return false;
		int[] p = GuiState.HUD.computeIfAbsent(dragging.id(), k -> dragging.defaultPos().clone());
		int nx = mx - dragDx, ny = my - dragDy;
		p[0] = dragging.rightAnchored() ? Math.max(0, vw - nx) : Math.max(0, nx);
		p[1] = Math.max(TAB_H, ny);
		return true;
	}

	@Override
	protected void onRelease(int mx, int my) { dragging = null; }
}
