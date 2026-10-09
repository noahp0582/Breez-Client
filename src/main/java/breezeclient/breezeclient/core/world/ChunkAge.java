package breezeclient.breezeclient.core.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Tells freshly generated ("new") chunks from chunks that already existed ("old"), the standard trick for finding where
 * players have been: a chunk that is generated while you watch has liquids that start to flow right after it loads.
 * The first classification of a chunk is kept for good (it records whether anyone had been there before you).
 *
 * <p>Pure logic, driven by events: {@link #onLoad}, {@link #onFluidFlow}, {@link #onUnload} and {@link #tick}.
 */
public final class ChunkAge {
	/** UNSURE: strict mode only, a chunk with no liquid gives no evidence either way. */
	public enum State { UNKNOWN, NEW, OLD, UNSURE }

	private final int windowTicks;
	private boolean strict;
	private final Map<Long, State> states = new HashMap<>();
	private final Map<Long, Long> loadedAt = new HashMap<>();
	private final Deque<Long> pending = new ArrayDeque<>();

	public ChunkAge(int windowTicks) { this.windowTicks = windowTicks; }

	/** Strict: a chunk is only called old when it contains liquid that stayed still; chunks without liquid become UNSURE. */
	public void setStrict(boolean strict) { this.strict = strict; }

	public static long key(int chunkX, int chunkZ) { return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32); }

	public static int x(long key) { return (int) key; }

	public static int z(long key) { return (int) (key >> 32); }

	/** A chunk finished loading. Chunks that already have a classification keep it. */
	public void onLoad(long key, long nowTick) {
		State s = states.get(key);
		if (s == State.NEW || s == State.OLD) return;
		states.put(key, State.UNKNOWN);
		loadedAt.put(key, nowTick);
		pending.addLast(key);
	}

	/** A flowing (non-source) fluid block update arrived inside this chunk. */
	public void onFluidFlow(long key, long nowTick) {
		Long at = loadedAt.get(key);
		if (at == null || states.get(key) != State.UNKNOWN) return;
		if (nowTick - at <= windowTicks) {
			states.put(key, State.NEW);
			loadedAt.remove(key);
		}
	}

	/** The chunk was unloaded; anything still undecided had no fluid flow in time. */
	public void onUnload(long key, java.util.function.LongPredicate hasLiquid) {
		if (states.get(key) == State.UNKNOWN) states.put(key, settled(key, hasLiquid));
		loadedAt.remove(key);
	}

	private State settled(long key, java.util.function.LongPredicate hasLiquid) { return !strict || hasLiquid.test(key) ? State.OLD : State.UNSURE; }

	/** Settles chunks whose observation window has passed. {@code hasLiquid} says whether a chunk contains any liquid (used in strict mode). */
	public void tick(long nowTick, java.util.function.LongPredicate hasLiquid) {
		while (!pending.isEmpty()) {
			long key = pending.peekFirst();
			Long at = loadedAt.get(key);
			if (at == null || states.get(key) != State.UNKNOWN) {
				pending.pollFirst();
				continue;
			}
			if (nowTick - at <= windowTicks) break;
			states.put(key, settled(key, hasLiquid));
			loadedAt.remove(key);
			pending.pollFirst();
		}
	}

	public State state(long key) { return states.getOrDefault(key, State.UNKNOWN); }

	public int count(State s) {
		int n = 0;
		for (State v : states.values()) if (v == s) n++;
		return n;
	}

	public Map<Long, State> snapshot() { return new HashMap<>(states); }

	/** Restores saved classifications (UNKNOWN entries are ignored). */
	public void restore(Map<Long, State> saved) {
		for (var e : saved.entrySet()) if (e.getValue() == State.NEW || e.getValue() == State.OLD) states.putIfAbsent(e.getKey(), e.getValue());
	}

	public void clear() {
		states.clear();
		loadedAt.clear();
		pending.clear();
	}
}
