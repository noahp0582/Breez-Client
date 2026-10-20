package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.core.humanize.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Works out how a block can be placed by hand from a given eye position, the way the schematic "printers" do it: try
 * every neighbouring block as the one to click, sample points on the face that touches the target, keep the ones the eye
 * can really see, and check that clicking there gives the block the orientation the schematic wants. The orientation
 * check does not use a table of block types: it asks the block itself what state a click at that spot, looking that way,
 * would produce.
 */
final class Placer {
	private Placer() {}

	/** A click that places the block: where to look, and whether the result has exactly the wanted orientation. */
	record Plan(BlockHitResult hit, Rotation rot, boolean exact) {}

	/** Properties a player decides by where they stand and what they click. Everything else follows from the surroundings. */
	private static final List<Property<?>> ORIENTATION = List.of(BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING,
		BlockStateProperties.FACING_HOPPER, BlockStateProperties.AXIS, BlockStateProperties.HORIZONTAL_AXIS, BlockStateProperties.HALF,
		BlockStateProperties.ATTACH_FACE, BlockStateProperties.VERTICAL_DIRECTION);

	/** Sample positions across a face, the middle first so a centred click is preferred. */
	private static final double[][] GRID = {{0.5, 0.5}, {0.5, 0.22}, {0.5, 0.78}, {0.22, 0.5}, {0.78, 0.5}, {0.22, 0.22}, {0.78, 0.78}, {0.22, 0.78}, {0.78, 0.22}};

	/** True when {@code got} is the wanted block in the wanted orientation (connections, power, water and the like are ignored). */
	static boolean oriented(BlockState got, BlockState want) {
		if (got == null || got.getBlock() != want.getBlock()) return false;
		for (Property<?> prop : ORIENTATION) {
			if (want.hasProperty(prop) && (!got.hasProperty(prop) || !got.getValue(prop).equals(want.getValue(prop)))) return false;
		}
		if (want.hasProperty(BlockStateProperties.SLAB_TYPE)) {
			SlabType w = want.getValue(BlockStateProperties.SLAB_TYPE);
			if (w != SlabType.DOUBLE && got.getValue(BlockStateProperties.SLAB_TYPE) != w) return false; // a double slab is two clicks
		}
		return true;
	}

	/** True when the wanted state has anything a player has to get right by how they click. */
	static boolean hasOrientation(BlockState want) {
		for (Property<?> prop : ORIENTATION) if (want.hasProperty(prop)) return true;
		return want.hasProperty(BlockStateProperties.SLAB_TYPE);
	}

	/**
	 * True when the wanted state fixes which half of the cell the block fills (a top or bottom slab, an upright or
	 * upside-down stair or trapdoor). Getting that wrong is a differently shaped block, not one that can be turned later.
	 */
	static boolean halfMatters(BlockState want) {
		return want.hasProperty(BlockStateProperties.HALF)
			|| (want.hasProperty(BlockStateProperties.SLAB_TYPE) && want.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE);
	}

	/** A single slab that the schematic wants doubled: the second half is placed by clicking the slab itself. */
	static boolean halfOfDouble(BlockState cur, BlockState want) {
		return cur.getBlock() == want.getBlock() && want.hasProperty(BlockStateProperties.SLAB_TYPE) && want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE
			&& cur.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE;
	}

	private static Vec3 onFace(BlockPos pos, AABB b, Direction face, double u, double v) {
		double x = pos.getX(), y = pos.getY(), z = pos.getZ();
		double ax = b.minX + (b.maxX - b.minX) * u, ay = b.minY + (b.maxY - b.minY) * v, az = b.minZ + (b.maxZ - b.minZ) * u;
		return switch (face) {
			case UP -> new Vec3(x + ax, y + b.maxY, z + b.minZ + (b.maxZ - b.minZ) * v);
			case DOWN -> new Vec3(x + ax, y + b.minY, z + b.minZ + (b.maxZ - b.minZ) * v);
			case EAST -> new Vec3(x + b.maxX, y + ay, z + az);
			case WEST -> new Vec3(x + b.minX, y + ay, z + az);
			case SOUTH -> new Vec3(x + ax, y + ay, z + b.maxZ);
			case NORTH -> new Vec3(x + ax, y + ay, z + b.minZ);
		};
	}

	/**
	 * The grid points of one face, its middle first: a click in the middle still lands on the face when the crosshair stops
	 * a little beside the aim, one near an edge can land on the block next to it.
	 */
	private static List<Vec3> points(BlockPos pos, AABB b, Direction face) {
		List<Vec3> pts = new java.util.ArrayList<>(GRID.length);
		for (double[] g : GRID) pts.add(onFace(pos, b, face, g[0], g[1]));
		return pts;
	}

