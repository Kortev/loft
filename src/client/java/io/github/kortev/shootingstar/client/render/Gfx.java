package io.github.kortev.shootingstar.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Immediate-mode helpers for flat geometry in screen (GUI) space. Only one buffer from the shared
 * tessellator may be open at a time: begin, fill, draw, then begin the next.
 */
public final class Gfx {
	private Gfx() {
	}

	// --- state -----------------------------------------------------------------------------

	public static void begin2d() {
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		alpha();
	}

	public static void end2d() {
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
	}

	public static void alpha() {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
	}

	public static void additive() {
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
	}

	// --- buffers ---------------------------------------------------------------------------

	public static BufferBuilder quads() {
		return Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
	}

	public static BufferBuilder triangles() {
		return Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
	}

	public static BufferBuilder texQuads() {
		return Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
	}

	public static void draw(BufferBuilder buffer) {
		BuiltBuffer built = buffer.endNullable();
		if (built != null) {
			RenderSystem.setShader(GameRenderer::getPositionColorProgram);
			BufferRenderer.drawWithGlobalProgram(built);
		}
	}

	public static void drawTex(BufferBuilder buffer, Identifier texture) {
		BuiltBuffer built = buffer.endNullable();
		if (built != null) {
			RenderSystem.setShaderTexture(0, texture);
			RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
			BufferRenderer.drawWithGlobalProgram(built);
		}
	}

	// --- colour ----------------------------------------------------------------------------

	public static int argb(int a, int r, int g, int b) {
		return (MathHelper.clamp(a, 0, 255) << 24) | (MathHelper.clamp(r, 0, 255) << 16)
				| (MathHelper.clamp(g, 0, 255) << 8) | MathHelper.clamp(b, 0, 255);
	}

	public static int argb(float a, float r, float g, float b) {
		return argb((int) (a * 255.0F), (int) (r * 255.0F), (int) (g * 255.0F), (int) (b * 255.0F));
	}

	/** Same colour with alpha scaled by {@code k}. */
	public static int fade(int argb, float k) {
		int a = (int) (((argb >>> 24) & 0xFF) * MathHelper.clamp(k, 0.0F, 1.0F));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int lerpColor(float t, int from, int to) {
		t = MathHelper.clamp(t, 0.0F, 1.0F);
		int a = (int) MathHelper.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
		int r = (int) MathHelper.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
		int g = (int) MathHelper.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
		int b = (int) MathHelper.lerp(t, from & 0xFF, to & 0xFF);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	// --- primitives ------------------------------------------------------------------------

	public static void rect(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
		b.vertex(m, x0, y0, 0).color(argb);
		b.vertex(m, x0, y1, 0).color(argb);
		b.vertex(m, x1, y1, 0).color(argb);
		b.vertex(m, x1, y0, 0).color(argb);
	}

	public static void rectV(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, int top, int bottom) {
		b.vertex(m, x0, y0, 0).color(top);
		b.vertex(m, x0, y1, 0).color(bottom);
		b.vertex(m, x1, y1, 0).color(bottom);
		b.vertex(m, x1, y0, 0).color(top);
	}

	/** A quad from four arbitrary corners. */
	public static void quad(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2,
			float x3, float y3, int c0, int c1, int c2, int c3) {
		b.vertex(m, x0, y0, 0).color(c0);
		b.vertex(m, x1, y1, 0).color(c1);
		b.vertex(m, x2, y2, 0).color(c2);
		b.vertex(m, x3, y3, 0).color(c3);
	}

	/** A thick line as a quad, with a colour at each end. */
	public static void line(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float width, int c0, int c1) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float len = MathHelper.sqrt(dx * dx + dy * dy);
		if (len < 1.0E-4F) {
			return;
		}
		float nx = -dy / len * width * 0.5F;
		float ny = dx / len * width * 0.5F;
		b.vertex(m, x0 + nx, y0 + ny, 0).color(c0);
		b.vertex(m, x0 - nx, y0 - ny, 0).color(c0);
		b.vertex(m, x1 - nx, y1 - ny, 0).color(c1);
		b.vertex(m, x1 + nx, y1 + ny, 0).color(c1);
	}

	public static void line(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float width, int argb) {
		line(b, m, x0, y0, x1, y1, width, argb, argb);
	}

	/** An annulus with separate inner and outer colours; rIn = 0 gives a disc. */
	public static void ring(BufferBuilder b, Matrix4f m, float cx, float cy, float rIn, float rOut, int inner, int outer,
			int segments) {
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2.0 * i / segments;
			double a1 = Math.PI * 2.0 * (i + 1) / segments;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);
			b.vertex(m, cx + c0 * rIn, cy + s0 * rIn, 0).color(inner);
			b.vertex(m, cx + c0 * rOut, cy + s0 * rOut, 0).color(outer);
			b.vertex(m, cx + c1 * rOut, cy + s1 * rOut, 0).color(outer);
			b.vertex(m, cx + c1 * rIn, cy + s1 * rIn, 0).color(inner);
		}
	}

	/** Outline of a circle. */
	public static void circle(BufferBuilder b, Matrix4f m, float cx, float cy, float r, float width, int argb, int segments) {
		ring(b, m, cx, cy, r - width * 0.5F, r + width * 0.5F, argb, argb, segments);
	}

	/** Four corner ticks around a point, the marker style of the uplink feed. */
	public static void brackets(BufferBuilder b, Matrix4f m, float cx, float cy, float half, float arm, float width, int argb) {
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				float x = cx + sx * half;
				float y = cy + sy * half;
				rect(b, m, Math.min(x, x - sx * arm), y - width * 0.5F, Math.max(x, x - sx * arm), y + width * 0.5F, argb);
				rect(b, m, x - width * 0.5F, Math.min(y, y - sy * arm), x + width * 0.5F, Math.max(y, y - sy * arm), argb);
			}
		}
	}

	/** Diamond marker. */
	public static void diamond(BufferBuilder b, Matrix4f m, float cx, float cy, float r, int argb) {
		quad(b, m, cx, cy - r, cx - r, cy, cx, cy + r, cx + r, cy, argb, argb, argb, argb);
	}

	// --- textured --------------------------------------------------------------------------

	public static void texRect(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float u0, float v0,
			float u1, float v1, int argb) {
		b.vertex(m, x0, y0, 0).texture(u0, v0).color(argb);
		b.vertex(m, x0, y1, 0).texture(u0, v1).color(argb);
		b.vertex(m, x1, y1, 0).texture(u1, v1).color(argb);
		b.vertex(m, x1, y0, 0).texture(u1, v0).color(argb);
	}

	/** A whole texture centred on a point, rotated by {@code angle} radians. */
	public static void sprite(BufferBuilder b, Matrix4f m, float cx, float cy, float halfW, float halfH, float angle, int argb) {
		float c = MathHelper.cos(angle);
		float s = MathHelper.sin(angle);
		float ax = c * halfW;
		float ay = s * halfW;
		float bx = -s * halfH;
		float by = c * halfH;
		b.vertex(m, cx - ax - bx, cy - ay - by, 0).texture(0, 0).color(argb);
		b.vertex(m, cx - ax + bx, cy - ay + by, 0).texture(0, 1).color(argb);
		b.vertex(m, cx + ax + bx, cy + ay + by, 0).texture(1, 1).color(argb);
		b.vertex(m, cx + ax - bx, cy + ay - by, 0).texture(1, 0).color(argb);
	}
}
