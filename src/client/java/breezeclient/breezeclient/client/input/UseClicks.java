package breezeclient.breezeclient.client.input;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lets modules take a right-click (use) before the game acts on it. A taken click places, opens and sends nothing.
 * The Minecraft mixin calls {@link #intercept} at the start of {@code startUseItem} and {@link #keyState} once per keybind pass.
 */
public final class UseClicks {
	public interface Interceptor {
		/** @return true to take this click away from the game */
		boolean onUse(Minecraft mc);

		/** The use key's state at the start of every keybind pass, for telling a new press from a held one. */
		default void useKeyState(boolean down) {}
	}

	private static final List<Interceptor> INTERCEPTORS = new CopyOnWriteArrayList<>();

	private UseClicks() {}

	public static void register(Interceptor i) { INTERCEPTORS.add(i); }

	public static boolean intercept(Minecraft mc) {
		for (Interceptor i : INTERCEPTORS) if (i.onUse(mc)) return true;
		return false;
	}

	public static void keyState(boolean down) {
		for (Interceptor i : INTERCEPTORS) i.useKeyState(down);
	}
}
