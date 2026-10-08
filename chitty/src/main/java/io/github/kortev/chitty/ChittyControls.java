package io.github.kortev.chitty;

/**
 * What the driver is asking of the car this tick. {@code forward} and {@code turn} are -1, 0 or 1 (turn is positive to
 * the left); {@code up} is the jump key (pull the lever: take off, climb), {@code down} the sprint key (dive).
 */
public record ChittyControls(int forward, int turn, boolean up, boolean down) {
	public static final ChittyControls NONE = new ChittyControls(0, 0, false, false);

	public byte pack() {
		int bits = 0;
		if (forward > 0) bits |= 1;
		if (forward < 0) bits |= 2;
		if (turn > 0) bits |= 4;
		if (turn < 0) bits |= 8;
		if (up) bits |= 16;
		if (down) bits |= 32;
		return (byte) bits;
	}

	public static ChittyControls unpack(byte bits) {
		int forward = ((bits & 1) != 0 ? 1 : 0) - ((bits & 2) != 0 ? 1 : 0);
		int turn = ((bits & 4) != 0 ? 1 : 0) - ((bits & 8) != 0 ? 1 : 0);
		return new ChittyControls(forward, turn, (bits & 16) != 0, (bits & 32) != 0);
	}
}
