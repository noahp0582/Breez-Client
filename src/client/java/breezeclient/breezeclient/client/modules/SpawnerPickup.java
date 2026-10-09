package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.nav.PathWalker;
import breezeclient.breezeclient.client.safety.StaffTracker;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.humanize.Rotation;
import breezeclient.breezeclient.core.nav.Pos;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Mines spawners one by one with a silk touch pickaxe, walking between them, then runs a home command and moves
 * only spawner items into an ender chest.
 *
 * <p>Vanilla resets block-break progress whenever the crosshair leaves the block, so the "look away" pause happens once
 * per spawner (configurable) at 40-70% of the expected break time, and the final pass runs uninterrupted.
 */
public class SpawnerPickup extends Module {
	private enum State {
		PICK, WALK, EQUIP_AIM, MINE_FIRST, LOOK_AWAY, AWAY_WAIT, LOOK_BACK, MINE_FINISH, SETTLE, COLLECT,
		HOME_CMD, TELEPORT_WAIT, WALK_CHEST, OPEN_CHEST, DEPOSIT, CLOSE_CHEST, RETURN_CMD, RETURN_WAIT, DONE
	}

	private final NumberSetting searchRadius = add(new NumberSetting("Search radius", 24, 4, 96));
	private final NumberSetting batch = add(new NumberSetting("Deposit every N spawners", 10, 1, 64));
	private final NumberSetting lookAways = add(new NumberSetting("Look-aways per spawner", 1, 0, 3));
	private final BoolSetting sneak = add(new BoolSetting("Sneak while mining", true));
	private final StringSetting homeCommand = add(new StringSetting("Home command", "/home base"));
	private final StringSetting returnCommand = add(new StringSetting("Return command", "/back"));
	private final StringSetting chestPos = add(new StringSetting("Ender chest (x y z)", ""));
	private final NumberSetting stopIfPlayer = add(new NumberSetting("Stop if player within (0=off)", 0, 0, 128));

	private final Random rng = new Random();
	private final ActionLimiter limiter = new ActionLimiter(rng);
	private final PathWalker walker = new PathWalker();
	private final Set<Long> failed = new HashSet<>();

	private ScanTarget spawners;
	private State state = State.DONE;
	private int timer;
	private int mineTicks;
	private int lookAwayAt;
	private int expectedTotal;
	private boolean clickedChest;
	private int depositCarried = -1;
	private int depositStuck;
	private int awaysDone;
	private int attempts;
	private BlockPos target;
	private Vec3 teleportFrom = Vec3.ZERO;
	private int collected;
	private int stage;
	private boolean moreRemaining;

	public SpawnerPickup() { super("SpawnerPickup", Category.WORLD); }

	public void setChest(BlockPos p) { chestPos.set(p.getX() + " " + p.getY() + " " + p.getZ()); }

	@Override
	public String info() { return state == State.DONE ? "" : state.name().toLowerCase(); }

	// lifecycle --------------------------------------------------------------------------------------------------

	@Override
	protected boolean persistsEnabled() { return false; }

	@Override
	protected void onEnable() {
		spawners = new ScanTarget(s -> s.is(Blocks.SPAWNER));
		ChunkScanner.add(spawners);
		failed.clear();
		collected = 0;
		target = null;
		go(State.PICK, 20);
	}

	@Override
	protected void onDisable() {
		if (spawners != null) ChunkScanner.remove(spawners);
		spawners = null;
		walker.cancel();
		RotationController.clear();
		Minecraft mc = Minecraft.getInstance();
		mc.options.keyAttack.setDown(false);
		mc.options.keyShift.setDown(false);
		if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
		state = State.DONE;
	}

	private void stop(String why) {
		EspModule.say("SpawnerPickup stopped: " + why);
		setEnabled(false);
	}

	private void go(State s, int wait) {
		state = s;
		timer = wait;
		stage = 0;
		clickedChest = false;
		depositCarried = -1;
		depositStuck = 0;
	}

