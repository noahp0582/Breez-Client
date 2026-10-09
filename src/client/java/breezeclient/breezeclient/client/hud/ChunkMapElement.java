package breezeclient.breezeclient.client.hud;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.gui.Gfx;
import breezeclient.breezeclient.client.world.ChunkAgeTracker;
import breezeclient.breezeclient.core.world.ChunkAge;
import net.minecraft.client.Minecraft;

/** Minimap of new / old / unseen chunks around you. Shown while the NewChunks module is enabled. */
public final class ChunkMapElement implements HudElements.Element {
	private static final int RADIUS = 12, CELL = 3;

	@Override
	public String id() { return "chunkmap"; }

	@Override
	public String label() { return "Chunk map"; }

	@Override
	public int[] defaultPos() { return new int[] {4, 52}; }

	@Override
	public boolean rightAnchored() { return false; }

	@Override
	public int[] draw(Gfx g, int x, int y, boolean preview) {
		var mod = BreezClientClient.MODULES.get("NewChunks");
		boolean on = mod != null && mod.enabled();
		int size = (RADIUS * 2 + 1) * CELL;
		if (!on && !preview) return new int[] {0, 0};
		g.rect(x - 1, y - 1, size + 2, size + 2, 0xAA000000);
		var mc = Minecraft.getInstance();
		int pcx = mc.player == null ? 0 : mc.player.chunkPosition().x, pcz = mc.player == null ? 0 : mc.player.chunkPosition().z;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				ChunkAge.State s = mc.player == null ? ChunkAge.State.UNKNOWN : ChunkAgeTracker.state(pcx + dx, pcz + dz);
				if (preview && mc.player == null) s = (dx + dz) % 3 == 0 ? ChunkAge.State.OLD : ((dx * dz) % 2 == 0 ? ChunkAge.State.NEW : ChunkAge.State.UNKNOWN);
				int c = switch (s) {
					case NEW -> 0xFFE03030;
					case OLD -> 0xFF30C060;
					default -> 0xFF303030;
				};
				g.rect(x + (dx + RADIUS) * CELL, y + (dz + RADIUS) * CELL, CELL - 1, CELL - 1, c);
			}
		}
		int cx = x + RADIUS * CELL, cy = y + RADIUS * CELL;
		g.rect(cx - 1, cy - 1, CELL + 1, CELL + 1, 0xFFFFFFFF);
		g.text("N", x + size / 2 - 2, y - 10, 0xFFFFFFFF);
		return new int[] {size, size};
	}
}
