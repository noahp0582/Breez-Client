package breezeclient.breezeclient.core.schem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Non-air blocks of a schematic, coordinates normalized so the minimum corner is (0,0,0). */
public record SchematicData(int sizeX, int sizeY, int sizeZ, List<Block> blocks) {
	/** @param state blockstate string such as {@code minecraft:oak_stairs[facing=north,half=bottom]} */
	public record Block(int x, int y, int z, String state) {}

	public static boolean isAir(String state) {
		return state.startsWith("minecraft:air") || state.startsWith("minecraft:cave_air") || state.startsWith("minecraft:void_air");
	}

	/**
	 * Build order: bottom layer first; inside a layer, rows along X, alternating direction each row (serpentine), so the
	 * camera sweeps in straight lines instead of jumping around.
	 */
	public List<Block> linearOrder() {
		List<Block> out = new ArrayList<>(blocks);
		out.sort(Comparator
			.comparingInt(Block::y)
			.thenComparingInt(Block::z)
			.thenComparingInt(b -> (b.z() % 2 == 0) ? b.x() : -b.x()));
		return out;
	}
}
