package breezeclient.breezeclient.client;

import breezeclient.breezeclient.client.modules.AmethystDetector;
import breezeclient.breezeclient.client.modules.AntiAfk;
import breezeclient.breezeclient.client.modules.AutoEat;
import breezeclient.breezeclient.client.modules.AutoReconnect;
import breezeclient.breezeclient.client.modules.AutoRelog;
import breezeclient.breezeclient.client.modules.KillSwitch;
import breezeclient.breezeclient.client.modules.StaffList;
import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.client.modules.CustomBlockEsp;
import breezeclient.breezeclient.client.modules.SpawnerEsp;
import breezeclient.breezeclient.client.modules.StorageEsp;
import breezeclient.breezeclient.client.world.BlockIds;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.command.Command;
import breezeclient.breezeclient.core.command.CommandManager;
import breezeclient.breezeclient.core.config.ConfigManager;

import java.util.function.Consumer;

/** Single place where every module and command is registered. */
public final class Modules {
	private Modules() {}

	public static CustomBlockEsp blockEsp;

	public static void registerAll(ModuleManager m) {
		m.register(new StorageEsp());
		m.register(new SpawnerEsp());
		m.register(new AmethystDetector());
		blockEsp = new CustomBlockEsp();
		m.register(blockEsp);
		m.register(new KillSwitch());
		m.register(new StaffList());
		m.register(new AutoRelog());
		m.register(new AutoReconnect());
		m.register(new AntiAfk());
		m.register(new AutoEat());
	}

	public static void registerCommands(CommandManager c, ConfigManager config) {
		c.register(new Command() {
			public String name() { return "staff"; }
			public String usage() { return "staff <add|remove|list|online|import <file>|clear> [name]"; }
			public void run(String[] a, Consumer<String> reply) {
				var profile = StaffTracker.profile();
				String sub = a.length == 0 ? "list" : a[0].toLowerCase();
				switch (sub) {
					case "list" -> reply.accept(profile.staff().isEmpty() ? "Staff list is empty." : "Staff: " + String.join(", ", profile.staff()));
					case "online" -> {
						var on = StaffTracker.onlineStaff();
						reply.accept(on.isEmpty() ? "No listed staff in the tab list." : "Online: " + String.join(", ", on));
					}
					case "add" -> {
						if (a.length < 2) { reply.accept("Usage: " + usage()); return; }
						int n = 0;
						for (int i = 1; i < a.length; i++) if (profile.addStaff(a[i])) n++;
						reply.accept("Added " + n);
						config.save();
					}
					case "remove" -> {
						if (a.length < 2) { reply.accept("Usage: " + usage()); return; }
						reply.accept(profile.removeStaff(a[1]) ? "Removed " + a[1] : "Not in list");
						config.save();
					}
					case "clear" -> {
						for (String s : new java.util.ArrayList<>(profile.staff())) profile.removeStaff(s);
						reply.accept("Cleared.");
						config.save();
					}
					case "import" -> {
						if (a.length < 2) { reply.accept("Usage: staff import <file with one name per line>"); return; }
						try {
							int n = 0;
							for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length))))) {
								String name = line.trim();
								if (!name.isEmpty() && !name.startsWith("#") && profile.addStaff(name)) n++;
							}
							reply.accept("Imported " + n + " names.");
							config.save();
						} catch (java.io.IOException e) {
							reply.accept("Could not read file: " + e.getMessage());
						}
					}
					default -> reply.accept("Usage: " + usage());
				}
			}
		});
		c.register(new Command() {
			public String name() { return "blockesp"; }
			public String usage() { return "blockesp <add|remove|list> [block id]"; }
			public void run(String[] a, Consumer<String> reply) {
				if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
					reply.accept("Blocks: " + String.join(", ", blockEsp.blocks.get()));
					return;
				}
				if (a.length < 2) { reply.accept("Usage: " + usage()); return; }
				if (BlockIds.parse(a[1]) == null) { reply.accept("Unknown block: " + a[1]); return; }
				String id = a[1].contains(":") ? a[1] : "minecraft:" + a[1];
				if (a[0].equalsIgnoreCase("add")) reply.accept(blockEsp.blocks.add(id) ? "Added " + id : "Already present");
				else if (a[0].equalsIgnoreCase("remove")) reply.accept(blockEsp.blocks.remove(id) ? "Removed " + id : "Not in list");
				else reply.accept("Usage: " + usage());
			}
		});
		c.register(new Command() {
			public String name() { return "waypoints"; }
			public String usage() { return "waypoints <list|add [name]|remove <n>|clear>"; }
			public void run(String[] a, Consumer<String> reply) {
				var mc = net.minecraft.client.Minecraft.getInstance();
				String sub = a.length == 0 ? "list" : a[0].toLowerCase();
				switch (sub) {
					case "list" -> {
						var l = Waypoints.forCurrentServer();
						if (l.isEmpty()) reply.accept("No waypoints for this server.");
						for (int i = 0; i < l.size(); i++) {
							var w = l.get(i);
							reply.accept(i + ": " + w.name() + " " + w.x() + " " + w.y() + " " + w.z());
						}
					}
					case "add" -> {
						if (mc.player == null) return;
						String name = a.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)) : "Waypoint";
						reply.accept(Waypoints.add(name, mc.player.blockPosition()) ? "Saved." : "Already saved here.");
					}
					case "remove" -> {
						if (a.length < 2) { reply.accept("Usage: " + usage()); return; }
						try {
							reply.accept(Waypoints.remove(Integer.parseInt(a[1])) ? "Removed." : "No such waypoint.");
						} catch (NumberFormatException e) {
							reply.accept("Not a number: " + a[1]);
						}
					}
					case "clear" -> {
						Waypoints.clearCurrent();
						reply.accept("Cleared.");
					}
					default -> reply.accept("Usage: " + usage());
				}
			}
		});
	}
}
