package io.github.kortev.chitty.client;

import io.github.kortev.chitty.carriage.CarriageEntity;
import io.github.kortev.chitty.client.mixin.HorseModelAccess;
import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.HorseEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.util.Identifier;

/**
 * The carriage's harness, on a horse hitched to it, drawn as the game draws a horse's saddle: boxes in its model's own
 * body and head frames, moving as they move (tools/carriage_model.py's HARNESS, which its renders draw). Round the base
 * of the neck a collar with brass hames, the traces' hooks on it; blinkers, a browband and a noseband, the bit's rings,
 * and the black plume on its poll; on its back a pad with brass terrets and a girth, the tugs that carry the shafts, the
 * crupper and the breeching round its quarters. Its texture is in bands, one colour each: leather, brass and plume.
 */
public class CarriageHarnessFeature<T extends AbstractHorseEntity, M extends HorseEntityModel<T>> extends FeatureRenderer<T, M> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(ShootingStar.id("carriage_harness"), "main");
	private static final Identifier TEXTURE = ShootingStar.id("textures/entity/carriage_harness.png");
	private final ModelPart body;
	private final ModelPart head;

	public CarriageHarnessFeature(FeatureRendererContext<T, M> context, ModelPart root) {
		super(context);
		this.body = root.getChild("body");
		this.head = root.getChild("head");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		// Leather at (0, 0), brass at (0, 32), the plume at (0, 48): each band one colour, so a box anywhere in it will do.
		root.addChild("body", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-5.5F, -8.6F, -11.5F, 11F, 0.6F, 4.5F)
				.uv(0, 0).cuboid(-5.6F, -8F, -11F, 0.6F, 5.5F, 3.5F)
				.uv(0, 0).cuboid(5F, -8F, -11F, 0.6F, 5.5F, 3.5F)
				.uv(0, 32).cuboid(-3.4F, -9.8F, -9.6F, 1F, 1.2F, 0.4F)
				.uv(0, 32).cuboid(2.4F, -9.8F, -9.6F, 1F, 1.2F, 0.4F)
				.uv(0, 0).cuboid(-5.45F, -2.5F, -10F, 0.45F, 4.5F, 1.5F)
				.uv(0, 0).cuboid(5F, -2.5F, -10F, 0.45F, 4.5F, 1.5F)
				.uv(0, 0).cuboid(-5.5F, 2F, -10F, 11F, 0.5F, 1.5F)
				.uv(0, 0).cuboid(-7.6F, -3.4F, -10.4F, 2.6F, 1F, 2.2F)
				.uv(0, 0).cuboid(5F, -3.4F, -10.4F, 2.6F, 1F, 2.2F)
				.uv(0, 0).cuboid(-0.5F, -8.45F, -7F, 1F, 0.45F, 12F)
				.uv(0, 0).cuboid(-5.45F, -3.5F, -2F, 0.45F, 1F, 7.4F)
				.uv(0, 0).cuboid(5F, -3.5F, -2F, 0.45F, 1F, 7.4F)
				.uv(0, 0).cuboid(-5.45F, -3.5F, 5F, 10.9F, 1F, 0.45F)
				.uv(0, 0).cuboid(-5.45F, -8.5F, 0.5F, 10.9F, 0.45F, 1F)
				.uv(0, 0).cuboid(-5.45F, -8.5F, 0.5F, 0.45F, 5F, 1F)
				.uv(0, 0).cuboid(5F, -8.5F, 0.5F, 0.45F, 5F, 1F), ModelTransform.NONE);
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
				.uv(0, 0).cuboid(-3F, 1F, -3F, 6F, 2.5F, 1F)
				.uv(0, 0).cuboid(-3F, 1F, 5F, 6F, 2.5F, 1F)
				.uv(0, 0).cuboid(-3F, 1F, -2F, 0.95F, 2.5F, 7F)
				.uv(0, 0).cuboid(1.95F, 1F, -2F, 1.05F, 2.5F, 7F)
				.uv(0, 32).cuboid(-3.4F, -1.5F, -3.4F, 0.5F, 5F, 0.6F)
				.uv(0, 32).cuboid(2.9F, -1.5F, -3.4F, 0.5F, 5F, 0.6F)
				.uv(0, 32).cuboid(-3.5F, -2.3F, -3.5F, 0.7F, 0.8F, 0.8F)
				.uv(0, 32).cuboid(2.8F, -2.3F, -3.5F, 0.7F, 0.8F, 0.8F)
				.uv(0, 0).cuboid(-3.6F, -10.6F, -1.8F, 0.5F, 3.2F, 3.2F)
				.uv(0, 0).cuboid(3.1F, -10.6F, -1.8F, 0.5F, 3.2F, 3.2F)
				.uv(0, 0).cuboid(-3.2F, -11.2F, -2.3F, 6.4F, 0.8F, 0.5F)
				.uv(0, 32).cuboid(-3.4F, -11.3F, -2.4F, 0.6F, 0.9F, 0.6F)
				.uv(0, 32).cuboid(2.8F, -11.3F, -2.4F, 0.6F, 0.9F, 0.6F)
				.uv(0, 0).cuboid(-2.3F, -11.3F, -5.4F, 4.6F, 0.3F, 0.8F)
				.uv(0, 0).cuboid(-2.3F, -6.3F, -5.4F, 4.6F, 0.3F, 0.8F)
				.uv(0, 0).cuboid(-2.3F, -11F, -5.4F, 0.3F, 4.7F, 0.8F)
				.uv(0, 0).cuboid(2F, -11F, -5.4F, 0.3F, 4.7F, 0.8F)
				.uv(0, 32).cuboid(-2.5F, -8F, -6.5F, 0.4F, 1.2F, 1.2F)
				.uv(0, 32).cuboid(2.1F, -8F, -6.5F, 0.4F, 1.2F, 1.2F)
				.uv(0, 32).cuboid(-0.6F, -12.6F, 2.4F, 1.2F, 1.6F, 1.2F), ModelTransform.NONE);
		// The plume's three feathers, fanned from their holder on the poll.
		head.addChild("plume", ModelPartBuilder.create().uv(0, 48).cuboid(-0.8F, -9.5F, -1.0F, 1.6F, 9.0F, 2.0F),
				ModelTransform.of(0.0F, -12.6F, 3.0F, -0.2F, 0.0F, 0.0F));
		head.addChild("plume_left", ModelPartBuilder.create().uv(0, 48).cuboid(-0.6F, -7.5F, -0.8F, 1.2F, 7.0F, 1.6F),
				ModelTransform.of(0.0F, -12.6F, 3.0F, -0.1F, 0.0F, 0.4F));
		head.addChild("plume_right", ModelPartBuilder.create().uv(0, 48).cuboid(-0.6F, -7.5F, -0.8F, 1.2F, 7.0F, 1.6F),
				ModelTransform.of(0.0F, -12.6F, 3.0F, -0.1F, 0.0F, -0.4F));
		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, T horse, float limbAngle, float limbDistance,
			float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (CarriageEntity.hitchedTo(horse) == null || horse.isInvisible()) {
			return;
		}
		HorseModelAccess model = (HorseModelAccess) getContextModel();
		body.copyTransform(model.chitty$body());
		head.copyTransform(model.chitty$head());
		VertexConsumer out = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		int overlay = LivingEntityRenderer.getOverlay(horse, 0.0F);
		body.render(matrices, out, light, overlay);
		head.render(matrices, out, light, overlay);
	}
}
