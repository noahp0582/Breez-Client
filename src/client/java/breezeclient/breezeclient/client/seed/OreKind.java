package breezeclient.breezeclient.client.seed;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum OreKind {
	DIAMOND("Diamond", 0xFF33EEFF, true),
	ANCIENT_DEBRIS("Ancient debris", 0xFFB5651D, true),
	EMERALD("Emerald", 0xFF22DD55, false),
	GOLD("Gold", 0xFFFFD700, false),
	IRON("Iron", 0xFFD8AF93, false),
	REDSTONE("Redstone", 0xFFFF2222, false),
	LAPIS("Lapis", 0xFF2244FF, false),
	COAL("Coal", 0xFF444444, false),
	COPPER("Copper", 0xFFE07B53, false),
	QUARTZ("Quartz", 0xFFEEEEEE, false);

	public final String label;
	public final int color;
	public final boolean defaultOn;

	OreKind(String label, int color, boolean defaultOn) {
		this.label = label;
		this.color = color;
		this.defaultOn = defaultOn;
	}

	public static OreKind of(BlockState s) {
		if (s.is(Blocks.ANCIENT_DEBRIS)) return ANCIENT_DEBRIS;
		if (s.is(BlockTags.DIAMOND_ORES)) return DIAMOND;
		if (s.is(BlockTags.EMERALD_ORES)) return EMERALD;
		if (s.is(BlockTags.GOLD_ORES) || s.is(Blocks.NETHER_GOLD_ORE)) return GOLD;
		if (s.is(BlockTags.IRON_ORES)) return IRON;
		if (s.is(BlockTags.REDSTONE_ORES)) return REDSTONE;
		if (s.is(BlockTags.LAPIS_ORES)) return LAPIS;
		if (s.is(BlockTags.COAL_ORES)) return COAL;
		if (s.is(BlockTags.COPPER_ORES)) return COPPER;
		if (s.is(Blocks.NETHER_QUARTZ_ORE)) return QUARTZ;
		return null;
	}
}
