package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Random;

/**
 * Bind to a key: swaps your worn chestplate and an elytra from the inventory using three ordinary inventory clicks with
 * short random pauses.
 */
public class ElytraSwap extends Module {
	private static final int CHEST_MENU_SLOT = 6;

	private final NumberSetting delay = add(new NumberSetting("Click delay (ticks)", 3, 1, 10), "Timing", "Average pause between the clicks.");
	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private int step;
	private int wait;
	private int source = -1;

	public ElytraSwap() { super("ElytraSwap", Category.COMBAT); }

	@Override
	protected boolean persistsEnabled() { return false; }

	private static boolean isChestplate(ItemStack s) { return s.is(net.minecraft.tags.ItemTags.CHEST_ARMOR); }

	@Override
	protected void onEnable() {
		var mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null) {
			setEnabled(false);
			return;
		}
		boolean wearingElytra = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.ELYTRA);
		source = -1;
		for (var s : p.inventoryMenu.slots) {
			if (s.container != p.getInventory() || s.getContainerSlot() >= 36) continue;
			if (wearingElytra ? isChestplate(s.getItem()) : s.getItem().is(Items.ELYTRA)) {
				source = s.index;
				break;
			}
		}
		if (source < 0) {
			Toasts.show("ElytraSwap", wearingElytra ? "No chestplate in your inventory" : "No elytra in your inventory", 0xFFFF5555);
			setEnabled(false);
			return;
		}
		step = 0;
		wait = limiter.nextDelayTicks(delay.get(), 1, 1);
	}

	@Override
	protected void onTick() {
		var mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.gameMode == null) {
			setEnabled(false);
			return;
		}
		if (wait-- > 0) return;
		int id = p.inventoryMenu.containerId;
		switch (step) {
			case 0 -> mc.gameMode.handleInventoryMouseClick(id, source, 0, ClickType.PICKUP, p);
			case 1 -> mc.gameMode.handleInventoryMouseClick(id, CHEST_MENU_SLOT, 0, ClickType.PICKUP, p);
			case 2 -> mc.gameMode.handleInventoryMouseClick(id, source, 0, ClickType.PICKUP, p);
			default -> {
				setEnabled(false);
				return;
			}
		}
		step++;
		wait = limiter.nextDelayTicks(delay.get(), 1, 1);
	}
}
