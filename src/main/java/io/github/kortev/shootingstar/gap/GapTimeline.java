package io.github.kortev.shootingstar.gap;

/**
 * Tick offsets of an Ω-00 Ginnungagap event, counted from the moment the Genesis Key turns, and the
 * shared geometry of the event that the server and every client must agree on.
 */
public final class GapTimeline {
	/** First person: the key comes up, goes into the air and turns. */
	public static final int KEY = 0;
	/** The camera leaves the shooter's eyes and climbs over the target into the clouds. */
	public static final int RISE = 42;
	/** The feed: out of the clouds and up to orbit. */
	public static final int FEED = 66;
	/** Bifröst, the gate in orbit over the target, wakes. */
	public static final int GATE = FEED + 26;
	/** The gate opens onto another universe, and the camera dives through the window into it. */
	public static final int OPEN = FEED + 70;
	/**
	 * Inside that universe, in among its galaxies; the camera pulls back out of it until it is a map, a lattice of
	 * blocks, and the block in the middle is selected.
	 */
	public static final int MAP = FEED + 118;
	/** That block is cut out and drawn through the gate. */
	public static final int CUT = MAP + 84;
	/** The bridge reaches down to the target and the block drops into it. */
	public static final int SEND = CUT + 42;
	/** The chase down the bridge to the ground. */
	public static final int FALL = SEND + 26;
	/** Back in the world: the block comes down on the target. */
	public static final int INBOUND = FALL + 68;
	/** The block hits. */
	public static final int CONTACT = INBOUND + 24;
	/** The impact frames, from the moment of contact. */
	public static final int FRAMES = CONTACT;
	/** The frames end on the explosion: that universe bursting out of its block. */
	public static final int BLAST = CONTACT + 30;
	/** It falls back in on itself. */
	public static final int COLLAPSE = BLAST + 60;
	/** Everything in the zone is erased, outward from the point of contact. */
	public static final int ERASURE = COLLAPSE + 10;
	/** Nothing is left but the shooter. */
	public static final int NOTHING = ERASURE + 100;
	/** The camera is back in the shooter's eyes, alone in the black, and they can move again. */
	public static final int RETURN = NOTHING + 100;
	public static final int END = RETURN + 80;

	// The rebuild, on the shooter's side only, in ticks from when they use the key again in the black: a loading screen,
	// Yggdrasil growing up out of the hole, its nine worlds lighting, light gathering in it and sent out through its
	// roots, and the world remade out from them.
	public static final int REBUILD_TREE = 40;
	public static final int REBUILD_WORLDS = 120;
	public static final int REBUILD_GATHER = 160;
	public static final int REBUILD_SWEEP = 220;
	public static final int REBUILD_DONE = 520;
	public static final int REBUILD_END = 640;

	/** Closest the target may be, so the camera shots have room. */
	public static final double MIN_RANGE = 24.0;

	private GapTimeline() {
	}

	/** Manhattan distance in blocks from the contact block that has been erased by tick {@code t}. */
	public static double eraseFront(double t) {
		double e = (t - ERASURE) / 20.0;
		return e <= 0.0 ? -1.0 : 1400.0 * Math.pow(e / 7.0, 1.5);
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
