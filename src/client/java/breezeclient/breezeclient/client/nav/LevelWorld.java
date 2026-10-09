package breezeclient.breezeclient.client.nav;

import breezeclient.breezeclient.core.nav.Pathfinder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Adapts a ClientLevel to the pure pathfinder. Unloaded chunks and hazards are impassable. */
public final class LevelWorld implements Pathfinder.World {
	private final ClientLevel level;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	public LevelWorld(ClientLevel level) { this.level = level; }

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
}
