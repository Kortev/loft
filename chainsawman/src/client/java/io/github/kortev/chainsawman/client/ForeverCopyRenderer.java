package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.ForeverCopyEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Placeholder: a moment of the target is drawn here. */
public class ForeverCopyRenderer extends EntityRenderer<ForeverCopyEntity> {
	public ForeverCopyRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ForeverCopyEntity entity) {
		return Identifier.of("chainsawman", "textures/entity/forever_flesh.png");
	}

	@Override
	public void render(ForeverCopyEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider provider, int light) {
	}
}
