package breezeclient.breezeclient.client.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Small helpers shared by the combat modules. */
final class CombatUtil {
	private CombatUtil() {}

	/** First hotbar slot whose stack matches, or -1. */
	static int hotbarSlot(LocalPlayer p, Predicate<ItemStack> match) {
		for (int i = 0; i < 9; i++) if (match.test(p.getInventory().getItem(i))) return i;
		return -1;
	}

	static int hotbarSlot(LocalPlayer p, Item item) { return hotbarSlot(p, s -> s.is(item)); }

	/**
	 * The only way modules attack. Refuses everything a server treats as an invalid attack (yourself, items, XP orbs,
	 * projectiles, entities the world no longer knows) because vanilla servers disconnect players who send those.
	 *
	 * @return true when the attack was sent
	 */
	static boolean attack(String who, Entity target) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null || target == null) return false;
		boolean valid = ((target instanceof LivingEntity le && le.isAlive()) || target instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) && !target.isRemoved() && target != p && target != mc.getCameraEntity()
			&& !(target instanceof net.minecraft.world.entity.player.Player pl && pl.getUUID().equals(p.getUUID()))
			&& mc.level.getEntity(target.getId()) == target;
		if (!valid) {
			java.util.logging.Logger.getLogger("breez-client").warning(who + " refused to attack " + target);
			return false;
		}
		mc.gameMode.attack(p, target);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		return true;
	}

	/** Angle in degrees between where the player looks and the direction to the point. */
	static double angleTo(LocalPlayer p, Vec3 point) {
		Vec3 look = p.getLookAngle().normalize();
		Vec3 to = point.subtract(p.getEyePosition()).normalize();
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, look.dot(to)))));
	}

	/** The chest-height aim point of an entity. */
	static Vec3 aimPoint(LivingEntity e) { return new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.65, e.getZ()); }
}
