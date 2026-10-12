package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.humanize.RotationController;
import breezeclient.breezeclient.client.input.Keys;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Raises a shield when end crystals are about to go off near you. When a crystal is close (or another player close by
 * is holding crystals or a respawn anchor) it gets a shield from the hotbar into the offhand with the swap-hands key,
 * turns to face the danger, because a shield only stops what comes from in front, and holds it up until the danger has
 * passed; then it puts back what was in the offhand. A totem in the offhand is left where it is by default: the shield
 * is held in the main hand instead.
 */
public class AutoShield extends Module {
	private final NumberSetting radius = add(new NumberSetting("Crystal range", 8, 3, 16), "Trigger", "A crystal within this many blocks raises the shield.");
	private final BoolSetting watchPlayers = add(new BoolSetting("Players holding crystals", true), "Trigger",
		"Also raise it when another player in range holds end crystals or a respawn anchor, before the first crystal is down. Friends are ignored.");
	private final BoolSetting notWhileCrystalling = add(new BoolSetting("Not while I hold crystals", true), "Trigger",
		"Stay out of the way while you are the one placing crystals (or holding an anchor or glowstone).");
	private final NumberSetting calmTicks = add(new NumberSetting("Lower after (ticks)", 15, 2, 100), "Trigger", "How long the danger has to be gone before the shield comes down.");
	private final BoolSetting face = add(new BoolSetting("Face the danger", true), "Shield", "Turn toward the nearest crystal. A shield does nothing against a blast from behind.");
	private final BoolSetting replaceTotem = add(new BoolSetting("Replace an offhand totem", false), "Shield",
		"Off: with a totem in the offhand the shield is held in the main hand, so the totem stays. On: the totem is swapped out for the shield.");
	private final BoolSetting restore = add(new BoolSetting("Put things back afterwards", true), "Shield", "Return the offhand item and your hotbar slot once the shield comes down.");
	private final NumberSetting delay = add(new NumberSetting("Reaction (ticks)", 1, 0, 10), "Timing", "Average pause before it reacts to a new danger.");

	private enum Swap { NONE, SELECTED, SWAPPED }

	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private int calm;
	private int react = -1;
	private boolean holding;
	private InteractionHand hand;
	/** Hotbar slot the offhand's old item went to when the shield was swapped in (-1 = offhand untouched). */
	private int swappedSlot = -1;
	/** Slot to go back to after holding the shield in the main hand (-1 = main hand untouched). */
	private int backSlot = -1;
	private Swap swap = Swap.NONE;
	private int swapWait;
	private int swapBack = -1;
	private boolean lowering;
	private int raised;

	public AutoShield() { super("AutoShield", Category.COMBAT); }

	@Override
	public String info() { return raised == 0 ? "" : String.valueOf(raised); }

	@Override
	protected void onEnable() {
		calm = 0;
		react = -1;
		holding = lowering = false;
		swappedSlot = backSlot = swapBack = -1;
		swap = Swap.NONE;
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		stopBlocking(mc);
		if (mc.player != null && backSlot >= 0 && restore.get()) mc.player.getInventory().setSelectedSlot(backSlot);
		backSlot = -1;
		swap = Swap.NONE;
	}

	private static boolean isShield(ItemStack s) { return s.is(Items.SHIELD); }

	private static boolean crystalGear(ItemStack s) { return s.is(Items.END_CRYSTAL) || s.is(Items.RESPAWN_ANCHOR); }

	/** Where the nearest danger is, or null when there is none. */
	private Vec3 danger(Minecraft mc, LocalPlayer p) {
		double r = radius.get();
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (EndCrystal c : mc.level.getEntitiesOfClass(EndCrystal.class, p.getBoundingBox().inflate(r))) {
			double d = c.distanceToSqr(p);
			if (d <= r * r && d < bestD) {
				bestD = d;
				best = c.position().add(0, 0.5, 0);
			}
		}
		if (best != null || !watchPlayers.get()) return best;
		for (Player o : mc.level.players()) {
			if (o == p || o.isSpectator() || Friends.is(o.getName().getString())) continue;
			double d = o.distanceToSqr(p);
			if (d > r * r || d >= bestD) continue;
			if (crystalGear(o.getMainHandItem()) || crystalGear(o.getOffhandItem())) {
				bestD = d;
				best = o.getEyePosition();
			}
		}
		return best;
	}

	private void stopBlocking(Minecraft mc) {
		if (!holding) return;
		holding = false;
		Keys.release(mc.options.keyUse);
		LocalPlayer p = mc.player;
		if (p != null && mc.gameMode != null && p.isUsingItem() && isShield(p.getUseItem()) && !mc.options.keyUse.isDown()) mc.gameMode.releaseUsingItem(p);
	}

