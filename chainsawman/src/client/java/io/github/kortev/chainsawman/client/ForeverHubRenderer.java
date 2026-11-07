package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.ForeverHubEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Placeholder: the devil's flesh is drawn here. */
public class ForeverHubRenderer extends EntityRenderer<ForeverHubEntity> {
	public ForeverHubRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ForeverHubEntity entity) {
		return Identifier.of("chainsawman", "textures/entity/forever_flesh.png");
	}

	@Override
	public void render(ForeverHubEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
	}
}
