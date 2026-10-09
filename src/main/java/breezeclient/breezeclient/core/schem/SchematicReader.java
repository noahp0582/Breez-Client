package breezeclient.breezeclient.core.schem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Reads .litematic, Sponge .schem (v2 and v3) and vanilla structure .nbt files. */
public final class SchematicReader {
	private SchematicReader() {}

	public static SchematicData read(byte[] data, String fileName) throws IOException {
		Map<String, Object> root = Nbt.readRoot(data);
		try {
			String n = fileName.toLowerCase(Locale.ROOT);
			if (n.endsWith(".litematic") || root.containsKey("Regions")) return litematic(root);
			if (n.endsWith(".schem") || root.containsKey("Schematic") || root.containsKey("BlockData")) return sponge(root);
			if (root.containsKey("palette") && root.containsKey("blocks")) return structure(root);
		} catch (RuntimeException e) {
			throw new IOException("could not parse schematic: " + e.getMessage(), e);
		}
		throw new IOException("unrecognized schematic format");
	}

	// vanilla structure .nbt ---------------------------------------------------------------------------------------

	static SchematicData structure(Map<String, Object> root) {
		List<Object> size = Nbt.list(root.get("size"));
		int sx = Nbt.intOf(size.get(0)), sy = Nbt.intOf(size.get(1)), sz = Nbt.intOf(size.get(2));
		List<String> palette = new ArrayList<>();
		for (Object o : Nbt.list(root.get("palette"))) palette.add(stateString(Nbt.map(o)));
		List<SchematicData.Block> blocks = new ArrayList<>();
		for (Object o : Nbt.list(root.get("blocks"))) {
			Map<String, Object> b = Nbt.map(o);
			List<Object> pos = Nbt.list(b.get("pos"));
			String state = palette.get(Nbt.intOf(b.get("state")));
			if (SchematicData.isAir(state)) continue;
			blocks.add(new SchematicData.Block(Nbt.intOf(pos.get(0)), Nbt.intOf(pos.get(1)), Nbt.intOf(pos.get(2)), state));
		}
		return new SchematicData(sx, sy, sz, blocks);
	}

	private static String stateString(Map<String, Object> entry) {
		String name = (String) entry.get("Name");
		Object props = entry.get("Properties");
		if (!(props instanceof Map<?, ?> pm) || pm.isEmpty()) return name;
		TreeMap<String, String> sorted = new TreeMap<>();
		for (Map.Entry<?, ?> e : pm.entrySet()) sorted.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
		StringBuilder sb = new StringBuilder(name).append('[');
		boolean first = true;
		for (Map.Entry<String, String> e : sorted.entrySet()) {
			if (!first) sb.append(',');
			sb.append(e.getKey()).append('=').append(e.getValue());
			first = false;
		}
		return sb.append(']').toString();
	}

	// Sponge .schem -------------------------------------------------------------------------------------------------

	static SchematicData sponge(Map<String, Object> top) {
		Map<String, Object> root = top.containsKey("Schematic") ? Nbt.map(top.get("Schematic")) : top;
		int w = Nbt.intOf(root.get("Width")), h = Nbt.intOf(root.get("Height")), l = Nbt.intOf(root.get("Length"));
		Map<String, Object> paletteTag;
		byte[] blockData;
		if (root.containsKey("Blocks")) { // v3
			Map<String, Object> blocksTag = Nbt.map(root.get("Blocks"));
			paletteTag = Nbt.map(blocksTag.get("Palette"));
			blockData = (byte[]) blocksTag.get("Data");
		} else { // v2
			paletteTag = Nbt.map(root.get("Palette"));
			blockData = (byte[]) root.get("BlockData");
		}
		Map<Integer, String> palette = new java.util.HashMap<>();
		for (Map.Entry<String, Object> e : paletteTag.entrySet()) palette.put(Nbt.intOf(e.getValue()), e.getKey());
		List<SchematicData.Block> blocks = new ArrayList<>();
		int[] cursor = {0};
		for (int i = 0; i < w * h * l; i++) {
			int id = varint(blockData, cursor);
			String state = palette.get(id);
			if (state == null) throw new IllegalArgumentException("palette id " + id + " missing");
			if (SchematicData.isAir(state)) continue;
			int x = i % w, z = (i / w) % l, y = i / (w * l);
			blocks.add(new SchematicData.Block(x, y, z, state));
		}
		return new SchematicData(w, h, l, blocks);
	}

	private static int varint(byte[] data, int[] cursor) {
		int value = 0, shift = 0;
		while (true) {
			if (cursor[0] >= data.length) throw new IllegalArgumentException("block data truncated");
			int b = data[cursor[0]++] & 0xFF;
			value |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) return value;
			shift += 7;
			if (shift > 35) throw new IllegalArgumentException("bad varint");
		}
	}

	// Litematica .litematic ----------------------------------------------------------------------------------------

	static SchematicData litematic(Map<String, Object> root) {
		Map<String, Object> regions = Nbt.map(root.get("Regions"));
		List<SchematicData.Block> all = new ArrayList<>();
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (Object ro : regions.values()) {
			Map<String, Object> r = Nbt.map(ro);
			Map<String, Object> pos = Nbt.map(r.get("Position")), size = Nbt.map(r.get("Size"));
			int px = Nbt.intOf(pos.get("x")), py = Nbt.intOf(pos.get("y")), pz = Nbt.intOf(pos.get("z"));
			int sx = Nbt.intOf(size.get("x")), sy = Nbt.intOf(size.get("y")), sz = Nbt.intOf(size.get("z"));
			int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
			int ox = px + (sx < 0 ? sx + 1 : 0), oy = py + (sy < 0 ? sy + 1 : 0), oz = pz + (sz < 0 ? sz + 1 : 0);
			List<String> palette = new ArrayList<>();
			for (Object po : Nbt.list(r.get("BlockStatePalette"))) palette.add(stateString(Nbt.map(po)));
			long[] states = (long[]) r.get("BlockStates");
			int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
			long mask = (1L << bits) - 1;
			for (int i = 0; i < ax * ay * az; i++) {
				long bitIndex = (long) i * bits;
				int li = (int) (bitIndex >>> 6), off = (int) (bitIndex & 63);
				if (li >= states.length) throw new IllegalArgumentException("block states truncated");
				long v = states[li] >>> off;
				if (off + bits > 64) {
					if (li + 1 >= states.length) throw new IllegalArgumentException("block states truncated");
					v |= states[li + 1] << (64 - off);
				}
				int id = (int) (v & mask);
				if (id >= palette.size()) throw new IllegalArgumentException("palette id " + id + " out of range");
				String state = palette.get(id);
				if (SchematicData.isAir(state)) continue;
				int x = ox + i % ax, z = oz + (i / ax) % az, y = oy + i / (ax * az);
				all.add(new SchematicData.Block(x, y, z, state));
			}
			minX = Math.min(minX, ox);
			minY = Math.min(minY, oy);
			minZ = Math.min(minZ, oz);
			maxX = Math.max(maxX, ox + ax);
			maxY = Math.max(maxY, oy + ay);
			maxZ = Math.max(maxZ, oz + az);
		}
		if (minX == Integer.MAX_VALUE) throw new IllegalArgumentException("no regions");
		List<SchematicData.Block> out = new ArrayList<>(all.size());
		for (SchematicData.Block b : all) out.add(new SchematicData.Block(b.x() - minX, b.y() - minY, b.z() - minZ, b.state()));
		return new SchematicData(maxX - minX, maxY - minY, maxZ - minZ, out);
	}
}
