package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Bind it to a key: each press copies the coordinates of the block you are looking at to the clipboard. */
public class CoordSnapper extends Module {
	private final BoolSetting commaSeparated = add(new BoolSetting("Comma separated", false), "Format", "Copy as x, y, z instead of x y z.");
	private final BoolSetting saveWaypoint = add(new BoolSetting("Also save a waypoint", false), "Format", "Add the spot to your waypoints too.");

	public CoordSnapper() { super("CoordSnapper", Category.MISC); }

	@Override
	protected boolean persistsEnabled() { return false; }

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		HitResult hr = mc.hitResult;
		if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
			var p = bh.getBlockPos();
			String text = commaSeparated.get() ? p.getX() + ", " + p.getY() + ", " + p.getZ() : p.getX() + " " + p.getY() + " " + p.getZ();
			mc.keyboardHandler.setClipboard(text);
			Toasts.show("Coordinates copied", text);
			if (saveWaypoint.get()) breezeclient.breezeclient.client.world.Waypoints.add("Snapped", p);
		} else {
			Toasts.show("CoordSnapper", "Look at a block first", 0xFFFF5555);
		}
		setEnabled(false);
	}
}
