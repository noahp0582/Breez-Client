package breezeclient.breezeclient.client.modules;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Soft "thock" sounds for every key, mouse click and scroll tick. The sounds are synthesized here (no sound files) and
 * played through the Java sound system, so they are outside Minecraft's own sound engine and only you hear them.
 */
public class CreamyKeys extends Module {
	private static final float RATE = 44100f;
	private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);
	private static CreamyKeys instance;

	private final NumberSetting volume = add(new NumberSetting("Volume", 0.6, 0.05, 1), "General", "Loudness. Also scaled by the game's master volume.");
	private final NumberSetting pitch = add(new NumberSetting("Pitch", 1.0, 0.5, 2), "General", "Lower is deeper and creamier, higher is clackier.");
	private final NumberSetting variation = add(new NumberSetting("Variation", 0.12, 0, 0.5), "General", "Random pitch change per press so it never sounds mechanical.");
	private final BoolSetting keyboard = add(new BoolSetting("Keyboard", true), "Sources", "Sound for key presses.");
	private final BoolSetting release = add(new BoolSetting("Key release", true), "Sources", "A lighter sound when a key comes back up.");
	private final BoolSetting mouse = add(new BoolSetting("Mouse buttons", true), "Sources", "Sound for mouse clicks.");
	private final BoolSetting scroll = add(new BoolSetting("Scroll wheel", true), "Sources", "Sound for scroll ticks.");
	private final BoolSetting inGameOnly = add(new BoolSetting("Only while playing", false), "Sources", "Stay quiet in menus, chat and inventories.");

	private final Map<Integer, byte[]> cache = new ConcurrentHashMap<>();
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Breez-CreamyKeys");
		t.setDaemon(true);
		return t;
	});
	private final AtomicInteger active = new AtomicInteger();
	private final Random random = new Random();
	private volatile boolean broken;

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
		m.play(key, action == 0 ? 0.55 : 1.0, 0);
	}

	public static void onMouse(int action, int button) {
		CreamyKeys m = instance;
		if (m == null || !m.enabled() || !m.mouse.get() || !m.allowed()) return;
		if (action == 0 && !m.release.get()) return;
		m.play(1000 + Math.min(button, 4), action == 0 ? 0.5 : 1.0, 1);
	}

	public static void onScroll(double dy) {
		CreamyKeys m = instance;
		if (m == null || !m.enabled() || !m.scroll.get() || dy == 0 || !m.allowed()) return;
		m.play(2000 + (dy > 0 ? 1 : 0), 0.7, 2);
	}

	private boolean allowed() {
		return !broken && (!inGameOnly.get() || Minecraft.getInstance().screen == null);
	}

	// ---- synthesis ----

	/** kind 0 = key thock, 1 = mouse click, 2 = scroll tick. */
	private byte[] sound(int id, int kind) {
		return cache.computeIfAbsent(id, k -> {
			Random r = new Random(k * 7919L + 13);
			double base;
			double decay;
			double click;
			double len;
			switch (kind) {
				case 1 -> { base = 260 + r.nextInt(60); decay = 0.030; click = 0.55; len = 0.09; }
				case 2 -> { base = 520; decay = 0.012; click = 0.35; len = 0.05; }
				default -> {
					boolean wide = k == 32 || k == 257 || k == 259 || k == 340 || k == 344 || k == 258; // space, enter, backspace, shift, tab
					base = wide ? 120 + r.nextInt(25) : 150 + r.nextInt(90);
					decay = wide ? 0.075 : 0.050;
					click = wide ? 0.30 : 0.38;
					len = wide ? 0.20 : 0.15;
				}
			}
			return render(base, decay, click, len, r);
		});
	}

	private static byte[] render(double f0, double decay, double click, double seconds, Random r) {
		int n = (int) (RATE * seconds);
		byte[] out = new byte[n * 2];
		double phase = 0;
		double lp = 0;
		for (int i = 0; i < n; i++) {
			double t = i / RATE;
			double env = Math.exp(-t / decay);
			double freq = f0 * (1 + 0.7 * Math.exp(-t * 55)); // the pitch settles downward: the "thock"
			phase += 2 * Math.PI * freq / RATE;
			double body = Math.sin(phase) * env + 0.25 * Math.sin(phase * 2.01) * env * env;
			double noise = (r.nextDouble() * 2 - 1) * Math.exp(-t / 0.0035) * click;
			lp += 0.35 * (noise - lp); // soften the click so it stays creamy
			double fade = Math.min(1.0, (seconds - t) / 0.01);
			double s = (body * 0.75 + lp * 0.9) * fade;
			short v = (short) Math.max(-32767, Math.min(32767, Math.round(s * 30000)));
			out[2 * i] = (byte) (v & 0xFF);
			out[2 * i + 1] = (byte) ((v >> 8) & 0xFF);
		}
		return out;
	}

	/** Resamples (cheap pitch shift) and scales a sound; runs on the worker thread. */
	private static byte[] transform(byte[] src, double speed, double gain) {
		int n = src.length / 2;
		int m = Math.max(2, (int) (n / speed));
		byte[] out = new byte[m * 2];
		for (int i = 0; i < m; i++) {
			double pos = i * speed;
			int a = (int) pos;
			if (a >= n - 1) break;
			double frac = pos - a;
			short s0 = (short) ((src[2 * a] & 0xFF) | (src[2 * a + 1] << 8));
			short s1 = (short) ((src[2 * a + 2] & 0xFF) | (src[2 * a + 3] << 8));
			double s = (s0 + (s1 - s0) * frac) * gain;
			short v = (short) Math.max(-32767, Math.min(32767, Math.round(s)));
			out[2 * i] = (byte) (v & 0xFF);
			out[2 * i + 1] = (byte) ((v >> 8) & 0xFF);
		}
		return out;
	}

	private void play(int id, double loudness, int kind) {
		if (active.get() > 10) return;
		double master = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
		double gain = volume.get() * loudness * master;
		if (gain <= 0.001) return;
		double speed = pitch.get() * (1 + (random.nextDouble() * 2 - 1) * variation.get());
		byte[] base = sound(id, kind);
		active.incrementAndGet();
		worker.execute(() -> {
			try {
				byte[] data = transform(base, speed, gain);
				Clip clip = AudioSystem.getClip();
				clip.addLineListener(e -> {
					if (e.getType() == LineEvent.Type.STOP) {
						clip.close();
						active.decrementAndGet();
					}
				});
				clip.open(FORMAT, data, 0, data.length);
				clip.start();
			} catch (Throwable t) {
				active.decrementAndGet();
				broken = true; // no audio device or format: stop trying instead of throwing on every key
			}
		});
	}

	@Override
	protected void onEnable() { broken = false; }
}
