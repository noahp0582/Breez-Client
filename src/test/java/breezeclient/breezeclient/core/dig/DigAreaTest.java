package breezeclient.breezeclient.core.dig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DigAreaTest {
	@Test
	void cornersInAnyOrderGiveTheSameBox() {
		DigArea a = DigArea.of(5, 70, -3, 1, 64, 2);
		assertEquals(new DigArea(1, 64, -3, 5, 70, 2), a);
		assertEquals(5 * 7 * 6, a.volume());
		assertTrue(a.contains(1, 64, -3));
		assertTrue(a.contains(5, 70, 2));
		assertFalse(a.contains(6, 70, 2));
		assertFalse(a.contains(1, 63, -3));
	}

	@Test
	void widePickaxeSlabMustStayInside() {
		DigArea a = DigArea.of(0, 0, 0, 4, 4, 4);
		// hit from above: the slab is horizontal, so only x and z matter
		assertTrue(a.planeInside(2, 4, 2, 1));
		assertTrue(a.planeInside(1, 0, 3, 1));
		assertFalse(a.planeInside(0, 2, 2, 1));
		assertFalse(a.planeInside(2, 2, 4, 1));
		// hit from the side along X: the slab spans y and z
		assertTrue(a.planeInside(0, 2, 2, 0));
		assertFalse(a.planeInside(0, 4, 2, 0));
		assertFalse(a.planeInside(9, 2, 2, 0));
	}

	@Test
	void aTwoWideBoxNeverFitsASlabAcrossIt() {
		DigArea a = DigArea.of(0, 0, 0, 1, 5, 5);
		assertFalse(a.planeInside(0, 2, 2, 1));
		assertTrue(a.planeInside(0, 2, 2, 0));
	}

	@Test
	void sizeLimits() {
		assertFalse(DigArea.of(0, 0, 0, 63, 63, 63).tooBig());
		assertTrue(DigArea.of(0, 0, 0, 200, 1, 1).tooBig());
		assertTrue(DigArea.of(0, 0, 0, 100, 100, 100).tooBig());
	}

	@Test
	void parseCorner() {
		assertArrayEquals(new int[] {1, -60, 30}, DigArea.parseCorner(" 1 -60  30 "));
		assertArrayEquals(new int[] {1, 2, 3}, DigArea.parseCorner("1, 2, 3"));
		assertNull(DigArea.parseCorner(""));
		assertNull(DigArea.parseCorner("1 2"));
		assertNull(DigArea.parseCorner("a b c"));
		assertNull(DigArea.parseCorner(null));
	}
}
