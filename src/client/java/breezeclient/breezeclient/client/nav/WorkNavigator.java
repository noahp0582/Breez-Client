package breezeclient.breezeclient.client.nav;

import breezeclient.breezeclient.core.nav.Goal;
import breezeclient.breezeclient.core.nav.Pathfinder;
import breezeclient.breezeclient.core.nav.Pathfinder.Move;
import breezeclient.breezeclient.core.nav.Pathfinder.Step;
import breezeclient.breezeclient.core.nav.Pos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * Gets a working module (the schematic builder, AutoDig) to a place it can work from. Instead of picking a spot and
 * hoping there is a way to it, the path search itself looks for the nearest standing place from which the caller says
 * work is possible, so the result is always reachable. The route uses the full pathfinder: steps up and down, drops,
 * sprint jumps over gaps and, when allowed, pillaring and bridging with spare blocks. It never breaks anything.
 */
public final class WorkNavigator {
	/** Whether some work can be done by a player whose feet are in this cell (eye at {@code eye}). */
	public interface Spot {
		boolean worksFrom(Pos feet, Vec3 eye);
	}

	/** Most cells one search may ask the caller about; the nearest ones come first, so a spot that exists is found well before this. */
	private static final int MAX_SPOT_CHECKS = 160;

	public enum State { IDLE, WALKING, ARRIVED, FAILED }

	public enum Start { OK, NO_ROUTE, NOT_READY }

	private final PathExecutor exec = new PathExecutor();
	private final List<BlockPos> scaffold = new ArrayList<>();
	private State state = State.IDLE;
	private boolean partial;

	public State state() { return state; }

	/** True when the last route only led toward the work (the search ran out before reaching a spot). */
	public boolean partial() { return partial; }

	/** Cells where a route planned to put a spare block (pillar or bridge). The caller may clear them up afterwards. */
	public List<BlockPos> scaffold() { return scaffold; }

	public String failReason() { return exec.failReason(); }

	/** The search goal: any cell near a target from which the caller can work. */
	private static final class WorkGoal implements Goal {
		private final List<BlockPos> targets;
		private final double reach;
		private final double eyeHeight;
		private final Spot spot;
		private final Map<Pos, Boolean> seen = new HashMap<>();
		private final net.minecraft.client.multiplayer.ClientLevel level;
		private final Pos start;
		private final Vec3 startEye;
		/** Cells the caller has been asked about. Each ask can cost hundreds of raycasts, so one search only gets so many. */
		private int asked;

		WorkGoal(List<BlockPos> targets, double reach, double eyeHeight, Spot spot, net.minecraft.client.multiplayer.ClientLevel level, Pos start, Vec3 startEye) {
			this.level = level;
			this.start = start;
			this.startEye = startEye;
			this.targets = targets;
			this.reach = reach;
			this.eyeHeight = eyeHeight;
			this.spot = spot;
		}

		@Override
		public boolean isEnd(Pos p) {
			Boolean known = seen.get(p);
			if (known != null) return known;
			double ex = p.x() + 0.5, ey = floorY(p) + eyeHeight, ez = p.z() + 0.5;
			if (p.equals(start)) { // where the player is now: the real eye, not the middle of the cell
				ex = startEye.x;
				ey = startEye.y;
				ez = startEye.z;
			}
			boolean near = false;
			for (BlockPos t : targets) {
				double dx = t.getX() + 0.5 - ex, dy = t.getY() + 0.5 - ey, dz = t.getZ() + 0.5 - ez;
				if (dx * dx + dy * dy + dz * dz <= (reach + 0.9) * (reach + 0.9)) {
					near = true;
					break;
				}
			}
			boolean ok = near && asked++ < MAX_SPOT_CHECKS && spot.worksFrom(p, new Vec3(ex, ey, ez));
			seen.put(p, ok);
			return ok;
		}

		/** Height the feet are at in this cell: the top of what is under it, which for a slab is half a block lower than the cell. */
		private double floorY(Pos p) {
			BlockPos below = new BlockPos(p.x(), p.y() - 1, p.z());
			var shape = level.getBlockState(below).getCollisionShape(level, below);
			return shape.isEmpty() ? p.y() : p.y() - 1 + Math.min(1.0, shape.max(net.minecraft.core.Direction.Axis.Y));
		}

		@Override
		public double heuristic(Pos p) {
			double best = Double.MAX_VALUE;
			for (BlockPos t : targets) {
				double ax = Math.abs(t.getX() - p.x()), az = Math.abs(t.getZ() - p.z());
				double diag = Math.min(ax, az);
				double h = diag * 1.414 + (Math.max(ax, az) - diag) + Math.abs(t.getY() - p.y()) * 0.6 - reach;
				if (h < best) best = h;
			}
			return targets.isEmpty() ? 0 : Math.max(0, best);
		}
	}

