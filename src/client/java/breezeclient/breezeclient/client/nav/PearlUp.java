package breezeclient.breezeclient.client.nav;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.core.humanize.Rotations;
import breezeclient.breezeclient.core.nav.Pos;
import breezeclient.breezeclient.core.physics.PearlAim;
import breezeclient.breezeclient.core.physics.Trajectory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Gets a working module up onto a tall build with an ender pearl, where walking and pillaring would take a long time or
 * cannot get there at all. It picks a flat spot near the work (so the pearl's small random spread still lands on it),
 * works out the pitch by simulating the pearl's flight through the real blocks, looks there, throws and waits to land.
 */
public final class PearlUp {
	/** A place to land on and the throw that gets there. */
	public record Landing(BlockPos feet, PearlAim.Shot shot) {}

	public enum Status { IDLE, WORKING, LANDED, FAILED }

	private static final int MAX_CANDIDATES = 40, MAX_SOLVES = 8;

	private Landing landing;
	private Status status = Status.IDLE;
	private int stage, ticks, back = -1;
	private boolean creative;
	private String failReason = "";

	public Status status() { return status; }

	public Landing landing() { return landing; }

	public String failReason() { return failReason; }

	/** True when the player has a pearl anywhere in the inventory (or may take one in creative). */
	public static boolean havePearl(LocalPlayer p, boolean creativeSupply) {
		if (creativeSupply && p.hasInfiniteMaterials()) return true;
		if (p.getOffhandItem().is(Items.ENDER_PEARL)) return true;
		for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).is(Items.ENDER_PEARL)) return true;
		return false;
	}

	/** Pearls cost 5 health on landing: only throw with enough of it to spare. */
	public static boolean healthy(LocalPlayer p) {
		return p.hasInfiniteMaterials() || p.isInvulnerable() || p.getHealth() + p.getAbsorptionAmount() > 10f;
	}

	/** Where a pearl thrown right now starts: the eye of a standing player, a tenth of a block lower. */
	private static Trajectory.Point throwStart(LocalPlayer p) {
		return new Trajectory.Point(p.getX(), p.getY() + p.getEyeHeight(Pose.STANDING) - 0.1, p.getZ());
	}

	private static Trajectory.Collider collider(Minecraft mc) {
		return (a, b) -> {
			var hit = mc.level.clip(new ClipContext(new Vec3(a.x(), a.y(), a.z()), new Vec3(b.x(), b.y(), b.z()), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
			if (hit.getType() == HitResult.Type.MISS) return null;
			Vec3 l = hit.getLocation();
			return new Trajectory.Point(l.x, l.y, l.z);
		};
	}

	/** Feet can be in this cell: two cells of room and something to stand on that is not dangerous. */
	private static boolean standable(LevelWorld w, int x, int y, int z) {
		return w.ground(x, y - 1, z) && w.passable(x, y, z) && w.passable(x, y + 1, z);
	}

	/** Height the feet end up at in a standable cell: full blocks only, so a landing on a slab edge does not count as flat. */
	private static boolean fullFloor(Minecraft mc, int x, int y, int z) {
		BlockPos below = new BlockPos(x, y - 1, z);
		var shape = mc.level.getBlockState(below).getCollisionShape(mc.level, below);
		return !shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) >= 0.999;
	}

	/**
	 * Looks for a spot at least {@code minRise} blocks above the player, near the targets, from which the caller can work,
	 * and a throw that lands on it.
	 *
	 * @param avoid cells that must stay free (blocks still to be placed there)
	 * @param bad   landing cells (packed) that failed before: skipped
	 */
	public static Landing find(Minecraft mc, List<BlockPos> targets, double reach, WorkNavigator.Spot spot, LongPredicate avoid, int minRise, Set<Long> bad) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || targets.isEmpty()) return null;
		LevelWorld w = new LevelWorld(mc.level);
		Pos here = WorkNavigator.feet(p);
		double eyeH = p.getEyeHeight(Pose.CROUCHING);
		Set<Long> seen = new HashSet<>();
		record Cand(BlockPos feet, int flat, double dist) {}
		List<Cand> cands = new ArrayList<>();
		int span = (int) Math.ceil(reach);
		for (BlockPos t : targets) {
			for (int dy = -span - 1; dy <= 1; dy++) {
				int y = t.getY() + dy;
				if (y - here.y() < minRise) continue;
				for (int dx = -span; dx <= span; dx++) {
					for (int dz = -span; dz <= span; dz++) {
						int x = t.getX() + dx, z = t.getZ() + dz;
						long key = BlockPos.asLong(x, y, z);
						if (!seen.add(key) || bad.contains(key)) continue;
						if (avoid.test(key) || avoid.test(BlockPos.asLong(x, y + 1, z))) continue;
						if (!mc.level.getChunkSource().hasChunk(x >> 4, z >> 4) || !standable(w, x, y, z) || !fullFloor(mc, x, y, z)) continue;
						int flat = 0;
						for (int ax = -1; ax <= 1; ax++) for (int az = -1; az <= 1; az++) if ((ax != 0 || az != 0) && standable(w, x + ax, y, z + az) && fullFloor(mc, x + ax, y, z + az)) flat++;
						cands.add(new Cand(new BlockPos(x, y, z), flat, Math.hypot(x + 0.5 - p.getX(), z + 0.5 - p.getZ())));
					}
				}
			}
		}
		// flat ground around the spot first (the pearl spreads a little), then the nearest (a short throw spreads less)
		cands.sort(Comparator.comparingInt((Cand c) -> -Math.min(c.flat(), 8) / 3).thenComparingDouble(Cand::dist));
		Trajectory.Point start = throwStart(p);
		Trajectory.Collider col = collider(mc);
		int asked = 0, solved = 0;
		for (Cand c : cands) {
			if (asked++ >= MAX_CANDIDATES || solved >= MAX_SOLVES) break;
			BlockPos f = c.feet();
			if (!spot.worksFrom(new Pos(f.getX(), f.getY(), f.getZ()), new Vec3(f.getX() + 0.5, f.getY() + eyeH, f.getZ() + 0.5))) continue;
			solved++;
			PearlAim.Shot shot = solve(mc, w, start, col, f, c.flat() >= 8 ? 0.45 : c.flat() >= 5 ? 0.3 : 0.2);
			if (shot != null) return new Landing(f, shot);
		}
		return null;
	}

	/** A throw from {@code start} that lands on the floor of {@code feet} (or flat floor right next to it). */
	private static PearlAim.Shot solve(Minecraft mc, LevelWorld w, Trajectory.Point start, Trajectory.Collider col, BlockPos feet, double maxError) {
		int fy = feet.getY();
		return PearlAim.solve(start, feet.getX() + 0.5, feet.getZ() + 0.5, col, l -> {
			if (Math.abs(l.y() - fy) > 0.05) return false; // on the top face of the floor, not a wall
			int x = (int) Math.floor(l.x()), z = (int) Math.floor(l.z());
			return standable(w, x, fy, z) && fullFloor(mc, x, fy, z);
		}, maxError);
	}

	public void start(Landing l, boolean creativeSupply) {
		landing = l;
		creative = creativeSupply;
		stage = ticks = 0;
		back = -1;
		failReason = "";
		status = Status.WORKING;
	}

	public void cancel() {
		if (status == Status.WORKING) RotationController.clear();
		restoreSlot();
		status = Status.IDLE;
	}

	private void restoreSlot() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null && back >= 0) p.getInventory().setSelectedSlot(back);
		back = -1;
	}

	private Status fail(String why) {
		failReason = why;
		RotationController.clear();
		restoreSlot();
		return status = Status.FAILED;
	}

	/** Call once per client tick while {@link #status()} is WORKING. */
	public Status tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (status != Status.WORKING) return status;
		if (p == null || mc.level == null || mc.gameMode == null) return fail("left the world");
		ticks++;
		mc.options.keyUp.setDown(false);
		mc.options.keyJump.setDown(false);
		switch (stage) {
			case 0 -> { // a pearl in hand
				if (ticks > 60) return fail("no pearl to throw");
				if (p.getMainHandItem().is(Items.ENDER_PEARL)) {
					stage = 1;
					return status;
				}
				for (int i = 0; i < 9; i++) {
					if (p.getInventory().getItem(i).is(Items.ENDER_PEARL)) {
						if (back < 0) back = p.getInventory().getSelectedSlot();
						p.getInventory().setSelectedSlot(i);
						return status;
					}
				}
				if (ticks % 5 != 1) return status; // give a swap the moment it needs before looking again
				for (int i = 9; i < 36; i++) {
					if (p.getInventory().getItem(i).is(Items.ENDER_PEARL)) {
						if (back < 0) back = p.getInventory().getSelectedSlot();
						mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, i, p.getInventory().getSelectedSlot(), ClickType.SWAP, p);
						return status;
					}
				}
				if (creative && p.hasInfiniteMaterials()) {
					if (back < 0) back = p.getInventory().getSelectedSlot();
					int hot = p.getInventory().getSelectedSlot();
					for (int i = 0; i < 9; i++) {
						if (p.getInventory().getItem(i).isEmpty()) {
							hot = i;
							break;
						}
					}
					p.getInventory().setSelectedSlot(hot);
					ItemStack stack = new ItemStack(Items.ENDER_PEARL, 16);
					p.getInventory().setItem(hot, stack);
					mc.gameMode.handleCreativeModeItemAdd(stack, 36 + hot);
					return status;
				}
				return fail("no pearl to throw");
			}
			case 1 -> { // stand still, upright, and look about right
				if (ticks > 200) return fail("could not get ready to throw");
				if (p.getCooldowns().isOnCooldown(p.getMainHandItem())) return status;
				Rotation want = new Rotation(landing.shot().yaw(), landing.shot().pitch());
				RotationController.aimAt(want, 14f);
				Vec3 v = p.getDeltaMovement();
				boolean still = p.onGround() && !p.isCrouching() && Math.hypot(v.x, v.z) < 0.02;
				Rotation now = new Rotation(p.getYRot(), p.getXRot());
				boolean facing = Math.abs(Rotations.wrapDegrees(now.yaw() - want.yaw())) < 2f && Math.abs(now.pitch() - want.pitch()) < 2f;
				if (!still || !facing) return status;
				// the last word on the pitch is a fresh solve from exactly where the eye is now
				PearlAim.Shot s = solve(mc, new LevelWorld(mc.level), throwStart(p), collider(mc), landing.feet(), 0.45);
				if (s == null) return fail("the throw does not land there from here");
				landing = new Landing(landing.feet(), s);
				RotationController.clear();
				p.setYRot(s.yaw());
				p.setXRot(s.pitch());
				p.yRotO = s.yaw();
				p.xRotO = s.pitch();
				mc.gameMode.useItem(p, InteractionHand.MAIN_HAND); // the use packet carries this rotation
				p.swing(InteractionHand.MAIN_HAND);
				stage = 2;
				ticks = 0;
			}
			case 2 -> { // in flight: wait for the teleport
				BlockPos f = landing.feet();
				double d = p.position().distanceTo(new Vec3(f.getX() + 0.5, f.getY(), f.getZ() + 0.5));
				if (d < 1.6 && p.onGround()) {
					restoreSlot();
					return status = Status.LANDED;
				}
				if (ticks > landing.shot().ticks() + 40) return fail("the pearl did not land on the spot");
			}
			default -> { }
		}
		return status;
	}
}
