package io.github.kortev.shootingstar.gap;

/**
 * Tick offsets of an Ω-00 Ginnungagap event, counted from the moment the Genesis Key turns, and the
 * shared geometry of the event that the server and every client must agree on.
 */
public final class GapTimeline {
	/** First person: the key turns, the world glitches and goes white. */
	public static final int KEY = 0;
	/** The key has turned in the air: the screen goes white. */
	public static final int TURNED = 60;
	/** From the white, the sky over the target shatters; another universe shows through where the shards fall away. */
	public static final int TEAR = 60;
	/** A shard of the other universe drops out of the broken sky; matter starts swapping between the two. */
	public static final int CLOSING = 110;
	/** Single blocks stop swapping a little before contact so the moment reads clean. */
	public static final int SWAPS_END = 206;
	/** The shard's tip touches the target. Every sound stops. */
	public static final int CONTACT = 220;
	/** The impact frames. */
	public static final int FRAMES = 240;
	/** Everything in the zone is erased, outward from the point of contact. */
	public static final int ERASURE = 370;
	/** Nothing is left but the shooter. */
	public static final int NOTHING = 530;
	public static final int END = 710;
	/** The camera is back in the shooter's eyes, alone in the black, and they can move again. */
	public static final int RETURN = 630;

	/** How far the falling shard's tip presses into the ground through the impact frames. */
	public static final double PRESS = 2.0;
	/** Closest the target may be, so the camera shots have room. */
	public static final double MIN_RANGE = 24.0;

	private GapTimeline() {
	}

	/** How much of the sky has shattered, as an angle from the point it breaks from, in radians (pi is all of it). */
	public static double shatter(double t) {
		return Math.PI * 1.05 * smooth((t - TEAR - 4) / 120.0);
	}

	/** Height of the falling shard's tip over the target: it shows in the broken sky, then drops and lands at contact. */
	public static double shardTip(double t) {
		if (t < CLOSING) {
			return 130.0 + 110.0 * (1.0 - smooth((t - TEAR) / (CLOSING - TEAR)));
		}
		if (t < CONTACT) {
			// It hangs, then comes down faster and faster: it hits at full speed.
			double p = (t - CLOSING) / (CONTACT - CLOSING);
			return 130.0 * (1.0 - Math.pow(p, 1.6));
		}
		return -PRESS * smooth((t - CONTACT) / (ERASURE - CONTACT));
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
