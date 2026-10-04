package io.github.kortev.shootingstar.client.render;

import net.minecraft.client.MinecraftClient;

/**
 * Pauses the world's section occlusion culling while the strike's camera shots fly. Culling decides which
 * sections to draw from what each one looked like when it was last built, so it loses track of the world
 * when the camera flies up into sky it has never been culled from, and whole sections go missing. It stays
 * on while a crater settles: with it off every section in view becomes a candidate for a mesh build, and
 * the queue that piles up starves the freshly opened ground of the rebuilds it needs.
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
