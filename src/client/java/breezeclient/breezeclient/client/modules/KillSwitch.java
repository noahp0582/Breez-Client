package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.safety.Reconnect;
import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.client.session.Session;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Drops the connection the moment staff (or a spectator / any player, if configured) is detected nearby. */
public class KillSwitch extends Module {
	private final NumberSetting radius = add(new NumberSetting("Radius", 128, 8, 512));
	private final BoolSetting spectator = add(new BoolSetting("Spectators", true));
	private final BoolSetting ghosts = add(new BoolSetting("Tab-list ghosts", false));
	private final BoolSetting anyPlayer = add(new BoolSetting("Any player", false));
	private final StringListSetting trusted = add(new StringListSetting("Trusted", List.of()));
	private final BoolSetting reconnect = add(new BoolSetting("Allow auto-reconnect", false));

	private static KillSwitch instance;

	public KillSwitch() {
		super("KillSwitch", Category.SAFETY);
		instance = this;
	}

	public static void registerEvents() {
		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (instance != null && instance.enabled() && entity instanceof Player) instance.check();
		});
	}

	public StringListSetting trusted() { return trusted; }

	@Override
	protected void onTick() { check(); }

	private void check() {
		Set<String> trust = new HashSet<>(trusted.get());
		var hit = StaffTracker.findNearby(radius.get(), spectator.get(), ghosts.get(), anyPlayer.get(), trust);
		if (hit == null) return;
		trigger(hit);
	}

	private void trigger(StaffTracker.Hit hit) {
		Minecraft mc = Minecraft.getInstance();
		var conn = mc.getConnection();
		if (conn == null) return;
		log(hit);
		if (!reconnect.get()) Reconnect.suppress();
		conn.getConnection().disconnect(Component.literal("KillSwitch: " + hit.name() + " (" + hit.reason() + ")"));
	}

	private void log(StaffTracker.Hit hit) {
		try {
			Path file = FabricLoader.getInstance().getConfigDir().resolve("breez-client").resolve("killswitch.log");
			Files.createDirectories(file.getParent());
			var p = Minecraft.getInstance().player;
			String pos = p == null ? "?" : p.blockPosition().toShortString();
			String line = LocalDateTime.now() + " server=" + Session.serverKey() + " player=" + hit.name() + " reason=" + hit.reason()
				+ " dist=" + String.format("%.1f", hit.distance()) + " myPos=" + pos + System.lineSeparator();
			Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// logging must never delay the disconnect
		}
	}
}
