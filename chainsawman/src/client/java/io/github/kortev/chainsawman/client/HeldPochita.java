package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.Saw;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Pochita held as a chainsaw (kortev, 2026-10-22): his own model, not the item's picture, in both hands at your waist,
 * the way Denji carries him; your right hand on the little handle under his rear, your left on the D handle on his head,
 * the bar out ahead. He shivers as he idles and shakes hard while he cuts. (The item's picture is still his icon: its
 * model shrinks it to nothing in the hands, {@code models/item/pochita.json}.)
 */
public final class HeldPochita extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	private static final Identifier TEXTURE = ChainsawMan.id("textures/entity/pochita.png");
	/** Where he's held (the player model's pixels: y down from the neck, front toward -z), how big, his middle. */
	private static final float AT_Y = 10.5F;
	private static final float AT_Z = -6.5F;
	private static final float SCALE = 0.8F;
	private static final float MIDDLE_Y = 17.0F;
	private static final float MIDDLE_Z = 0.0F;

	private static PochitaModel model;

	public HeldPochita(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context, PochitaModel pochita) {
		super(context);
		model = pochita;
	}

	/** Whether {@code player} holds him as a chainsaw now (not in the form, where he's your heart). */
	public static boolean held(PlayerEntity player) {
		return !ClientForms.on(player) && Saw.wielding(player);
	}

	/** Both arms on him: the right low on the handle under his rear, the left reaching across to the one on his head. */
	public static void pose(PlayerEntityModel<?> body, PlayerEntity player, float tickDelta) {
		float shake = shake(player, tickDelta) * 0.04F;
		body.rightArm.pitch = -0.55F + shake;
		body.rightArm.yaw = -0.15F;
		body.rightArm.roll = 0.0F;
		body.leftArm.pitch = -1.05F - shake;
		body.leftArm.yaw = 0.62F;
		body.leftArm.roll = 0.0F;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (model == null || player.isInvisible() || !held(player)) {
			return;
		}
		matrices.push();
		getContextModel().body.rotate(matrices);
		draw(matrices, consumers, light, player, tickDelta, AT_Y, AT_Z);
		matrices.pop();
	}

	/**
	 * Him drawn with his middle at ({@code y}, {@code z}) in the pixels of whatever space {@code matrices} is in (y down,
	 * front toward -z), facing the front, shaking as he runs.
	 */
	static void draw(MatrixStack matrices, VertexConsumerProvider consumers, int light, PlayerEntity player, float tickDelta, float y, float z) {
		float s = shake(player, tickDelta);
		float age = player.age + tickDelta;
		matrices.push();
		matrices.translate(s * 0.012F * MathHelper.sin(age * 7.3F), (y + s * 0.25F * MathHelper.sin(age * 9.1F)) / 16.0F, z / 16.0F);
		// The bar dips a little as he bites.
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ClientCombat.grinding(player) ? 6.0F : 0.0F));
		matrices.scale(SCALE, SCALE, SCALE);
		matrices.translate(0.0F, -MIDDLE_Y / 16.0F, -MIDDLE_Z / 16.0F);
		model.animate(0.0F, 0.0F, age, false);
		model.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
	}

	/**
	 * Held, seen from your own eyes (in place of the hand and what it holds): low in the middle of the view, his tail (the
	 * cord) toward you, the bar out ahead; both your arms reaching in to him from either side.
	 */
	public static void renderFirstPerson(net.minecraft.client.render.item.HeldItemRenderer renderer, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider.Immediate consumers, PlayerEntity player, int light) {
		var arms = (io.github.kortev.chainsawman.client.mixin.HeldItemRendererInvoker) renderer;
		float s = shake(player, tickDelta);
		float age = player.age + tickDelta;
		float jitter = s * (MathHelper.sin(age * 9.0F) * 0.008F + MathHelper.sin(age * 23.0F) * 0.005F);
		for (net.minecraft.util.Arm arm : new net.minecraft.util.Arm[] {net.minecraft.util.Arm.RIGHT, net.minecraft.util.Arm.LEFT}) {
			float side = arm == net.minecraft.util.Arm.RIGHT ? 1.0F : -1.0F;
			matrices.push();
			matrices.translate(-side * ARM_IN + jitter, ARM_DOWN + jitter, ARM_BACK);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * ARM_TURN));
			arms.chainsawman$renderArm(matrices, consumers, light, 0.0F, 0.0F, arm);
			matrices.pop();
		}
		matrices.push();
		matrices.translate(VIEW_X + jitter, VIEW_Y + jitter, VIEW_Z);
		// Held at your right hip, the bar angled in across the middle of the view and tipped down so you see along his back.
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(VIEW_TURN));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(VIEW_TIP));
		matrices.scale(VIEW_SCALE, VIEW_SCALE, VIEW_SCALE);
		// (A model's own space is upside down and mirrored.)
		matrices.scale(-1.0F, -1.0F, 1.0F);
		draw(matrices, consumers, light, player, tickDelta, 0.0F, 0.0F);
		matrices.pop();
		consumers.draw();
	}

	/** In your own eyes: where he is in the view (blocks), and your arms drawn in toward him, down and turned in. */
	private static final float VIEW_X = 0.1F;
	private static final float VIEW_Y = -0.8F;
	private static final float VIEW_Z = -1.5F;
	private static final float VIEW_TURN = 14.0F;
	private static final float VIEW_TIP = 12.0F;
	private static final float VIEW_SCALE = 0.75F;
	private static final float ARM_IN = 0.2F;
	private static final float ARM_DOWN = -0.14F;
	private static final float ARM_BACK = 0.0F;
	private static final float ARM_TURN = 10.0F;

	/** How hard he's shaking: a putter idling, hard while he cuts (0 to 1). */
	static float shake(PlayerEntity player, float tickDelta) {
		return ClientCombat.grinding(player) ? 1.0F : 0.25F;
	}
}
