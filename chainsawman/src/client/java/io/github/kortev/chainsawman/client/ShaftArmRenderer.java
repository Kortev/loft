package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.ShaftArmEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * One of the Eternity Devil's arms ({@link ShaftArmEntity}), a placeholder: a column of flesh lumps from the mass up to a hand (a palm and
 * four fingers, open while it reaches and closed on whoever it holds). Its own class, to be replaced by the devil's final look.
 */
public class ShaftArmRenderer extends EntityRenderer<ShaftArmEntity> {
	public ShaftArmRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ShaftArmEntity entity) {
		return ForeverHubRenderer.FLESH;
	}

	@Override
	public boolean shouldRender(ShaftArmEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(ShaftArmEntity arm, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(arm.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float now = world.getTime() + delta;
		double height = Math.max(0.3, arm.tip() - arm.getY());
		int lit = ForeverGeo.lit(light, 11, 8);
		int seed = arm.getId() * 31;
		ForeverGeo.Pen pen = new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.FLESH)), matrices.peek(), lit);
		// The column: lumps up it, thinner toward the hand, swaying as they climb.
		int n = (int) Math.ceil(height / 0.8);
		for (int i = 0; i < n; i++) {
			double y = i * 0.8;
			double u = Math.min(1.0, y / Math.max(1.0, height));
			double r = 0.55 - 0.18 * u;
			double sx = Math.sin(i * 1.7 + seed + now * 0.15) * 0.1;
			double sz = Math.cos(i * 1.3 + seed + now * 0.13) * 0.1;
			ForeverGeo.blob(pen, sx, y, sz, r, 0.5, r, 6, 10, 1.2, seed + i * 13, 0.2, 2.0F, 1.0F);
		}
		// The hand at the tip: a palm and four fingers, curled when it holds someone.
		matrices.push();
		matrices.translate(0.0, height - 0.1, 0.0);
		ForeverGeo.Pen hand = new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.FLESH)), matrices.peek(), lit);
		ForeverGeo.blob(hand, 0.0, 0.0, 0.0, 0.5, 0.3, 0.5, 6, 10, 1.2, seed + 7, 0.15, 2.0F, 1.0F);
		float curl = arm.holding() ? 1.0F : 0.25F + 0.1F * (float) Math.sin(now * 0.4);
		for (int f = 0; f < 4; f++) {
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * 90.0F + 20.0F));
			matrices.translate(0.34, 0.1, 0.0);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotation(-0.35F - 1.1F * curl));
			ForeverGeo.Pen finger = new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.FLESH)), matrices.peek(), lit);
			ForeverGeo.blob(finger, 0.0, 0.0, 0.0, 0.14, 0.62, 0.14, 5, 8, 1.2, seed + f * 5, 0.1, 1.0F, 1.0F);
			matrices.pop();
		}
		matrices.pop();
	}
}
