package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.ShaftHeadEntity;
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
 * A head lunging out of the Eternity Devil's mass ({@link ShaftHeadEntity}), a placeholder: a neck of two lumps, a head lump with a mouth
 * that opens as it comes up and snaps shut at the bite, and back down. Its own class, to be replaced by the devil's final look.
 */
public class ShaftHeadRenderer extends EntityRenderer<ShaftHeadEntity> {
	public ShaftHeadRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ShaftHeadEntity entity) {
		return ForeverHubRenderer.FLESH;
	}

	@Override
	public boolean shouldRender(ShaftHeadEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	@Override
	public void render(ShaftHeadEntity head, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(head.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float t = world.getTime() + delta - head.start();
		float rise = ease(t / ShaftHeadEntity.RISE);
		float sink = ease((t - (ShaftHeadEntity.BITE_AT + 6)) / (ShaftHeadEntity.LIFE - ShaftHeadEntity.BITE_AT - 6));
		float h = rise * (1.0F - sink) * (head.struck() ? 0.25F : 1.0F);
		if (h <= 0.03F) {
			return;
		}
		int lit = ForeverGeo.lit(light, 11, 8);
		int seed = head.getId() * 17;
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(head.getYaw()));
		ForeverGeo.Pen pen = new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.FLESH)), matrices.peek(), lit);
		ForeverGeo.blob(pen, 0.0, -0.3, 0.0, 0.6, 0.9 * h + 0.3, 0.6, 6, 10, 1.2, seed, 0.2, 2.0F, 1.0F);
		ForeverGeo.blob(pen, 0.0, 0.5 * h, 0.1 * h, 0.5, 0.7 * h, 0.5, 6, 10, 1.2, seed + 3, 0.2, 2.0F, 1.0F);
		// The head: leaning out, a lump with the mouth on its front.
		double hy = 1.1 * h;
		ForeverGeo.blob(pen, 0.0, hy, 0.35 * h, 0.7, 0.62, 0.72, 7, 12, 1.3, seed + 9, 0.25, 2.0F, 1.0F);
		float gape = t < ShaftHeadEntity.BITE_AT - 3 ? 0.9F * ease(t / (ShaftHeadEntity.BITE_AT - 3.0F))
				: t < ShaftHeadEntity.BITE_AT ? 0.9F : 0.9F * (1.0F - ease((t - ShaftHeadEntity.BITE_AT) / 3.0F));
		ForeverLayout.Mouth m = new ForeverLayout.Mouth();
		m.width = 0.9;
		m.seed = seed;
		matrices.push();
		matrices.translate(0.0, hy + 0.05, 0.35 * h + 0.55);
		matrices.multiply(ForeverHubRenderer.frame(new Vec3d(0.0, 0.15, 1.0)));
		ForeverHubRenderer.mouth(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.MOUTH)), matrices.peek(), lit), m, gape * h, 1.0F);
		matrices.pop();
		matrices.pop();
	}
}
