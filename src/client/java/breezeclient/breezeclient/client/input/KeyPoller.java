package breezeclient.breezeclient.client.input;

import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.config.ConfigManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

/** Polls raw key state every tick (module binds + ClickGUI key) and feeds ModuleManager edge detection. */
public final class KeyPoller {
	/** Shows up in Options > Controls under "Breez Client", so it can be rebound like any other key. Default: Right Shift. */
	private static KeyMapping openGui;

	private KeyPoller() {}

	/** True when this key event is the (rebindable) "Open Breez GUI" key. */
	public static boolean isGuiKey(KeyEvent e) { return openGui != null && openGui.matches(e); }

	public static void register(ModuleManager modules, ConfigManager config) {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("breez-client", "breez"));
		openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.breez-client.open_gui", InputConstants.KEY_RSHIFT, category));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> poll(mc, modules, config));
	}

	private static void poll(Minecraft mc, ModuleManager modules, ConfigManager config) {
		if (mc.getWindow() == null) return;
		boolean inScreen = mc.screen != null;
		modules.setInputBlocked(inScreen);

		var cs = breezeclient.breezeclient.client.gui.GuiState.CLIENT;
		breezeclient.breezeclient.core.humanize.ActionLimiter.setGlobalScale(cs.humanization.get());
		if (breezeclient.breezeclient.client.BreezClientClient.commands != null) breezeclient.breezeclient.client.BreezClientClient.commands.setPrefix(cs.safePrefix());

		boolean openRequested = false;
		while (openGui.consumeClick()) openRequested = true;
		if (openRequested && !inScreen && mc.player != null) breezeclient.breezeclient.client.gui.Tabs.openLast();

		Set<Integer> keys = new HashSet<>();
		for (Module m : modules.all()) if (m.key() >= 0) keys.add(m.key());
		for (int key : keys) modules.onKeyState(key, InputConstants.isKeyDown(mc.getWindow(), key));
	}
}
