package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/** Finds amethyst clusters (and optionally buds / budding amethyst, which mark geodes). */
public class AmethystDetector extends EspModule {
	private final BoolSetting buds = add(new BoolSetting("Include buds", false));
	private final BoolSetting budding = add(new BoolSetting("Include budding blocks", true));
	private final ColorSetting cluster = add(new ColorSetting("Cluster", 0xFFC77DFF));
	private final ColorSetting budColor = add(new ColorSetting("Bud", 0xFF8E5DD6));
	private final BoolSetting notify = add(new BoolSetting("Notify", true));
	private final BoolSetting waypoint = add(new BoolSetting("Waypoint", true));

	public AmethystDetector() { super("AmethystDetector", Category.WORLD); }

	@Override
	protected ScanTarget createTarget() {
		Set<Block> bud = Set.of(Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD);
		return new ScanTarget(s -> {
			Block b = s.getBlock();
			if (b == Blocks.AMETHYST_CLUSTER) return true;
			if (buds.get() && bud.contains(b)) return true;
			return budding.get() && b == Blocks.BUDDING_AMETHYST;
		});
	}

	@Override
	protected int colorOf(BlockState s) {
		return s.is(Blocks.AMETHYST_CLUSTER) || s.is(Blocks.BUDDING_AMETHYST) ? cluster.get() : budColor.get();
	}

	@Override
	protected void onFound(BlockPos p, BlockState s) {
		if (!s.is(Blocks.AMETHYST_CLUSTER)) return;
		if (notify.get()) say("Amethyst cluster at " + p.getX() + " " + p.getY() + " " + p.getZ());
		if (waypoint.get()) Waypoints.add("Amethyst", p);
	}
}
