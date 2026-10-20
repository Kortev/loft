package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.PochitaEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Pochita, drawn ({@link PochitaModel}). */
public class PochitaRenderer extends MobEntityRenderer<PochitaEntity, PochitaModel> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(ChainsawMan.id("pochita"), "main");
	private static final Identifier TEXTURE = ChainsawMan.id("textures/entity/pochita.png");

	public PochitaRenderer(EntityRendererFactory.Context context) {
		super(context, new PochitaModel(context.getPart(LAYER)), 0.35F);
	}

	@Override
	public Identifier getTexture(PochitaEntity pochita) {
		return TEXTURE;
	}
}
