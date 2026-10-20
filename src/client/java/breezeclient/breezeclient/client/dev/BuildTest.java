package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.Modules;
import breezeclient.breezeclient.client.modules.SchematicBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer test (BREEZ_SELFTEST=build): writes a small structure .nbt next to the mod folder, lets SchematicBuilder find
 * it by name, build it at the crosshair in creative mode, then checks every block, an axis block and a top slab.
 * Writes run/build-test.txt and quits.
 */
public final class BuildTest {
	record Want(int x, int y, int z, String block, String props) {}

	private static final String NAME = "breez-test-house";
	private static final List<Want> WANTS = new ArrayList<>();
	private static final List<String> LOG = new ArrayList<>();
	private static int ticks = -1;
	private static int startedAt = -1;

	private BuildTest() {}

	public static boolean register() {
		if (!"build".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> ticks = 0);
		ClientTickEvents.END_CLIENT_TICK.register(BuildTest::tick);
		return true;
	}

	private static void cmd(Minecraft mc, String c) {
		var server = mc.getSingleplayerServer();
		if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c));
	}

	/** 5x3x5: stone brick floor, oak plank walls two high with log corners, and a ceiling of top slabs inside the upper ring of the walls. */
	private static void design() {
		for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) WANTS.add(new Want(x, 0, z, "minecraft:stone_bricks", null));
		for (int y = 1; y <= 2; y++) {
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
					if (!edge) continue;
					boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
					WANTS.add(corner ? new Want(x, y, z, "minecraft:oak_log", "axis=y") : new Want(x, y, z, "minecraft:oak_planks", null));
				}
			}
		}
		for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) WANTS.add(new Want(x, 2, z, "minecraft:oak_slab", "type=top"));
		// blocks whose state depends on where the player stands and what is clicked
		WANTS.add(new Want(1, 1, 1, "minecraft:oak_stairs", "facing=north,half=bottom"));
		WANTS.add(new Want(3, 1, 3, "minecraft:oak_stairs", "facing=east,half=bottom"));
		WANTS.add(new Want(3, 1, 1, "minecraft:oak_log", "axis=x"));
		WANTS.add(new Want(1, 1, 3, "minecraft:stone_slab", "type=bottom"));
	}

	private static void writeFile() throws Exception { writeNbt(WANTS, 5, 3, 5, NAME); }

	/** Writes {@code wants} as a structure .nbt called {@code name} into the mods folder. */
	static void writeNbt(List<Want> wants, int sx, int sy, int sz, String name) throws Exception {
		List<String> palette = new ArrayList<>();
		ListTag blocks = new ListTag();
		for (Want w : wants) {
			String key = w.block() + (w.props() == null ? "" : "[" + w.props() + "]");
			int idx = palette.indexOf(key);
			if (idx < 0) {
				palette.add(key);
				idx = palette.size() - 1;
			}
			CompoundTag b = new CompoundTag();
			ListTag pos = new ListTag();
			pos.add(IntTag.valueOf(w.x()));
			pos.add(IntTag.valueOf(w.y()));
			pos.add(IntTag.valueOf(w.z()));
			b.put("pos", pos);
			b.putInt("state", idx);
			blocks.add(b);
		}
		ListTag pal = new ListTag();
		for (String key : palette) {
			CompoundTag e = new CompoundTag();
			int br = key.indexOf('[');
			e.putString("Name", br < 0 ? key : key.substring(0, br));
			if (br >= 0) {
				CompoundTag props = new CompoundTag();
				for (String kv : key.substring(br + 1, key.length() - 1).split(",")) {
					String[] p = kv.split("=");
					props.put(p[0], StringTag.valueOf(p[1]));
				}
				e.put("Properties", props);
			}
			pal.add(e);
		}
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(sx));
		size.add(IntTag.valueOf(sy));
		size.add(IntTag.valueOf(sz));
		CompoundTag root = new CompoundTag();
		root.put("size", size);
		root.put("palette", pal);
		root.put("blocks", blocks);
		root.putInt("DataVersion", 4671);
		Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
		Files.createDirectories(mods);
		NbtIo.writeCompressed(root, mods.resolve(name + ".nbt"));
	}

	private static void tick(Minecraft mc) {
		if (ticks < 0 || mc.player == null || mc.level == null) return;
		ticks++;
		mc.options.pauseOnLostFocus = false;
		if (ticks < 25) SelfTest.reviveAtStart(mc); // an earlier test may have left the player dead
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
		try {
			if (ticks == 10) {
				design();
				writeFile();
				cmd(mc, "gamemode creative @a");
				cmd(mc, "clear @a");
				cmd(mc, "fill -14 145 -14 14 149 14 minecraft:stone");
				cmd(mc, "fill -14 150 -14 14 160 14 minecraft:air");
				// night in the test world: a zombie that wanders in gets walled into the house and blocks its ceiling
				cmd(mc, "time set day");
				cmd(mc, "kill @e[type=!minecraft:player,x=-20,y=140,z=-20,dx=40,dy=30,dz=40]");
			}
			if (ticks == 40) cmd(mc, "execute as @a run tp @s 0.5 150 -6.5");
			if (ticks == 60) mc.player.setXRot(35);
			if (ticks == 70) {
				List<String> out = new ArrayList<>();
				BreezClientClient.commands.handle(".build files", out::add);
				LOG.add("files: " + out);
				LOG.add((out.toString().contains(NAME + ".nbt") ? "PASS" : "FAIL") + " .build files lists the schematic from the mods folder");
				var b = Modules.builder;
				String err = b.buildNow(NAME); // no extension, found by name
				LOG.add((err == null ? "PASS" : "FAIL") + " buildNow(" + NAME + ") " + (err == null ? "started, origin " + b.origin() : err));
				startedAt = ticks;
				var org = b.origin(); // dirt where wall blocks belong: the builder has to dig it out first
				if (org != null) {
					cmd(mc, "setblock " + (org.getX() + 2) + " " + (org.getY() + 1) + " " + org.getZ() + " minecraft:dirt");
					cmd(mc, "setblock " + (org.getX() + 3) + " " + (org.getY() + 2) + " " + org.getZ() + " minecraft:dirt");
				}
			}
			if (startedAt > 0 && (!Modules.builder.enabled() || ticks - startedAt > 6000)) finish(mc);
		} catch (Exception e) {
			LOG.add("FAIL test threw " + e);
			finish(mc);
		}
	}

	private static boolean finished;

	private static void finish(Minecraft mc) {
		if (finished) return;
		finished = true;
		var b = Modules.builder;
		for (String n : b.notes()) LOG.add("  skip: " + n);
		List<String> near = new ArrayList<>();
		for (var e : mc.level.getEntities(mc.player, new net.minecraft.world.phys.AABB(-20, 140, -20, 20, 170, 20))) near.add(net.minecraft.world.entity.EntityType.getKey(e.getType()).getPath() + " at " + e.blockPosition().toShortString());
		if (!near.isEmpty()) LOG.add("  entities near the build: " + near);
		LOG.add("builder status: " + b.status() + " after " + (ticks - startedAt) + " ticks, enabled=" + b.enabled());
		BlockPos o = b.origin();
		int bad = 0, total = 0;
		List<String> missing = new ArrayList<>();
		if (o != null) {
			for (Want w : WANTS) {
				total++;
				BlockPos p = o.offset(w.x(), w.y(), w.z());
				var st = mc.level.getBlockState(p);
				Block want = BuiltInRegistries.BLOCK.getValue(Identifier.parse(w.block()));
				boolean ok = st.getBlock() == want;
				if (ok && w.props() != null) {
					for (String kv : w.props().split(",")) {
						String[] e = kv.split("=");
						boolean same = false;
						for (var prop : st.getProperties()) if (prop.getName().equals(e[0])) same = st.getValue(prop).toString().equalsIgnoreCase(e[1]);
						ok &= same;
					}
				}
				if (!ok) {
					bad++;
					if (missing.size() < 12) missing.add(w.block() + (w.props() == null ? "" : "[" + w.props() + "]") + " at " + p.toShortString() + " is " + st);
				}
			}
		}
		LOG.add((b.pearlsThrown() == 0 ? "PASS" : "FAIL") + " no pearls thrown on a 3-high build (" + b.pearlsThrown() + ")");
		LOG.add((bad == 0 && total > 0 ? "PASS" : "FAIL") + " built " + (total - bad) + "/" + total + " blocks (stair facing, log axis and slab halves included)" + (missing.isEmpty() ? "" : " missing: " + missing));
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("build-test.txt"), String.join("\n", LOG) + "\n");
		} catch (Exception ignored) {
			// nothing else to do
		}
		mc.stop();
	}
}
