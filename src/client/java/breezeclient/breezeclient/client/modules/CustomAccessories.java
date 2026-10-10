package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.gui.ColorMath;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Cosmetics for yourself: a cape, a particle trail, an orbiting aura and a crown. Everything is drawn by your own client,
 * so only you see it (it is visible in third person and in freecam).
 */
public class CustomAccessories extends Module {
	public enum CapeStyle { GRADIENT, STRIPES, CHECKER, BORDERED }

	private static final Identifier CAPE_ID = Identifier.fromNamespaceAndPath("breez-client", "accessory_cape");
	private static CustomAccessories instance;

	private final BoolSetting cape = add(new BoolSetting("Cape", true), "Cape", "Wear a custom cape (also used for your elytra).");
	private final EnumSetting<CapeStyle> capeStyle = add(new EnumSetting<>("Cape style", CapeStyle.GRADIENT), "Cape", "Pattern of the cape.", cape::get);
	private final ColorSetting capeColor1 = add(new ColorSetting("Cape color 1", 0xFF913DE2), "Cape", "Main color.", cape::get);
	private final ColorSetting capeColor2 = add(new ColorSetting("Cape color 2", 0xFF1E1B3A), "Cape", "Second color.", cape::get);
	private final BoolSetting trail = add(new BoolSetting("Trail", false), "Trail", "Leave a glowing particle trail while you move.");
	private final ColorSetting trailColor = add(new ColorSetting("Trail color", 0xFF55FFFF), "Trail", "Trail color.", trail::get);
	private final BoolSetting aura = add(new BoolSetting("Aura", false), "Aura", "Particles circling you.");
	private final ColorSetting auraColor = add(new ColorSetting("Aura color", 0xFFFF55FF), "Aura", "Aura color.", aura::get);
	private final BoolSetting crown = add(new BoolSetting("Crown", false), "Crown", "A ring of golden sparks above your head.");
	private final ColorSetting crownColor = add(new ColorSetting("Crown color", 0xFFFFD21F), "Crown", "Crown color.", crown::get);
	private final BoolSetting rainbow = add(new BoolSetting("Rainbow", false), "General", "Cycle trail, aura and crown through the color wheel.");
	private final NumberSetting density = add(new NumberSetting("Density", 1.0, 0.25, 3), "General", "How many particles are spawned.");
	private final BoolSetting firstPerson = add(new BoolSetting("Show in first person", false), "General", "Also draw particles while you look through your own eyes.");

	private int lastCapeHash;
	private DynamicTexture capeTexture;
	private int tick;

	public CustomAccessories() {
		super("CustomAccessories", Category.RENDER);
		instance = this;
	}

	/** Called by the skin mixin for the local player. */
	public static PlayerSkin skinFor(PlayerSkin skin) {
		CustomAccessories m = instance;
		if (m == null || !m.enabled() || !m.cape.get() || m.capeTexture == null) return skin;
		ClientAsset.ResourceTexture asset = new ClientAsset.ResourceTexture(CAPE_ID, CAPE_ID);
		return new PlayerSkin(skin.body(), asset, asset, skin.model(), skin.secure());
	}

	private int capeHash() {
		return capeStyle.get().ordinal() * 31 + capeColor1.get() * 17 + capeColor2.get();
	}

	private void buildCape() {
		NativeImage img = new NativeImage(64, 32, true);
		int c1 = capeColor1.get() | 0xFF000000, c2 = capeColor2.get() | 0xFF000000;
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				double t = Math.min(1.0, y / 17.0);
				int c = switch (capeStyle.get()) {
					case GRADIENT -> ColorMath.mix(c1, c2, t);
					case STRIPES -> ((x / 2) % 2 == 0) ? c1 : c2;
					case CHECKER -> (((x / 2) + (y / 2)) % 2 == 0) ? c1 : c2;
					case BORDERED -> {
						int fx = x % 11; // inside each 10-wide face
						boolean edge = fx == 0 || fx == 10 || y == 0 || y >= 16;
						yield edge ? c2 : ColorMath.mix(c1, c2, t * 0.5);
					}
				};
				img.setPixel(x, y, c | 0xFF000000);
			}
		}
		var tm = Minecraft.getInstance().getTextureManager();
		DynamicTexture fresh = new DynamicTexture(() -> "breez cape", img);
		tm.register(CAPE_ID, fresh);
		capeTexture = fresh;
		lastCapeHash = capeHash();
	}

	private int color(int configured, double offset) {
		if (!rainbow.get()) return configured & 0xFFFFFF;
		double hue = ((System.currentTimeMillis() / 40.0) + offset * 60) % 360;
		return ColorMath.fromHsv(hue, 0.8, 1.0, 255) & 0xFFFFFF;
	}

	private void dust(net.minecraft.client.multiplayer.ClientLevel level, int rgb, float size, double x, double y, double z) {
		level.addParticle(new DustParticleOptions(rgb, size), x, y, z, 0, 0, 0);
	}

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		if (cape.get() && (capeTexture == null || lastCapeHash != capeHash())) buildCape();
		tick++;
		boolean thirdPerson = !mc.options.getCameraType().isFirstPerson() || breezeclient.breezeclient.client.modules.Freecam.active();
		if (!thirdPerson && !firstPerson.get()) return;
		var p = mc.player;
		var level = mc.level;
		double d = density.get();

		if (trail.get() && p.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
			int n = Math.max(1, (int) Math.round(d));
			for (int i = 0; i < n; i++) {
				dust(level, color(trailColor.get(), tick * 0.1), 1.0f, p.getX() + (Math.random() - 0.5) * 0.4, p.getY() + 0.1 + Math.random() * 0.2, p.getZ() + (Math.random() - 0.5) * 0.4);
			}
		}
		if (aura.get()) {
			int n = Math.max(1, (int) Math.round(3 * d));
			for (int i = 0; i < n; i++) {
				double a = tick * 0.25 + i * (2 * Math.PI / n);
				double y = p.getY() + 0.9 + Math.sin(tick * 0.1 + i) * 0.5;
				dust(level, color(auraColor.get(), i * 2 + tick * 0.1), 0.8f, p.getX() + Math.cos(a) * 0.8, y, p.getZ() + Math.sin(a) * 0.8);
			}
		}
		if (crown.get() && tick % 2 == 0) {
			int points = 10;
			double top = p.getY() + p.getBbHeight() + 0.12;
			for (int i = 0; i < points; i++) {
				double a = i * 2 * Math.PI / points;
				double spike = (i % 2 == 0) ? 0.22 : 0.05;
				dust(level, color(crownColor.get(), i), 0.7f, p.getX() + Math.cos(a) * 0.26, top + spike, p.getZ() + Math.sin(a) * 0.26);
			}
		}
	}

	@Override
	protected void onDisable() {
		if (capeTexture != null) {
			Minecraft.getInstance().getTextureManager().release(CAPE_ID);
			capeTexture = null;
		}
	}
}
