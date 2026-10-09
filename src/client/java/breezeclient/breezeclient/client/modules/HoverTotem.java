package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.mixin.AbstractContainerScreenAccessor;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

import java.util.Random;

/**
 * Inventory totem helper. HOVER: put a totem in your offhand when you hold the mouse over it. INVENTORY: as soon as your
 * inventory is open, move the first totem you own into the offhand.
 */
public class HoverTotem extends Module {
	public enum Mode { HOVER, INVENTORY }

	private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.HOVER), "General", "HOVER = the totem under the mouse; INVENTORY = any totem when the inventory opens.");
	private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 3, 0, 20), "Timing", "Average delay before the swap.");

	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private int wait = -1;

	public HoverTotem() { super("HoverTotem", Category.COMBAT); }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.gameMode == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)) {
			wait = -1;
			return;
		}
		if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
		var menu = p.containerMenu;
		int slot = -1;
		if (mode.get() == Mode.HOVER) {
			var hovered = ((AbstractContainerScreenAccessor) screen).breez$hoveredSlot();
			if (hovered != null && hovered.getItem().is(Items.TOTEM_OF_UNDYING)) slot = hovered.index;
		} else {
			for (var s : menu.slots) {
				if (s.container == p.getInventory() && s.getItem().is(Items.TOTEM_OF_UNDYING) && s.getContainerSlot() < 36) {
					slot = s.index;
					break;
				}
			}
		}
		if (slot < 0) {
			wait = -1;
			return;
		}
		if (wait < 0) wait = limiter.nextDelayTicks(delay.get(), 1, 0);
		if (wait-- > 0) return;
		wait = -1;
		mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 40, ClickType.SWAP, p);
	}
}
