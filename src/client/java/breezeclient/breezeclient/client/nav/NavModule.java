package breezeclient.breezeclient.client.nav;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.nav.Pathfinder;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Settings holder and status for the built-in pathing ("Baritone" commands). Turning the module off stops whatever it is doing.
 * Commands: .goto .mine .follow .explore .tunnel .thisway .surface .stop .pause .resume
 */
public class NavModule extends Module {
	public static NavModule instance;

	public final BoolSetting allowBreak = add(new BoolSetting("Allow breaking blocks", true), "Movement", "Dig through blocks that are in the way (mining and tunnelling always may).");
	public final BoolSetting allowPlace = add(new BoolSetting("Allow placing blocks", true), "Movement", "Bridge gaps and pillar up with cheap blocks from your hotbar.");
	public final BoolSetting allowParkour = add(new BoolSetting("Allow sprint jumps", true), "Movement", "Jump over gaps of up to three blocks.");
	public final BoolSetting sprint = add(new BoolSetting("Sprint", true), "Movement", "Sprint along straight stretches.");
	public final NumberSetting maxFall = add(new NumberSetting("Max fall height", 3, 0, 10), "Movement", "Largest drop it will take on purpose. 3 or less is safe.");
	public final NumberSetting breakWeight = add(new NumberSetting("Break cost weight", 1.0, 0.1, 5.0), "Movement", "Higher = prefers walking around over digging through.");
	public final NumberSetting nodeBudget = add(new NumberSetting("Search budget (nodes)", 9000, 1000, 60000), "Performance", "How many positions one path search may look at. Longer trips are walked in segments.");
	public final BoolSetting avoidFlooding = add(new BoolSetting("Never open liquids", true), "Safety", "Do not break blocks that touch water or lava.");
	public final NumberSetting stuckSeconds = add(new NumberSetting("Stuck timeout (s)", 4, 1, 30), "Safety", "Replan when no progress is made for this long.");
	public final StringListSetting placeable = add(new StringListSetting("Placeable blocks", List.of("minecraft:cobblestone", "minecraft:dirt", "minecraft:netherrack", "minecraft:cobbled_deepslate",
		"minecraft:stone", "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:deepslate", "minecraft:blackstone", "minecraft:tuff")).kind(StringListSetting.Kind.BLOCK),
		"Safety", "Blocks it may use for bridges and pillars.");
	public final StringListSetting protectedBlocks = add(new StringListSetting("Never break", List.of("minecraft:spawner", "minecraft:ender_chest", "minecraft:chest", "minecraft:trapped_chest",
		"minecraft:barrel", "minecraft:beacon", "minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:respawn_anchor")).kind(StringListSetting.Kind.BLOCK),
		"Safety", "Blocks that are never broken, even when mining or tunnelling. (Any block with an inventory is also protected.)");

	private String status = "";

	public NavModule() {
		super("Baritone", Category.MOVEMENT);
		instance = this;
	}

	public void setStatus(String s) { status = s; }

	@Override
	public String info() { return status; }

	@Override
	protected void onDisable() { Baritone.stop("module turned off"); }

	public Pathfinder.Options options(boolean breaking, boolean placing, boolean parkour) {
		return new Pathfinder.Options(breaking, placing, parkour, maxFall.asInt(), breakWeight.get(), 2.0);
	}

	/** Hotbar slot with a block this module may place, or -1. */
	public int placeableSlot(LocalPlayer p) {
		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) continue;
			if (placeable.contains(BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString())) return i;
		}
		return -1;
	}

	public boolean hasPlaceable() {
		LocalPlayer p = Minecraft.getInstance().player;
		return p != null && placeableSlot(p) >= 0;
	}
}
