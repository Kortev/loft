package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * The form on whoever is in it: the chainsaw head in place of their own (hidden, with their hat layer, by {@code
 * HeadHiddenMixin}), the saws out of their forearms, and the cord on their chest; each part moving with its bone.
 */
public final class FormFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	public static final Identifier TEXTURE = ChainsawMan.id("textures/entity/form.png");

	public FormFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (!ClientForms.on(player) || player.isInvisible()) {
			return;
		}
		FormMesh mesh = FormMesh.get();
		if (mesh == null) {
			return;
		}
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		int overlay = LivingEntityRenderer.getOverlay(player, 0.0F);
		PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
		int chainFrame = 0;
		for (FormMesh.Part part : mesh.parts.values()) {
			// Each chain comes in a few frames, each a step further round its bar: one at a time is drawn.
			if (part.chainFrame >= 0 && part.chainFrame != chainFrame) {
				continue;
			}
			ModelPart bone = switch (part.bone) {
				case "head" -> model.head;
				case "body" -> model.body;
				case "right_arm" -> model.rightArm;
				case "left_arm" -> model.leftArm;
				default -> null;
			};
			if (bone == null) {
				continue;
			}
			matrices.push();
			bone.rotate(matrices);
			part.draw(matrices.peek(), out, light, overlay);
			matrices.pop();
		}
	}
}
