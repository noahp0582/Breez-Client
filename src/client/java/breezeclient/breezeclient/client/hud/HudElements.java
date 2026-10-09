package breezeclient.breezeclient.client.hud;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.gui.Gfx;
import breezeclient.breezeclient.client.gui.GuiState;
import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.gui.GuiTheme;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The draggable HUD pieces. Each draws itself and reports its size, so the HUD editor can show and move them. */
public final class HudElements {
	public interface Element {
		String id();

		String label();

		/** Default offset from the anchor corner. */
		int[] defaultPos();

		/** True when x is measured from the right edge of the screen. */
		boolean rightAnchored();

		/** Draws at (x, y) and returns {width, height}; for right-anchored elements x is the right edge. {@code preview} = draw sample content even when empty. */
		int[] draw(Gfx g, int x, int y, boolean preview);
	}

	private static final List<Element> ALL = new ArrayList<>();

	static {
		ALL.add(new Element() {
			public String id() { return "watermark"; }
			public String label() { return "Watermark"; }
			public int[] defaultPos() { return new int[] {4, 4}; }
			public boolean rightAnchored() { return false; }
			public int[] draw(Gfx g, int x, int y, boolean preview) {
				String s = "Breez Client";
				text(g, s, x, y, g.accent());
				return new int[] {g.width(s), 9};
			}
		});
		ALL.add(new Element() {
			public String id() { return "modulelist"; }
			public String label() { return "Module list"; }
			public int[] defaultPos() { return new int[] {4, 4}; }
			public boolean rightAnchored() { return GuiState.THEME.moduleListSide.get() == GuiTheme.Side.RIGHT; }
			public int[] draw(Gfx g, int x, int y, boolean preview) {
				List<String> lines = new ArrayList<>();
				for (Module m : BreezClientClient.MODULES.all()) {
					if (!m.enabled() || !m.visibleInHud()) continue;
					String info = m.info();
					lines.add(info.isEmpty() ? m.name() : m.name() + " " + info);
				}
				if (lines.isEmpty() && preview) lines.add("ModuleName 12");
				lines.sort(Comparator.comparingInt((String s) -> g.width(s)).reversed());
				int w = 0, yy = y;
				for (String s : lines) w = Math.max(w, g.width(s));
				boolean right = rightAnchored();
				for (String s : lines) {
					text(g, s, right ? x - g.width(s) : x, yy, g.text());
					yy += 10;
				}
				return new int[] {w, Math.max(9, yy - y)};
			}
		});
		ALL.add(new Element() {
			public String id() { return "coords"; }
			public String label() { return "Coordinates"; }
			public int[] defaultPos() { return new int[] {4, 16}; }
			public boolean rightAnchored() { return false; }
			public int[] draw(Gfx g, int x, int y, boolean preview) {
				var p = Minecraft.getInstance().player;
				String s;
				if (p == null) s = "XYZ 0 64 0";
				else {
					s = String.format("XYZ %d %d %d", p.getBlockX(), p.getBlockY(), p.getBlockZ());
					var dim = Minecraft.getInstance().level.dimension();
					if (dim == net.minecraft.world.level.Level.OVERWORLD) s += String.format("  (Nether %d %d)", p.getBlockX() / 8, p.getBlockZ() / 8);
					else if (dim == net.minecraft.world.level.Level.NETHER) s += String.format("  (Overworld %d %d)", p.getBlockX() * 8, p.getBlockZ() * 8);
				}
				text(g, s, x, y, g.text());
				return new int[] {g.width(s), 9};
			}
		});
		ALL.add(new Element() {
			public String id() { return "staff"; }
			public String label() { return "Online staff"; }
			public int[] defaultPos() { return new int[] {4, 28}; }
			public boolean rightAnchored() { return false; }
			public int[] draw(Gfx g, int x, int y, boolean preview) {
				List<String> staff = preview ? List.of("ExampleStaff") : StaffTracker.onlineStaff();
				if (staff.isEmpty()) return new int[] {0, 0};
				text(g, "Staff online", x, y, 0xFFFF5555);
				int w = g.width("Staff online"), yy = y + 10;
				for (String n : staff) {
					text(g, n, x + 4, yy, g.text());
					w = Math.max(w, g.width(n) + 4);
					yy += 10;
				}
				return new int[] {w, yy - y};
			}
		});
	}

	private HudElements() {}

	public static List<Element> all() { return ALL; }

	public static void register(Element e) { ALL.add(e); }

	static void text(Gfx g, String s, int x, int y, int color) {
		if (GuiState.THEME.hudShadow.get()) g.textShadow(s, x, y, color);
		else g.text(s, x, y, color);
	}

	/** Screen position of an element for the given screen width. */
	public static int[] position(Element e, int screenW) {
		int[] p = GuiState.HUD.computeIfAbsent(e.id(), k -> e.defaultPos().clone());
		int x = e.rightAnchored() ? screenW - p[0] : p[0];
		return new int[] {x, p[1]};
	}
}
