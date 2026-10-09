package breezeclient.breezeclient.client.world;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared per-chunk block scanner. Runs on the client thread under a time budget; chunks are scanned on load and then
 * refreshed round-robin so block changes show up without needing packet mixins.
 */
public final class ChunkScanner {
	private static final Logger LOG = Logger.getLogger("breez-client");
	private static final long BUDGET_NANOS = 3_000_000L;

	private static final List<ScanTarget> TARGETS = new CopyOnWriteArrayList<>();
	private static final Set<Long> LOADED = new HashSet<>();
	private static final Deque<Long> QUEUE = new ArrayDeque<>();
	private static final List<Long> ROUND = new ArrayList<>();
	private static int roundIdx;
	private static ClientLevel lastLevel;

	private ChunkScanner() {}

	public static void register() {
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			long key = chunk.getPos().toLong();
			LOADED.add(key);
			QUEUE.addFirst(key);
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
			long key = chunk.getPos().toLong();
			LOADED.remove(key);
			QUEUE.remove(key);
			for (ScanTarget t : TARGETS) t.hits.remove(key);
		});
		ClientTickEvents.END_CLIENT_TICK.register(ChunkScanner::tick);
	}

	public static void add(ScanTarget t) {
		if (!TARGETS.contains(t)) {
			t.scannedVersion = -1;
			TARGETS.add(t);
		}
	}

	public static void remove(ScanTarget t) {
		TARGETS.remove(t);
		t.hits.clear();
	}

	public static Set<Long> loadedChunks() { return LOADED; }

	/** Force a rescan of every loaded chunk (used when the world changes). */
	public static void reset() {
		LOADED.clear();
		QUEUE.clear();
		for (ScanTarget t : TARGETS) t.hits.clear();
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			if (!LOADED.isEmpty()) reset();
			return;
		}
		if (level != lastLevel) { // dimension change swaps the level without unloading chunks one by one
			lastLevel = level;
			reset();
		}
		if (TARGETS.isEmpty()) return;

		for (ScanTarget t : TARGETS) {
			if (t.scannedVersion != t.version()) {
				t.scannedVersion = t.version();
				for (long key : LOADED) if (!QUEUE.contains(key)) QUEUE.addLast(key);
			}
		}
		// round-robin refresh so block changes are picked up
		if (QUEUE.isEmpty() && !LOADED.isEmpty()) {
			if (roundIdx >= ROUND.size()) {
				ROUND.clear();
				ROUND.addAll(LOADED);
				roundIdx = 0;
			}
			if (!ROUND.isEmpty()) QUEUE.addLast(ROUND.get(roundIdx++));
			if (roundIdx < ROUND.size()) QUEUE.addLast(ROUND.get(roundIdx++));
		}

		long start = System.nanoTime();
		while (!QUEUE.isEmpty() && System.nanoTime() - start < BUDGET_NANOS) {
			long key = QUEUE.pollFirst();
			if (!LOADED.contains(key)) continue;
			LevelChunk chunk = level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), false);
			if (chunk == null) continue;
			try {
				scan(chunk, key);
			} catch (RuntimeException e) {
				LOG.log(Level.WARNING, "chunk scan failed", e);
			}
		}
	}

	private static void scan(LevelChunk chunk, long key) {
		LevelChunkSection[] sections = chunk.getSections();
		int minSection = chunk.getMinY() >> 4;
		ChunkPos cp = chunk.getPos();
		for (ScanTarget t : TARGETS) {
			var pred = t.predicate();
			List<BlockPos> found = null;
			for (int i = 0; i < sections.length; i++) {
				LevelChunkSection s = sections[i];
				if (s == null || s.hasOnlyAir() || !s.maybeHas(pred)) continue;
				int baseY = (minSection + i) << 4;
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							if (pred.test(s.getBlockState(x, y, z))) {
								if (found == null) found = new ArrayList<>();
								found.add(new BlockPos(cp.getMinBlockX() + x, baseY + y, cp.getMinBlockZ() + z));
							}
						}
					}
				}
			}
			if (found == null) t.hits.remove(key);
			else t.hits.put(key, found);
		}
	}
}
