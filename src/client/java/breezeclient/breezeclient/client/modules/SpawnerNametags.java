package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** Labels each spawner with the mob it spawns and draws the 16-block sphere where it switches on. */
public class SpawnerNametags extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 128, 16, 512), "Render", "How far away spawners are labelled.");
	private final BoolSetting rings = add(new BoolSetting("Activation range ring", true), "Render", "Ring of radius 16 around each spawner: players inside it keep the spawner active.");
	private final BoolSetting onlyNearby = add(new BoolSetting("Ring only within 48 blocks", true), "Render", "Skip rings for far spawners to keep the screen readable.");
	private final ColorSetting color = add(new ColorSetting("Color", 0xFF55FF99), "Colors", "Color of labels and rings.");

	public SpawnerNametags() { super("SpawnerNametags", Category.RENDER); }

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		Vec3 cp = mc.gameRenderer.getMainCamera().position();
		double r2 = range.get() * range.get();
		int c = color.get() | 0xFF000000;
		for (long key : ChunkScanner.loadedChunks()) {
			LevelChunk chunk = mc.level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), false);
			if (chunk == null) continue;
			for (BlockEntity be : chunk.getBlockEntities().values()) {
				if (!(be instanceof SpawnerBlockEntity sp)) continue;
				BlockPos p = be.getBlockPos();
				double dx = p.getX() + 0.5 - cp.x, dy = p.getY() + 0.5 - cp.y, dz = p.getZ() + 0.5 - cp.z;
				double d2 = dx * dx + dy * dy + dz * dz;
				if (d2 > r2) continue;
				String type = "Spawner";
				try {
					var e = sp.getSpawner().getOrCreateDisplayEntity(mc.level, p);
					if (e != null) type = e.getType().getDescription().getString() + " spawner";
				} catch (RuntimeException ignored) {
					// unknown entity data: keep the generic label
				}
				f.drawText(p.getX() + 0.5, p.getY() + 1.6, p.getZ() + 0.5, type, c);
				if (rings.get() && (!onlyNearby.get() || d2 < 48 * 48)) {
					int seg = 40;
					double cx = p.getX() + 0.5, cy = p.getY() + 0.5, cz = p.getZ() + 0.5;
					for (int i = 0; i < seg; i++) {
						double a1 = i * 2 * Math.PI / seg, a2 = (i + 1) * 2 * Math.PI / seg;
						f.drawLine(cx + Math.cos(a1) * 16, cy, cz + Math.sin(a1) * 16, cx + Math.cos(a2) * 16, cy, cz + Math.sin(a2) * 16, c & 0xCCFFFFFF);
					}
				}
			}
		}
	}
}
