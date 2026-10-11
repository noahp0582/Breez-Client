package breezeclient.breezeclient.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps client hooks that run inside the game's packet handlers (chunk, entity and chat events, mixins on packet methods).
 * An exception that escapes a packet handler makes the client drop the connection with a "Network Protocol Error", so
 * anything of ours that runs there must never throw. Failures are logged once per hook and the hook is skipped.
 */
public final class Guard {
	private static final Logger LOG = Logger.getLogger("breez-client");
	private static final Map<String, Integer> FAILURES = new ConcurrentHashMap<>();

	private Guard() {}

	public static void run(String hook, Runnable body) {
		try {
			body.run();
		} catch (Exception | StackOverflowError t) { // other Errors (out of memory) are fatal and stay fatal, as in vanilla
			failed(hook, t);
		}
	}

	/** Like {@link #run} for hooks that return a value (chat rewriting); {@code fallback} is returned when the hook throws. */
	public static <T> T call(String hook, T fallback, Supplier<T> body) {
		try {
			return body.get();
		} catch (Exception | StackOverflowError t) {
			failed(hook, t);
			return fallback;
		}
	}

	/** How many times hooks have been caught throwing (used by the self-test). */
	public static int failures() {
		int n = 0;
		for (int v : FAILURES.values()) n += v;
		return n;
	}

	private static void failed(String hook, Throwable t) {
		int n = FAILURES.merge(hook, 1, Integer::sum);
		if (n == 1) LOG.log(Level.SEVERE, "Hook '" + hook + "' threw inside a packet handler and was skipped", t);
	}
}
