package io.github.kortev.shootingstar.test;

import net.fabricmc.api.ClientModInitializer;

/** The clip of Forever (filled in with the visuals). */
public class ForeverSelfTest implements ClientModInitializer {
	public static float lens() {
		return 0.0F;
	}

	public static boolean active() {
		return "forever".equals(System.getProperty("shootingstar.selftest"));
	}

	@Override
	public void onInitializeClient() {
	}
}
