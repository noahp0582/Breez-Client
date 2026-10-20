package breezeclient.breezeclient.core.physics;

import breezeclient.breezeclient.core.physics.Trajectory.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PearlAimTest {
	/** Ground at y=0 everywhere, plus a 5x5 tower from x/z 8..13 whose top is at y=12. */
	private static Point groundHit(Point a, Point b) {
		boolean inTower = b.x() >= 8 && b.x() < 13 && b.z() >= 8 && b.z() < 13;
		double top = inTower ? 12 : 0;
		if (b.y() > top) return null;
		if (a.y() >= top) { // came in through the top face
			double t = (a.y() - top) / (a.y() - b.y());
			return new Point(a.x() + (b.x() - a.x()) * t, top, a.z() + (b.z() - a.z()) * t);
		}
		return a; // hit a side: stop where it was
	}

	@Test
	void tickOrderIsGravityThenDragThenMove() {
		var r = PearlAim.fly(new Point(0, 10, 0), new Point(0, 0, 0), 2, null);
		assertEquals(10 - 0.03 * 0.99, r.points().get(0).y(), 1e-9);
		assertEquals(10 - 0.03 * 0.99 - (0.03 * 0.99 + 0.03) * 0.99, r.points().get(1).y(), 1e-9);
	}

	@Test
	void velocityMatchesVanillaDirections() {
		Point south = PearlAim.velocity(0, 0); // yaw 0 faces +z
		assertEquals(1.5, south.z(), 1e-9);
		assertEquals(0, south.x(), 1e-9);
		Point up = PearlAim.velocity(0, -90);
		assertEquals(1.5, up.y(), 1e-9);
		assertEquals(90f, PearlAim.yawTo(new Point(0, 0, 0), -5, 0), 1e-4); // yaw 90 faces -x
	}

	@Test
	void landsOnTopOfATallTower() {
		Point start = new Point(0.5, 1.52, 0.5);
		var shot = PearlAim.solve(start, 10.5, 10.5, PearlAimTest::groundHit, l -> Math.abs(l.y() - 12) < 0.05, 0.3);
		assertNotNull(shot);
		assertEquals(12, shot.landing().y(), 0.05);
		assertTrue(shot.error() < 0.3, "error " + shot.error());
		assertTrue(shot.pitch() < 0, "has to throw upward, pitch " + shot.pitch());
	}

	@Test
	void givesUpWhenTheSpotIsOutOfRange() {
		Point start = new Point(0.5, 1.52, 0.5);
		assertNull(PearlAim.solve(start, 400.5, 0.5, PearlAimTest::groundHit, l -> true, 0.3));
	}
}
