package breezeclient.breezeclient.client.world;

import breezeclient.breezeclient.client.session.Session;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Remembers which chunks you have had loaded, per server and dimension, so a long hunt does not cover the same ground
 * twice. Always on (it is one set insertion per chunk load) and saved to config/breez-client/coverage/.
 */
public final class CoverageTracker {
	private static final Map<String, LongOpenHashSet> BY_WORLD = new HashMap<>();
	private static final int SAVE_EVERY_TICKS = 20 * 300;
	private static int sinceSave;

	private CoverageTracker() {}

	public static void register() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> current().add(chunk.getPos().toLong()));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level != null && ++sinceSave > SAVE_EVERY_TICKS) saveAll();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(CoverageTracker::saveAll));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> saveAll());
	}

	private static String worldKey() {
		Minecraft mc = Minecraft.getInstance();
		String dim = mc.level == null ? "none" : mc.level.dimension().identifier().toString();
		return (Session.serverKey() + "_" + dim).replaceAll("[^A-Za-z0-9._-]", "_").toLowerCase(Locale.ROOT);
	}

	private static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("breez-client").resolve("coverage"); }

	private static LongOpenHashSet current() {
		return BY_WORLD.computeIfAbsent(worldKey(), k -> read(dir().resolve(k + ".bin")));
	}

	public static boolean seen(int chunkX, int chunkZ) { return current().contains(ChunkPos.asLong(chunkX, chunkZ)); }

	public static int count() { return current().size(); }

	public static void clearCurrent() {
		String key = worldKey();
		BY_WORLD.put(key, new LongOpenHashSet());
		try {
			Files.deleteIfExists(dir().resolve(key + ".bin"));
		} catch (IOException ignored) {
			// nothing to delete
		}
	}

	public static void saveAll() {
		sinceSave = 0;
		for (Map.Entry<String, LongOpenHashSet> e : BY_WORLD.entrySet()) {
			Path file = dir().resolve(e.getKey() + ".bin");
			try {
				Files.createDirectories(file.getParent());
				try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(file)))) {
					for (long k : e.getValue()) out.writeLong(k);
				}
			} catch (IOException ignored) {
				// best effort
			}
		}
	}

	/** Writes "chunkX,chunkZ,blockX,blockZ" for every seen chunk of the current world; returns the file. */
	public static Path export() throws IOException {
		Files.createDirectories(dir());
		Path out = dir().resolve("export_" + worldKey() + ".csv");
		List<String> lines = new ArrayList<>();
		lines.add("chunkX,chunkZ,blockX,blockZ");
		for (long k : current()) {
			int cx = ChunkPos.getX(k), cz = ChunkPos.getZ(k);
			lines.add(cx + "," + cz + "," + (cx * 16 + 8) + "," + (cz * 16 + 8));
		}
		Files.write(out, lines);
		return out;
	}

	private static LongOpenHashSet read(Path file) {
		LongOpenHashSet out = new LongOpenHashSet();
		if (!Files.isRegularFile(file)) return out;
		try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(file)))) {
			while (true) out.add(in.readLong());
		} catch (java.io.EOFException done) {
			// end of file
		} catch (IOException ignored) {
			// keep what was read
		}
		return out;
	}
}
