package io.github.kortev.chainsawman.form;

/**
 * The first death, when Pochita becomes your heart ({@link Merge}): the anime's scene, long, since it only ever happens
 * once, cut as the storyboard kortev approved (2026-10-20). Beat by beat in ticks; the server, the pictures, the sound
 * (tools/gen_chainsaw_sounds.py, form_merge) and the voices all read it. Then you stand there until you pull the cord
 * ({@link Merge#WAIT}): the transformation.
 *
 * <pre>
 *   0  FALL      cut 1: low at the ground, you collapse; the colour drains out of the world
 *  30  OVER      cut 2: straight down over you, rising: blood spreads out from under you
 *  60  DARK      cut 3: higher; the world goes black round the pool, only you lit
 *  90  COMING    cut 4: behind Pochita at his height, him trotting out of the dark to you
 * 140  SNIFF     cut 5: beside your head: he nudges your cheek with his nose, whimpers
 * 175  CLIMB     cut 6: he hops up and stands on your chest (ON_CHEST, 190)
 * 205  LINES[0]  cut 7: over his shoulder onto your face: "Pochita... did you take my body, like I said?"
 * 260  LISTEN    cut 8: your view, up past your chest at him sitting on you, head tilted
 * 311  MONTAGE   cut 9: the memories, over "I always loved hearing you talk about your dreams" (to MONTAGE_END)
 * 397  LINES[2]  cut 10: your view again, easing in: "Here's the deal. I'll give you my heart..."
 * 445  LINES[3]  cut 11: close on his face: "And in exchange, I want you to show me your dream."
 * 495  LEAP      cut 12: low beside you, looking up: he springs high off you into the light
 * 520  SINK      cut 13: your view, your hand reaching up after him: "POCHITA?!"; he dives at you; a white flash
 * 540  PLUNGE    cut 14: straight down on your chest: he plunges in, blood bursts; his tail, the cord, left in you (SUNK)
 * 572  WAKE      cut 15: black; your heart starts, three beats (BEATS), ドクン lettered on each
 * 596  EYES      cut 16: your view: your eyes flutter and open on the real sky (the gasp, GASP)
 * 608  RISE      cut 17: low in front: you sit up and get to your feet, the cord swinging on your chest
 * 626  END       cut 18: in front at chest height, easing in on the cord: "Pull the cord", until you do
 * </pre>
 */
public final class MergeTimeline {
	public static final int FALL = 0;
	public static final int FELL = 7;
	public static final int DEAD = 8;
	public static final int OVER = 30;
	public static final int DARK = 60;
	public static final int COMING = 90;
	public static final int SNIFF = 140;
	public static final int CLIMB = 175;
	public static final int ON_CHEST = 190;
	public static final int TALK = 190;
	/**
	 * When each line of the talk starts (ticks), as paced in the anime: yours ("Pochita... did you take my body?"), his
	 * three, and yours again, crying his name as he dives into you (at SINK); how long each stays under the picture; and
	 * which are his. Their voices are sounds/form_merge_voice_1..5.ogg.
	 */
	public static final int[] LINES = {205, 311, 397, 445, 520};
	public static final int[] LINE_LENGTHS = {104, 86, 46, 56, 40};
	public static final boolean[] POCHITA_SAYS = {false, true, true, true, false};
	/** Your view, up at him on your chest, as your line finishes. */
	public static final int LISTEN = 260;
	/** The memories, over his second line: when they start and end. */
	public static final int MONTAGE = 311;
	public static final int MONTAGE_END = 397;
	public static final int LEAP = 495;
	public static final int SINK = 520;
	/** He plunges into your chest; and is in, the cord's handle all that's left of him (SUNK). */
	public static final int PLUNGE = 540;
	public static final int HEART = PLUNGE;
	public static final int SUNK = 546;
	public static final int WAKE = 572;
	/** Your heart starting, in the black: three beats, the last quickest. */
	public static final int[] BEATS = {574, 584, 592};
	public static final int BEAT = 574;
	/** Your eyes opening on the sky; the gasp as they open. */
	public static final int EYES = 596;
	public static final int GASP = 600;
	public static final int RISE = 608;
	public static final int END = 626;
	/** How far from your body Pochita sets off at least and at most (blocks), however near or far he was. */
	public static final double FROM_NEAR = 4.5;
	public static final double FROM = 7.0;

	private MergeTimeline() {
	}
}
