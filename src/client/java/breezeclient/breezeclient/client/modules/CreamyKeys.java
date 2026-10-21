package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.EnumSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.Random;

/**
 * Recorded mechanical-switch sounds for every key, mouse click and scroll tick, using the sample packs from
 * CreamyKeys by cutebow (MIT, see LICENSE-CreamyKeys.txt). Each pack has 40
 * takes and Minecraft picks one at random per press. Played non-positionally on the client, so only you hear them.
 */
public class CreamyKeys extends Module {
	public enum Pack {
		BLACK_PBT("cherrymx_black_pbt"),
		BLACK_ABS("cherrymx_black_abs"),
		BROWN_PBT("cherrymx_brown_pbt"),
		BROWN_ABS("cherrymx_brown_abs"),
		RED_ABS("cherrymx_red_abs"),
		BLUE_ABS("cherrymx_blue_abs"),
		BLUE_ABS_2("cherrymx_blue_abs_2");

		final Identifier id;

		Pack(String folder) { this.id = Identifier.fromNamespaceAndPath("breez-client", "creamykeys." + folder); }
	}

	private static CreamyKeys instance;

	private final EnumSetting<Pack> keyPack = add(new EnumSetting<>("Keyboard switch", Pack.BLACK_PBT), "General", "Cherry MX switch and keycap recording used for key presses.");
	private final EnumSetting<Pack> mousePack = add(new EnumSetting<>("Mouse switch", Pack.BLUE_ABS_2), "General", "Recording used for mouse clicks and scroll ticks.");
	private final NumberSetting volume = add(new NumberSetting("Volume", 0.8, 0.05, 1), "General", "Loudness. Also scaled by the game's master volume.");
	private final NumberSetting amplify = add(new NumberSetting("Amplify", 1, 1, 4), "General", "Plays the sound this many times at once, for when full volume is still too quiet.");
	private final NumberSetting pitch = add(new NumberSetting("Pitch", 1.0, 0.5, 2), "General", "Lower is deeper and creamier, higher is clackier.");
	private final NumberSetting variation = add(new NumberSetting("Variation", 0.04, 0, 0.3), "General", "Random pitch change per press on top of the 40 recorded takes.");
	private final BoolSetting keyboard = add(new BoolSetting("Keyboard", true), "Sources", "Sound for key presses.");
	private final BoolSetting release = add(new BoolSetting("Key release", false), "Sources", "A quieter sound when a key or button comes back up.");
	private final BoolSetting mouse = add(new BoolSetting("Mouse buttons", true), "Sources", "Sound for mouse clicks.");
	private final BoolSetting scroll = add(new BoolSetting("Scroll wheel", true), "Sources", "Sound for scroll ticks.");
	private final BoolSetting inGameOnly = add(new BoolSetting("Only while playing", false), "Sources", "Stay quiet in menus, chat and inventories.");

	private final Random random = new Random();

	public CreamyKeys() {
		super("CreamyKeys", Category.MISC);
		instance = this;
	}

	// ---- input hooks (from mixins; run on the render thread) ----

	public static void onKey(int action, int key) {
		CreamyKeys m = instance;
		if (m == null || !m.enabled() || !m.keyboard.get() || action == 2 || key < 0) return;
		if (action == 0 && !m.release.get()) return;
		if (!m.allowed()) return;
		m.play(m.keyPack.get(), action == 0 ? 0.45 : 1.0);
	}

	public static void onMouse(int action, int button) {
		CreamyKeys m = instance;
		if (m == null || !m.enabled() || !m.mouse.get() || !m.allowed()) return;
		if (action == 0 && !m.release.get()) return;
		m.play(m.mousePack.get(), action == 0 ? 0.45 : 1.0);
	}

	public static void onScroll(double dy) {
		CreamyKeys m = instance;
		if (m == null || !m.enabled() || !m.scroll.get() || dy == 0 || !m.allowed()) return;
		m.play(m.mousePack.get(), 0.5);
	}

	private boolean allowed() {
		return !inGameOnly.get() || Minecraft.getInstance().screen == null;
	}

	private void play(Pack pack, double loudness) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getSoundManager() == null) return;
		float gain = (float) (volume.get() * loudness);
		if (gain <= 0.001f) return;
		float speed = (float) (pitch.get() * (1 + (random.nextDouble() * 2 - 1) * variation.get()));
		for (int i = 0; i < amplify.asInt(); i++) {
			mc.getSoundManager().play(new SimpleSoundInstance(pack.id, SoundSource.MASTER, gain, speed, RandomSource.create(),
					false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
		}
	}
}
