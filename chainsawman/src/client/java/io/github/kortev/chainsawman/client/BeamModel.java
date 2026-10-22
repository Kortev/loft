package io.github.kortev.chainsawman.client;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * Beam the shark fiend as he is in the Reze arc, in Minecraft's own way of making animals, like Pochita and the chainsaw
 * head: boxes, painted pixel by pixel. kortev liked the smooth Blender Beam ({@code tools/beam_model.py}) as the design but
 * wanted it in the same style as those, so this is that design in boxes: a heavy, deep, rounded torpedo of a shark about 5
 * blocks long, the body a run of slices along his length, each a short stack of boxes narrowing toward its top and bottom so
 * the outline steps smoothly round, sized to the blueprint's measured sections ({@code tools/beam_boxes.py} measures and
 * writes {@link BeamLayout}, every box, part and texture corner; {@code tools/gen_chainsaw_textures.py}, {@code beam_entity},
 * paints the texture from the same layout). A big blunt head with a long rounded nose carrying on well past and above the
 * mouth, the mouth hanging open about 38 degrees over a lower jaw that snaps shut to bite, rows of small cream teeth painted
 * along both jaws, three round eyes up each side in a diagonal, a row of dark spikes down his back, the rear of the body and
 * the tail's stalk and crescent fin hinged one to the next so the tail sways, small pectoral fins, and four crab legs out of
 * the lower sides of his body: the upper part, two boxes that curve, out sideways and up past his flank to a high knee, then
 * a shin of four boxes narrowing down and in to a claw on the ground.
 *
 * <p>Pixels in the model's space: y down, the top of his back at 0 (the renderer puts that {@link #BACK} above the ground, and
 * the soles of his feet at {@link BeamLayout#HEIGHT} pixels), the middle of him at x 0, z 0, his front toward -z.
 */
public class BeamModel {
	/** How high the top of his back is above the ground (pixels): his belly clears it by 13. */
	public static final float HEIGHT = BeamLayout.HEIGHT;
	public static final float BACK = HEIGHT / 16.0F;

	private final ModelPart root;
	private final ModelPart trunk;
	private final ModelPart rear;
	private final ModelPart stalk;
	private final ModelPart tail;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart rightPectoral;
	private final ModelPart leftPectoral;
	/**
	 * The four legs, front right, front left, back right, back left: the hip (the upper part, turned out and up), the second
	 * piece of the upper part (the bow), the knee (the shin) and the shin's three lower pieces.
	 */
	private final ModelPart[] hips = new ModelPart[4];
	private final ModelPart[] bows = new ModelPart[4];
	private final ModelPart[] knees = new ModelPart[4];
	private final ModelPart[][] claws = new ModelPart[4][BeamLayout.SHIN_BEND.length];

	/** How far the mouth is open when he is not biting (radians, from shut), and open wide for a bite. */
	private static final float OPEN_REST = BeamLayout.MOUTH_OPEN;
	private static final float OPEN_WIDE = BeamLayout.MOUTH_OPEN + 0.42F;
	/** A stride: how far a leg swings at its hip, and at its knee (radians), and how high it lifts its knee going forward. */
	private static final float STRIDE_HIP = 0.3F;
	private static final float STRIDE_KNEE = 0.4F;
	private static final float LIFT = 0.4F;

	public BeamModel(ModelPart root) {
		this.root = root;
		this.trunk = root.getChild("trunk");
		this.head = trunk.getChild("head");
		this.jaw = head.getChild("jaw");
		this.rightPectoral = trunk.getChild("pectoral_r");
		this.leftPectoral = trunk.getChild("pectoral_l");
		this.rear = trunk.getChild("rear");
		this.stalk = rear.getChild("stalk");
		this.tail = stalk.getChild("tail");
		String[] names = {"leg_fr", "leg_fl", "leg_br", "leg_bl"};
		for (int i = 0; i < 4; i++) {
			hips[i] = trunk.getChild(names[i]);
			bows[i] = hips[i].getChild(names[i] + "_u2");
			knees[i] = bows[i].getChild(names[i] + "_shin");
			ModelPart last = knees[i];
			for (int k = 0; k < BeamLayout.SHIN_BEND.length; k++) {
				last = last.getChild(names[i] + "_s" + (k + 2));
				claws[i][k] = last;
			}
		}
	}

	public static TexturedModelData texturedModelData() {
		ModelData data = new ModelData();
		BeamLayout.build(data.getRoot());
		return TexturedModelData.of(data, BeamLayout.TEXTURE_WIDTH, BeamLayout.TEXTURE_HEIGHT);
	}

	/**
	 * Poses him: {@code walk} the phase of his gait (it advances with the ground he covers), {@code time} a phase that runs
	 * with the clock (the tail's sway), {@code pace} how fast he is going (0 to 1), {@code bite} the ticks since his last
	 * bite, {@code air} how far he is off the ground (0 to 1: legs drawn up).
	 */
	public void animate(float walk, float time, float pace, float bite, float air) {
		// His gait: the diagonal pairs (front right with back left) stepping together. A leg's "forward" is positive.
		float[] forward = new float[4];
		float[] lifting = new float[4];
		float[] phases = {0.0F, MathHelper.PI, MathHelper.PI, 0.0F};
		for (int i = 0; i < 4; i++) {
			float p = walk + phases[i];
			forward[i] = MathHelper.sin(p) * pace;
			lifting[i] = Math.max(0.0F, MathHelper.cos(p)) * pace;
		}
		for (int i = 0; i < 4; i++) {
			int end = i < 2 ? 0 : 1;
			float side = i % 2 == 0 ? 1.0F : -1.0F;
			float lift = LIFT * lifting[i] + 0.5F * air;
			// The upper part: angled up from the side (the left one the other way round), curving (steeper at the hip, flatter
			// at the knee); yawed forward or back, swinging.
			float yaw = BeamLayout.LEG_SPLAY[end] + STRIDE_HIP * forward[i] * (1.0F - air);
			hips[i].yaw = side * yaw;
			float up = BeamLayout.LEG_UP + lift;
			hips[i].roll = -side * (up + BeamLayout.LEG_BOW);
			bows[i].roll = side * 2.0F * BeamLayout.LEG_BOW;
			// The shin: hangs from the knee, angled in at the foot (so it keeps its slant however the knee lifts); swinging
			// forward and back with the stride, its claw hooking in.
			knees[i].roll = side * (up - BeamLayout.LEG_BOW + BeamLayout.SHIN_IN);
			knees[i].pitch = BeamLayout.SHIN_FORE[end] - STRIDE_KNEE * forward[i] * (1.0F - air) - 0.3F * air;
			for (int k = 0; k < BeamLayout.SHIN_BEND.length; k++) {
				claws[i][k].roll = side * BeamLayout.SHIN_BEND[k];
			}
		}
		// The body rocks a little with his steps and rises and falls with them.
		trunk.roll = MathHelper.sin(walk) * 0.02F * pace;
		trunk.pivotY = -Math.abs(MathHelper.sin(walk)) * 1.0F * pace;
		float sway = 0.35F + 0.65F * pace;
		trunk.yaw = MathHelper.sin(time) * 0.025F * sway;
		rear.yaw = MathHelper.sin(time - 0.9F) * 0.12F * sway;
		stalk.yaw = MathHelper.sin(time - 1.8F) * 0.2F * sway;
		tail.yaw = MathHelper.sin(time - 2.7F) * 0.28F * sway;
		// The bite: the jaw opens wide, snaps shut and falls back open; the head lifts with it.
		float open = OPEN_REST + 0.05F * MathHelper.sin(time * 0.5F);
		float lift = 0.0F;
		if (bite < 9.0F) {
			if (bite < 1.5F) {
				float k = bite / 1.5F;
				open = MathHelper.lerp(k, OPEN_REST, OPEN_WIDE);
				lift = k * 0.2F;
			} else if (bite < 3.5F) {
				float k = (bite - 1.5F) / 2.0F;
				open = MathHelper.lerp(k, OPEN_WIDE, 0.03F);
				lift = MathHelper.lerp(k, 0.2F, 0.08F);
			} else {
				float k = MathHelper.clamp((bite - 3.5F) / 5.5F, 0.0F, 1.0F);
				open = MathHelper.lerp(k, 0.03F, open);
				lift = MathHelper.lerp(k, 0.08F, 0.0F);
			}
		}
		// The jaw lies along the lip when shut: its rest turn is the lip's slope up toward the front, the opening on top of it.
		jaw.pitch = -BeamLayout.LIP_TILT + open;
		head.pitch = -BeamLayout.HEAD_TILT - lift - 0.1F * air;
		rightPectoral.roll = BeamLayout.PECTORAL_DOWN + MathHelper.sin(time + 1.0F) * 0.06F;
		leftPectoral.roll = -BeamLayout.PECTORAL_DOWN - MathHelper.sin(time + 1.0F) * 0.06F;
		rightPectoral.yaw = -BeamLayout.PECTORAL_BACK;
		leftPectoral.yaw = BeamLayout.PECTORAL_BACK;
	}

	public void render(MatrixStack matrices, VertexConsumer vertices, int light) {
		root.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
	}
}
