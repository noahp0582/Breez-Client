package breezeclient.breezeclient.core.settings;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
	enum Mode { A, B, C }

	@Test
	void numberClampsOnSet() {
		NumberSetting s = new NumberSetting("n", 5, 0, 10);
		s.set(999.0);
		assertEquals(10.0, s.get());
		s.set(-4.0);
		assertEquals(0.0, s.get());
	}

	@Test
	void numberFromJsonOutOfRangeClamps() {
		NumberSetting s = new NumberSetting("n", 5, 0, 10);
		s.fromJson(new JsonPrimitive(500));
		assertEquals(10.0, s.get());
	}

	@Test
	void numberFromJsonWrongTypeIgnored() {
		NumberSetting s = new NumberSetting("n", 5, 0, 10);
		s.fromJson(new JsonPrimitive("banana"));
		assertEquals(5.0, s.get());
	}

	@Test
	void enumNextWraps() {
		EnumSetting<Mode> s = new EnumSetting<>("e", Mode.C);
		s.next();
		assertEquals(Mode.A, s.get());
	}

	@Test
	void enumFromJsonUnknownIgnored() {
		EnumSetting<Mode> s = new EnumSetting<>("e", Mode.B);
		s.fromJson(new JsonPrimitive("ZZZ"));
		assertEquals(Mode.B, s.get());
		s.fromJson(new JsonPrimitive("C"));
		assertEquals(Mode.C, s.get());
	}

	@Test
	void stringListCaseInsensitive() {
		StringListSetting s = new StringListSetting("l", List.of());
		s.add("Steve");
		assertTrue(s.contains("sTEVE"));
		s.add("steve");
		assertEquals(1, s.get().size());
		s.remove("STEVE");
		assertFalse(s.contains("steve"));
	}

	@Test
	void boolRoundTrip() {
		BoolSetting s = new BoolSetting("b", false);
		s.set(true);
		BoolSetting t = new BoolSetting("b", false);
		t.fromJson(s.toJson());
		assertTrue(t.get());
	}

	@Test
	void colorRoundTrip() {
		ColorSetting s = new ColorSetting("c", 0xFF112233);
		ColorSetting t = new ColorSetting("c", 0);
		t.fromJson(s.toJson());
		assertEquals(0xFF112233, t.get());
	}
}
