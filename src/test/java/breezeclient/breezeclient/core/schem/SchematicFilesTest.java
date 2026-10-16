package breezeclient.breezeclient.core.schem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchematicFilesTest {
	private static Path touch(Path p) throws IOException {
		Files.createDirectories(p.getParent());
		return Files.write(p, new byte[] {0});
	}

	private static List<String> names(List<SchematicFiles.Entry> es) {
		return es.stream().map(SchematicFiles.Entry::name).toList();
	}

	@Test
	void findsEveryFormatIncludingSubfolders(@TempDir Path dir) throws IOException {
		touch(dir.resolve("a.litematic"));
		touch(dir.resolve("b.SCHEM"));
		touch(dir.resolve("builds/castle/c.schematic"));
		touch(dir.resolve("d.nbt"));
		touch(dir.resolve("notes.txt"));
		touch(dir.resolve("e.litematic.zip"));
		assertEquals(List.of("a.litematic", "b.SCHEM", "builds/castle/c.schematic", "d.nbt"), names(SchematicFiles.scan(List.of(dir))));
	}

	@Test
	void stopsAtTheDepthLimit(@TempDir Path dir) throws IOException {
		touch(dir.resolve("1/2/3/ok.schem"));
		touch(dir.resolve("1/2/3/4/5/too-deep.schem"));
		assertEquals(List.of("1/2/3/ok.schem"), names(SchematicFiles.scan(List.of(dir))));
	}

	@Test
	void sameFileNameInTwoSubfoldersKeepsBoth(@TempDir Path dir) throws IOException {
		touch(dir.resolve("v1/house.litematic"));
		Path v2 = touch(dir.resolve("v2/house.litematic"));
		List<SchematicFiles.Entry> all = SchematicFiles.scan(List.of(dir));
		assertEquals(List.of("v1/house.litematic", "v2/house.litematic"), names(all));
		assertEquals(v2, SchematicFiles.find(all, "v2/house").path());
		assertEquals(v2, SchematicFiles.find(all, "V2\\HOUSE.litematic").path());
	}

	@Test
	void bareNamePicksTheLeastNestedFile(@TempDir Path dir) throws IOException {
		Path top = touch(dir.resolve("house.schem"));
		touch(dir.resolve("old/house.schem"));
		List<SchematicFiles.Entry> all = SchematicFiles.scan(List.of(dir));
		assertEquals(top, SchematicFiles.find(all, "house").path());
		assertEquals(top, SchematicFiles.find(all, "house.schem").path());
		assertNull(SchematicFiles.find(all, "barn"));
		assertNull(SchematicFiles.find(all, "  "));
	}

	@Test
	void firstFolderWinsForTheSameName(@TempDir Path dir) throws IOException {
		Path keep = touch(dir.resolve("first/house.schem"));
		touch(dir.resolve("second/house.schem"));
		List<SchematicFiles.Entry> all = SchematicFiles.scan(List.of(dir.resolve("first"), dir.resolve("second")));
		assertEquals(1, all.size());
		assertEquals(keep, all.get(0).path());
	}

	@Test
	void missingFoldersAreSkipped(@TempDir Path dir) throws IOException {
		touch(dir.resolve("x.nbt"));
		assertEquals(List.of("x.nbt"), names(SchematicFiles.scan(List.of(dir.resolve("nope"), dir))));
	}
}
