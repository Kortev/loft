package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.BlindfoldItem;
import io.github.kortev.gojolander.Gojolander;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * The blindfold on whoever wears it: a band of black cloth round the head over the eyes, knotted at the back with its
 * two ends hanging. As its owner casts his Domain he pushes it up onto his forehead, and his eyes show, burning blue,
 * until the Domain is gone. And while the sign is up, his right hand (drawn up in front of him by {@code
 * PlayerModelMixin}) has its first two fingers crossed, the rest curled.
 */
public final class BlindfoldFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	private static final Identifier CLOTH = Gojolander.id("textures/entity/blindfold.png");
	private static final Identifier EYES = Gojolander.id("textures/entity/six_eyes.png");
	/** How far up the forehead the band goes when it is pushed up (pixels), and how far it tips back. */
	private static final float LIFT = 2.8F;
	private static final float TIP = 0.16F;
	/** How far the sign's two fingers bend up from the line of the arm at the knuckles (radians). */
	private static final float KNUCKLE = -1.3F;
	/** How long it takes to push up, and to come down again (ticks). */
	private static final double UP = 5.0;
	private static final double DOWN = 6.0;

	private final ModelPart band;
	private final ModelPart wideHand;
	private final ModelPart slimHand;

	public BlindfoldFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
		band = bandModel().createModel();
		wideHand = handModel(false).createModel();
		slimHand = handModel(true).createModel();
	}

	/** The band, the knot and the two ends, in the head's own space (8 wide, from y -8 at the crown to 0 at the chin). */
	static TexturedModelData bandModel() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("band", ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -5.1F, -4.0F, 8.0F, 2.0F, 8.0F, new Dilation(0.62F)),
				ModelTransform.NONE);
		root.addChild("knot", ModelPartBuilder.create().uv(0, 12).cuboid(-1.0F, -5.4F, 4.5F, 2.0F, 2.6F, 1.0F, new Dilation(0.08F)),
				ModelTransform.NONE);
		root.addChild("left_end", ModelPartBuilder.create().uv(8, 12).cuboid(-0.5F, 0.0F, -0.25F, 1.0F, 4.6F, 0.5F),
				ModelTransform.of(-0.6F, -3.6F, 5.2F, 0.22F, 0.0F, 0.16F));
		root.addChild("right_end", ModelPartBuilder.create().uv(12, 12).cuboid(-0.5F, 0.0F, -0.25F, 1.0F, 3.8F, 0.5F),
				ModelTransform.of(0.6F, -3.6F, 5.2F, 0.3F, 0.0F, -0.22F));
		return TexturedModelData.of(data, 64, 32);
	}

	/**
	 * The hand making the sign, at the end of the right arm (whose own space runs from its shoulder at y -2 down to the
	 * hand at y 10): index and middle finger up and crossed, the other two and the thumb curled in. Textured from the
	 * front of the arm's own skin, low down, where it is the hand.
	 */
	static TexturedModelData handModel(boolean slim) {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		float left = slim ? -2.0F : -3.0F;
		float wide = slim ? 3.0F : 4.0F;
		// The arm is held out level in front of him; the fingers bend up at the knuckles to stand straight up before his chin.
		root.addChild("index", ModelPartBuilder.create().uv(44, 26).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 3.4F, 1.0F),
				ModelTransform.of(left + 1.0F, 9.6F, -1.4F, KNUCKLE, 0.0F, 0.2F));
		root.addChild("middle", ModelPartBuilder.create().uv(44, 26).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 3.8F, 1.0F),
				ModelTransform.of(left + 1.7F, 9.6F, -1.0F, KNUCKLE, 0.0F, -0.26F));
		root.addChild("curled", ModelPartBuilder.create().uv(44, 28).cuboid(0.0F, 0.0F, 0.0F, wide - 2.2F, 1.2F, 1.6F),
				ModelTransform.pivot(left + 2.0F, 9.7F, -1.6F));
		root.addChild("thumb", ModelPartBuilder.create().uv(44, 28).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 1.8F, 1.0F),
				ModelTransform.of(left + wide - 0.4F, 9.0F, -1.2F, KNUCKLE * 0.6F, 0.0F, -0.6F));
		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		ClientDomain unveiled = ClientDomains.unveiled(player);
		float lift = unveiled != null ? lift(unveiled, unveiled.time(tickDelta)) : 0.0F;
		if (BlindfoldItem.wearing(player)) {
			matrices.push();
			getContextModel().head.rotate(matrices);
			if (lift > 0.0F) {
				matrices.translate(0.0F, -LIFT / 16.0F * lift, 0.0F);
				matrices.multiply(RotationAxis.POSITIVE_X.rotation(-TIP * lift), 0.0F, -0.5F, 0.0F);
			}
			band.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(CLOTH)), light, OverlayTexture.DEFAULT_UV);
			matrices.pop();
		}
		if (lift > 0.0F) {
			// The eyes under it, burning: drawn full bright, over the face.
			getContextModel().head.render(matrices, consumers.getBuffer(RenderLayer.getEyes(EYES)), LightmapTextureManager.MAX_LIGHT_COORDINATE,
					OverlayTexture.DEFAULT_UV, gray(lift));
		}
		ClientDomain signing = ClientDomains.signing(player);
		if (signing != null && HandSign.raise(signing, signing.time(tickDelta)) > 0.5F) {
			SkinTextures skin = player.getSkinTextures();
			matrices.push();
			getContextModel().rightArm.rotate(matrices);
			ModelPart hand = skin.model() == SkinTextures.Model.SLIM ? slimHand : wideHand;
			hand.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(skin.texture())), light, OverlayTexture.DEFAULT_UV);
			matrices.pop();
		}
	}

	/** White as bright as {@code k}: the eyes' layer adds its light, so it fades by colour, not by alpha. */
	private static int gray(float k) {
		int g = MathHelper.clamp((int) (k * 255.0F), 0, 255);
		return 0xFF000000 | g << 16 | g << 8 | g;
	}

	/** How far up the band is at {@code t}: pushed up with the sign, down again as the barrier goes. */
	private static float lift(ClientDomain domain, double t) {
		double up = MathHelper.clamp(t / UP, 0.0, 1.0);
		double down = MathHelper.clamp((t - (domain.end() - DOWN)) / DOWN, 0.0, 1.0);
		double k = Math.min(up, 1.0 - down);
		return (float) (k * k * (3.0 - 2.0 * k));
	}
}
