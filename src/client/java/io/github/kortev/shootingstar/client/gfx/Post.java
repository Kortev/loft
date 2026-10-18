package io.github.kortev.shootingstar.client.gfx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/** Full-screen passes: bloom at half and quarter resolution and the helpers the passes share. */
public final class Post {
	private static final Matrix4f IDENTITY = new Matrix4f();
	private static final Target HALF_A = new Target(false, true);
	private static final Target HALF_B = new Target(false, true);
	private static final Target QUARTER_A = new Target(false, true);
	private static final Target QUARTER_B = new Target(false, true);
	private static final Target STREAK_A = new Target(false, true);
	private static final Target STREAK_B = new Target(false, true);

	private Post() {
	}

	/** State for screen passes: no depth, no blending, no culling. */
	public static void begin() {
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableBlend();
		RenderSystem.disableCull();
	}

	/** Draws a quad covering the bound target with {@code program} (positions are in clip space). */
	public static void quad(ShaderProgram program) {
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.BLIT_SCREEN);
		b.vertex(-1.0F, -1.0F, 0.0F);
		b.vertex(1.0F, -1.0F, 0.0F);
		b.vertex(1.0F, 1.0F, 0.0F);
		b.vertex(-1.0F, 1.0F, 0.0F);
		draw(b, program, IDENTITY, IDENTITY);
	}

	/** Uploads and draws an immediate buffer with explicit matrices, bypassing the global ones. */
	public static void draw(BufferBuilder builder, ShaderProgram program, Matrix4f modelView, Matrix4f projection) {
		BuiltBuffer built = builder.endNullable();
		if (built == null) {
			return;
		}
		VertexBuffer vb = built.getDrawParameters().format().getBuffer();
		vb.bind();
		vb.upload(built);
		vb.draw(modelView, projection, program);
		VertexBuffer.unbind();
	}

	/**
	 * Bright pass (light above {@code threshold}) and blur of the HDR texture {@code source}; returns
	 * the half and quarter resolution glow textures and a horizontal streak of the very brightest light.
	 */
	public static int[] bloom(int source, int width, int height, float threshold) {
		return bloom(source, width, height, threshold, true);
	}

	/**
	 * As {@link #bloom(int, int, int, float)}; without {@code streak} the streak (more than half the passes) is left out
	 * and its texture comes back as 0, for pictures that do not want it.
	 */
	public static int[] bloom(int source, int width, int height, float threshold, boolean streak) {
		int hw = Math.max(1, width / 2);
		int hh = Math.max(1, height / 2);
		int qw = Math.max(1, width / 4);
		int qh = Math.max(1, height / 4);
		Target halfA = HALF_A.ensure(hw, hh);
		Target halfB = HALF_B.ensure(hw, hh);
		Target quarterA = QUARTER_A.ensure(qw, qh);
		Target quarterB = QUARTER_B.ensure(qw, qh);

		halfA.bind();
		RenderSystem.setShaderTexture(0, source);
		Shaders.set(Shaders.bright, "Threshold", threshold);
		Shaders.set(Shaders.bright, "TexelSize", 1.0F / width, 1.0F / height);
		quad(Shaders.bright);

		// The streak: only light far above the threshold, squeezed into a short, wide buffer and smeared
		// sideways with ever wider steps.
		int streaked = 0;
		if (streak) {
			int sw = Math.max(1, width / 2);
			int sh = Math.max(1, height / 8);
			Target streakA = STREAK_A.ensure(sw, sh);
			Target streakB = STREAK_B.ensure(sw, sh);
			streakA.bind();
			RenderSystem.setShaderTexture(0, halfA.color());
			Shaders.set(Shaders.bright, "Threshold", 1.5F);
			Shaders.set(Shaders.bright, "TexelSize", 1.0F / hw, 4.0F / hh);
			quad(Shaders.bright);
			for (float step : new float[] {1.0F, 2.5F, 6.0F, 14.0F}) {
				streakB.bind();
				RenderSystem.setShaderTexture(0, streakA.color());
				Shaders.set(Shaders.blur, "Direction", step / sw, 0.0F);
				quad(Shaders.blur);
				streakA.bind();
				RenderSystem.setShaderTexture(0, streakB.color());
				Shaders.set(Shaders.blur, "Direction", step * 1.6F / sw, 0.0F);
				quad(Shaders.blur);
			}
			streaked = streakA.color();
		}

		blur(halfA, halfB, hw, hh);

		quarterA.bind();
		RenderSystem.setShaderTexture(0, halfA.color());
		Shaders.set(Shaders.bright, "Threshold", 0.0F);
		Shaders.set(Shaders.bright, "TexelSize", 1.0F / hw, 1.0F / hh);
		quad(Shaders.bright);
		blur(quarterA, quarterB, qw, qh);
		blur(quarterA, quarterB, qw, qh);
		return new int[] {halfA.color(), quarterA.color(), streaked};
	}

	/** Separable blur of {@code a} using {@code b} as scratch; the result ends up back in {@code a}. */
	private static void blur(Target a, Target b, int width, int height) {
		b.bind();
		RenderSystem.setShaderTexture(0, a.color());
		Shaders.set(Shaders.blur, "Direction", 1.0F / width, 0.0F);
		quad(Shaders.blur);
		a.bind();
		RenderSystem.setShaderTexture(0, b.color());
		Shaders.set(Shaders.blur, "Direction", 0.0F, 1.0F / height);
		quad(Shaders.blur);
	}
}
