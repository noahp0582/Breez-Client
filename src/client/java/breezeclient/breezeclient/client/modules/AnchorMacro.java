package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Random;

/**
 * Charges the respawn anchor you look at with glowstone, then switches to another item and detonates it. With "Cycles" at 2
 * (DoubleAnchor) it charges and detonates twice in a row. Only works in dimensions where anchors explode (not the Nether).
 */
public class AnchorMacro extends Module {
	private enum Phase { WAIT, CHARGE, SWITCH, EXPLODE, GAP }

	private final boolean dbl;
	private final NumberSetting cycles;
	private final NumberSetting delay = add(new NumberSetting("Step delay (ticks)", 2, 0, 10), "Timing", "Average delay between steps.");
	private final BoolSetting onlyWhileUse = add(new BoolSetting("Only while holding use", true), "Trigger", "Run while you hold right click on an anchor.");
	private final BoolSetting restore = add(new BoolSetting("Restore slot afterwards", true), "Trigger", "Go back to the slot you started in.");

	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private Phase phase = Phase.WAIT;
	private int timer;
	private int done;
	private int startSlot = -1;

	public AnchorMacro() { this(false); }

	protected AnchorMacro(boolean dbl) {
		super(dbl ? "DoubleAnchor" : "AnchorMacro", Category.COMBAT);
		this.dbl = dbl;
		this.cycles = add(new NumberSetting("Cycles", dbl ? 2 : 1, 1, 3), "Timing", "Charge-and-detonate rounds per activation.");
	}

	@Override
	protected void onDisable() { phase = Phase.WAIT; }

	private void next(Phase p, int meanDelay) {
		phase = p;
		timer = limiter.nextDelayTicks(meanDelay, 1, 0);
	}

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null || mc.screen != null) return;
		if (timer > 0) {
			timer--;
			return;
		}
		BlockHitResult hit = mc.hitResult instanceof BlockHitResult bh ? bh : null;
		boolean onAnchor = hit != null && mc.level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR);
		if (phase == Phase.WAIT) {
			if (!onAnchor || (onlyWhileUse.get() && !mc.options.keyUse.isDown())) return;
			startSlot = p.getInventory().getSelectedSlot();
			done = 0;
			phase = Phase.CHARGE;
		}
		if (!onAnchor) {
			phase = Phase.WAIT;
			return;
		}
		int charges = mc.level.getBlockState(hit.getBlockPos()).getValue(RespawnAnchorBlock.CHARGE);
		switch (phase) {
			case CHARGE -> {
				int glow = CombatUtil.hotbarSlot(p, Items.GLOWSTONE);
				if (glow < 0) {
					phase = Phase.WAIT;
					return;
				}
				if (charges >= 1) {
					next(Phase.SWITCH, (int) delay.get().doubleValue());
					return;
				}
				p.getInventory().setSelectedSlot(glow);
				mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
				p.swing(InteractionHand.MAIN_HAND);
				next(Phase.SWITCH, (int) delay.get().doubleValue());
			}
			case SWITCH -> {
				int other = CombatUtil.hotbarSlot(p, s -> !s.isEmpty() && !s.is(Items.GLOWSTONE));
				if (other < 0) other = startSlot >= 0 ? startSlot : 0;
				p.getInventory().setSelectedSlot(other);
				next(Phase.EXPLODE, (int) delay.get().doubleValue());
			}
			case EXPLODE -> {
				mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
				p.swing(InteractionHand.MAIN_HAND);
				done++;
				if (done >= cycles.asInt()) {
					if (restore.get() && startSlot >= 0) p.getInventory().setSelectedSlot(startSlot);
					phase = Phase.WAIT;
					timer = 10; // do not restart on the same press
				} else {
					next(Phase.GAP, (int) delay.get().doubleValue() + 1);
				}
			}
			case GAP -> phase = Phase.CHARGE;
			default -> { }
		}
		if (dbl && phase == Phase.WAIT) startSlot = -1;
	}
}
