package breezeclient.breezeclient.core.humanize;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AimMotionTest {
	private static final float Q = Rotations.quantum(0.5);
	private static final double DT = 1.0 / 60;

	/** Runs one turn to the end and returns the speed (degrees per second) of every frame. */
	private static List<Double> run(AimMotion m, Rotation from, Rotation to, double max, double dt, Rotation[] end) {
		List<Double> speeds = new ArrayList<>();
		Rotation cur = from;
		for (int i = 0; i < 2000 && !AimMotion.arrived(cur, to, Q); i++) {
			Rotation next = m.advance(cur, to, max, dt, Q);
			speeds.add(Math.hypot(Rotations.wrapDegrees(next.yaw() - cur.yaw()), next.pitch() - cur.pitch()) / dt);
			cur = next;
		}
		end[0] = cur;
		return speeds;
	}

	@Test
	void reachesTheTargetWithinAMouseStep() {
		for (long seed = 0; seed < 40; seed++) {
			Rotation[] end = new Rotation[1];
			Rotation to = new Rotation(-140f, 35f);
			List<Double> s = run(new AimMotion(new Random(seed)), new Rotation(20f, -10f), to, 300, DT, end);
			assertTrue(AimMotion.arrived(end[0], to, Q), "seed " + seed + " ended at " + end[0]);
			assertTrue(s.size() < 60 * 4, "seed " + seed + " took " + s.size() + " frames");
		}
	}

	@Test
	void takesTheShortWayAcrossTheYawSeam() {
		AimMotion m = new AimMotion(new Random(3));
		Rotation cur = new Rotation(350f, 0f), to = new Rotation(10f, 0f);
		double travelled = 0;
		for (int i = 0; i < 600 && !AimMotion.arrived(cur, to, Q); i++) {
			Rotation next = m.advance(cur, to, 300, DT, Q);
			travelled += Math.abs(next.yaw() - cur.yaw());
			cur = next;
		}
		assertTrue(AimMotion.arrived(cur, to, Q));
		assertTrue(travelled < 40, "went the long way round: " + travelled);
	}

	@Test
	void everyStepIsAWholeNumberOfMouseSteps() {
		AimMotion m = new AimMotion(new Random(7));
		Rotation cur = new Rotation(0f, 0f), to = new Rotation(77.3f, -41.9f);
		for (int i = 0; i < 600 && !AimMotion.arrived(cur, to, Q); i++) {
			Rotation next = m.advance(cur, to, 280, DT, Q);
			double sy = (next.yaw() - cur.yaw()) / Q, sp = (next.pitch() - cur.pitch()) / Q;
			assertEquals(Math.rint(sy), sy, 1e-3);
			assertEquals(Math.rint(sp), sp, 1e-3);
			cur = next;
		}
	}

	@Test
	void speedsUpCruisesAndSlowsDown() {
		Rotation[] end = new Rotation[1];
		List<Double> s = run(new AimMotion(new Random(11)), new Rotation(0f, 0f), new Rotation(170f, 0f), 300, DT, end);
		double peak = s.stream().mapToDouble(Double::doubleValue).max().orElse(0);
		assertTrue(s.get(0) < peak * 0.5, "starts from rest, first frame " + s.get(0) + " peak " + peak);
		assertTrue(s.get(s.size() - 1) < peak * 0.5, "slows into the target");
		// the middle of a long turn holds one speed: many frames close to the peak
		long nearPeak = s.stream().filter(v -> v > peak * 0.9).count();
		assertTrue(nearPeak >= 5, "no steady stretch: " + nearPeak + " frames near " + peak);
		assertTrue(peak <= 300 * 1.3 + Q / DT, "peak " + peak + " above the allowed cruise");
	}

	@Test
	void theSteadySpeedIsDifferentFromTurnToTurn() {
		AimMotion m = new AimMotion(new Random(5));
		List<Double> peaks = new ArrayList<>();
		Rotation a = new Rotation(0f, 0f), b = new Rotation(160f, 10f);
		for (int turn = 0; turn < 8; turn++) {
			Rotation[] end = new Rotation[1];
			List<Double> s = run(m, turn % 2 == 0 ? a : b, turn % 2 == 0 ? b : a, 300, DT, end);
			peaks.add(s.stream().mapToDouble(Double::doubleValue).max().orElse(0));
			m.reset();
		}
		double min = peaks.stream().mapToDouble(Double::doubleValue).min().orElse(0), max = peaks.stream().mapToDouble(Double::doubleValue).max().orElse(0);
		assertTrue(max - min > 40, "turns all cruise alike: " + peaks);
		for (int i = 1; i < peaks.size(); i++) assertNotEquals(peaks.get(i - 1), peaks.get(i), 1.0, "turn " + i + " repeated the speed before it");
	}

	@Test
	void frameRateDoesNotChangeHowLongATurnTakes() {
		Rotation[] end = new Rotation[1];
		Rotation from = new Rotation(0f, 0f), to = new Rotation(120f, 20f);
		double slow = run(new AimMotion(new Random(9)), from, to, 300, 1.0 / 30, end).size() / 30.0;
		double fast = run(new AimMotion(new Random(9)), from, to, 300, 1.0 / 144, end).size() / 144.0;
		assertEquals(slow, fast, 0.12, "30 fps took " + slow + " s, 144 fps took " + fast + " s");
	}

	@Test
	void pitchStaysInRangeAndAnArrivedCameraDoesNotMove() {
		AimMotion m = new AimMotion(new Random(2));
		Rotation cur = new Rotation(0f, 80f), to = new Rotation(40f, 90f);
		for (int i = 0; i < 600 && !AimMotion.arrived(cur, to, Q); i++) {
			cur = m.advance(cur, to, 300, DT, Q);
			assertTrue(cur.pitch() <= 90f && cur.pitch() >= -90f);
		}
		assertEquals(cur, m.advance(cur, to, 300, DT, Q));
	}
}
