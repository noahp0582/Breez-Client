package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.integration.DiscordIpcClient;
import breezeclient.breezeclient.client.integration.ProxyRelay;
import breezeclient.breezeclient.client.integration.RadioPlayer;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.net.ProxyHandshake;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Modules that talk to things outside the game: Discord, a proxy, internet radio. They share this file. */
public final class ClientModules {
	private ClientModules() {}

	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Shows what you are doing in your Discord profile, through Discord's local IPC (the desktop app must be running).
	 * It needs an application id from https://discord.com/developers/applications (the "Application ID" of a new app);
	 * nothing is sent anywhere else, and nothing is sent until the id is filled in.
	 */
	public static class DiscordPresence extends Module {
		private final StringSetting appId = add(new StringSetting("Application ID", ""), "Discord",
			"Create an application at discord.com/developers/applications and paste its Application ID here. Empty = nothing is sent.");
		private final StringSetting details = add(new StringSetting("Top line", "Playing Minecraft"), "Text",
			"First line. {server} = the server address (or 'Singleplayer' / 'Main menu'), {dimension} = overworld, nether or end.");
		private final StringSetting state = add(new StringSetting("Second line", "Breez Client"), "Text", "Second line. Same placeholders.");
		private final BoolSetting showServer = add(new BoolSetting("Show server address", false), "Privacy",
			"Off: {server} says 'a server' instead of the address, so your profile does not reveal where you play.");
		private final NumberSetting interval = add(new NumberSetting("Update every (s)", 15, 5, 120), "Text", "Discord rate-limits presence updates; 15 is safe.");

		private ExecutorService worker;
		private DiscordIpcClient client;
		private int ticks;
		private long start;
		private volatile String lastSent = "";
		private boolean warned;

		public DiscordPresence() { super("DiscordPresence", Category.CLIENT); }

