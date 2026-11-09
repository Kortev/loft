package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.EternityDevilEntity;
import io.github.kortev.chainsawman.hotel.Shaft;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

/**
 * The Eternity Devil's mouth at the bottom of the shaft, a placeholder: the floor itself is blocks (the mass, see {@link Shaft}), and this
 * draws the lips round the pit (a ring of heaving lumps) and uneven teeth standing in them, which part when it is hurt and gape wide when
 * someone is swallowed. Drawn by {@link EternityDevilRenderer} for the devil in the shaft, in its own frame; its own class, to be replaced
 * by the devil's final look.
 */
final class ShaftMassRenderer {
	private ShaftMassRenderer() {
	}

	/** The pit's middle in the devil's frame: across 0, up from the entity's feet (the pit's bottom), and out from its back plane. */
	private static final double PIT_UP = Shaft.PIT_DEPTH;
	private static final double PIT_OUT = Shaft.MASS_X - (Shaft.PIT_X + 0.5);

	static void render(EternityDevilEntity devil, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(devil.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float now = world.getTime() + delta;
		int lit = ForeverGeo.lit(light, 12, 9);
		// How hurt it is: the lips draw back, the teeth show.
		float pain = devil.pain();
		float heave = 0.5F + 0.5F * MathHelper.sin(now * 0.12F);
		int seed = devil.getId() * 13;
		matrices.push();
		matrices.translate(0.0, PIT_UP, PIT_OUT);
		ForeverGeo.Pen lips = new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.FLESH)), matrices.peek(), lit);
		int n = 10;
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2.0 / n;
			double r = 1.95 + 0.12 * heave + 0.2 * pain;
			double bump = 0.2 * Math.sin(now * 0.1 + i * 1.9);
			ForeverGeo.blob(lips, Math.cos(a) * r, -0.1, Math.sin(a) * r, 0.62, 0.42 + bump, 0.62, 6, 10, 1.2, seed + i * 11, 0.2, 2.0F, 1.0F);
		}
		// Teeth, uneven, in the lips.
		ForeverGeo.Pen teeth = new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.FLESH)), matrices.peek(), lit).tint(0.97F, 0.92F, 0.8F, 1.0F);
		int t = 14;
		for (int i = 0; i < t; i++) {
			double a = (i + 0.5) * Math.PI * 2.0 / t;
			double r = 1.65 - 0.08 * ((i * 7) % 3) + 0.15 * pain;
			double h = 0.35 + 0.25 * (((i * 5) % 4) / 3.0);
			ForeverGeo.blob(teeth, Math.cos(a) * r, 0.1, Math.sin(a) * r, 0.1, h, 0.1, 5, 6, 1.2, seed + i, 0.05, 1.0F, 1.0F);
		}
		matrices.pop();
	}
}
