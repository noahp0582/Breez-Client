package breezeclient.breezeclient.client.nav;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.core.humanize.Rotations;
import breezeclient.breezeclient.core.nav.Pathfinder;
import breezeclient.breezeclient.core.nav.Pos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Plans a route with the pathfinder, then walks it with key presses and eased camera turns. */
public final class PathWalker {
	public enum State { IDLE, WALKING, ARRIVED, FAILED }

	private List<Pos> path = List.of();
	private int idx;
	private State state = State.IDLE;
	private Vec3 lastPos = Vec3.ZERO;
	private int stuckChecks;
	private int tick;
	private Pos goal;
	private double radius;

	public State state() { return state; }

	public boolean walking() { return state == State.WALKING; }

	/** Start walking toward a point; arrival is any standable cell within {@code radius} of {@code goal}. */
	public boolean start(Pos goal, double radius) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) return fail();
		this.goal = goal;
		this.radius = radius;
		Pos here = new Pos(p.getBlockX(), p.blockPosition().getY(), p.getBlockZ());
		if (here.dist(goal.x(), goal.y(), goal.z()) <= radius) {
			state = State.ARRIVED;
			path = List.of();
			return true;
		}
		List<Pos> route = Pathfinder.find(new LevelWorld(mc.level), here, goal, radius, 3, 30000);
		if (route == null) return fail();
		path = route;
		idx = 0;
		stuckChecks = 0;
		tick = 0;
		lastPos = p.position();
		state = State.WALKING;
		return true;
	}

	private boolean fail() {
		state = State.FAILED;
		releaseKeys();
		return false;
	}

	public void cancel() {
		state = State.IDLE;
		path = List.of();
		releaseKeys();
	}

	public void releaseKeys() {
		Minecraft mc = Minecraft.getInstance();
		mc.options.keyUp.setDown(false);
		mc.options.keyJump.setDown(false);
		mc.options.keySprint.setDown(false);
	}

	/** Call once per client tick while walking. */
	public void tick() {
		if (state != State.WALKING) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			fail();
			return;
		}
		if (idx >= path.size()) {
			finish();
			return;
		}
		Pos node = path.get(idx);
		double cx = node.x() + 0.5, cz = node.z() + 0.5;
		double dx = cx - p.getX(), dz = cz - p.getZ();
		double horiz = Math.sqrt(dx * dx + dz * dz);
		boolean sameLevel = Math.abs(p.getY() - node.y()) < 1.1;
		if (horiz < 0.3 && sameLevel) {
			idx++;
			if (idx >= path.size()) {
				finish();
				return;
			}
			node = path.get(idx);
			cx = node.x() + 0.5;
			cz = node.z() + 0.5;
			dx = cx - p.getX();
			dz = cz - p.getZ();
			horiz = Math.sqrt(dx * dx + dz * dz);
		}

		float yawTo = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		RotationController.aimAt(new Rotation(yawTo, 8f), 16f);
		float err = Math.abs(Rotations.wrapDegrees(yawTo - p.getYRot()));
		mc.options.keyUp.setDown(err < 35f || horiz < 0.6);
		boolean needsStep = node.y() > p.getY() + 0.4;
		mc.options.keyJump.setDown(p.onGround() && (needsStep || p.horizontalCollision));

		if (++tick % 20 == 0) {
			if (p.position().distanceToSqr(lastPos) < 0.04) {
				if (++stuckChecks >= 3) {
					fail();
					return;
				}
			} else {
				stuckChecks = 0;
			}
			lastPos = p.position();
		}
	}

	private void finish() {
		releaseKeys();
		RotationController.clear();
		Minecraft mc = Minecraft.getInstance();
		boolean near = mc.player != null && goal != null && new Vec3(goal.x() + 0.5, goal.y() + 0.5, goal.z() + 0.5).distanceTo(mc.player.position()) <= radius + 1.5;
		state = near ? State.ARRIVED : State.FAILED;
	}

	public static Pos posOf(BlockPos p) { return new Pos(p.getX(), p.getY(), p.getZ()); }
}
