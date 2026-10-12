package breezeclient.breezeclient.core.humanize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RotationsTest {
	@Test
	void wrapDegreesRange() {
		assertEquals(-170f, Rotations.wrapDegrees(190f), 1e-4);
		assertEquals(170f, Rotations.wrapDegrees(-190f), 1e-4);
		assertEquals(-180f, Rotations.wrapDegrees(180f), 1e-4);
		assertEquals(0f, Rotations.wrapDegrees(720f), 1e-4);
	}

	@Test
	void quantumGrowsWithSensitivity() {
		assertTrue(Rotations.quantum(0.0) > 0f);
		assertTrue(Rotations.quantum(0.5) > Rotations.quantum(0.0));
		assertTrue(Rotations.quantum(1.0) > Rotations.quantum(0.5));
	}
}
