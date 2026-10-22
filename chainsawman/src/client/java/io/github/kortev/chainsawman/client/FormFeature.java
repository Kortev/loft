package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.ChainsawMan;
import io.github.kortev.chainsawman.form.Downed;
import io.github.kortev.chainsawman.form.Limbs;
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
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The form on whoever is in it: the chainsaw head in place of their own (hidden, with their hat layer, by {@code
 * HeadHiddenMixin}), the saws through their forearms, and the cord on their chest; each part moving with its bone. The
 * chains run round their bars all the while, and race when they swing.
 *
 * <p>Through the transformation ({@link FormTimeline}): the cord stretches out of the chest to the yanking hand and
 * snaps back; the saw's bar punches out through the forehead of their own head, thinner than it will be; at the burst
 * their head splits down the middle, the halves peeling open, and the chainsaw head is there in the blood (swelling the
 * last little way into place) with the saw growing on out of it to its full length and thickness; then the arms' saws
 * push out through the fists and grow, the fists splitting open as they come.
 */
public final class FormFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	public static final Identifier TEXTURE = ChainsawMan.id("textures/entity/form.png");
	/** The inside of their own head, on the cut faces of its halves as it splits open. */
	private static final Identifier FLESH = ChainsawMan.id("textures/entity/form_flesh.png");
	/** The flare of the eyes catching light at the roar, drawn added onto the picture. */
	private static final Identifier FLARE = ChainsawMan.id("textures/entity/form_flare.png");
	/** The flare: how big at its brightest (blocks across), and how fast it dies away (ticks). */
	private static final float FLARE_SIZE = 0.7F;
	private static final float FLARE_FADE = 6.0F;
	/** How fast the chains' frames go by (frames a tick) idling and swinging. */
	private static final float CHAIN_IDLE = 0.5F;
	private static final float CHAIN_SWING = 2.0F;
	/** The head swelling into place at the burst: from how small, over how many ticks, round which point (pixels). */
	private static final float HEAD_FROM = 0.72F;
	private static final float HEAD_SWELL = 3.0F;
	private static final Vector3f HEAD_MIDDLE = new Vector3f(0.0F, -4.5F, -1.0F);
	/**
	 * The bar punching out of their own head before the burst: how far along it (pixels) the face of a player's head is,
	 * how far out of the face the bar has come by the burst, how thick across it is until the chainsaw head swells, and
	 * how far lower than on the chainsaw head it comes out (the middle of the forehead, so none of it shows over the top).
	 */
	private static final float FACE = 4.0F;
	private static final float PUNCH_OUT = 6.0F;
	private static final float PUNCH_THICK = 0.5F;
	private static final float PUNCH_DROP = 1.4F;
	/** Their head split open: how far each half swings out on its hinge (degrees), and drifts out (pixels). */
	private static final float SPLIT_ANGLE = 75.0F;
	private static final float SPLIT_DRIFT = 1.5F;
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
		int overlay = LivingEntityRenderer.getOverlay(player, 0.0F);
		if (Downed.is(player)) {
			// Down: no saws, only where the arms and legs were.
			stumps(matrices, consumers, light, overlay, model.body, true, true, true);
			return;
		}
		if (t >= FormTimeline.REACH && t < FormTimeline.SNAP + 2) {
			cord(matrices, consumers, light, model, t);
		}
		float merge = MergeCinematic.age(player, tickDelta);
		if ((merge >= io.github.kortev.chainsawman.form.MergeTimeline.SUNK || ClientCombat.heart(player)) && !ClientForms.on(player)
				&& t < FormTimeline.REACH) {
			// He's in: the cord hangs out of your chest, from the merge on, until it's grabbed.
			draw(matrices, consumers, light, overlay, player, tickDelta, "body", model.body);
		}
		if (!ClientForms.on(player)) {
			// (Past the burst only while the server's word that the form is on is still on its way.)
			if (t >= FormTimeline.PUNCH && t < FormTimeline.HEAD_OPEN) {
				punch(matrices, consumers, light, overlay, player, tickDelta, model.head, t);
			}
			return;
		}
		draw(matrices, consumers, light, overlay, player, tickDelta, "head", model.head);
		draw(matrices, consumers, light, overlay, player, tickDelta, "body", model.body);
		if (Limbs.has(player, Limbs.RIGHT)) {
			draw(matrices, consumers, light, overlay, player, tickDelta, "right_arm", model.rightArm);
		}
		if (Limbs.has(player, Limbs.LEFT)) {
			draw(matrices, consumers, light, overlay, player, tickDelta, "left_arm", model.leftArm);
		}
		// An arm lost: its stub's torn end, bleeding.
		if (!Limbs.has(player, Limbs.RIGHT)) {
			Stumps.end(matrices, consumers, light, overlay, player, model.rightArm, true);
		}
		if (!Limbs.has(player, Limbs.LEFT)) {
			Stumps.end(matrices, consumers, light, overlay, player, model.leftArm, false);
		}
		if (t >= FormTimeline.BURST && t < FormTimeline.HEAD_OPEN) {
			split(matrices, consumers, light, overlay, player, model.head, t);
		}
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
		int chainFrame = chainFrame(mesh, player, tickDelta);
		boolean arm = boneName.endsWith("_arm");
		float grown = ClientForms.grown(player, tickDelta, arm);
		float t = ClientForms.pullAge(player, tickDelta);
		// The head's saw grows on from as far as it had punched out of their own head, and swells to its thickness with
		// the head.
		float length = arm ? grown : MathHelper.lerp(grown, (FACE + PUNCH_OUT) / headReach(mesh), 1.0F);
		float thick = arm ? 1.0F : MathHelper.lerp(swell(t), PUNCH_THICK, 1.0F);
		// The eyes dark through the transformation, until they catch at the roar.
		float eye = t >= 0.0F && t < FormTimeline.ROAR ? 0.0F : 1.0F;
		// Drenched: the more blood on it, the darker and redder.
		float soaked = ClientCombat.bloody(player);
		int tint = 0xFF000000 | 255 << 16 | (int) (255 * (1.0F - 0.55F * soaked)) << 8 | (int) (255 * (1.0F - 0.6F * soaked));
		java.util.List<Vector3f> eyes = new java.util.ArrayList<>();
		boolean flaring = t >= FormTimeline.ROAR && t < FormTimeline.ROAR + FLARE_FADE * 4 || ClientCombat.frenzy(player);
		boolean boxHead = false;
		matrices.push();
		bone.rotate(matrices);
		for (FormMesh.Part part : mesh.parts.values()) {
			if (!part.bone.equals(boneName) || part.name.equals("rope") || (part.chainFrame >= 0 && part.chainFrame != chainFrame)) {
				continue;
			}
			if (part.name.equals("head") && FormHeadModel.on) {
				// The boxes instead (drawn after the mesh's parts: another texture).
				boxHead = true;
				if (flaring) {
					eyes.addAll(FormHeadModel.EYES);
				}
				continue;
			}
			if (flaring) {
				eyes.addAll(part.glowCentres());
			}
			if (part.growRoot != null) {
				if (length <= 0.0F) {
					continue;
				}
				if (part.name.endsWith("_bar")) {
					// (The head's lifted on the box head.)
					Vector3f root = !arm && FormHeadModel.on ? new Vector3f(part.growRoot).add(0.0F, FormHeadModel.BAR_LIFT, 0.0F) : part.growRoot;
					SawTrails.sample(player, boneName, tickDelta, matrices.peek(), root, part.growAxis, part.reach() * length);
					if (boneName.equals("right_arm") && !SawTrails.firstPerson) {
						// Where the chain comes out from.
						org.joml.Vector3f tip = matrices.peek().getPositionMatrix().transformPosition(
								new Vector3f(part.growAxis).mul(part.reach() * length).add(root).div(16.0F));
						ChainRender.tip(player, new net.minecraft.util.math.Vec3d(tip.x, tip.y, tip.z)
								.add(MinecraftClient.getInstance().gameRenderer.getCamera().getPos()), player.getWorld().getTime() + tickDelta);
					}
				}
				matrices.push();
				if (!arm && FormHeadModel.on) {
					matrices.translate(0.0F, FormHeadModel.BAR_LIFT / 16.0F, 0.0F);
				}
				saw(matrices, part.growRoot, part.growAxis, 0.0F, length, thick);
				part.draw(matrices.peek(), out, light, overlay);
				matrices.pop();
				continue;
			}
			if (arm && grown <= 0.0F) {
				// The split fists open as the saws come through, not before.
				continue;
			}
			if (part.name.equals("head") && t >= 0.0F && t < FormTimeline.BURST + HEAD_SWELL) {
				float s = MathHelper.lerp(swell(t), HEAD_FROM, 1.0F);
				matrices.push();
				matrices.translate(HEAD_MIDDLE.x / 16.0F, HEAD_MIDDLE.y / 16.0F, HEAD_MIDDLE.z / 16.0F);
				matrices.scale(s, s, s);
				matrices.translate(-HEAD_MIDDLE.x / 16.0F, -HEAD_MIDDLE.y / 16.0F, -HEAD_MIDDLE.z / 16.0F);
				part.draw(matrices.peek(), out, light, overlay, eye, tint);
				matrices.pop();
				continue;
			}
			part.draw(matrices.peek(), out, light, overlay, eye, tint);
		}
		// (After the parts: asking for another layer finishes the parts' one.)
		if (boxHead) {
			matrices.push();
			if (t >= 0.0F && t < FormTimeline.BURST + HEAD_SWELL) {
				float s = MathHelper.lerp(swell(t), HEAD_FROM, 1.0F);
				matrices.translate(HEAD_MIDDLE.x / 16.0F, HEAD_MIDDLE.y / 16.0F, HEAD_MIDDLE.z / 16.0F);
				matrices.scale(s, s, s);
				matrices.translate(-HEAD_MIDDLE.x / 16.0F, -HEAD_MIDDLE.y / 16.0F, -HEAD_MIDDLE.z / 16.0F);
			}
			net.minecraft.client.model.ModelPart head = FormHeadModel.get();
			head.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(FormHeadModel.TEXTURE)), light, overlay);
			if (eye > 0.0F) {
				// In a frenzy the vents themselves burn red, throbbing (the red flare, added onto their yellow, would
				// only stay yellow).
				int glow = -1;
				if (ClientCombat.frenzy(player)) {
					float age = player.age + tickDelta;
					float throb = 0.55F + 0.2F * MathHelper.sin(age * 0.9F) + 0.1F * MathHelper.sin(age * 2.3F);
					glow = 0xFF000000 | (int) (255 * Math.min(1.0F, throb + 0.35F)) << 16 | (int) (35 * throb) << 8 | (int) (25 * throb);
				}
				head.render(matrices, consumers.getBuffer(RenderLayer.getEyes(FormHeadModel.GLOW)),
						net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, glow);
			}
			matrices.pop();
		}
		if (!eyes.isEmpty()) {
			flare(matrices, consumers, eyes, t, ClientCombat.frenzy(player) ? player.age + tickDelta : -1.0F);
		}
		matrices.pop();
	}

	/**
	 * The eyes igniting at the roar: a pixel star of light flaring on each, added onto the picture, facing the camera,
	 * dying away. In a frenzy ({@code frenzy} the time, or -1) they burn red, throbbing.
	 */
	private static void flare(MatrixStack matrices, VertexConsumerProvider consumers, java.util.List<Vector3f> eyes, float t, float frenzy) {
		float age = t - FormTimeline.ROAR;
		float k = t < 0.0F ? 0.0F : Math.min(age / 1.5F, 1.0F) * (float) Math.exp(-Math.max(age - 1.5F, 0.0F) / FLARE_FADE);
		int colour;
		float half;
		if (frenzy >= 0.0F && k < 0.6F) {
			float throb = 0.55F + 0.2F * MathHelper.sin(frenzy * 0.9F) + 0.1F * MathHelper.sin(frenzy * 2.3F);
			half = FLARE_SIZE * 0.5F * (0.55F + 0.25F * throb);
			colour = 0xFF000000 | (int) (255 * throb) << 16 | (int) (40 * throb) << 8 | (int) (30 * throb);
		} else {
			half = FLARE_SIZE * 0.5F * (0.6F + 0.4F * k);
			int c = (int) (255 * k);
			colour = 0xFF000000 | c << 16 | c << 8 | c;
		}
		org.joml.Quaternionf facing = MinecraftClient.getInstance().gameRenderer.getCamera().getRotation();
		Vector3f right = facing.transform(new Vector3f(half, 0.0F, 0.0F));
		Vector3f up = facing.transform(new Vector3f(0.0F, half, 0.0F));
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEyes(FLARE));
		Matrix4f m = matrices.peek().getPositionMatrix();
		for (Vector3f centre : eyes) {
			Vector3f at = m.transformPosition(centre.x / 16.0F, centre.y / 16.0F, centre.z / 16.0F, new Vector3f());
			// Both ways round, so it faces the camera whichever way the layer culls.
			float[][] corners = {{-1, -1, 0, 1}, {-1, 1, 0, 0}, {1, 1, 1, 0}, {1, -1, 1, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0},
					{-1, -1, 0, 1}};
			for (float[] q : corners) {
				out.vertex(at.x + right.x * q[0] + up.x * q[1], at.y + right.y * q[0] + up.y * q[1], at.z + right.z * q[0] + up.z * q[1], colour,
						q[2], q[3], net.minecraft.client.render.OverlayTexture.DEFAULT_UV, net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE,
						0.0F, 1.0F, 0.0F);
			}
		}
	}

	/** Down, where the arms and legs were: raw stumps at the shoulders and hips (the body's own space, in pixels). */
	private static final float[][][] STUMPS = {
			{{-4.05F, 0.0F, -2.0F}, {-4.05F, 4.0F, -2.0F}, {-4.05F, 4.0F, 2.0F}, {-4.05F, 0.0F, 2.0F}, {-1.0F, 0.0F, 0.0F}},
			{{4.05F, 0.0F, 2.0F}, {4.05F, 4.0F, 2.0F}, {4.05F, 4.0F, -2.0F}, {4.05F, 0.0F, -2.0F}, {1.0F, 0.0F, 0.0F}},
			{{-4.0F, 12.05F, -2.0F}, {0.0F, 12.05F, -2.0F}, {0.0F, 12.05F, 2.0F}, {-4.0F, 12.05F, 2.0F}, {0.0F, 1.0F, 0.0F}},
			{{0.0F, 12.05F, -2.0F}, {4.0F, 12.05F, -2.0F}, {4.0F, 12.05F, 2.0F}, {0.0F, 12.05F, 2.0F}, {0.0F, 1.0F, 0.0F}}};

	private static void stumps(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, ModelPart body, boolean right,
			boolean left, boolean legs) {
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH));
		matrices.push();
		body.rotate(matrices);
		MatrixStack.Entry entry = matrices.peek();
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		float[][] uv = {{0.0F, 0.0F}, {0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}};
		for (int s = 0; s < STUMPS.length; s++) {
			float[][] stump = STUMPS[s];
			if (s == 0 && !right || s == 1 && !left || s >= 2 && !legs) {
				continue;
			}
			entry.transformNormal(stump[4][0], stump[4][1], stump[4][2], n);
			for (int i = 0; i < 4; i++) {
				entry.getPositionMatrix().transformPosition(stump[i][0] / 16.0F, stump[i][1] / 16.0F, stump[i][2] / 16.0F, p);
				out.vertex(p.x, p.y, p.z, 0xFFFFFFFF, uv[i][0], uv[i][1], overlay, light, n.x, n.y, n.z);
			}
		}
		matrices.pop();
	}

	/** Which frame the chains have run round to. */
	private static int chainFrame(FormMesh mesh, AbstractClientPlayerEntity player, float tickDelta) {
		float time = player.age + tickDelta;
		float speed = ClientCombat.busy(player) ? CHAIN_SWING : CHAIN_IDLE;
		return Math.floorMod((int) (time * speed), mesh.chainFrames);
	}

	/** How far the chainsaw head has swelled into place since the burst (0 to 1); 1 when not transforming. */
	private static float swell(float t) {
		if (t < 0.0F) {
			return 1.0F;
		}
		float k = MathHelper.clamp((t - FormTimeline.BURST) / HEAD_SWELL, 0.0F, 1.0F);
		return 1.0F - (1.0F - k) * (1.0F - k);
	}

	/** How far the head's bar runs out from its root (pixels). */
	private static float headReach(FormMesh mesh) {
		FormMesh.Part bar = mesh.parts.get("head_bar");
		float reach = bar != null ? bar.reach() : 0.0F;
		return reach > 0.0F ? reach : 30.0F;
	}

	/**
	 * Shapes a saw part: squashed along {@code axis} toward {@code root} (pixels) to {@code length} of its length and to
	 * {@code thick} of its thickness across, with its root moved {@code base} pixels out along the axis.
	 */
	private static void saw(MatrixStack matrices, Vector3f root, Vector3f axis, float base, float length, float thick) {
		matrices.translate((root.x + axis.x * base) / 16.0F, (root.y + axis.y * base) / 16.0F, (root.z + axis.z * base) / 16.0F);
		float c = length - thick;
		Matrix4f m = new Matrix4f(
				thick + c * axis.x * axis.x, c * axis.y * axis.x, c * axis.z * axis.x, 0.0F,
				c * axis.x * axis.y, thick + c * axis.y * axis.y, c * axis.z * axis.y, 0.0F,
				c * axis.x * axis.z, c * axis.y * axis.z, thick + c * axis.z * axis.z, 0.0F,
				0.0F, 0.0F, 0.0F, 1.0F);
		matrices.multiplyPositionMatrix(m);
		matrices.translate(-root.x / 16.0F, -root.y / 16.0F, -root.z / 16.0F);
	}

	/**
	 * Before the burst, while their own head is still on: the head's saw punching out through its forehead, the bar
	 * coming out of the face (its root moved forward to it, so none of it shows through the back or top of the head) and
	 * thinner than it will be.
	 */
	private static void punch(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, AbstractClientPlayerEntity player,
			float tickDelta, ModelPart head, float t) {
		FormMesh mesh = FormMesh.get();
		if (mesh == null) {
			return;
		}
		// Out hard, then pushing on more slowly.
		float k = MathHelper.clamp((t - FormTimeline.PUNCH) / (FormTimeline.BURST - FormTimeline.PUNCH), 0.0F, 1.0F);
		float length = PUNCH_OUT * (1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k)) / headReach(mesh);
		if (length <= 0.001F) {
			return;
		}
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		int chainFrame = chainFrame(mesh, player, tickDelta);
		matrices.push();
		head.rotate(matrices);
		matrices.translate(0.0F, PUNCH_DROP / 16.0F, 0.0F);
		for (FormMesh.Part part : mesh.parts.values()) {
			if (!part.bone.equals("head") || part.growRoot == null || (part.chainFrame >= 0 && part.chainFrame != chainFrame)) {
				continue;
			}
			matrices.push();
			saw(matrices, part.growRoot, part.growAxis, FACE, length, PUNCH_THICK);
			part.draw(matrices.peek(), out, light, overlay);
			matrices.pop();
		}
		matrices.pop();
	}

	/**
	 * At the burst: their own head (hidden whole in the form) split down the middle by the saw, each half swinging out
	 * on a hinge at the bottom of its outer side and drifting away, raw inside, and shrinking to nothing by HEAD_OPEN,
	 * as the chainsaw head swells out from between them.
	 */
	private static void split(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, AbstractClientPlayerEntity player,
			ModelPart head, float t) {
		float k = MathHelper.clamp((t - FormTimeline.BURST) / (FormTimeline.HEAD_OPEN - FormTimeline.BURST), 0.0F, 1.0F);
		float open = 1.0F - (1.0F - k) * (1.0F - k);
		float size = k < 0.5F ? 1.0F : 1.0F - (k - 0.5F) * 2.0F;
		if (size <= 0.0F) {
			return;
		}
		boolean hat = player.isPartVisible(PlayerModelPart.HAT);
		// The skin first, then the cut faces: each layer's quads all together.
		VertexConsumer skin = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(player.getSkinTextures().texture()));
		for (int side = -1; side <= 1; side += 2) {
			matrices.push();
			half(matrices, head, side, open, size);
			halfBox(skin, matrices.peek(), side, 0.0F, 0.0F, 0.0F, light, overlay);
			if (hat) {
				halfBox(skin, matrices.peek(), side, 32.0F, 0.0F, 0.5F, light, overlay);
			}
			matrices.pop();
		}
		VertexConsumer flesh = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(FLESH));
		float[][] cut = {{-8.0F, -4.0F, 0.0F, 0.0F}, {0.0F, -4.0F, 0.0F, 1.0F}, {0.0F, 4.0F, 1.0F, 1.0F}, {-8.0F, 4.0F, 1.0F, 0.0F}};
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		for (int side = -1; side <= 1; side += 2) {
			matrices.push();
			half(matrices, head, side, open, size);
			MatrixStack.Entry entry = matrices.peek();
			entry.transformNormal(-side, 0.0F, 0.0F, n);
			for (float[] c : cut) {
				entry.getPositionMatrix().transformPosition(0.0F, c[0] / 16.0F, c[1] / 16.0F, p);
				flesh.vertex(p.x, p.y, p.z, 0xFFFFFFFF, c[2], c[3], overlay, light, n.x, n.y, n.z);
			}
			matrices.pop();
		}
	}

	/** Moves the stack to one half of the split head (side -1 the half at x &lt;= 0, 1 the other). */
	private static void half(MatrixStack matrices, ModelPart head, int side, float open, float size) {
		head.rotate(matrices);
		matrices.translate(side * (4.0F + open * SPLIT_DRIFT) / 16.0F, 0.0F, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * open * SPLIT_ANGLE));
		matrices.scale(size, size, size);
		matrices.translate(-side * 4.0F / 16.0F, 0.0F, 0.0F);
	}

	/**
	 * A player model box's faces as the game builds them: the corners of each (numbered as in {@code halfBox}), where it
	 * is in the skin (u1, v1, u2, v2, in pixels from the box's own corner there) and which way it faces.
	 */
	private static final int[][] FACE_CORNERS = {{5, 4, 0, 1}, {2, 3, 7, 6}, {0, 4, 7, 3}, {1, 0, 3, 2}, {5, 1, 2, 6}, {4, 5, 6, 7}};
	private static final float[][] FACE_UV = {{8, 0, 16, 8}, {16, 8, 24, 0}, {0, 8, 8, 16}, {8, 8, 16, 16}, {16, 8, 24, 16}, {24, 8, 32, 16}};
	private static final float[][] FACE_NORMAL = {{0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}};

	/**
	 * One half of a player model box 8 pixels each way, its texture at (u, v) in the skin and grown by {@code grow} (the
	 * head, or the hat over it): the game's own faces, cut through at x = 0 and only what is on {@code side}.
	 */
	private static void halfBox(VertexConsumer out, MatrixStack.Entry entry, int side, float u, float v, float grow, int light, int overlay) {
		float x0 = -4.0F - grow;
		float x1 = 4.0F + grow;
		float y0 = -8.0F - grow;
		float y1 = grow;
		float z0 = -4.0F - grow;
		float z1 = 4.0F + grow;
		float[][] corner = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		for (int f = 0; f < FACE_CORNERS.length; f++) {
			float[] r = FACE_UV[f];
			// The corners take (u2, v1), (u1, v1), (u1, v2), (u2, v2), as the game's quads do.
			float[][] uv = {{r[2], r[1]}, {r[0], r[1]}, {r[0], r[3]}, {r[2], r[3]}};
			float[][] q = new float[4][];
			for (int i = 0; i < 4; i++) {
				float[] c = corner[FACE_CORNERS[f][i]];
				q[i] = new float[] {c[0], c[1], c[2], u + uv[i][0], v + uv[i][1]};
			}
			if (!cut(q, side)) {
				continue;
			}
			entry.transformNormal(FACE_NORMAL[f][0], FACE_NORMAL[f][1], FACE_NORMAL[f][2], n);
			for (float[] c : q) {
				entry.getPositionMatrix().transformPosition(c[0] / 16.0F, c[1] / 16.0F, c[2] / 16.0F, p);
				out.vertex(p.x, p.y, p.z, 0xFFFFFFFF, c[3] / 64.0F, c[4] / 64.0F, overlay, light, n.x, n.y, n.z);
			}
		}
	}

	/**
	 * Cuts a box face (corners as x, y, z, u, v) at x = 0, keeping what is on {@code side}: each corner past the line is
	 * slid along its edge back to it, texture and all. False if none of the face is on that side.
	 */
	private static boolean cut(float[][] q, int side) {
		float[][] was = new float[4][];
		for (int i = 0; i < 4; i++) {
			was[i] = q[i].clone();
		}
		for (int i = 0; i < 4; i++) {
			if (side * was[i][0] >= 0.0F) {
				continue;
			}
			float[] to = null;
			for (float[] o : was) {
				if (side * o[0] > 0.0F && o[1] == was[i][1] && o[2] == was[i][2]) {
					to = o;
				}
			}
			if (to == null) {
				return false;
			}
			float k = -was[i][0] / (to[0] - was[i][0]);
			for (int j = 0; j < 5; j++) {
				q[i][j] = MathHelper.lerp(k, was[i][j], to[j]);
			}
		}
		return true;
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
