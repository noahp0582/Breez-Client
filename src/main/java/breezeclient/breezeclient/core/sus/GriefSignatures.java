package breezeclient.breezeclient.core.sus;

import breezeclient.breezeclient.core.nav.Pos;
import breezeclient.breezeclient.core.sus.BaseSignatures.Finding;
import breezeclient.breezeclient.core.sus.BaseSignatures.Signature;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recognizes bases that have been griefed or raided, and lavacasts. A wrecked base no longer has the storage rooms and
 * farms the other signatures look for, so this looks for the two things that stay behind together: signs that someone
 * lived there (workstations, crafted building blocks, an ender chest, redstone) and signs of what was done to it (TNT
 * left unlit, fire, obsidian piled on it, every chest gone while the workstations stand).
 */
public final class GriefSignatures {
	private GriefSignatures() {}

	public static final String COBBLESTONE = "minecraft:cobblestone";
	public static final String TNT = "minecraft:tnt";
	public static final String OBSIDIAN = "minecraft:obsidian";
	public static final String CRYING_OBSIDIAN = "minecraft:crying_obsidian";
	public static final Set<String> FIRE = Set.of("minecraft:fire", "minecraft:soul_fire");

	/** Blocks a player puts down to live somewhere and that raiders have no reason to take. */
	public static final Set<String> WORKSTATIONS = Set.of("minecraft:crafting_table", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
		"minecraft:enchanting_table", "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil", "minecraft:brewing_stand",
		"minecraft:respawn_anchor", "minecraft:lodestone");

	/** Cobblestone above sea level needed for a lavacast, and how tall it has to stand. */
	static final int CAST_BLOCKS = 80, CAST_HEIGHT = 16;
	/** A nether portal frame is 10 to 14 obsidian; this many more than that is somebody burying something. */
	static final int OBSIDIAN_PILE = 24;
	static final int STRIPPED_WORKSTATIONS = 4;

	/** Block ids the scanner must report on top of {@link BaseSignatures#scanIds()}. */
	public static Set<String> scanIds() {
		Set<String> s = new LinkedHashSet<>(WORKSTATIONS);
		s.add(COBBLESTONE);
		s.add(TNT);
		s.add(OBSIDIAN);
		s.add(CRYING_OBSIDIAN);
		s.addAll(FIRE);
		return s;
	}

	private static List<Pos> all(Map<String, List<Pos>> blocks, Set<String> ids) {
		List<Pos> out = new ArrayList<>();
		for (String id : ids) out.addAll(blocks.getOrDefault(id, List.of()));
		return out;
	}

	private static Finding box(Signature s, double score, String detail, List<Pos> ps) {
		int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
		for (Pos p : ps) {
			x1 = Math.min(x1, p.x());
			y1 = Math.min(y1, p.y());
			z1 = Math.min(z1, p.z());
			x2 = Math.max(x2, p.x());
			y2 = Math.max(y2, p.y());
			z2 = Math.max(z2, p.z());
		}
		return new Finding(s, score, detail, new Pos(x1, y1, z1), new Pos(x2, y2, z2));
	}

	/**
	 * @param blocks        block id to positions found in the chunk
	 * @param naturalNearby true in or next to a natural structure: a desert temple has TNT, a village has workstations
	 * @param fireIsNatural true where fire burns by itself (the Nether), so it says nothing
	 * @param seaLevel      cobblestone and obsidian below this are ignored: lava meeting water underground makes both
	 */
	public static List<Finding> evaluate(Map<String, List<Pos>> blocks, boolean naturalNearby, boolean fireIsNatural, Set<Signature> enabled, int seaLevel) {
		List<Finding> out = new ArrayList<>();

		if (enabled.contains(Signature.LAVACAST)) {
			List<Pos> cast = new ArrayList<>();
			int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
			for (Pos p : blocks.getOrDefault(COBBLESTONE, List.of())) {
				if (p.y() < seaLevel) continue;
				cast.add(p);
				low = Math.min(low, p.y());
				high = Math.max(high, p.y());
			}
			// a village house or a wall is wide and low; a lavacast is a lot of cobblestone standing tall
			if (cast.size() >= CAST_BLOCKS && high - low + 1 >= CAST_HEIGHT) {
				double score = Math.min(60, 34 + (cast.size() - CAST_BLOCKS) / 10.0);
				out.add(box(Signature.LAVACAST, score, cast.size() + " cobblestone standing " + (high - low + 1) + " blocks tall", cast));
			}
		}

		if (enabled.contains(Signature.GRIEFED) && !naturalNearby) {
			List<Pos> work = all(blocks, WORKSTATIONS);
			List<Pos> building = all(blocks, BaseSignatures.BUILDING_BLOCKS);
			List<Pos> containers = all(blocks, BaseSignatures.CONTAINERS);
			List<Pos> ender = blocks.getOrDefault(BaseSignatures.ENDER_CHEST_ID, List.of());
			List<Pos> redstone = all(blocks, BaseSignatures.REDSTONE_PARTS);
			List<Pos> evidence = new ArrayList<>();
			int lived = 0;
			if (work.size() >= 2) lived++;
			if (building.size() >= 4) lived++;
			if (!containers.isEmpty()) lived++;
			if (!ender.isEmpty()) lived++;
			if (redstone.size() >= 3) lived++;

			List<String> damage = new ArrayList<>();
			List<Pos> tnt = blocks.getOrDefault(TNT, List.of());
			if (!tnt.isEmpty()) {
				damage.add(tnt.size() + " unlit TNT");
				evidence.addAll(tnt);
			}
			List<Pos> fire = all(blocks, FIRE);
			if (!fireIsNatural && fire.size() >= 3) {
				damage.add(fire.size() + " fires");
				evidence.addAll(fire);
			}
			List<Pos> obsidian = new ArrayList<>();
			for (Pos p : blocks.getOrDefault(OBSIDIAN, List.of())) if (p.y() >= seaLevel) obsidian.add(p);
			// crying obsidian means a ruined portal, which is natural
			if (obsidian.size() >= OBSIDIAN_PILE && blocks.getOrDefault(CRYING_OBSIDIAN, List.of()).isEmpty()) {
				damage.add(obsidian.size() + " obsidian piled up");
				evidence.addAll(obsidian);
			}
			boolean stripped = work.size() >= STRIPPED_WORKSTATIONS && containers.isEmpty();
			if (stripped) damage.add(work.size() + " workstations and not one chest left");

			if (!damage.isEmpty() && (lived >= 1 || stripped)) {
				evidence.addAll(work);
				evidence.addAll(building);
				evidence.addAll(containers);
				evidence.addAll(ender);
				double score = Math.min(60, 26 + 7.0 * damage.size() + 3.0 * lived);
				out.add(box(Signature.GRIEFED, score, String.join(", ", damage), evidence));
			}
		}
		return out;
	}
}
