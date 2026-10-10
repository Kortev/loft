package io.github.kortev.chitty.client;

import io.github.kortev.chitty.carriage.CarriageEntity;
import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * The carriage's horse, drawn as the game draws its own: the vanilla horse model (EntityModelLayers.HORSE) in the black
 * coat, its legs, head and tail posed as HorseEntityModel poses a horse walking, trotting and galloping, 1.1 times its
 * model's size; and on it her harness, boxes in the same body and head frames (tools/carriage_model.py's HARNESS, which
 * its renders draw): a collar with brass hames, blinkers, a browband and noseband, the bit's rings, the black plume on
 * its poll; a pad with brass terrets and a girth, the tugs that carry the shafts, the crupper and the breeching. The
 * harness's texture is in bands, one colour each: leather, brass and plume.
 */
public final class CarriageHorse {
	public static final EntityModelLayer HARNESS = new EntityModelLayer(ShootingStar.id("carriage_harness"), "main");
	private static final Identifier COAT = Identifier.ofVanilla("textures/entity/horse/horse_black.png");
	private static final Identifier HARNESS_TEXTURE = ShootingStar.id("textures/entity/carriage_harness.png");
	/** How big the game draws a horse (HorseEntityRenderer). */
	public static final float SCALE = 1.1F;
	private static final String[] HIDDEN = {"right_hind_baby_leg", "left_hind_baby_leg", "right_front_baby_leg", "left_front_baby_leg"};
	private static final String[] TACK = {"left_saddle_mouth", "right_saddle_mouth", "left_saddle_line", "right_saddle_line", "head_saddle",
			"mouth_saddle_wrap"};

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart tail;
	private final ModelPart leftHindLeg;
	private final ModelPart rightHindLeg;
	private final ModelPart leftFrontLeg;
	private final ModelPart rightFrontLeg;
	private final ModelPart harnessBody;
	private final ModelPart harnessHead;

	public CarriageHorse(EntityRendererFactory.Context context) {
		root = context.getPart(EntityModelLayers.HORSE);
		body = root.getChild("body");
		head = root.getChild("head_parts");
		tail = body.getChild("tail");
		leftHindLeg = root.getChild("left_hind_leg");
		rightHindLeg = root.getChild("right_hind_leg");
		leftFrontLeg = root.getChild("left_front_leg");
		rightFrontLeg = root.getChild("right_front_leg");
		for (String name : HIDDEN) {
			root.getChild(name).visible = false;
		}
		body.getChild("saddle").visible = false;
		for (String name : TACK) {
			head.getChild(name).visible = false;
		}
		ModelPart harness = context.getPart(HARNESS);
		harnessBody = harness.getChild("body");
		harnessHead = harness.getChild("head");
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

	/**
	 * Poses it as HorseEntityModel.animateModel poses a grown horse, not rearing, eating or looking about: its legs at
	 * limbPos of their stride by limbSpeed (0 standing, 1 a gallop), its head nodding to its stride once it trots, its
	 * tail lifting as it goes.
	 */
	private void pose(float limbPos, float limbSpeed) {
		float nod = limbSpeed > 0.2F ? MathHelper.cos(limbPos * 0.8F) * 0.15F * limbSpeed : 0.0F;
		body.pivotY = 11.0F;
		body.pitch = 0.0F;
		head.pivotY = 4.0F;
		head.pivotZ = -12.0F;
		head.pitch = (float) (Math.PI / 6) + nod;
		head.yaw = 0.0F;
		float t = MathHelper.cos(limbPos * 0.6662F + (float) Math.PI);
		float u = t * 0.8F * limbSpeed;
		leftFrontLeg.pivotY = 14.0F;
		leftFrontLeg.pivotZ = -10.0F;
		rightFrontLeg.pivotY = 14.0F;
		rightFrontLeg.pivotZ = -10.0F;
		leftHindLeg.pitch = -t * 0.5F * limbSpeed;
		rightHindLeg.pitch = t * 0.5F * limbSpeed;
		leftFrontLeg.pitch = u;
		rightFrontLeg.pitch = -u;
		tail.pitch = (float) (Math.PI / 6) + limbSpeed * 0.75F;
		tail.pivotY = -5.0F + limbSpeed;
		tail.pivotZ = 2.0F + limbSpeed * 2.0F;
		harnessBody.copyTransform(body);
		harnessHead.copyTransform(head);
	}

	/**
	 * Draws it in her frame (yaw 0, x to her left, z forward) where it stands, turned with the shafts by steerDegrees
	 * (left positive), as LivingEntityRenderer draws a horse: turned about, flipped, scaled and lifted by its model's
	 * 1.501.
	 */
	public void render(CarriageEntity carriage, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light,
			int overlay) {
		float steer = carriage.getSteer(tickDelta);
		var at = carriage.horseLocal(steer, carriage.getHorseLift(tickDelta));
		pose(carriage.getLimbPos(tickDelta), carriage.getLimbSpeed(tickDelta));
		matrices.push();
		matrices.translate(at.x, at.y, at.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F + steer * CarriageEntity.STEER_MAX));
		matrices.scale(-1.0F, -1.0F, 1.0F);
		matrices.scale(SCALE, SCALE, SCALE);
		matrices.translate(0.0F, -1.501F, 0.0F);
		root.render(matrices, buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(COAT)), light, overlay);
		var harness = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(HARNESS_TEXTURE));
		harnessBody.render(matrices, harness, light, overlay);
		harnessHead.render(matrices, harness, light, overlay);
		matrices.pop();
	}
}
