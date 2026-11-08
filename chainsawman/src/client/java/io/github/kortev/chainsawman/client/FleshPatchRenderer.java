package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.FleshPatchEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Flesh creeping across the floor from the Eternity Devil ({@link FleshPatchEntity}): a low cluster of lumps with an eye and a mouth in
 * the middle that bites (opens wide and snaps) when it has something standing on it; it comes up, stays, and sinks back.
 */
public class FleshPatchRenderer extends EntityRenderer<FleshPatchEntity> {
	public FleshPatchRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(FleshPatchEntity entity) {
		return ForeverHubRenderer.FLESH;
	}

	@Override
	public boolean shouldRender(FleshPatchEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(FleshPatchEntity patch, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(patch.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float now = world.getTime() + delta;
		float t = now - patch.start();
		float rise = ease(t / FleshPatchEntity.RISE);
		float end = patch.span() - FleshPatchEntity.SINK;
		float sink = t > end ? ease((t - end) / FleshPatchEntity.SINK) : 0.0F;
		float h = rise * (1.0F - sink);
		if (h <= 0.02F) {
			return;
		}
		int lit = ForeverGeo.lit(light, 11, 8);
		int seed = patch.getId() * 31;
		matrices.push();
		matrices.translate(0.0, 0.0, 0.0);
		matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(patch.getYaw()));
		// Three lobes round the middle and a big one under it.
		for (int i = 0; i < 4; i++) {
			double a = i * 2.1 + seed;
			double r = i == 3 ? 0.0 : 0.55;
			matrices.push();
			matrices.translate(Math.cos(a) * r * h, 0.0, Math.sin(a) * r * h);
			ForeverGeo.blob(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.FLESH)), matrices.peek(), lit), 0.0, 0.0, 0.0,
					(i == 3 ? 0.85 : 0.5) * (0.4 + 0.6 * h), (i == 3 ? 0.3 : 0.26) * h, (i == 3 ? 0.85 : 0.5) * (0.4 + 0.6 * h), 7, 12, 0.3, seed + i * 17, 0.3, 2.0F, 1.0F);
			matrices.pop();
		}
		// The mouth on top, open when it has just bitten.
		float since = now - patch.bitAt();
		float bite = since < 4.0F ? 0.0F : (since < 14.0F ? 0.9F * (1.0F - (since - 4.0F) / 10.0F) : 0.0F);
		boolean active = patch.active(world.getTime());
		float gape = active ? Math.max(bite, 0.12F + 0.06F * MathHelper.sin(now * 0.3F)) : 0.04F;
		ForeverLayout.Mouth m = new ForeverLayout.Mouth();
		m.width = 0.95;
		m.seed = seed;
		matrices.push();
		matrices.translate(0.0, 0.28 * h, 0.0);
		matrices.multiply(ForeverHubRenderer.frame(new Vec3d(0.0, 1.0, 0.0)));
		ForeverHubRenderer.mouth(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.MOUTH)), matrices.peek(), lit), m, gape * h, 1.0F);
		matrices.pop();
		matrices.pop();
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
