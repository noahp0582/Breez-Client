package breezeclient.breezeclient.core.schem;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Test-only NBT writer so parser tests can build tiny schematics in memory. */
final class NbtTestWriter {
	private NbtTestWriter() {}

	static byte[] write(Map<String, Object> root, boolean gzip) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (DataOutputStream d = new DataOutputStream(gzip ? new GZIPOutputStream(bos) : bos)) {
			d.writeByte(10);
			d.writeUTF("");
			payload(d, root);
		}
		return bos.toByteArray();
	}

	private static int typeOf(Object o) {
		if (o instanceof Byte) return 1;
		if (o instanceof Short) return 2;
		if (o instanceof Integer) return 3;
		if (o instanceof Long) return 4;
		if (o instanceof Float) return 5;
		if (o instanceof Double) return 6;
		if (o instanceof byte[]) return 7;
		if (o instanceof String) return 8;
		if (o instanceof List) return 9;
		if (o instanceof Map) return 10;
		if (o instanceof int[]) return 11;
		if (o instanceof long[]) return 12;
		throw new IllegalArgumentException(o.getClass().toString());
	}

	@SuppressWarnings("unchecked")
	private static void payload(DataOutputStream d, Object o) throws IOException {
		switch (typeOf(o)) {
			case 1 -> d.writeByte((Byte) o);
			case 2 -> d.writeShort((Short) o);
			case 3 -> d.writeInt((Integer) o);
			case 4 -> d.writeLong((Long) o);
			case 5 -> d.writeFloat((Float) o);
			case 6 -> d.writeDouble((Double) o);
			case 7 -> {
				byte[] b = (byte[]) o;
				d.writeInt(b.length);
				d.write(b);
			}
			case 8 -> d.writeUTF((String) o);
			case 9 -> {
				List<Object> l = (List<Object>) o;
				d.writeByte(l.isEmpty() ? 1 : typeOf(l.get(0)));
				d.writeInt(l.size());
				for (Object e : l) payload(d, e);
			}
			case 10 -> {
				for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
					d.writeByte(typeOf(e.getValue()));
					d.writeUTF(e.getKey());
					payload(d, e.getValue());
				}
				d.writeByte(0);
			}
			case 11 -> {
				int[] a = (int[]) o;
				d.writeInt(a.length);
				for (int v : a) d.writeInt(v);
			}
			case 12 -> {
				long[] a = (long[]) o;
				d.writeInt(a.length);
				for (long v : a) d.writeLong(v);
			}
			default -> throw new IllegalStateException();
		}
	}
}
