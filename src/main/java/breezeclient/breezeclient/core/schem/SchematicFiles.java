package breezeclient.breezeclient.core.schem;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Finds schematic files in a list of folders, including their subfolders. */
public final class SchematicFiles {
	/** How many folder levels below each search folder are looked at (Litematica users keep files in subfolders). */
	public static final int MAX_DEPTH = 4;

	private SchematicFiles() {}

	public static boolean isSchematic(String fileName) {
		String l = fileName.toLowerCase(Locale.ROOT);
		return l.endsWith(".litematic") || l.endsWith(".schem") || l.endsWith(".schematic") || l.endsWith(".nbt");
	}

	/**
	 * Every schematic in the folders, sorted by file name. When a name appears twice the first folder in {@code dirs} wins,
	 * and within one folder the shallower file wins. Unreadable folders are skipped.
	 */
	public static List<Path> scan(List<Path> dirs) {
		Map<String, Path> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Path d : dirs) {
			if (!Files.isDirectory(d)) continue;
			List<Path> here = new ArrayList<>();
			try {
				Files.walkFileTree(d, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH, new SimpleFileVisitor<>() {
					@Override
					public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
						if (attrs.isRegularFile() && isSchematic(p.getFileName().toString())) here.add(p);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path p, IOException e) {
						return FileVisitResult.CONTINUE; // an unreadable subfolder is skipped, the rest still counts
					}
				});
			} catch (IOException ignored) {
				// the folder itself is unreadable
			}
			here.sort((a, b) -> Integer.compare(a.getNameCount(), b.getNameCount()));
			for (Path p : here) byName.putIfAbsent(p.getFileName().toString(), p);
		}
		return new ArrayList<>(byName.values());
	}
}
