package io.github.kortev.shootingstar.client.gfx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
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
	private static final Target HALF_A = new Target(false);
	private static final Target HALF_B = new Target(false);
	private static final Target QUARTER_A = new Target(false);
	private static final Target QUARTER_B = new Target(false);

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

	/** Bright pass and blur of {@code source}; returns the half and quarter resolution glow textures. */
	public static int[] bloom(int source, int width, int height, float threshold) {
		int hw = Math.max(1, width / 2);
		int hh = Math.max(1, height / 2);
		int qw = Math.max(1, width / 4);
		int qh = Math.max(1, height / 4);
		Framebuffer halfA = HALF_A.get(hw, hh);
		Framebuffer halfB = HALF_B.get(hw, hh);
		Framebuffer quarterA = QUARTER_A.get(qw, qh);
		Framebuffer quarterB = QUARTER_B.get(qw, qh);

		halfA.beginWrite(true);
		RenderSystem.setShaderTexture(0, source);
		Shaders.set(Shaders.bright, "Threshold", threshold);
		Shaders.set(Shaders.bright, "TexelSize", 1.0F / width, 1.0F / height);
		quad(Shaders.bright);
		blur(halfA, halfB, hw, hh);

		quarterA.beginWrite(true);
		RenderSystem.setShaderTexture(0, halfA.getColorAttachment());
		Shaders.set(Shaders.bright, "Threshold", 0.0F);
		Shaders.set(Shaders.bright, "TexelSize", 1.0F / hw, 1.0F / hh);
		quad(Shaders.bright);
		blur(quarterA, quarterB, qw, qh);
		blur(quarterA, quarterB, qw, qh);
		return new int[] {halfA.getColorAttachment(), quarterA.getColorAttachment()};
	}

	/** Separable blur of {@code a} using {@code b} as scratch; the result ends up back in {@code a}. */
	private static void blur(Framebuffer a, Framebuffer b, int width, int height) {
		b.beginWrite(true);
		RenderSystem.setShaderTexture(0, a.getColorAttachment());
		Shaders.set(Shaders.blur, "Direction", 1.0F / width, 0.0F);
		quad(Shaders.blur);
		a.beginWrite(true);
		RenderSystem.setShaderTexture(0, b.getColorAttachment());
		Shaders.set(Shaders.blur, "Direction", 0.0F, 1.0F / height);
		quad(Shaders.blur);
	}
}
