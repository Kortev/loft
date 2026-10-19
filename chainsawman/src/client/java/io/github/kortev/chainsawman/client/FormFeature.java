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
 * HeadHiddenMixin}), the saws out of their forearms, and the cord on their chest; each part moving with its bone. The
 * chains run round their bars all the while, and race when they swing.
 */
public final class FormFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	public static final Identifier TEXTURE = ChainsawMan.id("textures/entity/form.png");
	/** How many frames each chain is baked in, and how fast they go by (frames a tick) idling and swinging. */
	private static final int CHAIN_FRAMES = 4;
	private static final float CHAIN_IDLE = 0.5F;
	private static final float CHAIN_SWING = 2.0F;

	public FormFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (!ClientForms.on(player) || player.isInvisible()) {
			return;
		}
		PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
		int overlay = LivingEntityRenderer.getOverlay(player, 0.0F);
		draw(matrices, consumers, light, overlay, player, animationProgress, "head", model.head);
		draw(matrices, consumers, light, overlay, player, animationProgress, "body", model.body);
		draw(matrices, consumers, light, overlay, player, animationProgress, "right_arm", model.rightArm);
		draw(matrices, consumers, light, overlay, player, animationProgress, "left_arm", model.leftArm);
	}

	/**
	 * Draws the parts of the form that follow one bone of the player model, the stack moved to the bone first; the
	 * chains on the frame they have run to by {@code time} (ticks).
	 */
	public static void draw(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, AbstractClientPlayerEntity player,
			float time, String boneName, ModelPart bone) {
		FormMesh mesh = FormMesh.get();
		if (mesh == null) {
			return;
		}
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		float speed = player.handSwinging ? CHAIN_SWING : CHAIN_IDLE;
		int chainFrame = Math.floorMod((int) (time * speed), CHAIN_FRAMES);
		matrices.push();
		bone.rotate(matrices);
		for (FormMesh.Part part : mesh.parts.values()) {
			// Each chain comes in a few frames, each a step further round its bar: one at a time is drawn.
			if (part.bone.equals(boneName) && (part.chainFrame < 0 || part.chainFrame == chainFrame)) {
				part.draw(matrices.peek(), out, light, overlay);
			}
		}
		matrices.pop();
	}
}
