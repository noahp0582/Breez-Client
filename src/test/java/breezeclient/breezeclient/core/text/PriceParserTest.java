package breezeclient.breezeclient.core.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceParserTest {
	@Test
	void plainAndSuffixedAmounts() {
		assertEquals(500, PriceParser.parse("500"), 1e-9);
		assertEquals(1250, PriceParser.parse("$1,250"), 1e-9);
		assertEquals(1500, PriceParser.parse("$1.5K"), 1e-9);
		assertEquals(2_300_000, PriceParser.parse("2.3m"), 1e-3);
		assertEquals(4e9, PriceParser.parse("$ 4B"), 1e-3);
		assertTrue(Double.isNaN(PriceParser.parse("free")));
		assertTrue(Double.isNaN(PriceParser.parse(null)));
	}

	@Test
	void aLetterThatStartsAWordIsNotASuffix() {
		assertEquals(64, PriceParser.parse("64 blocks"), 1e-9);
		assertEquals(3, PriceParser.parse("3 minutes"), 1e-9);
		assertEquals(3000, PriceParser.parse("3k each"), 1e-9);
	}

	@Test
	void lorePrefersThePriceLine() {
		assertEquals(12_500, PriceParser.fromLore(List.of("Seller: Steve", "Expires in 23h 4m", "Price: $12.5K", "Click to buy")), 1e-6);
		assertEquals(800, PriceParser.fromLore(List.of("Stack of 64", "Cost 800")), 1e-9);
		assertEquals(99, PriceParser.fromLore(List.of("Worth 5", "$99")), 1e-9);
	}

	@Test
	void loreWithoutAPriceIsNotANumber() {
		assertTrue(Double.isNaN(PriceParser.fromLore(List.of("Seller: Steve", "Expires in 23h 4m"))));
		assertTrue(Double.isNaN(PriceParser.fromLore(List.of("Click to buy"))));
		assertTrue(Double.isNaN(PriceParser.fromLore(List.of())));
	}

	@Test
	void format() {
		assertEquals("$500", PriceParser.format(500));
		assertEquals("$1.5K", PriceParser.format(1500));
		assertEquals("$2M", PriceParser.format(2_000_000));
	}
}
