package breezeclient.breezeclient.core.humanize;

import breezeclient.breezeclient.core.humanize.AimAssistMath.Delta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimAssistMathTest {
	private static Delta step(float yaw, float pitch, float ty, float tp) {
		return AimAssistMath.step(yaw, pitch, ty, tp, 0.2, 3f, 1f, 0.5f, 0f);
	}

	@Test
	void insideTheDeadZoneNothingMoves() {
		assertTrue(step(10f, 0f, 10.5f, 0.3f).isZero());
	}

	@Test
	void nudgesTowardTheTargetByAFractionNotAllTheWay() {
		Delta d = step(0f, 0f, 20f, 0f);
		assertTrue(d.yaw() > 0f && d.yaw() < 20f);
		assertEquals(3f, d.yaw(), 1e-4); // 20 * 0.2 = 4, capped at 3
	}

	@Test
	void smallErrorsGetSmallSteps() {
		Delta d = step(0f, 0f, 5f, 0f);
		assertTrue(d.yaw() > 0f && d.yaw() <= 1.01f); // 5 * 0.2 = 1, faded because it is near the dead zone
	}

	@Test
	void takesTheShortWayAroundTheWrap() {
		Delta d = step(170f, 0f, -170f, 0f); // 20 degrees to the right across +-180
		assertTrue(d.yaw() > 0f);
		Delta back = step(-170f, 0f, 170f, 0f);
		assertTrue(back.yaw() < 0f);
	}

	@Test
	void pitchIsScaledDownAndCapped() {
		Delta d = AimAssistMath.step(0f, 0f, 0f, 30f, 0.5, 3f, 1f, 0.5f, 0f);
		assertEquals(3f, d.pitch(), 1e-4);
		Delta half = AimAssistMath.step(0f, 0f, 0f, 8f, 0.5, 5f, 1f, 0.5f, 0f);
		assertEquals(2f, half.pitch(), 1e-4); // 8 * 0.5 * 0.5
	}

	@Test
	void deltasAreWholeMouseSteps() {
		float q = 0.15f;
		Delta d = AimAssistMath.step(0f, 0f, 12f, 4f, 0.2, 3f, 1f, 0.5f, q);
		assertEquals(0f, Math.abs(d.yaw() / q - Math.round(d.yaw() / q)), 1e-3);
		assertEquals(0f, Math.abs(d.pitch() / q - Math.round(d.pitch() / q)), 1e-3);
	}

	@Test
	void perFrameFractionsAddUpToOneTick() {
		double perTick = 0.14;
		double left = 1;
		for (int i = 0; i < 7; i++) left *= 1 - AimAssistMath.perFrame(perTick, 20.0 / 140); // 7 frames at 140 fps = 1 tick
		assertEquals(1 - perTick, left, 1e-9);
		assertEquals(0.0, AimAssistMath.perFrame(perTick, 0), 1e-12);
	}

	@Test
	void subStepMovementsAccumulateIntoWholeSteps() {
		float q = 0.15f;
		float[] carry = {0f};
		float moved = 0f;
		for (int i = 0; i < 10; i++) {
			float out = AimAssistMath.quantize(0.05f, 100f, q, carry);
			assertEquals(0f, Math.abs(out / q - Math.round(out / q)), 1e-3);
			moved += out;
		}
		assertEquals(0.45f, moved, 1e-3); // 0.5 wanted, 0.05 still in the carry
		assertEquals(0f, AimAssistMath.quantize(0.3f, 0.1f, q, carry), 1e-6); // would pass the target
	}

	@Test
	void frameStepsMoveAboutAsFarAsTickSteps() {
		// one tick's worth of frames should land close to a single tick step
		float yaw = 0f;
		float[] carry = {0f};
		double dt = 20.0 / 144;
		for (int i = 0; i < 144 / 20; i++) {
			Delta d = AimAssistMath.step(yaw, 0f, 20f, 0f, AimAssistMath.perFrame(0.14, dt), (float) (2.5 * dt), 1.2f, 0.4f, 0f);
			yaw += AimAssistMath.quantize(d.yaw(), 20f - yaw, 0.15f, carry);
		}
		Delta tick = AimAssistMath.step(0f, 0f, 20f, 0f, 0.14, 2.5f, 1.2f, 0.4f, 0f);
		assertEquals(tick.yaw(), yaw, 0.4f);
	}

	@Test
	void neverOvershootsAndConvergesWithoutOscillating() {
		float yaw = 0f, pitch = 0f;
		float prevErr = Float.MAX_VALUE;
		for (int i = 0; i < 200; i++) {
			Delta d = AimAssistMath.step(yaw, pitch, 33f, 10f, 0.18, 3f, 1f, 0.5f, 0.15f);
			yaw += d.yaw();
			pitch += d.pitch();
			float err = Math.abs(33f - yaw);
			assertTrue(yaw <= 33f + 1e-3, "yaw overshot: " + yaw);
			assertTrue(err <= prevErr + 1e-3);
			prevErr = err;
		}
		assertTrue(Math.abs(33f - yaw) <= 1.5f, "settled near the target: " + yaw);
	}
}
