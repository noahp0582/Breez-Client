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

	@Test
	void functionKeys() {
		assertEquals(290, CommandManager.keyFromName("f1"));
		assertEquals(301, CommandManager.keyFromName("f12"));
		assertEquals(344, CommandManager.keyFromName("rshift"));
	}
}
