package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Announces when listed staff join or leave the tab list and can outline them when visible. */
public class StaffList extends Module {
	private final BoolSetting notify = add(new BoolSetting("Notify", true));
	private final BoolSetting esp = add(new BoolSetting("ESP", true));
	private final ColorSetting color = add(new ColorSetting("Color", 0xFFFF2222));

	private Set<String> online = new HashSet<>();
	private int ticks;

	public StaffList() { super("StaffList", Category.SAFETY); }

	@Override
	public String info() {
		return online.isEmpty() ? "" : String.valueOf(online.size());
	}

	@Override
	protected void onTick() {
		if (++ticks % 10 != 0) return;
		List<String> now = StaffTracker.onlineStaff();
		Set<String> set = new HashSet<>(now);
		if (notify.get()) {
			for (String n : set) if (!online.contains(n)) EspModule.say("Staff online: " + n);
			for (String n : online) if (!set.contains(n)) EspModule.say("Staff left: " + n);
		}
		online = set;
	}

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (!esp.get() || mc.level == null) return;
		var profile = StaffTracker.profile();
		for (Player p : mc.level.players()) {
			if (p == mc.player || !profile.isStaff(StaffTracker.nameOf(p))) continue;
			var b = p.getBoundingBox();
			f.drawBox(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, color.get());
			f.drawText(p.getX(), b.maxY + 0.5, p.getZ(), "STAFF " + StaffTracker.nameOf(p), color.get());
		}
	}

	@Override
	protected void onDisable() { online = new HashSet<>(); }
}
