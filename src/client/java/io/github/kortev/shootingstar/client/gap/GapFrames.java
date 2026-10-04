package io.github.kortev.shootingstar.client.gap;

/**
 * The impact frames, ticks from {@link io.github.kortev.shootingstar.gap.GapTimeline#FRAMES}: a hard cut on every
 * one, each with its own shot, ink and extras. A frame with panels splits the screen into three looks at once.
 */
public final class GapFrames {
	public static final int WHITE = 1;
	public static final int BLACK = 2;
	public static final int RED = 3;
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
			new Frame(4, BLACK, GapCamera.EXTREME, CRACK, null),
			new Frame(8, RED, GapCamera.CONTACT, LINES, null),
			new Frame(14, NEGATIVE, GapCamera.WIDE, 0, null),
			new Frame(20, WHITE, GapCamera.SIDE, SLAB, null),
			new Frame(25, BLACK, GapCamera.LOW, LINES, null),
			new Frame(30, 0, GapCamera.CONTACT, 0, new int[] {RED, WHITE, BLACK}),
			new Frame(43, RED, GapCamera.WIDE, CRACK | DOUBLE, null),
			new Frame(49, WHITE, GapCamera.EXTREME, CRACK, null),
			new Frame(53, BLACK, GapCamera.CONTACT, DOUBLE, null),
			new Frame(58, NEGATIVE, GapCamera.EYES, 0, null),
			new Frame(66, 0, GapCamera.LOW, 0, new int[] {BLACK, NEGATIVE, WHITE}),
			new Frame(79, WHITE, GapCamera.WIDE, LINES, null),
			new Frame(84, RED, GapCamera.SIDE, SLAB | CRACK, null),
			new Frame(89, BLACK, GapCamera.EXTREME, LINES, null),
			new Frame(94, NEGATIVE, GapCamera.LOW, DOUBLE, null),
			new Frame(100, 0, GapCamera.CONTACT, 0, new int[] {WHITE, RED, BLACK}),
			new Frame(112, WHITE, GapCamera.CONTACT, CRACK | LINES, null),
			new Frame(116, BLACK, GapCamera.WIDE, CRACK, null),
			new Frame(120, RED, GapCamera.EXTREME, LINES | CRACK, null),
			new Frame(124, WHITE, GapCamera.EXTREME, 0, null),
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
