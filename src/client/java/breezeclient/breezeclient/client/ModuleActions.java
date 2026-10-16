package breezeclient.breezeclient.client;

import breezeclient.breezeclient.client.chat.Cmd;
import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.modules.ProfileSeedSetting;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.Setting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.settings.StringSetting;

import java.util.List;

/**
 * Puts everything that used to need a typed chat command into the settings page of the module it belongs to, as buttons
 * and fields in an "Actions" section. Each button runs the same command the chat would, through {@link Cmd}.
 */
public final class ModuleActions {
	private static final String G = "Actions";

	private ModuleActions() {}

	private static void act(Module m, String name, String description, String command) {
		m.addAction(name, description, () -> Cmd.run(command));
	}

	/** A button that runs "prefix <text of the field>", or tells you to fill the field in first. */
	private static void actWith(Module m, String name, String description, StringSetting field, String prefix, String hint) {
		m.addAction(name, description, () -> {
			String v = field.get().trim();
			if (v.isEmpty()) {
				Toasts.show(m.name(), hint);
				return;
			}
			Cmd.run(prefix + " " + v);
		});
	}

	private static StringSetting find(Module m, String name) {
		for (Setting<?> s : m.settings()) if (s.name().equals(name) && s instanceof StringSetting str) return str;
		return null;
	}

