package breezeclient.breezeclient.core.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdPatternTest {
	@Test
	void exactAndNamespaceDefault() {
		assertTrue(IdPattern.matches("minecraft:spawner", "minecraft:spawner"));
		assertTrue(IdPattern.matches("spawner", "minecraft:spawner"));
		assertFalse(IdPattern.matches("spawner", "minecraft:spawner_egg"));
	}

	@Test
	void wildcards() {
		assertTrue(IdPattern.matches("minecraft:*_shulker_box", "minecraft:purple_shulker_box"));
		assertFalse(IdPattern.matches("minecraft:*_shulker_box", "minecraft:shulker_shell"));
		assertTrue(IdPattern.matches("*netherite*", "minecraft:netherite_ingot"));
		assertTrue(IdPattern.matches("minecraft:*", "minecraft:anything"));
		assertFalse(IdPattern.matches("othermod:*", "minecraft:anything"));
	}

	@Test
	void anyOfAList() {
		List<String> l = List.of("minecraft:spawner", "minecraft:*_shulker_box");
		assertTrue(IdPattern.matchesAny(l, "minecraft:red_shulker_box"));
		assertFalse(IdPattern.matchesAny(l, "minecraft:dirt"));
		assertFalse(IdPattern.matchesAny(List.of(), "minecraft:dirt"));
	}
}
