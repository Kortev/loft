package io.github.kortev.shootingstar.client.thunder;

import net.minecraft.util.math.MathHelper;

/**
 * How brightly Mjölnir's runes burn when it is drawn: 1 is their own steady glow, 0 puts them out, and above 1 they
 * blaze, the glow drawn over itself again (it adds light, so twice is twice as bright). It holds for every hammer
 * drawn until it is set back, so whoever turns it up draws the hammer and then calls {@link #reset()}: HammerRaise,
 * say, as the charge builds in the hammer held over the shooter's head.
 */
public final class HammerGlow {
	/** The runes' own glow. */
	public static final float STEADY = 1.0F;
	/** As bright as they go: this many times their own glow. */
	public static final float BLAZING = 3.0F;

	private static float level = STEADY;

	private HammerGlow() {
	}

	/** Sets how brightly the runes burn for the hammers drawn from now on, from 0 to {@link #BLAZING}. */
	public static void boost(float glow) {
		level = MathHelper.clamp(glow, 0.0F, BLAZING);
	}

	/** Back to their own steady glow. */
	public static void reset() {
		level = STEADY;
	}

	/** How brightly the runes burn now. */
	public static float level() {
		return level;
	}
}
