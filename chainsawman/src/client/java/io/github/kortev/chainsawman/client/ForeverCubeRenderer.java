package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.Forever;
import io.github.kortev.chainsawman.contract.ForeverCubeEntity;
import io.github.kortev.chainsawman.contract.ForeverHubEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The edge of a cube of frozen time, seen from outside: its six faces drawn as a faint pale sheen that shimmers and slides (a grid of
 * quads whose see-through-ness moves), with the twelve edges a little brighter. Inside it the screen pass drains the world to grey, so
 * the sheen sits where colour meets grey.
 */
public class ForeverCubeRenderer extends EntityRenderer<ForeverCubeEntity> {
	private static final float CELL = (4 * 4 + 2) / 32.0F;

	public ForeverCubeRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ForeverCubeEntity entity) {
		return ForeverHubRenderer.MOUTH;
	}

	@Override
	public boolean shouldRender(ForeverCubeEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(ForeverCubeEntity cube, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		float now = cube.getWorld().getTime() + delta;
		float fade;
		if (cube.phase() == ForeverHubEntity.SINKING) {
			fade = 1.0F - MathHelper.clamp((now - cube.phaseAt()) / 8.0F, 0.0F, 1.0F);
		} else {
			fade = MathHelper.clamp((now - cube.startedAt()) / 5.0F, 0.0F, 1.0F);
		}
		if (fade <= 0.01F) {
			return;
		}
		VertexConsumer vc = provider.getBuffer(RenderLayer.getEntityTranslucentEmissive(ForeverHubRenderer.MOUTH));
		double h = cube.half();
		net.minecraft.util.math.Vec3d cam = dispatcher.camera.getPos();
		net.minecraft.util.math.Vec3d at = cube.getLerpedPos(delta);
		double[] cc = {cam.x - at.x, cam.y - at.y, cam.z - at.z};
		int n = 9;
		for (int face = 0; face < 6; face++) {
			int axis = face / 2;
			double sign = face % 2 == 0 ? 1.0 : -1.0;
			float nearFace = MathHelper.clamp((float) (Math.abs(cc[axis] - sign * h) - 0.5) / 2.5F, 0.0F, 1.0F);
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					double u0 = -h + 2.0 * h * i / n;
					double u1 = -h + 2.0 * h * (i + 1) / n;
					double v0 = -h + 2.0 * h * j / n;
					double v1 = -h + 2.0 * h * (j + 1) / n;
					float wave = 0.5F + 0.5F * MathHelper.sin(now * 0.22F + (i * 0.8F + j * 1.1F) + face * 1.7F);
					float slide = 0.5F + 0.5F * MathHelper.sin(now * 0.07F - (i + j) * 0.5F);
					float a = fade * nearFace * (0.025F + 0.07F * wave * slide);
					quad(vc, matrices, axis, sign * h, u0, v0, u1, v1, a);
				}
			}
		}
		// The twelve edges: only a trace, and gone when the camera is within about 3 blocks of one, so none ever sits across the lens.
		double t = 0.035;
		for (int axis = 0; axis < 3; axis++) {
			for (int sa = -1; sa <= 1; sa += 2) {
				for (int sb = -1; sb <= 1; sb += 2) {
					double[] lo = new double[3];
					lo[(axis + 1) % 3] = sa * h;
					lo[(axis + 2) % 3] = sb * h;
					double near = Double.MAX_VALUE;
					{
						double cx = cam.x - at.x;
						double cy = cam.y - at.y;
						double cz = cam.z - at.z;
						double[] c = {cx, cy, cz};
						double sum = 0.0;
						for (int k = 0; k < 3; k++) {
							double d = k == axis ? Math.max(0.0, Math.abs(c[k]) - h) : c[k] - lo[k];
							sum += d * d;
						}
						near = Math.sqrt(sum);
					}
					float close = MathHelper.clamp((float) (near - 1.0) / 2.0F, 0.0F, 1.0F);
					float a = fade * close * (0.035F + 0.02F * MathHelper.sin(now * 0.3F + axis * 2.0F + sa + sb * 2.0F));
					if (a > 0.002F) {
						edge(vc, matrices, axis, sa * h, sb * h, h, t, a);
					}
				}
			}
		}
	}

	private static void put(VertexConsumer vc, MatrixStack.Entry e, double[] p, float a) {
		vc.vertex(e, (float) p[0], (float) p[1], (float) p[2]).color(0.82F, 0.86F, 0.9F, a).texture(CELL, 0.5F)
				.overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(e, 0.0F, 1.0F, 0.0F);
	}

	/** A quad on the face at {@code d} along {@code axis}, from (u0, v0) to (u1, v1) over the other two axes. */
	private static void quad(VertexConsumer vc, MatrixStack matrices, int axis, double d, double u0, double v0, double u1, double v1, float a) {
		MatrixStack.Entry e = matrices.peek();
		double[][] c = new double[4][3];
		double[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
		for (int k = 0; k < 4; k++) {
			c[k][axis] = d;
			c[k][(axis + 1) % 3] = uv[k][0];
			c[k][(axis + 2) % 3] = uv[k][1];
			put(vc, e, c[k], a);
		}
	}

	/** An edge: along {@code axis}, at {@code a} and {@code b} on the other two, as a thin square tube. */
	private static void edge(VertexConsumer vc, MatrixStack matrices, int axis, double a, double b, double h, double t, float alpha) {
		MatrixStack.Entry e = matrices.peek();
		double[] lo = new double[3];
		double[] hi = new double[3];
		for (int k = 0; k < 3; k++) {
			if (k == axis) {
				lo[k] = -h;
				hi[k] = h;
			} else if (k == (axis + 1) % 3) {
				lo[k] = a - t;
				hi[k] = a + t;
			} else {
				lo[k] = b - t;
				hi[k] = b + t;
			}
		}
		for (int face = 0; face < 6; face++) {
			int ax = face / 2;
			double d = face % 2 == 0 ? hi[ax] : lo[ax];
			int u = (ax + 1) % 3;
			int v = (ax + 2) % 3;
			double[][] uv = {{lo[u], lo[v]}, {hi[u], lo[v]}, {hi[u], hi[v]}, {lo[u], hi[v]}};
			for (int k = 0; k < 4; k++) {
				double[] p = new double[3];
				p[ax] = d;
				p[u] = uv[k][0];
				p[v] = uv[k][1];
				put(vc, e, p, alpha);
			}
		}
	}
}
