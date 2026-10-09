package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.render.RenderUtil;
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

import java.util.ArrayList;
import java.util.List;

/** Finds 1x1 holes (open air, two blocks tall, walled on all four sides): safe ones for crystal fights, and hiding holes. */
public class HoleEsp extends Module {
	private record Hole(BlockPos pos, boolean safe) {}

	private final NumberSetting range = add(new NumberSetting("Range", 12, 4, 32), "Detection", "Search radius around you in blocks.");
	private final BoolSetting safeOnly = add(new BoolSetting("Safe holes only", false), "Detection", "Only show holes walled by bedrock or obsidian on all four sides.");
	private final BoolSetting fill = add(new BoolSetting("Fill", true), "Render", "Translucent fill inside the markers.");
	private final ColorSetting safeColor = add(new ColorSetting("Safe color", 0xFF33FF66), "Colors", "Holes walled entirely by bedrock or obsidian.");
	private final ColorSetting unsafeColor = add(new ColorSetting("Unsafe color", 0xFFFFCC33), "Colors", "Holes walled by other blocks.");

	private final List<Hole> holes = new ArrayList<>();
	private int ticks;

	public HoleEsp() { super("HoleESP", Category.RENDER); }

	@Override
	public String info() { return holes.isEmpty() ? "" : String.valueOf(holes.size()); }

	@Override
	protected void onDisable() { holes.clear(); }

	private static boolean solid(BlockState s) { return !s.isAir() && s.getFluidState().isEmpty() && s.isSolid(); }

	private static boolean strong(BlockState s) { return s.is(Blocks.BEDROCK) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN) || s.is(Blocks.RESPAWN_ANCHOR); }

	@Override
	protected void onTick() {
		if (++ticks % 10 != 0) return;
		var mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		holes.clear();
		BlockPos c = mc.player.blockPosition();
		int r = range.asInt();
		var mut = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				for (int y = -r / 2; y <= r / 2; y++) {
					mut.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (!mc.level.getBlockState(mut).isAir() || !mc.level.getBlockState(mut.above()).isAir()) continue;
					if (!solid(mc.level.getBlockState(mut.below()))) continue;
					boolean allSolid = true, allStrong = strong(mc.level.getBlockState(mut.below()));
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockState side = mc.level.getBlockState(mut.relative(d));
						if (!solid(side)) {
							allSolid = false;
							break;
						}
						allStrong &= strong(side);
					}
					if (!allSolid || (safeOnly.get() && !allStrong)) continue;
					holes.add(new Hole(mut.immutable(), allStrong));
					if (holes.size() >= 400) return;
				}
			}
		}
	}

	@Override
	protected void onRender(RenderFrame f) {
		boolean doFill = fill.get() && f instanceof RenderUtil;
		for (Hole h : holes) {
			int c = (h.safe() ? safeColor.get() : unsafeColor.get()) | 0xFF000000;
			BlockPos p = h.pos();
			f.drawBox(p.getX() + 0.1, p.getY(), p.getZ() + 0.1, p.getX() + 0.9, p.getY() + 0.1, p.getZ() + 0.9, c);
			if (doFill) ((RenderUtil) f).drawFilledBox(p.getX() + 0.1, p.getY(), p.getZ() + 0.1, p.getX() + 0.9, p.getY() + 0.1, p.getZ() + 0.9, (c & 0xFFFFFF) | 0x60000000);
		}
	}
}
