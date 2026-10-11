package breezeclient.breezeclient.client.dev;

import breezeclient.breezeclient.client.BreezClientClient;
import breezeclient.breezeclient.client.gui.ModuleScreen;
import breezeclient.breezeclient.client.gui.Tabs;
import breezeclient.breezeclient.client.gui.Toasts;
import breezeclient.breezeclient.core.Module;
import net.minecraft.client.Screenshot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Developer tour (BREEZ_SELFTEST=gui): joins a world, opens every GUI screen one after another and saves a screenshot of
 * each to run/screenshots, then quits. Used to check the look without a human at the keyboard.
 */
public final class GuiTour {
	private record Step(int tick, Runnable action) {}

	private static final List<Step> STEPS = new ArrayList<>();
	private static int ticks = -1;

	private GuiTour() {}

	public static boolean register() {
		if (!"gui".equalsIgnoreCase(System.getenv("BREEZ_SELFTEST"))) return false;
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> {
			ticks = 0;
			build(mc);
		});
		ClientTickEvents.END_CLIENT_TICK.register(GuiTour::tick);
		return true;
	}

	private static void shot(Minecraft mc) {
		Screenshot.grab(FabricLoader.getInstance().getGameDir().toFile(), mc.getMainRenderTarget(), c -> {});
	}

	private static void build(Minecraft mc) {
		int t = 100;
		STEPS.add(new Step(t - 20, () -> {
			for (String n : List.of("StorageESP", "SpawnerESP", "KillSwitch", "Freecam", "PlayerESP", "SusChunks")) {
				Module m = BreezClientClient.MODULES.get(n);
				if (m != null) m.setEnabled(true);
			}
			BreezClientClient.MODULES.get("StorageESP").setKey(71);
			BreezClientClient.MODULES.get("SpawnerESP").setChatFeedback(true);
			Toasts.show("SpawnerESP", "Enabled", 0xFF55FF55);
			Toasts.show("Amethyst geode", "Fully grown at 12 -40 88 (94%)");
		}));
		String[] tabs = {"Modules", "Config", "GUI", "Macros", "Profiles", "Waypoints", "HUD"};
		for (String tab : tabs) {
			final String name = tab;
			STEPS.add(new Step(t, () -> Tabs.open(name)));
			STEPS.add(new Step(t + 14, () -> shot(mc)));
			t += 30;
		}
		for (String module : List.of("StorageESP", "SusChunks", "KillSwitch", "HitBox", "RTPBaseFinder")) {
			final String name = module;
			STEPS.add(new Step(t, () -> mc.setScreen(new ModuleScreen(BreezClientClient.MODULES.get(name)))));
			STEPS.add(new Step(t + 14, () -> shot(mc)));
			t += 30;
		}
		STEPS.add(new Step(t + 10, () -> mc.setScreen(null)));
		STEPS.add(new Step(t + 30, () -> shot(mc)));
		STEPS.add(new Step(t + 60, mc::stop));
	}

	private static void tick(Minecraft mc) {
		if (ticks < 0 || mc.player == null) return;
		ticks++;
		for (Step s : STEPS) if (s.tick() == ticks) s.action().run();
	}
}