	/** A face to click, with how far its middle is from the eye. */
	private record Face(BlockPos block, Direction side, AABB box, double dist) {}

	/** Sorts candidate faces nearest first (by the distance from the eye to the middle of each face). */
	private static void nearestFirst(List<Face> faces) { faces.sort(java.util.Comparator.comparingDouble(Face::dist)); }

	/** The ray from the eye through a point, as the crosshair would see it. Null unless it lands on that block and face. */
	private static BlockHitResult sight(Minecraft mc, LocalPlayer p, Vec3 eye, Vec3 point, BlockPos block, Direction face, double reach) {
		Vec3 to = point.subtract(eye);
		double dist = to.length();
		if (dist > reach || dist < 1e-4) return null;
		Vec3 end = eye.add(to.scale((dist + 0.25) / dist));
		BlockHitResult clip = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		if (clip.getType() != HitResult.Type.BLOCK || !clip.getBlockPos().equals(block)) return null;
		if (face != null && clip.getDirection() != face) return null;
		if (entityInLine(p, eye, clip.getLocation())) return null;
		return clip;
	}

	/** True when a mob or another entity the crosshair would stop at is between the eye and the point. */
	private static boolean entityInLine(LocalPlayer p, Vec3 eye, Vec3 point) {
		AABB around = new AABB(eye, point).inflate(1.0);
		return ProjectileUtil.getEntityHitResult(p, eye, point, around, e -> !e.isSpectator() && e.isPickable(), eye.distanceToSqr(point)) != null;
	}

	/** True when an entity other than the player stands where the block would go: the server refuses such a placement. */
	private static boolean entityInSpace(Minecraft mc, LocalPlayer p, BlockPos pos, BlockState want) {
		VoxelShape shape = want.getCollisionShape(mc.level, pos);
		return !shape.isEmpty() && !mc.level.isUnobstructed(p, shape.move(pos.getX(), pos.getY(), pos.getZ()));
	}

	/**
	 * Whether a click planned at {@code hit} still works when the crosshair lands a little beside it, as it will: the
	 * camera stops within a mouse step of where it was sent. Four points around the hit must be seen on the same face
	 * and, for a block with an orientation, give that orientation too. This throws out clicks that only work through the
	 * crack between two blocks or exactly on the line between the top and bottom half of a face.
	 */
	private static boolean steady(Minecraft mc, LocalPlayer p, Vec3 eye, BlockHitResult hit, BlockState want, Item item, double reach, boolean orientedBlock) {
		Direction face = hit.getDirection();
		Vec3 at = hit.getLocation();
		double e = 0.07;
		for (int i = 0; i < 4; i++) {
			double a = (i < 2 ? e : 0) * (i % 2 == 0 ? 1 : -1), b = (i >= 2 ? e : 0) * (i % 2 == 0 ? 1 : -1);
			Vec3 near = switch (face.getAxis()) {
				case X -> at.add(0, a, b);
				case Y -> at.add(a, 0, b);
				case Z -> at.add(a, b, 0);
			};
			BlockHitResult h = sight(mc, p, eye, near, hit.getBlockPos(), face, reach + 0.1);
			if (h == null) return false;
			if (orientedBlock && !oriented(simulate(p, want, item, h, RotationController.rotationTo(eye, h.getLocation())), want)) return false;
		}
		return true;
	}

	/**
	 * The state the block would take if the player clicked {@code hit} while looking along {@code rot}. Null when it could
	 * not be placed there at all.
	 */
	static BlockState simulate(LocalPlayer p, BlockState want, Item item, BlockHitResult hit, Rotation rot) {
		float yaw = p.getYRot(), pitch = p.getXRot();
		p.setYRot(rot.yaw());
		p.setXRot(rot.pitch());
		try {
			BlockPlaceContext ctx = new BlockPlaceContext(p, InteractionHand.MAIN_HAND, new ItemStack(item), hit);
			if (!ctx.canPlace()) return null;
			return want.getBlock().getStateForPlacement(ctx);
		} catch (RuntimeException e) {
			return null; // a block that needs more than a click context: treat as not placeable this way
		} finally {
			p.setYRot(yaw);
			p.setXRot(pitch);
		}
	}

