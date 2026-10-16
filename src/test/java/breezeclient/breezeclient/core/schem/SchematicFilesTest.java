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

	private static List<String> names(List<Path> ps) {
		return ps.stream().map(p -> p.getFileName().toString()).toList();
	}

	@Test
	void findsEveryFormatIncludingSubfolders(@TempDir Path dir) throws IOException {
		touch(dir.resolve("a.litematic"));
		touch(dir.resolve("b.SCHEM"));
		touch(dir.resolve("builds/castle/c.schematic"));
		touch(dir.resolve("d.nbt"));
		touch(dir.resolve("notes.txt"));
		touch(dir.resolve("e.litematic.zip"));
		assertEquals(List.of("a.litematic", "b.SCHEM", "c.schematic", "d.nbt"), names(SchematicFiles.scan(List.of(dir))));
	}

	@Test
	void stopsAtTheDepthLimit(@TempDir Path dir) throws IOException {
		touch(dir.resolve("1/2/3/ok.schem"));
		touch(dir.resolve("1/2/3/4/5/too-deep.schem"));
		assertEquals(List.of("ok.schem"), names(SchematicFiles.scan(List.of(dir))));
	}

	@Test
	void firstFolderThenShallowestWinsOnDuplicateNames(@TempDir Path dir) throws IOException {
		Path first = dir.resolve("first"), second = dir.resolve("second");
		Path keep = touch(first.resolve("house.schem"));
		touch(first.resolve("old/house.schem"));
		touch(second.resolve("house.schem"));
		assertEquals(List.of(keep), SchematicFiles.scan(List.of(first, second)));
	}

	@Test
	void missingFoldersAreSkipped(@TempDir Path dir) throws IOException {
		touch(dir.resolve("x.nbt"));
		assertEquals(List.of("x.nbt"), names(SchematicFiles.scan(List.of(dir.resolve("nope"), dir))));
	}
}
