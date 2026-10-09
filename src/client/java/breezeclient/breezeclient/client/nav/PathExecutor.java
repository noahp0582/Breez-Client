package breezeclient.breezeclient.client.nav;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.core.humanize.Rotations;
import breezeclient.breezeclient.core.nav.Pathfinder.Move;
import breezeclient.breezeclient.core.nav.Pathfinder.Step;
import breezeclient.breezeclient.core.nav.Pos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Walks a list of path steps with key presses and eased camera turns: walking, jumping, sprint-jumps, mining through blocks,
 * digging down, pillaring and bridging. Reports DONE or FAILED; the controller decides whether to replan.
 */
public final class PathExecutor {
	public enum Status { IDLE, RUNNING, DONE, FAILED }

	private List<Step> steps = List.of();
	private int idx;
	private Pos origin;
	private Status status = Status.IDLE;
	private String failReason = "";

	private int stepTicks;
	private int noProgress;
	private Vec3 lastPos = Vec3.ZERO;
	private int placeCooldown;
	private int mineTicks;
	private int breakIdx;
	private boolean jumpedForParkour;

	public Status status() { return status; }

	public String failReason() { return failReason; }

	public boolean running() { return status == Status.RUNNING; }

	public int remaining() { return Math.max(0, steps.size() - idx); }

	/** Final cell of the current path, or null. */
	public Pos end() { return steps.isEmpty() ? null : steps.get(steps.size() - 1).pos(); }

	public void start(List<Step> path, Pos from) {
		release();
		steps = path;
		origin = from;
		idx = 0;
		status = path.isEmpty() ? Status.DONE : Status.RUNNING;
		failReason = "";
		resetStep();
		var p = Minecraft.getInstance().player;
		lastPos = p == null ? Vec3.ZERO : p.position();
	}

	public void cancel() {
		release();
		status = Status.IDLE;
		steps = List.of();
	}

	private void resetStep() {
		stepTicks = 0;
		noProgress = 0;
		placeCooldown = 0;
		mineTicks = 0;
		breakIdx = 0;
		jumpedForParkour = false;
	}

	private void fail(String why) {
		release();
		status = Status.FAILED;
		failReason = why;
	}

	private void done() {
		release();
		RotationController.clear();
		status = Status.DONE;
	}

