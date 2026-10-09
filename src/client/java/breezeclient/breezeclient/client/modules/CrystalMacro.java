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

	private final ActionLimiter limiter = new ActionLimiter(new Random());
	private int cooldown;

	public CrystalMacro() { super("CrystalMacro", Category.COMBAT); }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		var level = mc.level;
		if (p == null || level == null || mc.gameMode == null || mc.screen != null) return;
		if (onlyWhileUse.get() && !mc.options.keyUse.isDown()) return;
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		HitResult hr = mc.hitResult;
		if (hr == null) return;

		if (breakCrystals.get() && hr instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystal crystal) {
			mc.gameMode.attack(p, crystal);
			p.swing(InteractionHand.MAIN_HAND);
			cooldown = limiter.nextDelayTicks(breakDelay.get(), breakDelay.get() * 0.3, 1);
			return;
		}

		if (place.get() && hr instanceof BlockHitResult bh && p.getMainHandItem().is(Items.END_CRYSTAL)
			&& bh.getDirection() == Direction.UP) {
			BlockPos base = bh.getBlockPos();
			var state = level.getBlockState(base);
			if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) return;
			BlockPos above = base.above();
			if (!level.isEmptyBlock(above) || !level.isEmptyBlock(above.above())) return;
			if (!level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(above).expandTowards(0, 1, 0)).isEmpty()) return;
			mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bh);
			p.swing(InteractionHand.MAIN_HAND);
			cooldown = limiter.nextDelayTicks(placeDelay.get(), placeDelay.get() * 0.3, 1);
		}
	}
}
