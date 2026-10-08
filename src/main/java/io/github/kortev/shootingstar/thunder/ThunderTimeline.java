package io.github.kortev.shootingstar.thunder;

/**
 * Tick offsets of a Mjölnir strike, counted from the moment the hammer is raised, and the geometry both sides need to
 * agree on. The server only acts on {@link #STROKE} and {@link #END}; everything else paces the shooter's storm feed and
 * what everyone sees in the world, and both read it from here so they cannot drift apart.
 */
public final class ThunderTimeline {
	/** The hammer goes up over the shooter's head; arcs crawl over its head. */
	public static final int RAISE = 0;
	/** A bolt leaps from the hammer straight up into the sky: the call. */
	public static final int CALL = 16;
	/** The shooter's camera rides up after the call, over the target and into the gathering storm. */
	public static final int RISE = 30;
	/** Feed: out of the cloud deck into orbit over the night side, every thunderstorm on Earth flickering. */
	public static final int FEED = 54;
	/** Feed: the global circuit's charge is drawn in across the planet to the target. */
	public static final int DRAW = 116;
	/** Feed: down onto the storm over the target as it winds into one vortex; MJÖLNIR. */
	public static final int FORGE = 206;
	/** Feed: down the eye after the stepped leader as it feels its way to the ground. */
	public static final int LEADER = 276;
	/** Back in the world: the leader steps down out of the vortex and streamers rise from everything under it. */
	public static final int INBOUND = 326;
	/** The return stroke: the bolt. The server breaks the ground, the scar burns outward and the arcs jump. */
	public static final int STROKE = 366;
	/** The channel strikes again down the same path, the way real lightning flickers. */
	public static final int[] RESTRIKES = {STROKE + 5, STROKE + 11, STROKE + 19};
	/** The impact frames are over; the shooter's camera cuts to high over the strike. */
	public static final int FRAMES_END = STROKE + 26;
	/** The shooter's camera has craned up over the strike to look down on the scar. */
	public static final int WIDE_END = STROKE + 120;
	/** The shooter's camera is back in their own eyes. */
	public static final int CAMERA_END = WIDE_END + 24;
	/** The storm has unwound and the strike is forgotten (the ground may still be settling). */
	public static final int END = STROKE + 240;

	/** How fast the scar burns out along its branches, in blocks of path per tick. */
	public static final double SCAR_SPEED = 2.5;
	/** How fast the heat of the stroke spreads out over the ground, in blocks per tick. */
	public static final double BURN_SPEED = 6.0;
	/** Steps the stepped leader takes from the cloud base to the ground. */
	public static final int LEADER_STEPS = 20;
	/** Default strike radius (the zone where nothing survives); the client's aim hint assumes it. */
	public static final int DEFAULT_RADIUS = 64;
	/** Danger-close floor for tiny strikes; normally the limit is the edge of the scar, 1.5x the radius. */
	public static final double MIN_RANGE = 16.0;
	public static final double MAX_RANGE = 640.0;

	private ThunderTimeline() {
	}

	/** Radius of the crater the channel blasts out of the ground. */
	public static int coreRadius(int radius) {
		return Math.max(4, Math.round(radius * 0.42F));
	}

	/** How far out the scar and the arcs reach. */
	public static int scarRadius(int radius) {
		return Math.round(radius * 1.5F);
	}

	/** Closest the shooter may stand to the target: outside the scar. */
	public static double minRange(int radius) {
		return Math.max(MIN_RANGE, Math.ceil(scarRadius(radius)));
	}

	/** Height of the storm's cloud base over a target at {@code targetY}, under the world's ceiling. */
	public static int cloudBase(int targetY, int topY) {
		return Math.max(targetY + 40, Math.min(topY - 24, Math.max(targetY + 110, 196)));
	}

	/**
	 * Radius of the storm vortex over the target at {@code t}: it boils out of the sky where the call goes into it, to a
	 * third of its size in under a second (so the camera has a storm to rise into), then winds up slowly to the stroke.
	 */
	public static double vortexRadius(double t, int radius) {
		double burst = smooth((t - CALL) / 16.0);
		double grow = smooth((t - CALL) / (double) (INBOUND - CALL));
		return radius * 3.2 * (0.35 * burst + 0.65 * grow);
	}

	/**
	 * How far down from the cloud base the stepped leader has reached at {@code t}, 0 to 1: it jumps a step at a time,
	 * slow at first and quickening as it nears the ground.
	 */
	public static double leaderReach(double t) {
		if (t < INBOUND) {
			return 0.0;
		}
		if (t >= STROKE) {
			return 1.0;
		}
		double p = (t - INBOUND) / (STROKE - INBOUND);
		// Steps come faster as it goes: the step count grows with p squared-ish.
		double steps = Math.floor(LEADER_STEPS * (0.35 * p + 0.65 * p * p) + 1.0e-6);
		return Math.min(1.0, steps / LEADER_STEPS);
	}

	/** Path distance the scar has burned out to, {@code e} ticks after the stroke. */
	public static double scarFront(double e) {
		return e < 0 ? 0.0 : e * SCAR_SPEED;
	}

	/** Brightness of the channel {@code e} ticks after the stroke: the stroke, each restrike, the glow between. */
	public static double channelFlash(double e) {
		double b = e >= 0 ? Math.exp(-e / 1.6) : 0.0;
		for (int restrike : RESTRIKES) {
			double since = e - (restrike - STROKE);
			if (since >= 0) {
				b = Math.max(b, 0.8 * Math.exp(-since / 1.3));
			}
		}
		return b;
	}

	public static double clamp01(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	public static double smooth(double x) {
		x = clamp01(x);
		return x * x * (3 - 2 * x);
	}
}
