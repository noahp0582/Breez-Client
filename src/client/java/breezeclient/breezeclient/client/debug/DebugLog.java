package breezeclient.breezeclient.client.debug;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Collects every warning and error Breez Client logs (module crashes, packet-hook failures, failed downloads, config
 * problems) from the moment the client starts, keeps the last {@link #MAX} in memory and appends each one to
 * {@code breez-debug.log} in the game folder. Optionally also picks up Minecraft's own and other mods' warnings.
 * The Debug module and the {@code .debug} command read from here.
 */
public final class DebugLog {
	public enum Kind { WARN, ERROR }

	public record Entry(LocalTime time, Kind kind, String source, String message, String stack) {
		public String line() { return TIME.format(time) + " " + kind + " [" + source + "] " + message; }
	}

	public static final int MAX = 200;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
	private static final List<Consumer<Entry>> LISTENERS = new ArrayList<>();
	private static final ThreadLocal<Boolean> INSIDE = ThreadLocal.withInitial(() -> false);
	private static Path file;
	private static boolean installed;
	private static volatile boolean gameWarnings;
	private static Object gameAppender; // log4j appender, kept as Object so a missing log4j-core only disables that part

	private DebugLog() {}

	public static synchronized void install() {
		if (installed) return;
		installed = true;
		file = FabricLoader.getInstance().getGameDir().resolve("breez-debug.log");
		write("==== session started " + LocalDateTime.now().withNano(0) + " ====");
		try {
			attachHandler();
		} catch (RuntimeException e) { // a replaced java.util.logging manager may refuse handlers; never break startup over it
			write("could not attach to the breez-client logger: " + e);
		}
	}

	private static void attachHandler() {
		Logger.getLogger("breez-client").addHandler(new Handler() {
			@Override
			public void publish(LogRecord r) {
				if (r.getLevel().intValue() < Level.WARNING.intValue()) return;
				Kind kind = r.getLevel().intValue() >= Level.SEVERE.intValue() ? Kind.ERROR : Kind.WARN;
				String msg = r.getMessage();
				if (r.getParameters() != null && msg != null) {
					try { msg = java.text.MessageFormat.format(msg, r.getParameters()); } catch (IllegalArgumentException ignored) {}
				}
				record(kind, "Breez", msg, r.getThrown());
			}
			@Override public void flush() {}
			@Override public void close() {}
		});
	}

	/** Also record WARN/ERROR lines from Minecraft and other mods. Off by default because vanilla is chatty. */
	public static synchronized void setGameWarnings(boolean on) {
		gameWarnings = on;
		if (on && gameAppender == null) {
			try {
				gameAppender = GameAppender.attach();
			} catch (Throwable t) { // log4j-core missing or changed: keep Breez's own messages working
				record(Kind.WARN, "Debug", "Could not listen to Minecraft's log: " + t, null);
			}
		}
	}

	static boolean gameWarnings() { return gameWarnings; }

	/** Record a problem directly (for code that wants to report something without going through a logger). */
	public static void record(Kind kind, String source, String message, Throwable t) {
		if (INSIDE.get()) return; // a listener that logs while handling an entry must not loop
		INSIDE.set(true);
		try {
			String stack = "";
			if (t != null) {
				StringWriter sw = new StringWriter();
				t.printStackTrace(new PrintWriter(sw));
				stack = sw.toString();
				if (message == null || message.isBlank()) message = t.toString();
				else message = message + ": " + t;
			}
			Entry e = new Entry(LocalTime.now().withNano(0), kind, source, message == null ? "(no message)" : message, stack);
			List<Consumer<Entry>> ls;
			synchronized (DebugLog.class) {
				ENTRIES.addLast(e);
				while (ENTRIES.size() > MAX) ENTRIES.removeFirst();
				ls = new ArrayList<>(LISTENERS);
			}
			write(e.line() + (stack.isEmpty() ? "" : "\n" + stack.stripTrailing()));
			for (Consumer<Entry> l : ls) {
				try { l.accept(e); } catch (Throwable ignored) {}
			}
		} finally {
			INSIDE.set(false);
		}
	}

	public static synchronized List<Entry> entries() { return new ArrayList<>(ENTRIES); }

	public static synchronized int count(Kind kind) {
		int n = 0;
		for (Entry e : ENTRIES) if (e.kind() == kind) n++;
		return n;
	}

	public static synchronized void clear() { ENTRIES.clear(); }

	public static synchronized void listen(Consumer<Entry> l) { LISTENERS.add(l); }

	public static Path file() { return file; }

	/** A paste-ready report: versions, enabled modules and every recorded entry with its stack trace. */
	public static String report() {
		StringBuilder sb = new StringBuilder();
		sb.append("Breez Client debug report ").append(LocalDateTime.now().withNano(0)).append('\n');
		String version = FabricLoader.getInstance().getModContainer("breez-client")
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		sb.append("Breez ").append(version).append(", Minecraft ").append(SharedConstants.getCurrentVersion().name())
			.append(", Java ").append(System.getProperty("java.version")).append('\n');
		Minecraft mc = Minecraft.getInstance();
		var server = mc.getCurrentServer();
		sb.append("Server: ").append(server != null ? server.ip : mc.hasSingleplayerServer() ? "singleplayer" : "none").append('\n');
		List<String> on = new ArrayList<>();
		var modules = breezeclient.breezeclient.client.BreezClientClient.MODULES;
		if (modules != null) for (var m : modules.all()) if (m.enabled()) on.add(m.name());
		sb.append("Enabled modules: ").append(on.isEmpty() ? "none" : String.join(", ", on)).append('\n');
		List<Entry> list = entries();
		sb.append("Entries: ").append(list.size()).append("\n\n");
		for (Entry e : list) {
			sb.append(e.line()).append('\n');
			if (!e.stack().isEmpty()) sb.append(e.stack().stripTrailing()).append('\n');
		}
		return sb.toString();
	}

	private static void write(String text) {
		if (file == null) return;
		try {
			Files.writeString(file, text + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// the in-memory list still has it
		}
	}
}
