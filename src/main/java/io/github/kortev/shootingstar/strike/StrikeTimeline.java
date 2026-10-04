package io.github.kortev.shootingstar.strike;

/**
 * Tick offsets of a strike, counted from the moment the uplink gets a kinetic lock. The server
 * only cares about {@link #IMPACT} and {@link #END}; the rest paces the client's uplink feed.
 */
public final class StrikeTimeline {
	/** Kinetic lock: beam and reticle on the target. */
	public static final int LOCK = 0;
	/** The shooter's camera rises over the target. */
	public static final int RISE = 26;
	/** Feed: pull back to Earth from orbit. */
	public static final int ORBIT = 50;
	/** Feed: relay jump out to Jupiter. */
	public static final int RELAY = 78;
	/** Feed: the ring around Jupiter powers up. */
	public static final int WAKE = 102;
	/** Feed: the round seats in the breech. */
	public static final int LOADING = 146;
	/** Feed: seven accelerating laps of the ring. */
	public static final int LAPS = 166;
	/** Feed: out of the muzzle in slow motion, shedding the sabot. */
	public static final int RELEASE = 250;
	/** Feed: the round crosses the main belt. */
	public static final int DEBRIS = 280;
	/** Feed: re-entry over Sol-3. */
	public static final int TERMINAL = 302;
	/** Back in the world: the round is a star over the target. */
	public static final int INBOUND = 336;
	public static final int IMPACT = 360;
	/** Stylised impact frame for the shooter. */
	public static final int IMPACT_FRAME_END = IMPACT + 14;
	/** Wide shot of the spire for the shooter. */
	public static final int WIDE_END = IMPACT + 90;
	/** The shooter's camera eases back to their own eyes. */
	public static final int CAMERA_END = WIDE_END + 24;
	public static final int END = IMPACT + 200;

	public static final int LAP_COUNT = 7;
	public static final double ENTRY_VELOCITY = 0.0183;
	public static final double EXIT_VELOCITY = 0.9612;

	private StrikeTimeline() {
	}

	/** Progress through the laps phase, 0..1. */
	public static double lapProgress(double age) {
		return clamp01((age - LAPS) / (RELEASE - LAPS));
	}

	/** Velocity as a fraction of c under constant acceleration through the laps. */
	public static double lapVelocity(double progress) {
		return ENTRY_VELOCITY + (EXIT_VELOCITY - ENTRY_VELOCITY) * progress;
	}

	/** Distance covered so far as a fraction of all seven laps. */
	public static double lapDistance(double progress) {
		double a = EXIT_VELOCITY - ENTRY_VELOCITY;
		double covered = ENTRY_VELOCITY * progress + 0.5 * a * progress * progress;
		double total = ENTRY_VELOCITY + 0.5 * a;
		return covered / total;
	}

	/** Current lap, 1..7. */
	public static int lapNumber(double progress) {
		return Math.min(LAP_COUNT, 1 + (int) Math.floor(lapDistance(progress) * LAP_COUNT));
	}

	public static double clamp01(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}
}
