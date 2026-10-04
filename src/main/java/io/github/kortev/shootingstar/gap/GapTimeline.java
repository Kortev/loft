package io.github.kortev.shootingstar.gap;

/**
 * Tick offsets of an Ω-00 Ginnungagap event, counted from the moment the Genesis Key turns, and the
 * shared geometry of the event that the server and every client must agree on.
 */
public final class GapTimeline {
	/** First person: the key turns, the world glitches and goes white. */
	public static final int KEY = 0;
	/** Third person on the shooter: the key is aimed, a beam runs out and the lock snaps on. */
	public static final int AIM = 60;
	/** A jagged tear opens in the sky over the target with the mirror universe behind it. */
	public static final int TEAR = 150;
	/** The mirror universe comes down through the tear; matter starts swapping between the two. */
	public static final int CLOSING = 200;
	/** A whole tree trades places with its upside-down twin. */
	public static final int TREE_SWAP = 272;
	/** Single blocks stop swapping a little before contact so the moment reads clean. */
	public static final int SWAPS_END = 296;
	/** The tip of the inverted mountain touches the target. Every sound stops. */
	public static final int CONTACT = 310;
	/** The impact frames. */
	public static final int FRAMES = 330;
	/** Everything is erased, outward from the point of contact. */
	public static final int ERASURE = 460;
	/** Nothing is left but the shooter. */
	public static final int NOTHING = 620;
	public static final int END = 800;

	/** Height of the tear above the target's surface, in blocks. */
	public static final double TEAR_HEIGHT = 70.0;
	/** Depth of the inverted mountain that hangs from the mirror universe towards the target. */
	public static final int PEAK = 36;
	/** How far the mirror's tip presses into the ground through the impact frames. */
	public static final double PRESS = 2.0;
	/** Closest the target may be, so the camera shots have room. */
	public static final double MIN_RANGE = 24.0;

	private GapTimeline() {
	}

	/**
	 * How far the mirror universe's plane of reflection is lifted: the mirror of the block at height y occupies
	 * y' = 2·s0 + lift − y − 1, where s0 is the top of the target block. The mirror's ground sits at s0 + lift and
	 * the mountain's tip at s0 + lift − PEAK, so at {@link #CONTACT} the lift is exactly {@link #PEAK}.
	 */
	public static double lift(double t) {
		double hover = TEAR_HEIGHT + PEAK + 6.0;
		if (t < CLOSING) {
			return hover + 18.0 * (1.0 - smooth((t - TEAR) / (CLOSING - TEAR)));
		}
		if (t < CONTACT) {
			double p = (t - CLOSING) / (CONTACT - CLOSING);
			return hover + (PEAK - hover) * p * p * (0.35 + 0.65 * p);
		}
		return PEAK - PRESS * smooth((t - CONTACT) / (ERASURE - CONTACT));
	}

	/** Half the tear's length along its long axis, in blocks. */
	public static double tearLength(double t) {
		return 2.0 + 58.0 * smooth((t - TEAR) / 50.0) + 60.0 * smooth((t - CLOSING) / 70.0);
	}

	/** Half the tear's width across its long axis, in blocks. */
	public static double tearWidth(double t) {
		return 0.4 + 21.6 * smooth((t - TEAR) / 50.0) + 50.0 * smooth((t - CLOSING) / 70.0);
	}

	/** Manhattan distance in blocks from the contact block that has been erased by tick {@code t}. */
	public static double eraseFront(double t) {
		double e = (t - ERASURE) / 20.0;
		return e <= 0.0 ? -1.0 : 900.0 * Math.pow(e / 7.0, 1.6);
	}

	/** Horizontal reach of the real erasure, which finishes well inside the visual wave. */
	public static double eraseReach(double t, int radius) {
		return (radius + 2.0) * smooth((t - ERASURE) / (NOTHING - ERASURE - 20.0));
	}

	public static double smooth(double x) {
		x = x < 0.0 ? 0.0 : x > 1.0 ? 1.0 : x;
		return x * x * (3.0 - 2.0 * x);
	}
}
