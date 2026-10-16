package breezeclient.breezeclient.client;

import breezeclient.breezeclient.client.modules.AmethystDetector;
import breezeclient.breezeclient.client.modules.CombatTweaks;
import breezeclient.breezeclient.client.modules.DefenseModules;
import breezeclient.breezeclient.client.modules.ClientModules;
import breezeclient.breezeclient.client.modules.BaseFindingModules;
import breezeclient.breezeclient.client.modules.RenderModules;
import breezeclient.breezeclient.client.modules.DonutModules;
import breezeclient.breezeclient.client.modules.MovementModules;
import breezeclient.breezeclient.client.modules.UtilityModules;
import breezeclient.breezeclient.client.modules.ScoreboardName;
import breezeclient.breezeclient.client.modules.TunnelFinder;
import breezeclient.breezeclient.client.modules.AutoLog;
import breezeclient.breezeclient.client.modules.AlertWebhook;
import breezeclient.breezeclient.client.modules.ItemEsp;
import breezeclient.breezeclient.client.modules.ChestLooter;
import breezeclient.breezeclient.client.modules.PlayerLogger;
import breezeclient.breezeclient.client.modules.CoverageMap;
import breezeclient.breezeclient.client.modules.ArmorTrimHider;
import breezeclient.breezeclient.client.modules.MotionBlur;
import breezeclient.breezeclient.client.modules.CustomGlint;
import breezeclient.breezeclient.client.modules.HitBox;
import breezeclient.breezeclient.client.modules.NameTags;
import breezeclient.breezeclient.client.modules.CreamyKeys;
import breezeclient.breezeclient.client.modules.CustomAccessories;
import breezeclient.breezeclient.client.modules.Animations;
import breezeclient.breezeclient.client.modules.RegionMap;
import breezeclient.breezeclient.client.modules.BlockEntityEsp;
import breezeclient.breezeclient.client.modules.ClusterEsp;
import breezeclient.breezeclient.client.modules.CustomFov;
import breezeclient.breezeclient.client.modules.Zoom;
import breezeclient.breezeclient.client.modules.FreeLook;
import breezeclient.breezeclient.client.modules.SkinProtect;
import breezeclient.breezeclient.client.modules.AutoWalk;
import breezeclient.breezeclient.client.modules.AutoClicker;
import breezeclient.breezeclient.client.modules.AutoTpa;
import breezeclient.breezeclient.client.modules.FastUse;
import breezeclient.breezeclient.client.modules.JumpCircles;
import breezeclient.breezeclient.client.modules.HitParticles;
import breezeclient.breezeclient.client.modules.NameProtect;
import breezeclient.breezeclient.client.modules.CustomCrosshair;
import breezeclient.breezeclient.client.modules.AimAssist;
import breezeclient.breezeclient.client.modules.Triggerbot;
import breezeclient.breezeclient.client.modules.HoverTotem;
import breezeclient.breezeclient.client.modules.AnchorMacro;
import breezeclient.breezeclient.client.modules.DoubleAnchor;
import breezeclient.breezeclient.client.modules.ElytraSwap;
import breezeclient.breezeclient.client.modules.MaceModules;
import breezeclient.breezeclient.client.modules.CoordSnapper;
import breezeclient.breezeclient.client.modules.EntityStorageEsp;
import breezeclient.breezeclient.client.modules.HoleEsp;
import breezeclient.breezeclient.client.modules.MobEsp;
import breezeclient.breezeclient.client.modules.NewChunks;
import breezeclient.breezeclient.client.modules.PortalEsp;
import breezeclient.breezeclient.client.modules.SpawnerNametags;
import breezeclient.breezeclient.client.modules.SpawnerProtect;
import breezeclient.breezeclient.client.modules.StashFinder;
import breezeclient.breezeclient.client.modules.TrailFinder;
import breezeclient.breezeclient.client.modules.WeatherNotifier;
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
		m.register(new ClusterEsp());
		m.register(new CustomFov());
		m.register(new Zoom());
		m.register(new FreeLook());
		m.register(new SkinProtect());
		m.register(new AutoWalk());
		m.register(new AutoClicker());
		m.register(new AutoTpa());
		m.register(new FastUse());
		m.register(new JumpCircles());
		m.register(new HitParticles());
		m.register(new NameProtect());
		m.register(new CustomCrosshair());
		m.register(new AimAssist());
		m.register(new Triggerbot());
		m.register(new HoverTotem());
		m.register(new AnchorMacro());
		m.register(new DoubleAnchor());
		m.register(new ElytraSwap());
		m.register(new MaceModules.MaceSwap());
		m.register(new MaceModules.MaceBomber());
		m.register(new MaceModules.ShieldBreaker());
		m.register(new DefenseModules.AutoDoubleHand());
		m.register(new DefenseModules.AutoInvTotem());
		m.register(new DefenseModules.TotemOffhand());
		m.register(new DefenseModules.AutoJumpReset());
		m.register(new breezeclient.breezeclient.client.modules.AutoShield());
		m.register(new CombatTweaks.NoHitDelay());
		m.register(new CombatTweaks.CrystalOptimizer());
		CombatTweaks.CrystalOptimizer.registerEvents();
		m.register(new CombatTweaks.StaticHitBoxes());
		m.register(new CombatTweaks.SpearSwap());
		CombatTweaks.SpearSwap.registerEvents();
		m.register(new CombatTweaks.AutoHitCrystal());
		m.register(new MovementModules.Sprint());
		m.register(new MovementModules.ElytraGlide());
		m.register(new MovementModules.AutoFirework());
		m.register(new MovementModules.KeyPearl());
		m.register(new MovementModules.KeyWindCharge());
		m.register(new UtilityModules.AutoMine());
		m.register(new UtilityModules.AutoTool());
		m.register(new UtilityModules.Fakeplayer());
		m.register(new UtilityModules.ItemDropper());
		m.register(new UtilityModules.AntiTrap());
		m.register(new DonutModules.AutoSell());
		m.register(new DonutModules.AutoSpawnerSell());
		m.register(new DonutModules.ChunkFinder());
		m.register(new DonutModules.NetheriteFinder());
		m.register(new DonutModules.PlayerChunks());
		m.register(new RenderModules.BlockNotifier());
		m.register(new RenderModules.PearlTrajectory());
		m.register(new RenderModules.RealHitBox());
		m.register(new RenderModules.TargetHud());
		RenderModules.TargetHud.registerEvents();
		m.register(new RenderModules.MusicHud());
		RenderModules.MusicHud.registerEvents();
		m.register(new BaseFindingModules.BlockEntityDebug());
		BaseFindingModules.BlockEntityDebug.registerHud();
		m.register(new BaseFindingModules.LightFinder());
		m.register(new BaseFindingModules.SuspiciousEsp());
		m.register(new BaseFindingModules.TunnelBaseFinder());
		m.register(new BaseFindingModules.PrimeChunkFinder());
		m.register(new BaseFindingModules.RtpBaseFinder());
		m.register(new BaseFindingModules.SeedChunkFinder());
		m.register(new ClientModules.DiscordPresence());
		m.register(new ClientModules.ProxyModule());
		m.register(new ClientModules.Radio());
		m.register(new ArmorTrimHider());
		m.register(new ScoreboardName());
		m.register(new TunnelFinder());
		m.register(new CoverageMap());
		m.register(new AutoLog());
		m.register(new AlertWebhook());
		AlertWebhook.registerEvents();
		m.register(new ItemEsp());
		m.register(new ChestLooter());
		ChestLooter.registerEvents();
		m.register(new PlayerLogger());
		CoverageMap.registerEvents();
		m.register(new MotionBlur());
		m.register(new CustomGlint());
		m.register(new HitBox());
		m.register(new NameTags());
		m.register(new CreamyKeys());
		m.register(new CustomAccessories());
		m.register(new Animations());
		m.register(new RegionMap());
		NameTags.registerEvents();
		ScoreboardName.registerEvents();
		RegionMap.registerEvents();
		HitParticles.registerEvents();
		NameProtect.registerEvents();
		CustomCrosshair.registerEvents();
		MaceModules.MaceSwap.registerEvents();
		m.register(new breezeclient.breezeclient.client.nav.NavModule());
		m.register(new NewChunks());
		m.register(new TrailFinder());
		m.register(new StashFinder());
		m.register(new BlockEntityEsp());
		m.register(new EntityStorageEsp());
		m.register(new PortalEsp());
		m.register(new HoleEsp());
		m.register(new SpawnerNametags());
		m.register(new MobEsp());
		m.register(new WeatherNotifier());
		m.register(new CoordSnapper());
		m.register(new SpawnerProtect());
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
		digger = new breezeclient.breezeclient.client.modules.AutoDig();
		m.register(digger);
	}

	/** Switches off everything that acts on its own (pathing, mining, building, macros, camera). Call on disconnect and when a safety fires. */
	public static void stopAutomation() {
		if (spawnerPickup != null) spawnerPickup.setEnabled(false);
		if (builder != null) builder.setEnabled(false);
		for (var m : BreezClientClient.macros.all()) m.setEnabled(false);
		for (String n : new String[] {"AutoMine", "AutoDig", "AutoWalk", "ItemDropper", "AutoSell", "AutoSpawnerSell", "RTPBaseFinder", "ElytraGlide"}) {
			var mod = BreezClientClient.MODULES.get(n);
			if (mod != null) mod.setEnabled(false);
		}
		Freecam.reset();
		breezeclient.breezeclient.client.nav.Baritone.stop(null);
	}

	public static SpawnerPickup spawnerPickup;
	public static SeedOres seedOres;
	public static SchematicBuilder builder;
	public static breezeclient.breezeclient.client.modules.AutoDig digger;

	public static void registerCommands(CommandManager c, ConfigManager config) {
		breezeclient.breezeclient.client.nav.NavCommands.register(c);
		c.register(new Command() {
			public String name() { return "detection"; }
			public String usage() { return "detection [undetected|risk|detected]"; }
			public void run(String[] a, Consumer<String> reply) {
				var all = breezeclient.breezeclient.client.BreezClientClient.MODULES.all();
				if (a.length == 0) {
					for (var d : breezeclient.breezeclient.core.Detection.values()) {
						java.util.List<String> names = new java.util.ArrayList<>();
						for (var m : all) if (m.detection() == d) names.add(m.name());
						if (d == breezeclient.breezeclient.core.Detection.UNDETECTED) reply.accept(d.label() + ": " + names.size() + " modules (" + c.prefix() + "detection undetected lists them)");
						else reply.accept(d.label() + " (" + names.size() + "): " + (names.isEmpty() ? "-" : String.join(", ", names)));
					}
					return;
				}
				var d = breezeclient.breezeclient.core.Detection.parse(String.join(" ", a));
				if (d == null) { reply.accept("Usage: " + usage()); return; }
				java.util.List<String> names = new java.util.ArrayList<>();
				for (var m : all) if (m.detection() == d) names.add(m.name());
				java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
				reply.accept(d.label() + " (" + names.size() + "): " + String.join(", ", names));
			}
		});
		c.register(new Command() {
			public String name() { return "prime"; }
			public String usage() { return "prime [count]"; }
			public void run(String[] a, Consumer<String> reply) {
				int n = 10;
				if (a.length > 0) {
					try { n = Math.max(1, Math.min(50, Integer.parseInt(a[0]))); } catch (NumberFormatException e) { reply.accept("Usage: " + usage()); return; }
				}
				var top = BaseFindingModules.PrimeChunkFinder.top(n);
				if (top.isEmpty()) reply.accept("No prime chunks (is PrimeChunkFinder enabled?).");
				for (var r : top) reply.accept("score " + r.score() + " chunk (" + r.chunkX() + "," + r.chunkZ() + ") at " + (r.chunkX() * 16 + 8) + " " + r.y() + " " + (r.chunkZ() * 16 + 8) + ": " + String.join(", ", r.reasons()));
			}
		});
		c.register(new Command() {
			public String name() { return "chunkfinder"; }
			public String usage() { return "chunkfinder [count]"; }
			public void run(String[] a, Consumer<String> reply) {
				int n = 10;
				if (a.length > 0) {
					try { n = Math.max(1, Math.min(50, Integer.parseInt(a[0]))); } catch (NumberFormatException e) { reply.accept("Usage: " + usage()); return; }
				}
				var top = DonutModules.ChunkFinder.top(n);
				if (top.isEmpty()) reply.accept("No chunks flagged (is ChunkFinder enabled?).");
				for (var r : top) { var p = r.center(); reply.accept(r.count() + " blocks, chunk (" + r.chunkX() + "," + r.chunkZ() + ") at " + p.getX() + " " + p.getY() + " " + p.getZ()); }
			}
		});
		c.register(new Command() {
			public String name() { return "chunks"; }
			public String usage() { return "chunks <stats|export|clear>"; }
			public void run(String[] a, Consumer<String> reply) {
				var age = breezeclient.breezeclient.client.world.ChunkAgeTracker.current();
				String sub = a.length == 0 ? "stats" : a[0].toLowerCase();
				switch (sub) {
					case "stats" -> reply.accept(age.count(breezeclient.breezeclient.core.world.ChunkAge.State.NEW) + " new chunks, " + age.count(breezeclient.breezeclient.core.world.ChunkAge.State.OLD) + " old chunks recorded in this dimension.");
					case "export" -> {
						try {
							reply.accept("Wrote " + breezeclient.breezeclient.client.world.ChunkAgeTracker.export());
						} catch (java.io.IOException e) {
							reply.accept("Could not export: " + e.getMessage());
						}
					}
					case "clear" -> {
						breezeclient.breezeclient.client.world.ChunkAgeTracker.clearCurrent();
						reply.accept("Chunk records for this dimension cleared.");
					}
					default -> reply.accept("Usage: " + usage());
				}
			}
		});
		c.register(new Command() {
			public String name() { return "stash"; }
			public String usage() { return "stash <list [n]|export|clear>"; }
			public void run(String[] a, Consumer<String> reply) {
				String sub = a.length == 0 ? "list" : a[0].toLowerCase();
				switch (sub) {
					case "list" -> {
						int n = 10;
						if (a.length > 1) {
							try { n = Math.max(1, Math.min(50, Integer.parseInt(a[1]))); } catch (NumberFormatException e) { reply.accept("Usage: " + usage()); return; }
						}
						var top = StashFinder.top(n);
						if (top.isEmpty()) reply.accept("No stashes found (is StashFinder enabled?).");
						for (var r : top) reply.accept(String.format("score %.0f at %d %d %d: %s", r.score(), r.center().getX(), r.center().getY(), r.center().getZ(), r.summary()));
					}
					case "export" -> reply.accept(java.nio.file.Files.exists(StashFinder.csv()) ? "Finds are logged in " + StashFinder.csv() : "No finds logged yet.");
					case "clear" -> {
						StashFinder.clear();
						reply.accept("Stash list and log cleared.");
					}
					default -> reply.accept("Usage: " + usage());
				}
			}
		});

		c.register(new Command() {
			public String name() { return "geodes"; }
			public String usage() { return "geodes [count|reset]"; }
			public void run(String[] a, Consumer<String> reply) {
				if (a.length > 0 && a[0].equalsIgnoreCase("reset")) {
					AmethystDetector.resetNotified();
					reply.accept("Geode notifications reset.");
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
				var list = AmethystDetector.geodes();
				if (list.isEmpty()) reply.accept("No geodes in range (is AmethystDetector enabled?).");
				for (var g : list.subList(0, Math.min(n, list.size()))) {
					reply.accept(String.format("%.0f%% grown, %d clusters %d large %d medium %d small, %d open, ~%.1fh at %d %d %d", g.grownPercent(), g.cluster(), g.large(), g.medium(), g.small(), g.openSlots(), g.estHours(), g.center().x(), g.center().y(), g.center().z()));
				}
			}
		});
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
			public String usage() { return "build <auto [file]|files|load <file>|origin [x y z]|start|pause|stop|status>"; }
			public void run(String[] a, Consumer<String> reply) {
				var mc = net.minecraft.client.Minecraft.getInstance();
				String sub = a.length == 0 ? "status" : a[0].toLowerCase();
				switch (sub) {
					case "auto" -> {
						String err = builder.buildNow(a.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)) : null);
						reply.accept(err == null ? "Building " + builder.status() : "Could not start: " + err);
					}
					case "files" -> {
						var f = SchematicBuilder.files();
						reply.accept(f.isEmpty() ? "No schematics found. Put .litematic, .schem, .schematic or .nbt files next to the mod jar (subfolders count)." : "Found " + f.size() + " schematic" + (f.size() == 1 ? "" : "s") + ": " + String.join(", ", f));
						for (String line : SchematicBuilder.describeFolders()) reply.accept("  " + line);
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
			public String name() { return "dig"; }
			public String usage() { return "dig <mark|pos1|pos2|start|stop|clear|status>"; }
			public void run(String[] a, Consumer<String> reply) {
				var mc = net.minecraft.client.Minecraft.getInstance();
				String sub = a.length == 0 ? "status" : a[0].toLowerCase();
				switch (sub) {
					case "mark" -> digger.toggleMarking();
					case "pos1", "pos2" -> {
						if (mc.player == null) { reply.accept("Join a world first."); return; }
						net.minecraft.core.BlockPos at = mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh && bh.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
							? bh.getBlockPos() : mc.player.blockPosition();
						digger.setCorner(sub.equals("pos1") ? 1 : 2, at);
						reply.accept("Corner " + sub.charAt(3) + " set to " + at.toShortString() + ". " + digger.status());
					}
					case "start" -> {
						digger.setEnabled(false);
						digger.setEnabled(true);
						if (digger.enabled()) reply.accept(digger.status());
					}
					case "stop", "pause" -> digger.setEnabled(false);
					case "clear" -> {
						digger.setEnabled(false);
						digger.clearCorners();
						reply.accept("Area cleared.");
					}
					case "status" -> reply.accept(digger.status());
					default -> reply.accept("Usage: " + usage());
				}
			}
		});
		c.register(new Command() {
			public String name() { return "coverage"; }
			public String usage() { return "coverage [stats|export|clear]"; }
			public void run(String[] a, Consumer<String> reply) {
				String sub = a.length == 0 ? "stats" : a[0].toLowerCase();
				switch (sub) {
					case "stats" -> reply.accept("Chunks seen on this server and dimension: " + breezeclient.breezeclient.client.world.CoverageTracker.count());
					case "clear" -> {
						breezeclient.breezeclient.client.world.CoverageTracker.clearCurrent();
						reply.accept("Coverage cleared for this server and dimension.");
					}
					case "export" -> {
						try {
							reply.accept("Saved " + breezeclient.breezeclient.client.world.CoverageTracker.export());
						} catch (java.io.IOException e) {
							reply.accept("Could not write the file: " + e.getMessage());
						}
					}
					default -> reply.accept("Usage: " + usage());
				}
			}
		});
		c.register(new Command() {
			public String name() { return "friend"; }
			public String usage() { return "friend <add|remove> <name> | friend list"; }
			public void run(String[] a, Consumer<String> reply) {
				var list = breezeclient.breezeclient.client.gui.GuiState.CLIENT.friends;
				String sub = a.length == 0 ? "list" : a[0].toLowerCase();
				switch (sub) {
					case "list" -> reply.accept(list.get().isEmpty() ? "No friends yet. " + usage() : "Friends: " + String.join(", ", list.get()));
					case "add" -> reply.accept(a.length > 1 ? (list.add(a[1]) ? "Added " + a[1] : a[1] + " is already a friend.") : "Usage: " + usage());
					case "remove" -> reply.accept(a.length > 1 ? (list.remove(a[1]) ? "Removed " + a[1] : a[1] + " is not a friend.") : "Usage: " + usage());
					default -> reply.accept("Usage: " + usage());
				}
			}
		});
		c.register(new Command() {
			public String name() { return "players"; }
			public String usage() { return "players [name]"; }
			public void run(String[] a, Consumer<String> reply) {
				if (a.length > 0) {
					var s = PlayerLogger.find(a[0]);
					reply.accept(s == null ? "No sighting of " + a[0] + " yet (is PlayerLogger on?)."
						: s.name() + " last seen at " + s.x() + " " + s.y() + " " + s.z() + " in " + s.dim()
						+ ", " + (System.currentTimeMillis() - s.time()) / 1000 + " s ago");
					return;
				}
				var recent = PlayerLogger.recent();
				if (recent.isEmpty()) reply.accept("No players seen yet (turn PlayerLogger on).");
				for (var s : recent.subList(0, Math.min(15, recent.size()))) {
					reply.accept(s.name() + "  " + s.x() + " " + s.y() + " " + s.z() + "  " + (System.currentTimeMillis() - s.time()) / 1000 + " s ago");
				}
			}
		});
		c.register(new Command() {
			public String name() { return "alerts"; }
			public String usage() { return "alerts [count]"; }
			public void run(String[] a, Consumer<String> reply) {
				int n = 10;
				if (a.length > 0) {
					try {
						n = Math.max(1, Math.min(100, Integer.parseInt(a[0])));
					} catch (NumberFormatException e) {
						reply.accept("Usage: " + usage());
						return;
					}
				}
				var all = breezeclient.breezeclient.client.gui.Toasts.history();
				if (all.isEmpty()) reply.accept("No alerts yet.");
				for (String line : all.subList(Math.max(0, all.size() - n), all.size())) reply.accept(line);
			}
		});
		c.register(new Command() {
			public String name() { return "gui"; }
			public String usage() { return "gui [Modules|Config|GUI|Macros|Profiles|Waypoints|HUD]"; }
			public void run(String[] a, Consumer<String> reply) {
				String tab = a.length > 0 ? a[0] : breezeclient.breezeclient.client.gui.GuiState.lastTab;
				// opened on the next tick: the chat screen is still closing while this command runs
				net.minecraft.client.Minecraft.getInstance().execute(() -> breezeclient.breezeclient.client.gui.Tabs.open(tab));
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
					reply.accept(String.format("%.0f  %s  chunk (%d,%d) at %d %d %d", r.score(), r.label(), r.chunkX(), r.chunkZ(), p.getX(), p.getY(), p.getZ()));
					if (r.all().size() > 1) for (String line : r.all()) reply.accept("    - " + line);
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
