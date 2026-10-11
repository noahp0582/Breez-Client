package breezeclient.breezeclient.client.integration;

import breezeclient.breezeclient.core.net.ProxyHandshake;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends Minecraft's server connection through a SOCKS or HTTP proxy. Minecraft's network code uses Netty and ignores the
 * JVM proxy settings, so each connection is pointed at a throwaway listener on the loopback address; that listener
 * opens the real connection through the proxy and copies bytes both ways. Nothing else in the game uses the proxy.
 */
public final class ProxyRelay {
	private static final Logger LOG = Logger.getLogger("breez-client");

	public record Config(ProxyHandshake.Type type, String host, int port, String user, String pass) {}

	private static volatile Supplier<Config> source = () -> null;
	private static volatile String lastError = "";

	private ProxyRelay() {}

	/** The module installs a supplier that returns the active configuration, or null when the proxy is off. */
	public static void setSource(Supplier<Config> s) { source = s; }

	public static String lastError() { return lastError; }

	/**
	 * @return {@code target} itself when no proxy is active (or for loopback targets); otherwise a loopback address that
	 *         tunnels to {@code target}. Fails closed: if the relay cannot start the connection fails rather than
	 *         going out directly.
	 */
	public static InetSocketAddress redirect(InetSocketAddress target) {
		Config c = source.get();
		if (c == null || target == null) return target;
		InetAddress a = target.getAddress();
		if (a != null && (a.isLoopbackAddress() || a.isAnyLocalAddress())) return target; // singleplayer and LAN-on-this-machine
		ServerSocket ss;
		try {
			ss = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		} catch (IOException e) {
			lastError = "could not start the proxy relay: " + e.getMessage();
			throw new IllegalStateException("Proxy: " + lastError, e);
		}
		Thread t = new Thread(() -> serve(ss, c, target), "breez-proxy-relay");
		t.setDaemon(true);
		t.start();
		return new InetSocketAddress(InetAddress.getLoopbackAddress(), ss.getLocalPort());
	}

	private static void serve(ServerSocket ss, Config c, InetSocketAddress target) {
		Socket client = null, upstream = null;
		try {
			ss.setSoTimeout(20_000);
			client = ss.accept();
			ss.close();
			client.setTcpNoDelay(true);
			upstream = new Socket();
			upstream.setTcpNoDelay(true);
			upstream.connect(new InetSocketAddress(c.host(), c.port()), 10_000);
			upstream.setSoTimeout(15_000); // only for the handshake
			ProxyHandshake.connect(c.type(), upstream.getInputStream(), upstream.getOutputStream(), target.getHostString(), target.getPort(), c.user(), c.pass());
			upstream.setSoTimeout(0);
			lastError = "";
			Socket cl = client, up = upstream;
			Thread back = new Thread(() -> pump(up, cl), "breez-proxy-down");
			back.setDaemon(true);
			back.start();
			pump(cl, up);
		} catch (IOException e) {
			lastError = e.getMessage() == null ? e.toString() : e.getMessage();
			LOG.log(Level.WARNING, "proxy connection failed: " + lastError);
		} finally {
			close(ss);
			close(client);
			close(upstream);
		}
	}

	/** Copies from one socket to the other until either side ends, then closes both so the other pump stops too. */
	private static void pump(Socket from, Socket to) {
		byte[] buf = new byte[16 * 1024];
		try {
			InputStream in = from.getInputStream();
			OutputStream out = to.getOutputStream();
			int n;
			while ((n = in.read(buf)) >= 0) {
				out.write(buf, 0, n);
				out.flush();
			}
		} catch (IOException ignored) {
			// the other side went away
		} finally {
			close(from);
			close(to);
		}
	}

	private static void close(AutoCloseable c) {
		if (c == null) return;
		try {
			c.close();
		} catch (Exception ignored) {
			// already closed
		}
	}
}
