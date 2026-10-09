package breezeclient.breezeclient.client.world;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BlockIds {
	private BlockIds() {}

	/** Parses "minecraft:chest" or "chest"; returns null for unknown ids (and for air). */
	public static Block parse(String id) {
		String s = id.contains(":") ? id : "minecraft:" + id;
		Identifier ident = Identifier.tryParse(s.toLowerCase());
		if (ident == null || !BuiltInRegistries.BLOCK.containsKey(ident)) return null;
		Block b = BuiltInRegistries.BLOCK.getValue(ident);
		return b == Blocks.AIR ? null : b;
	}

	public static Set<Block> resolve(List<String> ids) {
		Set<Block> out = new HashSet<>();
		for (String id : ids) {
			Block b = parse(id);
			if (b != null) out.add(b);
		}
		return out;
	}

	public static String idOf(Block b) { return BuiltInRegistries.BLOCK.getKey(b).toString(); }
}