	/** Starts swapping the item in this hotbar slot with the offhand, the way the swap-hands key does. */
	private void beginSwap(LocalPlayer p, int slot) {
		swapBack = p.getInventory().getSelectedSlot();
		if (swapBack != slot) p.getInventory().setSelectedSlot(slot);
		swapWait = swapBack == slot ? 0 : 2; // the server has to learn of the new slot before the swap
		swap = Swap.SELECTED;
	}

	/** @return true while a swap is still going on */
	private boolean tickSwap(Minecraft mc, LocalPlayer p) {
		if (swap == Swap.NONE) return false;
		if (swapWait-- > 0) return true;
		if (swap == Swap.SELECTED) {
			mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
			swap = Swap.SWAPPED;
			swapWait = 1;
			return true;
		}
		if (swapBack >= 0 && swapBack != p.getInventory().getSelectedSlot()) p.getInventory().setSelectedSlot(swapBack);
		swapBack = -1;
		swap = Swap.NONE;
		return false;
	}

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) return;
		if (tickSwap(mc, p)) return;
		if (mc.screen != null) {
			stopBlocking(mc);
			return;
		}
		Vec3 threat = danger(mc, p);
		if (threat != null && notWhileCrystalling.get() && (crystalGear(p.getMainHandItem()) || p.getMainHandItem().is(Items.GLOWSTONE))) threat = null;
		if (threat == null) {
			react = -1;
			if (!holding && swappedSlot < 0 && backSlot < 0) return;
			if (++calm < calmTicks.asInt()) {
				keepUp(mc, p); // still up while we wait to be sure
				return;
			}
			lower(mc, p);
			return;
		}
		calm = 0;
		lowering = false;
		if (!holding) {
			if (react < 0) react = limiter.nextDelayTicks(delay.get(), delay.get() * 0.4, 0);
			if (react-- > 0) return;
			if (!ready(mc, p)) return;
		}
		if (face.get()) RotationController.aimAtPoint(threat, 18f);
		keepUp(mc, p);
	}

	/** Gets a shield into a hand. @return true once one is there */
	private boolean ready(Minecraft mc, LocalPlayer p) {
		if (isShield(p.getOffhandItem())) {
			hand = InteractionHand.OFF_HAND;
			return true;
		}
		int slot = CombatUtil.hotbarSlot(p, Items.SHIELD);
		if (slot < 0) return false;
		boolean totem = p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
		if (totem && !replaceTotem.get()) { // the totem stays: hold the shield itself
			if (p.getInventory().getSelectedSlot() != slot) {
				if (backSlot < 0) backSlot = p.getInventory().getSelectedSlot();
				p.getInventory().setSelectedSlot(slot);
				return false; // the next tick uses it, once the slot change is on its way
			}
			hand = InteractionHand.MAIN_HAND;
			return true;
		}
		swappedSlot = slot; // what was in the offhand ends up in the shield's old slot
		beginSwap(p, slot);
		return false;
	}

	/** Raises the shield, or keeps it raised. Never presses use while the shield is not what would be used. */
	private void keepUp(Minecraft mc, LocalPlayer p) {
		if (hand == null) return;
		ItemStack inHand = p.getItemInHand(hand);
		if (!isShield(inHand)) { // it broke, or you moved it
			stopBlocking(mc);
			return;
		}
		if (p.isUsingItem() && isShield(p.getUseItem())) {
			Keys.press(mc.options.keyUse); // the game lowers a used item the moment the key is up
			holding = true;
			return;
		}
		// not blocking (yet, or an axe knocked it down): the use key must be up, or it would use the other hand's item
		if (holding) Keys.release(mc.options.keyUse);
		holding = false;
		if (p.isUsingItem() || p.getCooldowns().isOnCooldown(inHand)) return;
		mc.gameMode.useItem(p, hand);
		if (p.isUsingItem() && isShield(p.getUseItem())) {
			Keys.press(mc.options.keyUse);
			holding = true;
			if (!lowering) raised++;
		}
	}

	private void lower(Minecraft mc, LocalPlayer p) {
		stopBlocking(mc);
		lowering = true;
		calm = 0;
		hand = null;
		if (!restore.get()) {
			swappedSlot = backSlot = -1;
			return;
		}
		if (backSlot >= 0) {
			p.getInventory().setSelectedSlot(backSlot);
			backSlot = -1;
		}
		if (swappedSlot >= 0) {
			int slot = swappedSlot;
			swappedSlot = -1;
			if (isShield(p.getOffhandItem())) beginSwap(p, slot); // the same swap again puts both back
		}
	}
}
