package breezeclient.breezeclient.core.net;

import breezeclient.breezeclient.core.net.ProxyHandshake.Type;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ProxyHandshakeTest {
	private static byte[] sent(Type t, byte[] reply, String host, int port, String user, String pass) throws IOException {
		var out = new ByteArrayOutputStream();
		ProxyHandshake.connect(t, new ByteArrayInputStream(reply), out, host, port, user, pass);
		return out.toByteArray();
	}

	@Test
	void socks5WithoutLoginSendsAConnectWithTheDomainName() throws IOException {
		byte[] reply = {5, 0, /* connect reply: */ 5, 0, 0, 1, 0, 0, 0, 0, 0, 0};
		byte[] sent = sent(Type.SOCKS5, reply, "mc.example.org", 25565, null, null);
		assertArrayEquals(new byte[] {5, 1, 0}, java.util.Arrays.copyOfRange(sent, 0, 3));
		assertEquals(5, sent[3]);
		assertEquals(1, sent[4]); // CONNECT
		assertEquals(3, sent[6]); // domain name
		assertEquals(14, sent[7]);
		assertEquals(25565, ((sent[sent.length - 2] & 0xFF) << 8) | (sent[sent.length - 1] & 0xFF));
	}

	@Test
	void socks5SendsAnIpv4LiteralAsAnAddress() throws IOException {
		byte[] reply = {5, 0, 5, 0, 0, 1, 0, 0, 0, 0, 0, 0};
		byte[] sent = sent(Type.SOCKS5, reply, "10.1.2.3", 80, null, null);
		assertEquals(1, sent[6]);
		assertArrayEquals(new byte[] {10, 1, 2, 3}, java.util.Arrays.copyOfRange(sent, 7, 11));
	}

	@Test
	void socks5LoginIsSentWhenTheProxyAsksForIt() throws IOException {
		byte[] reply = {5, 2, 1, 0, 5, 0, 0, 1, 0, 0, 0, 0, 0, 0};
		byte[] sent = sent(Type.SOCKS5, reply, "a.b", 1, "bob", "pw");
		assertArrayEquals(new byte[] {5, 2, 0, 2}, java.util.Arrays.copyOfRange(sent, 0, 4));
		assertEquals(1, sent[4]);
		assertEquals(3, sent[5]);
		assertEquals('b', sent[6]);
	}

	@Test
	void socks5RefusalIsReported() {
		byte[] reply = {5, 0, 5, 5, 0, 1, 0, 0, 0, 0, 0, 0};
		var e = assertThrows(IOException.class, () -> sent(Type.SOCKS5, reply, "a.b", 1, null, null));
		assertTrue(e.getMessage().contains("refused"));
	}

	@Test
	void socks5LoginRefusalIsReported() {
		byte[] reply = {5, 2, 1, 1};
		assertThrows(IOException.class, () -> sent(Type.SOCKS5, reply, "a.b", 1, "bob", "bad"));
	}

	@Test
	void socks4aSendsTheNameAfterTheUser() throws IOException {
		byte[] reply = {0, 0x5A, 0, 0, 0, 0, 0, 0};
		byte[] sent = sent(Type.SOCKS4, reply, "mc.test", 25565, "u", null);
		assertEquals(4, sent[0]);
		assertEquals(1, sent[1]);
		assertArrayEquals(new byte[] {0, 0, 0, 1}, java.util.Arrays.copyOfRange(sent, 4, 8));
		assertEquals("mc.test", new String(sent, sent.length - 8, 7, StandardCharsets.US_ASCII));
	}

	@Test
	void httpConnectSucceedsOn200AndConsumesOnlyTheHeaders() throws IOException {
		byte[] reply = "HTTP/1.1 200 Connection established\r\nVia: x\r\n\r\nPAYLOAD".getBytes(StandardCharsets.US_ASCII);
		var in = new ByteArrayInputStream(reply);
		var out = new ByteArrayOutputStream();
		ProxyHandshake.connect(Type.HTTP, in, out, "mc.test", 25565, "bob", "pw");
		String req = out.toString(StandardCharsets.US_ASCII);
		assertTrue(req.startsWith("CONNECT mc.test:25565 HTTP/1.1\r\n"));
		assertTrue(req.contains("Proxy-Authorization: Basic Ym9iOnB3\r\n"));
		assertEquals("PAYLOAD", new String(in.readAllBytes(), StandardCharsets.US_ASCII));
	}

	@Test
	void httpConnectRefusalIsReported() {
		byte[] reply = "HTTP/1.1 407 Proxy Authentication Required\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
		var e = assertThrows(IOException.class, () -> sent(Type.HTTP, reply, "a.b", 1, null, null));
		assertTrue(e.getMessage().contains("407"));
	}

	@Test
	void aClosedConnectionAndBadTargetsFail() {
		assertThrows(IOException.class, () -> sent(Type.SOCKS5, new byte[0], "a.b", 1, null, null));
		assertThrows(IOException.class, () -> sent(Type.SOCKS5, new byte[0], "", 1, null, null));
		assertThrows(IOException.class, () -> sent(Type.SOCKS5, new byte[0], "a.b", 70000, null, null));
	}
}
