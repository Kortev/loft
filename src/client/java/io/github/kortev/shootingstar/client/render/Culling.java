package io.github.kortev.shootingstar.client.render;

import net.minecraft.client.MinecraftClient;

/**
 * Pauses the world's section occlusion culling while the strike needs it off. Culling decides which
 * sections to draw (and rebuild) from what each one looked like when it was last built, so it loses
 * track of the world when the camera flies up into sky it has never been culled from, and of ground
 * that was buried a moment ago and is suddenly open to the sky. Either way whole sections go missing
 * and the sky shows through the world.
 */
public final class Culling {
	private static boolean paused;
	private static boolean saved;

	private Culling() {
	}

	public static void update(MinecraftClient client, boolean pause) {
		if (pause == paused || client.worldRenderer == null) {
			return;
		}
		if (pause) {
			saved = client.chunkCullingEnabled;
			client.chunkCullingEnabled = false;
		} else {
			client.chunkCullingEnabled = saved;
		}
		paused = pause;
		if (client.world != null) {
			client.worldRenderer.scheduleTerrainUpdate();
		}
	}

	public static void reset(MinecraftClient client) {
		update(client, false);
	}
}
