package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Finds nether portals (players' travel points, and bases tend to sit near them). */
public class PortalEsp extends EspModule {
	private final ColorSetting color = add(new ColorSetting("Color", 0xFFB44BFF), "Colors", "Color of portal blocks.");
	private final BoolSetting notify = add(new BoolSetting("Notify", true), "Alerts", "Show a notification for each new portal.");
	private final BoolSetting waypoint = add(new BoolSetting("Waypoint", false), "Alerts", "Save a waypoint for each new portal.");

	public PortalEsp() { super("PortalESP", Category.RENDER); }

	@Override
	protected ScanTarget createTarget() { return new ScanTarget(s -> s.is(Blocks.NETHER_PORTAL)); }

	@Override
	protected int colorOf(BlockState s) { return color.get(); }

	@Override
	protected void onFound(BlockPos p, BlockState s) {
		if (notify.get()) breezeclient.breezeclient.client.gui.Toasts.show("Nether portal", p.getX() + " " + p.getY() + " " + p.getZ());
		if (waypoint.get()) Waypoints.add("Portal", p);
	}

}
