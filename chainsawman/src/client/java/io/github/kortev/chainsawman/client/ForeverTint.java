package io.github.kortev.chainsawman.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;

/**
 * A provider that hands out the buffers of the layers it is given, with every colour multiplied by a tint; the target's own skin
 * layer is swapped for a see-through one (or a glowing one) so the transparency shows. How the copies of a locked target, and the
 * drained wash over the target itself, are drawn with the target's own renderer.
 */
final class ForeverTint implements VertexConsumerProvider {
	private final VertexConsumerProvider inner;
	private final float[] tint;
	private final Identifier texture;
	private final boolean emissive;

	ForeverTint(VertexConsumerProvider inner, float[] tint, Identifier texture, boolean emissive) {
		this.inner = inner;
		this.tint = tint;
		this.texture = texture;
		this.emissive = emissive;
	}

	@Override
	public VertexConsumer getBuffer(RenderLayer layer) {
		RenderLayer use = layer;
		boolean skin = layer == RenderLayer.getEntityCutoutNoCull(texture) || layer == RenderLayer.getEntityTranslucent(texture)
				|| layer == RenderLayer.getEntityCutout(texture) || layer == RenderLayer.getEntitySolid(texture);
		if (skin) {
			use = emissive ? RenderLayer.getEntityTranslucentEmissive(texture) : RenderLayer.getEntityTranslucent(texture);
		}
		return new Consumer(inner.getBuffer(use), tint);
	}

	private static final class Consumer implements VertexConsumer {
		private final VertexConsumer inner;
		private final float[] tint;

		Consumer(VertexConsumer inner, float[] tint) {
			this.inner = inner;
			this.tint = tint;
		}

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			inner.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int r, int g, int b, int a) {
			inner.color(Math.min(255, Math.round(r * tint[0])), Math.min(255, Math.round(g * tint[1])), Math.min(255, Math.round(b * tint[2])),
					Math.round(a * tint[3]));
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			inner.texture(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			inner.overlay(u, v);
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			inner.light(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			inner.normal(x, y, z);
			return this;
		}
	}
}
