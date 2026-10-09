package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

/** Bursts of particles when you hit something. Purely visual and local. */
public class HitParticles extends Module {
	public enum Style { CRIT, HEARTS, MAGIC, FLAME, NOTES, SMOKE }

	private static HitParticles instance;
	private final EnumSetting<Style> style = add(new EnumSetting<>("Style", Style.CRIT), "General", "Which particle to burst.");
	private final NumberSetting amount = add(new NumberSetting("Amount", 12, 1, 60), "General", "Particles per hit.");

	private final Random rng = new Random();

	public HitParticles() {
		super("HitParticles", Category.RENDER);
		instance = this;
	}

	public static void registerEvents() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (instance != null && instance.enabled() && level.isClientSide() && entity instanceof LivingEntity) instance.burst(entity);
			return InteractionResult.PASS;
		});
	}

	private ParticleOptions type() {
		return switch (style.get()) {
			case HEARTS -> ParticleTypes.HEART;
			case MAGIC -> ParticleTypes.ENCHANT;
			case FLAME -> ParticleTypes.FLAME;
			case NOTES -> ParticleTypes.NOTE;
			case SMOKE -> ParticleTypes.LARGE_SMOKE;
			default -> ParticleTypes.CRIT;
		};
	}

	private void burst(net.minecraft.world.entity.Entity e) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		for (int i = 0; i < amount.asInt(); i++) {
			level.addParticle(type(), e.getX() + (rng.nextDouble() - 0.5) * e.getBbWidth(), e.getY() + rng.nextDouble() * e.getBbHeight(), e.getZ() + (rng.nextDouble() - 0.5) * e.getBbWidth(),
				(rng.nextDouble() - 0.5) * 0.3, rng.nextDouble() * 0.25, (rng.nextDouble() - 0.5) * 0.3);
		}
	}
}
