package io.github.kortev.chitty.client;

import io.github.kortev.chitty.airship.AirshipBombEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** A falling bomb: one of the bombs from the airship's mesh, nose down, turning slowly as it drops. */
public class AirshipBombRenderer extends EntityRenderer<AirshipBombEntity> {
	public AirshipBombRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.shadowRadius = 0.15F;
	}

	@Override
	public Identifier getTexture(AirshipBombEntity bomb) {
		return AirshipRenderer.TEXTURE;
	}

	@Override
	public void render(AirshipBombEntity bomb, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
		ChittyMesh mesh = ChittyMesh.get("airship");
		ChittyMesh.Part part = mesh == null ? null : mesh.parts.get("bomb_0");
		if (part != null) {
			matrices.push();
			matrices.translate(0.0F, 0.27F, 0.0F);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((bomb.age + tickDelta) * 9.0F - yaw));
			matrices.multiply(part.rest);
			part.draw(matrices.peek(), buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(AirshipRenderer.TEXTURE)), light,
					OverlayTexture.DEFAULT_UV, null);
			matrices.pop();
		}
		super.render(bomb, yaw, tickDelta, matrices, buffers, light);
	}
}
