package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.hotel.SquadMemberEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * The missing squad's hunters: the player's own model, in a Public Safety suit's skin (three of them, a little different), katana in hand.
 * Starving or downed ones sit against the wall (the model's sitting pose, a little lower); the rest stand and walk.
 */
public class SquadRenderer extends BipedEntityRenderer<SquadMemberEntity, PlayerEntityModel<SquadMemberEntity>> {
	private static final Identifier[] SKINS = {ChainsawMan.id("textures/entity/squad_0.png"), ChainsawMan.id("textures/entity/squad_1.png"),
			ChainsawMan.id("textures/entity/squad_2.png")};

	public SquadRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);
		this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
	}

	@Override
	public Identifier getTexture(SquadMemberEntity entity) {
		return SKINS[Math.floorMod(entity.variant(), SKINS.length)];
	}

	@Override
	protected void setupTransforms(SquadMemberEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
		this.model.riding = entity.sitting();
		super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta, scale);
		if (entity.sitting()) {
			matrices.translate(0.0F, -0.55F, 0.0F);
		}
	}
}
