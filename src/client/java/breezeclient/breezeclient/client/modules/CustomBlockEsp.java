package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.world.BlockIds;
import breezeclient.breezeclient.client.world.ScanTarget;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

/** ESP for a user-defined list of block ids (edit with .blockesp add/remove/list). */
public class CustomBlockEsp extends EspModule {
	public final StringListSetting blocks = add(new StringListSetting("Blocks", List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore")));
	private final ColorSetting color = add(new ColorSetting("Color", 0xFF33DDFF));
	private List<String> applied = List.of();

	public CustomBlockEsp() { super("BlockESP", Category.RENDER); }

	@Override
	protected ScanTarget createTarget() {
		applied = List.copyOf(blocks.get());
		return ScanTarget.ofBlocks(resolve());
	}

	private Set<Block> resolve() { return BlockIds.resolve(blocks.get()); }

	@Override
	protected void onTick() {
		super.onTick();
		if (target != null && !applied.equals(blocks.get())) {
			applied = List.copyOf(blocks.get());
			Set<Block> set = resolve();
			target.setPredicate(s -> set.contains(s.getBlock()));
		}
	}

	@Override
	protected int colorOf(BlockState s) { return color.get(); }
}
