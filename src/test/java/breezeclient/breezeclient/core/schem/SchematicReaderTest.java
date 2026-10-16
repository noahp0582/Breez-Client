package breezeclient.breezeclient.core.schem;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchematicReaderTest {
	private static Map<String, Object> m(Object... kv) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
		return out;
	}

	private static SchematicData.Block find(SchematicData d, int x, int y, int z) {
		return d.blocks().stream().filter(b -> b.x() == x && b.y() == y && b.z() == z).findFirst().orElse(null);
	}

	@Test
	void spongeV2() throws IOException {
		// 2x1x2: (0,0,0)=stone (1,0,0)=air (0,0,1)=oak_stairs (1,0,1)=stone ; varints, ids: 0 air 1 stone 2 stairs
		byte[] data = {1, 0, 2, 1};
		Map<String, Object> root = m("Version", 2, "Width", (short) 2, "Height", (short) 1, "Length", (short) 2,
			"Palette", m("minecraft:air", 0, "minecraft:stone", 1, "minecraft:oak_stairs[facing=north]", 2), "BlockData", data);
		SchematicData d = SchematicReader.read(NbtTestWriter.write(root, true), "x.schem");
		assertEquals(3, d.blocks().size());
		assertEquals("minecraft:stone", find(d, 0, 0, 0).state());
		assertNull(find(d, 1, 0, 0));
		assertEquals("minecraft:oak_stairs[facing=north]", find(d, 0, 0, 1).state());
		assertEquals(2, d.sizeX());
		assertEquals(2, d.sizeZ());
	}

	@Test
	void spongeV3NestedBlocks() throws IOException {
		Map<String, Object> blocks = m("Palette", m("minecraft:air", 0, "minecraft:dirt", 1), "Data", new byte[] {1, 0});
		Map<String, Object> root = m("Schematic", m("Version", 3, "Width", (short) 2, "Height", (short) 1, "Length", (short) 1, "Blocks", blocks));
		SchematicData d = SchematicReader.read(NbtTestWriter.write(root, true), "x.schem");
		assertEquals(1, d.blocks().size());
		assertEquals("minecraft:dirt", d.blocks().get(0).state());
	}

	@Test
	void spongeMultiByteVarint() throws IOException {
		Map<String, Object> palette = new LinkedHashMap<>();
		palette.put("minecraft:air", 0);
		palette.put("minecraft:glass", 200);
		byte[] data = {(byte) 0xC8, 0x01}; // 200
		Map<String, Object> root = m("Width", (short) 1, "Height", (short) 1, "Length", (short) 1, "Palette", palette, "BlockData", data);
		SchematicData d = SchematicReader.read(NbtTestWriter.write(root, false), "x.schem");
		assertEquals("minecraft:glass", d.blocks().get(0).state());
	}

	@Test
	void vanillaStructureNbt() throws IOException {
		List<Object> palette = new ArrayList<>();
		palette.add(m("Name", "minecraft:air"));
		palette.add(m("Name", "minecraft:oak_log", "Properties", m("axis", "y")));
		palette.add(m("Name", "minecraft:oak_stairs", "Properties", m("half", "bottom", "facing", "east")));
		List<Object> blocks = new ArrayList<>();
		blocks.add(m("pos", List.of(0, 0, 0), "state", 1));
		blocks.add(m("pos", List.of(1, 0, 0), "state", 2));
		blocks.add(m("pos", List.of(0, 1, 0), "state", 0));
		Map<String, Object> root = m("size", List.of(2, 2, 1), "palette", palette, "blocks", blocks);
		SchematicData d = SchematicReader.read(NbtTestWriter.write(root, true), "house.nbt");
		assertEquals(2, d.blocks().size());
		assertEquals("minecraft:oak_log[axis=y]", find(d, 0, 0, 0).state());
		assertEquals("minecraft:oak_stairs[facing=east,half=bottom]", find(d, 1, 0, 0).state()); // sorted props
	}

	@Test
	void litematicPacksBitsAndNormalizesOrigin() throws IOException {
		// palette: air, stone, glass -> 2 bits per entry. region 2x1x2 at position (10,5,10).
		// index order: x fastest, then z, then y. entries: [stone, air, glass, stone] = 1,0,2,1
		long packed = 1L | (0L << 2) | (2L << 4) | (1L << 6);
		List<Object> palette = new ArrayList<>();
		palette.add(m("Name", "minecraft:air"));
		palette.add(m("Name", "minecraft:stone"));
		palette.add(m("Name", "minecraft:glass"));
		Map<String, Object> region = m("Position", m("x", 10, "y", 5, "z", 10), "Size", m("x", 2, "y", 1, "z", 2),
			"BlockStatePalette", palette, "BlockStates", new long[] {packed});
		Map<String, Object> root = m("Regions", m("main", region));
		SchematicData d = SchematicReader.read(NbtTestWriter.write(root, true), "t.litematic");
		assertEquals(3, d.blocks().size());
		assertEquals("minecraft:stone", find(d, 0, 0, 0).state());
		assertNull(find(d, 1, 0, 0));
		assertEquals("minecraft:glass", find(d, 0, 0, 1).state());
		assertEquals("minecraft:stone", find(d, 1, 0, 1).state());
	}

	@Test
	void litematicEntriesSpanningLongBoundary() throws IOException {
		// 5 palette entries -> 3 bits; 22 entries cross the 64-bit boundary at entry 21 (bits 63..65)
		List<Object> palette = new ArrayList<>();
		palette.add(m("Name", "minecraft:air"));
		for (String n : List.of("stone", "dirt", "glass", "sand")) palette.add(m("Name", "minecraft:" + n));
		int count = 22;
		long[] arr = new long[2];
		int[] ids = new int[count];
		for (int i = 0; i < count; i++) {
			ids[i] = 1 + (i % 4);
			long bitIndex = (long) i * 3;
			int li = (int) (bitIndex >>> 6), off = (int) (bitIndex & 63);
			arr[li] |= ((long) ids[i]) << off;
			if (off + 3 > 64) arr[li + 1] |= ((long) ids[i]) >>> (64 - off);
		}
		Map<String, Object> region = m("Position", m("x", 0, "y", 0, "z", 0), "Size", m("x", count, "y", 1, "z", 1),
			"BlockStatePalette", palette, "BlockStates", arr);
		SchematicData d = SchematicReader.read(NbtTestWriter.write(m("Regions", m("r", region)), true), "t.litematic");
		assertEquals(count, d.blocks().size());
		String[] names = {"", "stone", "dirt", "glass", "sand"};
		for (int i = 0; i < count; i++) assertEquals("minecraft:" + names[ids[i]], find(d, i, 0, 0).state(), "entry " + i);
	}

	@Test
	void garbageIsAnIOException() {
		assertThrows(IOException.class, () -> SchematicReader.read(new byte[] {1, 2, 3, 4}, "x.schem"));
		assertThrows(IOException.class, () -> SchematicReader.read(NbtTestWriter.write(m("hello", 1), true), "x.schem"));
	}

	@Test
	void spongeNamedDotSchematicStillLoads() throws IOException {
		Map<String, Object> root = m("Version", 2, "Width", (short) 1, "Height", (short) 1, "Length", (short) 1,
			"Palette", m("minecraft:stone", 0), "BlockData", new byte[] {0});
		assertEquals(1, SchematicReader.read(NbtTestWriter.write(root, true), "old-name.schematic").blocks().size());
	}

	@Test
	void oldMcEditSchematicSaysHowToConvert() throws IOException {
		Map<String, Object> root = m("Width", (short) 1, "Height", (short) 1, "Length", (short) 1, "Materials", "Alpha",
			"Blocks", new byte[] {1}, "Data", new byte[] {0});
		IOException e = assertThrows(IOException.class, () -> SchematicReader.read(NbtTestWriter.write(root, true), "castle.schematic"));
		assertTrue(e.getMessage().contains("MCEdit"), e.getMessage());
	}

	@Test
	void truncatedBlockDataIsAnIOException() throws IOException {
		Map<String, Object> root = m("Width", (short) 4, "Height", (short) 1, "Length", (short) 1, "Palette", m("minecraft:air", 0, "minecraft:stone", 1), "BlockData", new byte[] {1});
		assertThrows(IOException.class, () -> SchematicReader.read(NbtTestWriter.write(root, true), "x.schem"));
	}

	@Test
	void linearOrderIsLayerThenSerpentine() {
		List<SchematicData.Block> b = new ArrayList<>();
		for (int z = 0; z < 2; z++) for (int x = 0; x < 3; x++) b.add(new SchematicData.Block(x, 0, z, "minecraft:stone"));
		b.add(new SchematicData.Block(0, 1, 0, "minecraft:stone"));
		List<SchematicData.Block> ordered = new SchematicData(3, 2, 2, b).linearOrder();
		assertEquals(7, ordered.size());
		assertEquals(0, ordered.get(0).x()); // row z=0 ascending x
		assertEquals(2, ordered.get(2).x());
		assertEquals(2, ordered.get(3).x()); // row z=1 descending x (serpentine)
		assertEquals(0, ordered.get(5).x());
		assertEquals(1, ordered.get(6).y()); // next layer last
	}
}
