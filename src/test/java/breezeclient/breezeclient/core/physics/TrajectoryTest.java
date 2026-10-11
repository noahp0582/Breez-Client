package breezeclient.breezeclient.core.physics;

import breezeclient.breezeclient.core.physics.Trajectory.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrajectoryTest {
	@Test
	void withoutGravityOrDragItFliesStraight() {
		var r = Trajectory.simulate(new Point(0, 0, 0), new Point(1, 0, 0), 0, 1, 5, null);
		assertEquals(5, r.points().size());
		assertEquals(5.0, r.points().get(4).x(), 1e-9);
		assertEquals(0.0, r.points().get(4).y(), 1e-9);
		assertNull(r.landing());
	}

	@Test
	void gravityIsAppliedAfterTheMoveAndDragBeforeIt() {
		// tick 1 moves by the initial velocity, then v = v * 0.99 - 0.03
		var r = Trajectory.simulate(new Point(0, 10, 0), new Point(0, 0, 0), 0.03, 0.99, 2, null);
		assertEquals(10.0, r.points().get(0).y(), 1e-9);
		assertEquals(10.0 - 0.03, r.points().get(1).y(), 1e-9);
	}

	@Test
	void anArcRisesThenFalls() {
		var r = Trajectory.simulate(new Point(0, 0, 0), new Point(1, 0.5, 0), 0.03, 0.99, 80, null);
		double peak = 0;
		for (Point p : r.points()) peak = Math.max(peak, p.y());
		assertTrue(peak > 1.0);
		assertTrue(r.points().get(r.points().size() - 1).y() < peak);
	}

	@Test
	void stopsAtTheFirstCollision() {
		Trajectory.Collider floor = (a, b) -> b.y() <= 0 ? new Point(b.x(), 0, b.z()) : null;
		var r = Trajectory.simulate(new Point(0, 3, 0), new Point(1, 0, 0), 0.5, 1, 50, floor);
		assertNotNull(r.landing());
		assertEquals(0.0, r.landing().y(), 1e-9);
		assertEquals(r.landing(), r.points().get(r.points().size() - 1));
		assertTrue(r.points().size() < 50);
	}
}
