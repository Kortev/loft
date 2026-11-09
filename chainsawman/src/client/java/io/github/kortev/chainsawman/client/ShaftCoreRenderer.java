package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.ShaftCoreEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * The Eternity Devil's core ({@link ShaftCoreEntity}), a placeholder: the figure-8 lying on its side, two lumps of flesh joined at the
 * waist, an eye and a mouth on each. It rises out of the pit, beats while it is offered, and falls in two halves when it is cut. Its own
 * class, to be replaced by the devil's final look.
 */
public class ShaftCoreRenderer extends EntityRenderer<ShaftCoreEntity> {
	public ShaftCoreRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ShaftCoreEntity entity) {
		return ForeverHubRenderer.FLESH;
	}

	@Override
	public boolean shouldRender(ShaftCoreEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	@Override
	public void render(ShaftCoreEntity core, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(core.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float now = world.getTime() + delta;
		float rise = ease((now - core.born()) / ShaftCoreEntity.RISE);
		float cut = core.cutAt() < 0 ? 0.0F : ease((now - core.cutAt()) / ShaftCoreEntity.FALL);
		float beat = 1.0F + 0.06F * MathHelper.sin(now * 0.35F) * (core.cutAt() < 0 ? 1.0F : 0.0F);
		int lit = ForeverGeo.lit(light, 13, 10);
		matrices.push();
		matrices.translate(0.0, -2.2 * (1.0 - rise) + 0.1, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(core.getYaw()));
		for (int side = -1; side <= 1; side += 2) {
			matrices.push();
			// (Cut: each half slides out and tips away.)
			matrices.translate(side * (0.62 + 0.9 * cut), -0.55 * cut, 0.0);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotation(-side * 0.7F * cut));
			matrices.scale(beat, beat, beat);
			int seed = core.getId() * 7 + side;
			ForeverGeo.Pen pen = new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.FLESH)), matrices.peek(), lit);
			ForeverGeo.blob(pen, 0.0, 0.0, 0.0, 0.75, 0.62, 0.75, 8, 14, 1.4, seed, 0.2, 2.0F, 1.0F);
			// An eye on top, looking up the shaft, and a mouth on the front.
			matrices.push();
			matrices.translate(0.0, 0.52, 0.0);
			ForeverGeo.ball(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.EYE)), matrices.peek(), LightmapTextureManager.MAX_LIGHT_COORDINATE), 0.3, 8, 14);
			matrices.pop();
			ForeverLayout.Mouth m = new ForeverLayout.Mouth();
			m.width = 0.8;
			m.seed = seed;
			matrices.push();
			matrices.translate(0.0, -0.05, 0.62);
			matrices.multiply(ForeverHubRenderer.frame(new Vec3d(0.0, 0.0, 1.0)));
			float gape = core.cuttable() ? 0.5F + 0.3F * MathHelper.sin(now * 0.5F + side) : 0.18F + 0.1F * MathHelper.sin(now * 0.3F + side);
			ForeverHubRenderer.mouth(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.MOUTH)), matrices.peek(), lit), m, gape, 1.0F);
			matrices.pop();
			matrices.pop();
		}
		matrices.pop();
	}
}
