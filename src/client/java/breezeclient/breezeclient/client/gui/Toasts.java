package breezeclient.breezeclient.client.gui;

import breezeclient.breezeclient.client.chat.ChatCommands;
import breezeclient.breezeclient.core.gui.ColorMath;
import breezeclient.breezeclient.core.gui.GuiTheme;

import java.util.ArrayList;
import java.util.List;

/** Pop-up notifications. Falls back to chat when toasts are turned off in the GUI tab. */
public final class Toasts {
	private static final class Toast {
		final String title;
		String body;
		final int color;
		long born = System.currentTimeMillis();
		int merged;

		Toast(String title, String body, int color) {
			this.title = title;
			this.body = body;
			this.color = color;
		}
	}

	private static final List<Toast> LIVE = new ArrayList<>();
	/** Every alert, newest last, so a burst merged into one pop-up can still be read back with .alerts. */
	private static final java.util.ArrayDeque<String> HISTORY = new java.util.ArrayDeque<>();
	private static final int HISTORY_MAX = 100;
	private static final int W = 190, H = 30, GAP = 4;

	private Toasts() {}

	public static void show(String title, String body) { show(title, body, 0); }

	/** @param color ARGB accent bar color, 0 = theme accent */
	public static void show(String title, String body, int color) {
		synchronized (HISTORY) {
			if (HISTORY.size() >= HISTORY_MAX) HISTORY.removeFirst();
			HISTORY.addLast(title + ": " + body);
		}
		if (!GuiState.THEME.toastsEnabled.get()) {
			ChatCommands.print(title + ": " + body);
			return;
		}
		synchronized (LIVE) {
			// a burst of alerts with the same title becomes one pop-up: the first one and how many more came in (see .alerts)
			long now = System.currentTimeMillis();
			for (Toast t : LIVE) {
				if (t.title.equals(title) && now - t.born < 1500) {
					t.merged++;
					t.born = now - 400;
					return;
				}
			}
			if (LIVE.size() >= 4) LIVE.remove(0);
			LIVE.add(new Toast(title, body, color));
		}
	}

	/** Recent alerts, oldest first. */
	public static List<String> history() {
		synchronized (HISTORY) { return new ArrayList<>(HISTORY); }
	}

	public static int count() {
		synchronized (LIVE) { return LIVE.size(); }
	}

	public static void render(Gfx g, int screenW, int screenH) {
		GuiTheme t = g.theme;
		long life = (long) (t.toastSeconds.get() * 1000);
		long now = System.currentTimeMillis();
		synchronized (LIVE) {
			LIVE.removeIf(tt -> now - tt.born > life + 400);
			int slot = 0;
			for (Toast toast : LIVE) {
				long age = now - toast.born;
				double slideIn = t.animationSpeed.get() <= 0 ? 1 : Math.min(1, age / (300.0 / t.animationSpeed.get()));
				double slideOut = age > life ? Math.min(1, (age - life) / 400.0) : 0;
				double visible = ease(slideIn) * (1 - ease(slideOut));
				int px, py;
				switch (t.toastPosition.get()) {
					case TOP_RIGHT -> {
						px = screenW - W - 6 + (int) ((1 - visible) * (W + 10));
						py = 6 + slot * (H + GAP);
					}
					case BOTTOM_RIGHT -> {
						px = screenW - W - 6 + (int) ((1 - visible) * (W + 10));
						py = screenH - H - 6 - slot * (H + GAP);
					}
					default -> {
						px = screenW / 2 - W / 2;
						py = 6 + slot * (H + GAP) - (int) ((1 - visible) * (H + 12));
					}
				}
				g.rrect(px, py, W, H, ColorMath.withAlpha(t.background.get(), 235));
				int bar = toast.color == 0 ? g.accent() : toast.color;
				g.rect(px, py + 2, 2, H - 4, bar);
				g.text(g.fit(toast.title, W - 14), px + 8, py + 5, bar);
				g.text(g.fit(toast.merged > 0 ? toast.body + "  (+" + toast.merged + ")" : toast.body, W - 14), px + 8, py + 17, g.text());
				slot++;
			}
		}
	}

	private static double ease(double t) { return 1 - Math.pow(1 - t, 3); }
}
