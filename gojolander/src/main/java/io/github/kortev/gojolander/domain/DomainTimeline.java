package io.github.kortev.gojolander.domain;

import net.minecraft.util.math.MathHelper;

/**
 * When everything in a Domain happens, in ticks from the key being pressed. The server, every client's cues, the
 * caster's shots and every effect read these, so nothing can drift. How long the void holds is a game rule
 * ({@code unlimitedVoidHoldSeconds}), read once as it is cast and carried in the event as {@code hold}; everything after
 * the void is counted from it.
 *
 * <pre>
 *  0  SIGN      he lifts the blindfold and crosses two fingers; the camera swings round in front of him; a dark ring
 *               spreads out over the ground round him. Anyone who gets out of the ring is not caught.
 * 22  RISE      the ring is at its full reach; black flows up out of it, round the sphere.
 * 30  CLOSE     the black meets over the top. Whoever is inside is caught and held still; nobody gets in or out.
 * 40  VOID      inside, the world is gone: endless space over still black water. He is back in his own eyes, free.
 * 40+hold       COLLAPSE: the barrier cracks and shatters; the world rushes back; the caught are let go, dazed.
 *    +SHATTER   END: the last of it gone.
 * </pre>
 */
public final class DomainTimeline {
	public static final int SIGN = 0;
	public static final int RISE = 22;
	public static final int CLOSE = 30;
	public static final int VOID = 40;
	/** How long the barrier takes to crack, shatter and be gone. */
	public static final int SHATTER = 16;
	/** How long the caught are left dazed after (slow, and the light going in and out), in ticks. */
	public static final int DAZE = 100;

	private DomainTimeline() {
	}

	/** The tick the barrier shatters, for a Domain that holds {@code hold} ticks. */
	public static int collapse(int hold) {
		return VOID + hold;
	}

	public static int end(int hold) {
		return collapse(hold) + SHATTER;
	}

	/** How far out the ring on the ground has spread, from 0 to 1, at tick {@code t}. */
	public static double ring(double t) {
		double x = MathHelper.clamp(t / RISE, 0.0, 1.0);
		return 1.0 - Math.pow(1.0 - x, 3.0);
	}

	/**
	 * How far the black has climbed from the ring towards the top of the sphere, from 0 (just the ring) to 1 (closed), at
	 * tick {@code t}: slow off the ground, faster as it curls in overhead.
	 */
	public static double rise(double t) {
		double x = MathHelper.clamp((t - RISE) / (CLOSE - RISE), 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
