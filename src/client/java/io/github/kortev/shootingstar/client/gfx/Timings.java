package io.github.kortev.shootingstar.client.gfx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.opengl.GL11;

/**
 * How long each of the mod's drawing passes takes, for finding what makes a frame slow. Off unless the game is started
 * with {@code -Dshootingstar.timings=true} (the self tests turn it on). When on, every section waits for the graphics
 * card to finish before and after it, so the time it reports is what that section cost the card as well as the
 * processor; that also slows the frame down, which is why it is never on in play.
 */
public final class Timings {
	/** On with {@code -Dshootingstar.timings=true}, or when a self test turns it on. */
	public static boolean on = Boolean.getBoolean("shootingstar.timings");
	/** Per section: total nanoseconds, how many times, the longest once. */
	private static final Map<String, long[]> TOTALS = new LinkedHashMap<>();
	private static final Deque<String> OPEN = new ArrayDeque<>();
	private static final Deque<Long> STARTS = new ArrayDeque<>();

	private Timings() {
	}

	public static void begin(String section) {
		if (!on) {
			return;
		}
		GL11.glFinish();
		OPEN.push(section);
		STARTS.push(System.nanoTime());
	}

	public static void end() {
		if (!on || OPEN.isEmpty()) {
			return;
		}
		GL11.glFinish();
		long took = System.nanoTime() - STARTS.pop();
		add(OPEN.pop(), took);
	}

	/** Adds a time measured elsewhere (the server's ticks). */
	public static synchronized void add(String section, long nanos) {
		long[] total = TOTALS.computeIfAbsent(section, k -> new long[3]);
		total[0] += nanos;
		total[1]++;
		total[2] = Math.max(total[2], nanos);
	}

	/** Everything measured since the last call, as "section avg/max ms (count)", and starts again. */
	public static synchronized String drain() {
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, long[]> e : TOTALS.entrySet()) {
			long[] t = e.getValue();
			if (t[1] == 0) {
				continue;
			}
			out.append(String.format(java.util.Locale.ROOT, "%s %.2f/%.2f ms (%d)  ", e.getKey(), t[0] / 1.0E6 / t[1], t[2] / 1.0E6,
					t[1]));
			t[0] = 0;
			t[1] = 0;
			t[2] = 0;
		}
		return out.toString();
	}
}
