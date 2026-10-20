package io.github.kortev.chainsawman.form;

/**
 * The first death, when Pochita becomes your heart ({@link Merge}): the anime's scene, long, since it only ever happens
 * once. Beat by beat in ticks; the server, the pictures, the sound (tools/gen_chainsaw_sounds.py, form_merge) and
 * Pochita's lines all read it. Then you stand there until you pull the cord ({@link Merge#WAIT}): the transformation.
 *
 * <pre>
 *   0  FALL      struck down: you go over backwards, hard; the colour drains out of the world, black bars close in
 *   8  DEAD      lying there: high over you in the grey and the quiet, a pool of blood spreading from under you
 *  60  DARK      the world goes out round you: black, but for you lying in a last light
 *  90  COMING    Pochita comes hobbling to you out of the dark
 * 140  SNIFF     at your face: he sniffs at you, nudges you, whimpers
 * 175  CLIMB     he hops up onto your chest and settles
 * 190  TALK      he talks to you, standing on you: his five lines (LINES, each LINE ticks), back and forth between him
 *                and your face
 * 505  BOW       he lowers his head to your chest
 * 520  SINK      he goes down into your chest: a white flash, impact frames, a fountain of blood
 * 534  SUNK      the cord pushes out of your chest
 * 550  BEAT      your heart starts: six beats, closer and harder, each lifting the dark and bringing the colour back
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
	/** When each of Pochita's lines starts, and how long each lasts. */
	public static final int[] LINES = {200, 262, 324, 386, 448};
	public static final int LINE = 55;
	public static final int BOW = 505;
	public static final int SINK = 520;
	public static final int SUNK = 534;
	public static final int BEAT = 550;
	public static final int[] BEATS = {BEAT, BEAT + 12, BEAT + 22, BEAT + 30, BEAT + 36, BEAT + 41};
	public static final int RISE = 596;
	public static final int END = 626;
	/** How far from your body Pochita sets off at most (blocks), however far away he was. */
	public static final double FROM = 7.0;

	private MergeTimeline() {
	}
}
