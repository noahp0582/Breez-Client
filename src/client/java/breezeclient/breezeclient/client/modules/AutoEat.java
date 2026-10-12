package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.client.input.Keys;
import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Switches to food in the hotbar when hungry, eats, then restores the previous slot. */
public class AutoEat extends Module {
	private final NumberSetting hunger = add(new NumberSetting("Eat below", 14, 1, 19));
	private final StringListSetting avoid = add(new StringListSetting("Avoid", List.of(
		"minecraft:enchanted_golden_apple", "minecraft:golden_apple", "minecraft:rotten_flesh", "minecraft:poisonous_potato",
		"minecraft:spider_eye", "minecraft:pufferfish", "minecraft:chorus_fruit", "minecraft:suspicious_stew")).kind(StringListSetting.Kind.ITEM));

	private int savedSlot = -1;
	private int ticksEating;
	private int idleTicks;

	public AutoEat() { super("AutoEat", Category.MOVEMENT); }

	@Override
	protected void onDisable() { stopEating(Minecraft.getInstance()); }

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.screen != null) return;
		if (savedSlot >= 0) {
			ticksEating++;
			idleTicks = p.isUsingItem() ? 0 : idleTicks + 1;
			boolean full = p.getFoodData().getFoodLevel() >= 20;
			if (full || idleTicks > 5 || ticksEating > 100) stopEating(mc);
			return;
		}
		if (p.getFoodData().getFoodLevel() > hunger.get() || p.isUsingItem()) return;
		int slot = findFood(p);
		if (slot < 0) return;
		if (useWouldInteract(mc)) return; // holding use here would open the chest or trade with the villager instead of eating
		savedSlot = p.getInventory().getSelectedSlot();
		p.getInventory().setSelectedSlot(slot);
		Keys.press(mc.options.keyUse);
		ticksEating = 0;
		idleTicks = 0;
	}

	/** True when the crosshair is on something that right click acts on: an entity, or a block with a menu or data. */
	private static boolean useWouldInteract(Minecraft mc) {
		if (mc.hitResult == null || mc.level == null) return false;
		if (mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) return true;
		if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh && bh.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			var st = mc.level.getBlockState(bh.getBlockPos());
			return mc.level.getBlockEntity(bh.getBlockPos()) != null || st.getMenuProvider(mc.level, bh.getBlockPos()) != null;
		}
		return false;
	}

	private int findFood(LocalPlayer p) {
		for (int i = 0; i < 9; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.isEmpty() || !s.has(DataComponents.FOOD)) continue;
			String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
			if (!avoid.contains(id)) return i;
		}
		return -1;
	}

	private void stopEating(Minecraft mc) {
		if (savedSlot >= 0 || ticksEating > 0) Keys.release(mc.options.keyUse);
		if (savedSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(savedSlot);
		savedSlot = -1;
	}
}
