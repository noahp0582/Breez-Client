package breezeclient.breezeclient.core.config;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.settings.NumberSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
	static class Probe extends Module {
		final NumberSetting range = add(new NumberSetting("range", 4, 0, 10));
		Probe() { super("probe", Category.MISC); }
	}

	private static ModuleManager managerWith(Probe p) {
		ModuleManager m = new ModuleManager();
		m.register(p);
		return m;
	}

	@Test
	void roundTripPersistsModuleState(@TempDir Path dir) {
		Probe a = new Probe();
		ConfigManager c = new ConfigManager(dir, managerWith(a));
		a.setEnabled(true);
		a.setKey(70);
		a.range.set(8.0);
		c.save();

		Probe b = new Probe();
		new ConfigManager(dir, managerWith(b)).load();
		assertTrue(b.enabled());
		assertEquals(70, b.key());
		assertEquals(8.0, b.range.get());
	}

	@Test
	void missingFileLoadsDefaults(@TempDir Path dir) {
		Probe a = new Probe();
		new ConfigManager(dir, managerWith(a)).load();
		assertFalse(a.enabled());
		assertEquals(4.0, a.range.get());
	}

	@Test
	void corruptFileKeepsBackupAndDefaults(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("config.json"), "{not json");
		Probe a = new Probe();
		assertDoesNotThrow(() -> new ConfigManager(dir, managerWith(a)).load());
		assertFalse(a.enabled());
		assertTrue(Files.exists(dir.resolve("config.json.bad")));
	}

	@Test
	void emptyFileIsTreatedAsCorrupt(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("config.json"), "");
		Probe a = new Probe();
		assertDoesNotThrow(() -> new ConfigManager(dir, managerWith(a)).load());
		assertTrue(Files.exists(dir.resolve("config.json.bad")));
	}

	@Test
	void unknownModuleAndSettingKeysIgnored(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("config.json"),
			"{\"ghost\":{\"enabled\":true},\"probe\":{\"enabled\":true,\"settings\":{\"nope\":1,\"range\":99}}}");
		Probe a = new Probe();
		new ConfigManager(dir, managerWith(a)).load();
		assertTrue(a.enabled());
		assertEquals(10.0, a.range.get());
	}

	@Test
	void profileStaffCaseInsensitive(@TempDir Path dir) {
		ConfigManager c = new ConfigManager(dir, new ModuleManager());
		c.profile("srv").addStaff("Notch");
		assertTrue(c.profile("srv").isStaff("NOTCH"));
		assertFalse(c.profile("srv").isStaff("jeb"));
	}

	@Test
	void profileKeyNormalized(@TempDir Path dir) {
		ConfigManager c = new ConfigManager(dir, new ModuleManager());
		assertSame(c.profile("Example.COM:25565"), c.profile("example.com:25565"));
	}

	@Test
	void profilesPersist(@TempDir Path dir) {
		ConfigManager c = new ConfigManager(dir, new ModuleManager());
		c.profile("a:1").addStaff("x");
		c.profile("a:1").setSeed("12345");
		c.setLastServer("a:1");
		c.save();
		ConfigManager d = new ConfigManager(dir, new ModuleManager());
		d.load();
		assertTrue(d.profile("a:1").isStaff("x"));
		assertEquals("12345", d.profile("a:1").seed());
		assertEquals("a:1", d.lastServer());
	}

	@Test
	void saveIsAtomic(@TempDir Path dir) throws Exception {
		new ConfigManager(dir, new ModuleManager()).save();
		try (var s = Files.list(dir)) {
			assertTrue(s.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
		}
	}
}
