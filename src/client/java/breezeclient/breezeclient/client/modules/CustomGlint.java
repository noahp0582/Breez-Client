package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.gui.ColorMath;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.ColorSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.io.InputStream;

/**
 * Recolors the enchantment glint by re-registering the glint textures with a tinted copy. Local only. The original textures
 * come back when the module is turned off or the resource packs reload.
 */
public class CustomGlint extends Module {
	private static final class Target {
		final Identifier id;
		int[] luma;
		int w, h;
		DynamicTexture texture;

		Target(String path) { this.id = Identifier.withDefaultNamespace(path); }
	}

	private final ColorSetting color = add(new ColorSetting("Color", 0xFFFF3D8B), "Look", "Tint of the glint.");
	private final BoolSetting rainbow = add(new BoolSetting("Rainbow", false), "Look", "Slowly cycle through the whole color wheel.");
	private final NumberSetting rainbowSpeed = add(new NumberSetting("Rainbow speed", 0.5, 0.1, 3), "Look", "Cycles per ten seconds.", rainbow::get);
	private final NumberSetting brightness = add(new NumberSetting("Brightness", 1.3, 0.4, 3), "Look", "Multiplier on the glint's brightness.");
	private final BoolSetting items = add(new BoolSetting("Items", true), "Where", "Recolor the glint on held and dropped items and in the inventory.");
	private final BoolSetting armor = add(new BoolSetting("Armor", true), "Where", "Recolor the glint on worn armor.");

	private final Target itemTex = new Target("textures/misc/enchanted_glint_item.png");
	private final Target armorTex = new Target("textures/misc/enchanted_glint_armor.png");
	private int lastColor = Integer.MIN_VALUE;
	private int tick;
	private int reloadCheck;
	private boolean lastItems, lastArmor;

	public CustomGlint() { super("CustomGlint", Category.MISC); }

	private static boolean load(Target t) {
		if (t.luma != null) return true;
		try {
			var res = Minecraft.getInstance().getResourceManager().getResource(t.id);
			if (res.isEmpty()) return false;
			try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
				t.w = img.getWidth();
				t.h = img.getHeight();
				t.luma = new int[t.w * t.h];
				for (int y = 0; y < t.h; y++) {
					for (int x = 0; x < t.w; x++) {
						int p = img.getPixel(x, y); // ARGB
						int r = ColorMath.red(p), g = ColorMath.green(p), b = ColorMath.blue(p);
						int a = ColorMath.alpha(p);
						// perceived brightness, keeping the original alpha in the top byte
						t.luma[y * t.w + x] = (a << 24) | Math.min(255, (int) (0.3 * r + 0.59 * g + 0.11 * b));
					}
				}
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void paint(Target t, int rgb) {
		if (!load(t)) return;
		// keep one texture per glint and rewrite its pixels, instead of allocating and registering a new one every repaint
		NativeImage img = t.texture != null ? t.texture.getPixels() : null;
		boolean reuse = img != null;
		if (!reuse) img = new NativeImage(t.w, t.h, false);
		double gain = brightness.get();
		int cr = ColorMath.red(rgb), cg = ColorMath.green(rgb), cb = ColorMath.blue(rgb);
		for (int y = 0; y < t.h; y++) {
			for (int x = 0; x < t.w; x++) {
				int v = t.luma[y * t.w + x];
				int a = v >>> 24;
				double l = (v & 0xFF) / 255.0 * gain;
				int r = (int) Math.min(255, cr * l), g = (int) Math.min(255, cg * l), b = (int) Math.min(255, cb * l);
				img.setPixel(x, y, ColorMath.argb(a, r, g, b));
			}
		}
		if (reuse) {
			t.texture.upload();
		} else {
			DynamicTexture fresh = new DynamicTexture(() -> "breez glint", img);
			Minecraft.getInstance().getTextureManager().register(t.id, fresh);
			t.texture = fresh;
		}
	}

	private void restore(Target t) {
		if (t.texture == null) return;
		Minecraft.getInstance().getTextureManager().release(t.id);
		t.texture = null;
	}

	private int currentColor() {
		if (!rainbow.get()) return color.get() & 0xFFFFFF;
		double hue = ((System.currentTimeMillis() / 10000.0) * rainbowSpeed.get()) % 1.0;
		return ColorMath.fromHsv(hue * 360.0, 0.85, 1.0, 255) & 0xFFFFFF;
	}

	@Override
	protected void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getTextureManager() == null) return;
		boolean wantItems = items.get(), wantArmor = armor.get();
		if (!wantItems) restore(itemTex);
		if (!wantArmor) restore(armorTex);
		int c = currentColor();
		// a resource reload replaces our copy; looking once a second is enough to notice
		boolean lost = false;
		if (++reloadCheck % 20 == 0 && itemTex.texture != null) {
			AbstractTexture seenItem = mc.getTextureManager().getTexture(itemTex.id);
			lost = seenItem != itemTex.texture;
			if (lost) {
				itemTex.texture = null;
				armorTex.texture = null;
			}
		}
		// repaint when the color changed (rainbow: at most every 3 ticks), settings flipped, or the textures were reloaded
		boolean due = c != lastColor && (!rainbow.get() || tick++ % 3 == 0);
		if (due || lost || wantItems != lastItems || wantArmor != lastArmor || (wantItems && itemTex.texture == null) || (wantArmor && armorTex.texture == null)) {
			if (wantItems) paint(itemTex, c);
			if (wantArmor) paint(armorTex, c);
			lastColor = c;
			lastItems = wantItems;
			lastArmor = wantArmor;
		}
	}

	@Override
	protected void onDisable() {
		restore(itemTex);
		restore(armorTex);
		lastColor = Integer.MIN_VALUE;
	}
}
