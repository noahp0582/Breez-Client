package breezeclient.breezeclient.client.input;

import breezeclient.breezeclient.client.gui.ClickGui;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.config.ConfigManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.Set;

/** Polls raw key state every tick (module binds + ClickGUI key) and feeds ModuleManager edge detection. */
public final class KeyPoller {
	public static final int GUI_KEY = 344; // right shift

	private static boolean guiKeyWasDown;

	private KeyPoller() {}

	public static void register(ModuleManager modules, ConfigManager config) {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> poll(mc, modules, config));
	}

	private static void poll(Minecraft mc, ModuleManager modules, ConfigManager config) {
		if (mc.getWindow() == null) return;
		boolean inScreen = mc.screen != null;
		modules.setInputBlocked(inScreen);

		boolean guiDown = InputConstants.isKeyDown(mc.getWindow(), GUI_KEY);
		if (guiDown && !guiKeyWasDown && !inScreen && mc.player != null) mc.setScreen(new ClickGui(modules, config));
		guiKeyWasDown = guiDown;

		Set<Integer> keys = new HashSet<>();
		for (Module m : modules.all()) if (m.key() >= 0) keys.add(m.key());
		for (int key : keys) modules.onKeyState(key, InputConstants.isKeyDown(mc.getWindow(), key));
	}
}
