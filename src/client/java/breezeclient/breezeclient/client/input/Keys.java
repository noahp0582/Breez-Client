package breezeclient.breezeclient.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Pressing and releasing game key bindings on behalf of a module. {@link KeyMapping#setAll()} only re-syncs keyboard
 * bindings, so it leaves attack and use (mouse buttons by default) stuck down; this helper handles both kinds and the
 * "toggle attack / use" options, where every {@code setDown(true)} flips the state instead of setting it.
 */
public final class Keys {
	private Keys() {}

	/** Holds the binding down. Does nothing when it is already down, so toggle-style bindings are not flipped back off. */
	public static void press(KeyMapping k) {
		if (!k.isDown()) k.setDown(true);
	}

	/** Puts the binding back to what the hardware says: still down if you are physically holding that key or button. */
	public static void release(KeyMapping k) {
		Minecraft mc = Minecraft.getInstance();
		boolean physical = physicallyDown(mc, k);
		if (k.isDown() == physical) return;
		k.setDown(physical);
		if (k.isDown() != physical) k.setDown(true); // a toggle-style binding ignores setDown(false) and flips on a "press"
	}

	private static boolean physicallyDown(Minecraft mc, KeyMapping k) {
		if (mc.getWindow() == null) return false;
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(k);
		if (key == null || key.getValue() == InputConstants.UNKNOWN.getValue()) return false;
		if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
		if (key.getType() == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
		return false;
	}
}