	/** Lets go of every key this executor may hold. */
	public void release() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null) return;
		mc.options.keyUp.setDown(false);
		mc.options.keyJump.setDown(false);
		mc.options.keySprint.setDown(false);
		mc.options.keyShift.setDown(false);
		mc.options.keyAttack.setDown(false);
	}

	// per tick ------------------------------------------------------------------------------------------------------------

	public void tick() {
		if (status != Status.RUNNING) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		ClientLevel level = mc.level;
		if (p == null || level == null || mc.gameMode == null) {
			fail("not in a world");
			return;
		}
		if (mc.screen != null) { // never fight with an open menu
			release();
			return;
		}
		if (idx >= steps.size()) {
			done();
			return;
		}
		// skip steps we are already standing on (overshot or fell onto a later cell)
		for (int k = Math.min(steps.size() - 1, idx + 2); k >= idx; k--) {
			if (k > idx && arrived(p, steps.get(k)) && steps.get(k).move() != Move.PILLAR) {
				idx = k;
				break;
			}
		}
		Step s = steps.get(idx);
		if (arrived(p, s)) {
			idx++;
			resetStep();
			if (idx >= steps.size()) done();
			return;
		}
		Pos prev = idx == 0 ? origin : steps.get(idx - 1).pos();
		stepTicks++;
		if (p.getY() < Math.min(prev.y(), s.pos().y()) - 2.2) {
			fail("fell off the path");
			return;
		}
		NavModule nav = NavModule.instance;
		int stuckTicks = (nav == null ? 4 : nav.stuckSeconds.asInt()) * 20;
		if (p.position().distanceToSqr(lastPos) > 0.0025) {
			lastPos = p.position();
			noProgress = 0;
		} else if (++noProgress > stuckTicks) {
			fail("stuck at " + s.move());
			return;
		}

		switch (s.move()) {
			case WALK, DIAGONAL, DESCEND -> walk(p, s, nav != null && nav.sprint.get() && idx < steps.size() - 1 && s.move() != Move.DESCEND, false);
			case ASCEND -> walk(p, s, false, true);
			case PARKOUR -> parkour(p, prev, s);
			case BREAK -> breakThenWalk(mc, level, p, s);
			case DIG_DOWN -> digDown(mc, level, p, s);
			case PILLAR -> pillar(mc, p, prev, s);
			case BRIDGE -> bridge(mc, level, p, prev, s);
		}
	}

	private boolean arrived(LocalPlayer p, Step s) {
		Pos t = s.pos();
		double dx = t.x() + 0.5 - p.getX(), dz = t.z() + 0.5 - p.getZ();
		boolean horizontal = Math.sqrt(dx * dx + dz * dz) < (s.move() == Move.PARKOUR ? 0.75 : 0.45);
		return horizontal && Math.abs(p.getY() - t.y()) < 0.6 && p.onGround();
	}

	// movement kinds ---------------------------------------------------------------------------------------------------------

	/** Face the target and push forward; jump when the next cell is higher or something blocks us. */
	private void walk(LocalPlayer p, Step s, boolean sprint, boolean jumpUp) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 target = new Vec3(s.pos().x() + 0.5, s.pos().y(), s.pos().z() + 0.5);
		face(p, target, 16f);
		double dx = target.x - p.getX(), dz = target.z - p.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		float err = Math.abs(Rotations.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0) - p.getYRot()));
		mc.options.keyUp.setDown(err < 40f || dist < 0.6);
		mc.options.keySprint.setDown(sprint && err < 20f);
		boolean higher = target.y > p.getY() + 0.4;
		mc.options.keyJump.setDown(p.onGround() && ((higher && dist < 1.4) || (jumpUp && higher) || p.horizontalCollision));
	}

	private void parkour(LocalPlayer p, Pos prev, Step s) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 target = new Vec3(s.pos().x() + 0.5, s.pos().y(), s.pos().z() + 0.5);
		face(p, target, 22f);
		mc.options.keyUp.setDown(true);
		mc.options.keySprint.setDown(true);
		int dx = Integer.signum(s.pos().x() - prev.x()), dz = Integer.signum(s.pos().z() - prev.z());
		// distance from the take-off block's far edge, along the jump direction (negative = still on the block)
		double edge = dx != 0 ? (dx > 0 ? prev.x() + 1 - p.getX() : p.getX() - prev.x()) : (dz > 0 ? prev.z() + 1 - p.getZ() : p.getZ() - prev.z());
		double fromCenter = dx != 0 ? Math.abs(p.getX() - (prev.x() + 0.5)) : Math.abs(p.getZ() - (prev.z() + 0.5));
		boolean ready = p.onGround() && edge < 0.3 && (fromCenter > 0.15 || stepTicks > 14);
		mc.options.keyJump.setDown(ready);
		if (ready) jumpedForParkour = true;
	}

	private void breakThenWalk(Minecraft mc, ClientLevel level, LocalPlayer p, Step s) {
		while (breakIdx < s.breaks().size()) {
			Pos b = s.breaks().get(breakIdx);
			BlockPos cell = new BlockPos(b.x(), b.y(), b.z());
			if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
				breakIdx++;
				mc.options.keyAttack.setDown(false);
				mineTicks = 0;
				continue;
			}
			if (!mine(mc, level, p, cell)) return;
		}
		mc.options.keyAttack.setDown(false);
		walk(p, s, false, true);
	}

	private void digDown(Minecraft mc, ClientLevel level, LocalPlayer p, Step s) {
		BlockPos cell = new BlockPos(s.pos().x(), s.pos().y(), s.pos().z());
		if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
			mc.options.keyAttack.setDown(false);
			mc.options.keyUp.setDown(false);
			return; // falls into the hole; arrival check ends the step
		}
		// stand in the middle of the column so the dig does not drop us at an angle
		Vec3 center = new Vec3(cell.getX() + 0.5, p.getY(), cell.getZ() + 0.5);
		double off = Math.hypot(center.x - p.getX(), center.z - p.getZ());
		if (off > 0.3) {
			face(p, center, 16f);
			mc.options.keyUp.setDown(true);
			return;
		}
		mc.options.keyUp.setDown(false);
		mine(mc, level, p, cell);
	}

	/** Breaks one block with the best hotbar tool. @return true once the block is gone. */
	public boolean mine(Minecraft mc, ClientLevel level, LocalPlayer p, BlockPos cell) {
		BlockState st = level.getBlockState(cell);
		if (st.getCollisionShape(level, cell).isEmpty()) return true;
		NavModule nav = NavModule.instance;
		if (nav != null && nav.avoidFlooding.get()) {
			for (Direction d : Direction.values()) {
				if (d == Direction.DOWN) continue;
				if (!level.getBlockState(cell.relative(d)).getFluidState().isEmpty()) {
					fail("liquid next to the block to break");
					return false;
				}
			}
		}
		int slot = bestTool(p, st);
		if (slot >= 0) p.getInventory().setSelectedSlot(slot);
		Vec3 center = Vec3.atCenterOf(cell);
		if (RotationController.busy() || !(mc.hitResult instanceof BlockHitResult bh && bh.getBlockPos().equals(cell))) {
			RotationController.aimAtPoint(center, 14f);
			mc.options.keyAttack.setDown(false);
			if (++mineTicks > 60) fail("cannot see the block to break");
			return false;
		}
		mc.options.keyAttack.setDown(true);
		mineTicks = 0;
		noProgress = 0; // breaking counts as progress
		return false;
	}

	private int bestTool(LocalPlayer p, BlockState st) {
		int best = p.getInventory().getSelectedSlot();
		float speed = p.getInventory().getItem(best).getDestroySpeed(st);
		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			float v = s.getDestroySpeed(st);
			if (v > speed) {
				speed = v;
				best = i;
			}
		}
		return best;
	}

	private void pillar(Minecraft mc, LocalPlayer p, Pos prev, Step s) {
		NavModule nav = NavModule.instance;
		int slot = nav == null ? -1 : nav.placeableSlot(p);
		if (slot < 0) {
			fail("no blocks to pillar with");
			return;
		}
		p.getInventory().setSelectedSlot(slot);
		RotationController.aimAt(new Rotation(p.getYRot(), 90f), 25f);
		mc.options.keyUp.setDown(false);
		double dx = prev.x() + 0.5 - p.getX(), dz = prev.z() + 0.5 - p.getZ();
		if (Math.hypot(dx, dz) > 0.25) { // drift back to the column center
			mc.options.keyUp.setDown(false);
		}
		mc.options.keyJump.setDown(p.onGround() || p.getY() < prev.y() + 1.0);
		if (placeCooldown > 0) placeCooldown--;
		if (!p.onGround() && p.getY() >= prev.y() + 1.0 && placeCooldown == 0) {
			BlockHitResult hit = new BlockHitResult(new Vec3(prev.x() + 0.5, prev.y(), prev.z() + 0.5), Direction.UP, new BlockPos(prev.x(), prev.y() - 1, prev.z()), false);
			mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
			p.swing(InteractionHand.MAIN_HAND);
			placeCooldown = 6;
		}
	}

	private void bridge(Minecraft mc, ClientLevel level, LocalPlayer p, Pos prev, Step s) {
		NavModule nav = NavModule.instance;
		int slot = nav == null ? -1 : nav.placeableSlot(p);
		BlockPos under = new BlockPos(s.pos().x(), s.pos().y() - 1, s.pos().z());
		boolean needsBlock = level.getBlockState(under).isAir();
		if (needsBlock && slot < 0) {
			fail("no blocks to bridge with");
			return;
		}
		mc.options.keyShift.setDown(true); // sneaking keeps us on the edge
		if (needsBlock) {
			p.getInventory().setSelectedSlot(slot);
			Direction face = Direction.fromYRot(0);
			int dx = s.pos().x() - prev.x(), dz = s.pos().z() - prev.z();
			for (Direction d : Direction.Plane.HORIZONTAL) if (d.getStepX() == dx && d.getStepZ() == dz) face = d;
			Vec3 loc = new Vec3(prev.x() + 0.5 + dx * 0.5, prev.y() - 0.5, prev.z() + 0.5 + dz * 0.5);
			RotationController.aimAtPoint(loc, 16f);
			mc.options.keyUp.setDown(false);
			if (placeCooldown > 0) placeCooldown--;
			if (!RotationController.busy() && placeCooldown == 0) {
				BlockPos support = new BlockPos(prev.x(), prev.y() - 1, prev.z());
				if (level.getBlockState(support).isAir()) { // our own previous bridge block may still be in flight
					placeCooldown = 3;
					return;
				}
				mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(loc, face, support, false));
				p.swing(InteractionHand.MAIN_HAND);
				placeCooldown = 4;
			}
		} else {
			walk(p, s, false, false);
			mc.options.keyShift.setDown(true);
		}
	}

	private void face(LocalPlayer p, Vec3 target, float maxStep) {
		double dx = target.x - p.getX(), dz = target.z - p.getZ();
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		RotationController.aimAt(new Rotation(yaw, 8f), maxStep);
	}
}
