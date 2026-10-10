package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.session.Session;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Logs players joining and leaving and where each was last seen, to players.csv, with `.players [name]`. */
public class PlayerLogger extends Module {
	public record Seen(String name, int x, int y, int z, String dim, long time) {}

	private static final Map<String, Seen> LAST_SEEN = new HashMap<>();

	private final BoolSetting announce = add(new BoolSetting("Announce joins and leaves", true), "General", "Pop-up when a player joins or leaves.");
	private final BoolSetting ignoreFriends = add(new BoolSetting("Skip friends", true), "General", "Do not announce your friends.");

	private Set<String> online = new HashSet<>();
	private boolean primed;
	private int ticks;

	public PlayerLogger() { super("PlayerLogger", Category.MISC); }

	public static List<Seen> recent() {
		List<Seen> l = new ArrayList<>(LAST_SEEN.values());
		l.sort((a, b) -> Long.compare(b.time(), a.time()));
		return l;
	}

	public static Seen find(String name) { return LAST_SEEN.get(name.toLowerCase(Locale.ROOT)); }

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("breez-client").resolve("players.csv"); }

	@Override
	protected void onEnable() {
		online = new HashSet<>();
		primed = false;
	}

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var conn = mc.getConnection();
		if (conn == null || mc.player == null || mc.level == null) return;
		if (++ticks % 20 != 0) return;
		Set<String> now = new HashSet<>();
		for (PlayerInfo pi : conn.getOnlinePlayers()) now.add(pi.getProfile().name());
		if (primed) {
			for (String n : now) if (!online.contains(n)) event("joined", n);
			for (String n : online) if (!now.contains(n)) event("left", n);
		}
		online = now;
		primed = true;
		String dim = mc.level.dimension().identifier().toString();
		for (Player p : mc.level.players()) {
			if (p == mc.player) continue;
			String n = p.getName().getString();
			LAST_SEEN.put(n.toLowerCase(Locale.ROOT), new Seen(n, p.getBlockX(), p.getBlockY(), p.getBlockZ(), dim, System.currentTimeMillis()));
		}
	}

	private void event(String what, String name) {
		boolean friend = Friends.is(name);
		if (announce.get() && !(friend && ignoreFriends.get())) Toasts.show("Player " + what, name);
		Seen s = find(name);
		String where = s == null ? "" : s.x() + " " + s.y() + " " + s.z() + " " + s.dim();
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			String line = LocalDateTime.now() + "," + Session.serverKey() + "," + what + "," + name + "," + where + System.lineSeparator();
			Files.writeString(f, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// logging is best effort
		}
	}
}
