package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Gfx;
import breezeclient.breezeclient.client.hud.HudElements;
import breezeclient.breezeclient.client.world.CoverageTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;

/** HUD map of which chunks you have already had loaded on this server and dimension. Tracking itself is always on. */
public class CoverageMap extends Module {
	private static CoverageMap instance;

	private final NumberSetting radius = add(new NumberSetting("Radius (chunks)", 16, 6, 32), "Map", "How many chunks each way the map shows.");
	private final NumberSetting cell = add(new NumberSetting("Cell size (px)", 3, 2, 6), "Map", "Size of one chunk on screen.");

	public CoverageMap() {
		super("CoverageMap", Category.WORLD);
		instance = this;
	}

	@Override
	public String info() { return String.valueOf(CoverageTracker.count()); }

	public static void registerEvents() {
		HudElements.register(new HudElements.Element() {
			public String id() { return "coverage"; }
			public String label() { return "Coverage map"; }
			public int[] defaultPos() { return new int[] {4, 150}; }
			public boolean rightAnchored() { return false; }
			public int[] draw(Gfx g, int x, int y, boolean preview) {
				CoverageMap m = instance;
				if (m == null || (!m.enabled() && !preview)) return new int[] {0, 0};
				return m.drawMap(g, x, y, preview);
			}
		});
	}

	private int[] drawMap(Gfx g, int x, int y, boolean preview) {
		int r = radius.asInt(), c = cell.asInt();
		int size = (r * 2 + 1) * c;
		g.rect(x - 1, y - 1, size + 2, size + 2, 0xAA000000);
		Minecraft mc = Minecraft.getInstance();
		boolean live = mc.player != null && mc.level != null;
		int pcx = live ? mc.player.chunkPosition().x : 0, pcz = live ? mc.player.chunkPosition().z : 0;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				boolean seen = live ? CoverageTracker.seen(pcx + dx, pcz + dz) : (dx * dx + dz * dz) < (r * r) / 3;
				g.rect(x + (dx + r) * c, y + (dz + r) * c, c - 1, c - 1, seen ? 0xFF2E8B57 : 0xFF2A2A2A);
			}
		}
		g.rect(x + r * c - 1, y + r * c - 1, c + 1, c + 1, 0xFFFFFFFF);
		g.text("N", x + size / 2 - 2, y - 10, 0xFFFFFFFF);
		return new int[] {size, size};
	}
}