	/**
	 * Finds a click that puts {@code want} at {@code pos}, seen from {@code eye}.
	 *
	 * @param needExact only accept a click that gives the wanted orientation; otherwise an unoriented click is the fallback
	 */
	static Plan find(Minecraft mc, LocalPlayer p, BlockPos pos, BlockState want, Item item, Vec3 eye, double reach, boolean needExact) {
		if (!(item instanceof BlockItem bi)) return null;
		if (entityInSpace(mc, p, pos, want)) return null;
		BlockState cur = mc.level.getBlockState(pos);
		if (halfOfDouble(cur, want)) {
			Direction face = cur.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM ? Direction.UP : Direction.DOWN;
			AABB b = cur.getShape(mc.level, pos).bounds();
			for (Vec3 pt : points(pos, b, face)) {
				BlockHitResult hit = sight(mc, p, eye, pt, pos, face, reach);
				if (hit != null) return new Plan(hit, RotationController.rotationTo(eye, hit.getLocation()), true);
			}
			return null;
		}
		boolean orientedBlock = hasOrientation(want);
		// a torch on a wall is another block than a torch on the floor, and which one the item gives depends on the face clicked
		boolean wallVariant = item instanceof StandingAndWallBlockItem && bi.getBlock() != want.getBlock();
		boolean floorVariant = item instanceof StandingAndWallBlockItem && bi.getBlock() == want.getBlock();
		// the faces that could be clicked, nearest the eye first: the first one that is not covered (and works) is used
		List<Face> faces = new java.util.ArrayList<>(6);
		for (Direction d : Direction.values()) {
			Direction face = d.getOpposite();
			// that face of the neighbour looks back toward the target: the eye must be on the target's side of it to see it
			double side = (eye.x - (pos.getX() + 0.5)) * d.getStepX() + (eye.y - (pos.getY() + 0.5)) * d.getStepY() + (eye.z - (pos.getZ() + 0.5)) * d.getStepZ();
			if (side >= 0.5) continue;
			if (wallVariant && face.getAxis() == Direction.Axis.Y) continue;
			if (floorVariant && face != Direction.UP) continue;
			BlockPos support = pos.relative(d);
			BlockState ss = mc.level.getBlockState(support);
			if (ss.isAir() || ss.canBeReplaced()) continue;
			VoxelShape shape = ss.getShape(mc.level, support);
			if (shape.isEmpty()) continue;
			AABB b = shape.bounds();
			faces.add(new Face(support, face, b, eye.distanceToSqr(onFace(support, b, face, 0.5, 0.5))));
		}
		nearestFirst(faces);
		Plan fallback = null;
		for (Face f : faces) {
			for (Vec3 pt : points(f.block(), f.box(), f.side())) {
				BlockHitResult hit = sight(mc, p, eye, pt, f.block(), f.side(), reach);
				if (hit == null) continue; // covered there by another block (or out of reach)
				Rotation rot = RotationController.rotationTo(eye, hit.getLocation());
				BlockState got = simulate(p, want, item, hit, rot);
				if (got == null) continue;
				if (!orientedBlock || oriented(got, want)) {
					if (steady(mc, p, eye, hit, want, item, reach, orientedBlock)) return new Plan(hit, rot, true);
				} else if (fallback == null && steady(mc, p, eye, hit, want, item, reach, false)) {
					fallback = new Plan(hit, rot, false);
				}
			}
		}
		return needExact ? null : fallback;
	}

	/**
	 * A point on the block the eye can see within reach, or null: on the uncovered face nearest the eye (its middle if that
	 * is in sight), else the middle of the block if a ray gets there.
	 */
	static Vec3 visiblePoint(Minecraft mc, LocalPlayer p, BlockPos pos, Vec3 eye, double reach) {
		BlockState st = mc.level.getBlockState(pos);
		VoxelShape shape = st.getShape(mc.level, pos);
		if (shape.isEmpty()) return null;
		AABB b = shape.bounds();
		Vec3 mid = new Vec3(pos.getX() + (b.minX + b.maxX) / 2, pos.getY() + (b.minY + b.maxY) / 2, pos.getZ() + (b.minZ + b.maxZ) / 2);
		List<Face> faces = new java.util.ArrayList<>(3);
		for (Direction face : Direction.values()) {
			// only faces turned toward the eye can be seen at all
			double side = (eye.x - mid.x) * face.getStepX() + (eye.y - mid.y) * face.getStepY() + (eye.z - mid.z) * face.getStepZ();
			if (side > 0) faces.add(new Face(pos, face, b, eye.distanceToSqr(onFace(pos, b, face, 0.5, 0.5))));
		}
		nearestFirst(faces);
		for (Face f : faces) {
			for (Vec3 pt : points(pos, b, f.side())) {
				BlockHitResult hit = sight(mc, p, eye, pt, pos, f.side(), reach);
				if (hit != null) return hit.getLocation(); // the first face in sight is the nearest one that is not covered
			}
		}
		return sight(mc, p, eye, mid, pos, null, reach) != null ? mid : null;
	}
}
