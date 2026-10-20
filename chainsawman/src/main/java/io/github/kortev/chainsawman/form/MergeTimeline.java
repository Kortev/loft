package io.github.kortev.chainsawman.form;

/**
 * The first death, when Pochita becomes your heart ({@link Merge}): the anime's scene, long, since it only ever happens
 * once. Beat by beat in ticks; the server, the pictures, the sound (tools/gen_chainsaw_sounds.py, form_merge) and the
 * voices all read it. Then you stand there until you pull the cord ({@link Merge#WAIT}): the transformation.
 *
 * <pre>
 *   0  FALL      struck down: you go over backwards, hard; the colour drains out of the world, black bars close in
 *   8  DEAD      lying there: high over you in the grey and the quiet, a pool of blood spreading from under you
 *  60  DARK      the world falls away: the dream, a black void, you lying in a pool of light, motes drifting
 *  90  COMING    Pochita comes hobbling to you out of the dark
 * 140  SNIFF     at your face: he sniffs at you, nudges you, whimpers
 * 175  CLIMB     he hops up onto your chest and settles
 * 190  TALK      the dream's talk, him standing on you (LINES, LINE_LENGTHS; the anime's own voices, cut from the clip
 *                kortev chose): you ask if he took your body; he loved hearing your dreams (your life with him in manga
 *                panels, MONTAGE); this is a contract; show me your dream
 * 495  LEAP      he crouches, and leaps up off you, slow, and dives down into your chest
 * 520  SINK      in: a white flash, impact frames, a fountain of blood; you cry his name, your hand reaching after him
 *                into the light
 * 540  HEART     inside your chest, seen through: him curled up there as your heart; it starts (BEAT, 550)
 * 566  SUNK      the cord tears out of your chest
 * 572  WAKE      the last beats bring the world back: the dream lifts, the colour comes back
 * 596  RISE      you get up: sit up, legs out in front; rock forward into a crouch, a knee down; stand
 * 626  END       standing, the cord hanging out of your chest: "Pull the cord" in the bar, until you do
 * </pre>
 */
public final class MergeTimeline {
	public static final int FALL = 0;
	public static final int FELL = 7;
	public static final int DEAD = 8;
	public static final int DARK = 60;
	public static final int COMING = 90;
	public static final int SNIFF = 140;
	public static final int CLIMB = 175;
	public static final int ON_CHEST = 190;
	public static final int TALK = 190;
	/**
	 * When each line of the talk starts (ticks), as paced in the anime: yours ("Pochita... did you take my body?"), his
	 * three, and yours again, crying his name as he goes into you (at SINK); how long each stays under the picture; and
	 * which are his. Their voices are sounds/form_merge_voice_1..5.ogg.
	 */
	public static final int[] LINES = {205, 311, 397, 445, 520};
	public static final int[] LINE_LENGTHS = {104, 86, 46, 56, 40};
	public static final boolean[] POCHITA_SAYS = {false, true, true, true, false};
	/** The memories, over his second line: when they start and end. */
	public static final int MONTAGE = 311;
	public static final int MONTAGE_END = 397;
	public static final int LEAP = 495;
	public static final int SINK = 520;
	public static final int HEART = 540;
	public static final int SUNK = 566;
	public static final int BEAT = 550;
	public static final int[] BEATS = {BEAT, BEAT + 12, BEAT + 22, BEAT + 30, BEAT + 36, BEAT + 41};
	public static final int WAKE = 572;
	public static final int RISE = 596;
	public static final int END = 626;
	/** How far from your body Pochita sets off at most (blocks), however far away he was. */
	public static final double FROM = 7.0;

	private MergeTimeline() {
	}
}
