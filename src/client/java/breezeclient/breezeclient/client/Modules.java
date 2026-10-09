package breezeclient.breezeclient.client;

import breezeclient.breezeclient.client.modules.AmethystDetector;
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
	}

	public static void registerCommands(CommandManager c, ConfigManager config) {
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
