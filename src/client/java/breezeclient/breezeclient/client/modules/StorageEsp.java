package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.settings.ColorSetting;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public class StorageEsp extends EspModule {
	private static final Set<Block> OTHER = Set.of(
		Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
		Blocks.BREWING_STAND, Blocks.CRAFTER, Blocks.CHISELED_BOOKSHELF, Blocks.DECORATED_POT
	);

	private final ColorSetting chest = add(new ColorSetting("Chest", 0xFFFF9A1F));
	private final ColorSetting trapped = add(new ColorSetting("Trapped chest", 0xFFFF3030));
	private final ColorSetting barrel = add(new ColorSetting("Barrel", 0xFFB5651D));
	private final ColorSetting ender = add(new ColorSetting("Ender chest", 0xFFAA44FF));
	private final ColorSetting shulker = add(new ColorSetting("Shulker", 0xFFFF55FF));
	private final ColorSetting other = add(new ColorSetting("Other", 0xFFAAAAAA));

	public StorageEsp() { super("StorageESP", Category.RENDER); }

	public static boolean isStorage(BlockState s) {
		Block b = s.getBlock();
		return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL || b == Blocks.ENDER_CHEST
			|| s.is(BlockTags.SHULKER_BOXES) || OTHER.contains(b);
	}

	@Override
	protected ScanTarget createTarget() { return new ScanTarget(StorageEsp::isStorage); }

	@Override
	protected int colorOf(BlockState s) {
		Block b = s.getBlock();
		if (b == Blocks.CHEST) return chest.get();
		if (b == Blocks.TRAPPED_CHEST) return trapped.get();
		if (b == Blocks.BARREL) return barrel.get();
		if (b == Blocks.ENDER_CHEST) return ender.get();
		if (s.is(BlockTags.SHULKER_BOXES)) return shulker.get();
		return other.get();
	}
}
