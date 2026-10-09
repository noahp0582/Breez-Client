# Breez Client

A standalone Fabric client mod for Minecraft **1.21.11**: ESP, base-hunting tools, safety tooling, automation and macros.
It has its own module system, config, command prefix and click GUI. It does not depend on Meteor, Baritone or any other client.

> Using a client like this breaks the rules of most multiplayer servers and can get you banned. Use it where you are allowed to.

## Build and run

Needs **JDK 21**.

```
gradlew.bat build        # compiles, runs the unit tests, builds build/libs/breez-client-1.0.0.jar
gradlew.bat runClient    # development client
gradlew.bat test         # pure-logic unit tests only
```

Drop the jar from `build/libs` (not the `-sources` one) into `.minecraft/mods` together with Fabric API `0.141.6+1.21.11`.

## Using it

- **Right Shift** opens the click GUI: left click toggles a module, right click expands its settings, drag a slider to change numbers.
- **Chat commands** start with `.` and are never sent to the server. `.help` lists them.
- Bind any module to a key: `.bind <module> <key>` (`.bind storageesp g`, `.bind killswitch none`).
- Change any setting from chat: `.set <module> <setting> <value>`. List settings: `.set <module>`. Lists use `add`/`remove`:
  `.set killswitch trusted add MyAlt`.
- Config lives in `.minecraft/config/breez-client/`: `config.json` (modules), `profiles.json` (per-server staff list and seed),
  `macros.json`, `waypoints.json`, `killswitch.log`, and a `schematics/` folder.

## Modules

| Module | What it does |
|---|---|
| StorageESP | Boxes and tracers on chests, trapped chests, barrels, ender chests, shulkers, hoppers, furnaces and more. |
| SpawnerESP | Highlights spawners, optional chat notice and waypoint. |
| AmethystDetector | Finds amethyst clusters (and optionally buds / budding blocks that mark geodes). |
| BlockESP | ESP for any block ids you choose: `.blockesp add minecraft:diamond_ore`. |
| OreFinder | Scan-based ore ESP for what the server actually sent. Includes ancient debris. |
| SeedOres | Seed-based ore prediction for chunks the server hid behind anti-xray. `.seed <seed>`, enable SeedOres. |
| SusChunks | Scores chunks by player-built blocks (pistons, chests, hoppers, block entities) with configurable weights. `.sus` lists the top chunks. |
| PlayerESP | Boxes, tracers, and through-walls nametags with health and distance. |
| Freecam | Detached camera. Your real player stands still and cannot attack or place. |
| Fullbright | Local night vision. |
| KillSwitch | Drops the connection immediately when listed staff, a spectator, or (optionally) any player comes within range. Logs to `killswitch.log`. |
| StaffList | `.staff add/remove/list/online/import <file>/clear`. Announces listed staff joining or leaving the tab list. |
| AutoRelog | Leaves and rejoins when your Y level reaches the threshold (default -3) to reload chunks. Cooldown prevents loops. |
| AutoReconnect | Reconnects after the server drops you (not after you press Disconnect). Suppressed after a kill switch. |
| AntiAFK, AutoEat | Small irregular camera turns and hops; eats food from the hotbar when hungry. |
| CrystalMacro, TotemMacro | Places and breaks end crystals at your crosshair; refills the offhand totem. |
| SpawnerPickup | Walks to each spawner, mines it with a silk touch pickaxe (looks away 90 degrees to the right mid-way, then looks back and finishes), then runs your home command and moves **only spawners** into your ender chest. Set the chest with `.spawner chest`. |
| SchematicBuilder | Builds `.litematic`, `.schem` and `.nbt` files layer by layer in straight sweeps. `.build files`, `.build load <file>`, `.build origin`, `.build start`. |
| Macros | `.macro add <name> <script>`; script steps: `say`, `wait`, `slot`, `use`, `attack`, `look`, `turn`, `sneak`, `jump`, `loop`. Bind with `.bind`. |

Waypoints: `.waypoints list|add|remove|clear`. Finds from SpawnerESP, AmethystDetector and SusChunks can save waypoints automatically.

## About "undetectable"

Nothing here sends custom packets or changes the client brand, and every automated action goes through a humanization layer:
camera moves are eased and snapped to real mouse-step sizes, and clicks and placements use randomized, non-periodic delays.
That makes automation look less robotic. It does **not** make it undetectable: server anti-cheat varies, staff can watch you,
and ESP, freecam and fullbright are only invisible because they are local. Treat everything as detectable.

## Known limits

- SeedOres reuses the game's own feature sorter, placement modifiers and ore configs, and ports the two ore placement
  algorithms. Measured against a real generated world (seed-matched singleplayer, 300+ chunks): ancient debris 100% precision /
  99% recall, quartz and nether gold 99-100%, diamond 99% / 98%, gold 98% / 97%, redstone 100% / 98%, lapis 98% / 96%,
  iron 97% / 88%, copper 74% / 63% (the large noise-based copper and iron veins are not modelled), coal about 57% (coal veins
  are huge and numerous, so one mis-modelled block shifts the random stream for the rest of that feature). It only knows about
  chunks the server has sent you, and it needs the real seed. Check it yourself in singleplayer with `.seed verify`.
- Not verified against a live multiplayer server: KillSwitch, AutoRelog, AutoReconnect, SpawnerPickup, SchematicBuilder and the
  macro engine compile and are wired in, but only the parts with unit tests (pathfinder, schematic parsers, macro parser,
  scoring, rotations) were exercised automatically. Try them on a test world before relying on them.
- Dev self-test: set the `BREEZ_SELFTEST` environment variable and launch with `--quickPlaySingleplayer <world>`; it enables the
  passive modules, checks SeedOres against the world and writes `run/breez-selftest.txt`.
- SchematicBuilder handles axis blocks, slabs and half-stairs. Other facing-dependent blocks are placed but may need rotating.
- Kill switch cannot see staff who are vanished by a plugin (they never reach your client).
- Rendering was written against the 1.21.11 render pipeline; if a future Minecraft version changes it, `render/BreezRender.java` is the file to look at.

## License

CC0-1.0 (see LICENSE).
