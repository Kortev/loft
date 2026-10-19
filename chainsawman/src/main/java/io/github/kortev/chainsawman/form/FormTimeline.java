package io.github.kortev.chainsawman.form;

/**
 * The transformation's beats, in ticks from the pull: what the server does and what every client shows read the same
 * numbers, and the sounds (tools/gen_chainsaw_sounds.py: CATCH_AT, BURST_AT) are cut to them.
 */
public final class FormTimeline {
	/** The hand comes up to the cord on the chest. */
	public static final int REACH = 4;
	/** The cord is yanked out, the arm flung out with it. */
	public static final int YANK = 9;
	/** The engine catches. */
	public static final int CATCH = 6;
	/** The saws burst out of head and arms in a spray of blood: the form is on. */
	public static final int BURST = 17;
	/** The arm has come back down. */
	public static final int SETTLE = 24;

	private FormTimeline() {
	}
}
