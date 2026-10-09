package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.render.RenderUtil;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Marks the empty "open slots" next to budding amethyst, where a crystal can still start growing (unfinished geodes). */
public class ClusterEsp extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 64, 8, 256), "Render", "How far away open slots are drawn.");
	private final BoolSetting fill = add(new BoolSetting("Fill", true), "Render", "Translucent fill inside the markers.");
	private final BoolSetting waterToo = add(new BoolSetting("Include water", true), "Detection", "Water source blocks also count as open slots (crystals grow underwater too).");
	private final ColorSetting color = add(new ColorSetting("Color", 0xFF5A189A), "Colors", "Color of open slots.");

	private ScanTarget target;
	private int ticks;
	private final List<BlockPos> slots = new ArrayList<>();

	public ClusterEsp() { super("ClusterESP", Category.RENDER); }

	@Override
	protected void onEnable() {
		target = new ScanTarget(s -> s.is(Blocks.BUDDING_AMETHYST));
		ChunkScanner.add(target);
		slots.clear();
	}

	@Override
	protected void onDisable() {
		if (target != null) ChunkScanner.remove(target);
		target = null;
		slots.clear();
	}

	@Override
	public String info() { return slots.isEmpty() ? "" : String.valueOf(slots.size()); }

	@Override
	protected void onTick() {
		if (target == null || ++ticks % 20 != 0) return;
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		Set<BlockPos> found = new HashSet<>();
		for (var list : target.chunks()) {
			for (BlockPos b : list) {
				for (Direction d : Direction.values()) {
					BlockPos n = b.relative(d);
					BlockState st = level.getBlockState(n);
					boolean open = st.isAir() || (waterToo.get() && st.is(Blocks.WATER) && st.getFluidState().isSource());
					if (open && found.size() < 6000) found.add(n.immutable());
				}
			}
		}
		slots.clear();
		slots.addAll(found);
	}

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		double r2 = range.get() * range.get();
		boolean doFill = fill.get() && f instanceof RenderUtil;
		int c = color.get() | 0xFF000000;
		for (BlockPos p : slots) {
			double dx = p.getX() + 0.5 - cam.x, dy = p.getY() + 0.5 - cam.y, dz = p.getZ() + 0.5 - cam.z;
			if (dx * dx + dy * dy + dz * dz > r2) continue;
			double e = 0.28; // inset so the marker reads as "a crystal-sized slot", not a full block
			f.drawBox(p.getX() + e, p.getY() + e, p.getZ() + e, p.getX() + 1 - e, p.getY() + 1 - e, p.getZ() + 1 - e, c);
			if (doFill) ((RenderUtil) f).drawFilledBox(p.getX() + e, p.getY() + e, p.getZ() + e, p.getX() + 1 - e, p.getY() + 1 - e, p.getZ() + 1 - e, (c & 0xFFFFFF) | 0x50000000);
		}
	}
}