		@Override
		protected void onEnable() {
			worker = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "breez-discord");
				t.setDaemon(true);
				return t;
			});
			client = new DiscordIpcClient();
			start = System.currentTimeMillis() / 1000;
			ticks = 0;
			lastSent = "";
			warned = false;
		}

		@Override
		protected void onDisable() {
			ExecutorService w = worker;
			DiscordIpcClient c = client;
			worker = null;
			client = null;
			if (w != null) w.submit(() -> {
				if (c != null) c.close();
			});
			if (w != null) w.shutdown();
		}

		static String fill(String template, String server, String dimension) {
			return template.replace("{server}", server).replace("{dimension}", dimension);
		}

		private String describeServer(Minecraft mc) {
			if (mc.level == null) return "Main menu";
			if (mc.getSingleplayerServer() != null) return "Singleplayer";
			var s = mc.getCurrentServer();
			return s != null && showServer.get() ? s.ip : "a server";
		}

		private static String dimension(Minecraft mc) {
			if (mc.level == null) return "";
			var d = mc.level.dimension().identifier().getPath();
			return d.equals("the_nether") ? "the Nether" : d.equals("the_end") ? "the End" : "the Overworld";
		}

		@Override
		protected void onTick() {
			if (worker == null || ++ticks % (interval.asInt() * 20) != 1) return;
			String id = appId.get().trim();
			if (id.isEmpty()) {
				if (!warned) {
					warned = true;
					Toasts.show("DiscordPresence", "Paste an Application ID in the module's settings first", 0xFFFFAA00);
				}
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			String server = describeServer(mc), dim = dimension(mc);
			String top = fill(details.get(), server, dim), bottom = fill(state.get(), server, dim);
			String key = id + "|" + top + "|" + bottom;
			DiscordIpcClient c = client;
			long since = start;
			if (key.equals(lastSent) && c != null && c.connected()) return;
			lastSent = key;
			worker.submit(() -> {
				try {
					if (!c.connected()) c.connect(id);
					c.setActivity(ProcessHandle.current().pid(), top, bottom, since);
				} catch (IOException e) {
					c.close();
					lastSent = ""; // try again next time
				}
			});
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Connects to servers through a SOCKS5, SOCKS4 or HTTP proxy. Only the game's server connection uses it (and the
	 * server-list pings); the Microsoft login and downloads do not. The login details are stored in your Breez config
	 * file; leave them empty for an open proxy.
	 */
	public static class ProxyModule extends Module {
		private final EnumSetting<ProxyHandshake.Type> type = add(new EnumSetting<>("Type", ProxyHandshake.Type.SOCKS5), "Proxy", "SOCKS5 is the usual choice.");
		private final StringSetting host = add(new StringSetting("Host", ""), "Proxy", "Proxy address, e.g. 203.0.113.7 or proxy.example.com. Empty = no proxy.");
		private final NumberSetting port = add(new NumberSetting("Port", 1080, 1, 65535), "Proxy", "Proxy port.");
		private final StringSetting user = add(new StringSetting("Username", ""), "Login", "Leave empty for an open proxy.");
		private final StringSetting pass = add(new StringSetting("Password", ""), "Login", "Stored as plain text in the config file.");

		public ProxyModule() {
			super("Proxy", Category.CLIENT);
			ProxyRelay.setSource(this::config);
			addAction("Show the last proxy error", "What went wrong the last time a connection through the proxy failed.", () -> {
				String e = ProxyRelay.lastError();
				Toasts.show("Proxy", e.isEmpty() ? "No error" : e);
			});
		}

		private ProxyRelay.Config config() {
			if (!enabled()) return null;
			// enabled without a host is still a proxy config: the relay then refuses to connect instead of leaking your address
			return new ProxyRelay.Config(type.get(), host.get().trim(), port.asInt(), user.get(), pass.get());
		}

		@Override
		public String info() { return host.get().isBlank() ? "" : host.get().trim() + ":" + port.asInt(); }

		@Override
		protected void onEnable() {
			if (host.get().isBlank()) Toasts.show("Proxy", "Set the proxy Host and Port in the module's settings; until then server connections are blocked, not sent directly", 0xFFFFAA00);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** Plays an internet radio station (MP3 streams) while you play. Audio goes to your default output device. */
	public static class Radio extends Module {
		public enum Station {
			RADIO_PARADISE("https://stream.radioparadise.com/mp3-128"),
			ILOVERADIO("https://streams.ilovemusic.de/iloveradio1.mp3"),
			FIP("https://icecast.radiofrance.fr/fip-midfi.mp3"),
			CUSTOM("");

			final String url;

			Station(String url) { this.url = url; }
		}

		private final EnumSetting<Station> station = add(new EnumSetting<>("Station", Station.RADIO_PARADISE), "Station", "Pick a station, or CUSTOM and give an MP3 stream URL below.");
		private final StringSetting custom = add(new StringSetting("Custom URL", ""), "Station", "An http(s) URL of an MP3 stream (used when Station is CUSTOM).");
		private final NumberSetting volume = add(new NumberSetting("Volume", 30, 0, 100), "Audio", "Percent.");

		private RadioPlayer player;
		private volatile String status = "";
		private Station playing;
		private String playingUrl = "";
		private int lastVolume = -1;

		public Radio() { super("Radio", Category.CLIENT); }

		private String urlNow() { return station.get() == Station.CUSTOM ? custom.get().trim() : station.get().url; }

		@Override
		public String info() { return status.isEmpty() ? "" : status; }

		@Override
		protected void onEnable() { start(); }

		@Override
		protected void onDisable() {
			RadioPlayer p = player;
			player = null;
			status = "";
			if (p != null) new Thread(p::stop, "breez-radio-stop").start(); // joining the audio thread must not stall the game
		}

		private void start() {
			String url = urlNow();
			playing = station.get(); // remembered even when the URL is unusable, so the same bad value is not retried every tick
			playingUrl = url;
			if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
				Toasts.show("Radio", "Set a stream URL starting with http:// or https://", 0xFFFFAA00);
				status = "no URL";
				return;
			}
			RadioPlayer p = new RadioPlayer(s -> status = s);
			p.setVolume(volume.asInt());
			p.play(url);
			player = p;
			status = "";
			lastVolume = volume.asInt();
		}

		private String pendingUrl = "";
		private int pendingTicks;

		@Override
		protected void onTick() {
			if (player != null && lastVolume != volume.asInt()) {
				lastVolume = volume.asInt();
				player.setVolume(lastVolume);
			}
			String url = urlNow();
			boolean changed = playing != station.get() || !playingUrl.equals(url);
			if (!changed) {
				pendingUrl = "";
				return;
			}
			// wait until the value has stopped changing, so typing a URL does not open a connection per keystroke
			if (!url.equals(pendingUrl)) {
				pendingUrl = url;
				pendingTicks = 0;
				return;
			}
			if (++pendingTicks < 30) return;
			pendingUrl = "";
			RadioPlayer old = player;
			player = null;
			if (old != null) new Thread(old::stop, "breez-radio-stop").start();
			start();
		}
	}
}
