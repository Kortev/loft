package io.github.kortev.chainsawman.client;

import net.minecraft.util.math.MathHelper;

/** What this client has been told about the Eternity Devil's hotel: the loop's fog (blocks, 0 off), eased in and out. */
public final class ClientHotel {
	private static float fogTarget;
	private static float fogNow;
	private static float fogPrev;

	private ClientHotel() {
	}

	public static void fog(float end) {
		fogTarget = end;
	}

	/** One tick: the fog's closing in or opening out. */
	public static void tick() {
		fogPrev = fogNow;
		float to = fogTarget > 0.0F ? 1.0F : 0.0F;
		fogNow += MathHelper.clamp(to - fogNow, -0.1F, 0.1F);
	}

	/** How closed in the corridor is, 0 to 1. */
	public static float fogAmount(float delta) {
		return MathHelper.lerp(delta, fogPrev, fogNow);
	}

	/** The distance the fog ends at when fully closed. */
	public static float fogEnd() {
		return fogTarget > 0.0F ? fogTarget : 11.0F;
	}

	public static void clear() {
		fogTarget = 0.0F;
		fogNow = 0.0F;
		fogPrev = 0.0F;
	}
}
