package breezeclient.breezeclient.core.sus;

import breezeclient.breezeclient.core.nav.Pos;
import breezeclient.breezeclient.core.sus.BaseSignatures.Finding;
import breezeclient.breezeclient.core.sus.BaseSignatures.Signature;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GriefSignaturesTest {
	private static final Set<Signature> BOTH = EnumSet.of(Signature.GRIEFED, Signature.LAVACAST);
	private static final int SEA = 63;

	private static void put(Map<String, List<Pos>> m, String id, int n, int y) {
		List<Pos> l = m.computeIfAbsent(id, k -> new ArrayList<>());
		for (int i = 0; i < n; i++) l.add(new Pos(i % 16, y, (i / 16) % 16));
	}

	private static Finding only(List<Finding> f, Signature s) {
		assertEquals(1, f.size(), f.toString());
		assertEquals(s, f.get(0).signature());
		return f.get(0);
	}

	@Test
	void aBaseWithTntLeftInItIsGriefed() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:crafting_table", 1, 70);
		put(b, "minecraft:furnace", 2, 70);
		put(b, "minecraft:chest", 3, 70);
		put(b, "minecraft:tnt", 2, 71);
		Finding f = only(GriefSignatures.evaluate(b, false, false, BOTH, SEA), Signature.GRIEFED);
		assertTrue(f.detail().contains("unlit TNT"), f.detail());
		assertTrue(f.score() >= 30, "must clear the default threshold: " + f.score());
	}

	@Test
	void tntByItselfOrBesideATempleIsNot() {
		Map<String, List<Pos>> lone = new HashMap<>();
		put(lone, "minecraft:tnt", 9, 60);
		assertTrue(GriefSignatures.evaluate(lone, false, false, BOTH, SEA).isEmpty(), "nobody lived here");

		Map<String, List<Pos>> temple = new HashMap<>();
		put(temple, "minecraft:tnt", 9, 60);
		put(temple, "minecraft:chest", 4, 60);
		assertTrue(GriefSignatures.evaluate(temple, true, false, BOTH, SEA).isEmpty(), "natural structure nearby");
	}

	@Test
	void workstationsWithEveryChestGoneIsAStrippedBase() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:furnace", 3, 70);
		put(b, "minecraft:enchanting_table", 1, 70);
		put(b, "minecraft:anvil", 1, 70);
		Finding f = only(GriefSignatures.evaluate(b, false, false, BOTH, SEA), Signature.GRIEFED);
		assertTrue(f.detail().contains("not one chest"), f.detail());

		put(b, "minecraft:chest", 2, 70);
		assertTrue(GriefSignatures.evaluate(b, false, false, BOTH, SEA).isEmpty(), "a base that still has its chests is just a base");
	}

	@Test
	void aSmallCampIsNotAStrippedBase() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:crafting_table", 1, 70);
		put(b, "minecraft:furnace", 2, 70);
		assertTrue(GriefSignatures.evaluate(b, false, false, BOTH, SEA).isEmpty());
	}

	@Test
	void fireCountsInTheOverworldButNotInTheNether() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:ender_chest", 1, 70);
		put(b, "minecraft:fire", 5, 70);
		only(GriefSignatures.evaluate(b, false, false, BOTH, SEA), Signature.GRIEFED);
		assertTrue(GriefSignatures.evaluate(b, false, true, BOTH, SEA).isEmpty());
	}

	@Test
	void aPortalFrameIsNotAnObsidianPileAndARuinedPortalIsNatural() {
		Map<String, List<Pos>> portal = new HashMap<>();
		put(portal, "minecraft:chest", 2, 70);
		put(portal, "minecraft:obsidian", 14, 70);
		assertTrue(GriefSignatures.evaluate(portal, false, false, BOTH, SEA).isEmpty());

		Map<String, List<Pos>> buried = new HashMap<>();
		put(buried, "minecraft:chest", 2, 70);
		put(buried, "minecraft:obsidian", 40, 70);
		only(GriefSignatures.evaluate(buried, false, false, BOTH, SEA), Signature.GRIEFED);

		put(buried, "minecraft:crying_obsidian", 3, 70);
		assertTrue(GriefSignatures.evaluate(buried, false, false, BOTH, SEA).isEmpty());

		Map<String, List<Pos>> deep = new HashMap<>();
		put(deep, "minecraft:chest", 2, 10);
		put(deep, "minecraft:obsidian", 40, 10);
		assertTrue(GriefSignatures.evaluate(deep, false, false, BOTH, SEA).isEmpty(), "obsidian by an underground lava lake");
	}

	@Test
	void aLavacastIsALotOfCobblestoneStandingTall() {
		Map<String, List<Pos>> cast = new HashMap<>();
		List<Pos> l = new ArrayList<>();
		for (int y = 64; y < 94; y++) for (int i = 0; i < 4; i++) l.add(new Pos(i, y, i));
		cast.put("minecraft:cobblestone", l);
		Finding f = only(GriefSignatures.evaluate(cast, true, false, BOTH, SEA), Signature.LAVACAST);
		assertEquals(64, f.min().y());
		assertEquals(93, f.max().y());

		Map<String, List<Pos>> house = new HashMap<>();
		put(house, "minecraft:cobblestone", 200, 70); // wide and one block high
		assertTrue(GriefSignatures.evaluate(house, false, false, BOTH, SEA).isEmpty());

		Map<String, List<Pos>> cave = new HashMap<>();
		List<Pos> under = new ArrayList<>();
		for (int y = 0; y < 40; y++) for (int i = 0; i < 4; i++) under.add(new Pos(i, y, i));
		cave.put("minecraft:cobblestone", under);
		assertTrue(GriefSignatures.evaluate(cave, false, false, BOTH, SEA).isEmpty(), "below sea level does not count");
	}

	@Test
	void nothingIsReportedForSignaturesThatAreSwitchedOff() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:chest", 3, 70);
		put(b, "minecraft:tnt", 2, 71);
		assertTrue(GriefSignatures.evaluate(b, false, false, EnumSet.of(Signature.LAVACAST), SEA).isEmpty());
	}
}
