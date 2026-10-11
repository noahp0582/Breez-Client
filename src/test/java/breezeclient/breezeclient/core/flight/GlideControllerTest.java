package breezeclient.breezeclient.core.flight;

import breezeclient.breezeclient.core.flight.GlideController.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlideControllerTest {
	private static GlideController controller() { return new GlideController(35f, -40f, 1.6, 0.9); }

	@Test
	void startsByDiving() {
		var c = controller();
		assertEquals(35f, c.nextPitch(0.5, false));
		assertEquals(Phase.DIVE, c.phase());
	}

	@Test
	void climbsOnceFastEnoughAndDivesAgainWhenSlow() {
		var c = controller();
		assertEquals(35f, c.nextPitch(1.2, false));
		assertEquals(-40f, c.nextPitch(1.7, false));
		assertEquals(-40f, c.nextPitch(1.2, false)); // still above the dive threshold: keeps climbing
		assertEquals(35f, c.nextPitch(0.8, false));
	}

	@Test
	void tooCloseToTheGroundForcesAClimb() {
		var c = controller();
		assertEquals(-40f, c.nextPitch(0.4, true));
		assertEquals(Phase.CLIMB, c.phase());
	}

	@Test
	void diveThresholdNeverExceedsTheClimbThreshold() {
		var c = new GlideController(30f, -30f, 1.0, 2.0);
		assertEquals(-30f, c.nextPitch(1.0, false));
		assertEquals(30f, c.nextPitch(0.9, false));
	}
}
