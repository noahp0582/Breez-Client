package breezeclient.breezeclient.client.integration;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Plays an MP3 internet radio stream on a background thread. Reconnects a few times when the stream drops. */
public final class RadioPlayer {
	private static final Logger LOG = Logger.getLogger("breez-client");
	private static final int MAX_RECONNECTS = 4;

	private final Consumer<String> status;
	private volatile boolean stop;
	private volatile int volume = 30;
	private Thread thread;
	private volatile InputStream open;

	/** @param status receives short messages ("Connecting...", an error) on the player thread */
	public RadioPlayer(Consumer<String> status) { this.status = status; }

	public void setVolume(int percent) { this.volume = Math.max(0, Math.min(100, percent)); }

	public boolean running() { return thread != null && thread.isAlive(); }

	public void play(String url) {
		stop();
		stop = false;
		thread = new Thread(() -> run(url), "breez-radio");
		thread.setDaemon(true);
		thread.start();
	}

	public void stop() {
		stop = true;
		InputStream s = open;
		if (s != null) {
			try {
				s.close();
			} catch (IOException ignored) {
				// closing only unblocks the read
			}
		}
		Thread t = thread;
		if (t != null && t != Thread.currentThread()) {
			try {
				t.join(1500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		thread = null;
	}

	private void run(String url) {
		int attempts = 0;
		while (!stop && attempts <= MAX_RECONNECTS) {
			try {
				status.accept("Connecting...");
				stream(url);
				attempts = 0; // it played for a while: a later drop gets fresh attempts
			} catch (Exception e) {
				if (stop) return;
				LOG.log(Level.WARNING, "radio: " + e);
				status.accept("Stream error: " + e.getMessage());
			}
			if (stop) return;
			attempts++;
			try {
				Thread.sleep(2000L * attempts);
			} catch (InterruptedException e) {
				return;
			}
		}
		if (!stop) status.accept("Stopped: the stream could not be reached");
	}

	private void stream(String url) throws IOException, BitstreamException, DecoderException, LineUnavailableException {
		HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
		c.setConnectTimeout(10_000);
		c.setReadTimeout(15_000);
		c.setRequestProperty("User-Agent", "Mozilla/5.0 BreezClient");
		c.setInstanceFollowRedirects(true);
		int code = c.getResponseCode();
		if (code / 100 != 2) throw new IOException("HTTP " + code);
		SourceDataLine line = null;
		try (InputStream in = new java.io.BufferedInputStream(c.getInputStream(), 64 * 1024)) {
			open = in;
			Bitstream bits = new Bitstream(in);
			Decoder decoder = new Decoder();
			byte[] pcm = new byte[0];
			boolean announced = false;
			while (!stop) {
				Header h = bits.readFrame();
				if (h == null) break; // end of stream
				SampleBuffer buf = (SampleBuffer) decoder.decodeFrame(h, bits);
				if (line == null) {
					AudioFormat fmt = new AudioFormat(buf.getSampleFrequency(), 16, buf.getChannelCount(), true, false);
					line = AudioSystem.getSourceDataLine(fmt);
					line.open(fmt, 1 << 16);
					line.start();
				}
				short[] samples = buf.getBuffer();
				int n = buf.getBufferLength();
				if (pcm.length < n * 2) pcm = new byte[n * 2];
				int v = volume;
				for (int i = 0; i < n; i++) {
					int s = samples[i] * v / 100;
					pcm[i * 2] = (byte) s;
					pcm[i * 2 + 1] = (byte) (s >> 8);
				}
				line.write(pcm, 0, n * 2);
				bits.closeFrame();
				if (!announced) {
					announced = true;
					status.accept("Playing");
				}
			}
		} finally {
			open = null;
			if (line != null) {
				try {
					line.stop();
					line.close();
				} catch (RuntimeException ignored) {
					// device already gone
				}
			}
			c.disconnect();
		}
	}
}
