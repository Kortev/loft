package io.github.kortev.gojolander.purple;

import net.minecraft.util.math.MathHelper;

/**
 * When everything in a Hollow Purple happens, in ticks from the key being pressed. The server, every client's cues,
 * the caster's shots and every effect read these, so nothing can drift.
 *
 * <pre>
 *   0  BLUE     his left arm out; a blue orb forms at his fingertips and pulls everything in.
 *  24  RED      his right arm out; a red orb at the other hand, pushing the air out in rings.
 *  48  SWEEP    his hands sweep together in front of him.
 *  56  MERGE    the orbs collide and fold into one: a flash, a crack, a huge violet sphere with a white core, growing.
 *  76  RELEASE  he flicks it forward. It flies straight along where he looked, SPEED blocks a tick, erasing everything.
 *  76+...       FLIGHT: range / SPEED ticks, the camera riding behind it, then cutting wide.
 *      +FADE    it collapses in a flash of light where its range ends.
 * </pre>
 */
public final class PurpleTimeline {
	public static final int BLUE = 0;
	public static final int RED = 24;
	public static final int SWEEP = 48;
	public static final int MERGE = 56;
	/** The sphere is whole and as big as it gets. */
	public static final int WHOLE = 70;
	public static final int RELEASE = 76;
	/** How far it flies a tick, in blocks. */
	public static final double SPEED = 3.5;
	/** How long it takes to collapse in light where it ends. */
	public static final int FADE = 14;
	/** How long the camera rides behind the sphere after the release, in ticks (then it cuts wide); and how long all his shots run: then his own eyes are back. */
	public static final int RIDE = 46;
	public static final int CINEMA = 74;
	/** Seconds the client keeps one a little longer for its dust and sounds to finish, in ticks. */
	public static final int KEEP = 60;

	private PurpleTimeline() {
	}

	/** How many ticks the sphere flies for {@code range} blocks. */
	public static int flight(double range) {
		return (int) Math.ceil(range / SPEED);
	}

	/** The tick it is gone by (its collapse over) for {@code range} blocks. */
	public static int end(double range) {
		return RELEASE + flight(range) + FADE;
	}

	/** How far along its path the sphere is at {@code t} ticks (blocks from where it was released). */
	public static double front(double t, double range) {
		return MathHelper.clamp((t - RELEASE) * SPEED, 0.0, range);
	}

	/** How far the orb at one hand has formed at {@code t} (0 to 1): {@code from} when it begins. */
	public static double grow(double t, double from, double length) {
		double x = MathHelper.clamp((t - from) / length, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}

	/** How far the hands have swept together at {@code t}, 0 (out) to 1 (touching). */
	public static double sweep(double t) {
		double x = MathHelper.clamp((t - SWEEP) / (double) (MERGE - SWEEP), 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}

	/** How whole the violet sphere is at {@code t}, 0 (nothing before the merge) to 1: it swells out of the collision. */
	public static double sphere(double t) {
		double x = MathHelper.clamp((t - MERGE) / (double) (WHOLE - MERGE), 0.0, 1.0);
		return 1.0 - (1.0 - x) * (1.0 - x) * (1.0 - x);
	}
}
