package breezeclient.breezeclient.core.gui;

import breezeclient.breezeclient.core.Category;
import breezeclient.breezeclient.core.Module;
import breezeclient.breezeclient.core.ModuleManager;
import breezeclient.breezeclient.core.settings.BoolSetting;
import breezeclient.breezeclient.core.settings.NumberSetting;
import breezeclient.breezeclient.core.settings.StringListSetting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuiCoreTest {
	// ColorMath ------------------------------------------------------------------------------------------------

	@Test
	void hsvRoundTripForPrimaryAndAccentColors() {
		for (int c : new int[] {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF913DE2, 0xFF808080, 0xFFFFFFFF, 0xFF000000}) {
			double[] h = ColorMath.toHsv(c);
			int back = ColorMath.fromHsv(h[0], h[1], h[2], 255);
			assertEquals(ColorMath.red(c), ColorMath.red(back), 1, Integer.toHexString(c));
			assertEquals(ColorMath.green(c), ColorMath.green(back), 1, Integer.toHexString(c));
			assertEquals(ColorMath.blue(c), ColorMath.blue(back), 1, Integer.toHexString(c));
		}
	}

	@Test
	void hexParseAndFormat() {
		assertEquals(0xFF913DE2, ColorMath.parseHex("#913DE2"));
		assertEquals(0xFF913DE2, ColorMath.parseHex("913de2"));
		assertEquals(0x80913DE2, ColorMath.parseHex("0x80913DE2"));
		assertNull(ColorMath.parseHex("12345"));
		assertNull(ColorMath.parseHex("zzzzzz"));
		assertNull(ColorMath.parseHex(null));
		assertEquals("#913DE2", ColorMath.toHex(0xFF913DE2));
		assertEquals("#80913DE2", ColorMath.toHex(0x80913DE2));
	}

	@Test
	void mixAndFade() {
		assertEquals(0xFF7F7F7F, ColorMath.mix(0xFF000000, 0xFFFFFFFF, 0.5) | 0, 0x01010101);
		assertEquals(0x80, ColorMath.alpha(ColorMath.fade(0xFFFFFFFF, 0.5)), 1);
		assertEquals(0xFF000000, ColorMath.mix(0xFF000000, 0xFFFFFFFF, -5));
		assertEquals(0xFFFFFFFF, ColorMath.mix(0xFF000000, 0xFFFFFFFF, 5));
	}

	// Fuzzy ------------------------------------------------------------------------------------------------------

	@Test
	void fuzzyRanksExactPrefixSubstringSubsequence() {
		assertTrue(Fuzzy.score("chest", "chest") > Fuzzy.score("chest", "chest_minecart"));
		assertTrue(Fuzzy.score("chest", "chest_minecart") > Fuzzy.score("chest", "ender_chest"));
		assertTrue(Fuzzy.score("chest", "ender_chest") > Fuzzy.score("cht", "chest"));
		assertEquals(-1, Fuzzy.score("xyz", "chest"));
		assertEquals(0, Fuzzy.score("", "anything"));
	}

	@Test
	void fuzzyIgnoresCaseNamespaceAndUnderscores() {
		assertEquals(Fuzzy.score("diamond ore", "minecraft:diamond_ore"), 1000);
		assertTrue(Fuzzy.score("DIAMOND", "minecraft:deepslate_diamond_ore") > 0);
	}

	@Test
	void fuzzyRankLimitsAndOrders() {
		List<String> items = List.of("minecraft:stone", "minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:dirt");
		List<String> r = Fuzzy.rank("stone", items, 2);
		assertEquals(List.of("minecraft:stone", "minecraft:stone_bricks"), r);
		assertTrue(Fuzzy.rank("qqq", items, 5).isEmpty());
	}

	// ScrollMath ---------------------------------------------------------------------------------------------------

	@Test
	void scrollClampsAndThumb() {
		assertEquals(0, ScrollMath.maxScroll(100, 200));
		assertEquals(0, ScrollMath.clamp(-30, 500, 200));
		assertEquals(300, ScrollMath.clamp(999, 500, 200));
		assertEquals(100, ScrollMath.thumbHeight(100, 200, 100, 10));
		assertEquals(40, ScrollMath.thumbHeight(500, 200, 100, 10), 1e-9);
		assertEquals(10, ScrollMath.thumbHeight(100000, 200, 100, 10), 1e-9);
	}

	@Test
	void thumbOffsetAndInverseAgree() {
		double content = 800, view = 200, track = 200;
		double thumb = ScrollMath.thumbHeight(content, view, track, 10);
		for (double s : new double[] {0, 100, 300, 600}) {
			double off = ScrollMath.thumbOffset(s, content, view, track, thumb);
			assertEquals(s, ScrollMath.scrollForThumb(off, content, view, track, thumb), 1e-6);
		}
		assertEquals(0, ScrollMath.thumbOffset(0, 100, 200, 100, 100));
	}

	// GuiTheme ---------------------------------------------------------------------------------------------------

	@Test
	void themeJsonRoundTripAndGarbageIsIgnored() {
		GuiTheme a = new GuiTheme();
		a.accent.set(0xFF123456);
		a.scale.set(1.5);
		a.rounded.set(false);
		a.moduleListSide.set(GuiTheme.Side.LEFT);
		GuiTheme b = new GuiTheme();
		b.fromJsonString(a.toJsonString());
		assertEquals(0xFF123456, b.accent.get());
		assertEquals(1.5, b.scale.get());
		assertFalse(b.rounded.get());
		assertEquals(GuiTheme.Side.LEFT, b.moduleListSide.get());

		GuiTheme c = new GuiTheme();
		c.fromJsonString("{not json");
		c.fromJsonString("[1,2]");
		c.fromJsonString("{\"Scale\": 99, \"Accent color\": \"red\"}");
		assertEquals(2.0, c.scale.get()); // clamped
		assertEquals(0xFF913DE2, c.accent.get()); // wrong type ignored
	}

	@Test
	void themeSettingsAllHaveGroupAndDescription() {
		for (var s : new GuiTheme().settings()) {
			assertFalse(s.group().equals("General"), s.name());
			assertFalse(s.description().isBlank(), s.name());
		}
	}

	// Setting metadata + module flags --------------------------------------------------------------------------

	static class Probe extends Module {
		final BoolSetting parent = add(new BoolSetting("Parent", false), "Main", "A parent toggle");
		final NumberSetting child = add(new NumberSetting("Child", 3, 0, 10), "Main", "Only shown with parent", parent::get);
		Probe() { super("Probe", Category.MISC); }
	}

	@Test
	void settingVisibilityFollowsCondition() {
		Probe p = new Probe();
		assertTrue(p.parent.visible());
		assertFalse(p.child.visible());
		p.parent.set(true);
		assertTrue(p.child.visible());
		assertEquals("Main", p.child.group());
		assertEquals("Only shown with parent", p.child.description());
	}

	@Test
	void moduleFlagsPersist() {
		Probe a = new Probe();
		a.setChatFeedback(true);
		a.setVisibleInHud(false);
		a.setToggleOnBindRelease(true);
		Probe b = new Probe();
		b.fromJson(a.toJson());
		assertTrue(b.chatFeedback());
		assertFalse(b.visibleInHud());
		assertTrue(b.toggleOnBindRelease());
	}

	@Test
	void holdToEnableFollowsTheKey() {
		ModuleManager m = new ModuleManager();
		Probe p = new Probe();
		p.setKey(70);
		p.setToggleOnBindRelease(true);
		m.register(p);
		m.onKeyState(70, true);
		assertTrue(p.enabled());
		m.onKeyState(70, true);
		assertTrue(p.enabled());
		m.onKeyState(70, false);
		assertFalse(p.enabled());
	}

	@Test
	void holdToEnableReleaseWorksEvenWhenInputBlocked() {
		ModuleManager m = new ModuleManager();
		Probe p = new Probe();
		p.setKey(70);
		p.setToggleOnBindRelease(true);
		m.register(p);
		m.onKeyState(70, true);
		m.setInputBlocked(true);
		m.onKeyState(70, false);
		assertFalse(p.enabled(), "a held module must never stay on after the key is released");
	}

	@Test
	void stringListKindAndDefaultTracking() {
		StringListSetting s = new StringListSetting("Blocks", List.of("minecraft:stone")).kind(StringListSetting.Kind.BLOCK);
		assertEquals(StringListSetting.Kind.BLOCK, s.kind());
		assertTrue(s.isDefault());
		s.add("minecraft:dirt");
		assertFalse(s.isDefault());
		s.reset();
		assertTrue(s.isDefault());
	}

	@Test
	void moduleListenerSeesToggles() {
		Probe p = new Probe();
		int[] seen = {0};
		Module.setListener((mod, on) -> seen[0] += on ? 1 : 10);
		try {
			p.setEnabled(true);
			p.setEnabled(true);
			p.setEnabled(false);
			assertEquals(11, seen[0]);
		} finally {
			Module.setListener(null);
		}
	}
}
