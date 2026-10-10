package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.humanize.ActionLimiter;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

/**
 * Places and breaks end crystals at whatever the crosshair is on, with randomized timing.
 * The player still does the aiming; vanilla reach and line of sight apply.
 */
public class CrystalMacro extends Module {
	private final NumberSetting placeDelay = add(new NumberSetting("Place delay (ticks)", 3, 1, 20));
	private final NumberSetting breakDelay = add(new NumberSetting("Break delay (ticks)", 2, 1, 20));
	private final BoolSetting onlyWhileUse = add(new BoolSetting("Only while holding use", true));
	private final BoolSetting place = add(new BoolSetting("Place", true));
	private final BoolSetting breakCrystals = add(new BoolSetting("Break", true));
	private final BoolSetting layBase = add(new BoolSetting("Lay obsidian base", true), "Base",
		"When you aim at the top of a block that is not obsidian or bedrock, place obsidian on it first (from your hotbar), then the crystal.");
	private final BoolSetting restoreSlot = add(new BoolSetting("Return to crystals", true), "Base",
		"After placing the base, switch back to the end crystals in your hotbar.");

	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private int cooldown;
	/** True while the macro itself moved your selected slot to obsidian and still owes you the switch back. */
	private boolean onObsidian;

	public CrystalMacro() { super("CrystalMacro", Category.COMBAT); }

	@Override
	protected void onDisable() { onObsidian = false; }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		var level = mc.level;
		if (p == null || level == null || mc.gameMode == null || mc.screen != null) return;
		if (onlyWhileUse.get() && !mc.options.keyUse.isDown()) {
			onObsidian = false; // a slot switch the macro owes you does not outlive the use key
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		HitResult hr = mc.hitResult;
		if (hr == null) return;

		if (breakCrystals.get() && hr instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystal crystal) {
			if (!CombatUtil.attack("CrystalMacro", crystal)) return;
			cooldown = limiter.nextDelayTicks(breakDelay.get(), breakDelay.get() * 0.3, 1);
			return;
		}

		if (!place.get() || !(hr instanceof BlockHitResult bh) || bh.getDirection() != Direction.UP) return;
		BlockPos base = bh.getBlockPos();
		var state = level.getBlockState(base);
		BlockPos above = base.above();
		if (!level.isEmptyBlock(above) || !level.isEmptyBlock(above.above())) return;
		boolean solidBase = state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);

		if (!solidBase) {
			if (!layBase.get() || state.isAir() || !state.isSolidRender()) return;
			// only crystal play lays a base: with no crystals around this is just someone building with obsidian
			if (CombatUtil.hotbarSlot(p, Items.END_CRYSTAL) < 0 && !p.getMainHandItem().is(Items.END_CRYSTAL)) return;
			int obsidian = CombatUtil.hotbarSlot(p, Items.OBSIDIAN);
			if (obsidian < 0) return;
			if (p.getInventory().getSelectedSlot() != obsidian) {
				p.getInventory().setSelectedSlot(obsidian);
				onObsidian = true;
				cooldown = limiter.nextDelayTicks(placeDelay.get(), placeDelay.get() * 0.3, 1);
				return;
			}
			if (!level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(above)).isEmpty()) return;
			mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bh);
			p.swing(InteractionHand.MAIN_HAND);
			cooldown = limiter.nextDelayTicks(placeDelay.get(), placeDelay.get() * 0.3, 1);
			return;
		}

		if (!p.getMainHandItem().is(Items.END_CRYSTAL)) {
			// only the macro's own slot change is undone; whatever you chose yourself is left alone
			int crystals = CombatUtil.hotbarSlot(p, Items.END_CRYSTAL);
			if (onObsidian && restoreSlot.get() && crystals >= 0) {
				p.getInventory().setSelectedSlot(crystals);
				onObsidian = false;
				cooldown = limiter.nextDelayTicks(placeDelay.get(), placeDelay.get() * 0.3, 1);
			}
			return;
		}
		onObsidian = false;
		if (!level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(above).expandTowards(0, 1, 0)).isEmpty()) return;
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bh);
		p.swing(InteractionHand.MAIN_HAND);
		cooldown = limiter.nextDelayTicks(placeDelay.get(), placeDelay.get() * 0.3, 1);
	}
}
