package breezeclient.breezeclient.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ModuleManager {
	private static final Logger LOG = Logger.getLogger("breez-client");

	private final List<Module> modules = new ArrayList<>();
	private final Set<Integer> down = new HashSet<>();
	private boolean inputBlocked;
	private static int crashes;

	/** Number of module exceptions caught so far (used by the self-test). */
	public static int crashCount() { return crashes; }

	public void register(Module m) { modules.add(m); }

	public void unregister(Module m) {
		m.setEnabled(false);
		modules.remove(m);
	}

	public List<Module> all() { return Collections.unmodifiableList(modules); }

	public Module get(String name) {
		for (Module m : modules) if (m.name().equalsIgnoreCase(name)) return m;
		return null;
	}

	public void tick() {
		for (Module m : new ArrayList<>(modules)) {
			if (!m.enabled()) continue;
			try {
				m.tickInternal();
			} catch (Throwable t) {
				fail(m, t);
			}
		}
	}

	public void render(RenderFrame frame) {
		for (Module m : new ArrayList<>(modules)) {
			if (!m.enabled()) continue;
			try {
				m.renderInternal(frame);
			} catch (Throwable t) {
				fail(m, t);
			}
		}
	}

	private void fail(Module m, Throwable t) {
		crashes++;
		LOG.log(Level.SEVERE, "Module " + m.name() + " crashed and was disabled", t);
		try {
			m.setEnabled(false);
		} catch (Throwable ignored) {
			// onDisable may also throw; the module is already marked disabled
		}
	}

	public void setInputBlocked(boolean blocked) { this.inputBlocked = blocked; }

	/** Edge-triggered: toggles bound modules on the up-to-down transition only. */
	public void onKeyState(int key, boolean isDown) {
		if (key < 0) return;
		if (!isDown) {
			down.remove(key);
			return;
		}
		boolean edge = down.add(key);
		if (!edge || inputBlocked) return;
		for (Module m : modules) if (m.key() == key) m.toggle();
	}
}
