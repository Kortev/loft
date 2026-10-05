package io.github.kortev.shootingstar.client.gap;

/**
 * The impact frames, ticks from {@link io.github.kortev.shootingstar.gap.GapTimeline#FRAMES}: a hard cut on every
 * one, each with its own shot, ink and extras. A frame with panels splits the screen into three looks at once.
 */
public final class GapFrames {
	public static final int WHITE = 1;
	public static final int BLACK = 2;
	public static final int VIOLET = 3;
	public static final int NEGATIVE = 4;

	public static final int LINES = 1;
	public static final int CRACK = 2;
	public static final int DOUBLE = 4;
	public static final int SLAB = 8;

	public record Frame(int start, int style, int shot, int extras, int[] panels) {
		public boolean paneled() {
			return panels != null;
		}
	}

	private static final Frame[] FRAMES = {
			new Frame(0, WHITE, GapCamera.EXTREME, LINES, null),
			new Frame(3, BLACK, GapCamera.EXTREME, CRACK, null),
			new Frame(6, VIOLET, GapCamera.CONTACT, LINES, null),
			new Frame(9, NEGATIVE, GapCamera.WIDE, 0, null),
			new Frame(13, 0, GapCamera.CONTACT, 0, new int[] {VIOLET, WHITE, BLACK}),
			new Frame(21, WHITE, GapCamera.LOW, LINES | CRACK, null),
			new Frame(25, BLACK, GapCamera.SIDE, DOUBLE, null),
			new Frame(28, WHITE, GapCamera.EXTREME, SLAB, null),
	};


	private GapFrames() {
	}

	/** The frame showing {@code e} ticks into the impact frames. */
	public static Frame at(double e) {
		int i = 0;
		while (i + 1 < FRAMES.length && e >= FRAMES[i + 1].start()) {
			i++;
		}
		return FRAMES[i];
	}

	public static int index(double e) {
		int i = 0;
		while (i + 1 < FRAMES.length && e >= FRAMES[i + 1].start()) {
			i++;
		}
		return i;
	}
}
