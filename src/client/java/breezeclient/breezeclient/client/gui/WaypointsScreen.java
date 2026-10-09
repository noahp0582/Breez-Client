package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.client.world.Waypoints;
import net.minecraft.client.Minecraft;

/** Saved waypoints for the current server. */
public class WaypointsScreen extends BreezScreen {
	private final Controls.TextBox name = new Controls.TextBox();

	public WaypointsScreen() {
		super("Waypoints");
		name.placeholder = "name for a new waypoint at your position";
	}

	private static final class Row extends Widget {
		private final int index;
		private final Waypoints.Waypoint wp;
		private final Runnable deleted;

		Row(int index, Waypoints.Waypoint wp, Runnable deleted) {
			this.index = index;
			this.wp = wp;
			this.deleted = deleted;
		}

		@Override
		public void render(Gfx g, int mx, int my) {
			boolean hov = hit(mx, my);
			if (hov) g.rrect(x, y, w, h, g.hover());
			g.text(g.fit(wp.name(), 90), x + 3, y + 2, g.text());
			g.text(wp.x() + " " + wp.y() + " " + wp.z(), x + 96, y + 2, g.dim());
			boolean copyHov = mx >= x + w - 54 && mx < x + w - 28 && my >= y && my < y + h;
			boolean delHov = mx >= x + w - 26 && mx < x + w - 2 && my >= y && my < y + h;
			g.text("copy", x + w - 52, y + 2, copyHov ? g.accent() : g.dim());
			g.text("del", x + w - 24, y + 2, delHov ? 0xFFFF5555 : g.dim());
		}

		@Override
		public boolean click(int mx, int my, int button) {
			if (!hit(mx, my) || button != 0) return false;
			if (mx >= x + w - 54 && mx < x + w - 28) {
				Minecraft.getInstance().keyboardHandler.setClipboard(wp.x() + " " + wp.y() + " " + wp.z());
				Toasts.show("Waypoints", "Copied " + wp.name());
			} else if (mx >= x + w - 26) {
				Waypoints.remove(index);
				deleted.run();
			}
			return true;
		}
	}

	@Override
	protected void build() {
		int w = 340;
		Window win = new Window("waypoints", "Waypoints", w, 14, TAB_H + 10);
		win.maxH = vh - TAB_H - 20;
		var list = Waypoints.forCurrentServer();
		win.content.add(new Paragraph(list.isEmpty() ? "No waypoints for this server yet. Finds from SpawnerESP, AmethystDetector and SusChunks can add them automatically."
			: list.size() + " waypoints on this server.", true));
		for (int i = 0; i < list.size(); i++) win.content.add(new Row(i, list.get(i), this::rebuild));
		win.content.add(name);
		win.content.add(new Controls.Button("Add at my position", () -> {
			var p = Minecraft.getInstance().player;
			if (p == null) {
				Toasts.show("Waypoints", "Join a world first", 0xFFFF5555);
				return;
			}
			String n = name.text.isBlank() ? "Waypoint" : name.text.trim();
			Toasts.show("Waypoints", Waypoints.add(n, p.blockPosition()) ? "Saved " + n : "Already saved here");
			name.setText("");
			rebuild();
		}));
		win.content.add(new Controls.Button("Clear all on this server", () -> {
			Waypoints.clearCurrent();
			rebuild();
		}));
		windows.add(win);
	}

	private void rebuild() {
		windows.clear();
		build();
	}
}