	public static void install(ModuleManager mm) {
		Module m;


		if ((m = mm.get("PrimeChunkFinder")) != null) {
			act(m, "List the best chunks", "Same as .prime.", "prime 10");
		}

		if ((m = mm.get("ChunkFinder")) != null) {
			act(m, "List the richest chunks", "Same as .chunkfinder.", "chunkfinder 10");
		}

		if ((m = mm.get("SpawnerPickup")) != null) {
			act(m, "Set ender chest to the block I'm looking at", "Look at your ender chest, then click. Same as .spawner chest.", "spawner chest");
		}

		if ((m = mm.get("SchematicBuilder")) != null) {
			final Module mod = m;
			StringSetting file = find(m, "File");
			m.addAction("Load and build at my crosshair", "Loads the File named below (or the newest schematic next to the mod jar), puts its corner on the block face you look at and starts building. Same as .build auto.", () -> {
				String err = ((breezeclient.breezeclient.client.modules.SchematicBuilder) mod).buildNow(file == null ? null : file.get().trim());
				if (err != null) Toasts.show(mod.name(), "Could not start: " + err);
			});
			act(m, "List schematic files", "Shows the schematics found next to the mod jar and in the other search folders. Same as .build files.", "build files");
			if (file != null) {
				m.addAction("Load the file named above", "Reads the schematic named in the File field. Same as .build load <file>.", () -> {
					if (file.get().isBlank()) Toasts.show(mod.name(), "Type a file name in the File field first (see List schematic files).");
					else Cmd.run("build load " + file.get().trim());
				});
			}
			act(m, "Set origin to my position", "The build's minimum corner becomes the block you stand on. Same as .build origin.", "build origin");
			StringSetting origin = m.addSetting(new StringSetting("Origin (x y z)", ""), G, "Minimum corner of the build as three whole numbers, e.g. 100 64 -200.");
			actWith(m, "Set origin to the coordinates above", "Same as .build origin <x y z>.", origin, "build origin", "Type three numbers in Origin (x y z) first.");
			act(m, "Show build status", "Same as .build status.", "build status");
			act(m, "Pause the build", "Same as .build pause. Start again with the module switch.", "build pause");
		}

		if ((m = mm.get("AutoDig")) != null) {
			act(m, "Mark corners with right-click", "Then right-click one corner block and the opposite one. Those clicks place and open nothing and are never sent. Click again to stop. Same as .dig mark.", "dig mark");
			act(m, "Set corner 1 to the block I look at", "Look at one corner of the area, then click. Same as .dig pos1.", "dig pos1");
			act(m, "Set corner 2 to the block I look at", "Look at the opposite corner, then click. Same as .dig pos2.", "dig pos2");
			act(m, "Start digging", "Same as .dig start.", "dig start");
			act(m, "Show status", "Same as .dig status.", "dig status");
			act(m, "Clear the area", "Stops and forgets both corners. Same as .dig clear.", "dig clear");
		}

		if ((m = mm.get("SeedOres")) != null) {
			m.addSetting(new ProfileSeedSetting(), "Seed", "The real world seed of the server you are on (each server keeps its own). Numbers or any text.");
			act(m, "Verify against this world", "Singleplayer only: compares the predictions with the real blocks. Same as .seed verify.", "seed verify");
			act(m, "Clear the seed", "Same as .seed clear.", "seed clear");
		}

		if ((m = mm.get("NewChunks")) != null) {
			act(m, "Show counts", "How many new and old chunks are recorded here. Same as .chunks stats.", "chunks stats");
			act(m, "Export to a CSV file", "Same as .chunks export.", "chunks export");
			act(m, "Clear records for this dimension", "Same as .chunks clear.", "chunks clear");
		}

		if ((m = mm.get("StashFinder")) != null) {
			act(m, "List the best finds", "Same as .stash list.", "stash list");
			act(m, "Show where the log file is", "Same as .stash export.", "stash export");
			act(m, "Clear the list and the log", "Same as .stash clear.", "stash clear");
		}

		if ((m = mm.get("AmethystDetector")) != null) {
			act(m, "List geodes", "Same as .geodes.", "geodes");
			act(m, "Reset notifications", "Geodes that already notified you can notify again. Same as .geodes reset.", "geodes reset");
		}

		if ((m = mm.get("SusChunks")) != null) {
			act(m, "List the top chunks", "Same as .sus.", "sus 10");
			act(m, "Reset announcements", "Chunks that already notified you can notify again. Same as .sus reset.", "sus reset");
		}

		if ((m = mm.get("CoverageMap")) != null) {
			act(m, "Show how many chunks you have seen", "Same as .coverage stats.", "coverage stats");
			act(m, "Export to a CSV file", "Same as .coverage export.", "coverage export");
			act(m, "Clear coverage for this dimension", "Same as .coverage clear.", "coverage clear");
		}

		if ((m = mm.get("StaffList")) != null) {
			act(m, "Show staff online now", "Same as .staff online.", "staff online");
			StringSetting path = m.addSetting(new StringSetting("Import file", ""), G, "Path of a text file with one name per line (lines starting with # are skipped).");
			actWith(m, "Import names from the file above", "Same as .staff import <file>.", path, "staff import", "Type the path of a text file in Import file first.");
			act(m, "Clear the staff list", "Removes every name on this server's list. Same as .staff clear.", "staff clear");
		}

		if ((m = mm.get("PlayerLogger")) != null) {
			act(m, "List recently seen players", "Same as .players.", "players");
			StringSetting who = m.addSetting(new StringSetting("Player name", ""), G, "A player to look up.");
			actWith(m, "Where was this player last seen?", "Same as .players <name>.", who, "players", "Type a player name in Player name first.");
		}

		if ((m = mm.get("Baritone")) != null) {
			final Module mod = m;
			act(m, "Stop", "Cancels the current task. Same as .stop.", "stop");
			act(m, "Pause", "Same as .pause.", "pause");
			act(m, "Resume", "Same as .resume.", "resume");
			act(m, "Show status", "Same as .pathstatus.", "pathstatus");
			act(m, "Head for the surface", "Same as .surface.", "surface");
			act(m, "Explore", "Walks toward unseen chunks. Same as .explore.", "explore");

			StringSetting target = m.addSetting(new StringSetting("Go to", ""), G, "x y z, x z, a waypoint name, or a player name.");
			actWith(m, "Go to the target above", "Same as .goto <target>.", target, "goto", "Type coordinates, a waypoint or a player in Go to first.");

			StringListSetting blocks = m.addSetting(new StringListSetting("Mine: blocks", List.of("minecraft:diamond_ore")).kind(StringListSetting.Kind.BLOCK),
				G, "Blocks to mine. Containers, spawners and the protected list are never broken.");
			NumberSetting count = m.addSetting(new NumberSetting("Mine: count (0 = no limit)", 0, 0, 2000), G, "Stop after this many blocks.");
			m.addAction("Start mining the blocks above", "Same as .mine <blocks> [count].", () -> {
				if (blocks.get().isEmpty()) {
					Toasts.show(mod.name(), "Add at least one block to Mine: blocks first.");
					return;
				}
				String ids = String.join(" ", blocks.get());
				Cmd.run("mine " + ids + (count.asInt() > 0 ? " " + count.asInt() : ""));
			});

			StringSetting who = m.addSetting(new StringSetting("Follow", ""), G, "Name of a player to follow.");
			actWith(m, "Follow the player above", "Same as .follow <player>.", who, "follow", "Type a player name in Follow first.");

			NumberSetting tunnel = m.addSetting(new NumberSetting("Tunnel length", 20, 1, 500), G, "Blocks to dig in the direction you face.");
			m.addAction("Tunnel in the direction I'm facing", "Same as .tunnel <length>.", () -> Cmd.run("tunnel " + tunnel.asInt()));

			NumberSetting way = m.addSetting(new NumberSetting("This way distance", 50, 5, 1000), G, "Blocks to travel in the direction you face.");
			m.addAction("Travel that far in the direction I'm facing", "Same as .thisway <blocks>.", () -> Cmd.run("thisway " + way.asInt()));
		}
	}
}
