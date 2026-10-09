package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

import java.util.Random;

/** Keeps a totem of undying in the offhand, refilling it a short random moment after one pops. */
public class TotemMacro extends Module {
	private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 4, 1, 20));

	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private int wait = -1;

	public TotemMacro() { super("TotemMacro", Category.COMBAT); }

	@Override
	protected void onEnable() { wait = -1; }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.gameMode == null) return;
		if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			wait = -1;
			return;
		}
		int slot = findTotem(p);
		if (slot < 0) return;
		if (wait < 0) {
			wait = limiter.nextDelayTicks(delay.get(), delay.get() * 0.3, 1);
			return;
		}
		if (--wait > 0) return;
		wait = -1;
		// Inventory menu slot numbering: 9-35 main inventory, 36-44 hotbar, 45 offhand. Button 40 = swap with offhand.
		mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, slot, 40, ClickType.SWAP, p);
	}

	private int findTotem(net.minecraft.client.player.LocalPlayer p) {
		for (int i = 0; i < 36; i++) {
			if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return i < 9 ? 36 + i : i;
		}
		return -1;
	}
}
