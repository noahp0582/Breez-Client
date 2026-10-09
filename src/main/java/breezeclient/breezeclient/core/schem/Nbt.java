package breezeclient.breezeclient.core.schem;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal NBT reader (no Minecraft classes). Values: Byte, Short, Integer, Long, Float, Double, String,
 * byte[], int[], long[], List&lt;Object&gt;, Map&lt;String,Object&gt;.
 */
public final class Nbt {
	private Nbt() {}

	/** Reads a (possibly gzipped) NBT file and returns the root compound. */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> readRoot(byte[] data) throws IOException {
		InputStream in = new ByteArrayInputStream(data);
		if (data.length > 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B) in = new GZIPInputStream(in);
		try (DataInputStream d = new DataInputStream(in)) {
			int type = d.readUnsignedByte();
			if (type != 10) throw new IOException("root tag is not a compound");
			d.readUTF(); // root name
			return (Map<String, Object>) readPayload(d, 10, 0);
		}
	}

	private static Object readPayload(DataInputStream d, int type, int depth) throws IOException {
		if (depth > 512) throw new IOException("nbt nested too deep");
		switch (type) {
			case 1: return d.readByte();
			case 2: return d.readShort();
			case 3: return d.readInt();
			case 4: return d.readLong();
			case 5: return d.readFloat();
			case 6: return d.readDouble();
			case 7: {
				int n = checkLen(d.readInt());
				byte[] b = new byte[n];
				d.readFully(b);
				return b;
			}
			case 8: return readString(d);
			case 9: {
				int et = d.readUnsignedByte();
				int n = checkLen(d.readInt());
				List<Object> l = new ArrayList<>(Math.min(n, 1 << 16));
				for (int i = 0; i < n; i++) l.add(readPayload(d, et, depth + 1));
				return l;
			}
			case 10: {
				Map<String, Object> m = new LinkedHashMap<>();
				while (true) {
					int t = d.readUnsignedByte();
					if (t == 0) break;
					String name = readString(d);
					m.put(name, readPayload(d, t, depth + 1));
				}
				return m;
			}
			case 11: {
				int n = checkLen(d.readInt());
				int[] a = new int[n];
				for (int i = 0; i < n; i++) a[i] = d.readInt();
				return a;
			}
			case 12: {
				int n = checkLen(d.readInt());
				long[] a = new long[n];
				for (int i = 0; i < n; i++) a[i] = d.readLong();
				return a;
			}
			default: throw new IOException("unknown nbt tag type " + type);
		}
	}

	private static int checkLen(int n) throws IOException {
		if (n < 0 || n > 200_000_000) throw new IOException("bad nbt array length " + n);
		return n;
	}

	private static String readString(DataInputStream d) throws IOException {
		int len = d.readUnsignedShort();
		byte[] b = new byte[len];
		d.readFully(b);
		return new String(b, StandardCharsets.UTF_8);
	}

	// helpers ---------------------------------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	public static Map<String, Object> map(Object o) {
		if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
		throw new IllegalArgumentException("expected compound");
	}

	@SuppressWarnings("unchecked")
	public static List<Object> list(Object o) {
		if (o instanceof List<?> l) return (List<Object>) l;
		throw new IllegalArgumentException("expected list");
	}

	public static int intOf(Object o) {
		if (o instanceof Number n) return n.intValue();
		throw new IllegalArgumentException("expected number");
	}
}
