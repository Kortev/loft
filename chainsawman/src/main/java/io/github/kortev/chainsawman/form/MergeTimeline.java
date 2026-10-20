package io.github.kortev.chainsawman.form;

/**
 * The first death, when Pochita becomes your heart ({@link Merge}), beat by beat in ticks; the server, the pictures and
 * the sound (tools/gen_chainsaw_sounds.py, form_merge) all read it. Then the cord pulls itself: the transformation.
 *
 * <pre>
 *   0  FALL       you go over backwards, dead; the world goes dark and quiet but for a ringing
 *  20  COMING     Pochita comes, limping, out of where he was (or out of your pack)
 *  44  CLIMB      he climbs up onto your chest
 *  52  ON_CHEST   he stands on you and looks at your face
 *  64  SINK       he sinks into your chest: a burst of blood, impact frames, the cord coming out of you
 *  76  BEAT       your heart starts: four beats, closer and closer (BEAT, +10, +17, +22)
 * 100  RISE       you sit up and get to your feet
 * 110  END        the cord pulls itself: the transformation
 * </pre>
 */
public final class MergeTimeline {
	public static final int FALL = 0;
	public static final int FELL = 6;
	public static final int COMING = 20;
	public static final int CLIMB = 44;
	public static final int ON_CHEST = 52;
	public static final int SINK = 64;
	public static final int SUNK = 74;
	public static final int BEAT = 76;
	public static final int[] BEATS = {BEAT, BEAT + 10, BEAT + 17, BEAT + 22};
	public static final int RISE = 100;
	public static final int END = 110;
	/** How far from your body Pochita sets off at most (blocks), however far away he was. */
	public static final double FROM = 6.0;

	private MergeTimeline() {
	}
}
