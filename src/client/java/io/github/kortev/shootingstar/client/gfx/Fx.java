package io.github.kortev.shootingstar.client.gfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Batched additive light shapes for {@code ss_glow}: billboards, beams and flat rings. */
public final class Fx {
	public static final int BLOB = 0;
	public static final int BEAM = 1;
	public static final int RING = 2;
	public static final int STREAK = 3;
	public static final int PROGRESS = 4;
	public static final int SPIKES = 5;
	/** A hard-edged tapered streak with a white core, drawn rather than glowing (the impact's sparks). */
	public static final int DRAWN_STREAK = 6;

	private final Vector3f right = new Vector3f();
	private final Vector3f up = new Vector3f();
	private BufferBuilder builder;
	private int mode = -1;
	private float param;
	private Matrix4f modelView;
	private Matrix4f projection;

	/** Starts a batch drawn with one mode; {@code right}/{@code up} are the camera's axes in the same space. */
	public Fx begin(int mode, float param, Matrix4f modelView, Matrix4f projection, Vector3f camRight, Vector3f camUp) {
		this.mode = mode;
		this.param = param;
		this.modelView = modelView;
		this.projection = projection;
		this.right.set(camRight);
		this.up.set(camUp);
		this.builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		return this;
	}

	/** Camera-facing square of half-size {@code size}, rotated by {@code angle}. */
	public Fx sprite(Vector3f c, float size, float angle, int argb) {
		float cs = (float) Math.cos(angle) * size;
		float sn = (float) Math.sin(angle) * size;
		float rx = right.x * cs + up.x * sn;
		float ry = right.y * cs + up.y * sn;
		float rz = right.z * cs + up.z * sn;
		float ux = up.x * cs - right.x * sn;
		float uy = up.y * cs - right.y * sn;
		float uz = up.z * cs - right.z * sn;
		vertex(c.x - rx - ux, c.y - ry - uy, c.z - rz - uz, -1, -1, argb);
		vertex(c.x + rx - ux, c.y + ry - uy, c.z + rz - uz, 1, -1, argb);
		vertex(c.x + rx + ux, c.y + ry + uy, c.z + rz + uz, 1, 1, argb);
		vertex(c.x - rx + ux, c.y - ry + uy, c.z - rz + uz, -1, 1, argb);
		return this;
	}

	/** Stretched sprite: {@code size} across, {@code length} along {@code axis} (for streaks). */
	public Fx stretched(Vector3f c, Vector3f axis, float length, float size, int argb) {
		Vector3f a = new Vector3f(axis).normalize().mul(length);
		Vector3f toCam = new Vector3f(right).cross(up);
		Vector3f side = new Vector3f(axis).cross(toCam);
		if (side.lengthSquared() < 1.0E-8F) {
			side.set(right);
		}
		side.normalize().mul(size);
		vertex(c.x - a.x - side.x, c.y - a.y - side.y, c.z - a.z - side.z, -1, -1, argb);
		vertex(c.x + a.x - side.x, c.y + a.y - side.y, c.z + a.z - side.z, 1, -1, argb);
		vertex(c.x + a.x + side.x, c.y + a.y + side.y, c.z + a.z + side.z, 1, 1, argb);
		vertex(c.x - a.x + side.x, c.y - a.y + side.y, c.z - a.z + side.z, -1, 1, argb);
		return this;
	}

	/** Beam from {@code a} to {@code b} facing the viewer at {@code eye}; uv.x across, uv.y 0..1 along. */
	public Fx beam(Vector3f a, Vector3f b, Vector3f eye, float width, int argbA, int argbB) {
		Vector3f dir = new Vector3f(b).sub(a);
		Vector3f mid = new Vector3f(a).add(b).mul(0.5F);
		Vector3f toEye = new Vector3f(eye).sub(mid);
		Vector3f side = dir.cross(toEye, new Vector3f());
		if (side.lengthSquared() < 1.0E-10F) {
			side.set(right);
		}
		side.normalize().mul(width);
		vertex(a.x - side.x, a.y - side.y, a.z - side.z, -1, 0, argbA);
		vertex(a.x + side.x, a.y + side.y, a.z + side.z, 1, 0, argbA);
		vertex(b.x + side.x, b.y + side.y, b.z + side.z, 1, 1, argbB);
		vertex(b.x - side.x, b.y - side.y, b.z - side.z, -1, 1, argbB);
		return this;
	}

	/** Flat square in the plane spanned by {@code u} and {@code v} (half-sizes), for rings and decals. */
	public Fx flat(Vector3f c, Vector3f u, Vector3f v, int argb) {
		vertex(c.x - u.x - v.x, c.y - u.y - v.y, c.z - u.z - v.z, -1, -1, argb);
		vertex(c.x + u.x - v.x, c.y + u.y - v.y, c.z + u.z - v.z, 1, -1, argb);
		vertex(c.x + u.x + v.x, c.y + u.y + v.y, c.z + u.z + v.z, 1, 1, argb);
		vertex(c.x - u.x + v.x, c.y - u.y + v.y, c.z - u.z + v.z, -1, 1, argb);
		return this;
	}

	private void vertex(float x, float y, float z, float u, float v, int argb) {
		builder.vertex(x, y, z).texture(u, v).color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
	}

	/** Draws the batch additively; depth-tested against what is already drawn but never writing depth. */
	public void end(boolean depthTest) {
		end(depthTest, 1.0F);
	}

	/** As {@link #end(boolean)}, with every colour scaled by {@code intensity} (HDR targets go past white). */
	public void end(boolean depthTest, float intensity) {
		if (depthTest) {
			RenderSystem.enableDepthTest();
		} else {
			RenderSystem.disableDepthTest();
		}
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ZERO,
				GlStateManager.DstFactor.ONE);
		Shaders.setInt(Shaders.glow, "Mode", mode);
		Shaders.set(Shaders.glow, "Param", param);
		Shaders.set(Shaders.glow, "Tint", intensity, intensity, intensity);
		Post.draw(builder, Shaders.glow, modelView, projection);
		Shaders.set(Shaders.glow, "Tint", 1.0F, 1.0F, 1.0F);
		builder = null;
	}

	public static int argb(float r, float g, float b, float a) {
		return ((int) (clamp(a) * 255) << 24) | ((int) (clamp(r) * 255) << 16) | ((int) (clamp(g) * 255) << 8) | (int) (clamp(b) * 255);
	}

	public static int fade(int rgb, float a) {
		return ((int) (clamp(a) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static float clamp(float v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}
}
