package breezeclient.breezeclient.core.sus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SusColorTest {
	private static int red(int argb) { return (argb >> 16) & 0xFF; }
	private static int green(int argb) { return (argb >> 8) & 0xFF; }
	private static int blue(int argb) { return argb & 0xFF; }

	@Test
	void leastSusIsGreenAndMostSusIsRed() {
		assertEquals(0xFF00FF00, SusColor.gradient(0.0));
		assertEquals(0xFFFF0000, SusColor.gradient(1.0));
	}

	@Test
	void middleIsYellow() {
		assertEquals(0xFFFFFF00, SusColor.gradient(0.5));
	}

	@Test
	void greenFadesOutAndRedFadesInMonotonically() {
		int prevRed = -1, prevGreen = 256;
		for (int i = 0; i <= 100; i++) {
			int c = SusColor.gradient(i / 100.0);
			assertTrue(red(c) >= prevRed, "red must not decrease at " + i);
			assertTrue(green(c) <= prevGreen, "green must not increase at " + i);
			assertEquals(0, blue(c));
			assertEquals(0xFF, c >>> 24);
			prevRed = red(c);
			prevGreen = green(c);
		}
	}

	@Test
	void gradientClampsOutOfRangeInput() {
		assertEquals(SusColor.gradient(0.0), SusColor.gradient(-3));
		assertEquals(SusColor.gradient(1.0), SusColor.gradient(7));
		assertEquals(SusColor.gradient(0.0), SusColor.gradient(Double.NaN));
	}

	@Test
	void fractionRunsFromThresholdToTop() {
		assertEquals(0.0, SusColor.fraction(30, 30, 90), 1e-9);
		assertEquals(0.5, SusColor.fraction(60, 30, 90), 1e-9);
		assertEquals(1.0, SusColor.fraction(90, 30, 90), 1e-9);
	}

	@Test
	void fractionClampsAndSurvivesDegenerateRange() {
		assertEquals(0.0, SusColor.fraction(10, 30, 90), 1e-9);
		assertEquals(1.0, SusColor.fraction(500, 30, 90), 1e-9);
		assertEquals(0.0, SusColor.fraction(50, 30, 30), 1e-9);
	}
}
