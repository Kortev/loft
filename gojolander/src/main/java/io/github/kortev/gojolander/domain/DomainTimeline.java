package io.github.kortev.gojolander.domain;

import net.minecraft.util.math.MathHelper;

/**
 * When everything in a Domain happens, in ticks from the key being pressed. The server, every client's cues, the
 * caster's shots and every effect read these, so nothing can drift. How long the void holds is a game rule
 * ({@code unlimitedVoidHoldSeconds}), read once as it is cast and carried in the event as {@code hold}; everything after
 * the void is counted from it.
 *
 * <pre>
 *   0  SIGN      a hard cut to his face, close: the blindfold comes up, a ring of ink starts out over the ground.
 *  18  EYES      the blindfold is up; his eyes burn.
 *  30  HAND      his hand snaps up into the sign, the camera punching in on it. DOMAIN EXPANSION.
 *  38  WIDE      a cut to wide: the camera cranes back and up round him to watch the rest.
 *  50  RISE      the ring is at its full reach; the black floods up out of it, round the sphere.
 *  86  CLOSE     the black meets overhead. Whoever is inside is caught and held still; nobody gets in or out. For everyone
 *                in it, a hard cut to black and silence; his camera is back in his eyes.
 * 100  VOID      the void opens out of the great eye, over OPEN ticks. UNLIMITED VOID. He is free.
 * 100+...        every PULSE or so, the flood surges: whoever is caught takes a step of it, and flinches.
 * 100+hold       COLLAPSE: the barrier cracks out from one point, then shatters, its shards falling away; the world
 *                rushes back; the caught are let go, broken: half a heart (the last step), and dazed. The ringing in
 *                their heads snaps off at SNAP and their knees give out; they come to at UP, and are up by RISEN.
 *    +SHATTER    END: the last of it gone.
 * </pre>
 *
 * Anyone who gets out of the ring before CLOSE is not caught: about 4 s, against a sprint of a block every 3.6 ticks.
 *
 * <p>How much it takes from whoever it holds is how long it holds them (as in the show: kept in it for a fifth of a
 * second, a crowd needs two months to recover). Its steps share the hold out evenly, about one every PULSE, the last of
 * them as it lets go; each takes them a share of the way down to half a heart, so a Domain cut short leaves them
 * only part of the way.
 */
public final class DomainTimeline {
	public static final int SIGN = 0;
	public static final int EYES = 18;
	public static final int HAND = 30;
	public static final int WIDE = 38;
	public static final int RISE = 50;
	public static final int CLOSE = 86;
	public static final int VOID = 100;
	/** How long the void takes to open out of the eye over the whole sky. */
	public static final int OPEN = 22;
	/** How long the barrier takes to crack, shatter and be gone. */
	public static final int SHATTER = 30;
	/** How long the caught are left dazed after (slow, weak, and the light going in and out), in ticks. */
	public static final int DAZE = 300;
	/** About how far apart the void's steps are. */
	public static final int PULSE = 40;
	/**
	 * Let go of at its natural end, after COLLAPSE: the ringing snaps off and their knees give out (down by SNAP); they
	 * come to at UP (and may move again), and are on their feet by RISEN.
	 */
	public static final int SNAP = 7;
	public static final int UP = 30;
	public static final int RISEN = 44;

	private DomainTimeline() {
	}

	/** The tick the barrier shatters, for a Domain that holds {@code hold} ticks. */
	public static int collapse(int hold) {
		return VOID + hold;
	}

	/** How many steps a Domain holding {@code hold} ticks takes them down in (the last as it lets go). */
	public static int steps(int hold) {
		return Math.max(1, Math.round(hold / (float) PULSE));
	}

	/** The tick of step {@code k} (1 to {@link #steps}); the last is the collapse. */
	public static int step(int hold, int k) {
		return VOID + Math.round(hold * k / (float) steps(hold));
	}

	/** Which step falls on tick {@code age} (1 to {@link #steps}), or 0 for none. */
	public static int stepAt(int hold, int age) {
		int n = steps(hold);
		for (int k = 1; k <= n; k++) {
			if (step(hold, k) == age) {
				return k;
			}
		}
		return 0;
	}

	public static int end(int hold) {
		return collapse(hold) + SHATTER;
	}

	/** How far out the ring on the ground has spread, from 0 to 1, at tick {@code t}. */
	public static double ring(double t) {
		double x = MathHelper.clamp(t / RISE, 0.0, 1.0);
		return 1.0 - Math.pow(1.0 - x, 2.2);
	}

	/**
	 * How far the black has climbed from the ring towards the top of the sphere, from 0 (just the ring) to 1 (closed), at
	 * tick {@code t}: slow off the ground, faster as it curls in overhead.
	 */
	public static double rise(double t) {
		double x = MathHelper.clamp((t - RISE) / (CLOSE - RISE), 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}

	/** How far the void has opened out of the eye, from 0 (all black) to 1 (the whole sky), at tick {@code t}. */
	public static double open(double t) {
		double x = MathHelper.clamp((t - VOID) / OPEN, 0.0, 1.0);
		return 1.0 - Math.pow(1.0 - x, 3.0);
	}
}
