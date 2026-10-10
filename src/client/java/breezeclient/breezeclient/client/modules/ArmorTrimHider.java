package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

import java.util.List;

/** Hides or randomizes the armor trims you see on worn armor. Local rendering only; the items themselves are untouched. */
public class ArmorTrimHider extends Module {
	public enum Mode { HIDE, RANDOMIZE }

	private static ArmorTrimHider instance;
	private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.HIDE), "General", "HIDE removes trims from rendering, RANDOMIZE shows a different (stable) trim on every trimmed armor piece.");

	private RegistryAccess cachedFor;
	private List<Holder.Reference<TrimPattern>> patterns = List.of();
	private List<Holder.Reference<TrimMaterial>> materials = List.of();

	public ArmorTrimHider() {
		super("ArmorTrimHider", Category.MISC);
		instance = this;
	}

	/** Called from the equipment renderer mixin: returns the stack to draw instead of the real one. */
	public static ItemStack filter(ItemStack stack) {
		ArmorTrimHider m = instance;
		if (m == null || !m.enabled() || stack == null || stack.isEmpty()) return stack;
		ArmorTrim trim = stack.get(DataComponents.TRIM);
		if (trim == null) return stack;
		ItemStack copy = stack.copy();
		if (m.mode.get() == Mode.HIDE) {
			copy.remove(DataComponents.TRIM);
			return copy;
		}
		ArmorTrim random = m.randomTrim(trim);
		if (random == null) return stack;
		copy.set(DataComponents.TRIM, random);
		return copy;
	}

	private ArmorTrim randomTrim(ArmorTrim original) {
		var level = Minecraft.getInstance().level;
		if (level == null) return null;
		RegistryAccess access = level.registryAccess();
		if (access != cachedFor) {
			cachedFor = access;
			patterns = access.lookupOrThrow(Registries.TRIM_PATTERN).listElements().toList();
			materials = access.lookupOrThrow(Registries.TRIM_MATERIAL).listElements().toList();
		}
		if (patterns.isEmpty() || materials.isEmpty()) return null;
		// stable per original trim, so a piece does not flicker between frames
		int h = original.hashCode() * 0x9E3779B1;
		int pi = Math.floorMod(h >>> 3, patterns.size());
		int mi = Math.floorMod(h >>> 17, materials.size());
		return new ArmorTrim(materials.get(mi), patterns.get(pi));
	}
}
