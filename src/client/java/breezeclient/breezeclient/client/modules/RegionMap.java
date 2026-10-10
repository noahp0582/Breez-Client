package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Gfx;
import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.hud.HudElements;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.gui.ColorMath;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * A HUD map of regions you define yourself (a server's spawn, a base area, a no-go zone). Each entry is written as
 * "name x1 z1 x2 z2". Add them in the settings or with {@code .set regionmap regions add spawn -500 -500 500 500}.
 */
public class RegionMap extends Module {
	private static RegionMap instance;

	private final StringListSetting regions = add(new StringListSetting("Regions", List.of()), "Regions", "One per line: name x1 z1 x2 z2 (two opposite corners).");
	private final NumberSetting span = add(new NumberSetting("Map span (blocks)", 2000, 100, 100000), "Map", "How many blocks wide and tall the map shows, centered on you.");
	private final NumberSetting size = add(new NumberSetting("Map size (px)", 90, 50, 200), "Map", "Size of the HUD square in pixels.");
	private final BoolSetting toasts = add(new BoolSetting("Announce region changes", true), "Alerts", "Pop-up when you walk into a different region.");

	private record Region(String name, int x1, int z1, int x2, int z2) {
		boolean contains(double x, double z) { return x >= x1 && x <= x2 && z >= z1 && z <= z2; }
	}

	private String lastRegion = "";

	public RegionMap() {
		super("RegionMap", Category.RENDER);
		instance = this;
	}

	public static void registerEvents() {
		HudElements.register(new HudElements.Element() {
			public String id() { return "regionmap"; }
			public String label() { return "Region map"; }
			public int[] defaultPos() { return new int[] {4, 90}; }
			public boolean rightAnchored() { return false; }
			public int[] draw(Gfx g, int x, int y, boolean preview) {
				RegionMap m = instance;
				if (m == null || (!m.enabled() && !preview)) return new int[] {0, 0};
				return m.drawMap(g, x, y, preview);
			}
		});
	}

	private List<Region> parse() {
		List<Region> out = new ArrayList<>();
		for (String line : regions.get()) {
			String[] t = line.trim().split("\\s+");
			if (t.length < 5) continue;
			try {
				int a = Integer.parseInt(t[t.length - 4]), b = Integer.parseInt(t[t.length - 3]);
				int c = Integer.parseInt(t[t.length - 2]), d = Integer.parseInt(t[t.length - 1]);
				String name = String.join(" ", java.util.Arrays.copyOfRange(t, 0, t.length - 4));
				out.add(new Region(name, Math.min(a, c), Math.min(b, d), Math.max(a, c), Math.max(b, d)));
			} catch (NumberFormatException ignored) {
				// malformed lines are skipped; the settings editor shows them as typed
			}
		}
		return out;
	}

	private static int colorFor(String name) {
		int h = name.hashCode();
		return ColorMath.fromHsv(Math.floorMod(h, 360), 0.65, 0.9, 255);
	}

	private int[] drawMap(Gfx g, int x, int y, boolean preview) {
		var p = Minecraft.getInstance().player;
		double px = p == null ? 0 : p.getX(), pz = p == null ? 0 : p.getZ();
		int s = size.asInt();
		double half = span.get() / 2.0;
		g.rect(x, y, s, s, 0xAA101018);
		g.outline(x, y, s, s, g.accent());
		List<Region> list = parse();
		if (list.isEmpty() && preview) list = List.of(new Region("example", -600, -400, 500, 700));
		String here = "";
		for (Region r : list) {
			int rx1 = x + (int) Math.round((r.x1() - (px - half)) / span.get() * s);
			int rx2 = x + (int) Math.round((r.x2() - (px - half)) / span.get() * s);
			int rz1 = y + (int) Math.round((r.z1() - (pz - half)) / span.get() * s);
			int rz2 = y + (int) Math.round((r.z2() - (pz - half)) / span.get() * s);
			int cx1 = Math.max(x, rx1), cx2 = Math.min(x + s, rx2), cz1 = Math.max(y, rz1), cz2 = Math.min(y + s, rz2);
			if (cx2 <= cx1 || cz2 <= cz1) continue;
			int c = colorFor(r.name());
			g.rect(cx1, cz1, cx2 - cx1, cz2 - cz1, ColorMath.withAlpha(c, 90));
			g.outline(cx1, cz1, cx2 - cx1, cz2 - cz1, ColorMath.withAlpha(c, 220));
			if (cx2 - cx1 > g.width(r.name()) + 2) g.text(r.name(), cx1 + 2, cz1 + 2, 0xFFFFFFFF);
			if (r.contains(px, pz)) here = r.name();
		}
		g.rect(x + s / 2 - 1, y + s / 2 - 1, 3, 3, 0xFFFFFFFF); // you
		String label = here.isEmpty() ? "No region" : here;
		g.text(label, x, y + s + 2, g.text());
		if (p != null && toasts.get() && !here.equals(lastRegion)) {
			if (!here.isEmpty()) Toasts.show("Region", "Entered " + here);
			lastRegion = here;
		}
		return new int[] {s, s + 12};
	}
}
