package breezeclient.breezeclient.client;

import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.Setting;

import java.util.HashMap;
import java.util.Map;

/**
 * Descriptions and GUI grouping for modules and their settings, kept in one place so the module classes stay small.
 * Settings not listed keep the default group "General".
 */
public final class ModuleInfo {
	private static final Map<String, String> MODULES = new HashMap<>();
	/** "Module|Setting" -> {group, description} */
	private static final Map<String, String[]> SETTINGS = new HashMap<>();

	private ModuleInfo() {}

	private static void m(String module, String description) { MODULES.put(module.toLowerCase(), description); }

	private static void s(String module, String setting, String group, String description) {
		SETTINGS.put((module + "|" + setting).toLowerCase(), new String[] {group, description});
	}

	static {
		m("StorageESP", "Boxes and tracers on chests, trapped chests, barrels, ender chests, shulker boxes, hoppers and furnaces.");
		m("SpawnerESP", "Highlights mob spawners and tells you where new ones appear.");
		m("AmethystDetector", "Finds amethyst geodes, judges how grown they are from their buds, and notifies you when they are ready.");
		m("ClusterESP", "Marks the empty slots next to budding amethyst where crystals can still grow: shows which geodes are unfinished.");
		m("NewChunks", "Marks chunks that were freshly generated while you watched (new) and chunks that already existed (old). Old chunks inside new terrain are where players have been.");
		m("TrailFinder", "Points to old chunks that border unexplored chunks: the places a player trail or base continues.");
		m("StashFinder", "Scores chunks by storage, redstone, decoration or spawners, ignores natural dungeons, and logs every find to stashes.csv.");
		m("BlockEntityESP", "Highlights block entities from the chunk data itself: chests, spawners, signs (with text), beds and more, even when hidden or camouflaged.");
		m("EntityStorageESP", "Finds stashes that are entities: chest boats and minecarts, pack animals with chests, item frames, armor stands.");
		m("PortalESP", "Highlights nether portals.");
		m("HoleESP", "Finds 1x1 holes walled on all sides; green ones are bedrock/obsidian safe holes.");
		m("SpawnerNametags", "Labels spawners with their mob type and draws the 16-block activation ring.");
		m("MobESP", "Highlights hostile (and optionally passive) mobs through walls.");
		m("WeatherNotifier", "Pop-up when it starts or stops raining or storming.");
		m("CoordSnapper", "Bind to a key: copies the coordinates of the block you are looking at.");
		m("SpawnerProtect", "When a stranger gets close, picks up nearby spawners then leaves the server.");
		m("BlockESP", "Highlights any blocks you choose through walls. Edit the list in the settings or with .blockesp.");
		m("OreFinder", "Highlights the ores the server has actually sent you, including ancient debris.");
		m("SeedOres", "Predicts where ores and ancient debris generated using the world seed, even when anti-xray hides them. Set the seed in the Profiles tab.");
		m("SusChunks", "Scores chunks by player-built blocks (pistons, chests, hoppers, block entities) and highlights suspicious ones.");
		m("PlayerESP", "Boxes, tracers and through-walls nametags with health and distance for other players.");
		m("Freecam", "Detaches the camera so you can look around while your character stands still.");
		m("Fullbright", "Maximum brightness everywhere (local night vision; nothing is sent to the server).");
		m("KillSwitch", "Drops the connection immediately when staff, a spectator or another player comes within range. Logs to killswitch.log.");
		m("StaffList", "Announces listed staff joining or leaving and outlines them when visible.");
		m("AutoRelog", "Leaves and rejoins when you reach a Y level (default -3) so the server re-sends nearby chunks.");
		m("AutoReconnect", "Reconnects after the server drops you. Never after a manual disconnect or a kill switch.");
		m("AntiAFK", "Small irregular camera turns and hops so you are not kicked for idling.");
		m("AutoEat", "Eats food from your hotbar when hungry, then switches back.");
		m("CrystalMacro", "Places and breaks end crystals at your crosshair with randomized timing.");
		m("TotemMacro", "Keeps a totem of undying in your offhand, refilling it after a pop.");
		m("SpawnerPickup", "Walks to spawners, mines them with a silk touch pickaxe, then moves only the spawners into your ender chest.");
		m("SchematicBuilder", "Builds a loaded schematic layer by layer with eased camera movement. Use .build load / origin / start.");

		s("StorageESP", "Range", "Render", "How far away storage is highlighted.");
		s("StorageESP", "Tracers", "Render", "Draw lines from the crosshair to each storage block.");
		s("StorageESP", "Fill", "Render", "Fill the boxes with a translucent color.");
		s("SpawnerESP", "Notify", "Alerts", "Show a notification when a new spawner is found.");
		s("SpawnerESP", "Waypoint", "Alerts", "Save a waypoint for each new spawner.");
		s("BlockESP", "Blocks", "Blocks", "Blocks to highlight.");
		s("SeedOres", "Only hidden ores", "General", "Skip ores the server already shows you; only mark ones that are hidden.");
		s("SeedOres", "Simulate radius (chunks)", "Performance", "How many chunks around you are simulated.");
		s("SusChunks", "Indicators", "Scoring", "Blocks that make a chunk suspicious, written as block_id=weight.");
		s("SusChunks", "Threshold", "Scoring", "Minimum score for a chunk to be flagged.");
		s("SusChunks", "Block entity weight", "Scoring", "Extra score per block entity in the chunk.");
		s("KillSwitch", "Radius", "Detection", "Distance in blocks at which a player triggers the kill switch.");
		s("KillSwitch", "Spectators", "Detection", "Also trigger on any player in spectator mode.");
		s("KillSwitch", "Tab-list ghosts", "Detection", "Also trigger on players that have an entity but are missing from the tab list.");
		s("KillSwitch", "Any player", "Detection", "Trigger on every player that is not trusted, staff or not.");
		s("KillSwitch", "Trusted", "Detection", "Player names that never trigger the kill switch.");
		s("KillSwitch", "Allow auto-reconnect", "Behavior", "Let AutoReconnect rejoin after the kill switch fires.");
		s("AutoRelog", "Y level", "Trigger", "Relog when your Y is at or below this.");
		s("AutoRelog", "Cooldown (s)", "Trigger", "Minimum time between relogs, so it never loops.");
		s("AutoRelog", "Rejoin delay (s)", "Trigger", "Average wait before rejoining.");
		s("AutoRelog", "Overworld only", "Trigger", "Only relog in the overworld.");
		s("SpawnerPickup", "Search radius", "Targets", "How far from you spawners are collected.");
		s("SpawnerPickup", "Deposit every N spawners", "Targets", "Go home and deposit after carrying this many.");
		s("SpawnerPickup", "Look-aways per spawner", "Humanization", "Times the camera turns 90 degrees away mid-break and back.");
		s("SpawnerPickup", "Sneak while mining", "Humanization", "Hold sneak while breaking.");
		s("SpawnerPickup", "Home command", "Home", "Command that teleports you home, e.g. /home base.");
		s("SpawnerPickup", "Return command", "Home", "Command that brings you back for the next batch, e.g. /back. Blank = stop after one batch.");
		s("SpawnerPickup", "Ender chest (x y z)", "Home", "Set with .spawner chest while looking at the chest.");
		s("SpawnerPickup", "Stop if player within (0=off)", "Safety", "Stop when any player comes this close.");
		s("SchematicBuilder", "File", "Build", "Schematic file name (set with .build load).");
		s("SchematicBuilder", "Reach", "Build", "Maximum distance for placing blocks.");
		s("SchematicBuilder", "Delay (ticks)", "Humanization", "Average delay between placements.");
		s("CrystalMacro", "Only while holding use", "Behavior", "Only act while you hold right click.");
		s("TotemMacro", "Delay (ticks)", "Humanization", "Average delay before refilling the offhand.");
		s("Freecam", "Speed", "General", "Camera speed in blocks per tick.");
	}

	public static void apply(Module m) {
		String d = MODULES.get(m.name().toLowerCase());
		if (d != null && m.description().isBlank()) m.setDescription(d);
		for (Setting<?> s : m.settings()) {
			String[] meta = SETTINGS.get((m.name() + "|" + s.name()).toLowerCase());
			if (meta != null && s.description().isBlank()) s.setMeta(meta[0], meta[1]);
		}
	}
}
