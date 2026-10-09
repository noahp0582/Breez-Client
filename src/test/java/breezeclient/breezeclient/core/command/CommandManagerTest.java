package breezeclient.breezeclient.core.command;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandManagerTest {
	static class Fly extends Module {
		Fly() { super("Fly", Category.MOVEMENT); }
	}

	ModuleManager mm;
	Fly fly;
	CommandManager cm;
	List<String> out;

	@BeforeEach
	void setUp() {
		mm = new ModuleManager();
		fly = new Fly();
		mm.register(fly);
		cm = new CommandManager(".", mm);
		out = new ArrayList<>();
	}

	@Test
	void nonPrefixedNotHandled() {
		assertFalse(cm.handle("hello", out::add));
		assertTrue(out.isEmpty());
	}

	@Test
	void barePrefixHandledWithHint() {
		assertTrue(cm.handle(".", out::add));
		assertTrue(out.get(0).contains("help"));
	}

	@Test
	void unknownCommandHandled() {
		assertTrue(cm.handle(".wat", out::add));
		assertTrue(out.get(0).startsWith("Unknown command"));
	}

	@Test
	void toggleFlipsModule() {
		cm.handle(".toggle fly", out::add);
		assertTrue(fly.enabled());
		cm.handle(".TOGGLE FLY", out::add);
		assertFalse(fly.enabled());
	}

	@Test
	void toggleUnknownModuleReplies() {
		cm.handle(".toggle nothing", out::add);
		assertTrue(out.get(0).contains("No such module"));
	}

	@Test
	void bindSetsKey() {
		cm.handle(".bind fly f", out::add);
		assertEquals(70, fly.key());
		cm.handle(".bind fly none", out::add);
		assertEquals(-1, fly.key());
	}

	@Test
	void bindUnknownKeyRejected() {
		cm.handle(".bind fly zzzz", out::add);
		assertEquals(-1, fly.key());
		assertTrue(out.get(0).contains("Unknown key"));
	}

	@Test
	void bindMissingArgsShowsUsage() {
		cm.handle(".bind fly", out::add);
		assertTrue(out.get(0).startsWith("Usage"));
	}

	@Test
	void modulesLists() {
		cm.handle(".modules", out::add);
		assertTrue(out.get(0).contains("Fly"));
	}

	static class Cfg extends Module {
		final breezeclient.breezeclient.core.settings.BoolSetting flag = add(new breezeclient.breezeclient.core.settings.BoolSetting("Tab-list ghosts", false));
		final breezeclient.breezeclient.core.settings.NumberSetting radius = add(new breezeclient.breezeclient.core.settings.NumberSetting("Radius", 10, 0, 100));
		final breezeclient.breezeclient.core.settings.StringListSetting names = add(new breezeclient.breezeclient.core.settings.StringListSetting("Trusted", List.of()));
		final breezeclient.breezeclient.core.settings.ColorSetting color = add(new breezeclient.breezeclient.core.settings.ColorSetting("Color", 0));
		Cfg() { super("Cfg", Category.MISC); }
	}

	@Test
	void setBoolNumberListColor() {
		Cfg c = new Cfg();
		mm.register(c);
		cm.handle(".set cfg tab-listghosts on", out::add);
		assertTrue(c.flag.get());
		cm.handle(".set cfg radius 999", out::add);
		assertEquals(100.0, c.radius.get());
		cm.handle(".set cfg trusted add Steve", out::add);
		assertTrue(c.names.contains("steve"));
		cm.handle(".set cfg trusted remove STEVE", out::add);
		assertFalse(c.names.contains("steve"));
		cm.handle(".set cfg color ff8800", out::add);
		assertEquals(0xFFFF8800, c.color.get());
	}

	@Test
	void setRejectsGarbage() {
		Cfg c = new Cfg();
		mm.register(c);
		cm.handle(".set cfg radius banana", out::add);
		assertEquals(10.0, c.radius.get());
		cm.handle(".set cfg nothing 1", out::add);
		assertTrue(out.get(out.size() - 1).contains("No such setting"));
		cm.handle(".set nomod x 1", out::add);
		assertTrue(out.get(out.size() - 1).contains("No such module"));
	}

	@Test
	void functionKeys() {
		assertEquals(290, CommandManager.keyFromName("f1"));
		assertEquals(301, CommandManager.keyFromName("f12"));
		assertEquals(344, CommandManager.keyFromName("rshift"));
	}
}
