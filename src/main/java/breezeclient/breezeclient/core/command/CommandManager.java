package breezeclient.breezeclient.core.command;

import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.Setting;
import breezeclient.breezeclient.core.settings.StringListSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CommandManager {
	private final String prefix;
	private final ModuleManager modules;
	private final List<Command> commands = new ArrayList<>();

	public CommandManager(String prefix, ModuleManager modules) {
		this.prefix = prefix;
		this.modules = modules;
		register(new Simple("toggle", "toggle <module>", (a, r) -> {
			if (a.length < 1) { r.accept("Usage: " + prefix + "toggle <module>"); return; }
			Module m = modules.get(a[0]);
			if (m == null) { r.accept("No such module: " + a[0]); return; }
			m.toggle();
			r.accept(m.name() + (m.enabled() ? " enabled" : " disabled"));
		}));
		register(new Simple("bind", "bind <module> <key|none>", (a, r) -> {
			if (a.length < 2) { r.accept("Usage: " + prefix + "bind <module> <key|none>"); return; }
			Module m = modules.get(a[0]);
			if (m == null) { r.accept("No such module: " + a[0]); return; }
			int key = keyFromName(a[1]);
			if (key == Integer.MIN_VALUE) { r.accept("Unknown key: " + a[1]); return; }
			m.setKey(key);
			r.accept(m.name() + (key < 0 ? " unbound" : " bound to " + a[1].toUpperCase(Locale.ROOT)));
		}));
		register(new Simple("set", "set <module> [setting] [value|add x|remove x|clear]", (a, r) -> handleSet(a, r)));
		register(new Simple("modules", "modules", (a, r) -> r.accept(
			modules.all().stream().map(m -> (m.enabled() ? "+" : "-") + m.name()).collect(Collectors.joining(", ")))));
		register(new Simple("help", "help", (a, r) -> {
			for (Command c : commands) r.accept(prefix + c.usage());
		}));
	}

	public String prefix() { return prefix; }

	private static String key(String s) { return s.toLowerCase(Locale.ROOT).replace(" ", ""); }

	private void handleSet(String[] a, Consumer<String> r) {
		if (a.length < 1) { r.accept("Usage: " + prefix + "set <module> [setting] [value]"); return; }
		Module m = modules.get(a[0]);
		if (m == null) { r.accept("No such module: " + a[0]); return; }
		if (a.length == 1) {
			for (Setting<?> s : m.settings()) r.accept(key(s.name()) + " = " + s.toJson());
			return;
		}
		Setting<?> setting = null;
		for (Setting<?> s : m.settings()) if (key(s.name()).equals(key(a[1]))) setting = s;
		if (setting == null) { r.accept("No such setting: " + a[1]); return; }
		if (a.length == 2) { r.accept(key(setting.name()) + " = " + setting.toJson()); return; }
		String value = String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length));
		if (setting instanceof BoolSetting b) {
			String v = a[2].toLowerCase(Locale.ROOT);
			switch (v) {
				case "true", "on", "yes", "1" -> b.set(true);
				case "false", "off", "no", "0" -> b.set(false);
				case "toggle" -> b.toggle();
				default -> { r.accept("Expected on/off"); return; }
			}
		} else if (setting instanceof NumberSetting n) {
			try {
				n.set(Double.parseDouble(a[2]));
			} catch (NumberFormatException e) {
				r.accept("Not a number: " + a[2]);
				return;
			}
		} else if (setting instanceof EnumSetting<?> e) {
			e.fromJson(new com.google.gson.JsonPrimitive(a[2]));
		} else if (setting instanceof ColorSetting c) {
			String hex = a[2].replace("#", "").replace("0x", "");
			try {
				long v = Long.parseLong(hex, 16);
				c.set((int) (hex.length() <= 6 ? (0xFF000000L | v) : v));
			} catch (NumberFormatException ex) {
				r.accept("Expected hex color like ff8800");
				return;
			}
		} else if (setting instanceof breezeclient.breezeclient.core.settings.StringSetting str) {
			str.set(value);
		} else if (setting instanceof StringListSetting l) {
			String op = a[2].toLowerCase(Locale.ROOT);
			if (op.equals("clear")) l.set(java.util.List.of());
			else if (op.equals("list")) { r.accept(String.join(", ", l.get())); return; }
			else if (a.length < 4) { r.accept("Usage: " + prefix + "set " + m.name() + " " + key(l.name()) + " add|remove|clear|list [value]"); return; }
			else if (op.equals("add")) l.add(a[3]);
			else if (op.equals("remove")) l.remove(a[3]);
			else { r.accept("Expected add/remove/clear/list"); return; }
		} else {
			r.accept("Setting cannot be edited here");
			return;
		}
		r.accept(m.name() + "." + key(setting.name()) + " = " + setting.toJson());
	}

	public void register(Command c) { commands.add(c); }

	/** Returns true when the message starts with the prefix; such messages must never reach the server. */
	public boolean handle(String message, Consumer<String> reply) {
		if (message == null || !message.startsWith(prefix)) return false;
		String body = message.substring(prefix.length()).trim();
		if (body.isEmpty()) {
			reply.accept("Unknown command. Type " + prefix + "help.");
			return true;
		}
		String[] parts = body.split("\\s+");
		String name = parts[0];
		String[] args = new String[parts.length - 1];
		System.arraycopy(parts, 1, args, 0, args.length);
		for (Command c : commands) {
			if (c.name().equalsIgnoreCase(name)) {
				try {
					c.run(args, reply);
				} catch (RuntimeException e) {
					reply.accept("Command failed: " + e.getMessage());
				}
				return true;
			}
		}
		reply.accept("Unknown command. Type " + prefix + "help.");
		return true;
	}

	/** GLFW key code for a name; -1 for "none"; Integer.MIN_VALUE when unknown. */
	public static int keyFromName(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.equals("none")) return -1;
		if (n.length() == 1) {
			char c = n.charAt(0);
			if (c >= 'a' && c <= 'z') return 'A' + (c - 'a');
			if (c >= '0' && c <= '9') return c;
		}
		if (n.matches("f([1-9]|1[0-2])")) return 290 + Integer.parseInt(n.substring(1)) - 1;
		return switch (n) {
			case "rshift" -> 344;
			case "lshift" -> 340;
			case "rctrl" -> 345;
			case "lctrl" -> 341;
			case "ralt" -> 346;
			case "lalt" -> 342;
			case "space" -> 32;
			case "tab" -> 258;
			case "grave" -> 96;
			case "up" -> 265;
			case "down" -> 264;
			case "left" -> 263;
			case "right" -> 262;
			default -> Integer.MIN_VALUE;
		};
	}

	private record Simple(String name, String usage, Body body) implements Command {
		@Override public void run(String[] args, Consumer<String> reply) { body.run(args, reply); }
	}

	private interface Body {
		void run(String[] args, Consumer<String> reply);
	}
}
