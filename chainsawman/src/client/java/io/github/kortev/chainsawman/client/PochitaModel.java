package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.PochitaEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * Pochita: a round orange body on four stubby legs, a stub of a tail, and a big round head with a chainsaw's blade
 * standing out of his forehead, teeth along its edges (the texture, {@code tools/gen_chainsaw_textures.py}, lays them
 * out). Sitting, his legs fold under him.
 */
public class PochitaModel extends SinglePartEntityModel<PochitaEntity> {
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart tail;
	private final ModelPart[] legs;

	public PochitaModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.tail = root.getChild("tail");
		this.legs = new ModelPart[] {root.getChild("leg0"), root.getChild("leg1"), root.getChild("leg2"), root.getChild("leg3")};
	}

	public static TexturedModelData texturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-3.0F, -2.5F, -4.0F, 6.0F, 5.0F, 8.0F), ModelTransform.pivot(0.0F, 18.5F, 0.0F));
		ModelPartData head = root.addChild("head", ModelPartBuilder.create().uv(28, 0).cuboid(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 5.0F),
				ModelTransform.pivot(0.0F, 17.0F, -3.5F));
		head.addChild("blade", ModelPartBuilder.create().uv(0, 14).cuboid(-0.5F, -3.5F, -12.0F, 1.0F, 2.0F, 8.0F, new Dilation(0.0F)),
				ModelTransform.NONE);
		root.addChild("tail", ModelPartBuilder.create().uv(30, 14).cuboid(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F), ModelTransform.pivot(0.0F, 17.0F, 4.0F));
		float[][] at = {{-1.8F, -2.5F}, {1.8F, -2.5F}, {-1.8F, 2.5F}, {1.8F, 2.5F}};
		for (int i = 0; i < 4; i++) {
			root.addChild("leg" + i, ModelPartBuilder.create().uv(20, 14).cuboid(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
					ModelTransform.pivot(at[i][0], 21.0F, at[i][1]));
		}
		return TexturedModelData.of(data, 64, 32);
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(PochitaEntity pochita, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
		head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
		tail.yaw = MathHelper.sin(animationProgress * 0.4F) * 0.4F;
		boolean sitting = pochita.isInSittingPose();
		for (int i = 0; i < 4; i++) {
			float swing = MathHelper.cos(limbAngle * 0.8F + (i == 1 || i == 2 ? MathHelper.PI : 0.0F)) * 1.2F * limbDistance;
			legs[i].pitch = sitting ? (i < 2 ? -1.4F : 1.4F) : swing;
		}
		root.pivotY = sitting ? 2.0F : 0.0F;
	}
}
