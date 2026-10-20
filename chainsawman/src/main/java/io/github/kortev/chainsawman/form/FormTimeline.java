package io.github.kortev.chainsawman.form;

/**
 * The transformation's beats, in ticks from the cord key: the server, every client's pictures and the camera read the
 * same numbers, and the sounds (tools/gen_chainsaw_sounds.py) are cut to them.
 *
 * <pre>
 *  0  GRAB        the camera cuts round in front of him; his hand comes up to the cord on his chest
 *  4  REACH       hand on the handle
 *  4-12 YANK      one long hard pull: the cord stretches out of his chest, he leans back, his head snaps back
 * 13  SNAP        the cord snaps back in
 * 14  CATCH       the engine catches; misses; COUGH (17) catches again: smoke and a jolt on each
 * 16  PUNCH       the saw's bar punches out through the forehead of his own head
 * 20  BURST       his head splits open in blood, the halves peeling away (HEAD_OPEN), and the chainsaw head is there;
 *                 its saw grows on out to its length (HEAD_GROWN)
 * 23  ARMS        the arms' saws push out through his fists and slide out past them (ARMS_GROWN)
 * 36  ROAR        a cut to his face, close; the anime's impact frames, red and black; the engine screams; he roars
 * 52  ROAR_END    the camera pulls back
 * 62  END         back in first person, the form on
 * </pre>
 */
public final class FormTimeline {
	public static final int GRAB = 0;
	public static final int REACH = 4;
	public static final int YANK = 12;
	public static final int SNAP = 13;
	public static final int CATCH = 14;
	public static final int COUGH = 17;
	public static final int PUNCH = 16;
	public static final int BURST = 20;
	public static final int HEAD_OPEN = 24;
	public static final int HEAD_GROWN = 27;
	public static final int ARMS = 23;
	public static final int ARMS_GROWN = 32;
	public static final int ROAR = 36;
	/** How long the impact frames last, from ROAR. */
	public static final int IMPACT = 4;
	public static final int ROAR_END = 52;
	public static final int END = 62;

	/*
	 * Turning back (the cord pulled again in the form), in ticks from the pull:
	 *  0  BACK_ARMS     the engine dies; the arms' saws slide back in through the fists
	 *  3  BACK_HEAD     the head's saw slides back into the head, the head bowing
	 * 14  BACK_SPLASH   the chainsaw head comes away in a splash of blood: your own head, thrown back
	 * 24  BACK_END      upright again
	 */
	public static final int BACK_ARMS = 0;
	public static final int BACK_ARMS_IN = 9;
	public static final int BACK_HEAD = 3;
	public static final int BACK_HEAD_IN = 12;
	public static final int BACK_SPLASH = 14;
	public static final int BACK_END = 24;

	private FormTimeline() {
	}
}
