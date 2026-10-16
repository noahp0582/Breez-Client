package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashSet;
import java.util.Set;

/**
 * Shows a title on your own screen when a player in spectator mode comes within range. The title is drawn locally
 * through the vanilla title overlay; nothing is sent to the server.
 */
public class SpectatorAlert extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 32, 4, 128), "Alert", "How close a spectator must be to trigger the title.");
	private final NumberSetting seconds = add(new NumberSetting("Title seconds", 3, 1, 10), "Alert", "How long the title stays on screen.");
	private final BoolSetting sound = add(new BoolSetting("Sound", true), "Alert", "Play a ping with the title.");
	private final BoolSetting chat = add(new BoolSetting("Chat line", false), "Alert", "Also print a local chat line with the name and distance.");

	// spectators currently inside range; each one alerts again only after leaving range and coming back
	private Set<String> inRange = new HashSet<>();

	public SpectatorAlert() { super("SpectatorAlert", Category.SAFETY); }

	@Override
	public String info() { return inRange.isEmpty() ? "" : String.valueOf(inRange.size()); }

	@Override
	protected void onEnable() { inRange = new HashSet<>(); }

	@Override
	protected void onDisable() { inRange = new HashSet<>(); }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) { inRange.clear(); return; }
		double r2 = range.get() * range.get();
		Set<String> now = new HashSet<>();
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == mc.player || p.getId() < 0 || !p.isSpectator()) continue; // negative ids are client-only fakes
			double d2 = p.distanceToSqr(mc.player);
			if (d2 > r2) continue;
			String name = StaffTracker.nameOf(p);
			now.add(name);
			if (!inRange.contains(name)) alert(mc, name, Math.sqrt(d2));
		}
		inRange = now;
	}

	private void alert(Minecraft mc, String name, double distance) {
		int stay = (int) Math.round(seconds.get() * 20);
		mc.gui.setTimes(5, stay, 10);
		mc.gui.setSubtitle(Component.literal("§7" + name + " §8- §f" + Math.round(distance) + "m"));
		mc.gui.setTitle(Component.literal("§cSpectator nearby"));
		if (sound.get()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.2f));
		if (chat.get()) EspModule.say("Spectator nearby: " + name + " (" + Math.round(distance) + "m)");
	}
}
