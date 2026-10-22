package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.BeamEntity;
import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.LightType;

/**
 * Beam, drawn ({@link BeamModel}): the model's back {@link BeamEntity#BACK} above where he is (the surface), the rest of him
 * lower, in the ground, so only the back and the fin show; turned to the way he faces, pitched as he leaps, no shadow
 * (he is under the ground).
 */
public class BeamRenderer extends EntityRenderer<BeamEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(ChainsawMan.id("beam"), "main");
	private static final Identifier TEXTURE = ChainsawMan.id("textures/entity/beam.png");
	/** Where his body turns when he pitches: this far under the top of his back (blocks). */
	private static final float MIDDLE = 0.5F;

	private final BeamModel model;

	public BeamRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.model = new BeamModel(context.getPart(LAYER));
		this.shadowRadius = 0.0F;
	}

	@Override
	public void render(BeamEntity beam, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		matrices.push();
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, beam.prevYaw, beam.getYaw());
		float pitch = MathHelper.lerp(tickDelta, beam.prevPitch, beam.getPitch());
		matrices.translate(0.0F, BeamEntity.BACK - MIDDLE, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));
		matrices.translate(0.0F, MIDDLE, 0.0F);
		matrices.scale(-1.0F, -1.0F, 1.0F);
		model.animate(beam.swim(tickDelta), beam.pace(), beam.sinceBite(tickDelta));
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		model.render(matrices, vertices, light);
		matrices.pop();
		super.render(beam, yaw, tickDelta, matrices, consumers, light);
	}

	/**
	 * How light he is is judged from the air over his back, not from where he is: on a slope he is partly in the ground,
	 * and lit from inside a block he would be drawn black.
	 */
	@Override
	protected int getBlockLight(BeamEntity beam, BlockPos pos) {
		return above(beam, pos, LightType.BLOCK);
	}

	@Override
	protected int getSkyLight(BeamEntity beam, BlockPos pos) {
		return above(beam, pos, LightType.SKY);
	}

	/** The brightest of the three blocks of air above {@code pos}. */
	private static int above(BeamEntity beam, BlockPos pos, LightType type) {
		int best = 0;
		for (int up = 0; up <= 3; up++) {
			best = Math.max(best, beam.getWorld().getLightLevel(type, pos.up(up)));
		}
		return best;
	}

	@Override
	public Identifier getTexture(BeamEntity beam) {
		return TEXTURE;
	}
}
