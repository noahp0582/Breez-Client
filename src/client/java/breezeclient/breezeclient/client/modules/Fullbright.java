package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Local-only night vision. Nothing is sent to the server. */
public class Fullbright extends Module {
	public Fullbright() { super("Fullbright", Category.RENDER); }

	@Override
	protected void onTick() {
		var p = Minecraft.getInstance().player;
		if (p == null) return;
		MobEffectInstance cur = p.getEffect(MobEffects.NIGHT_VISION);
		if (cur == null || !cur.isInfiniteDuration()) {
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
		}
	}

	@Override
	protected void onDisable() {
		var p = Minecraft.getInstance().player;
		if (p == null) return;
		MobEffectInstance cur = p.getEffect(MobEffects.NIGHT_VISION);
		if (cur != null && cur.isInfiniteDuration()) p.removeEffect(MobEffects.NIGHT_VISION);
	}
}
