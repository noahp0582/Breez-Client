package breezeclient.breezeclient.core.discord;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class IpcFrameTest {
	@Test
	void frameIsOpcodeLengthThenBodyLittleEndian() {
		byte[] f = IpcFrame.encode(1, "{}");
		assertEquals(10, f.length);
		assertArrayEquals(new byte[] {1, 0, 0, 0, 2, 0, 0, 0}, Arrays.copyOfRange(f, 0, 8));
		assertEquals("{}", new String(f, 8, 2, StandardCharsets.UTF_8));
	}

	@Test
	void headerRoundTripsAndClampsHugeLengths() {
		byte[] f = IpcFrame.encode(IpcFrame.PING, "hello");
		assertArrayEquals(new int[] {3, 5}, IpcFrame.header(Arrays.copyOfRange(f, 0, 8)));
		byte[] evil = {1, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F};
		assertEquals(64 * 1024, IpcFrame.header(evil)[1]);
		byte[] negative = {1, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
		assertEquals(0, IpcFrame.header(negative)[1]);
	}

	@Test
	void handshakeCarriesTheClientId() {
		var o = JsonParser.parseString(IpcFrame.handshake("12345")).getAsJsonObject();
		assertEquals(1, o.get("v").getAsInt());
		assertEquals("12345", o.get("client_id").getAsString());
	}

	@Test
	void activityHasTheFieldsDiscordExpects() {
		var o = JsonParser.parseString(IpcFrame.setActivity(42, "Playing", "Survival", 1700000000L, "n1")).getAsJsonObject();
		assertEquals("SET_ACTIVITY", o.get("cmd").getAsString());
		assertEquals("n1", o.get("nonce").getAsString());
		var args = o.getAsJsonObject("args");
		assertEquals(42, args.get("pid").getAsLong());
		var act = args.getAsJsonObject("activity");
		assertEquals("Playing", act.get("details").getAsString());
		assertEquals("Survival", act.get("state").getAsString());
		assertEquals(1700000000L, act.getAsJsonObject("timestamps").get("start").getAsLong());
	}

	@Test
	void blankLinesAreLeftOutAndLongOnesClipped() {
		var act = JsonParser.parseString(IpcFrame.setActivity(1, " ", "x".repeat(300), 0, "n")).getAsJsonObject().getAsJsonObject("args").getAsJsonObject("activity");
		assertFalse(act.has("details"));
		assertFalse(act.has("timestamps"));
		assertEquals(128, act.get("state").getAsString().length());
		assertEquals(2, IpcFrame.clip("a").length());
	}
}
