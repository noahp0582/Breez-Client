package breezeclient.breezeclient.client;

import breezeclient.breezeclient.client.modules.AmethystDetector;
import breezeclient.breezeclient.client.modules.AntiAfk;
import breezeclient.breezeclient.client.modules.AutoEat;
import breezeclient.breezeclient.client.modules.AutoReconnect;
import breezeclient.breezeclient.client.modules.AutoRelog;
import breezeclient.breezeclient.client.modules.Freecam;
import breezeclient.breezeclient.client.modules.Fullbright;
import breezeclient.breezeclient.client.modules.CrystalMacro;
import breezeclient.breezeclient.client.modules.KillSwitch;
import breezeclient.breezeclient.client.modules.SchematicBuilder;
import breezeclient.breezeclient.client.modules.SeedOres;
import breezeclient.breezeclient.client.modules.SpawnerPickup;
import breezeclient.breezeclient.client.modules.SusChunks;
import breezeclient.breezeclient.client.modules.TotemMacro;
import breezeclient.breezeclient.client.modules.OreFinder;
import breezeclient.breezeclient.client.modules.PlayerEsp;
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
		m.register(new Freecam());
		m.register(new Fullbright());
		m.register(new PlayerEsp());
		m.register(new OreFinder());
		m.register(new SusChunks());
		m.register(new CrystalMacro());
		m.register(new TotemMacro());
		spawnerPickup = new SpawnerPickup();
		m.register(spawnerPickup);
		seedOres = new SeedOres();
		m.register(seedOres);
		builder = new SchematicBuilder();
		m.register(builder);
	}

	/** Switches off everything that acts on its own (pathing, mining, building, macros, camera). Call on disconnect and when a safety fires. */
	public static void stopAutomation() {
		if (spawnerPickup != null) spawnerPickup.setEnabled(false);
		if (builder != null) builder.setEnabled(false);
		for (var m : BreezClientClient.macros.all()) m.setEnabled(false);
		Freecam.reset();
	}

	public static SpawnerPickup spawnerPickup;
	public static SeedOres seedOres;
	public static SchematicBuilder builder;

	public static void registerCommands(CommandManager c, ConfigManager config) {
		c.register(new Command() {
			public String name() { return "seed"; }
			public String usage() { return "seed [number or text | clear | verify]"; }
			public void run(String[] a, Consumer<String> reply) {
				var profile = StaffTracker.profile();
				if (a.length == 0) {
					reply.accept(profile.seed() == null ? "No seed set for this server. Use " + c.prefix() + "seed <value>." : "Seed: " + profile.seed());
					return;
				}
				if (a[0].equalsIgnoreCase("verify")) {
					for (String line : seedOres.verify().split("\n")) reply.accept(line);
					return;
				}
				if (a[0].equalsIgnoreCase("clear")) {
					profile.setSeed(null);
					config.save();
					reply.accept("Seed cleared.");
					return;
				}
				profile.setSeed(String.join(" ", a));
				config.save();
				reply.accept("Seed set. Enable SeedOres to see predicted ores.");
			}
		});
		c.register(new Command() {
			public String name() { return "spawner"; }
			public String usage() { return "spawner <chest|start|stop>   (chest = set ender chest to the block you look at)"; }
			public void run(String[] a, Consumer<String> reply) {
				var mc = net.minecraft.client.Minecraft.getInstance();
				String sub = a.length == 0 ? "" : a[0].toLowerCase();
				switch (sub) {
					case "chest" -> {
						if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh && mc.level != null
							&& mc.level.getBlockState(bh.getBlockPos()).is(net.minecraft.world.level.block.Blocks.ENDER_CHEST)) {
							spawnerPickup.setChest(bh.getBlockPos());
							config.save();
							reply.accept("Ender chest set to " + bh.getBlockPos().toShortString());
						} else {
							reply.accept("Look at an ender chest first.");
						}
					}
					case "start" -> spawnerPickup.setEnabled(true);
					case "stop" -> spawnerPickup.setEnabled(false);
					default -> reply.accept("Usage: " + usage());
				}
			}
		});
		c.register(new Command() {
			public String name() { return "build"; }
			public String usage() { return "build <files|load <file>|origin [x y z]|start|pause|stop|status>"; }
			public void run(String[] a, Consumer<String> reply) {
				var mc = net.minecraft.client.Minecraft.getInstance();
				String sub = a.length == 0 ? "status" : a[0].toLowerCase();
				switch (sub) {
					case "files" -> {
						var f = SchematicBuilder.files();
						reply.accept(f.isEmpty() ? "No schematics in " + SchematicBuilder.dir() : String.join(", ", f));
					}
					case "load" -> {
						if (a.length < 2) { reply.accept("Usage: " + usage()); return; }
						String err = builder.load(String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
						reply.accept(err == null ? "Loaded. Set the origin with " + c.prefix() + "build origin, then " + c.prefix() + "build start." : "Could not load: " + err);
					}
					case "origin" -> {
						if (a.length >= 4) {
							try {
								builder.setOrigin(new net.minecraft.core.BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3])));
							} catch (NumberFormatException e) {
								reply.accept("Coordinates must be whole numbers.");
								return;
							}
						} else if (mc.player != null) {
							builder.setOrigin(mc.player.blockPosition());
						}
						reply.accept("Origin (minimum corner) set to " + (builder.origin() == null ? "?" : builder.origin().toShortString()));
					}
					case "start" -> builder.setEnabled(true);
					case "pause", "stop" -> builder.setEnabled(false);
					case "status" -> reply.accept(builder.status());
					default -> reply.accept("Usage: " + usage());
				}
			}
		});
		c.register(new Command() {
			public String name() { return "sus"; }
			public String usage() { return "sus [count|reset]"; }
			public void run(String[] a, Consumer<String> reply) {
				if (a.length > 0 && a[0].equalsIgnoreCase("reset")) {
					SusChunks.resetAnnounced();
					reply.accept("Sus chunk announcements reset.");
					return;
				}
				int n = 10;
				if (a.length > 0) {
					try {
						n = Math.max(1, Math.min(50, Integer.parseInt(a[0])));
					} catch (NumberFormatException e) {
						reply.accept("Usage: " + usage());
						return;
					}
				}
				var top = SusChunks.top(n);
				if (top.isEmpty()) reply.accept("No suspicious chunks (is SusChunks enabled?).");
				for (var r : top) {
					var p = r.center();
					reply.accept(String.format("score %.1f chunk (%d,%d) at %d %d %d", r.score(), r.chunkX(), r.chunkZ(), p.getX(), p.getY(), p.getZ()));
				}
			}
		});
		c.register(new Command() {
			public String name() { return "macro"; }
			public String usage() { return "macro <add <name> <script>|remove <name>|list|show <name>|run <name>|stop>"; }
			public void run(String[] a, Consumer<String> reply) {
				var store = BreezClientClient.macros;
				String sub = a.length == 0 ? "list" : a[0].toLowerCase();
				switch (sub) {
					case "list" -> {
						if (store.all().isEmpty()) reply.accept("No macros. Try: " + c.prefix() + "macro add hello say hi; wait 20; say there");
						for (var m : store.all()) reply.accept(m.name() + (m.key() >= 0 ? " [key " + m.key() + "]" : ""));
					}
					case "add" -> {
						if (a.length < 3) { reply.accept("Usage: " + usage()); return; }
						String err = store.add(a[1], String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)));
						reply.accept(err == null ? "Saved. Bind with " + c.prefix() + "bind " + a[1] + " <key>" : err);
					}
					case "remove" -> reply.accept(a.length > 1 && store.remove(a[1]) ? "Removed." : "No such macro.");
					case "show" -> {
						var m = a.length > 1 ? store.find(a[1]) : null;
						reply.accept(m == null ? "No such macro." : m.script());
					}
					case "run" -> {
						var m = a.length > 1 ? store.find(a[1]) : null;
						if (m == null) { reply.accept("No such macro."); return; }
						m.setEnabled(false);
						m.setEnabled(true);
					}
					case "stop" -> {
						for (var m : store.all()) m.setEnabled(false);
						reply.accept("Stopped.");
					}
					default -> reply.accept("Usage: " + usage());
				}
			}
		});
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
