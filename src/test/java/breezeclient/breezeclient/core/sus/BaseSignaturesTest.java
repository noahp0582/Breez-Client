package breezeclient.breezeclient.core.sus;

import breezeclient.breezeclient.core.nav.Pos;
import breezeclient.breezeclient.core.sus.BaseSignatures.Config;
import breezeclient.breezeclient.core.sus.BaseSignatures.Finding;
import breezeclient.breezeclient.core.sus.BaseSignatures.Natural;
import breezeclient.breezeclient.core.sus.BaseSignatures.Preset;
import breezeclient.breezeclient.core.sus.BaseSignatures.Signature;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseSignaturesTest {
	private static final Config CFG = Config.defaults();
	private static final Set<Signature> ALL = Preset.ALL_BASES.signatures();

	private static void put(Map<String, List<Pos>> m, String id, Pos... ps) {
		m.computeIfAbsent(id, k -> new ArrayList<>()).addAll(List.of(ps));
	}

	private static Set<Signature> found(List<Finding> fs) {
		Set<Signature> s = EnumSet.noneOf(Signature.class);
		for (Finding f : fs) s.add(f.signature());
		return s;
	}

	private static Map<String, Integer> counts(Object... kv) {
		Map<String, Integer> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
		return m;
	}

	@Test
	void tightSpawnerPileIsAStack() {
		Map<String, List<Pos>> b = new HashMap<>();
		for (int i = 0; i < 5; i++) put(b, "minecraft:spawner", new Pos(100 + (i % 2), 20, 200 + i / 2));
		List<Finding> f = BaseSignatures.evaluate(b, false, ALL, CFG);
		assertTrue(found(f).contains(Signature.SPAWNER_STACK));
		Finding stack = BaseSignatures.strongest(f);
		assertEquals(Signature.SPAWNER_STACK, stack.signature());
		assertTrue(stack.score() >= 50 + 8 * 2);
	}

	@Test
	void spreadOutSpawnersAreNotAStack() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:spawner", new Pos(0, 20, 0), new Pos(40, 20, 0), new Pos(0, 20, 40), new Pos(40, 20, 40));
		// each one has mossy cobblestone around it, like separate dungeons
		for (Pos s : new ArrayList<>(b.get("minecraft:spawner"))) put(b, "minecraft:mossy_cobblestone", new Pos(s.x() + 1, s.y() - 1, s.z()));
		List<Finding> f = BaseSignatures.evaluate(b, false, ALL, CFG);
		assertFalse(found(f).contains(Signature.SPAWNER_STACK));
		assertFalse(found(f).contains(Signature.LONE_SPAWNER));
	}

	@Test
	void dungeonSpawnerWithChestIsNotFlagged() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:spawner", new Pos(10, 30, 10));
		for (int i = 0; i < 8; i++) put(b, "minecraft:mossy_cobblestone", new Pos(8 + i % 4, 29, 8 + i / 4));
		put(b, "minecraft:chest", new Pos(13, 30, 10), new Pos(7, 30, 11));
		assertTrue(BaseSignatures.evaluate(b, false, ALL, CFG).isEmpty());
	}

	@Test
	void spawnerWithoutNaturalSurroundingsIsFlagged() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:spawner", new Pos(10, -30, 10));
		List<Finding> f = BaseSignatures.evaluate(b, false, ALL, CFG);
		assertEquals(Set.of(Signature.LONE_SPAWNER), found(f));
		// but not when a natural structure is nearby
		assertTrue(BaseSignatures.evaluate(b, true, ALL, CFG).isEmpty());
	}

	@Test
	void hoppersNextToASpawnerMakeAFarm() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:spawner", new Pos(0, 10, 0));
		put(b, "minecraft:hopper", new Pos(1, 6, 0), new Pos(2, 6, 0));
		put(b, "minecraft:mossy_cobblestone", new Pos(1, 9, 1));
		assertTrue(found(BaseSignatures.evaluate(b, false, ALL, CFG)).contains(Signature.SPAWNER_FARM));
	}

	@Test
	void hoppersAreNeverNaturalSoTheyAlwaysCount() {
		Map<String, List<Pos>> b = new HashMap<>();
		for (int i = 0; i < 4; i++) put(b, "minecraft:hopper", new Pos(i, 5, 0));
		put(b, "minecraft:chest", new Pos(0, 4, 0), new Pos(1, 4, 0));
		List<Finding> f = BaseSignatures.evaluate(b, true, ALL, CFG); // even next to a natural structure
		assertTrue(found(f).contains(Signature.HOPPER_STORAGE));
		assertTrue(BaseSignatures.strongest(f).score() >= 38);
	}

	@Test
	void storageRoomNeedsManyContainersAndNoStructure() {
		Map<String, List<Pos>> b = new HashMap<>();
		for (int i = 0; i < 10; i++) put(b, "minecraft:chest", new Pos(i, 5, 0));
		assertTrue(found(BaseSignatures.evaluate(b, false, ALL, CFG)).contains(Signature.STORAGE_ROOM));
		assertFalse(found(BaseSignatures.evaluate(b, true, ALL, CFG)).contains(Signature.STORAGE_ROOM));
		Map<String, List<Pos>> few = new HashMap<>();
		for (int i = 0; i < 4; i++) put(few, "minecraft:chest", new Pos(i, 5, 0));
		assertTrue(BaseSignatures.evaluate(few, false, ALL, CFG).isEmpty());
	}

	@Test
	void shulkersCountDouble() {
		Map<String, List<Pos>> b = new HashMap<>();
		for (int i = 0; i < 4; i++) put(b, "minecraft:purple_shulker_box", new Pos(i, 5, 0));
		assertTrue(found(BaseSignatures.evaluate(b, false, ALL, CFG)).contains(Signature.STORAGE_ROOM)); // 4 x 2 = 8
	}

	@Test
	void villageIsRecognizedFromItsJobsitesAndPaths() {
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:bell", 1, "minecraft:composter", 2, "minecraft:lectern", 1)).contains(Natural.VILLAGE));
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:dirt_path", 30)).contains(Natural.VILLAGE));
		assertFalse(BaseSignatures.naturalKinds(counts("minecraft:composter", 1)).contains(Natural.VILLAGE));
	}

	@Test
	void otherNaturalStructures() {
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:cobweb", 9, "minecraft:rail", 5)).contains(Natural.MINESHAFT));
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:mossy_cobblestone", 20)).contains(Natural.DUNGEON));
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:end_portal_frame", 3)).contains(Natural.STRONGHOLD));
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:sculk_shrieker", 1)).contains(Natural.ANCIENT_CITY));
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:trial_spawner", 1)).contains(Natural.TRIAL_CHAMBER));
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:nether_bricks", 40)).contains(Natural.NETHER_FORTRESS));
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:gilded_blackstone", 2)).contains(Natural.BASTION));
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:chest", 30, "minecraft:furnace", 5)).isEmpty());
	}

	@Test
	void presetsPickTheirSignatures() {
		assertTrue(Preset.SPAWNER_BASES.signatures().contains(Signature.SPAWNER_STACK));
		assertFalse(Preset.SPAWNER_BASES.signatures().contains(Signature.STORAGE_ROOM));
		assertFalse(Preset.SPAWNER_BASES.signatures().contains(Signature.REDSTONE));
		assertEquals(Signature.values().length, Preset.ALL_BASES.signatures().size());
		assertTrue(Preset.CUSTOM.signatures().isEmpty());
	}

	@Test
	void combinedScoreRanksMultipleSignaturesHigher() {
		Map<String, List<Pos>> one = new HashMap<>();
		for (int i = 0; i < 3; i++) put(one, "minecraft:spawner", new Pos(i, 10, 0));
		Map<String, List<Pos>> two = new HashMap<>(one);
		put(two, "minecraft:ender_chest", new Pos(5, 10, 5));
		double a = BaseSignatures.combine(BaseSignatures.evaluate(one, false, ALL, CFG));
		double b = BaseSignatures.combine(BaseSignatures.evaluate(two, false, ALL, CFG));
		assertTrue(b > a);
	}

	@Test
	void nothingFoundMeansZero() {
		assertEquals(0, BaseSignatures.combine(List.of()));
		assertTrue(BaseSignatures.evaluate(new HashMap<>(), false, ALL, CFG).isEmpty());
	}

	@Test
	void scanIdsCoverEverythingEvaluateUses() {
		Set<String> ids = BaseSignatures.scanIds();
		for (String needed : List.of("minecraft:spawner", "minecraft:hopper", "minecraft:ender_chest", "minecraft:beacon", "minecraft:chest",
			"minecraft:piston", "minecraft:bell", "minecraft:mossy_cobblestone", "minecraft:cobweb", "minecraft:purple_shulker_box")) {
			assertTrue(ids.contains(needed), needed);
		}
	}

	@Test
	void pileSplitByAChunkBorderIsStillAStack() {
		// 2 spawners in chunk 0 and 2 in chunk 1: neither side reaches minStack 3 alone
		Map<String, List<Pos>> own = new HashMap<>();
		put(own, "minecraft:spawner", new Pos(14, 10, 4), new Pos(15, 10, 4));
		assertFalse(found(BaseSignatures.evaluate(own, false, ALL, CFG)).contains(Signature.SPAWNER_STACK));
		Map<String, List<Pos>> merged = new HashMap<>(own);
		put(merged, "minecraft:spawner", new Pos(16, 10, 4), new Pos(17, 10, 4));
		List<Finding> a = BaseSignatures.evaluateClusters(merged, ALL, CFG, 0, 0);
		assertTrue(found(a).contains(Signature.SPAWNER_STACK));
		assertTrue(BaseSignatures.evaluateClusters(merged, ALL, CFG, 1, 0).isEmpty()); // reported once, by the anchor chunk
	}

	@Test
	void clusterAnchorKeepsASmallerLocalPileFromBeingShadowed() {
		Map<String, List<Pos>> merged = new HashMap<>();
		put(merged, "minecraft:spawner", new Pos(2, 10, 2), new Pos(3, 10, 2), new Pos(4, 10, 2)); // 3 in chunk (0,0)
		for (int i = 0; i < 6; i++) put(merged, "minecraft:spawner", new Pos(40 + i, 10, 2)); // bigger pile in chunk (2,0)
		assertTrue(found(BaseSignatures.evaluateClusters(merged, ALL, CFG, 0, 0)).contains(Signature.SPAWNER_STACK));
		assertTrue(found(BaseSignatures.evaluateClusters(merged, ALL, CFG, 2, 0)).contains(Signature.SPAWNER_STACK));
	}

	@Test
	void scatteredHoppersAreNotOneGroup() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:hopper", new Pos(0, 5, 0), new Pos(30, 5, 0), new Pos(60, 5, 0), new Pos(90, 5, 0));
		assertFalse(found(BaseSignatures.evaluateClusters(b, ALL, CFG, 0, 0)).contains(Signature.HOPPER_STORAGE));
	}

	@Test
	void playerBuildingBlocksNeedAMinimumAndNoStructure() {
		Map<String, List<Pos>> b = new HashMap<>();
		for (int i = 0; i < 10; i++) put(b, "minecraft:white_concrete", new Pos(i, 5, 0));
		List<Finding> f = BaseSignatures.evaluate(b, false, ALL, CFG);
		assertTrue(found(f).contains(Signature.BUILDING_BLOCKS));
		assertFalse(found(BaseSignatures.evaluate(b, true, ALL, CFG)).contains(Signature.BUILDING_BLOCKS));
		Map<String, List<Pos>> few = new HashMap<>();
		for (int i = 0; i < 3; i++) put(few, "minecraft:glass", new Pos(i, 5, 0));
		put(few, "minecraft:red_stained_glass", new Pos(9, 5, 0));
		assertFalse(found(BaseSignatures.evaluate(few, false, ALL, CFG)).contains(Signature.BUILDING_BLOCKS));
	}

	@Test
	void litFurnaceIsActivity() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:furnace[lit]", new Pos(1, 5, 1));
		put(b, "minecraft:furnace", new Pos(2, 5, 1), new Pos(3, 5, 1));
		List<Finding> f = BaseSignatures.evaluate(b, false, ALL, CFG);
		assertTrue(found(f).contains(Signature.ACTIVITY));
		assertTrue(BaseSignatures.strongest(f).score() >= 30);
		Map<String, List<Pos>> idle = new HashMap<>();
		put(idle, "minecraft:furnace", new Pos(2, 5, 1));
		assertFalse(found(BaseSignatures.evaluate(idle, false, ALL, CFG)).contains(Signature.ACTIVITY));
	}

	@Test
	void packedAnimalsAreAFarmButAFewAreNot() {
		List<BaseSignatures.EntityPoint> pen = new java.util.ArrayList<>();
		for (int i = 0; i < 14; i++) pen.add(new BaseSignatures.EntityPoint("minecraft:cow", false, new Pos(2 + i % 4, 5, 2 + i / 4)));
		assertTrue(found(BaseSignatures.evaluateEntities(pen, false, 0, 0)).contains(Signature.ENTITY_CLUSTER));
		List<BaseSignatures.EntityPoint> herd = pen.subList(0, 4);
		assertTrue(BaseSignatures.evaluateEntities(herd, false, 0, 0).isEmpty());
	}

	@Test
	void villagerCellsAreSkippedNextToAVillage() {
		List<BaseSignatures.EntityPoint> cell = new java.util.ArrayList<>();
		for (int i = 0; i < 6; i++) cell.add(new BaseSignatures.EntityPoint("minecraft:villager", false, new Pos(2 + i, 5, 2)));
		assertTrue(found(BaseSignatures.evaluateEntities(cell, false, 0, 0)).contains(Signature.ENTITY_CLUSTER));
		assertTrue(BaseSignatures.evaluateEntities(cell, true, 0, 0).isEmpty());
	}

	@Test
	void entityGroupIsReportedOnlyByItsAnchorChunk() {
		List<BaseSignatures.EntityPoint> stands = new java.util.ArrayList<>();
		for (int i = 0; i < 3; i++) stands.add(new BaseSignatures.EntityPoint("minecraft:armor_stand", false, new Pos(14 + i, 5, 2)));
		assertFalse(BaseSignatures.evaluateEntities(stands, false, 0, 0).isEmpty());
		assertTrue(BaseSignatures.evaluateEntities(stands, false, 1, 0).isEmpty());
	}

	@Test
	void tunnelFindingScalesWithLength() {
		double short12 = BaseSignatures.tunnelFinding(12, 1, new Pos(0, 0, 0), new Pos(12, 2, 0)).score();
		double long16 = BaseSignatures.tunnelFinding(16, 1, new Pos(0, 0, 0), new Pos(16, 2, 0)).score();
		double many = BaseSignatures.tunnelFinding(16, 3, new Pos(0, 0, 0), new Pos(16, 2, 0)).score();
		assertTrue(long16 > short12);
		assertTrue(many > long16);
	}

	@Test
	void copperBulbsAndGratesMarkATrialChamber() {
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:waxed_copper_bulb", 3)).contains(Natural.TRIAL_CHAMBER));
		assertTrue(BaseSignatures.naturalKinds(counts("minecraft:waxed_oxidized_copper_grate", 5)).contains(Natural.TRIAL_CHAMBER));
		assertFalse(BaseSignatures.naturalKinds(counts("minecraft:copper_bulb", 1)).contains(Natural.TRIAL_CHAMBER));
		assertTrue(BaseSignatures.isBig(Natural.TRIAL_CHAMBER));
		assertFalse(BaseSignatures.isBig(Natural.VILLAGE));
		assertTrue(BaseSignatures.scanIds().contains("minecraft:waxed_exposed_copper_bulb"));
	}

	@Test
	void nearABigStructureAFewHoppersAreNotABase() {
		Map<String, List<Pos>> b = new HashMap<>();
		for (int i = 0; i < 6; i++) put(b, "minecraft:hopper", new Pos(i, 5, 0));
		put(b, "minecraft:barrel", new Pos(0, 4, 0), new Pos(1, 4, 0), new Pos(2, 4, 0));
		assertTrue(found(BaseSignatures.evaluate(b, false, false, ALL, CFG)).contains(Signature.HOPPER_STORAGE));
		assertFalse(found(BaseSignatures.evaluate(b, true, true, ALL, CFG)).contains(Signature.HOPPER_STORAGE));
		Map<String, List<Pos>> farm = new HashMap<>();
		for (int i = 0; i < 12; i++) put(farm, "minecraft:hopper", new Pos(i, 5, 0));
		assertTrue(found(BaseSignatures.evaluate(farm, true, true, ALL, CFG)).contains(Signature.HOPPER_STORAGE)); // a real farm still shows
	}

	@Test
	void nearABigStructureASpawnerNeedsMorePartsToBeAFarm() {
		Map<String, List<Pos>> b = new HashMap<>();
		put(b, "minecraft:spawner", new Pos(0, 10, 0));
		put(b, "minecraft:hopper", new Pos(1, 6, 0), new Pos(2, 6, 0));
		put(b, "minecraft:mossy_cobblestone", new Pos(1, 9, 1));
		assertTrue(found(BaseSignatures.evaluate(b, false, false, ALL, CFG)).contains(Signature.SPAWNER_FARM));
		assertFalse(found(BaseSignatures.evaluate(b, true, true, ALL, CFG)).contains(Signature.SPAWNER_FARM));
	}
}
