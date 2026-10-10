package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModItems;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * The Genesis Key in first person as the event starts, in time with its sound: it comes up into the middle of
 * the view and wakes, swaying to show its depth; a lock of light closes in out of nothing ahead of it; it is pushed
 * into it along its shaft and turns in two snaps, with the click and the clunk; the lock flares, and the camera leaves.
 */
public final class KeyTurn {
	private KeyTurn() {
	}

	/** Where along the key's shaft (from its middle, in the hand's units) the lock forms, which the point goes through. */
	private static final float LOCK = 0.3F;

	/** Draws the key itself; false leaves the hand to vanilla. */
	public static boolean render(HeldItemRenderer renderer, AbstractClientPlayerEntity player, float tickDelta, ItemStack item,
			MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		ClientGap gap = ClientGaps.mine();
		if (gap == null || !item.isOf(ModItems.GENESIS_KEY)) {
			return false;
		}
		double t = gap.time(tickDelta);
		if (t >= GapTimeline.RISE) {
			return false;
		}
		double up = ease(t / 16.0);
		// Pushed in along its own shaft, slow and then home.
		double thrust = Math.pow(ease((t - 22.0) / 7.0), 1.6);
		// Two snaps, in time with the sound: a quarter of the way round with the click, the rest with the clunk, each
		// overshooting a little and settling.
		double turn = 45.0 * snap(t - 30.0) + 45.0 * snap(t - 37.0);
		// The clunk knocks it back a hair, and it hums.
		double knock = Math.exp(-Math.max(0.0, t - 37.0) / 2.0) * (t >= 37.0 ? 0.02 : 0.0);
		double shake = MathHelper.clamp((t - 37.0) / 5.0, 0.0, 1.0) * 0.004;
		// Coming up, it sways enough to show it has depth: a key, not a picture of one.
		double sway = (1.0 - ease((t - 14.0) / 8.0)) * Math.sin(t * 0.35) * 28.0;
		float x = (float) (MathHelper.lerp(up, 0.42, 0.0) + Math.sin(t * 9.7) * shake);
		float y = (float) (MathHelper.lerp(up, -0.55, -0.1) + Math.sin(t * 7.3 + 1.0) * shake);
		float z = (float) (MathHelper.lerp(up, -0.7, -0.66) + knock);
		boolean lit = t > 10.0;
		matrices.push();
		matrices.translate(x, y, z);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) MathHelper.lerp(up, -20.0, -62.0)));
		// The lock, in the key's own frame before it is pushed in or turned: a plane across its shaft, out by its point.
		lock(t, (float) turn, matrices, consumers);
		matrices.translate(0.0F, (float) (0.13 * thrust), 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (turn + sway + 12.0 * (1.0 - up))));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0F));
		// No centring here: the item renderer centres the model itself.
		matrices.scale(0.5F, 0.5F, 0.5F);
		renderer.renderItem(player, item, ModelTransformationMode.NONE, false, matrices, consumers,
				lit ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light);
		matrices.pop();
		return true;
	}

	/** 0 to 1 over three ticks from {@code e} = 0, overshooting by a tenth on the way and settling back. */
	private static double snap(double e) {
		if (e <= 0.0) {
			return 0.0;
		}
		double k = Math.min(1.0, e / 3.0);
		return ease(k) + 0.12 * Math.sin(Math.PI * k) * Math.exp(-Math.max(0.0, e - 1.0) / 2.0);
	}

	/**
	 * The lock of light the key goes into: rings closing in out of nothing to the point ahead of the key, their runes
	 * lighting one by one round them; the inner ring turning with the key; flaring out as it turns home.
	 */
	private static void lock(double t, float turn, MatrixStack matrices, VertexConsumerProvider consumers) {
		double form = ease((t - 8.0) / 14.0);
		if (form <= 0.0) {
			return;
		}
		double flare = t < 37.0 ? 0.0 : ease((t - 37.0) / 5.0);
		float alpha = (float) (form * (1.0 - 0.7 * flare));
		VertexConsumer out = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f m = matrices.peek().getPositionMatrix();
		float close = (float) (1.0 + 2.5 * (1.0 - form));
		float grow = (float) (1.0 + 2.2 * flare);
		float click = (float) (Math.exp(-Math.max(0.0, t - 30.0) / 2.0) * (t >= 30.0 ? 1.0 : 0.0)
				+ Math.exp(-Math.max(0.0, t - 37.0) / 3.0) * (t >= 37.0 ? 1.5 : 0.0));
		// The outer ring, still; the middle one turning against the key as it forms; the inner one turning with it.
		ring(out, m, 0.125F * close * grow, 0.008F, (float) (t * -2.0), 1.0F, 64, 0.6F, 0.95F, 1.0F, alpha * (0.6F + 0.4F * click));
		ring(out, m, 0.095F * close * grow, 0.005F, (float) (-t * 6.0 * (1.0 - form)), 0.62F, 48, 0.75F, 0.9F, 1.0F, alpha * 0.8F);
		ring(out, m, 0.06F * close, 0.006F, turn, 0.25F, 32, 1.0F, 1.0F, 1.0F, alpha * (0.7F + 0.6F * click));
		// The runes round the outer ring, lighting one after another as it forms, all at once at the click.
		int runes = 16;
		for (int i = 0; i < runes; i++) {
			double on = MathHelper.clamp((t - 12.0 - i * 0.6) / 2.0, 0.0, 1.0);
			float a = (float) (alpha * Math.max(on, click * 0.8));
			if (a <= 0.01F) {
				continue;
			}
			double angle = i / (double) runes * Math.PI * 2.0;
			float r0 = 0.135F * close * grow;
			float r1 = r0 + 0.02F;
			float w = 0.006F;
			float cx = (float) Math.cos(angle);
			float cz = (float) Math.sin(angle);
			quad(out, m, cx * r0 - cz * w, cz * r0 + cx * w, cx * r1 - cz * w, cz * r1 + cx * w, cx * r1 + cz * w, cz * r1 - cx * w,
					cx * r0 + cz * w, cz * r0 - cx * w, 0.8F, 1.0F, 1.0F, a);
		}
		// The keyhole's own light, bright as the key turns home.
		float core = 0.035F * (1.0F + click);
		quad(out, m, -core, -core, core, -core, core, core, -core, core, 0.85F, 1.0F, 1.0F, alpha * 0.35F * (1.0F + click));
	}

	/** A ring across the shaft at the lock, made of {@code segments} quads; {@code arc} of a full turn of it drawn. */
	private static void ring(VertexConsumer out, Matrix4f m, float radius, float width, float spin, float arc, int segments, float r,
			float g, float b, float a) {
		if (a <= 0.01F) {
			return;
		}
		int n = Math.max(3, (int) (segments * arc));
		double from = Math.toRadians(spin);
		double step = Math.PI * 2.0 * arc / n;
		for (int i = 0; i < n; i++) {
			double a0 = from + i * step;
			double a1 = a0 + step;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);
			float ri = radius - width;
			float ro = radius + width;
			quad(out, m, c0 * ri, s0 * ri, c0 * ro, s0 * ro, c1 * ro, s1 * ro, c1 * ri, s1 * ri, r, g, b, a);
		}
	}

	/** One quad in the lock's plane (across the shaft, at LOCK along it), corners given as (x, z) pairs. */
	private static void quad(VertexConsumer out, Matrix4f m, float x0, float z0, float x1, float z1, float x2, float z2, float x3, float z3,
			float r, float g, float b, float a) {
		out.vertex(m, x0, LOCK, z0).color(r, g, b, a);
		out.vertex(m, x1, LOCK, z1).color(r, g, b, a);
		out.vertex(m, x2, LOCK, z2).color(r, g, b, a);
		out.vertex(m, x3, LOCK, z3).color(r, g, b, a);
	}

	private static double ease(double x) {
		return GapCamera.ease(x);
	}
}
