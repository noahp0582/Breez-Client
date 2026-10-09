package breezeclient.breezeclient.core.sus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SusScoreTest {
	@Test
	void parseWeightsReadsIdEqualsWeight() {
		Map<String, Double> w = SusScore.parseWeights(List.of("minecraft:piston=3", "chest=1.5", "minecraft:hopper=2"));
		assertEquals(3.0, w.get("minecraft:piston"));
		assertEquals(1.5, w.get("minecraft:chest"));
		assertEquals(2.0, w.get("minecraft:hopper"));
	}

	@Test
	void parseWeightsIgnoresMalformed() {
		Map<String, Double> w = SusScore.parseWeights(List.of("", "a=b", "minecraft:x=", "minecraft:ok=2", "=5"));
		assertEquals(1, w.size());
		assertEquals(2.0, w.get("minecraft:ok"));
	}

	@Test
	void parseWeightsDefaultsToOneWhenNoWeight() {
		Map<String, Double> w = SusScore.parseWeights(List.of("minecraft:barrel"));
		assertEquals(1.0, w.get("minecraft:barrel"));
	}

	@Test
	void scoreSumsCountsTimesWeightsPlusBlockEntities() {
		Map<String, Double> w = Map.of("minecraft:piston", 3.0, "minecraft:chest", 1.0);
		Map<String, Integer> counts = Map.of("minecraft:piston", 4, "minecraft:chest", 10);
		assertEquals(4 * 3.0 + 10 * 1.0 + 20 * 0.5, SusScore.score(counts, w, 20, 0.5), 1e-9);
	}

	@Test
	void unknownBlocksScoreZero() {
		assertEquals(0.0, SusScore.score(Map.of("minecraft:dirt", 999), Map.of("minecraft:piston", 3.0), 0, 0.5), 1e-9);
	}

	@Test
	void emptyChunkScoresZero() {
		assertEquals(0.0, SusScore.score(Map.of(), Map.of(), 0, 0.5), 1e-9);
	}

	@Test
	void negativeWeightsAllowedToDiscountNaturalBlocks() {
		Map<String, Double> w = SusScore.parseWeights(List.of("minecraft:spawner=-5"));
		assertEquals(-5.0, w.get("minecraft:spawner"));
	}
}
