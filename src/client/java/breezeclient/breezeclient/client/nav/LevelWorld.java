package breezeclient.breezeclient.client.nav;

import breezeclient.breezeclient.core.nav.Pathfinder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Adapts a ClientLevel to the pure pathfinder. Unloaded chunks and hazards are impassable. */
public final class LevelWorld implements Pathfinder.World {
	private final ClientLevel level;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private final boolean canPlace;

	public LevelWorld(ClientLevel level) { this(level, false); }

	/** @param canPlace true when the player carries a block the pathing may place */
	public LevelWorld(ClientLevel level, boolean canPlace) {
		this.level = level;
		this.canPlace = canPlace;
	}

	private BlockState state(int x, int y, int z) {
		if (y < level.getMinY() || y >= level.getMaxY()) return Blocks.AIR.defaultBlockState();
		return level.getBlockState(pos.set(x, y, z));
	}

	private boolean loaded(int x, int z) { return level.getChunkSource().hasChunk(x >> 4, z >> 4); }

	public static boolean hazard(BlockState s) {
		return !s.getFluidState().isEmpty()
			|| s.is(Blocks.MAGMA_BLOCK) || s.is(BlockTags.FIRE) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH)
			|| s.is(Blocks.WITHER_ROSE) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.COBWEB) || s.is(Blocks.CAMPFIRE)
			|| s.is(Blocks.SOUL_CAMPFIRE) || s.is(Blocks.POINTED_DRIPSTONE);
	}

	@Override
	public boolean passable(int x, int y, int z) {
		if (!loaded(x, z)) return false;
		BlockState s = state(x, y, z);
		if (hazard(s)) return false;
		return s.getCollisionShape(level, pos.set(x, y, z)).isEmpty();
	}

	@Override
	public boolean ground(int x, int y, int z) {
		if (!loaded(x, z)) return false;
		BlockState s = state(x, y, z);
		if (hazard(s)) return false;
		VoxelShape shape = s.getCollisionShape(level, pos.set(x, y, z));
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 1.0 && shape.max(Direction.Axis.Y) >= 0.5;
	}

	@Override
	public double breakCost(int x, int y, int z) {
		if (!loaded(x, z)) return -1;
		BlockState s = state(x, y, z);
		if (s.isAir() || hazard(s)) return -1;
		BlockPos p = new BlockPos(x, y, z);
		float hardness = s.getDestroySpeed(level, p);
		if (hardness < 0) return -1; // bedrock and friends
		if (level.getBlockEntity(p) != null) return -1; // containers, spawners, signs...
		NavModule nav = NavModule.instance;
		if (nav != null) {
			if (nav.protectedBlocks.contains(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString())) return -1;
			if (nav.avoidFlooding.get() && touchesLiquid(x, y, z)) return -1;
		}
		return 1.0 + hardness * 1.8;
	}

	private boolean touchesLiquid(int x, int y, int z) {
		int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int[] o : d) if (!state(x + o[0], y + o[1], z + o[2]).getFluidState().isEmpty()) return true;
		return false;
	}

	@Override
	public boolean placeable(int x, int y, int z) {
		if (!canPlace || !loaded(x, z)) return false;
		BlockState s = state(x, y, z);
		return s.isAir();
	}
}
