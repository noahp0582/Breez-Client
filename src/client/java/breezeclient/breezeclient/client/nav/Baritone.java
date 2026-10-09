package breezeclient.breezeclient.client.nav;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.world.ChunkAgeTracker;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.core.nav.Goal;
import breezeclient.breezeclient.core.nav.Pathfinder;
import breezeclient.breezeclient.core.nav.Pos;
import breezeclient.breezeclient.core.world.ChunkAge;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The built-in "Baritone"-style controller: goto, mine, follow, explore, tunnel. Plans with {@link Pathfinder} in segments
 * (long trips are walked in pieces) and walks the plan with {@link PathExecutor}.
 */
public final class Baritone {
	public enum Mode { NONE, GOTO, MINE, FOLLOW, EXPLORE, TUNNEL }

	private static Mode mode = Mode.NONE;
	private static String lastStop = "";

	/** Why the last task ended (for diagnostics and the dev tests). */
	public static String lastStop() { return lastStop; }
	private static boolean paused;
	private static final PathExecutor EXEC = new PathExecutor();
	private static final PathExecutor MINER = new PathExecutor();

	private static Goal goal;
	private static String label = "";
	private static int replans;
	private static int waitTicks;
	private static Pos lastPlanEnd;
	private static int samePlanEnd;

	// mine
	private static Set<Block> mineBlocks = Set.of();
	private static int mineTarget, mined;
	private static BlockPos mineCell;
	private static final Set<Long> BAD = new HashSet<>();
	private static ScanTarget mineScan;
	private static int mineTicks, collectTicks;
	private static boolean mining;

	// follow
	private static String followName = "";
	private static int followTicks;
	private static Pos followLast;

	// explore
	private static final Set<Long> VISITED = new HashSet<>();
	private static int exploreTicks;

	// tunnel
	private static Direction tunnelDir = Direction.NORTH;
	private static int tunnelLeft;

	private Baritone() {}

	public static void register() { ClientTickEvents.END_CLIENT_TICK.register(Baritone::tick); }

	public static Mode mode() { return mode; }

	public static boolean active() { return mode != Mode.NONE; }

	public static String status() {
		if (mode == Mode.NONE) return "idle";
		return (paused ? "paused " : "") + label + (EXEC.running() ? " (" + EXEC.remaining() + " steps)" : "");
	}

	// commands ------------------------------------------------------------------------------------------------------------

	private static void begin(Mode m, String lbl) {
		stopQuiet();
		mode = m;
		label = lbl;
		replans = 0;
		waitTicks = 0;
		lastPlanEnd = null;
		samePlanEnd = 0;
		paused = false;
		if (NavModule.instance != null) NavModule.instance.setStatus(lbl);
	}

	public static void goTo(Goal g, String lbl) {
		begin(Mode.GOTO, lbl);
		goal = g;
	}

	public static void mine(Set<Block> blocks, int count, String lbl) {
		begin(Mode.MINE, lbl);
		mineBlocks = blocks;
		mineTarget = count;
		mined = 0;
		mineCell = null;
		mining = false;
		BAD.clear();
		var set = blocks;
		mineScan = new ScanTarget(s -> set.contains(s.getBlock()));
		ChunkScanner.add(mineScan);
	}

	public static void follow(String playerName) {
		begin(Mode.FOLLOW, "follow " + playerName);
		followName = playerName;
		followTicks = 0;
		followLast = null;
	}

	public static void explore() {
		begin(Mode.EXPLORE, "explore");
		VISITED.clear();
		exploreTicks = 0;
		goal = null;
	}

	public static void tunnel(Direction dir, int length) {
		begin(Mode.TUNNEL, "tunnel " + length + " " + dir.getName());
		tunnelDir = dir;
		tunnelLeft = length;
	}

	public static void pause() {
		if (mode == Mode.NONE) return;
		paused = true;
		EXEC.cancel();
		MINER.cancel();
		RotationController.clear();
	}

	public static void resume() { paused = false; }

	public static void stop(String why) {
		if (why != null && !why.isEmpty()) lastStop = why;
		if (mode != Mode.NONE && why != null && !why.isEmpty()) Toasts.show("Baritone", "Stopped: " + why, 0xFFFF9955);
		stopQuiet();
	}

	private static void stopQuiet() {
		EXEC.cancel();
		MINER.cancel();
		RotationController.clear();
		if (mineScan != null) ChunkScanner.remove(mineScan);
		mineScan = null;
		mode = Mode.NONE;
		goal = null;
		if (NavModule.instance != null) NavModule.instance.setStatus("");
	}

	private static void finish(String msg) {
		lastStop = "finished: " + msg;
		Toasts.show("Baritone", msg, 0xFF55FF55);
		stopQuiet();
	}

	// main loop -------------------------------------------------------------------------------------------------------------

