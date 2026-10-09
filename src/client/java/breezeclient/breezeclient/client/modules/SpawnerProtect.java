package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.Modules;
import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.safety.Reconnect;
import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;

/**
 * When a stranger gets close, picks up the spawners within reach with SpawnerPickup, then leaves the server. Without a
 * silk touch pickaxe, or with nothing in reach, it just leaves.
 */
public class SpawnerProtect extends Module {
	private final NumberSetting radius = add(new NumberSetting("Stranger radius", 48, 8, 256), "Trigger", "A player this close triggers the protection.");
	private final StringListSetting trusted = add(new StringListSetting("Trusted", List.of()), "Trigger", "Players that never trigger it.");
	private final BoolSetting salvage = add(new BoolSetting("Salvage spawners first", true), "Behavior", "Pick up spawners within 12 blocks before leaving. Off = leave immediately.");
	private final NumberSetting maxSeconds = add(new NumberSetting("Max salvage time (s)", 45, 10, 180), "Behavior", "Leave anyway after this long.");
	private final BoolSetting leave = add(new BoolSetting("Disconnect afterwards", true), "Behavior", "Leave the server when done. Off = only salvage.");

	private boolean running;
	private boolean finishing;
	private int ticks;

	public SpawnerProtect() { super("SpawnerProtect", Category.SAFETY); }

	@Override
	protected void onDisable() { running = false; }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		if (running) {
			if (++ticks > maxSeconds.asInt() * 20) finish();
			return;
		}
		var hit = StaffTracker.findNearby(radius.get(), true, false, true, new HashSet<>(trusted.get()));
		if (hit == null) return;
		Toasts.show("SpawnerProtect", hit.name() + " is " + (int) hit.distance() + " blocks away", 0xFFFF5555);
		boolean nearSpawner = false;
		var p = mc.player.blockPosition();
		for (int x = -12; x <= 12 && !nearSpawner; x += 1) {
			for (int y = -6; y <= 6 && !nearSpawner; y++) {
				for (int z = -12; z <= 12; z++) {
					if (mc.level.getBlockState(p.offset(x, y, z)).is(Blocks.SPAWNER)) {
						nearSpawner = true;
						break;
					}
				}
			}
		}
		if (salvage.get() && nearSpawner) {
			running = true;
			ticks = 0;
			Modules.spawnerPickup.startSalvage(this::finish);
		} else {
			finish();
		}
	}

	private void finish() {
		if (finishing) return; // stopping SpawnerPickup calls back into finish
		finishing = true;
		try {
			finishNow();
		} finally {
			finishing = false;
		}
	}

	private void finishNow() {
		running = false;
		Modules.spawnerPickup.setEnabled(false);
		if (!leave.get()) return;
		var mc = Minecraft.getInstance();
		var conn = mc.getConnection();
		if (conn == null) return;
		Reconnect.suppress();
		conn.getConnection().disconnect(Component.literal("SpawnerProtect"));
	}
}
