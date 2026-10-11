package breezeclient.breezeclient.core.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * The client side of the SOCKS5, SOCKS4/4a and HTTP CONNECT handshakes, over plain streams so it can be tested without
 * a network. After {@code connect} returns, the streams carry the tunnelled connection to {@code host:port}.
 */
public final class ProxyHandshake {
	public enum Type { SOCKS5, SOCKS4, HTTP }

	private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

	private ProxyHandshake() {}

	/** @param user may be null or empty for an open proxy */
	public static void connect(Type type, InputStream in, OutputStream out, String host, int port, String user, String pass) throws IOException {
		if (host == null || host.isBlank() || port < 1 || port > 65535) throw new IOException("bad target " + host + ":" + port);
		switch (type) {
			case SOCKS5 -> socks5(in, out, host, port, user, pass);
			case SOCKS4 -> socks4(in, out, host, port, user);
			case HTTP -> http(in, out, host, port, user, pass);
		}
	}

	private static int read(InputStream in) throws IOException {
		int b = in.read();
		if (b < 0) throw new IOException("proxy closed the connection");
		return b;
	}

	private static void readFully(InputStream in, byte[] buf) throws IOException {
		for (int i = 0; i < buf.length; i++) buf[i] = (byte) read(in);
	}

	private static byte[] ipv4(String host) {
		var m = IPV4.matcher(host);
		if (!m.matches()) return null;
		byte[] b = new byte[4];
		for (int i = 0; i < 4; i++) {
			int v = Integer.parseInt(m.group(i + 1));
			if (v > 255) return null;
			b[i] = (byte) v;
		}
		return b;
	}

	private static void socks5(InputStream in, OutputStream out, String host, int port, String user, String pass) throws IOException {
		boolean auth = user != null && !user.isEmpty();
		out.write(auth ? new byte[] {5, 2, 0, 2} : new byte[] {5, 1, 0});
		out.flush();
		if (read(in) != 5) throw new IOException("not a SOCKS5 proxy");
		int method = read(in);
		if (method == 2) {
			if (!auth) throw new IOException("the proxy wants a username and password");
			byte[] u = user.getBytes(StandardCharsets.UTF_8), p = (pass == null ? "" : pass).getBytes(StandardCharsets.UTF_8);
			if (u.length > 255 || p.length > 255) throw new IOException("username or password too long for SOCKS5");
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			b.write(1);
			b.write(u.length);
			b.write(u);
			b.write(p.length);
			b.write(p);
			out.write(b.toByteArray());
			out.flush();
			read(in); // version of the sub-negotiation
			if (read(in) != 0) throw new IOException("proxy login refused");
		} else if (method != 0) {
			throw new IOException("the proxy accepts none of our login methods");
		}
		ByteArrayOutputStream req = new ByteArrayOutputStream();
		req.write(5);
		req.write(1); // CONNECT
		req.write(0);
		byte[] v4 = ipv4(host);
		if (v4 != null) {
			req.write(1);
			req.write(v4);
		} else if (host.indexOf(':') >= 0) {
			byte[] v6 = java.net.InetAddress.getByName(host).getAddress(); // an IPv6 literal: no DNS involved
			req.write(4);
			req.write(v6);
		} else {
			byte[] name = host.getBytes(StandardCharsets.US_ASCII);
			if (name.length > 255) throw new IOException("host name too long");
			req.write(3);
			req.write(name.length);
			req.write(name);
		}
		req.write(port >> 8);
		req.write(port & 0xFF);
		out.write(req.toByteArray());
		out.flush();
		if (read(in) != 5) throw new IOException("bad SOCKS5 reply");
		int rep = read(in);
		read(in); // reserved
		int atyp = read(in);
		int skip = switch (atyp) {
			case 1 -> 4;
			case 4 -> 16;
			case 3 -> read(in);
			default -> throw new IOException("bad SOCKS5 address type " + atyp);
		};
		readFully(in, new byte[skip + 2]);
		if (rep != 0) throw new IOException("proxy refused the connection: " + socks5Error(rep));
	}

	private static String socks5Error(int code) {
		return switch (code) {
			case 1 -> "general failure";
			case 2 -> "not allowed by ruleset";
			case 3 -> "network unreachable";
			case 4 -> "host unreachable";
			case 5 -> "connection refused";
			case 6 -> "TTL expired";
			case 7 -> "command not supported";
			case 8 -> "address type not supported";
			default -> "code " + code;
		};
	}

	private static void socks4(InputStream in, OutputStream out, String host, int port, String user) throws IOException {
		ByteArrayOutputStream req = new ByteArrayOutputStream();
		req.write(4);
		req.write(1);
		req.write(port >> 8);
		req.write(port & 0xFF);
		byte[] v4 = ipv4(host);
		if (v4 != null) req.write(v4);
		else req.write(new byte[] {0, 0, 0, 1}); // SOCKS4a: the name follows
		req.write((user == null ? "" : user).getBytes(StandardCharsets.UTF_8));
		req.write(0);
		if (v4 == null) {
			req.write(host.getBytes(StandardCharsets.US_ASCII));
			req.write(0);
		}
		out.write(req.toByteArray());
		out.flush();
		read(in); // reply version (0)
		int status = read(in);
		readFully(in, new byte[6]);
		if (status != 0x5A) throw new IOException("proxy refused the connection (SOCKS4 code " + status + ")");
	}

	private static void http(InputStream in, OutputStream out, String host, int port, String user, String pass) throws IOException {
		String target = (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + port;
		StringBuilder req = new StringBuilder("CONNECT ").append(target).append(" HTTP/1.1\r\nHost: ").append(target).append("\r\n");
		if (user != null && !user.isEmpty()) {
			String cred = Base64.getEncoder().encodeToString((user + ":" + (pass == null ? "" : pass)).getBytes(StandardCharsets.UTF_8));
			req.append("Proxy-Authorization: Basic ").append(cred).append("\r\n");
		}
		req.append("\r\n");
		out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
		out.flush();
		// read the response headers up to the blank line, one byte at a time so no tunnelled data is consumed
		StringBuilder head = new StringBuilder();
		while (head.length() < 8192) {
			head.append((char) read(in));
			int n = head.length();
			if (n >= 4 && head.charAt(n - 4) == '\r' && head.charAt(n - 3) == '\n' && head.charAt(n - 2) == '\r' && head.charAt(n - 1) == '\n') break;
		}
		String status = head.toString().split("\r\n", 2)[0];
		String[] parts = status.split(" ");
		if (parts.length < 2 || !parts[1].equals("200")) throw new IOException("proxy refused the connection: " + status);
	}
}
