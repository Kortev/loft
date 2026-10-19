package io.github.kortev.shootingstar.client.gfx;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;

/**
 * The world's frame as it was drawn, which the passes over the world draw with: kept, so that they can be drawn after a
 * shader pack has finished the frame as well as at its end ({@link WorldPasses}). Named as the world's own drawing names
 * them, so a pass reads the same either way.
 */
public record WorldFrame(ClientWorld world, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta) {
	static WorldFrame of(WorldRenderContext context) {
		return new WorldFrame(context.world(), context.camera(), new Matrix4f(context.positionMatrix()),
				new Matrix4f(context.projectionMatrix()), context.tickCounter().getTickDelta(false));
	}
}
