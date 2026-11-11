package io.github.kortev.chainsawman.form;

/**
 * The rhythm of the saws' slashes, worked out the same way by the server (which decides, and tells everyone) and by the slasher's own
 * game (which swings at once, before the server's word): which step of the combo a slash is (the right arm across, the left back, then
 * both down from overhead, the big cut, every {@link #BIG_EVERY}th in a row), how many have come in a row (a flurry: the engine climbs
 * with it), and the engine's pitch for it. A pause longer than {@link Saw#COMBO_GAP} starts both over. Look and sound only: nothing
 * here touches damage, reach or timing.
 */
public final class SwingRhythm {
	/** The big cut comes every so many slashes in a row. */
	public static final int BIG_EVERY = 3;
	/** The engine's pitch: where a flurry starts, how much each slash in a row adds, and how high it can climb. */
	public static final float PITCH_FROM = 1.0F;
	public static final float PITCH_STEP = 0.05F;
	public static final float PITCH_TOP = 1.3F;

	private SwingRhythm() {
	}

	/** Whether a slash at {@code now} starts over, the last having come at {@code lastAt} (or never: {@code lastAt} far back). */
	public static boolean fresh(long lastAt, long now) {
		return now - lastAt > Saw.COMBO_GAP;
	}

	/**
	 * The step of a slash after {@code lastStep} ({@code fresh}: after a pause, or the first), with the arms there are ({@code right},
	 * {@code left}) and Pochita only held ({@code held}: one arm, no left sweep): 0 the right arm across, 1 the left back, 2 the big cut.
	 * An arm gone, its sweep comes from the other; with none, the head's saw nods down (the big cut's step).
	 */
	public static int step(boolean fresh, int lastStep, boolean right, boolean left, boolean held) {
		int step = fresh || lastStep < 0 ? 0 : (lastStep + 1) % BIG_EVERY;
		if (!right && !left) {
			step = 2;
		} else if (step == 0 && !right) {
			step = 1;
		} else if (step == 1 && !left) {
			step = 0;
		}
		if (held && step == 1) {
			step = 0;
		}
		return step;
	}

	/** How many slashes in a row a slash makes, after {@code count} ({@code fresh}: after a pause, the first again). */
	public static int flurry(boolean fresh, int count) {
		return fresh ? 1 : count + 1;
	}

	/** The engine's pitch for the {@code flurry}th slash in a row: climbing with each, to its top. */
	public static float pitch(int flurry) {
		return Math.min(PITCH_TOP, PITCH_FROM + PITCH_STEP * Math.max(0, flurry - 1));
	}

	/** Whether {@code step} is the big cut. */
	public static boolean big(int step) {
		return step == 2;
	}

	/** Which arm swings in {@code step}: 1 the right, -1 the left, 0 both (the big cut). */
	public static int side(int step) {
		return step == 0 ? 1 : step == 1 ? -1 : 0;
	}
}
