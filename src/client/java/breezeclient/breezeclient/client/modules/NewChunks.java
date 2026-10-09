package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.render.RenderUtil;
import breezeclient.breezeclient.client.world.ChunkAgeTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.world.ChunkAge;
import net.minecraft.client.Minecraft;

/**
 * Draws which chunks are new (freshly generated while you watched) and which already existed. Old chunks in the middle of
 * new terrain are where players have been. Collection runs all the time; this module only draws it.
 */
public class NewChunks extends Module {
	private final NumberSetting range = add(new NumberSetting("Range (chunks)", 10, 2, 32), "Render", "How many chunks around you are drawn.");
	private final BoolSetting strict = add(new BoolSetting("Strict old detection", false), "Detection", "Only call a chunk old when it contains liquid that stayed still. Fewer false 'old' chunks in new terrain, but chunks without any liquid stay unmarked.");
	private final BoolSetting showNew = add(new BoolSetting("Show new chunks", true), "Render", "Draw freshly generated chunks.");
	private final BoolSetting showOld = add(new BoolSetting("Show old chunks", true), "Render", "Draw chunks that already existed.");
	private final BoolSetting followY = add(new BoolSetting("Draw at my height", true), "Render", "Draw the chunk markers at your feet instead of a fixed height.");
	private final NumberSetting fixedY = add(new NumberSetting("Fixed height", 64, -64, 320), "Render", "Height of the markers when not following you.", () -> !followY.get());
	private final BoolSetting fill = add(new BoolSetting("Fill", true), "Render", "Translucent fill inside each chunk marker.");
	private final ColorSetting newColor = add(new ColorSetting("New color", 0xFFFF3030), "Colors", "Color of new chunks.");
	private final ColorSetting oldColor = add(new ColorSetting("Old color", 0xFF30E060), "Colors", "Color of old chunks.");

	public NewChunks() { super("NewChunks", Category.WORLD); }

	@Override
	protected void onTick() { ChunkAgeTracker.setStrict(strict.get()); }

	@Override
	public String info() {
		var c = ChunkAgeTracker.current();
		return c.count(ChunkAge.State.NEW) + "/" + c.count(ChunkAge.State.OLD);
	}

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
		int r = range.asInt();
		double y = followY.get() ? Math.floor(mc.player.getY()) + 0.02 : fixedY.get();
		boolean doFill = fill.get() && f instanceof RenderUtil;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				ChunkAge.State s = ChunkAgeTracker.state(pcx + dx, pcz + dz);
				if (s == ChunkAge.State.UNKNOWN || (s == ChunkAge.State.NEW && !showNew.get()) || (s == ChunkAge.State.OLD && !showOld.get())) continue;
				int c = (s == ChunkAge.State.NEW ? newColor.get() : oldColor.get()) | 0xFF000000;
				double x0 = (pcx + dx) * 16.0, z0 = (pcz + dz) * 16.0;
				f.drawBox(x0 + 0.2, y, z0 + 0.2, x0 + 15.8, y + 0.05, z0 + 15.8, c);
				if (doFill) ((RenderUtil) f).drawFilledBox(x0 + 0.2, y, z0 + 0.2, x0 + 15.8, y + 0.05, z0 + 15.8, (c & 0xFFFFFF) | 0x30000000);
			}
		}
	}
}
