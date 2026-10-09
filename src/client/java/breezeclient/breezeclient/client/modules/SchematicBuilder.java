package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.nav.PathWalker;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.schem.SchematicData;
import breezeclient.breezeclient.core.schem.SchematicReader;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringSetting;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Builds a loaded schematic layer by layer in straight sweeps. Walks into reach, eases the camera onto a real block
 * face, and places at randomized intervals, like a player would. Orientation is handled for axis blocks, slabs and
 * half-stairs; other facing-dependent blocks are placed but may need manual rotation (counted in status).
 */
public class SchematicBuilder extends Module {
	private enum State { IDLE, PICK, WALK, AIM, PLACE, VERIFY, NEED_ITEMS, DONE }

	private record Target(BlockPos pos, BlockState want) {}

	private final StringSetting file = add(new StringSetting("File", ""));
	private final NumberSetting reach = add(new NumberSetting("Reach", 4.0, 2.5, 4.5));
	private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 4, 1, 20));

	private final Random rng = new Random();
	private final ActionLimiter limiter = new ActionLimiter(rng);
	private final PathWalker walker = new PathWalker();

	private List<Target> queue = List.of();
	private final List<Target> deferred = new ArrayList<>();
	private int idx;
	private int pass;
	private State state = State.IDLE;
	private int timer;
	private int stage;
	private int retries;
	private BlockPos origin;
	private SchematicData loaded;
	private int placed;
	private int skipped;
	private int orientationUnsure;
	private BlockHitResult plannedHit;
	private int plannedSlot = -1;

	public SchematicBuilder() { super("SchematicBuilder", Category.WORLD); }

	public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("breez-client").resolve("schematics"); }

	public static List<String> files() {
		try (Stream<Path> s = Files.isDirectory(dir()) ? Files.list(dir()) : Stream.<Path>empty()) {
			return s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".nbt")).sorted().toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	/** @return an error message, or null when the file loaded. */
	public String load(String name) {
		Path p = dir().resolve(name).normalize();
		if (!p.startsWith(dir().normalize())) return "File must be inside " + dir();
		try {
			loaded = SchematicReader.read(Files.readAllBytes(p), name);
			file.set(name);
			return null;
		} catch (IOException e) {
			loaded = null;
			return e.getMessage();
		}
	}

	public void setOrigin(BlockPos p) { origin = p; }

	public BlockPos origin() { return origin; }

	public boolean loaded() { return loaded != null; }

	public String status() {
		if (loaded == null) return "No schematic loaded. Put files in " + dir() + " and use .build load <file>.";
		return String.format("%s: %d/%d placed, %d skipped, %d deferred, state %s%s", file.get(), placed, loaded.blocks().size(), skipped,
			deferred.size(), state.name().toLowerCase(), orientationUnsure > 0 ? ", " + orientationUnsure + " orientation-dependent blocks may need rotating" : "");
	}

	@Override
	public String info() { return loaded == null ? "" : placed + "/" + loaded.blocks().size(); }

	// lifecycle --------------------------------------------------------------------------------------------------

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (loaded == null || origin == null || mc.level == null) {
			EspModule.say("SchematicBuilder: load a schematic and set the origin first (.build load / .build origin).");
			setEnabled(false);
			return;
		}
		List<Target> q = new ArrayList<>();
		for (SchematicData.Block b : loaded.linearOrder()) {
			BlockState st = parse(b.state());
			if (st == null || st.isAir()) continue;
			q.add(new Target(origin.offset(b.x(), b.y(), b.z()), st));
		}
		queue = q;
		deferred.clear();
		idx = 0;
		pass = 0;
		placed = skipped = orientationUnsure = 0;
		state = State.PICK;
		timer = 10;
	}

	@Override
	protected void onDisable() {
		walker.cancel();
		RotationController.clear();
		state = State.IDLE;
	}

	private static BlockState parse(String s) {
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
		} catch (CommandSyntaxException e) {
			return null;
		}
	}

	// main loop --------------------------------------------------------------------------------------------------

	private Target current() { return idx < queue.size() ? queue.get(idx) : null; }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.gameMode == null) return;
		walker.tick();
		if (timer > 0) {
			timer--;
			return;
		}
		switch (state) {
			case PICK -> pick(mc, p);
			case WALK -> walk(mc, p);
			case AIM -> aim(mc, p);
			case PLACE -> place(mc, p);
			case VERIFY -> verify(mc, p);
			case NEED_ITEMS -> needItems(mc, p);
			case DONE -> {
				EspModule.say("SchematicBuilder finished. " + status());
				setEnabled(false);
			}
			default -> { }
		}
	}

	private void advance() {
		idx++;
		retries = 0;
		state = State.PICK;
	}

	private void skip(String why) {
		skipped++;
		Target t = current();
		if (t != null && why != null) EspModule.say("SchematicBuilder: skipping " + t.pos().toShortString() + " (" + why + ")");
		advance();
	}

	private void pick(Minecraft mc, LocalPlayer p) {
		while (idx < queue.size()) {
			Target t = queue.get(idx);
			if (!mc.level.getChunkSource().hasChunk(t.pos().getX() >> 4, t.pos().getZ() >> 4)) {
				skip("chunk not loaded");
				continue;
			}
			if (mc.level.getBlockState(t.pos()) == t.want()) {
				placed++;
				idx++;
				continue;
			}
			break;
		}
		if (idx >= queue.size()) {
			if (!deferred.isEmpty() && pass < 2) {
				pass++;
				queue = new ArrayList<>(deferred);
				deferred.clear();
				idx = 0;
				return;
			}
			state = State.DONE;
			return;
		}
		Target t = queue.get(idx);
		Vec3 eye = p.getEyePosition();
		if (eye.distanceTo(Vec3.atCenterOf(t.pos())) > reach.get() - 0.2) {
			if (!walker.start(PathWalker.posOf(t.pos()), Math.max(2.0, reach.get() - 0.8))) {
				skip("no walkable route");
				return;
			}
			state = State.WALK;
			return;
		}
		state = State.AIM;
		stage = 0;
		plannedHit = null;
	}

	private void walk(Minecraft mc, LocalPlayer p) {
		switch (walker.state()) {
			case WALKING -> { }
			case ARRIVED -> {
				RotationController.clear();
				state = State.AIM;
				stage = 0;
				plannedHit = null;
				timer = limiter.between(2, 6);
			}
			default -> {
				if (retries++ < 2 && current() != null) {
					state = State.PICK;
					return;
				}
				skip("could not get close enough");
			}
		}
	}

	private void aim(Minecraft mc, LocalPlayer p) {
		Target t = current();
		if (t == null) {
			state = State.DONE;
			return;
		}
		if (plannedHit == null) {
			plannedHit = findFace(mc, p, t);
			if (plannedHit == null) {
				deferred.add(t);
				advance();
				return;
			}
			plannedSlot = prepareItem(mc, p, t.want());
			if (plannedSlot == -2) {
				state = State.NEED_ITEMS;
				return;
			}
			if (plannedSlot == -3) {
				plannedHit = null; // swap in progress; re-plan once the stack has moved
				return;
			}
			RotationController.aimAtPoint(plannedHit.getLocation(), 12f);
			stage = 0;
		}
		if (RotationController.busy() && stage++ < 60) return;
		state = State.PLACE;
		timer = limiter.nextDelayTicks(delay.get() * 0.5, 1, 0);
	}

	/** Finds a neighbouring support block and a point on its face that is in reach and in line of sight. */
	private BlockHitResult findFace(Minecraft mc, LocalPlayer p, Target t) {
		Vec3 eye = p.getEyePosition();
		var want = t.want();
		Direction.Axis wantAxis = want.hasProperty(BlockStateProperties.AXIS) ? want.getValue(BlockStateProperties.AXIS) : null;
		double yFrac = 0.5;
		if (want.hasProperty(BlockStateProperties.SLAB_TYPE) && want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP) yFrac = 0.8;
		else if (want.hasProperty(BlockStateProperties.SLAB_TYPE) && want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM) yFrac = 0.2;
		if (want.hasProperty(BlockStateProperties.HALF)) yFrac = want.getValue(BlockStateProperties.HALF) == Half.TOP ? 0.8 : 0.2;
		if (want.hasProperty(BlockStateProperties.FACING) || want.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) orientationUnsure++;

		BlockHitResult best = null;
		double bestD = Double.MAX_VALUE;
		for (Direction d : Direction.values()) {
			BlockPos support = t.pos().relative(d);
			BlockState ss = mc.level.getBlockState(support);
			if (ss.isAir() || !ss.getFluidState().isEmpty() || ss.canBeReplaced()) continue;
			Direction face = d.getOpposite(); // face of the support block that touches our target
			if (wantAxis != null && face.getAxis() != wantAxis) continue;
			double fy = face.getAxis() == Direction.Axis.Y ? 0.5 : yFrac;
			Vec3 hit = new Vec3(support.getX() + 0.5 + face.getStepX() * 0.5, support.getY() + (face.getAxis() == Direction.Axis.Y ? (face == Direction.UP ? 1.0 : 0.0) : fy),
				support.getZ() + 0.5 + face.getStepZ() * 0.5);
			double dist = eye.distanceTo(hit);
			if (dist > reach.get()) continue;
			var clip = mc.level.clip(new ClipContext(eye, hit.add(hit.subtract(eye).normalize().scale(0.05)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (clip.getType() != HitResult.Type.BLOCK || !clip.getBlockPos().equals(support)) continue;
			if (dist < bestD) {
				bestD = dist;
				best = new BlockHitResult(hit, face, support, false);
			}
		}
		return best;
	}

	/** @return hotbar slot holding the item (selected), -2 when missing, -3 while a swap is in progress. */
	private int prepareItem(Minecraft mc, LocalPlayer p, BlockState want) {
		Item item = want.getBlock().asItem();
		if (p.getAbilities().instabuild) {
			// creative: any stack of it, otherwise nothing to place
		}
		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item)) {
				p.getInventory().setSelectedSlot(i);
				return i;
			}
		}
		for (int i = 9; i < 36; i++) {
			if (p.getInventory().getItem(i).is(item)) {
				int hot = p.getInventory().getSelectedSlot();
				mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, i, hot, ClickType.SWAP, p);
				timer = limiter.nextDelayTicks(4, 1, 2);
				return -3;
			}
		}
		return -2;
	}

	private void place(Minecraft mc, LocalPlayer p) {
		Target t = current();
		if (t == null || plannedHit == null) {
			state = State.PICK;
			return;
		}
		BlockHitResult use = plannedHit;
		if (mc.hitResult instanceof BlockHitResult cross && cross.getBlockPos().equals(plannedHit.getBlockPos()) && cross.getDirection() == plannedHit.getDirection()) {
			use = new BlockHitResult(cross.getLocation(), cross.getDirection(), cross.getBlockPos(), false);
		}
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, use);
		p.swing(InteractionHand.MAIN_HAND);
		state = State.VERIFY;
		timer = limiter.nextDelayTicks(delay.get(), delay.get() * 0.3, 1);
	}

	private void verify(Minecraft mc, LocalPlayer p) {
		Target t = current();
		if (t == null) {
			state = State.PICK;
			return;
		}
		BlockState now = mc.level.getBlockState(t.pos());
		if (now.getBlock() == t.want().getBlock()) {
			placed++;
			advance();
			timer = limiter.nextDelayTicks(delay.get(), delay.get() * 0.3, 1);
			return;
		}
		if (++retries >= 3) {
			skip("placement was rejected");
			return;
		}
		plannedHit = null;
		state = State.AIM;
		timer = limiter.between(2, 6);
	}

	private void needItems(Minecraft mc, LocalPlayer p) {
		Target t = current();
		if (t == null) {
			state = State.PICK;
			return;
		}
		if (stage++ % 100 == 0) EspModule.say("SchematicBuilder: out of " + t.want().getBlock().getName().getString() + ". Add some to your inventory (waiting).");
		Item item = t.want().getBlock().asItem();
		for (int i = 0; i < 36; i++) {
			if (p.getInventory().getItem(i).is(item)) {
				state = State.AIM;
				plannedHit = null;
				return;
			}
		}
	}
}
