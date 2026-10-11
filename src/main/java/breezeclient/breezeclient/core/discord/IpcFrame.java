package breezeclient.breezeclient.core.discord;

import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** The wire format of Discord's local IPC (rich presence): an opcode, a length and a JSON body, little-endian. */
public final class IpcFrame {
	public static final int HANDSHAKE = 0, FRAME = 1, CLOSE = 2, PING = 3, PONG = 4;

	private IpcFrame() {}

	public static byte[] encode(int opcode, String json) {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		return ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN).putInt(opcode).putInt(body.length).put(body).array();
	}

	/** {opcode, length} from the 8-byte header; the length is clamped so a corrupt header cannot ask for a huge buffer. */
	public static int[] header(byte[] eight) {
		ByteBuffer b = ByteBuffer.wrap(eight).order(ByteOrder.LITTLE_ENDIAN);
		int op = b.getInt();
		int len = b.getInt();
		return new int[] {op, Math.max(0, Math.min(len, 64 * 1024))};
	}

	public static String handshake(String clientId) {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		o.addProperty("client_id", clientId);
		return o.toString();
	}

	/**
	 * @param details first line under the app name; blank leaves it out
	 * @param state   second line; blank leaves it out
	 * @param start   epoch seconds for the "elapsed" timer, or 0 for none
	 */
	public static String setActivity(long pid, String details, String state, long start, String nonce) {
		JsonObject activity = new JsonObject();
		if (details != null && !details.isBlank()) activity.addProperty("details", clip(details));
		if (state != null && !state.isBlank()) activity.addProperty("state", clip(state));
		if (start > 0) {
			JsonObject ts = new JsonObject();
			ts.addProperty("start", start);
			activity.add("timestamps", ts);
		}
		JsonObject args = new JsonObject();
		args.addProperty("pid", pid);
		args.add("activity", activity);
		JsonObject o = new JsonObject();
		o.addProperty("cmd", "SET_ACTIVITY");
		o.add("args", args);
		o.addProperty("nonce", nonce);
		return o.toString();
	}

	/** Discord ignores text outside 2 to 128 characters; keep it inside. */
	static String clip(String s) {
		String t = s.trim();
		if (t.length() < 2) t = t + " ";
		return t.length() > 128 ? t.substring(0, 128) : t;
	}
}
