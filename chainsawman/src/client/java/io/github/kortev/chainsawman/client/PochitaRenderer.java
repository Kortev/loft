package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.PochitaEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Pochita, drawn ({@link PochitaModel}). */
public class PochitaRenderer extends MobEntityRenderer<PochitaEntity, PochitaModel> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(ChainsawMan.id("pochita"), "main");
	private static final Identifier TEXTURE = ChainsawMan.id("textures/entity/pochita.png");

	public PochitaRenderer(EntityRendererFactory.Context context) {
		super(context, new PochitaModel(context.getPart(LAYER)), 0.35F);
	}

	@Override
	protected void setupTransforms(PochitaEntity pochita, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
		super.setupTransforms(pochita, matrices, animationProgress, bodyYaw, tickDelta, scale);
		if (pochita.fainted()) {
			// Fainted: over on his side.
			matrices.translate(0.3F, 0.0F, 0.0F);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0F));
		}
	}

	@Override
	public Identifier getTexture(PochitaEntity pochita) {
		return TEXTURE;
	}
}
