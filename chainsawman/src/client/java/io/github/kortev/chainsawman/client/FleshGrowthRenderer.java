package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.hotel.FleshGrowthEntity;
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
 * The flesh that grows at the corners of the loop floor ({@link FleshGrowthEntity}): lumps of the Eternity Devil's own flesh clinging to a
 * wall, floor or ceiling, a heap of two or three lobes, with an eye (looking at whoever looks) on some. It is drawn with its back to the wall
 * (+z out of it, into the corridor), coming up over about a second.
 */
public class FleshGrowthRenderer extends EntityRenderer<FleshGrowthEntity> {
	public FleshGrowthRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(FleshGrowthEntity entity) {
		return ForeverHubRenderer.FLESH;
	}

	@Override
	public boolean shouldRender(FleshGrowthEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(FleshGrowthEntity growth, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
		if (!(growth.getWorld() instanceof ClientWorld world)) {
			return;
		}
		float grow = MathHelper.clamp((world.getTime() + delta - growth.born()) / 24.0F, 0.0F, 1.0F);
		grow = grow * grow * (3.0F - 2.0F * grow);
		if (grow <= 0.02F) {
			return;
		}
		int lit = ForeverGeo.lit(light, 10, 8);
		int seed = growth.seed();
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-growth.getYaw()));
		int kind = growth.kind();
		// (Those up in the ceiling's corner are drawn as bulges of the wall, facing out, so they are lit like the rest.)
		boolean bulge = kind != FleshGrowthEntity.FLOOR;
		if (kind == FleshGrowthEntity.CEILING || kind == FleshGrowthEntity.HANGING) {
			matrices.translate(0.0, -0.32, 0.0);
		}
		double s = bulge ? 1.0 : 1.0 * (0.7 + 0.3 * ((seed >>> 3) % 5) / 4.0);
		if (kind == FleshGrowthEntity.WALL) {
			s = 1.15;
		}
		if (bulge) {
			// A bulge out of the wall's face: the wall is behind it; it is a lump lying against the wall, facing out.
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
			ForeverGeo.blob(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.FLESH)), matrices.peek(), lit), 0.0, 0.0, 0.0, 0.6 * s * grow,
					0.52 * s * grow, 0.7 * s * grow, 8, 12, 0.5, seed, 0.3, 2.0F, 1.0F);
			matrices.pop();
		} else {
			// Two or three lobes in the corner, the biggest against the wall.
			for (int i = 0; i < 3; i++) {
				double a = seed * 0.37 + i * 2.2;
				double r = i == 0 ? 0.0 : 0.38;
				matrices.push();
				matrices.translate(Math.cos(a) * r * 0.9, 0.0, Math.abs(Math.sin(a)) * r * grow + 0.12);
				ForeverGeo.blob(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.FLESH)), matrices.peek(), lit), 0.0, 0.0, 0.0,
						(i == 0 ? 0.52 : 0.3) * s * grow, (i == 0 ? 0.62 : 0.34) * s * grow, (i == 0 ? 0.5 : 0.3) * s * grow, 8, 12, 0.35, seed + i * 19, 0.3, 2.0F, 1.0F);
				matrices.pop();
			}
		}
		// An eye on about half of them, on the lump's face, looking at the viewer.
		if ((seed & 1) == 0 && grow > 0.6F) {
			Vec3d pos = new Vec3d(0.12, kind == FleshGrowthEntity.WALL ? 0.1 : 0.42 * s, kind == FleshGrowthEntity.WALL ? 0.42 * s : 0.45 * s);
			double radius = 0.13 * s;
			net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
			Vec3d look = pos.normalize();
			if (client.player != null) {
				Vec3d eye = client.player.getCameraPosVec(delta).subtract(growth.getPos());
				org.joml.Vector3f v = new org.joml.Vector3f((float) eye.x, (float) eye.y, (float) eye.z).rotateY((float) Math.toRadians(growth.getYaw()));
				if (kind == FleshGrowthEntity.CEILING || kind == FleshGrowthEntity.HANGING) {
					v.rotateY((float) Math.PI).rotateX((float) Math.PI);
				}
				Vec3d toViewer = new Vec3d(v.x, v.y, v.z).subtract(pos);
				if (toViewer.lengthSquared() > 1.0E-6) {
					look = toViewer.normalize();
				}
			}
			if (look.z < 0.2) {
				look = new Vec3d(look.x * 0.5, look.y * 0.5, 0.2 + Math.abs(look.z)).normalize();
			}
			matrices.push();
			matrices.translate(pos.x, pos.y, pos.z);
			matrices.multiply(new org.joml.Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, (float) look.x, (float) look.y, (float) look.z));
			ForeverGeo.ball(new ForeverGeo.Pen(provider.getBuffer(RenderLayer.getEntityCutoutNoCull(ForeverHubRenderer.EYE)), matrices.peek(),
					net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE), radius * grow, 8, 14);
			matrices.pop();
		}
		matrices.pop();
	}
}
