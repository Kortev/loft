package io.github.kortev.shootingstar.client.gap;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Universe;
import io.github.kortev.shootingstar.gap.VoidWorld;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The sky of Ginnungagap, the void between universes: the other universes all round, each a block of galaxies in
 * its glass, near and far, turning slowly. Drawn as if infinitely far off (it turns with the camera but never moves
 * with it), then the depth is cleared so the floor and everyone on it draw over it.
 */
public final class VoidSky {
	private static final int UNIVERSES = 18;
	private static final float DISTANCE = 60.0F;
	private static final Vector3f[] TINTS = {new Vector3f(1.0F, 0.75F, 1.0F), new Vector3f(0.55F, 0.8F, 1.0F),
			new Vector3f(1.0F, 0.78F, 0.5F), new Vector3f(0.8F, 0.8F, 1.0F)};
	private static final Vector3f[] DIRECTIONS = new Vector3f[UNIVERSES];
	private static final float[] SIZES = new float[UNIVERSES];
	private static final float[] SPINS = new float[UNIVERSES];

	static {
		Random random = Random.create(4096113L);
		for (int i = 0; i < UNIVERSES; i++) {
			// Spread round the sky (a Fibonacci sphere), mostly above the floor's horizon, a few low.
			double y = 1.0 - (i + 0.5) / UNIVERSES * 1.3;
			double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
			double a = i * 2.399963;
			DIRECTIONS[i] = new Vector3f((float) (Math.cos(a) * r), (float) y, (float) (Math.sin(a) * r)).normalize();
			SIZES[i] = 2.5F + random.nextFloat() * 6.5F;
			SPINS[i] = (random.nextFloat() - 0.5F) * 0.02F;
		}
	}

	private VoidSky() {
	}

	public static void register() {
		DimensionRenderingRegistry.registerSkyRenderer(VoidWorld.KEY, VoidSky::render);
	}

	private static void render(WorldRenderContext context) {
		if (!Shaders.ready()) {
			return;
		}
		Universe.ensure();
		Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
		float w = main.textureWidth;
		float h = main.textureHeight;
		float time = context.world() == null ? 0.0F : (float) (context.world().getTime() + context.tickCounter().getTickDelta(false));
		// Turned with the camera only: these are infinitely far off.
		Matrix4f view = new Matrix4f(context.positionMatrix());
		Matrix4f proj = new Matrix4f(context.projectionMatrix());
		for (int i = 0; i < UNIVERSES; i++) {
			Vector3f at = new Vector3f(DIRECTIONS[i]).mul(DISTANCE);
			Matrix4f model = new Matrix4f().translation(at).rotateY(time * SPINS[i] + i).rotateX(i * 0.7F).scale(SIZES[i]);
			int detail = SIZES[i] > 6.0F ? Universe.MID : Universe.LOW;
			Universe.draw(new Matrix4f(view).mul(model), proj, w, h, detail, i % 48, 1.4F, 1.6F, TINTS[i % TINTS.length], 0.45F, 0.6F,
					0xE6DCFF, 0.0F);
		}
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		// The world draws over all of it.
		RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
	}
}
