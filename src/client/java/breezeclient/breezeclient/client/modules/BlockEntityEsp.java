package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.render.RenderUtil;
import breezeclient.breezeclient.client.world.ChunkScanner;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.RenderFrame;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Highlights block entities straight from the chunk data the server sent, so storage, spawners, signs and beds show up even
 * when their blocks are camouflaged, hidden by anti-xray or tucked behind other blocks.
 */
public class BlockEntityEsp extends Module {
	private final NumberSetting range = add(new NumberSetting("Range", 128, 16, 512), "Render", "How far away block entities are drawn.");
	private final BoolSetting tracers = add(new BoolSetting("Tracers", false), "Render", "Lines from the crosshair to each block entity.");
	private final BoolSetting fill = add(new BoolSetting("Fill", true), "Render", "Translucent fill inside the boxes.");
	private final BoolSetting chests = add(new BoolSetting("Chests", true), "Types", "Chests and trapped chests.");
	private final BoolSetting barrels = add(new BoolSetting("Barrels", true), "Types", "Barrels.");
	private final BoolSetting shulkers = add(new BoolSetting("Shulker boxes", true), "Types", "Shulker boxes.");
	private final BoolSetting enderChests = add(new BoolSetting("Ender chests", false), "Types", "Ender chests.");
	private final BoolSetting spawners = add(new BoolSetting("Spawners", true), "Types", "Mob spawners and trial spawners.");
	private final BoolSetting hoppers = add(new BoolSetting("Hoppers and dispensers", false), "Types", "Hoppers, droppers and dispensers.");
	private final BoolSetting furnaces = add(new BoolSetting("Furnaces", false), "Types", "Furnaces, smokers and blast furnaces.");
	private final BoolSetting signs = add(new BoolSetting("Signs", false), "Types", "Signs and hanging signs.");
	private final BoolSetting signText = add(new BoolSetting("Show sign text", true), "Types", "Show the first lines of each sign above it.", signs::get);
	private final BoolSetting beds = add(new BoolSetting("Beds", false), "Types", "Beds (players sleep or set spawn near bases).");
	private final BoolSetting banners = add(new BoolSetting("Banners", false), "Types", "Banners.");
	private final BoolSetting skulls = add(new BoolSetting("Skulls and heads", false), "Types", "Player heads and mob skulls.");
	private final BoolSetting others = add(new BoolSetting("Everything else", false), "Types", "Any other block entity (beacons, crafters, decorated pots, ...).");
	private final ColorSetting chestColor = add(new ColorSetting("Chest color", 0xFFFF9A1F), "Colors", "Color for chests.");
	private final ColorSetting spawnerColor = add(new ColorSetting("Spawner color", 0xFF00FF66), "Colors", "Color for spawners.");
	private final ColorSetting otherColor = add(new ColorSetting("Other color", 0xFFAAAAFF), "Colors", "Color for every other type.");

	public BlockEntityEsp() { super("BlockEntityESP", Category.RENDER); }

	/** @return an ARGB color when this entity type is enabled, else 0 */
	private int colorFor(BlockEntity be) {
		if (be instanceof ChestBlockEntity) return chests.get() ? chestColor.get() : 0;
		if (be instanceof BarrelBlockEntity) return barrels.get() ? 0xFFB5651D : 0;
		if (be instanceof ShulkerBoxBlockEntity) return shulkers.get() ? 0xFFFF55FF : 0;
		if (be instanceof EnderChestBlockEntity) return enderChests.get() ? 0xFFAA44FF : 0;
		if (be instanceof SpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity) return spawners.get() ? spawnerColor.get() : 0;
		if (be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity) return hoppers.get() ? 0xFF999999 : 0;
		if (be instanceof AbstractFurnaceBlockEntity) return furnaces.get() ? 0xFFBBBBBB : 0;
		if (be instanceof SignBlockEntity) return signs.get() ? 0xFFE8D28A : 0;
		if (be instanceof BedBlockEntity) return beds.get() ? 0xFFFF5555 : 0;
		if (be instanceof BannerBlockEntity) return banners.get() ? 0xFF55AAFF : 0;
		if (be instanceof SkullBlockEntity) return skulls.get() ? 0xFFDDDDDD : 0;
		return others.get() ? otherColor.get() : 0;
	}

	@Override
	protected void onRender(RenderFrame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		var cam = mc.gameRenderer.getMainCamera();
		Vec3 cp = cam.position();
		var fwd = cam.forwardVector();
		double r2 = range.get() * range.get();
		boolean doFill = fill.get() && f instanceof RenderUtil;
		for (long key : ChunkScanner.loadedChunks()) {
			int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
			double ddx = cx * 16 + 8 - cp.x, ddz = cz * 16 + 8 - cp.z;
			if (ddx * ddx + ddz * ddz > (range.get() + 16) * (range.get() + 16)) continue;
			LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
			if (chunk == null) continue;
			for (BlockEntity be : chunk.getBlockEntities().values()) {
				BlockPos p = be.getBlockPos();
				double dx = p.getX() + 0.5 - cp.x, dy = p.getY() + 0.5 - cp.y, dz = p.getZ() + 0.5 - cp.z;
				if (dx * dx + dy * dy + dz * dz > r2) continue;
				int c = colorFor(be);
				if (c == 0) continue;
				c |= 0xFF000000;
				f.drawBox(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, c);
				if (doFill) ((RenderUtil) f).drawFilledBox(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, (c & 0xFFFFFF) | 0x35000000);
				if (tracers.get()) f.drawLine(cp.x + fwd.x() * 0.3, cp.y + fwd.y() * 0.3, cp.z + fwd.z() * 0.3, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, c);
				if (signText.get() && be instanceof SignBlockEntity sign) {
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < 4; i++) {
						String line = sign.getFrontText().getMessage(i, false).getString().trim();
						if (!line.isEmpty()) sb.append(sb.length() > 0 ? " | " : "").append(line);
					}
					if (sb.length() > 0) f.drawText(p.getX() + 0.5, p.getY() + 1.4, p.getZ() + 0.5, sb.toString(), c);
				}
			}
		}
	}
}
