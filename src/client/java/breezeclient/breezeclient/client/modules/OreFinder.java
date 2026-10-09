package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.settings.BoolSetting;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Scan-based ore ESP (works on any server). Includes netherite via ancient debris. */
public class OreFinder extends EspModule {
	private final BoolSetting diamond = add(new BoolSetting("Diamond", true));
	private final BoolSetting debris = add(new BoolSetting("Ancient debris", true));
	private final BoolSetting emerald = add(new BoolSetting("Emerald", false));
	private final BoolSetting gold = add(new BoolSetting("Gold", false));
	private final BoolSetting iron = add(new BoolSetting("Iron", false));
	private final BoolSetting redstone = add(new BoolSetting("Redstone", false));
	private final BoolSetting lapis = add(new BoolSetting("Lapis", false));
	private final BoolSetting coal = add(new BoolSetting("Coal", false));
	private final BoolSetting copper = add(new BoolSetting("Copper", false));
	private final BoolSetting quartz = add(new BoolSetting("Quartz", false));

	public OreFinder() { super("OreFinder", Category.WORLD); }

	@Override
	protected ScanTarget createTarget() {
		ScanTarget t = new ScanTarget(this::wanted);
		return t;
	}

	private boolean wanted(BlockState s) {
		if (debris.get() && s.is(Blocks.ANCIENT_DEBRIS)) return true;
		if (diamond.get() && s.is(BlockTags.DIAMOND_ORES)) return true;
		if (emerald.get() && s.is(BlockTags.EMERALD_ORES)) return true;
		if (gold.get() && (s.is(BlockTags.GOLD_ORES) || s.is(Blocks.NETHER_GOLD_ORE))) return true;
		if (iron.get() && s.is(BlockTags.IRON_ORES)) return true;
		if (redstone.get() && s.is(BlockTags.REDSTONE_ORES)) return true;
		if (lapis.get() && s.is(BlockTags.LAPIS_ORES)) return true;
		if (coal.get() && s.is(BlockTags.COAL_ORES)) return true;
		if (copper.get() && s.is(BlockTags.COPPER_ORES)) return true;
		return quartz.get() && s.is(Blocks.NETHER_QUARTZ_ORE);
	}

	private int tickCount;
	private int lastMask = -1;

	private int mask() {
		int m = 0;
		boolean[] b = {diamond.get(), debris.get(), emerald.get(), gold.get(), iron.get(), redstone.get(), lapis.get(), coal.get(), copper.get(), quartz.get()};
		for (int i = 0; i < b.length; i++) if (b[i]) m |= 1 << i;
		return m;
	}

	@Override
	protected void onTick() {
		super.onTick();
		if (target == null || ++tickCount % 10 != 0) return;
		int m = mask();
		if (m != lastMask) {
			lastMask = m;
			target.setPredicate(this::wanted); // re-scan with the new selection
		}
	}

	@Override
	protected int colorOf(BlockState s) {
		if (s.is(Blocks.ANCIENT_DEBRIS)) return 0xFFB5651D;
		if (s.is(BlockTags.DIAMOND_ORES)) return 0xFF33EEFF;
		if (s.is(BlockTags.EMERALD_ORES)) return 0xFF22DD55;
		if (s.is(BlockTags.GOLD_ORES) || s.is(Blocks.NETHER_GOLD_ORE)) return 0xFFFFD700;
		if (s.is(BlockTags.IRON_ORES)) return 0xFFD8AF93;
		if (s.is(BlockTags.REDSTONE_ORES)) return 0xFFFF2222;
		if (s.is(BlockTags.LAPIS_ORES)) return 0xFF2244FF;
		if (s.is(BlockTags.COAL_ORES)) return 0xFF444444;
		if (s.is(BlockTags.COPPER_ORES)) return 0xFFE07B53;
		return 0xFFEEEEEE;
	}
}
