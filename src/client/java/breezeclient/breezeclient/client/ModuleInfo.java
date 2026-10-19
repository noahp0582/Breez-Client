package breezeclient.breezeclient.client;

import breezeclient.breezeclient.core.Detection;
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

	/** Modules that are not Undetected. Everything not listed here is Undetected. */
	private static final Map<String, Detection> DETECTION = new HashMap<>();

	private static void d(String module, Detection d) { DETECTION.put(module.toLowerCase(), d); }

	/** The rating for a module name; Undetected when none was set. */
	public static Detection detectionOf(String module) {
		return DETECTION.getOrDefault(module.toLowerCase(), Detection.UNDETECTED);
	}

	static {
		// Rated by what each module sends to
		// the server and how visible it is: pure rendering and local-only modules are Undetected, anything that places,
		// breaks, clicks or paths on its own at speed is Use at Risk.
		d("HitBox", Detection.DETECTED);
		d("TunnelBaseFinder", Detection.DETECTED);
		d("TotemMacro", Detection.USE_AT_RISK);
		d("AutoInvTotem", Detection.USE_AT_RISK);
		d("StaticHitBoxes", Detection.USE_AT_RISK);
		d("RTPBaseFinder", Detection.USE_AT_RISK);
		d("SchematicBuilder", Detection.USE_AT_RISK);
		d("SpawnerPickup", Detection.USE_AT_RISK);
		d("ChestLooter", Detection.USE_AT_RISK);
		d("Baritone", Detection.USE_AT_RISK);
		d("AntiTrap", Detection.USE_AT_RISK);
		d("AutoShield", Detection.USE_AT_RISK);
		d("AutoDig", Detection.USE_AT_RISK);
		d("ItemDropper", Detection.USE_AT_RISK);
		d("AutoSell", Detection.USE_AT_RISK);
		d("AutoSpawnerSell", Detection.USE_AT_RISK);
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
		m("Baritone", "Built-in pathing with Baritone-style commands: .goto .mine .follow .explore .tunnel .thisway .surface .stop. This is its settings page; turning it off stops the current task.");
		m("CustomFOV", "Sets the field of view beyond the vanilla slider range.");
		m("Zoom", "Optical zoom with smooth easing. Bind it to a key; it zooms while the key is held.");
		m("FreeLook", "Look around in third person without turning your character. Bind it and hold the key.");
		m("SkinProtect", "Shows default skins instead of real ones for recordings and screenshots. Local only.");
		m("AutoWalk", "Holds the forward key for you.");
		m("AutoClicker", "Attacks what your crosshair is on at a randomized click rate, waiting for the attack cooldown.");
		m("AutoTPA", "Sends teleport requests to one player on a humanized timer, up to a limit.");
		m("FastUse", "Shortens the delay between item uses and block placements.");
		m("JumpCircles", "A glowing ring that expands and fades where you jump.");
		m("HitParticles", "Bursts of particles when you hit something. Visual and local only.");
		m("NameProtect", "Replaces your own name in chat with an alias so clips do not show it.");
		m("CustomCrosshair", "Replaces the vanilla crosshair with a configurable one.");
		m("AimAssist", "Adds a small correction toward the target nearest your crosshair each tick: a fraction of the remaining angle, capped, faded near the crosshair and snapped to mouse steps. You stay in control; it never snaps.");
		m("Triggerbot", "Attacks when your crosshair is on a valid target and the weapon is recharged, after a small random reaction delay.");
		m("HoverTotem", "In the inventory, puts a totem in your offhand: the one under the mouse, or any when the inventory opens.");
		m("AnchorMacro", "Charges the respawn anchor you look at with glowstone, switches item and detonates it.");
		m("DoubleAnchor", "AnchorMacro that charges and detonates twice in a row.");
		m("ElytraSwap", "Bind to a key: swaps your chestplate and an elytra from the inventory with ordinary clicks.");
		m("MaceSwap", "Switches to a mace when you attack, then back to the slot you had.");
		m("MaceBomber", "While falling onto a target, equips the mace, lines up and smashes for the fall bonus.");
		m("ShieldBreaker", "When the target under your crosshair is blocking, switches to an axe (and optionally hits).");
		m("ArmorTrimHider", "Hides or randomizes the armor trims you see on worn armor. Local rendering only.");
		m("ScoreboardName", "Replaces your name with custom text (color codes supported) or hides your line, on the sidebar scoreboard (line, score text, title), the tab list, and chat/action bar lines. Only your name is replaced; scores and what others see are untouched.");
		m("TunnelFinder", "Finds long straight 1x2 corridors underground (strip mines, dug hallways) that natural caves do not have. Also feeds SusChunks.");
		m("CoverageMap", "HUD map of which chunks you have already had loaded on this server and dimension, so a long hunt does not repeat ground.");
		m("AutoLog", "Disconnects when your health is low, after totem pops, or when you are in lava.");
		m("AlertWebhook", "Posts finds, KillSwitch/AutoLog events and disconnects to a Discord webhook you set. Only the alert text and server address are sent, only to that URL.");
		m("ItemESP", "Highlights dropped valuable items (spawners, shulker boxes, netherite and more) from a list with wildcards.");
		m("ChestLooter", "Shift-clicks listed items out of a container you opened by hand, at a randomized pace. Refuses server menus that were not opened from a container block.");
		m("PlayerLogger", "Logs players joining and leaving and where each was last seen, to players.csv. Use .players [name].");
		m("MotionBlur", "Cinematic motion blur by blending frames. Strength 1-5.");
		m("CustomGlint", "Recolors the enchantment glint on items and armor, with an optional rainbow cycle. Local only.");
		m("HitBox", "Your crosshair treats other entities as slightly wider. The server still uses the real hitbox and its own reach check, so keep it small.");
		m("NameTags", "HUD-styled nametags with health, distance and gear for players, and names with counts for dropped items; visible through walls.");
		m("CreamyKeys", "Synthesized soft keyboard, mouse and scroll sounds with pitch variation. Only you hear them.");
		m("CustomAccessories", "Cosmetics for yourself: a custom cape, trail, aura and crown. Only you see them.");
		m("Animations", "Steadier first-person hands: no equip or cooldown dip, swing speed, hand offset and size. Visual only.");
		m("RegionMap", "HUD map of regions you define (name x1 z1 x2 z2), with an alert when you enter a different one.");
		m("BlockESP", "Highlights any blocks you choose through walls. Edit the list in the settings or with .blockesp.");
		m("OreFinder", "Highlights the ores the server has actually sent you, including ancient debris.");
		m("SeedOres", "Predicts where ores and ancient debris generated using the world seed, even when anti-xray hides them. Set the seed in the Profiles tab.");
		m("SusChunks", "Finds player bases by specific signatures (spawner stacks, spawners with hoppers, hopper storage, big storage rooms, ender chests, beacons), griefed or raided bases (TNT left behind, fire, obsidian piles, workstations with every chest gone) and lavacasts. Ignores villages, mineshafts, dungeons and other natural structures, ranks finds that touch each other higher, and outlines the whole chunk.");
		m("PlayerESP", "Boxes, tracers and through-walls nametags with health and distance for other players.");
		m("Freecam", "Detaches the camera so you can look around while your character stands still.");
		m("Fullbright", "Maximum brightness everywhere (local night vision; nothing is sent to the server).");
		m("KillSwitch", "Drops the connection immediately when staff, a spectator or another player comes within range. Logs to killswitch.log.");
		m("StaffList", "Announces listed staff joining or leaving and outlines them when visible.");
		m("SpectatorAlert", "Puts a title on your own screen when a player in spectator mode comes within range. Drawn locally; nothing is sent to the server.");
		m("Debug", "Shows Breez Client warnings and errors in chat as they happen so you can report them. Everything is recorded from startup and written to breez-debug.log; .debug copy puts a full report on the clipboard.");
		m("AutoRelog", "Leaves and rejoins when you reach a Y level (default -3) so the server re-sends nearby chunks.");
		m("AutoReconnect", "Reconnects after the server drops you. Never after a manual disconnect or a kill switch.");
		m("AntiAFK", "Small irregular camera turns and hops so you are not kicked for idling.");
		m("AutoEat", "Eats food from your hotbar when hungry, then switches back.");
		m("CrystalMacro", "Places and breaks end crystals at your crosshair with randomized timing, laying an obsidian base first when needed.");
		m("TotemMacro", "Keeps a totem of undying in your offhand, refilling it after a pop.");
		m("SpawnerPickup", "Walks to spawners, mines them with a silk touch pickaxe, then moves only the spawners into your ender chest.");
		m("SchematicBuilder", "Builds a schematic by hand like a printer: places whatever in reach can be placed right now (lowest layer first), with the right orientation, and paths to the next spot when nothing can. Digs what is in the way, never shuts itself in, and can buy missing blocks from the auction house within a price limit. Use .build auto [file].");

		m("AutoDoubleHand", "When your health runs low and a totem is in the offhand, also holds a totem in the main hand so one pop never leaves you empty-handed.");
		m("AutoInvTotem", "While your inventory is open, moves a totem into the offhand (and optionally a hotbar slot) with ordinary clicks.");
		m("TotemOffhand", "Puts a hotbar totem into the offhand with the swap-hands action when the offhand empties, then returns to your slot.");
		m("AutoShield", "Raises a shield when an end crystal is close, or a nearby player is holding crystals or an anchor: swaps a hotbar shield into the offhand, faces the danger and blocks until it has passed, then puts things back. An offhand totem stays where it is unless you allow the swap.");
		m("AutoJumpReset", "Jumps the tick you take a hit while on the ground, so some of the knockback becomes upward motion.");
		m("NoHitDelay", "Removes the ten-tick pause vanilla puts on your attack button after swinging at nothing.");
		m("CrystalOptimizer", "Removes an end crystal from your screen the moment you hit it, so you can place and target the next one without waiting for the server.");
		m("StaticHitBoxes", "Your crosshair keeps seeing players at standing height while they sneak, crawl or swim. Targeting only; the server uses the real box.");
		m("SpearSwap", "Switches to a spear when you attack something, then back to the slot you had.");
		m("AutoHitCrystal", "Breaks any end crystal under your crosshair once it is in reach, after a short random delay. Never places anything.");
		m("Sprint", "Holds sprint while you move forward.");
		m("ElytraGlide", "While gliding, cycles dive and climb to keep speed and height without fireworks. Bind it and toggle in the air.");
		m("AutoFirework", "While gliding, fires a firework rocket from your hotbar or offhand whenever you slow down.");
		m("KeyPearl", "Bind to a key: each press throws an ender pearl from your hotbar, then returns to your slot.");
		m("KeyWindCharge", "Bind to a key: each press throws a wind charge from your hotbar, then returns to your slot.");
		m("AutoMine", "Holds the attack button while your crosshair is on a block that is safe to break. Containers, spawners and unbreakable blocks are skipped.");
		m("AutoDig", "Clears every block inside a box you mark with two corners, top down: digs whatever in reach it can see and paths to the next spot when nothing is. Knows the DonutSMP shard pickaxe: one hit takes a 3x3 slab, and the rim of the box is finished with an ordinary pickaxe. Use .dig pos1 / pos2 / start.");
		m("AutoTool", "Switches to the fastest tool in your hotbar for the block you are breaking, and back when you stop.");
		m("Fakeplayer", "Spawns a client-side copy of a player where you stand, for testing ESP and aim modules. Nobody else can see it.");
		m("ItemDropper", "Drops the items on your list (or everything not on the Keep list) from your inventory at a steady pace.");
		m("AntiTrap", "When you are walled into a one by two cell, digs out through the softest block. Never touches containers or spawners.");
		m("AutoSell", "Runs your sell command on a timer, moves matching items into the sell window and presses confirm. Does nothing unless the window title matches.");
		m("AutoSpawnerSell", "In a spawner window, presses the sell button (or takes the items out) and closes it. Can open the spawner you look at on a timer.");
		m("ChunkFinder", "Highlights chunks that hold at least a number of the blocks you pick (spawners by default). .chunkfinder lists the richest.");
		m("NetheriteFinder", "Highlights ancient debris and netherite blocks.");
		m("PlayerChunks", "Remembers the chunks other players were seen in and outlines them, fading with age.");
		m("BlockNotifier", "Tells you when one of the listed blocks shows up within range, with a pop-up and an optional chime.");
		m("PearlTrajectory", "Draws where an ender pearl will land: the one in your hand and pearls already in the air.");
		m("BowTrajectory", "Draws where your arrow or trident will hit while you hold a bow, loaded crossbow or trident, and outlines the player or mob it would hit.");
		m("RealHitBox", "Draws the true hitbox of other entities (what the server uses), so you can compare it with what HitBox makes you aim at.");
		m("TargetHUD", "A HUD panel for whoever you last hit or are aiming at: name, health bar, armor and distance.");
		m("MusicHUD", "A HUD line with the music or record that is playing.");
		m("BlockEntityDebug", "HUD panel and chat listing of the block entities in your chunk by type, straight from the chunk data.");
		m("LightFinder", "Finds player-placed light sources (torches, lanterns, lamps) deep underground, where nothing natural lights a room.");
		m("SuspiciousESP", "Highlights single blocks that mark player activity: hoppers, observers, pistons, beacons, ender chests and similar. A few (obsidian, TNT, anvils) also occur in natural structures, so expect some noise.");
		m("TunnelBaseFinder", "Joins TunnelFinder runs into whole tunnels and marks both ends: a long straight tunnel leads somewhere.");
		m("PrimeChunkFinder", "Combines SusChunks, StashFinder, TunnelFinder and NewChunks: a chunk is prime when several independent signals agree. .prime lists them.");
		m("RTPBaseFinder", "Uses your random-teleport command, waits for the area to load, checks the finders and repeats until something worth a look turns up.");
		m("SeedChunkFinder", "With the world seed set, finds chunks where many of the ores world generation put are gone: somebody has been mining there.");
		m("DiscordPresence", "Shows what you are doing in your Discord profile through the local Discord app. Needs an Application ID; nothing is sent without one.");
		m("Proxy", "Connects to servers through a SOCKS5, SOCKS4 or HTTP proxy. Only the server connection uses it.");
		m("Radio", "Plays an internet radio station (MP3 streams) while you play.");

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
		s("CrystalMacro", "Lay obsidian base", "Base", "Place obsidian on the aimed block first when it is not obsidian or bedrock.");
		s("CrystalMacro", "Only while holding use", "Behavior", "Only act while you hold right click.");
		s("TotemMacro", "Delay (ticks)", "Humanization", "Average delay before refilling the offhand.");
		s("Freecam", "Speed", "General", "Camera speed in blocks per tick.");
	}

	public static void apply(Module m) {
		String d = MODULES.get(m.name().toLowerCase());
		if (d != null && m.description().isBlank()) m.setDescription(d);
		m.setDetection(detectionOf(m.name()));
		for (Setting<?> s : m.settings()) {
			String[] meta = SETTINGS.get((m.name() + "|" + s.name()).toLowerCase());
			if (meta != null && s.description().isBlank()) s.setMeta(meta[0], meta[1]);
		}
	}
}
