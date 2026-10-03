package io.github.kortev.shootingstar.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Remembers the last frame's world matrices so HUD code can pin labels to world positions. */
public final class WorldProjector {
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static Vec3d camera = Vec3d.ZERO;
	private static boolean valid;

	private WorldProjector() {
	}

	public static void capture(WorldRenderContext context) {
		VIEW.set(context.positionMatrix());
		PROJECTION.set(context.projectionMatrix());
		camera = context.camera().getPos();
		valid = true;
	}

	public static Vec3d camera() {
		return camera;
	}

	/** World position to GUI coordinates; z is the view depth. Null when behind the camera. */
	@Nullable
	public static Vector3f project(double x, double y, double z, float guiWidth, float guiHeight) {
		if (!valid) {
			return null;
		}
		Vector4f v = new Vector4f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1.0F);
		VIEW.transform(v);
		PROJECTION.transform(v);
		if (v.w <= 1.0E-3F) {
			return null;
		}
		float nx = v.x / v.w;
		float ny = v.y / v.w;
		return new Vector3f((nx * 0.5F + 0.5F) * guiWidth, (0.5F - ny * 0.5F) * guiHeight, v.w);
	}
}
