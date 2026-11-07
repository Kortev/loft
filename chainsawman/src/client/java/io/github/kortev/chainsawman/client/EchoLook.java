package io.github.kortev.chainsawman.client;

import java.util.List;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * How a copy (or the disguised owner, to everyone else) is drawn: the same model as the player, over and over.
 * <ul>
 * <li>A <b>past</b> copy is faded and sepia, and drags a smear of afterimages (where it has been) behind it.</li>
 * <li>A <b>future</b> copy is bright (full light), wrapped in a pale glowing haze, with a faint see-through ghost of itself a
 * little ahead, along where it is heading.</li>
 * </ul>
 * Each pass is the caller's own draw ({@link Draw}), seen through a tint ({@link Tint}) that changes the colour and the
 * transparency of whatever it draws.
 */
public final class EchoLook {
	/** Past: the body's tint and see-through-ness, then the afterimages (how many, how far apart in ticks, how strong at first). */
	static final float[] PAST_BODY = {0.88F, 0.78F, 0.64F, 0.66F};
	static final float[] PAST_SMEAR = {0.74F, 0.66F, 0.55F};
	static final int SMEAR = 6;
	static final int SMEAR_EVERY = 2;
	static final float SMEAR_ALPHA = 0.34F;
	/** Future: the haze round it (tint, see-through-ness, how much bigger), the ghost ahead (how many ticks ahead, how strong). */
	static final float[] FUTURE_HAZE = {0.70F, 0.93F, 1.0F, 0.30F};
	static final float HAZE_SCALE = 1.09F;
	static final float[] FUTURE_GHOST = {0.78F, 0.95F, 1.0F, 0.24F};
	static final float GHOST_TICKS = 9.0F;

	private EchoLook() {
	}

	/** A draw of the model into {@code matrices} (already placed), lit {@code light}, into {@code provider}. */
	public interface Draw {
		void draw(MatrixStack matrices, VertexConsumerProvider provider, int light);
	}

	/**
	 * Draws one look. {@code bodyOffset} is where the body is drawn relative to where the game put the matrices (a streak in or
	 * out); {@code behind} are earlier places, as offsets from the same origin, newest first; {@code heading} is the way (blocks
	 * a tick) it is going.
	 */
	public static void draw(boolean future, Identifier texture, boolean flicker, boolean streak, Vec3d bodyOffset, List<Vec3d> behind, Vec3d heading,
			MatrixStack matrices, VertexConsumerProvider provider, int light, Draw draw) {
		if (future) {
			int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;
			if (streak) {
				// Streaking: pale afterimages along the path.
				for (int i = 0; i < behind.size(); i++) {
					float[] tint = {FUTURE_GHOST[0], FUTURE_GHOST[1], FUTURE_GHOST[2], 0.42F * (1.0F - i / (float) behind.size())};
					pass(matrices, provider, full, draw, behind.get(i), 1.0F, tint, texture, true);
				}
			}
			// The ghost ahead.
			Vec3d ahead = heading.multiply(GHOST_TICKS);
			if (ahead.lengthSquared() > 0.04) {
				pass(matrices, provider, full, draw, bodyOffset.add(ahead), 1.0F, FUTURE_GHOST, texture, true);
			}
			// The body, bright (see-through only while it flickers from a hit), then the haze round it.
			float[] body = {1.0F, 1.0F, 1.0F, flicker ? 0.4F : 1.0F};
			pass(matrices, provider, full, draw, bodyOffset, 1.0F, body, texture, false);
			pass(matrices, provider, full, draw, bodyOffset, HAZE_SCALE, FUTURE_HAZE, texture, true);
		} else {
			// The smear: afterimages at places it has been, fading.
			for (int i = 0; i < SMEAR; i++) {
				int back = streak ? i : (i + 1) * SMEAR_EVERY - 1;
				if (back >= behind.size()) {
					break;
				}
				float[] tint = {PAST_SMEAR[0], PAST_SMEAR[1], PAST_SMEAR[2], SMEAR_ALPHA * (1.0F - i / (float) SMEAR)};
				pass(matrices, provider, light, draw, behind.get(back), 1.0F, tint, texture, false);
			}
			float[] body = {PAST_BODY[0], PAST_BODY[1], PAST_BODY[2], flicker ? 0.28F : PAST_BODY[3]};
			pass(matrices, provider, light, draw, bodyOffset, 1.0F, body, texture, false);
		}
	}

	/** One pass of the draw, moved by {@code offset}, scaled by {@code scale} about its feet, tinted. */
	private static void pass(MatrixStack matrices, VertexConsumerProvider provider, int light, Draw draw, Vec3d offset, float scale,
			float[] tint, Identifier texture, boolean emissive) {
		matrices.push();
		matrices.translate(offset.x, offset.y, offset.z);
		if (scale != 1.0F) {
			matrices.scale(scale, scale, scale);
		}
		draw.draw(matrices, new Tint(provider, tint, texture, emissive), light);
		matrices.pop();
	}

	/**
	 * A provider that hands out the buffers of the layers it is given, with every colour multiplied by a tint; the skin's own layer
	 * is swapped for a see-through one (or a glowing one) so the transparency shows.
	 */
	static final class Tint implements VertexConsumerProvider {
		private final VertexConsumerProvider inner;
		private final float[] tint;
		private final Identifier texture;
		private final boolean emissive;

		Tint(VertexConsumerProvider inner, float[] tint, Identifier texture, boolean emissive) {
			this.inner = inner;
			this.tint = tint;
			this.texture = texture;
			this.emissive = emissive;
		}

		@Override
		public VertexConsumer getBuffer(RenderLayer layer) {
			RenderLayer use = layer;
			boolean skin = layer == RenderLayer.getEntityCutoutNoCull(texture) || layer == RenderLayer.getEntityTranslucent(texture);
			if (emissive && skin) {
				use = RenderLayer.getEntityTranslucentEmissive(texture);
			} else if (tint[3] < 0.999F && layer == RenderLayer.getEntityCutoutNoCull(texture)) {
				use = RenderLayer.getEntityTranslucent(texture);
			}
			return new TintConsumer(inner.getBuffer(use), tint);
		}
	}

	private static final class TintConsumer implements VertexConsumer {
		private final VertexConsumer inner;
		private final float[] tint;

		TintConsumer(VertexConsumer inner, float[] tint) {
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
