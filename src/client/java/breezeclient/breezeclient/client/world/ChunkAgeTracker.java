package breezeclient.breezeclient.client.world;

import breezeclient.breezeclient.client.session.Guard;
import breezeclient.breezeclient.client.session.Session;
import breezeclient.breezeclient.core.world.ChunkAge;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Always-on bookkeeping for new/old chunk detection (one {@link ChunkAge} per server and dimension), saved to
 * config/breez-client/chunks/. The NewChunks module only draws what this collects.
 */
public final class ChunkAgeTracker {
	public static final int WINDOW_TICKS = 120;

	private static final Map<String, ChunkAge> BY_WORLD = new HashMap<>();
	private static long tick;
	private static int sinceSave;
	private static String currentKey;
	private static boolean strict;

	/** Strict mode: only chunks that contain settled liquid are called old; the rest stay unsure. */
	public static void setStrict(boolean s) {
		if (s == strict) return;
		strict = s;
		for (ChunkAge a : BY_WORLD.values()) a.setStrict(s);
	}

	private static boolean hasLiquid(long key) {
		var level = Minecraft.getInstance().level;
		if (level == null) return false;
		var chunk = level.getChunkSource().getChunk(ChunkAge.x(key), ChunkAge.z(key), false);
		if (chunk == null) return false;
		for (var section : chunk.getSections()) if (!section.hasOnlyAir() && section.maybeHas(st -> !st.getFluidState().isEmpty())) return true;
		return false;
	}

	private ChunkAgeTracker() {}

	public static void register() {
		// both run inside the chunk packet handler, so they must not throw (see Guard)
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> Guard.run("ChunkAge load", () -> current().onLoad(chunk.getPos().toLong(), tick)));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> Guard.run("ChunkAge unload", () -> current().onUnload(chunk.getPos().toLong(), ChunkAgeTracker::hasLiquid)));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null) return;
			tick++;
			current().tick(tick, ChunkAgeTracker::hasLiquid);
			if (++sinceSave > 20 * 300) saveAll();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(ChunkAgeTracker::saveAll));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> saveAll());
	}

	private static String worldKey() {
		var mc = Minecraft.getInstance();
		String dim = mc.level == null ? "none" : mc.level.dimension().identifier().toString();
		return (Session.serverKey() + "_" + dim).replaceAll("[^A-Za-z0-9._-]", "_").toLowerCase(Locale.ROOT);
	}

	private static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("breez-client").resolve("chunks"); }

	/** The classifier for the world (server + dimension) the player is in right now. */
	public static ChunkAge current() {
		String k = worldKey();
		currentKey = k;
		return BY_WORLD.computeIfAbsent(k, key -> {
			ChunkAge a = new ChunkAge(WINDOW_TICKS);
			a.setStrict(strict);
			a.restore(read(dir().resolve(key + ".txt")));
			return a;
		});
	}

	public static void onBlockUpdate(BlockPos pos, BlockState state) {
		if (state.getFluidState().isEmpty() || state.getFluidState().isSource()) return;
		current().onFluidFlow(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), tick);
	}

	public static ChunkAge.State state(int chunkX, int chunkZ) { return current().state(ChunkAge.key(chunkX, chunkZ)); }

	public static void saveAll() {
		sinceSave = 0;
		for (var e : BY_WORLD.entrySet()) write(dir().resolve(e.getKey() + ".txt"), e.getValue().snapshot());
	}

	public static void clearCurrent() {
		current().clear();
		try {
			Files.deleteIfExists(dir().resolve(currentKey + ".txt"));
		} catch (IOException ignored) {
			// nothing to delete
		}
	}

	/** Writes "x z N|O" lines for the current world; returns the file path. */
	public static Path export() throws IOException {
		Files.createDirectories(dir());
		Path out = dir().resolve("export_" + worldKey() + ".csv");
		List<String> lines = new ArrayList<>();
		lines.add("chunkX,chunkZ,blockX,blockZ,state");
		for (var e : current().snapshot().entrySet()) {
			if (e.getValue() == ChunkAge.State.UNKNOWN) continue;
			int cx = ChunkAge.x(e.getKey()), cz = ChunkAge.z(e.getKey());
			lines.add(cx + "," + cz + "," + (cx * 16 + 8) + "," + (cz * 16 + 8) + "," + e.getValue());
		}
		Files.write(out, lines);
		return out;
	}

	private static void write(Path file, Map<Long, ChunkAge.State> snap) {
		try {
			Files.createDirectories(file.getParent());
			List<String> lines = new ArrayList<>();
			for (var e : snap.entrySet()) {
				if (e.getValue() == ChunkAge.State.UNKNOWN) continue;
				lines.add(ChunkAge.x(e.getKey()) + " " + ChunkAge.z(e.getKey()) + " " + (e.getValue() == ChunkAge.State.NEW ? "N" : "O"));
			}
			Files.write(file, lines);
		} catch (IOException ignored) {
			// best effort
		}
	}

	private static Map<Long, ChunkAge.State> read(Path file) {
		Map<Long, ChunkAge.State> out = new HashMap<>();
		try {
			if (!Files.isRegularFile(file)) return out;
			for (String line : Files.readAllLines(file)) {
				String[] p = line.trim().split(" ");
				if (p.length != 3) continue;
				try {
					out.put(ChunkAge.key(Integer.parseInt(p[0]), Integer.parseInt(p[1])), p[2].equals("N") ? ChunkAge.State.NEW : ChunkAge.State.OLD);
				} catch (NumberFormatException ignored) {
					// skip bad line
				}
			}
		} catch (IOException ignored) {
			// start empty
		}
		return out;
	}
}
