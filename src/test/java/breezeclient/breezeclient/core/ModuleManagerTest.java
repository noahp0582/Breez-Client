package breezeclient.breezeclient.core;

import breezeclient.breezeclient.core.settings.NumberSetting;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleManagerTest {
	static class Probe extends Module {
		int enables, disables, ticks, renders;
		final NumberSetting range = add(new NumberSetting("range", 4, 0, 10));
		Probe(String name) { super(name, Category.MISC); }
		@Override protected void onEnable() { enables++; }
		@Override protected void onDisable() { disables++; }
		@Override protected void onTick() { ticks++; }
		@Override protected void onRender(RenderFrame f) { renders++; }
	}

	static class Thrower extends Module {
		Thrower() { super("thrower", Category.MISC); }
		@Override protected void onTick() { throw new IllegalStateException("boom"); }
		@Override protected void onRender(RenderFrame f) { throw new IllegalStateException("boom"); }
	}

	static class FakeFrame implements RenderFrame {
		public void drawBox(double a, double b, double c, double d, double e, double f, int argb) {}
		public void drawLine(double a, double b, double c, double d, double e, double f, int argb) {}
		public void drawText(double x, double y, double z, String text, int argb) {}
	}

	@Test
	void toggleCallsHooksOnce() {
		Probe p = new Probe("p");
		p.setEnabled(true);
		p.setEnabled(true);
		p.setEnabled(false);
		p.setEnabled(false);
		assertEquals(1, p.enables);
		assertEquals(1, p.disables);
	}

	static class OneShot extends Module {
		OneShot() { super("oneshot", Category.MISC); }
		@Override protected void onEnable() { setEnabled(false); }
	}

	@Test
	void aModuleThatSwitchesItselfOffWhileEnablingIsNotAnnounced() {
		List<String> events = new ArrayList<>();
		Module.setListener((m, on) -> events.add(m.name() + ":" + on));
		try {
			OneShot s = new OneShot();
			s.setEnabled(true);
			assertFalse(s.enabled());
			assertTrue(events.isEmpty(), "got " + events);
			Probe p = new Probe("normal");
			p.setEnabled(true);
			p.setEnabled(false);
			assertEquals(List.of("normal:true", "normal:false"), events);
		} finally {
			Module.setListener(null);
		}
	}

	@Test
	void tickOnlyEnabled() {
		ModuleManager m = new ModuleManager();
		Probe a = new Probe("a"), b = new Probe("b");
		m.register(a);
		m.register(b);
		a.setEnabled(true);
		m.tick();
		m.tick();
		assertEquals(2, a.ticks);
		assertEquals(0, b.ticks);
	}

	@Test
	void keyEdgeTogglesOnce() {
		ModuleManager m = new ModuleManager();
		Probe a = new Probe("a");
		a.setKey(70);
		m.register(a);
		m.onKeyState(70, true);
		m.onKeyState(70, true);
		assertTrue(a.enabled());
		m.onKeyState(70, false);
		m.onKeyState(70, true);
		assertFalse(a.enabled());
	}

	@Test
	void inputBlockedIgnoresKey() {
		ModuleManager m = new ModuleManager();
		Probe a = new Probe("a");
		a.setKey(70);
		m.register(a);
		m.setInputBlocked(true);
		m.onKeyState(70, true);
		assertFalse(a.enabled());
		m.onKeyState(70, false);
		m.setInputBlocked(false);
		m.onKeyState(70, true);
		assertTrue(a.enabled());
	}

	@Test
	void throwingModuleIsDisabledOthersKeepTicking() {
		ModuleManager m = new ModuleManager();
		Thrower t = new Thrower();
		Probe p = new Probe("p");
		m.register(t);
		m.register(p);
		t.setEnabled(true);
		p.setEnabled(true);
		m.tick();
		assertFalse(t.enabled());
		assertEquals(1, p.ticks);
	}

	@Test
	void renderOnlyEnabledAndIsolated() {
		ModuleManager m = new ModuleManager();
		Thrower t = new Thrower();
		Probe on = new Probe("on"), off = new Probe("off");
		m.register(t);
		m.register(on);
		m.register(off);
		t.setEnabled(true);
		on.setEnabled(true);
		m.render(new FakeFrame());
		assertFalse(t.enabled());
		assertEquals(1, on.renders);
		assertEquals(0, off.renders);
	}

	@Test
	void moduleJsonRoundTrip() {
		Probe a = new Probe("a");
		a.setEnabled(true);
		a.setKey(65);
		a.range.set(9.0);
		JsonObject j = a.toJson();
		Probe b = new Probe("a");
		b.fromJson(j);
		assertTrue(b.enabled());
		assertEquals(65, b.key());
		assertEquals(9.0, b.range.get());
	}

	@Test
	void getIsCaseInsensitive() {
		ModuleManager m = new ModuleManager();
		Probe a = new Probe("StorageESP");
		m.register(a);
		assertSame(a, m.get("storageesp"));
		assertNull(m.get("nope"));
	}
}
