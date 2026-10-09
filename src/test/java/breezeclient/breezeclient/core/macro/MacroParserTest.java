package breezeclient.breezeclient.core.macro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MacroParserTest {
	@Test
	void parsesSemicolonAndNewlineSeparatedSteps() {
		var r = MacroParser.parse("say hello world; wait 20\nslot 3; use 5");
		assertTrue(r.errors().isEmpty(), r.errors().toString());
		assertEquals(4, r.steps().size());
		assertEquals("say", r.steps().get(0).op());
		assertEquals(java.util.List.of("hello world"), r.steps().get(0).args());
		assertEquals(java.util.List.of("20"), r.steps().get(1).args());
		assertEquals(java.util.List.of("3"), r.steps().get(2).args());
	}

	@Test
	void defaultsUseAndAttackToOneTick() {
		var r = MacroParser.parse("use; attack");
		assertEquals(java.util.List.of("1"), r.steps().get(0).args());
		assertEquals(java.util.List.of("1"), r.steps().get(1).args());
	}

	@Test
	void loopMustBeLastAndSetsFlag() {
		var ok = MacroParser.parse("jump; wait 10; loop");
		assertTrue(ok.loop());
		assertEquals(2, ok.steps().size());
		var bad = MacroParser.parse("loop; jump");
		assertFalse(bad.errors().isEmpty());
	}

	@Test
	void unknownOpIsAnError() {
		var r = MacroParser.parse("dance 5");
		assertEquals(1, r.errors().size());
		assertTrue(r.errors().get(0).contains("dance"));
	}

	@Test
	void badNumbersAreErrors() {
		assertFalse(MacroParser.parse("wait banana").errors().isEmpty());
		assertFalse(MacroParser.parse("wait -5").errors().isEmpty());
		assertFalse(MacroParser.parse("slot 0").errors().isEmpty());
		assertFalse(MacroParser.parse("slot 10").errors().isEmpty());
		assertFalse(MacroParser.parse("look 90").errors().isEmpty());
		assertFalse(MacroParser.parse("sneak maybe").errors().isEmpty());
		assertFalse(MacroParser.parse("say").errors().isEmpty());
	}

	@Test
	void lookAndTurnTakeTwoFloats() {
		var r = MacroParser.parse("look 90 -10.5; turn 90 0");
		assertTrue(r.errors().isEmpty());
		assertEquals(java.util.List.of("90", "-10.5"), r.steps().get(0).args());
	}

	@Test
	void emptyScriptIsAnError() {
		assertFalse(MacroParser.parse("   ").errors().isEmpty());
		assertFalse(MacroParser.parse(";;").errors().isEmpty());
	}

	@Test
	void sayKeepsInnerSpacesAndSlashCommands() {
		var r = MacroParser.parse("say /home base");
		assertEquals(java.util.List.of("/home base"), r.steps().get(0).args());
	}
}
