package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnerEsp extends EspModule {
	private final ColorSetting color = add(new ColorSetting("Color", 0xFF00FF66));
	private final BoolSetting notify = add(new BoolSetting("Notify", true));
	private final BoolSetting waypoint = add(new BoolSetting("Waypoint", false));

	public SpawnerEsp() { super("SpawnerESP", Category.RENDER); }

	@Override
	protected ScanTarget createTarget() { return new ScanTarget(s -> s.is(Blocks.SPAWNER)); }

	@Override
	protected int colorOf(BlockState s) { return color.get(); }

	@Override
	protected void onFound(BlockPos p, BlockState s) {
		if (notify.get()) say("Spawner at " + p.getX() + " " + p.getY() + " " + p.getZ());
		if (waypoint.get()) Waypoints.add("Spawner", p);
	}
}
