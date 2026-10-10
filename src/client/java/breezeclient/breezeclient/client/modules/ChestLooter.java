package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import breezeclient.breezeclient.core.text.IdPattern;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Random;

/**
 * Moves items out of a chest, barrel, shulker box, hopper or dispenser you opened by hand, with ordinary shift-clicks at a
 * randomized pace. It refuses menus that were not opened from a real container block: server shops and menus are chest
 * screens too, and shift-clicking in them can buy things.
 */
public class ChestLooter extends Module {
	public enum Mode { LISTED, ALL }

	private static long lastUseTick = Long.MIN_VALUE;
	private static BlockPos lastUsePos;
	private static long worldTick;

	private final EnumSetting<Mode> mode = add(new EnumSetting<>("Take", Mode.LISTED), "General", "LISTED takes only the items below; ALL takes everything.");
	private final StringListSetting items = add(new StringListSetting("Items", List.of("minecraft:spawner", "minecraft:*_shulker_box",
		"minecraft:shulker_box", "minecraft:netherite_ingot", "minecraft:netherite_scrap", "minecraft:netherite_block", "minecraft:ancient_debris",
		"minecraft:elytra", "minecraft:enchanted_golden_apple", "minecraft:totem_of_undying", "minecraft:diamond_block", "minecraft:beacon"))
		.kind(StringListSetting.Kind.ITEM), "General", "Item ids to take in LISTED mode. A * matches any run of characters.", () -> mode.get() == Mode.LISTED);
	private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 4, 1, 20), "Humanization", "Average wait between clicks.");
	private final BoolSetting closeWhenDone = add(new BoolSetting("Close when done", false), "General", "Close the container once nothing more can be taken.");

	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private int wait;
	private Object lastScreen;
	private long openedAt;

	public ChestLooter() { super("ChestLooter", Category.MISC); }

	public static void registerEvents() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide()) {
				lastUseTick = worldTick;
				lastUsePos = hit.getBlockPos();
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean realContainerMenu(AbstractContainerMenu m) {
		return m instanceof ChestMenu || m instanceof ShulkerBoxMenu || m instanceof HopperMenu || m instanceof DispenserMenu;
	}

	/** The menu counts only if you right-clicked a container block just before it opened. */
	private boolean openedFromBlock(Minecraft mc) {
		if (lastUsePos == null || mc.level == null) return false;
		if (openedAt - lastUseTick > 40 || openedAt < lastUseTick) return false;
		return mc.level.getBlockEntity(lastUsePos) instanceof Container;
	}

	@Override
	protected void onTick() {
		worldTick++;
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.gameMode == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)) {
			lastScreen = null;
			return;
		}
		if (screen != lastScreen) {
			lastScreen = screen;
			openedAt = worldTick;
			wait = limiter.nextDelayTicks(delay.get() + 2, 1, 2);
		}
		AbstractContainerMenu menu = screen.getMenu();
		if (!realContainerMenu(menu) || !openedFromBlock(mc)) return;
		if (wait-- > 0) return;
		if (p.getInventory().getFreeSlot() == -1) return;
		for (Slot s : menu.slots) {
			if (s.container == p.getInventory() || !s.hasItem()) continue;
			ItemStack st = s.getItem();
			if (mode.get() == Mode.LISTED && !IdPattern.matchesAny(items.get(), BuiltInRegistries.ITEM.getKey(st.getItem()).toString())) continue;
			mc.gameMode.handleInventoryMouseClick(menu.containerId, s.index, 0, ClickType.QUICK_MOVE, p);
			wait = limiter.nextDelayTicks(delay.get(), delay.get() * 0.35, 1);
			return;
		}
		if (closeWhenDone.get()) p.closeContainer();
	}
}
