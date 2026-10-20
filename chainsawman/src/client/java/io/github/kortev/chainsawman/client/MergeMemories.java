package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.MergeTimeline;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * What Pochita's "I always loved hearing you talk about your dreams" calls up, in the merge's dream: your life with him,
 * four little scenes built in the game, floating in the black, tinted like an old photograph ({@link #sepia}), the camera
 * cutting from one to the next as he talks. You in the shack holding him; you with bread, him looking up at it; asleep,
 * him curled up beside you; a devil job, him the chainsaw in your hands, cutting into a zombie. You are your own skin,
 * he his own model, the blocks the game's.
 *
 * <p>Each scene is set far up over where you lie (so nothing of the world is near it, and the dream's fog hides the
 * rest), its front toward +z: the camera looks at it from there.
 */
public final class MergeMemories {
	private static final Identifier POCHITA = ChainsawMan.id("textures/entity/pochita.png");
	/** How many scenes, how far apart (blocks), and how high over you. */
	private static final int SCENES = 4;
	private static final double APART = 40.0;
	private static final double UP = 90.0;
	/** The light they're lit by: warm lamplight, no sky. */
	private static final int LIGHT = LightmapTextureManager.pack(15, 0);
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.0F);

	private static ZombieEntity devil;

	private MergeMemories() {
	}

	/** Whether {@code t} is in the memories. */
	public static boolean on(float t) {
		return t >= MergeTimeline.MONTAGE && t < MergeTimeline.MONTAGE_END;
	}

	/** Which scene is up at {@code t}, and how far into it (0 to 1). */
	private static int scene(float t) {
		return MathHelper.clamp((int) ((t - MergeTimeline.MONTAGE) / length()), 0, SCENES - 1);
	}

	private static float into(float t) {
		return MathHelper.clamp((t - MergeTimeline.MONTAGE - scene(t) * length()) / length(), 0.0F, 1.0F);
	}

	private static float length() {
		return (MergeTimeline.MONTAGE_END - MergeTimeline.MONTAGE) / (float) SCENES;
	}

	/** How far the picture is drained into an old photograph's browns (for the screen's shader). */
	public static float sepia(float t) {
		if (!on(t)) {
			return 0.0F;
		}
		float in = MathHelper.clamp((t - MergeTimeline.MONTAGE) / 3.0F, 0.0F, 1.0F);
		float out = MathHelper.clamp((MergeTimeline.MONTAGE_END - t) / 3.0F, 0.0F, 1.0F);
		return 0.8F * Math.min(in, out);
	}

	/** Where scene {@code i} is, for someone lying at {@code feet}. */
	private static Vec3d origin(Vec3d feet, int i) {
		return new Vec3d(Math.floor(feet.x) + 0.5 + (i - 1.5) * APART, Math.floor(feet.y) + UP, Math.floor(feet.z) + 0.5);
	}

	/** The camera on the scene up at {@code t}: from in front and a little to one side, slowly closing in. */
	public static FormCinematic.Shot shot(Vec3d feet, float t) {
		int i = scene(t);
		Vec3d o = origin(feet, i);
		double k = into(t);
		Vec3d[] from = {new Vec3d(1.0, 1.05, 1.9), new Vec3d(-1.2, 1.0, 1.8), new Vec3d(0.6, 1.9, 1.5), new Vec3d(2.3, 1.3, -0.5)};
		Vec3d[] at = {new Vec3d(0.0, 0.45, 0.1), new Vec3d(0.4, 0.7, 0.1), new Vec3d(-0.4, 0.2, 0.5), new Vec3d(0.0, 1.0, -0.6)};
		Vec3d eye = o.add(at[i]).add(from[i].subtract(at[i]).multiply(1.0 - 0.15 * k));
		Vec3d to = o.add(at[i]).subtract(eye);
		float yaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * MathHelper.DEGREES_PER_RADIAN);
		return new FormCinematic.Shot(eye.x, eye.y, eye.z, yaw, pitch);
	}

	/** Draws the scene up now (for your own merge), as the entities are drawn. */
	static void render(AbstractClientPlayerEntity self, Vec3d feet, float t, MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam,
			ClientWorld world) {
		int i = scene(t);
		Vec3d o = origin(feet, i);
		matrices.push();
		matrices.translate(o.x - cam.x, o.y - cam.y, o.z - cam.z);
		switch (i) {
			case 0 -> {
				shack(matrices, consumers);
				you(self, matrices, consumers, 0.0, 0.0, 0.0, 0.0F, Pose.SITTING_HOLDING);
				// In your lap, held to you.
				pochita(matrices, consumers, 0.0, 0.12, 0.45, 0.0F, 0.1F, t, 0.8F);
			}
			case 1 -> {
				shack(matrices, consumers);
				you(self, matrices, consumers, 0.0, 0.0, 0.0, -15.0F, Pose.SITTING_EATING);
				bread(matrices, consumers, -0.33, 1.1, 0.35);
				pochita(matrices, consumers, 0.95, 0.0, 0.55, -125.0F, -0.35F, t, 1.0F);
			}
			case 2 -> {
				shack(matrices, consumers);
				for (int x = -1; x <= 1; x++) {
					block(matrices, consumers, Blocks.WHITE_CARPET.getDefaultState(), x, 0, 0);
				}
				you(self, matrices, consumers, -0.9, 0.08, 0.0, 90.0F, Pose.ASLEEP);
				pochita(matrices, consumers, -0.6, 0.06, 1.05, -100.0F, 0.0F, t, 0.75F);
			}
			default -> {
				for (int x = -3; x <= 3; x++) {
					for (int z = -4; z <= 2; z++) {
						block(matrices, consumers, Blocks.COARSE_DIRT.getDefaultState(), x, -1, z);
					}
				}
				zombie(world, matrices, consumers, 0.0, 0.0, -2.0);
				you(self, matrices, consumers, 0.0, 0.0, 0.8, 180.0F, Pose.SAWING);
				pochita(matrices, consumers, 0.0, 0.85, 0.05, 180.0F, 0.0F, t, 1.0F);
				if (world.getTime() % 2 == 0) {
					Vec3d hit = o.add(0.0, 1.2, -1.6);
					world.addParticle(BLOOD, hit.x, hit.y, hit.z, 0.0, 0.0, 0.0);
				}
			}
		}
		matrices.pop();
	}

	/** The shack: a spruce floor, two walls, a window with the night in it, a lantern hanging. */
	private static void shack(MatrixStack matrices, VertexConsumerProvider consumers) {
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 2; z++) {
				block(matrices, consumers, Blocks.SPRUCE_PLANKS.getDefaultState(), x, -1, z);
			}
		}
		for (int y = 0; y <= 3; y++) {
			for (int x = -3; x <= 3; x++) {
				block(matrices, consumers, x == 1 && (y == 1 || y == 2) ? Blocks.GLASS_PANE.getDefaultState() : Blocks.SPRUCE_PLANKS.getDefaultState(), x, y,
						-3);
			}
			for (int z = -2; z <= 2; z++) {
				block(matrices, consumers, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState(), -3, y, z);
			}
		}
		block(matrices, consumers, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), 1, 3, -1);
	}

	private static void block(MatrixStack matrices, VertexConsumerProvider consumers, BlockState state, int x, int y, int z) {
		matrices.push();
		matrices.translate(x - 0.5, y, z - 0.5);
		MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, matrices, consumers, LIGHT, OverlayTexture.DEFAULT_UV);
		matrices.pop();
	}

	private static void bread(MatrixStack matrices, VertexConsumerProvider consumers, double x, double y, double z) {
		matrices.push();
		matrices.translate(x, y, z);
		matrices.scale(0.45F, 0.45F, 0.45F);
		MinecraftClient.getInstance().getItemRenderer().renderItem(new ItemStack(Items.BREAD), ModelTransformationMode.FIXED, LIGHT,
				OverlayTexture.DEFAULT_UV, matrices, consumers, MinecraftClient.getInstance().world, 0);
		matrices.pop();
	}

	/** Pochita at ({@code x}, {@code y}, {@code z}), turned {@code yaw}, nose {@code pitch} down, {@code size} of himself. */
	private static void pochita(MatrixStack matrices, VertexConsumerProvider consumers, double x, double y, double z, float yaw, float pitch, float t,
			float size) {
		PochitaModel model = MergeCinematic.pochitaModel();
		if (model == null) {
			return;
		}
		matrices.push();
		matrices.translate(x, y, z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
		matrices.translate(0.0, 0.3, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
		matrices.translate(0.0, -0.3, 0.0);
		matrices.scale(-size, -size, size);
		matrices.translate(0.0, -1.501, 0.0);
		model.animate(0.0F, 0.0F, t, false);
		model.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(POCHITA)), LIGHT, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
		matrices.pop();
	}

	private static void zombie(ClientWorld world, MatrixStack matrices, VertexConsumerProvider consumers, double x, double y, double z) {
		if (devil == null || devil.getWorld() != world) {
			devil = EntityType.ZOMBIE.create(world);
		}
		if (devil == null) {
			return;
		}
		devil.setYaw(0.0F);
		devil.setBodyYaw(0.0F);
		devil.setHeadYaw(0.0F);
		devil.prevBodyYaw = 0.0F;
		devil.prevHeadYaw = 0.0F;
		MinecraftClient.getInstance().getEntityRenderDispatcher().render(devil, x, y, z, 0.0F, 1.0F, matrices, consumers, LIGHT);
	}

	/** How you're posed in a memory. */
	private enum Pose { SITTING_HOLDING, SITTING_EATING, ASLEEP, SAWING }

	/** You, in your own skin, at ({@code x}, {@code y}, {@code z}), turned {@code yaw}, held as {@code pose}. */
	private static void you(AbstractClientPlayerEntity self, MatrixStack matrices, VertexConsumerProvider consumers, double x, double y, double z,
			float yaw, Pose pose) {
		if (!(MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(self) instanceof PlayerEntityRenderer renderer)) {
			return;
		}
		PlayerEntityModel<AbstractClientPlayerEntity> model = renderer.getModel();
		ModelPart[] parts = {model.head, model.hat, model.body, model.jacket, model.rightArm, model.leftArm, model.rightSleeve, model.leftSleeve,
				model.rightLeg, model.leftLeg, model.rightPants, model.leftPants};
		for (ModelPart part : parts) {
			part.resetTransform();
			part.visible = true;
		}
		matrices.push();
		matrices.translate(x, y, z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
		switch (pose) {
			case SITTING_HOLDING, SITTING_EATING -> {
				// Sat on the floor, legs out in front.
				matrices.translate(0.0, -0.62, 0.0);
				model.rightLeg.pitch = -1.45F;
				model.leftLeg.pitch = -1.45F;
				model.rightLeg.yaw = 0.15F;
				model.leftLeg.yaw = -0.15F;
				if (pose == Pose.SITTING_HOLDING) {
					model.rightArm.pitch = -0.9F;
					model.leftArm.pitch = -0.9F;
					model.rightArm.yaw = -0.45F;
					model.leftArm.yaw = 0.45F;
					model.head.pitch = 0.35F;
				} else {
					model.rightArm.pitch = -2.1F;
					model.rightArm.yaw = -0.3F;
					model.leftArm.pitch = -0.2F;
					model.head.pitch = -0.1F;
				}
			}
			case ASLEEP -> {
				// On your back, head turned toward him.
				matrices.translate(0.0, 0.15, 0.0);
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
				model.head.yaw = 0.5F;
				model.rightArm.roll = 0.25F;
				model.leftArm.roll = -0.15F;
			}
			case SAWING -> {
				// Him held out in front in both hands, leaning into it.
				model.rightArm.pitch = -1.35F;
				model.leftArm.pitch = -1.35F;
				model.rightArm.yaw = -0.25F;
				model.leftArm.yaw = 0.25F;
				model.rightLeg.pitch = 0.35F;
				model.leftLeg.pitch = -0.35F;
				model.body.pitch = 0.12F;
			}
		}
		model.hat.copyTransform(model.head);
		model.jacket.copyTransform(model.body);
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftSleeve.copyTransform(model.leftArm);
		model.rightPants.copyTransform(model.rightLeg);
		model.leftPants.copyTransform(model.leftLeg);
		matrices.scale(-0.9375F, -0.9375F, 0.9375F);
		matrices.translate(0.0, -1.501, 0.0);
		model.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(self.getSkinTextures().texture())), LIGHT,
				OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
		matrices.pop();
	}
}