	/** The level as the pathfinder sees it for a working module: nothing may be broken, spare blocks only go where allowed. */
	private static final class WorkWorld implements Pathfinder.World {
		private final LevelWorld base;
		private final LongPredicate noPlace;

		WorkWorld(LevelWorld base, LongPredicate noPlace) {
			this.base = base;
			this.noPlace = noPlace;
		}

		@Override
		public boolean passable(int x, int y, int z) { return base.passable(x, y, z); }

		@Override
		public boolean ground(int x, int y, int z) { return base.ground(x, y, z); }

		@Override
		public boolean placeable(int x, int y, int z) { return base.placeable(x, y, z) && !noPlace.test(BlockPos.asLong(x, y, z)); }
	}

	/**
	 * The cell the player stands in, as the pathfinder counts cells. Standing on a slab or another low block puts the feet
	 * halfway up a cell: the cell above is then the one with room for the body, and the low block is its ground.
	 */
	public static Pos feet(LocalPlayer p) {
		double y = p.getY();
		double up = y - Math.floor(y);
		return new Pos(p.getBlockX(), (int) Math.floor(y) + (up >= 0.4 ? 1 : 0), p.getBlockZ());
	}

	/**
	 * Plans a route to the nearest cell from which work is possible on one of the targets and starts walking it.
	 *
	 * @param targets    blocks to work on (a few dozen at most: they are checked for every cell the search looks at)
	 * @param reach      how far from the eye a block may be worked on
	 * @param eyeHeight  eye height the caller works at (lower when it sneaks)
	 * @param allowPlace pillar up and bridge with spare blocks from the hotbar when walking is not enough
	 * @param noPlace    cells that must never get a spare block (packed block positions)
	 */
	public Start start(Minecraft mc, List<BlockPos> targets, double reach, double eyeHeight, Spot spot, boolean allowPlace, LongPredicate noPlace) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || targets.isEmpty()) return fail(Start.NO_ROUTE);
		if (!p.onGround()) return Start.NOT_READY; // wait to land before planning
		NavModule nav = NavModule.instance;
		boolean place = allowPlace && nav != null && nav.hasPlaceable();
		Pathfinder.Options opts = new Pathfinder.Options(false, place, nav == null || nav.allowParkour.get(), nav == null ? 3 : Math.min(3, nav.maxFall.asInt()), 1.0, 6.0);
		Pos here = feet(p);
		WorkGoal goal = new WorkGoal(targets, reach, eyeHeight, spot, mc.level, here, p.position().add(0, eyeHeight, 0));
		Pathfinder.Result r = Pathfinder.search(LevelWorld.fromCrouch(new WorkWorld(new LevelWorld(mc.level, place), noPlace), mc.level, here.x(), here.y(), here.z()), here, goal, opts, 5000);
		if (r == null) return Start.NOT_READY; // standing somewhere the search cannot start from (mid-step, on a slab edge)
		if (r.reachedGoal() && r.steps().isEmpty()) { // already there
			state = State.ARRIVED;
			partial = false;
			return Start.OK;
		}
		if (r.steps().isEmpty()) return fail(Start.NO_ROUTE);
		partial = !r.reachedGoal();
		Pos prev = here;
		for (Step s : r.steps()) {
			BlockPos spare = s.move() == Move.PILLAR ? new BlockPos(prev.x(), prev.y(), prev.z())
				: s.move() == Move.BRIDGE ? new BlockPos(s.pos().x(), s.pos().y() - 1, s.pos().z()) : null;
			// only a cell that is empty now can later hold a block of ours
			if (spare != null && mc.level.getBlockState(spare).isAir()) scaffold.add(spare);
			prev = s.pos();
		}
		exec.start(r.steps(), here);
		state = State.WALKING;
		return Start.OK;
	}

	private Start fail(Start s) {
		state = State.FAILED;
		return s;
	}

	/** Call once per client tick. */
	public void tick() {
		if (state != State.WALKING) return;
		exec.tick();
		switch (exec.status()) {
			case DONE -> state = State.ARRIVED;
			case FAILED, IDLE -> state = State.FAILED;
			default -> { }
		}
	}

	public void cancel() {
		exec.cancel();
		state = State.IDLE;
	}

	/** Lets go of the movement keys without dropping the route (a menu opened). */
	public void releaseKeys() { exec.release(); }
}
