package breezeclient.breezeclient.core.nav;

public record Pos(int x, int y, int z) {
	public double dist(double ox, double oy, double oz) {
		double dx = x - ox, dy = y - oy, dz = z - oz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
