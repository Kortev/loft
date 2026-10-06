package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.gap.VoidWorld;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;

/** The sky of the void: nothing. Without this it would be the End's. */
public final class VoidSky {
	private VoidSky() {
	}

	public static void register() {
		DimensionRenderingRegistry.registerSkyRenderer(VoidWorld.KEY, context -> {
		});
	}
}
