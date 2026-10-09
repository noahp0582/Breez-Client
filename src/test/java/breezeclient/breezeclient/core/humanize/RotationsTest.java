package breezeclient.breezeclient.core.humanize;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RotationsTest {
	private static final float Q = Rotations.quantum(0.5);

	@Test
	void wrapDegreesRange() {
		assertEquals(-170f, Rotations.wrapDegrees(190f), 1e-4);
		assertEquals(170f, Rotations.wrapDegrees(-190f), 1e-4);
		assertEquals(-180f, Rotations.wrapDegrees(180f), 1e-4);
		assertEquals(0f, Rotations.wrapDegrees(720f), 1e-4);
	}

	@Test
	void shortestYawPathAcrossSeam() {
		Random rng = new Random(1);
		Rotation cur = new Rotation(350f, 0f);
		Rotation target = new Rotation(10f, 0f);
		Rotation first = Rotations.step(cur, target, 30f, Q, rng);
		assertTrue(Rotations.wrapDegrees(first.yaw() - cur.yaw()) > 0f, "first step must go +, not -340");
		for (int i = 0; i < 60; i++) cur = Rotations.step(cur, target, 30f, Q, rng);
		assertEquals(0f, Math.abs(Rotations.wrapDegrees(cur.yaw() - 10f)), Q);
	}

	@Test
	void neverOvershoots() {
		Random rng = new Random(2);
		Random gen = new Random(3);
		for (int i = 0; i < 200; i++) {
			Rotation cur = new Rotation(gen.nextFloat() * 720 - 360, gen.nextFloat() * 180 - 90);
			Rotation target = new Rotation(gen.nextFloat() * 720 - 360, gen.nextFloat() * 180 - 90);
			for (int n = 0; n < 5; n++) {
				float beforeY = Math.abs(Rotations.wrapDegrees(target.yaw() - cur.yaw()));
				float beforeP = Math.abs(target.pitch() - cur.pitch());
				Rotation next = Rotations.step(cur, target, 25f, Q, rng);
				assertTrue(Math.abs(Rotations.wrapDegrees(target.yaw() - next.yaw())) <= beforeY + 1e-3);
				assertTrue(Math.abs(target.pitch() - next.pitch()) <= beforeP + 1e-3);
				cur = next;
			}
		}
	}

	@Test
	void stepsAreQuantumMultiples() {
		Random rng = new Random(4);
		Rotation cur = new Rotation(0f, 0f);
		Rotation target = new Rotation(120f, 40f);
		for (int i = 0; i < 10; i++) {
			Rotation next = Rotations.step(cur, target, 30f, Q, rng);
			float dy = next.yaw() - cur.yaw();
			float dp = next.pitch() - cur.pitch();
			assertEquals(0f, Math.abs(dy / Q - Math.round(dy / Q)), 1e-3);
			assertEquals(0f, Math.abs(dp / Q - Math.round(dp / Q)), 1e-3);
			cur = next;
		}
	}

	@Test
	void convergesWithinBudget() {
		Random rng = new Random(5);
		Rotation cur = new Rotation(0f, 0f);
		Rotation target = new Rotation(180f, 0f);
		int steps = 0;
		while (Math.abs(Rotations.wrapDegrees(target.yaw() - cur.yaw())) >= Q && steps < 60) {
			cur = Rotations.step(cur, target, 30f, Q, rng);
			steps++;
		}
		assertTrue(steps < 60, "should converge in under 60 steps, took " + steps);
	}

	@Test
	void pitchClamped() {
		Random rng = new Random(6);
		Rotation cur = new Rotation(0f, 89f);
		Rotation target = new Rotation(0f, 200f);
		for (int i = 0; i < 20; i++) cur = Rotations.step(cur, target, 30f, Q, rng);
		assertTrue(cur.pitch() <= 90f);
	}

	@Test
	void deterministicWithSeed() {
		Rotation a = new Rotation(0f, 0f), b = new Rotation(0f, 0f);
		Rotation t = new Rotation(90f, 20f);
		Random r1 = new Random(9), r2 = new Random(9);
		for (int i = 0; i < 10; i++) {
			a = Rotations.step(a, t, 20f, Q, r1);
			b = Rotations.step(b, t, 20f, Q, r2);
		}
		assertEquals(a, b);
	}

	@Test
	void unchangedWhenAlreadyThere() {
		Rotation t = new Rotation(33f, 12f);
		assertEquals(t, Rotations.step(t, t, 20f, Q, new Random(1)));
	}
}
