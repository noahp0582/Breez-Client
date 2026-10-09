package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.world.ChunkAgeTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.world.ChunkAge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Points along trails of old chunks: an old chunk that touches never-seen chunks is a "trail head", the place where
 * someone's path (or a base) continues into the unexplored. Shows a line and a heading to the nearest one.
 */
public class TrailFinder extends Module {
	private final NumberSetting minDist = add(new NumberSetting("Min distance (chunks)", 3, 1, 20), "Detection", "Ignore trail heads closer than this (the ones you are already standing at).");
	private final NumberSetting maxDist = add(new NumberSetting("Max distance (chunks)", 60, 8, 200), "Detection", "How far away trail heads are searched.");
	private final BoolSetting onlyInNew = add(new BoolSetting("Only inside new terrain", true), "Detection", "Require new chunks next to the old one, so natural old areas near spawn are not trails.");
	private final BoolSetting toast = add(new BoolSetting("Toast when a trail appears", true), "Alerts", "Notify when a trail head is found.");
	private final BoolSetting tracer = add(new BoolSetting("Draw line", true), "Render", "Draw a line to the trail head.");
	private final ColorSetting color = add(new ColorSetting("Color", 0xFFFFD23F), "Colors", "Color of the line and label.");

	private int tx, tz;
	private boolean have;
	private boolean announced;
	private int ticks;

	public TrailFinder() { super("TrailFinder", Category.WORLD); }

	@Override
	protected void onDisable() {
		have = false;
		announced = false;
	}

	@Override
	public String info() {
		var p = Minecraft.getInstance().player;
		if (!have || p == null) return "";
		return (int) Math.hypot(tx * 16 + 8 - p.getX(), tz * 16 + 8 - p.getZ()) + "m";
	}

	@Override
	protected void onTick() {
		var mc = Minecraft.getInstance();
		if (mc.player == null || ++ticks % 40 != 0) return;
		var snap = ChunkAgeTracker.current().snapshot();
		int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
		long bestD = Long.MAX_VALUE;
		int bx = 0, bz = 0;
		boolean found = false;
		long min2 = (long) minDist.asInt() * minDist.asInt(), max2 = (long) maxDist.asInt() * maxDist.asInt();
		int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (var e : snap.entrySet()) {
			if (e.getValue() != ChunkAge.State.OLD) continue;
			int cx = ChunkAge.x(e.getKey()), cz = ChunkAge.z(e.getKey());
			long d = (long) (cx - pcx) * (cx - pcx) + (long) (cz - pcz) * (cz - pcz);
			if (d < min2 || d > max2 || d >= bestD) continue;
			boolean frontier = false, nearNew = false;
			for (int[] n : n4) {
				ChunkAge.State s = snap.getOrDefault(ChunkAge.key(cx + n[0], cz + n[1]), ChunkAge.State.UNKNOWN);
				if (s == ChunkAge.State.UNKNOWN) frontier = true;
				if (s == ChunkAge.State.NEW) nearNew = true;
			}
			if (!frontier || (onlyInNew.get() && !nearNew)) continue;
			bestD = d;
			bx = cx;
			bz = cz;
			found = true;
		}
		have = found;
		if (found) {
			tx = bx;
			tz = bz;
			if (!announced && toast.get()) {
				announced = true;
				Toasts.show("Trail found", String.format("Old chunk at %d %d, %d blocks away", bx * 16 + 8, bz * 16 + 8, (int) Math.sqrt(bestD) * 16), 0xFFFFD23F);
			}
		} else {
			announced = false;
		}
	}

	@Override
	protected void onRender(RenderFrame f) {
		var mc = Minecraft.getInstance();
		if (!have || mc.player == null) return;
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		var fwd = mc.gameRenderer.getMainCamera().forwardVector();
		double y = mc.player.getY() + 1.0;
		double x = tx * 16 + 8, z = tz * 16 + 8;
		int c = color.get() | 0xFF000000;
		if (tracer.get()) f.drawLine(cam.x + fwd.x() * 0.3, cam.y + fwd.y() * 0.3, cam.z + fwd.z() * 0.3, x, y, z, c);
		f.drawBox(tx * 16 + 0.5, y - 1, tz * 16 + 0.5, tx * 16 + 15.5, y + 40, tz * 16 + 15.5, c);
		f.drawText(x, y + 42, z, "Trail head " + (int) Math.hypot(x - mc.player.getX(), z - mc.player.getZ()) + "m", c);
	}
}
