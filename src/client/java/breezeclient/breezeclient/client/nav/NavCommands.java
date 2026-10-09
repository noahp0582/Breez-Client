package breezeclient.breezeclient.client.nav;

import breezeclient.breezeclient.client.world.BlockIds;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.command.Command;
import breezeclient.breezeclient.core.command.CommandManager;
import breezeclient.breezeclient.core.nav.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Chat commands for the built-in pathing. */
public final class NavCommands {
	private NavCommands() {}

	private static Command simple(String name, String usage, java.util.function.BiConsumer<String[], Consumer<String>> body) {
		return new Command() {
			public String name() { return name; }
			public String usage() { return usage; }
			public void run(String[] a, Consumer<String> reply) { body.accept(a, reply); }
		};
	}

	private static Integer num(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static void register(CommandManager c) {
		c.register(simple("goto", "goto <x y z | x z | waypoint | player>", (a, r) -> {
			var mc = Minecraft.getInstance();
			if (mc.player == null) return;
			if (a.length == 3 && num(a[0]) != null && num(a[1]) != null && num(a[2]) != null) {
				Baritone.goTo(new Goal.GoalNear(num(a[0]), num(a[1]), num(a[2]), 1.5), "goto " + a[0] + " " + a[1] + " " + a[2]);
				r.accept("Going to " + a[0] + " " + a[1] + " " + a[2]);
			} else if (a.length == 2 && num(a[0]) != null && num(a[1]) != null) {
				Baritone.goTo(new Goal.GoalXZ(num(a[0]), num(a[1])), "goto " + a[0] + " " + a[1]);
				r.accept("Going to x " + a[0] + " z " + a[1]);
			} else if (a.length == 1) {
				for (var w : Waypoints.forCurrentServer()) {
					if (w.name().equalsIgnoreCase(a[0])) {
						Baritone.goTo(new Goal.GoalNear(w.x(), w.y(), w.z(), 2.5), "goto " + w.name());
						r.accept("Going to waypoint " + w.name());
						return;
					}
				}
				for (Player o : mc.level.players()) {
					if (o != mc.player && o.getName().getString().equalsIgnoreCase(a[0])) {
						Baritone.goTo(new Goal.GoalNear(o.getBlockX(), o.getBlockY(), o.getBlockZ(), 2.5), "goto " + a[0]);
						r.accept("Going to " + a[0]);
						return;
					}
				}
				r.accept("No waypoint or nearby player called " + a[0]);
			} else {
				r.accept("Usage: goto <x y z | x z | waypoint | player>");
			}
		}));
		c.register(simple("mine", "mine <block...> [count]", (a, r) -> {
			if (a.length == 0) {
				r.accept("Usage: mine <block...> [count]   e.g. mine diamond_ore 8");
				return;
			}
			int count = 0, end = a.length;
			Integer last = num(a[a.length - 1]);
			if (last != null) {
				count = Math.max(0, last);
				end--;
			}
			Set<Block> blocks = new HashSet<>();
			StringBuilder names = new StringBuilder();
			for (int i = 0; i < end; i++) {
				Block b = BlockIds.parse(a[i]);
				if (b == null) {
					r.accept("Unknown block: " + a[i]);
					return;
				}
				blocks.add(b);
				names.append(names.length() > 0 ? "," : "").append(a[i]);
			}
			if (blocks.isEmpty()) {
				r.accept("Usage: mine <block...> [count]");
				return;
			}
			Baritone.mine(blocks, count, "mine " + names);
			r.accept("Mining " + names + (count > 0 ? " x" + count : "") + ". Stop with .stop");
		}));
		c.register(simple("follow", "follow <player>", (a, r) -> {
			if (a.length < 1) {
				r.accept("Usage: follow <player>");
				return;
			}
			Baritone.follow(a[0]);
			r.accept("Following " + a[0]);
		}));
		c.register(simple("explore", "explore", (a, r) -> {
			Baritone.explore();
			r.accept("Exploring. Stop with .stop");
		}));
		c.register(simple("tunnel", "tunnel [length] [north|south|east|west]", (a, r) -> {
			var mc = Minecraft.getInstance();
			if (mc.player == null) return;
			int len = 20;
			Direction dir = mc.player.getDirection();
			for (String s : a) {
				Integer n = num(s);
				if (n != null) len = Math.max(1, Math.min(500, n));
				else {
					for (Direction d : Direction.Plane.HORIZONTAL) if (d.getName().equalsIgnoreCase(s)) dir = d;
				}
			}
			Baritone.tunnel(dir, len);
			r.accept("Tunnelling " + len + " blocks " + dir.getName());
		}));
		c.register(simple("thisway", "thisway [blocks]", (a, r) -> {
			var mc = Minecraft.getInstance();
			if (mc.player == null) return;
			int n = a.length > 0 && num(a[0]) != null ? Math.max(1, num(a[0])) : 50;
			Direction d = mc.player.getDirection();
			Baritone.goTo(new Goal.GoalXZ(mc.player.getBlockX() + d.getStepX() * n, mc.player.getBlockZ() + d.getStepZ() * n), "thisway " + n);
			r.accept("Going " + n + " blocks " + d.getName());
		}));
		c.register(simple("surface", "surface", (a, r) -> {
			var mc = Minecraft.getInstance();
			if (mc.player == null) return;
			Baritone.goTo(Baritone.surfaceGoal(mc, mc.player), "surface");
			r.accept("Heading to the surface");
		}));
		c.register(simple("stop", "stop", (a, r) -> {
			Baritone.stop(null);
			r.accept("Stopped.");
		}));
		c.register(simple("pause", "pause", (a, r) -> {
			Baritone.pause();
			r.accept("Paused. Resume with .resume");
		}));
		c.register(simple("resume", "resume", (a, r) -> {
			Baritone.resume();
			r.accept("Resumed.");
		}));
		c.register(simple("pathstatus", "pathstatus", (a, r) -> r.accept("Baritone: " + Baritone.status().toLowerCase(Locale.ROOT))));
	}
}
