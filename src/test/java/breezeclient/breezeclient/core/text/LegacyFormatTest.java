package breezeclient.breezeclient.core.text;

import breezeclient.breezeclient.core.text.LegacyFormat.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFormatTest {
	@Test
	void plainTextIsOneSegment() {
		List<Segment> s = LegacyFormat.parse("Steve");
		assertEquals(1, s.size());
		assertEquals("Steve", s.get(0).text());
		assertEquals(-1, s.get(0).color());
	}

	@Test
	void colorCodeStartsANewSegment() {
		List<Segment> s = LegacyFormat.parse("&aGreen&cRed");
		assertEquals(2, s.size());
		assertEquals(0x55FF55, s.get(0).color());
		assertEquals("Green", s.get(0).text());
		assertEquals(0xFF5555, s.get(1).color());
	}

	@Test
	void stylesStackUntilReset() {
		List<Segment> s = LegacyFormat.parse("&l&oBoth&rPlain");
		assertEquals(2, s.size());
		assertTrue(s.get(0).bold());
		assertTrue(s.get(0).italic());
		assertFalse(s.get(1).bold());
		assertEquals(-1, s.get(1).color());
	}

	@Test
	void colorCodeClearsStylesLikeTheGame() {
		List<Segment> s = LegacyFormat.parse("&lBold&bAqua");
		assertTrue(s.get(0).bold());
		assertFalse(s.get(1).bold());
		assertEquals(0x55FFFF, s.get(1).color());
	}

	@Test
	void sectionSignWorksAndUnknownCodesStayAsText() {
		assertEquals(0xFFAA00, LegacyFormat.parse("§6Gold").get(0).color());
		assertEquals("a&zb", LegacyFormat.strip("a&zb"));
		assertEquals("trailing&", LegacyFormat.strip("trailing&"));
	}

	@Test
	void stripAndEmpty() {
		assertEquals("Hello World", LegacyFormat.strip("&aHello &lWorld"));
		assertTrue(LegacyFormat.parse("").isEmpty());
		assertTrue(LegacyFormat.parse(null).isEmpty());
	}

	@Test
	void replaceIgnoreCaseReplacesEveryOccurrence() {
		assertEquals("X joined, X left", LegacyFormat.replaceIgnoreCase("Noah joined, noah left", "NOAH", "X"));
		assertEquals("nothing", LegacyFormat.replaceIgnoreCase("nothing", "abc", "X"));
		assertEquals("same", LegacyFormat.replaceIgnoreCase("same", "", "X"));
	}
}
