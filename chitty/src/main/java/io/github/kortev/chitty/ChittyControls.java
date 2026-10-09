package io.github.kortev.chitty;

/**
 * What the driver is asking of the car this tick. {@code forward} and {@code turn} are -1, 0 or 1 (turn is positive to
 * the left); {@code up} is the jump key (pull the lever: take off, climb), {@code down} the sprint key (dive), and
 * {@code rev} the rev key (her foot on the throttle out of gear, standing).
 */
public record ChittyControls(int forward, int turn, boolean up, boolean down, boolean rev) {
	public static final ChittyControls NONE = new ChittyControls(0, 0, false, false, false);

	/** Controls without the rev key (the airship's). */
	public ChittyControls(int forward, int turn, boolean up, boolean down) {
		this(forward, turn, up, down, false);
	}

	public byte pack() {
		int bits = 0;
		if (forward > 0) bits |= 1;
		if (forward < 0) bits |= 2;
		if (turn > 0) bits |= 4;
		if (turn < 0) bits |= 8;
		if (up) bits |= 16;
		if (down) bits |= 32;
		if (rev) bits |= 64;
		return (byte) bits;
	}

	public static ChittyControls unpack(byte bits) {
		int forward = ((bits & 1) != 0 ? 1 : 0) - ((bits & 2) != 0 ? 1 : 0);
		int turn = ((bits & 4) != 0 ? 1 : 0) - ((bits & 8) != 0 ? 1 : 0);
		return new ChittyControls(forward, turn, (bits & 16) != 0, (bits & 32) != 0, (bits & 64) != 0);
	}
}
