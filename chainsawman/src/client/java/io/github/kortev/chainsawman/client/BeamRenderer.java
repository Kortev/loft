package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.BeamEntity;
import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Beam, drawn ({@link BeamModel}): standing on the ground on his legs, turned to the way he faces, pitched as he leaps;
 * with the chain for reins from the rider's hands to the two corners of his mouth ({@link ChainRender#reins}). The
 * shadow is as wide as his body.
 */
public class BeamRenderer extends EntityRenderer<BeamEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(ChainsawMan.id("beam"), "main");
	private static final Identifier TEXTURE = ChainsawMan.id("textures/entity/beam.png");
	/** Where his body turns when he pitches: this far under the top of his back (blocks). */
	private static final float MIDDLE = 1.0F;
	/** The corners of his mouth, from his middle: how far out to the sides, ahead of it, and above his feet (blocks). */
	private static final double CORNER_OUT = 0.5;
	private static final double CORNER_AHEAD = 0.85;
	private static final double CORNER_UP = 1.56;
	/** A rider's hands holding the reins, from their feet: how far ahead, how high, how far apart (blocks); and from your own eyes, in your own view. */
	private static final double HAND_AHEAD = 0.5;
	private static final double HAND_UP = 0.85;
	private static final double HAND_APART = 0.22;
	private static final double OWN_AHEAD = 0.6;
	private static final double OWN_DOWN = 0.6;

	private final BeamModel model;

	public BeamRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.model = new BeamModel(context.getPart(LAYER));
		this.shadowRadius = 1.0F;
	}

	@Override
	public void render(BeamEntity beam, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, beam.prevYaw, beam.getYaw());
		reins(beam, bodyYaw, tickDelta, matrices, consumers);
		matrices.push();
		float pitch = MathHelper.lerp(tickDelta, beam.prevPitch, beam.getPitch());
		matrices.translate(0.0F, BeamModel.BACK - MIDDLE, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));
		matrices.translate(0.0F, MIDDLE, 0.0F);
		matrices.scale(-1.0F, -1.0F, 1.0F);
		model.animate(beam.walk(tickDelta), beam.time(tickDelta), beam.pace(), beam.sinceBite(tickDelta), beam.air(tickDelta));
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		model.render(matrices, vertices, light);
		matrices.pop();
		super.render(beam, yaw, tickDelta, matrices, consumers, light);
	}

	/** The reins: from where the rider's hands are (in your own view, low in front of you) to the corners of his mouth. */
	private static void reins(BeamEntity beam, float bodyYaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers) {
		Entity rider = beam.getFirstPassenger();
		if (rider == null || beam.diving()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d origin = beam.getLerpedPos(tickDelta);
		Vec3d ahead = Vec3d.fromPolar(0.0F, bodyYaw);
		Vec3d left = new Vec3d(ahead.z, 0.0, -ahead.x);
		Vec3d hands;
		Vec3d across;
		if (rider == client.player && !client.gameRenderer.getCamera().isThirdPerson()) {
			Vec3d look = rider.getRotationVec(tickDelta);
			Vec3d leftOfYou = Vec3d.fromPolar(0.0F, rider.getYaw(tickDelta) - 90.0F);
			hands = client.gameRenderer.getCamera().getPos().add(look.multiply(OWN_AHEAD)).add(0.0, -OWN_DOWN, 0.0);
			across = leftOfYou.multiply(HAND_APART);
		} else {
			Vec3d facing = Vec3d.fromPolar(0.0F, rider.getYaw(tickDelta));
			hands = rider.getLerpedPos(tickDelta).add(facing.multiply(HAND_AHEAD)).add(0.0, HAND_UP, 0.0);
			across = new Vec3d(facing.z, 0.0, -facing.x).multiply(HAND_APART);
		}
		Vec3d corner = origin.add(ahead.multiply(CORNER_AHEAD)).add(0.0, CORNER_UP, 0.0);
		ChainRender.reins(matrices, consumers, client.world, origin, hands.add(across),
				corner.add(left.multiply(CORNER_OUT)));
		ChainRender.reins(matrices, consumers, client.world, origin, hands.subtract(across), corner.subtract(left.multiply(CORNER_OUT)));
	}

	@Override
	public Identifier getTexture(BeamEntity beam) {
		return TEXTURE;
	}
}
