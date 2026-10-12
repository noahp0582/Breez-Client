package breezeclient.breezeclient.core.dig;

/** The box AutoDig clears, both corners inclusive. */
public record DigArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	/** Longest side and volume AutoDig accepts, so a mistyped corner cannot queue a mountain. */
	public static final int MAX_SIDE = 128;
	public static final long MAX_VOLUME = 500_000;

	public static DigArea of(int x1, int y1, int z1, int x2, int y2, int z2) {
		return new DigArea(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
	}

	public int sizeX() { return maxX - minX + 1; }
	public int sizeY() { return maxY - minY + 1; }
	public int sizeZ() { return maxZ - minZ + 1; }

	public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }

	public boolean tooBig() { return sizeX() > MAX_SIDE || sizeY() > MAX_SIDE || sizeZ() > MAX_SIDE || volume() > MAX_VOLUME; }

	public boolean contains(int x, int y, int z) {
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}

	/**
	 * Whether the 3x3 slab a wide pickaxe takes out stays inside the box. The slab is centred on the block and lies across
	 * the face that was hit, so it is perpendicular to {@code axis} (0 = X, 1 = Y, 2 = Z).
	 */
	public boolean planeInside(int x, int y, int z, int axis) {
		if (!contains(x, y, z)) return false;
		boolean fx = axis == 0 || (x > minX && x < maxX);
		boolean fy = axis == 1 || (y > minY && y < maxY);
		boolean fz = axis == 2 || (z > minZ && z < maxZ);
		return fx && fy && fz;
	}

	/** "x y z" (commas allowed) to three whole numbers, or null. */
	public static int[] parseCorner(String text) {
		if (text == null) return null;
		String[] p = text.trim().split("[\\s,]+");
		if (p.length != 3) return null;
		try {
			return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
