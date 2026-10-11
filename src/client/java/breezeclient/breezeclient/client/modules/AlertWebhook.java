package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.session.Guard;
import breezeclient.breezeclient.client.session.Session;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Posts alerts to a Discord webhook you set, so a hunt or an AFK session can reach your phone: finds, safety events
 * (KillSwitch, AutoLog) and disconnects. Only the alert text and the server address are sent, only to that URL.
 */
public class AlertWebhook extends Module {
	private static AlertWebhook instance;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private final StringSetting url = add(new StringSetting("Webhook URL", ""), "Webhook",
		"A Discord webhook address (https://discord.com/api/webhooks/...). Treat it like a password: anyone with it can post to your channel.");
	private final StringSetting name = add(new StringSetting("Sender name", "Breez"), "Webhook", "Name shown on the posted messages.");
	private final BoolSetting finds = add(new BoolSetting("Finds", true), "Send", "Bases, spawners, stashes and similar pop-ups.");
	private final BoolSetting safety = add(new BoolSetting("Safety events", true), "Send", "KillSwitch and AutoLog disconnects.");
	private final BoolSetting disconnects = add(new BoolSetting("Disconnects", true), "Send", "Being disconnected from a server.");
	private final NumberSetting cooldown = add(new NumberSetting("Find cooldown (s)", 10, 2, 300), "Send",
		"Finds are grouped and sent at most this often, so a burst becomes one message.");

	private final StringBuilder pending = new StringBuilder();
	private long lastFlush;
	private long lastSafety;

	public AlertWebhook() {
		super("AlertWebhook", Category.MISC);
		instance = this;
	}

	public static void registerEvents() {
		Toasts.setSink((title, body) -> {
			AlertWebhook m = instance;
			if (m == null || !m.enabled() || !m.finds.get()) return;
			if (body.equals("Enabled") || body.equals("Disabled")) return; // module toggles are not finds
			m.queue(title + ": " + body);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> Guard.run("AlertWebhook disconnect", () -> {
			AlertWebhook m = instance;
			if (m != null && m.enabled() && m.disconnects.get()) m.sendNow("Disconnected from " + Session.serverKey());
		}));
	}

	/** A safety event (KillSwitch, AutoLog); sent immediately because the connection is about to drop. */
	public static void safety(String text) {
		AlertWebhook m = instance;
		if (m != null && m.enabled() && m.safety.get()) m.sendNow(text);
	}

	private void queue(String line) {
		synchronized (pending) {
			if (pending.length() < 1500) pending.append(line).append('\n');
		}
	}

	@Override
	protected void onTick() {
		long now = System.currentTimeMillis();
		if (now - lastFlush < cooldown.get() * 1000) return;
		String text;
		synchronized (pending) {
			if (pending.length() == 0) return;
			text = pending.toString().trim();
			pending.setLength(0);
		}
		lastFlush = now;
		post(text);
	}

	private void sendNow(String text) {
		long now = System.currentTimeMillis();
		if (now - lastSafety < 2000) return;
		lastSafety = now;
		post(text);
	}

	/** Only Discord webhook addresses are accepted, so the URL cannot be pointed somewhere else by mistake. */
	public static boolean validUrl(String u) {
		try {
			URI uri = URI.create(u);
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			boolean discord = host.equals("discord.com") || host.equals("discordapp.com") || host.endsWith(".discord.com") || host.endsWith(".discordapp.com");
			return "https".equals(uri.getScheme()) && discord && uri.getPath() != null && uri.getPath().startsWith("/api/webhooks/");
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private void post(String text) {
		String u = url.get().trim();
		if (!validUrl(u)) return;
		JsonObject body = new JsonObject();
		String content = "[" + Session.serverKey() + "] " + text;
		body.addProperty("content", content.length() > 1900 ? content.substring(0, 1900) : content);
		body.addProperty("username", name.get().isBlank() ? "Breez" : name.get());
		HttpRequest req = HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(10))
			.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding());
	}
}
