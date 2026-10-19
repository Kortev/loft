package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.FormTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The form on whoever is in it: the chainsaw head in place of their own (hidden, with their hat layer, by {@code
 * HeadHiddenMixin}), the saws through their forearms, and the cord on their chest; each part moving with its bone. The
 * chains run round their bars all the while, and race when they swing.
 *
 * <p>Through the transformation ({@link FormTimeline}): the cord stretches out of the chest to the yanking hand and
 * snaps back; at the burst the head is suddenly there in the blood (swelling the last little way into place) and its
 * saw grows out of it to its full length; then the arms' saws push out through the fists and grow, the fists splitting
 * open as they come.
 */
public final class FormFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	public static final Identifier TEXTURE = ChainsawMan.id("textures/entity/form.png");
	/** How many frames each chain is baked in, and how fast they go by (frames a tick) idling and swinging. */
	private static final int CHAIN_FRAMES = 4;
	private static final float CHAIN_IDLE = 0.5F;
	private static final float CHAIN_SWING = 2.0F;
	/** The head swelling into place at the burst: from how small, over how many ticks, round which point (pixels). */
	private static final float HEAD_FROM = 0.72F;
	private static final float HEAD_SWELL = 3.0F;
	private static final Vector3f HEAD_MIDDLE = new Vector3f(0.0F, -4.5F, -1.0F);
	/** The cord's root on the chest, in the body's space (pixels); the hand's grip, in the right arm's; its thickness. */
	private static final Vector3f CORD_ROOT = new Vector3f(0.0F, 3.5F, -2.15F);
	private static final Vector3f GRIP = new Vector3f(-1.0F, 10.5F, 0.0F);
	private static final float CORD_HALF = 0.04F;

	public FormFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
		float t = ClientForms.pullAge(player, tickDelta);
		if (t >= FormTimeline.REACH && t < FormTimeline.SNAP + 2) {
			cord(matrices, consumers, light, model, t);
		}
		if (!ClientForms.on(player)) {
			return;
		}
		int overlay = LivingEntityRenderer.getOverlay(player, 0.0F);
		draw(matrices, consumers, light, overlay, player, tickDelta, "head", model.head);
		draw(matrices, consumers, light, overlay, player, tickDelta, "body", model.body);
		draw(matrices, consumers, light, overlay, player, tickDelta, "right_arm", model.rightArm);
		draw(matrices, consumers, light, overlay, player, tickDelta, "left_arm", model.leftArm);
	}

	/**
	 * Draws the parts of the form that follow one bone of the player model, the stack moved to the bone first: the chains
	 * on the frame they have run to, and through the transformation the saws only as far as they have grown.
	 */
	public static void draw(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, AbstractClientPlayerEntity player,
			float tickDelta, String boneName, ModelPart bone) {
		FormMesh mesh = FormMesh.get();
		if (mesh == null) {
			return;
		}
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		float time = player.age + tickDelta;
		float speed = player.handSwinging ? CHAIN_SWING : CHAIN_IDLE;
		int chainFrame = Math.floorMod((int) (time * speed), CHAIN_FRAMES);
		boolean arm = boneName.endsWith("_arm");
		float grown = ClientForms.grown(player, tickDelta, arm);
		float t = ClientForms.pullAge(player, tickDelta);
		matrices.push();
		bone.rotate(matrices);
		for (FormMesh.Part part : mesh.parts.values()) {
			if (!part.bone.equals(boneName) || part.name.equals("rope") || (part.chainFrame >= 0 && part.chainFrame != chainFrame)) {
				continue;
			}
			if (part.growRoot != null) {
				if (grown <= 0.0F) {
					continue;
				}
				matrices.push();
				along(matrices, part.growRoot, part.growAxis, grown);
				part.draw(matrices.peek(), out, light, overlay);
				matrices.pop();
				continue;
			}
			if (arm && grown <= 0.0F) {
				// The split fists open as the saws come through, not before.
				continue;
			}
			if (part.name.equals("head") && t >= 0.0F && t < FormTimeline.BURST + HEAD_SWELL) {
				float k = MathHelper.clamp((t - FormTimeline.BURST) / HEAD_SWELL, 0.0F, 1.0F);
				float s = MathHelper.lerp(1.0F - (1.0F - k) * (1.0F - k), HEAD_FROM, 1.0F);
				matrices.push();
				matrices.translate(HEAD_MIDDLE.x / 16.0F, HEAD_MIDDLE.y / 16.0F, HEAD_MIDDLE.z / 16.0F);
				matrices.scale(s, s, s);
				matrices.translate(-HEAD_MIDDLE.x / 16.0F, -HEAD_MIDDLE.y / 16.0F, -HEAD_MIDDLE.z / 16.0F);
				part.draw(matrices.peek(), out, light, overlay);
				matrices.pop();
				continue;
			}
			part.draw(matrices.peek(), out, light, overlay);
		}
		matrices.pop();
	}

	/** Squashes everything along {@code axis} toward {@code root} (pixels) to {@code k} of its length: a saw part grown that far. */
	private static void along(MatrixStack matrices, Vector3f root, Vector3f axis, float k) {
		float x = root.x / 16.0F;
		float y = root.y / 16.0F;
		float z = root.z / 16.0F;
		matrices.translate(x, y, z);
		float c = k - 1.0F;
		Matrix4f m = new Matrix4f(
				1.0F + c * axis.x * axis.x, c * axis.y * axis.x, c * axis.z * axis.x, 0.0F,
				c * axis.x * axis.y, 1.0F + c * axis.y * axis.y, c * axis.z * axis.y, 0.0F,
				c * axis.x * axis.z, c * axis.y * axis.z, 1.0F + c * axis.z * axis.z, 0.0F,
				0.0F, 0.0F, 0.0F, 1.0F);
		matrices.multiplyPositionMatrix(m);
		matrices.translate(-x, -y, -z);
	}

	/**
	 * The cord, stretched from its root on the chest to the hand yanking it, with the handle across the grip; after the
	 * yank it snaps back in, shrinking to the chest.
	 */
	private static void cord(MatrixStack matrices, VertexConsumerProvider consumers, int light, PlayerEntityModel<?> model, float t) {
		FormMesh mesh = FormMesh.get();
		if (mesh == null) {
			return;
		}
		Vector3f root = at(matrices, model.body, CORD_ROOT);
		Vector3f hand = at(matrices, model.rightArm, GRIP);
		if (t >= FormTimeline.SNAP) {
			float back = MathHelper.clamp((t - FormTimeline.SNAP) / 2.0F, 0.0F, 1.0F);
			hand.lerp(root, back);
		}
		float[] uv = mesh.ropeUv();
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		Vector3f d = new Vector3f(hand).sub(root);
		// A flat strip turned to face the camera, and the handle across its end. (The stack's space is the world's, from
		// the camera, not turned with it.)
		Vector3f look = MinecraftClient.getInstance().gameRenderer.getCamera().getHorizontalPlane();
		Vector3f side = new Vector3f(d).cross(look);
		if (side.lengthSquared() < 1.0E-8F) {
			return;
		}
		side.normalize(CORD_HALF);
		quad(out, light, uv, new Vector3f(root).sub(side), new Vector3f(root).add(side), new Vector3f(hand).add(side), new Vector3f(hand).sub(side));
		Vector3f across = new Vector3f(side).normalize(0.14F);
		Vector3f thick = new Vector3f(d).normalize(0.05F);
		quad(out, light, uv, new Vector3f(hand).sub(across).sub(thick), new Vector3f(hand).add(across).sub(thick),
				new Vector3f(hand).add(across).add(thick), new Vector3f(hand).sub(across).add(thick));
	}

	/** Where {@code point} (pixels in {@code bone}'s space) is, in the stack's space. */
	private static Vector3f at(MatrixStack matrices, ModelPart bone, Vector3f point) {
		matrices.push();
		bone.rotate(matrices);
		Vector3f p = matrices.peek().getPositionMatrix().transformPosition(point.x / 16.0F, point.y / 16.0F, point.z / 16.0F, new Vector3f());
		matrices.pop();
		return p;
	}

	private static void quad(VertexConsumer out, int light, float[] uv, Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
		for (Vector3f p : new Vector3f[] {a, b, c, d}) {
			out.vertex(p.x, p.y, p.z, 0xFFFFFFFF, uv[0], uv[1], net.minecraft.client.render.OverlayTexture.DEFAULT_UV, light, 0.0F, 0.0F, 1.0F);
		}
	}
}
