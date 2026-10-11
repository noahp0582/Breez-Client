package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.client.world.BlockIds;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.client.world.Waypoints;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Modules for DonutSMP-style servers: selling, spawners, chunk and player tracking. */
public final class DonutModules {
	private DonutModules() {}

	/** Draws the footprint of a chunk between two heights. */
	static void drawChunk(RenderFrame f, int cx, int cz, double y1, double y2, int argb) {
		f.drawBox(cx * 16, y1, cz * 16, cx * 16 + 16, y2, cz * 16 + 16, argb);
	}

	/** The open container screen (not your own inventory), or null. */
	private static AbstractContainerScreen<?> containerScreen(Minecraft mc) {
		if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?> cs && cs.getMenu() != mc.player.inventoryMenu) return cs;
		return null;
	}

	private static String nameOf(ItemStack s) { return s.getHoverName().getString().toLowerCase(Locale.ROOT); }

	/** True for slots that belong to the player's inventory part of an open menu (as opposed to the container). */
	private static boolean isPlayerSlot(LocalPlayer p, Slot s) { return s.container == p.getInventory(); }

	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Runs your sell command on a timer, moves matching inventory items into the sell window it opens with ordinary
	 * shift-clicks, and presses the confirm button. It does nothing unless a container whose title contains the text
	 * you set actually opened.
	 */
	public static class AutoSell extends Module {
		private enum State { IDLE, OPENING, MOVING, CONFIRM, CLOSING }

		private final StringSetting command = add(new StringSetting("Command", "/sell"), "Command", "Chat command that opens the sell window.");
		private final NumberSetting interval = add(new NumberSetting("Interval (s)", 60, 10, 3600), "Command", "Seconds between sells.");
		private final StringSetting title = add(new StringSetting("Window title contains", "sell"), "Window", "Only act in a window whose title contains this (case ignored).");
		private final StringSetting confirm = add(new StringSetting("Confirm button contains", "confirm"), "Window",
			"Name of the button to press after moving items (case ignored). Leave blank to just close the window.");
		private final StringListSetting only = add(new StringListSetting("Only sell", List.of()).kind(StringListSetting.Kind.ITEM), "Items", "Empty = every item that is not on the Keep list.");
		private final StringListSetting keep = add(new StringListSetting("Keep", List.of("minecraft:totem_of_undying", "minecraft:elytra", "minecraft:netherite_ingot",
			"minecraft:enchanted_golden_apple", "minecraft:ender_pearl", "minecraft:spawner", "minecraft:shulker_box")).kind(StringListSetting.Kind.ITEM), "Items", "Never moved into the sell window.");
		private final BoolSetting hotbar = add(new BoolSetting("Include hotbar", false), "Items", "Also sell items from the hotbar.");
		private final NumberSetting clickDelay = add(new NumberSetting("Click delay (ticks)", 2, 1, 10), "Timing", "Average pause between clicks.");

		private final ActionLimiter limiter = new ActionLimiter(new Random());
		private State state = State.IDLE;
		private int timer;
		private int wait;
		private int sold;

		public AutoSell() { super("AutoSell", Category.DONUT); }

		@Override
		protected boolean persistsEnabled() { return false; }

		@Override
		public String info() { return sold == 0 ? "" : String.valueOf(sold); }

		@Override
		protected void onEnable() {
			state = State.IDLE;
			timer = 40;
			sold = 0;
		}

		@Override
		protected void onDisable() { state = State.IDLE; }

		private boolean wanted(ItemStack s) {
			if (s.isEmpty()) return false;
			String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
			if (keep.contains(id)) return false;
			return only.get().isEmpty() || only.contains(id);
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.gameMode == null || mc.getConnection() == null) {
				setEnabled(false);
				return;
			}
			var cs = containerScreen(mc);
			switch (state) {
				case IDLE -> {
					if (mc.screen != null || --timer > 0) return;
					String c = command.get().trim();
					if (c.isEmpty()) return;
					if (c.startsWith("/")) mc.getConnection().sendCommand(c.substring(1));
					else mc.getConnection().sendChat(c);
					state = State.OPENING;
					timer = 60; // give the server three seconds to open the window
				}
				case OPENING -> {
					if (cs != null && cs.getTitle().getString().toLowerCase(Locale.ROOT).contains(title.get().toLowerCase(Locale.ROOT))) {
						state = State.MOVING;
						wait = limiter.nextDelayTicks(clickDelay.get() + 2, 1, 2);
					} else if (--timer <= 0) {
						state = State.IDLE;
						timer = interval.asInt() * 20;
					}
				}
				case MOVING -> {
					if (cs == null) {
						state = State.IDLE;
						timer = interval.asInt() * 20;
						return;
					}
					if (--wait > 0) return;
					AbstractContainerMenu menu = cs.getMenu();
					for (Slot s : menu.slots) {
						if (!isPlayerSlot(p, s) || !wanted(s.getItem())) continue;
						int idx = s.getContainerSlot();
						if (!hotbar.get() && idx < 9) continue; // inventory container slots 0-8 are the hotbar
						if (idx >= 36) continue; // armor and offhand
						mc.gameMode.handleInventoryMouseClick(menu.containerId, s.index, 0, ClickType.QUICK_MOVE, p);
						sold++;
						wait = limiter.nextDelayTicks(clickDelay.get(), clickDelay.get() * 0.3, 1);
						return;
					}
					state = State.CONFIRM;
					wait = limiter.nextDelayTicks(clickDelay.get() + 2, 1, 2);
				}
				case CONFIRM -> {
					if (cs == null) {
						state = State.IDLE;
						timer = interval.asInt() * 20;
						return;
					}
					if (--wait > 0) return;
					String want = confirm.get().trim().toLowerCase(Locale.ROOT);
					if (!want.isEmpty()) {
						for (Slot s : cs.getMenu().slots) {
							if (isPlayerSlot(p, s) || s.getItem().isEmpty()) continue;
							if (nameOf(s.getItem()).contains(want)) {
								mc.gameMode.handleInventoryMouseClick(cs.getMenu().containerId, s.index, 0, ClickType.PICKUP, p);
								break;
							}
						}
					}
					state = State.CLOSING;
					wait = limiter.nextDelayTicks(clickDelay.get() + 2, 1, 2);
				}
				case CLOSING -> {
					if (--wait > 0) return;
					if (mc.screen != null) p.closeContainer();
					state = State.IDLE;
					timer = interval.asInt() * 20;
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * In a spawner window, presses the sell button (and optionally takes the items out), then closes it. Can also open the
	 * spawner you are looking at on a timer.
	 */
	public static class AutoSpawnerSell extends Module {
		private final StringSetting title = add(new StringSetting("Window title contains", "spawner"), "Window", "Only act in a window whose title contains this (case ignored).");
		private final StringSetting sell = add(new StringSetting("Sell button contains", "sell"), "Window", "Name of the sell button (case ignored).");
		private final BoolSetting take = add(new BoolSetting("Take items out if there is no sell button", false), "Window",
			"Shift-click every stack out of the spawner window instead.");
		private final BoolSetting autoOpen = add(new BoolSetting("Open the spawner I look at", false), "Opening", "Right-click the spawner under your crosshair on a timer.");
		private final NumberSetting openEvery = add(new NumberSetting("Open every (s)", 60, 10, 3600), "Opening", "Seconds between opening it.", autoOpen::get);
		private final NumberSetting clickDelay = add(new NumberSetting("Click delay (ticks)", 3, 1, 10), "Timing", "Average pause before each click.");

		private final ActionLimiter limiter = new ActionLimiter(new Random());
		private int openTimer;
		private int wait = -1;
		private boolean done;
		private int actions;

		public AutoSpawnerSell() { super("AutoSpawnerSell", Category.DONUT); }

		@Override
		public String info() { return actions == 0 ? "" : String.valueOf(actions); }

		@Override
		protected void onEnable() {
			wait = -1;
			done = false;
			openTimer = 20;
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer p = mc.player;
			if (p == null || mc.gameMode == null) return;
			var cs = containerScreen(mc);
			if (cs == null) {
				wait = -1;
				done = false;
				if (mc.screen == null && autoOpen.get() && --openTimer <= 0
					&& mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && mc.level.getBlockState(bh.getBlockPos()).is(Blocks.SPAWNER)) {
					mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bh);
					p.swing(InteractionHand.MAIN_HAND);
					openTimer = openEvery.asInt() * 20;
				}
				return;
			}
			if (!cs.getTitle().getString().toLowerCase(Locale.ROOT).contains(title.get().toLowerCase(Locale.ROOT))) return;
			if (done) {
				if (--wait <= 0) p.closeContainer();
				return;
			}
			if (wait < 0) wait = limiter.nextDelayTicks(clickDelay.get() + 2, 1, 2);
			if (--wait > 0) return;
			AbstractContainerMenu menu = cs.getMenu();
			String want = sell.get().trim().toLowerCase(Locale.ROOT);
			for (Slot s : menu.slots) {
				if (isPlayerSlot(p, s) || s.getItem().isEmpty() || want.isEmpty()) continue;
				if (nameOf(s.getItem()).contains(want)) {
					mc.gameMode.handleInventoryMouseClick(menu.containerId, s.index, 0, ClickType.PICKUP, p);
					actions++;
					done = true;
					wait = limiter.nextDelayTicks(clickDelay.get() + 2, 1, 2);
					return;
				}
			}
			if (take.get()) {
				for (Slot s : menu.slots) {
					if (isPlayerSlot(p, s) || s.getItem().isEmpty()) continue;
					mc.gameMode.handleInventoryMouseClick(menu.containerId, s.index, 0, ClickType.QUICK_MOVE, p);
					actions++;
					wait = limiter.nextDelayTicks(clickDelay.get(), clickDelay.get() * 0.3, 1);
					return;
				}
			}
			done = true; // nothing left to do in this window
			wait = limiter.nextDelayTicks(clickDelay.get() + 2, 1, 2);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** Highlights chunks that hold at least a number of the blocks you pick. */
	public static class ChunkFinder extends Module {
		public record Result(int chunkX, int chunkZ, int count, BlockPos min, BlockPos max) {
			public BlockPos center() { return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2); }
		}

		private static ChunkFinder instance;

		private final StringListSetting blocks = add(new StringListSetting("Blocks", List.of("minecraft:spawner")).kind(StringListSetting.Kind.BLOCK), "Detection", "Blocks to count per chunk.");
		private final NumberSetting minCount = add(new NumberSetting("Min per chunk", 3, 1, 200), "Detection", "A chunk is flagged when it holds at least this many of the blocks.");
		private final NumberSetting minDistance = add(new NumberSetting("Ignore chunks within", 3, 0, 32), "Detection", "Skip chunks this close to you, so your own base is not flagged.");
		private final NumberSetting range = add(new NumberSetting("Draw range", 256, 32, 1024), "Display", "How far away flagged chunks are drawn.");
		private final NumberSetting maxDrawn = add(new NumberSetting("Max drawn", 20, 1, 100), "Display", "Only the richest chunks are drawn.");
		private final ColorSetting color = add(new ColorSetting("Color", 0xFF55AAFF), "Display", "Outline color.");
		private final BoolSetting notify = add(new BoolSetting("Notify", true), "Alerts", "Pop-up the first time a chunk is flagged.");
		private final BoolSetting waypoint = add(new BoolSetting("Waypoint", false), "Alerts", "Save a waypoint for each flagged chunk.");

		private ScanTarget target;
		private List<String> applied = List.of();
		private final List<Result> results = new ArrayList<>();
		private final Set<Long> announced = new HashSet<>();
		private int ticks;

		public ChunkFinder() {
			super("ChunkFinder", Category.DONUT);
			instance = this;
		}

		public static List<Result> top(int n) {
			if (instance == null) return List.of();
			List<Result> copy = new ArrayList<>(instance.results);
			copy.sort(Comparator.comparingInt(Result::count).reversed());
			return copy.subList(0, Math.min(n, copy.size()));
		}

		@Override
		public String info() { return results.isEmpty() ? "" : String.valueOf(results.size()); }

		@Override
		protected void onEnable() {
			applied = List.copyOf(blocks.get());
			var set = BlockIds.resolve(applied);
			target = ScanTarget.ofBlocks(set);
			ChunkScanner.add(target);
			announced.clear();
			results.clear();
		}

		@Override
		protected void onDisable() {
			if (target != null) ChunkScanner.remove(target);
			target = null;
			results.clear();
		}

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (target == null || mc.player == null || mc.level == null) return;
			if (!applied.equals(blocks.get())) {
				applied = List.copyOf(blocks.get());
				var set = BlockIds.resolve(applied);
				target.setPredicate(s -> set.contains(s.getBlock()));
			}
			if (++ticks % 20 != 0) return;
			ChunkPos me = mc.player.chunkPosition();
			results.clear();
			for (Map.Entry<Long, List<BlockPos>> en : target.hitsByChunk().entrySet()) {
				List<BlockPos> hits = en.getValue();
				if (hits.size() < minCount.asInt()) continue;
				int cx = ChunkPos.getX(en.getKey()), cz = ChunkPos.getZ(en.getKey());
				if (Math.max(Math.abs(cx - me.x), Math.abs(cz - me.z)) <= minDistance.asInt()) continue;
				int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
				for (BlockPos b : hits) {
					x1 = Math.min(x1, b.getX());
					y1 = Math.min(y1, b.getY());
					z1 = Math.min(z1, b.getZ());
					x2 = Math.max(x2, b.getX());
					y2 = Math.max(y2, b.getY());
					z2 = Math.max(z2, b.getZ());
				}
				Result r = new Result(cx, cz, hits.size(), new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2));
				results.add(r);
				if (announced.add(en.getKey())) {
					BlockPos c = r.center();
					if (notify.get()) EspModule.say(r.count() + " blocks in chunk (" + cx + ", " + cz + ") at " + c.getX() + " " + c.getY() + " " + c.getZ());
					if (waypoint.get()) Waypoints.add("Chunk " + r.count(), c);
				}
			}
		}

		@Override
		protected void onRender(RenderFrame f) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return;
			double r2 = range.get() * range.get();
			List<Result> near = new ArrayList<>();
			for (Result r : results) if (mc.player.distanceToSqr(r.center().getX(), r.center().getY(), r.center().getZ()) <= r2) near.add(r);
			near.sort(Comparator.comparingInt(Result::count).reversed());
			int c = color.get() | 0xFF000000;
			for (int i = 0; i < Math.min(near.size(), maxDrawn.asInt()); i++) {
				Result r = near.get(i);
				drawChunk(f, r.chunkX(), r.chunkZ(), r.min().getY(), r.max().getY() + 1, c);
				f.drawText(r.chunkX() * 16 + 8, r.max().getY() + 2.5, r.chunkZ() * 16 + 8, r.count() + " blocks", c);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** Highlights ancient debris and netherite blocks. */
	public static class NetheriteFinder extends EspModule {
		private final ColorSetting debrisColor = add(new ColorSetting("Debris color", 0xFFB8651B), "Colors", "Ancient debris.");
		private final ColorSetting blockColor = add(new ColorSetting("Block color", 0xFF3A3A3A), "Colors", "Netherite blocks.");
		private final BoolSetting notify = add(new BoolSetting("Notify", true), "Alerts", "Pop-up when something new is found.");
		private final BoolSetting waypoint = add(new BoolSetting("Waypoint", false), "Alerts", "Save a waypoint for each find.");

		public NetheriteFinder() { super("NetheriteFinder", Category.DONUT); }

		@Override
		protected ScanTarget createTarget() { return new ScanTarget(s -> s.is(Blocks.ANCIENT_DEBRIS) || s.is(Blocks.NETHERITE_BLOCK)); }

		@Override
		protected int colorOf(BlockState s) { return s.is(Blocks.NETHERITE_BLOCK) ? blockColor.get() : debrisColor.get(); }

		@Override
		protected void onFound(BlockPos p, BlockState s) {
			String what = s.is(Blocks.NETHERITE_BLOCK) ? "Netherite block" : "Ancient debris";
			if (notify.get()) say(what + " at " + p.getX() + " " + p.getY() + " " + p.getZ());
			if (waypoint.get()) Waypoints.add(what, p);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/** Remembers the chunks other players were seen in and outlines them, fading with age. */
	public static class PlayerChunks extends Module {
		private record Seen(String name, double y, long time) {}

		private final NumberSetting forget = add(new NumberSetting("Forget after (min)", 10, 1, 120), "General", "Chunks are dropped this long after the last sighting.");
		private final BoolSetting skipFriends = add(new BoolSetting("Skip friends", true), "General", "Do not mark chunks your friends were in.");
		private final BoolSetting labels = add(new BoolSetting("Names", true), "Display", "Write the player's name above the chunk.");
		private final NumberSetting range = add(new NumberSetting("Draw range", 512, 32, 2048), "Display", "How far away chunks are drawn.");
		private final ColorSetting color = add(new ColorSetting("Color", 0xFFFF5577), "Display", "Outline color (fades as the sighting gets old).");

		private final Map<Long, Seen> chunks = new HashMap<>();
		private Object level;

		public PlayerChunks() { super("PlayerChunks", Category.DONUT); }

		@Override
		public String info() { return chunks.isEmpty() ? "" : String.valueOf(chunks.size()); }

		@Override
		protected void onEnable() { chunks.clear(); }

		@Override
		protected void onDisable() { chunks.clear(); }

		@Override
		protected void onTick() {
			Minecraft mc = Minecraft.getInstance();
			ClientLevel lvl = mc.level;
			if (lvl == null || mc.player == null) return;
			if (lvl != level) {
				chunks.clear();
				level = lvl;
			}
			long now = System.currentTimeMillis();
			long maxAge = (long) (forget.get() * 60_000);
			chunks.values().removeIf(s -> now - s.time() > maxAge);
			for (Player p : lvl.players()) {
				if (p == mc.player || p == mc.getCameraEntity() || p.isSpectator()) continue;
				String name = p.getName().getString();
				if (skipFriends.get() && Friends.is(name)) continue;
				chunks.put(ChunkPos.asLong(p.getBlockX() >> 4, p.getBlockZ() >> 4), new Seen(name, p.getY(), now));
			}
		}

		@Override
		protected void onRender(RenderFrame f) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) return;
			long now = System.currentTimeMillis();
			double maxAge = forget.get() * 60_000;
			double r2 = range.get() * range.get();
			for (Map.Entry<Long, Seen> en : chunks.entrySet()) {
				int cx = ChunkPos.getX(en.getKey()), cz = ChunkPos.getZ(en.getKey());
				double dx = cx * 16 + 8 - mc.player.getX(), dz = cz * 16 + 8 - mc.player.getZ();
				if (dx * dx + dz * dz > r2) continue;
				Seen s = en.getValue();
				double age = Math.min(1.0, (now - s.time()) / maxAge);
				int alpha = (int) (255 - 190 * age);
				int c = (alpha << 24) | (color.get() & 0xFFFFFF);
				drawChunk(f, cx, cz, s.y(), s.y() + 2, c);
				if (labels.get()) f.drawText(cx * 16 + 8, s.y() + 3, cz * 16 + 8, s.name(), c);
			}
		}
	}
}
