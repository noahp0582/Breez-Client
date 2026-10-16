package breezeclient.breezeclient.core.schem;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
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

	/** A found file and its name: the path below its search folder with '/' separators, e.g. "v2/house.litematic". */
	public record Entry(String name, Path path) {
		public String fileName() { return path.getFileName().toString(); }
	}

	/**
	 * Every schematic in the folders and their subfolders (symbolic links followed), sorted by name. When the same name
	 * turns up in two search folders the first one in {@code dirs} wins. Unreadable folders are skipped.
	 */
	public static List<Entry> scan(List<Path> dirs) {
		Map<String, Entry> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Path d : dirs) {
			if (!Files.isDirectory(d)) continue;
			try {
				Files.walkFileTree(d, EnumSet.of(FileVisitOption.FOLLOW_LINKS), MAX_DEPTH, new SimpleFileVisitor<>() {
					@Override
					public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
						if (attrs.isRegularFile() && isSchematic(p.getFileName().toString())) {
							String name = d.relativize(p).toString().replace('\\', '/');
							byName.putIfAbsent(name, new Entry(name, p));
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path p, IOException e) {
						return FileVisitResult.CONTINUE; // an unreadable subfolder or a link loop is skipped, the rest still counts
					}
				});
			} catch (IOException ignored) {
				// the folder itself is unreadable
			}
		}
		return new ArrayList<>(byName.values());
	}

	/**
	 * The file a typed name means, case ignored, extension optional: a full name such as "v2/house" first, then a bare
	 * file name, where the least deeply nested match wins. Null when nothing matches.
	 */
	public static Entry find(List<Entry> all, String typed) {
		String want = typed.trim().replace('\\', '/');
		if (want.startsWith("./")) want = want.substring(2);
		if (want.isEmpty()) return null;
		List<Entry> byDepth = new ArrayList<>(all);
		byDepth.sort(Comparator.comparingInt(e -> e.name().split("/").length));
		for (Entry e : byDepth) if (e.name().equalsIgnoreCase(want) || stripExt(e.name()).equalsIgnoreCase(want)) return e;
		for (Entry e : byDepth) if (e.fileName().equalsIgnoreCase(want) || stripExt(e.fileName()).equalsIgnoreCase(want)) return e;
		return null;
	}

	private static String stripExt(String n) {
		int slash = n.lastIndexOf('/'), dot = n.lastIndexOf('.');
		return dot > slash + 1 ? n.substring(0, dot) : n;
	}
}