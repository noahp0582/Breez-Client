package breezeclient.breezeclient.client.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A named block query. ChunkScanner keeps {@link #hits} up to date for every loaded chunk while registered. */
public final class ScanTarget {
	private volatile Predicate<BlockState> predicate;
	private volatile int version;
	final Map<Long, List<BlockPos>> hits = new ConcurrentHashMap<>();
	int scannedVersion = -1;

	public ScanTarget(Predicate<BlockState> predicate) { this.predicate = predicate; }

	public static ScanTarget ofBlocks(Set<Block> blocks) { return new ScanTarget(s -> blocks.contains(s.getBlock())); }

	public Predicate<BlockState> predicate() { return predicate; }
	public int version() { return version; }

	/** Changing the predicate triggers a rescan of all loaded chunks. */
	public void setPredicate(Predicate<BlockState> p) {
		this.predicate = p;
		this.version++;
	}

	public Collection<List<BlockPos>> chunks() { return hits.values(); }

	public int count() {
		int n = 0;
		for (List<BlockPos> l : hits.values()) n += l.size();
		return n;
	}

	public int countInChunk(long chunkKey) {
		List<BlockPos> l = hits.get(chunkKey);
		return l == null ? 0 : l.size();
	}

	public void forEachWithin(Vec3 center, double range, Consumer<BlockPos> out) {
		double r2 = range * range;
		for (List<BlockPos> l : hits.values()) {
			for (BlockPos p : l) {
				double dx = p.getX() + 0.5 - center.x, dy = p.getY() + 0.5 - center.y, dz = p.getZ() + 0.5 - center.z;
				if (dx * dx + dy * dy + dz * dz <= r2) out.accept(p);
			}
		}
	}
}
