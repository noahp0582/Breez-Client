package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.modules.RenderModules;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.Rotations;
import breezeclient.breezeclient.core.physics.Trajectory;
import breezeclient.breezeclient.core.settings.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer test (BREEZ_SELFTEST=aimbow): on a stone strip, checks that BowTrajectory stops its path at a zombie in the
 * way, that a real full-power bow shot lands where it predicted, and that AimAssist pulls the view toward a zombie while
 * the player clicks attack (and leaves it alone while they do not). Writes run/aim-bow-test.txt and quits.
 */
public final class AimBowTest {
	private static final String ZOMBIE = "{NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}";
	private static final List<String> LOG = new ArrayList<>();
	private static int ticks = -1;
	private static boolean finished;

	private static Trajectory.Point predicted;
	private static Vec3 arrowPos;
	private static int arrowStill, arrowSeenAt = -1;
	private static double angleBefore, angleIdle;

	private AimBowTest() {}

	public static boolean register() {
		if (!"aimbow".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> ticks = 0);
		ClientTickEvents.END_CLIENT_TICK.register(AimBowTest::tick);
		return true;
	}

	private static void cmd(Minecraft mc, String c) {
		var server = mc.getSingleplayerServer();
		if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c));
	}

	@SuppressWarnings("unchecked")
	private static void set(Module m, String setting, Object value) {
		for (Setting<?> s : m.settings()) if (s.name().equals(setting)) ((Setting<Object>) s).set(value);
	}

	private static Zombie zombie(Minecraft mc) {
		Zombie best = null; // the test zombie stands 3.5 blocks in front of the spawn point; ignore any from the world
		for (Entity e : mc.level.entitiesForRendering())
			if (e instanceof Zombie z && z.distanceToSqr(0.5, 150, 4.0) < 4 && (best == null || z.distanceToSqr(0.5, 150, 4.0) < best.distanceToSqr(0.5, 150, 4.0))) best = z;
		return best;
	}

	/** Degrees between where the player looks and the zombie's chest. */
	private static double angleToZombie(Minecraft mc) {
		Zombie z = zombie(mc);
		if (z == null) return -1;
		Vec3 d = new Vec3(z.getX(), z.getY() + z.getBbHeight() * 0.65, z.getZ()).subtract(mc.player.getEyePosition());
		double yaw = Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		return Math.hypot(Rotations.wrapDegrees((float) (yaw - mc.player.getYRot())), pitch - mc.player.getXRot());
	}

	private static void check(boolean ok, String what) { LOG.add((ok ? "PASS " : "FAIL ") + what); }

	private static void tick(Minecraft mc) {
		if (ticks < 0 || mc.player == null || mc.level == null || finished) return;
		ticks++;
		mc.options.pauseOnLostFocus = false;
		if (ticks < 25) SelfTest.reviveAtStart(mc); // an earlier test may have left the player dead
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
		var p = mc.player;
		var bow = (RenderModules.BowTrajectory) BreezClientClient.MODULES.get("BowTrajectory");
		Module aim = BreezClientClient.MODULES.get("AimAssist");
		try {
			if (ticks == 10) { // a player who logged in somewhere else would leave the arena's chunks unloaded and every fill below failing
				cmd(mc, "gamemode creative @a");
				cmd(mc, "forceload add -16 -16 16 80");
				cmd(mc, "execute as @a run tp @s 0.5 166 0.5 0 0");
			}
			if (ticks == 30) {
				cmd(mc, "gamemode survival @a");
				cmd(mc, "clear @a");
				cmd(mc, "effect give @a minecraft:resistance 600 4 true");
				cmd(mc, "effect give @a minecraft:saturation 600 20 true");
				cmd(mc, "time set day");
				cmd(mc, "fill -6 140 -6 6 149 70 minecraft:stone");
				cmd(mc, "fill -6 150 -6 6 165 70 minecraft:air");
				cmd(mc, "fill -6 150 20 6 160 20 minecraft:stone"); // a wall to shoot at: on the floor a grazing shot turns vanilla spread into a long miss
				cmd(mc, "kill @e[type=!minecraft:player,x=-20,y=130,z=-20,dx=40,dy=50,dz=100]");
				cmd(mc, "give @a minecraft:bow");
				cmd(mc, "give @a minecraft:arrow 16");
			}
			if (ticks == 40) {
				cmd(mc, "execute as @a run tp @s 0.5 150 0.5 0 0");
				p.getInventory().setSelectedSlot(0);
				bow.setEnabled(true);
			}

			// 1. a zombie in the way: the path stops at it and it is the hit entity
			if (ticks == 60) cmd(mc, "summon minecraft:zombie 0.5 150 8.5 " + ZOMBIE);
			if (ticks >= 80 && ticks < 100) { p.setYRot(0); p.setXRot(0); }
			if (ticks == 100) {
				Entity hit = bow.heldHit();
				var l = bow.heldLanding();
				LOG.add("held bow: " + p.getMainHandItem().getHoverName().getString() + ", landing " + l + ", hit " + hit);
				check(hit instanceof Zombie, "BowTrajectory marks the zombie in front as the hit");
				check(l != null && Math.abs(l.z() - 8.2) < 0.5, "path stops at the zombie's front (z = " + (l == null ? "none" : String.format("%.2f", l.z())) + ", expected ~8.2)");
				cmd(mc, "kill @e[type=minecraft:zombie]");
			}

			// 2. a real full-power shot lands where the path said
			if (ticks >= 110 && ticks < 150) { p.setYRot(0); p.setXRot(0); }
			if (ticks == 115) mc.options.keyUse.setDown(true);
			if (ticks == 145) {
				predicted = bow.heldLanding();
				LOG.add("drawn " + p.getTicksUsingItem() + " ticks, predicted landing " + predicted + ", hit " + bow.heldHit());
				mc.options.keyUse.setDown(false);
			}
			if (ticks > 146 && arrowSeenAt != -2 && ticks < 400) {
				for (Entity e : mc.level.entitiesForRendering()) {
					if (!(e instanceof AbstractArrow a) || a.getOwner() != p) continue;
					if (arrowSeenAt < 0) arrowSeenAt = ticks;
					if ((ticks - arrowSeenAt) % 5 == 0 && ticks - arrowSeenAt < 40) LOG.add("  arrow t+" + (ticks - arrowSeenAt) + " at " + a.position() + " v " + a.getDeltaMovement());
					Vec3 now = a.position();
					arrowStill = arrowPos != null && now.distanceToSqr(arrowPos) < 1.0e-4 ? arrowStill + 1 : 0;
					arrowPos = now;
				}
				if (arrowStill >= 10) {
					arrowSeenAt = -2;
					double off = predicted == null ? Double.NaN : arrowPos.distanceTo(new Vec3(predicted.x(), predicted.y(), predicted.z()));
					LOG.add(String.format("arrow came to rest at (%.2f, %.2f, %.2f), %.2f blocks from the prediction", arrowPos.x, arrowPos.y, arrowPos.z, off));
					check(predicted != null && off < 1.0, "real bow shot hit the wall within 1 block of the predicted point");
				}
			}
			if (ticks == 400 && arrowSeenAt != -2) check(false, "real bow shot: arrow " + (arrowSeenAt < 0 ? "never seen" : "never came to rest at " + arrowPos));

			// 3. AimAssist: nothing while idle, a pull toward the zombie while clicking
			if (ticks == 410) {
				bow.setEnabled(false);
				cmd(mc, "kill @e[type=minecraft:arrow]");
				cmd(mc, "item replace entity @a weapon.mainhand with minecraft:diamond_sword");
				cmd(mc, "summon minecraft:zombie 0.5 150 4.0 " + ZOMBIE);
				set(aim, "Mobs", true);
				aim.setEnabled(true);
			}
			if (ticks == 440) { p.setYRot(30); p.setXRot(0); }
			if (ticks == 445) angleBefore = angleToZombie(mc);
			if (ticks == 505) {
				angleIdle = angleToZombie(mc);
				LOG.add("player at " + p.position() + ", test zombie " + zombie(mc));
				LOG.add(String.format("holding %s, angle %.2f -> %.2f after 3 s without clicking", p.getMainHandItem().getHoverName().getString(), angleBefore, angleIdle));
				check(angleBefore > 20 && Math.abs(angleIdle - angleBefore) < 0.5, "AimAssist leaves the view alone while you do not attack");
			}
			if (ticks > 505 && ticks <= 565) mc.options.keyAttack.setDown((ticks - 506) % 12 == 0); // one short click every 0.6 s
			if (ticks == 566) {
				mc.options.keyAttack.setDown(false);
				double after = angleToZombie(mc);
				LOG.add(String.format("angle %.2f -> %.2f after 5 clicks over 3 s", angleIdle, after));
				check(after < 10, "AimAssist pulls the view onto the zombie while you click attack");
			}
			if (ticks == 580) finish(mc);
		} catch (Exception e) {
			LOG.add("FAIL test threw " + e);
			finish(mc);
		}
	}

	private static void finish(Minecraft mc) {
		if (finished) return;
		finished = true;
		mc.options.keyUse.setDown(false);
		mc.options.keyAttack.setDown(false);
		try {
			Files.writeString(FabricLoader.getInstance().getGameDir().resolve("aim-bow-test.txt"), String.join("\n", LOG) + "\n");
		} catch (Exception ignored) {
			// nothing else to do
		}
		mc.stop();
	}
}
