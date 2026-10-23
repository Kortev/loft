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
 * head: boxes, painted pixel by pixel. This is the shape kortev approved as two pixel-art drawings (the side and the front,
 * {@code tools/beam_profile.py} and {@code tools/beam_front.py}), built from them in a handful of big boxes the way Pochita is: a
 * huge heavy shark about 9 blocks from snout to tail and 4 tall, each mass of him one big box with narrower steps on top and
 * under, painted flat ({@code tools/beam_mob.py} lists the boxes, writes {@link BeamLayout}, every box, part and texture
 * corner, and paints the texture; fins and spikes are flat cut-out planes). A big blunt head with
 * a snout dome overhanging the wide open mouth, the corner of the mouth under the front eye, the lower jaw (its own part)
 * dropped from that corner over a big round chin, teeth along both lips, three round eyes up each side, five spikes down his
 * back, the rear of the body and the tail's stalk and crescent fin hinged one to the next so the tail sways, small pectoral
 * fins, and four crab legs: the upper blade out of the lower side of his body, sideways and up to a high knee, the shin a
 * tapering blade down and in to a point on the ground.
 *
 * <p>Pixels in the model's space: y down, the model's y = 0 {@link #BACK} above the ground (the spike tips), the soles of
 * his feet at {@link BeamLayout#HEIGHT} pixels, the middle of him at x 0, z 0, his front toward -z.
 */
public class BeamModel {
	/** How high the model's y = 0 is above the ground (blocks). */
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
	/** The four legs, front right, front left, back right, back left: the hip (turned by the stride), the upper blade, the shin. */
	private final ModelPart[] hips = new ModelPart[4];
	private final ModelPart[] uppers = new ModelPart[4];
	private final ModelPart[] shins = new ModelPart[4];

	/** How far the mouth is open when he is not biting (radians, from shut), and open wide for a bite. */
	private static final float OPEN_REST = BeamLayout.MOUTH_OPEN;
	private static final float OPEN_WIDE = BeamLayout.MOUTH_OPEN + 0.42F;
	/** A stride: how far a leg swings at its hip (radians), and how far it lifts (a roll at the hip). */
	private static final float STRIDE_HIP = 0.4F;
	private static final float LIFT = 0.45F;

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
			uppers[i] = hips[i].getChild(names[i] + "_up");
			shins[i] = uppers[i].getChild(names[i] + "_shin");
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
			float side = i % 2 == 0 ? 1.0F : -1.0F;
			// The legs stand as drawn (the parts' own turns); the hip swings the whole leg about the vertical through it, and
			// rolls it up to lift the foot (the right-hand legs one way, the left the other).
			hips[i].yaw = side * STRIDE_HIP * forward[i] * (1.0F - air);
			hips[i].roll = -side * (LIFT * lifting[i] + 0.7F * air);
			shins[i].pitch = BeamLayout.SHIN_REST[i][0] - 0.5F * air;
		}
		// The body rocks a little with his steps and rises and falls with them.
		trunk.roll = MathHelper.sin(walk) * 0.015F * pace;
		trunk.pivotY = -Math.abs(MathHelper.sin(walk)) * 1.0F * pace;
		float sway = 0.35F + 0.65F * pace;
		trunk.yaw = MathHelper.sin(time) * 0.02F * sway;
		rear.yaw = MathHelper.sin(time - 0.9F) * 0.05F * sway;
		stalk.yaw = MathHelper.sin(time - 1.8F) * 0.12F * sway;
		tail.yaw = MathHelper.sin(time - 2.7F) * 0.22F * sway;
		// The bite: the jaw opens wide, snaps shut and falls back open; the head lifts with it.
		float open = OPEN_REST + 0.04F * MathHelper.sin(time * 0.5F);
		float lift = 0.0F;
		if (bite < 9.0F) {
			if (bite < 1.5F) {
				float k = bite / 1.5F;
				open = MathHelper.lerp(k, OPEN_REST, OPEN_WIDE);
				lift = k * 0.12F;
			} else if (bite < 3.5F) {
				float k = (bite - 1.5F) / 2.0F;
				open = MathHelper.lerp(k, OPEN_WIDE, 0.03F);
				lift = MathHelper.lerp(k, 0.12F, 0.04F);
			} else {
				float k = MathHelper.clamp((bite - 3.5F) / 5.5F, 0.0F, 1.0F);
				open = MathHelper.lerp(k, 0.03F, open);
				lift = MathHelper.lerp(k, 0.04F, 0.0F);
			}
		}
		// The jaw is built as drawn (open by MOUTH_OPEN, its frame turned along the rim by JAW_REST): shut is MOUTH_OPEN less.
		jaw.pitch = BeamLayout.JAW_REST - BeamLayout.MOUTH_OPEN + open;
		head.pitch = -lift - 0.08F * air;
		rightPectoral.roll = BeamLayout.PECTORAL_ROLL + MathHelper.sin(time + 1.0F) * 0.06F;
		leftPectoral.roll = -BeamLayout.PECTORAL_ROLL - MathHelper.sin(time + 1.0F) * 0.06F;
	}

	public void render(MatrixStack matrices, VertexConsumer vertices, int light) {
		root.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
	}
}