	// main loop --------------------------------------------------------------------------------------------------

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.gameMode == null) return;

		if (stopIfPlayer.get() > 0) {
			var hit = StaffTracker.findNearby(stopIfPlayer.get(), false, false, true, Set.of());
			if (hit != null) {
				stop("player " + hit.name() + " nearby");
				return;
			}
		}
		walker.tick();
		if (timer > 0) {
			timer--;
			return;
		}

		switch (state) {
			case PICK -> pick(mc, p);
			case WALK -> walk(p);
			case EQUIP_AIM -> equipAndAim(mc, p);
			case MINE_FIRST -> mineFirst(mc, p);
			case LOOK_AWAY -> lookAway(mc, p);
			case AWAY_WAIT -> {
				if (RotationController.busy()) return;
				go(State.LOOK_BACK, limiter.between(10, 30));
			}
			case LOOK_BACK -> lookBack(p);
			case MINE_FINISH -> mineFinish(mc, p);
			case SETTLE -> go(State.COLLECT, 0);
			case COLLECT -> collect(mc, p);
			case HOME_CMD -> homeCmd(mc, p);
			case TELEPORT_WAIT -> teleportWait(p);
			case WALK_CHEST -> walkChest(p);
			case OPEN_CHEST -> openChest(mc, p);
			case DEPOSIT -> deposit(mc, p);
			case CLOSE_CHEST -> closeChest(p);
			case RETURN_CMD -> returnCmd(p);
			case RETURN_WAIT -> {
				if (p.position().distanceToSqr(teleportFrom) > 100 || stage++ > 300) go(State.PICK, limiter.between(40, 80));
			}
			case DONE -> setEnabled(false);
		}
	}

	// helpers ----------------------------------------------------------------------------------------------------

	private int spawnersCarried(LocalPlayer p) {
		int n = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(Items.SPAWNER)) n += s.getCount();
		}
		return n;
	}

	private boolean inventoryFull(LocalPlayer p) {
		for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) return false;
		return true;
	}

	private int silkPickaxeSlot(Minecraft mc, LocalPlayer p) {
		var enchants = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		var silk = enchants.getOrThrow(Enchantments.SILK_TOUCH);
		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.isEmpty() || !s.is(ItemTags.PICKAXES)) continue;
			if (EnchantmentHelper.getItemEnchantmentLevel(silk, s) > 0 && s.getMaxDamage() - s.getDamageValue() > 15) return i;
		}
		return -1;
	}

	private int remainingInRange(LocalPlayer p) {
		int n = 0;
		double r = searchRadius.get();
		for (var list : spawners.chunks()) {
			for (BlockPos b : list) if (!failed.contains(b.asLong()) && p.distanceToSqr(center(b)) <= r * r) n++;
		}
		return n;
	}

	private Vec3 center(BlockPos pos) { return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5); }

	private void say(LocalPlayer p, String text) {
		if (text == null || text.isBlank()) return;
		if (text.startsWith("/")) p.connection.sendCommand(text.substring(1));
		else p.connection.sendChat(text);
	}

	// states -----------------------------------------------------------------------------------------------------

	private void pick(Minecraft mc, LocalPlayer p) {
		mc.options.keyAttack.setDown(false);
		mc.options.keyShift.setDown(false);
		boolean full = inventoryFull(p);
		if (full && spawnersCarried(p) == 0) {
			stop("inventory is full, free some space first (broken spawners would be lost)");
			return;
		}
		if (spawnersCarried(p) >= batch.asInt() || (full && spawnersCarried(p) > 0)) {
			go(State.HOME_CMD, limiter.between(10, 30));
			return;
		}
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		double r = searchRadius.get();
		for (var list : spawners.chunks()) {
			for (BlockPos b : list) {
				if (failed.contains(b.asLong())) continue;
				double d = p.distanceToSqr(center(b));
				if (d <= r * r && d < bestD) {
					bestD = d;
					best = b;
				}
			}
		}
		if (best == null) {
			if (spawnersCarried(p) > 0) go(State.HOME_CMD, limiter.between(10, 30));
			else go(State.DONE, 0);
			return;
		}
		if (silkPickaxeSlot(mc, p) < 0) {
			stop("no silk touch pickaxe in the hotbar (needs 15+ durability left)");
			return;
		}
		target = best;
		attempts = 0;
		if (!walker.start(PathWalker.posOf(best), 3.3)) {
			failed.add(best.asLong());
			go(State.PICK, 5);
			return;
		}
		go(State.WALK, 0);
	}

	private void walk(LocalPlayer p) {
		switch (walker.state()) {
			case WALKING -> { }
			case ARRIVED -> {
				RotationController.clear();
				go(State.EQUIP_AIM, limiter.between(4, 12));
			}
			default -> {
				if (++attempts < 3 && walker.start(PathWalker.posOf(target), 3.3)) return;
				failed.add(target.asLong());
				EspModule.say("SpawnerPickup: could not reach spawner at " + target.toShortString() + ", skipping");
				go(State.PICK, 10);
			}
		}
	}

	private void equipAndAim(Minecraft mc, LocalPlayer p) {
		int slot = silkPickaxeSlot(mc, p);
		if (slot < 0) {
			stop("silk touch pickaxe gone");
			return;
		}
		p.getInventory().setSelectedSlot(slot);
		if (sneak.get()) mc.options.keyShift.setDown(true);
		RotationController.aimAtPoint(center(target), 14f);
		var state = mc.level.getBlockState(target);
		float progress = state.getDestroyProgress(p, mc.level, target);
		int total = progress <= 0f ? 400 : (int) Math.ceil(1.0 / progress);
		expectedTotal = total;
		lookAwayAt = Math.max(5, (int) (total * (0.4 + 0.3 * rng.nextDouble())));
		mineTicks = 0;
		awaysDone = 0;
		go(State.MINE_FIRST, 0);
	}

	private boolean onTarget(Minecraft mc) {
		HitResult hr = mc.hitResult;
		return hr instanceof BlockHitResult bh && bh.getBlockPos().equals(target);
	}

	private void mineFirst(Minecraft mc, LocalPlayer p) {
		if (!mc.level.getBlockState(target).is(Blocks.SPAWNER)) {
			broken(mc);
			return;
		}
		if (mc.screen != null) { // vanilla only continues an attack with no screen open; do not burn the timeout
			mc.options.keyAttack.setDown(false);
			return;
		}
		RotationController.aimAtPoint(center(target), 12f);
		if (RotationController.busy() && !onTarget(mc)) return;
		mc.options.keyAttack.setDown(true);
		mineTicks++;
		if (awaysDone < lookAways.asInt() && mineTicks >= lookAwayAt) go(State.LOOK_AWAY, 0);
		else if (mineTicks > 20 * 40) giveUp(mc, "mining timed out");
	}

	private void lookAway(Minecraft mc, LocalPlayer p) {
		mc.options.keyAttack.setDown(false);
		RotationController.aimAt(new Rotation(p.getYRot() + 90f, p.getXRot()), 14f); // 90 degrees to the right
		awaysDone++;
		go(State.AWAY_WAIT, 0);
	}

	private void lookBack(LocalPlayer p) {
		RotationController.aimAtPoint(center(target), 14f);
		go(State.MINE_FINISH, 0);
		mineTicks = 0;
		lookAwayAt = Math.max(5, (int) (expectedTotal * (0.4 + 0.3 * rng.nextDouble())));
	}

	private void mineFinish(Minecraft mc, LocalPlayer p) {
		if (!mc.level.getBlockState(target).is(Blocks.SPAWNER)) {
			broken(mc);
			return;
		}
		if (mc.screen != null) {
			mc.options.keyAttack.setDown(false);
			return;
		}
		RotationController.aimAtPoint(center(target), 12f);
		if (RotationController.busy() && !onTarget(mc)) return;
		mc.options.keyAttack.setDown(true);
		mineTicks++;
		if (awaysDone < lookAways.asInt() && mineTicks >= lookAwayAt) go(State.LOOK_AWAY, 0);
		else if (mineTicks > 20 * 40) giveUp(mc, "mining timed out");
	}

	private void giveUp(Minecraft mc, String why) {
		mc.options.keyAttack.setDown(false);
		failed.add(target.asLong());
		EspModule.say("SpawnerPickup: " + why + " at " + target.toShortString());
		go(State.PICK, 10);
	}

	private void broken(Minecraft mc) {
		failed.add(target.asLong()); // done: never target this position again, the scanner may still list it for a while
		mc.options.keyAttack.setDown(false);
		RotationController.clear();
		go(State.SETTLE, limiter.between(6, 14));
	}

	private void collect(Minecraft mc, LocalPlayer p) {
		ItemEntity drop = null;
		double best = 64;
		for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8))) {
			if (!e.getItem().is(Items.SPAWNER)) continue;
			double d = e.distanceToSqr(p);
			if (d < best) {
				best = d;
				drop = e;
			}
		}
		if (drop == null) {
			if (stage++ < 40) return;
			go(State.PICK, limiter.between(5, 15));
			return;
		}
		if (best < 1.5) {
			go(State.PICK, limiter.between(10, 25));
			return;
		}
		if (!walker.walking()) {
			if (!walker.start(new Pos(drop.getBlockX(), drop.blockPosition().getY(), drop.getBlockZ()), 0.9)) {
				if (stage++ > 20) go(State.PICK, 10);
			}
		}
	}

	private void homeCmd(Minecraft mc, LocalPlayer p) {
		mc.options.keyShift.setDown(false);
		teleportFrom = p.position();
		moreRemaining = remainingInRange(p) > 0;
		say(p, homeCommand.get());
		go(State.TELEPORT_WAIT, limiter.between(20, 40));
	}

	private void teleportWait(LocalPlayer p) {
		if (p.position().distanceToSqr(teleportFrom) > 100 || stage++ > 300) go(State.WALK_CHEST, limiter.between(30, 60));
	}

	private BlockPos chest() {
		String[] a = chestPos.get().trim().split("\\s+");
		if (a.length != 3) return null;
		try {
			return new BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void walkChest(LocalPlayer p) {
		BlockPos c = chest();
		if (c == null) {
			stop("ender chest position not set (look at it and use .spawner chest)");
			return;
		}
		if (stage == 0) {
			stage = 1;
			if (!walker.start(PathWalker.posOf(c), 3.0)) {
				stop("could not path to the ender chest at " + c.toShortString());
			}
			return;
		}
		switch (walker.state()) {
			case ARRIVED -> go(State.OPEN_CHEST, limiter.between(6, 14));
			case WALKING -> { }
			default -> stop("lost the way to the ender chest");
		}
	}

	private void openChest(Minecraft mc, LocalPlayer p) {
		BlockPos c = chest();
		if (c == null) return;
		if (!mc.level.getBlockState(c).is(Blocks.ENDER_CHEST)) {
			stop("the saved position is not an ender chest (set it again with .spawner chest)");
			return;
		}
		if (p.containerMenu != p.inventoryMenu) {
			if (clickedChest) { // only a menu we opened ourselves counts
				go(State.DEPOSIT, limiter.between(8, 16));
			} else {
				p.closeContainer(); // something else opened a menu (server GUI): never deposit into it
			}
			return;
		}
		RotationController.aimAtPoint(center(c), 14f);
		if (RotationController.busy()) return;
		if (mc.hitResult instanceof BlockHitResult bh && bh.getBlockPos().equals(c)) {
			if (stage++ % 20 == 0) {
				mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bh);
				p.swing(InteractionHand.MAIN_HAND);
				clickedChest = true;
			}
			if (stage > 100) stop("could not open the ender chest");
		} else if (stage++ > 100) {
			stop("ender chest not in line of sight");
		}
	}

	private void deposit(Minecraft mc, LocalPlayer p) {
		AbstractContainerMenu menu = p.containerMenu;
		if (menu == p.inventoryMenu) {
			go(State.OPEN_CHEST, 5);
			return;
		}
		int carried = spawnersCarried(p);
		if (depositCarried >= 0 && carried >= depositCarried && ++depositStuck >= 3) {
			go(State.CLOSE_CHEST, limiter.between(8, 18)); // nothing is moving: the chest is full
			return;
		}
		if (depositCarried >= 0 && carried < depositCarried) depositStuck = 0;
		for (Slot s : menu.slots) {
			// only slots of the player's own inventory, only spawners
			if (s.container == p.getInventory() && s.getItem().is(Items.SPAWNER)) {
				depositCarried = carried;
				mc.gameMode.handleInventoryMouseClick(menu.containerId, s.index, 0, ClickType.QUICK_MOVE, p);
				timer = limiter.nextDelayTicks(5, 1.5, 2);
				return;
			}
		}
		go(State.CLOSE_CHEST, limiter.between(8, 18));
	}

	private void closeChest(LocalPlayer p) {
		p.closeContainer();
		if (spawnersCarried(p) > 0) EspModule.say("SpawnerPickup: ender chest could not take every spawner");
		go(State.RETURN_CMD, limiter.between(10, 25));
	}

	private void returnCmd(LocalPlayer p) {
		boolean more = moreRemaining;
		if (returnCommand.get().isBlank() || !more) {
			go(State.DONE, 0);
			EspModule.say("SpawnerPickup finished a batch.");
			return;
		}
		teleportFrom = p.position();
		say(p, returnCommand.get());
		go(State.RETURN_WAIT, limiter.between(20, 40));
	}
}
