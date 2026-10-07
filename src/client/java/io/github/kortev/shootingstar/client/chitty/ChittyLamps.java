package io.github.kortev.shootingstar.client.chitty;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.chitty.ChittyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Chitty's lamps at night: while she runs in the dark (at night, in the rain, underground), a soft beam of light
 * reaches out ahead of each headlamp and each spotlight, brightest at the glass and fading away down its length. Each
 * beam is a few crossed planes about its middle, so it reads as a shaft of light from any side.
 */
final class ChittyLamps {
	private static final Identifier BEAM = ShootingStar.id("chitty_beam");
	/** The beams: which lamps (markers in the mesh), how long, how wide at the glass and at the far end, how far they dip (degrees) and how bright. */
	private static final String[] LAMPS = {"beam_lamp_l", "beam_lamp_r", "beam_spot_l", "beam_spot_r"};
	private static final float[][] SHAPES = {{7.0F, 0.10F, 1.3F, 4.0F, 0.40F}, {7.0F, 0.10F, 1.3F, 4.0F, 0.40F},
			{9.0F, 0.06F, 0.9F, 7.0F, 0.28F}, {9.0F, 0.06F, 0.9F, 7.0F, 0.28F}};
	private static final int PLANES = 4;
	private static boolean textureMade;

	private ChittyLamps() {
	}

	/** Draws the beams, in her own frame (the renderer's matrices, turned and tipped with her). */
	static void draw(ChittyEntity car, ChittyMesh mesh, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers,
			int light) {
		float dark = darkness(car.getWorld(), tickDelta, light);
		if (dark < 0.01F || !car.isEngineRunning()) {
			return;
		}
		makeTexture();
		VertexConsumer out = buffers.getBuffer(RenderLayer.getEntityTranslucentEmissive(BEAM));
		MatrixStack.Entry entry = matrices.peek();
		for (int i = 0; i < LAMPS.length; i++) {
			Vec3d at = mesh.markers.get(LAMPS[i]);
			if (at != null) {
				float[] s = SHAPES[i];
				beam(out, entry, at, s[0], s[1], s[2], s[3], s[4] * dark);
			}
		}
	}

	/** How dark it is where she is: 0 in daylight under the open sky, 1 at night, deep in the rain or underground. */
	private static float darkness(World world, float tickDelta, int light) {
		float open = LightmapTextureManager.getSkyLightCoordinates(light) / 15.0F;
		float day = MathHelper.clamp(MathHelper.cos(world.getSkyAngleRadians(tickDelta)) * 2.0F + 0.5F, 0.0F, 1.0F);
		float daylight = open * day * (1.0F - 0.5F * world.getRainGradient(tickDelta));
		float x = MathHelper.clamp((0.6F - daylight) / 0.4F, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	private static void beam(VertexConsumer out, MatrixStack.Entry entry, Vec3d at, float length, float r0, float r1, float dip,
			float alpha) {
		Matrix4f m = entry.getPositionMatrix();
		float d = dip * MathHelper.RADIANS_PER_DEGREE;
		// Straight ahead (her +z), dipping a little; across it, her side and her up, dipped with it.
		Vector3f ahead = new Vector3f(0.0F, -MathHelper.sin(d), MathHelper.cos(d));
		Vector3f up = new Vector3f(0.0F, MathHelper.cos(d), MathHelper.sin(d));
		Vector3f side = new Vector3f(1.0F, 0.0F, 0.0F);
		Vector3f origin = new Vector3f((float) at.x, (float) at.y, (float) at.z);
		// Brightest at the glass, half as bright a third of the way along, gone at the end.
		float[] along = {0.0F, 0.33F, 1.0F};
		float[] bright = {1.0F, 0.5F, 0.0F};
		for (int p = 0; p < PLANES; p++) {
			float a = MathHelper.PI * p / PLANES;
			Vector3f across = new Vector3f(side).mul(MathHelper.cos(a)).add(new Vector3f(up).mul(MathHelper.sin(a)));
			for (int k = 0; k < along.length - 1; k++) {
				float t0 = along[k];
				float t1 = along[k + 1];
				Vector3f c0 = new Vector3f(origin).add(new Vector3f(ahead).mul(length * t0));
				Vector3f c1 = new Vector3f(origin).add(new Vector3f(ahead).mul(length * t1));
				float w0 = MathHelper.lerp(t0, r0, r1);
				float w1 = MathHelper.lerp(t1, r0, r1);
				int a0 = color(alpha * bright[k]);
				int a1 = color(alpha * bright[k + 1]);
				vertex(out, m, new Vector3f(across).mul(-w0).add(c0), a0, 0.0F, t0);
				vertex(out, m, new Vector3f(across).mul(w0).add(c0), a0, 1.0F, t0);
				vertex(out, m, new Vector3f(across).mul(w1).add(c1), a1, 1.0F, t1);
				vertex(out, m, new Vector3f(across).mul(-w1).add(c1), a1, 0.0F, t1);
			}
		}
	}

	/** Warm lamplight with the given opacity, as ARGB. */
	private static int color(float alpha) {
		int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
		return a << 24 | 255 << 16 | 236 << 8 | 196;
	}

	private static void vertex(VertexConsumer out, Matrix4f m, Vector3f p, int color, float u, float v) {
		Vector3f q = m.transformPosition(p, new Vector3f());
		out.vertex(q.x, q.y, q.z, color, u, v, OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, 0.0F, 1.0F,
				0.0F);
	}

	/** The beam's texture, made once: white, fading out smoothly to either edge across it. */
	private static void makeTexture() {
		if (textureMade) {
			return;
		}
		textureMade = true;
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, 32, 4, false);
		for (int x = 0; x < 32; x++) {
			float s = MathHelper.sin(MathHelper.PI * (x + 0.5F) / 32.0F);
			int alpha = (int) (s * s * 255.0F);
			for (int y = 0; y < 4; y++) {
				// ABGR.
				image.setColor(x, y, alpha << 24 | 0xFFFFFF);
			}
		}
		MinecraftClient.getInstance().getTextureManager().registerTexture(BEAM, new NativeImageBackedTexture(image));
	}
}
