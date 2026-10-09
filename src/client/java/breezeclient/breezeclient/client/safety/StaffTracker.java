package breezeclient.breezeclient.client.safety;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.session.Session;
import breezeclient.breezeclient.core.config.ServerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Decides which nearby players count as staff, and lists staff currently in the tab list. */
public final class StaffTracker {
	public record Hit(String name, String reason, double distance) {}

	private StaffTracker() {}

	public static ServerProfile profile() { return BreezClientClient.config.profile(Session.serverKey()); }

	/**
	 * First player within {@code radius} blocks that matches a rule.
	 *
	 * @param spectator flag players in spectator mode
	 * @param ghosts    flag players that have an entity but no tab-list entry (vanish plugins)
	 * @param anyPlayer flag every player not in {@code trusted}
	 */
	public static Hit findNearby(double radius, boolean spectator, boolean ghosts, boolean anyPlayer, Set<String> trusted) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || mc.getConnection() == null) return null;
		ServerProfile profile = profile();
		double r2 = radius * radius;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == mc.player) continue;
			String name = nameOf(p);
			if (trusted.contains(name.toLowerCase(Locale.ROOT))) continue;
			double d2 = p.distanceToSqr(mc.player);
			if (d2 > r2) continue;
			double d = Math.sqrt(d2);
			if (profile.isStaff(name)) return new Hit(name, "staff list", d);
			if (spectator && p.isSpectator()) return new Hit(name, "spectator mode", d);
			if (ghosts && mc.getConnection().getPlayerInfo(p.getUUID()) == null) return new Hit(name, "not in tab list", d);
			if (anyPlayer) return new Hit(name, "player nearby", d);
		}
		return null;
	}

	public static String nameOf(Player p) { return p.getName().getString(); }

	/** Staff names currently present in the tab list. */
	public static List<String> onlineStaff() {
		Minecraft mc = Minecraft.getInstance();
		List<String> out = new ArrayList<>();
		if (mc.getConnection() == null) return out;
		ServerProfile profile = profile();
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			String n = info.getProfile().name();
			if (profile.isStaff(n)) out.add(n);
		}
		return out;
	}
}