	private static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			if (mode != Mode.NONE) stopQuiet();
			return;
		}
		// track explored chunks for EXPLORE even when it is not the active mode
		markVisited(p);
		if (mode == Mode.NONE || paused) return;
		EXEC.tick();
		if (NavModule.instance != null) NavModule.instance.setStatus(status());
		switch (mode) {
			case GOTO -> goalTick(mc, p, goal, NavModule.instance != null && NavModule.instance.allowBreak.get());
			case MINE -> mineTick(mc, p);
			case FOLLOW -> followTick(mc, p);
			case EXPLORE -> exploreTick(mc, p);
			case TUNNEL -> tunnelTick(mc, p);
			default -> { }
		}
	}

	private static void markVisited(LocalPlayer p) {
		var cp = p.chunkPosition();
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) VISITED.add(ChunkAge.key(cp.x + dx, cp.z + dz));
	}

	private static Pos feet(LocalPlayer p) { return new Pos(p.getBlockX(), p.blockPosition().getY(), p.getBlockZ()); }

	/** Plans and walks toward {@code g}. @return true when the goal is reached. */
	private static boolean goalTick(Minecraft mc, LocalPlayer p, Goal g, boolean breaking) {
		Pos here = feet(p);
		if (g.isEnd(here) && !EXEC.running()) {
			if (mode == Mode.GOTO) finish("Arrived");
			return true;
		}
		if (EXEC.running()) return false;
		if (EXEC.status() == PathExecutor.Status.FAILED) {
			if (++replans > 4) {
				stop("gave up: " + EXEC.failReason());
				return false;
			}
			EXEC.cancel();
		}
		if (!p.onGround()) return false; // wait to land before planning
		if (waitTicks > 0) {
			waitTicks--;
			return false;
		}
		NavModule nav = NavModule.instance;
		boolean place = nav != null && nav.allowPlace.get() && nav.hasPlaceable();
		var opts = nav == null ? Pathfinder.Options.walking(3) : nav.options(breaking, place, nav.allowParkour.get());
		var result = Pathfinder.search(new LevelWorld(mc.level, place), here, g, opts, nav == null ? 9000 : nav.nodeBudget.asInt());
		if (result == null) {
			waitTicks = 10;
			if (++replans > 6) stop("standing somewhere the pathing cannot start from");
			return false;
		}
		if (result.steps().isEmpty()) {
			if (!result.reachedGoal()) {
				if (++replans > 3) stop("no path found");
				waitTicks = 20;
			}
			return false;
		}
		Pos end = result.steps().get(result.steps().size() - 1).pos();
		if (end.equals(lastPlanEnd)) {
			if (++samePlanEnd > 2) {
				stop("no progress toward the goal");
				return false;
			}
		} else {
			samePlanEnd = 0;
		}
		lastPlanEnd = end;
		EXEC.start(result.steps(), here);
		return false;
	}

	// mine ----------------------------------------------------------------------------------------------------------------------

	private static void mineTick(Minecraft mc, LocalPlayer p) {
		ClientLevel level = mc.level;
		if (mineTarget > 0 && mined >= mineTarget) {
			finish("Mined " + mined);
			return;
		}
		if (mineCell == null) {
			mineCell = nearestTarget(p);
			mining = false;
			mineTicks = 0;
			if (mineCell == null) {
				if (collectTicks > 0) {
					collectTicks--;
					return;
				}
				// nothing known nearby: walk to unexplored ground and look again
				if (!EXEC.running() && exploreTicks++ % 100 == 0) pickExploreGoal(p);
				if (goal != null) goalTick(mc, p, goal, false);
				label = "mine (searching)";
				return;
			}
			goal = null;
			label = "mine " + mineCell.toShortString();
		}
		if (!mineBlocks.contains(level.getBlockState(mineCell).getBlock())) {
			mined++;
			mineCell = null;
			MINER.cancel();
			collectTicks = 40;
			return;
		}
		Goal g = new Goal.GoalGetToBlock(mineCell.getX(), mineCell.getY(), mineCell.getZ());
		if (!mining) {
			if (g.isEnd(feet(p)) && !EXEC.running()) {
				mining = true;
				EXEC.cancel();
			} else {
				goalTick(mc, p, g, NavModule.instance != null && NavModule.instance.allowBreak.get());
				return;
			}
		}
		// in reach: break it
		if (++mineTicks > 20 * 25) {
			BAD.add(mineCell.asLong());
			mineCell = null;
			MINER.cancel();
			return;
		}
		if (!g.isEnd(feet(p))) {
			mining = false; // got pushed away
			return;
		}
		if (MINER.status() != PathExecutor.Status.FAILED) {
			MINER.mine(mc, level, p, mineCell);
		}
		if (MINER.status() == PathExecutor.Status.FAILED) {
			BAD.add(mineCell.asLong());
			mineCell = null;
			MINER.cancel();
		}
	}

	private static BlockPos nearestTarget(LocalPlayer p) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		var level = Minecraft.getInstance().level;
		for (var list : mineScan.chunks()) {
			for (BlockPos b : list) {
				if (BAD.contains(b.asLong()) || !mineBlocks.contains(level.getBlockState(b).getBlock())) continue;
				double dx = b.getX() + 0.5 - p.getX(), dy = (b.getY() + 0.5 - p.getY()) * 1.5, dz = b.getZ() + 0.5 - p.getZ();
				double d = dx * dx + dy * dy + dz * dz;
				if (d < bestD) {
					bestD = d;
					best = b;
				}
			}
		}
		return best;
	}

	// follow ---------------------------------------------------------------------------------------------------------------------

	private static void followTick(Minecraft mc, LocalPlayer p) {
		Player target = null;
		for (Player o : mc.level.players()) if (o != p && o.getName().getString().equalsIgnoreCase(followName)) target = o;
		if (target == null) {
			stop("cannot see " + followName);
			return;
		}
		Pos t = new Pos(target.getBlockX(), target.blockPosition().getY(), target.getBlockZ());
		boolean moved = followLast == null || Math.abs(t.x() - followLast.x()) + Math.abs(t.z() - followLast.z()) >= 3;
		if (moved && EXEC.running() && ++followTicks > 10) {
			EXEC.cancel();
			followTicks = 0;
		}
		if (moved || !EXEC.running()) followLast = t;
		goalTick(mc, p, new Goal.GoalNear(t.x(), t.y(), t.z(), 3.0), false);
		replans = 0; // following never gives up
	}

	// explore ----------------------------------------------------------------------------------------------------------------------

	private static void pickExploreGoal(LocalPlayer p) {
		var cp = p.chunkPosition();
		double bestScore = -1;
		int bx = 0, bz = 0;
		float yaw = p.getYRot();
		for (int a = 0; a < 16; a++) {
			double ang = Math.toRadians(a * 22.5);
			for (int dist : new int[] {5, 8}) {
				int cx = cp.x + (int) Math.round(Math.cos(ang) * dist), cz = cp.z + (int) Math.round(Math.sin(ang) * dist);
				int unknown = 0;
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
					long k = ChunkAge.key(cx + dx, cz + dz);
					if (!VISITED.contains(k) && ChunkAgeTracker.state(cx + dx, cz + dz) == ChunkAge.State.UNKNOWN) unknown++;
				}
				double heading = Math.cos(Math.toRadians(a * 22.5 - (yaw + 90))) * 0.5;
				double score = unknown + heading;
				if (score > bestScore) {
					bestScore = score;
					bx = cx * 16 + 8;
					bz = cz * 16 + 8;
				}
			}
		}
		goal = new Goal.GoalXZ(bx, bz);
		label = "explore -> " + bx + " " + bz;
		replans = 0;
	}

	private static void exploreTick(Minecraft mc, LocalPlayer p) {
		if (goal == null || (goal.isEnd(feet(p)) && !EXEC.running()) || exploreTicks++ > 20 * 40) {
			exploreTicks = 0;
			pickExploreGoal(p);
			EXEC.cancel();
		}
		boolean done = goalTick(mc, p, goal, false);
		if (done) goal = null;
		if (mode == Mode.EXPLORE) replans = Math.min(replans, 3); // keep exploring after a dead end
		if (mode == Mode.NONE) return;
		if (replans >= 3) {
			goal = null; // pick another direction
			EXEC.cancel();
			replans = 0;
		}
	}

	// tunnel ----------------------------------------------------------------------------------------------------------------------------

	private static void tunnelTick(Minecraft mc, LocalPlayer p) {
		if (EXEC.running()) return;
		if (EXEC.status() == PathExecutor.Status.FAILED) {
			stop("tunnel stopped: " + EXEC.failReason());
			return;
		}
		if (tunnelLeft <= 0) {
			finish("Tunnel finished");
			return;
		}
		if (!p.onGround()) return;
		Pos here = feet(p);
		int nx = here.x() + tunnelDir.getStepX(), nz = here.z() + tunnelDir.getStepZ();
		var world = new LevelWorld(mc.level, NavModule.instance != null && NavModule.instance.hasPlaceable());
		List<Pos> breaks = new ArrayList<>();
		for (int dy = 0; dy <= 1; dy++) {
			if (world.passable(nx, here.y() + dy, nz)) continue;
			if (world.breakCost(nx, here.y() + dy, nz) < 0) {
				stop("blocked by something that must not be broken at " + nx + " " + (here.y() + dy) + " " + nz);
				return;
			}
			breaks.add(new Pos(nx, here.y() + dy, nz));
		}
		Pathfinder.Move move = breaks.isEmpty() ? Pathfinder.Move.WALK : Pathfinder.Move.BREAK;
		if (!world.ground(nx, here.y() - 1, nz)) {
			NavModule nav = NavModule.instance;
			if (nav != null && nav.allowPlace.get() && nav.hasPlaceable() && world.passable(nx, here.y() - 1, nz) && breaks.isEmpty()) move = Pathfinder.Move.BRIDGE;
			else {
				stop("no floor ahead");
				return;
			}
		}
		tunnelLeft--;
		EXEC.start(List.of(new Pathfinder.Step(new Pos(nx, here.y(), nz), move, breaks)), here);
	}

	// helpers used by commands --------------------------------------------------------------------------------------------------------------

	public static Goal surfaceGoal(Minecraft mc, LocalPlayer p) {
		int h = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ());
		return new Goal.GoalBlock(p.getBlockX(), h, p.getBlockZ());
	}


}
