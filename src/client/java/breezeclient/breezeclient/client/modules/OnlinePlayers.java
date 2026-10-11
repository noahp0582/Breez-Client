package breezeclient.breezeclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Names of the players in the tab list right now, for the GUI pickers of staff, friends and trusted lists. */
public final class OnlinePlayers {
	private OnlinePlayers() {}

	public static List<String> names() {
		var conn = Minecraft.getInstance().getConnection();
		List<String> out = new ArrayList<>();
		if (conn == null) return out;
		for (PlayerInfo pi : conn.getOnlinePlayers()) out.add(pi.getProfile().name());
		out.sort(Comparator.comparing(String::toLowerCase));
		return out;
	}
}
