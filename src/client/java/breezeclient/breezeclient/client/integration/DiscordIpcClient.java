package breezeclient.breezeclient.client.integration;

import breezeclient.breezeclient.core.discord.IpcFrame;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to a running Discord client over its local IPC pipe or socket. Not thread-safe: the module drives it from one
 * worker thread. Needs only the application (client) id; no login or token is involved.
 */
public final class DiscordIpcClient implements Closeable {
	private InputStream in;
	private OutputStream out;
	private Closeable handle;
	private boolean ready;

	public boolean connected() { return ready; }

	/** Opens the first Discord IPC endpoint that answers and performs the handshake. */
	public void connect(String clientId) throws IOException {
		close();
		IOException last = new IOException("Discord is not running");
		for (int i = 0; i < 10; i++) {
			try {
				open(i);
				write(IpcFrame.HANDSHAKE, IpcFrame.handshake(clientId));
				readFrame(); // READY or an error body; either way the endpoint is alive
				ready = true;
				return;
			} catch (IOException e) {
				last = e;
				close();
			}
		}
		throw last;
	}

	private void open(int n) throws IOException {
		if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
			RandomAccessFile raf = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + n, "rw");
			handle = raf;
			in = new java.io.FileInputStream(raf.getFD());
			out = new java.io.FileOutputStream(raf.getFD());
			return;
		}
		List<Path> dirs = new ArrayList<>();
		for (String v : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
			String d = System.getenv(v);
			if (d != null && !d.isBlank()) dirs.add(Path.of(d));
		}
		dirs.add(Path.of("/tmp"));
		IOException last = new IOException("no Discord socket found");
		for (Path d : dirs) {
			for (Path p : new Path[] {d.resolve("discord-ipc-" + n), d.resolve("snap.discord").resolve("discord-ipc-" + n),
				d.resolve("app/com.discordapp.Discord").resolve("discord-ipc-" + n)}) {
				if (!Files.exists(p)) continue;
				try {
					SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
					ch.connect(UnixDomainSocketAddress.of(p));
					handle = ch;
					in = Channels.newInputStream(ch);
					out = Channels.newOutputStream(ch);
					return;
				} catch (IOException e) {
					last = e;
				}
			}
		}
		throw last;
	}

	private void write(int opcode, String json) throws IOException {
		out.write(IpcFrame.encode(opcode, json));
		out.flush();
	}

	private String readFrame() throws IOException {
		byte[] head = in.readNBytes(8);
		if (head.length < 8) throw new IOException("Discord closed the connection");
		int[] h = IpcFrame.header(head);
		byte[] body = in.readNBytes(h[1]);
		return new String(body, java.nio.charset.StandardCharsets.UTF_8);
	}

	/** Sends one activity update and consumes the reply so the pipe never fills up. */
	public void setActivity(long pid, String details, String state, long start) throws IOException {
		if (!ready) throw new IOException("not connected");
		write(IpcFrame.FRAME, IpcFrame.setActivity(pid, details, state, start, java.util.UUID.randomUUID().toString()));
		readFrame();
	}

	@Override
	public void close() {
		ready = false;
		try {
			if (handle != null) handle.close();
		} catch (IOException ignored) {
			// already closed
		}
		handle = null;
		in = null;
		out = null;
	}
}
