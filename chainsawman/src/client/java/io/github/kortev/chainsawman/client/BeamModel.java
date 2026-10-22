package io.github.kortev.chainsawman.client;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * Beam the shark fiend as he is in the Reze arc, in Minecraft's own way of making animals, like Pochita: boxes, painted
 * pixel by pixel ({@code tools/gen_chainsaw_textures.py}, {@code beam_entity}, lays the texture out for exactly these
 * boxes and their texture corners; change one, change the other). A big shark about 3.75 blocks long that stands and
 * walks on <em>four long thin crab-like legs</em>: a deep trunk with a row of dark spikes down its back, the head in front
 * (a huge mouth hanging open over a lower jaw that snaps shut to bite, rows of cream teeth, three round eyes standing out
 * up each side), the rear of the body, the tail's stalk and its crescent fin hinged one to the next so the tail sways, and
 * the small pectoral fins. Each leg is an upper part angled out and up from his side and a long shin angled down to the
 * ground, ending in a point (the shin is three boxes narrowing to the tip).
 *
 * <p>Pixels in the model's space: y down, the top of his back at 0 (the renderer puts that {@link #BACK} above the
 * ground, and the soles of his feet at {@link #HEIGHT} pixels), the middle of the trunk at x 0, z 0, his front toward -z.
 */
public class BeamModel {
	/** How high the top of his back is above the ground (pixels): the length of a leg and the depth of his body and his clearance. */
	public static final float HEIGHT = 41.0F;
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
	/** The four legs' two hinges each: the hip (the upper part) and the knee (the shin). Front right, front left, back right, back left. */
	private final ModelPart[] hips = new ModelPart[4];
	private final ModelPart[] knees = new ModelPart[4];

	/** How far the jaw hangs open when he is not biting (radians), and open wide. */
	private static final float JAW_REST = 0.5F;
	private static final float JAW_WIDE = 0.95F;
	/** The upper parts of the legs: how far up they are angled from his side (radians), and how far forward (front) or back (rear) they stand. */
	private static final float LEG_UP = 0.5F;
	private static final float LEG_SPLAY = 0.28F;
	/** The shins' lean out at their feet (radians, from straight down). */
	private static final float SHIN_LEAN = 0.08F;
	/** A stride: how far a leg swings at its hip, and at its knee (radians), and how high it lifts its knee going forward. */
	private static final float STRIDE_HIP = 0.3F;
	private static final float STRIDE_KNEE = 0.3F;
	private static final float LIFT = 0.4F;
	/** The pectorals' angle down and out of his sides, and their sweep back (radians). */
	private static final float PECTORAL_DOWN = 0.6F;
	private static final float PECTORAL_BACK = 0.45F;

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
			knees[i] = hips[i].getChild("shin");
		}
	}

	public static TexturedModelData texturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData trunk = root.addChild("trunk", box("trunk", -9.0F, 0.0F, -11.0F, 18, 22, 22), ModelTransform.NONE);
		// The row of dark spikes down his back, the front one the biggest, each a flat triangle swept back.
		trunk.addChild("spike0", plane("spike0", 0.0F, -12.0F, 5.0F), ModelTransform.NONE);
		trunk.addChild("spike1", plane("spike1", 0.0F, -11.0F, 8.0F), ModelTransform.NONE);
		trunk.addChild("spike2", plane("spike2", 0.0F, -8.0F, 11.0F), ModelTransform.NONE);
		trunk.addChild("spike3", plane("spike3", 0.0F, -7.0F, 14.0F), ModelTransform.NONE);
		// The head, hinged where it meets the trunk so it lifts to bite: the roof of the mouth in three steps narrowing to the
		// blunt snout (cream teeth hanging along its edges), three eyes standing out up each side, and the lower jaw hinged
		// at the back of the mouth (teeth rising along it).
		ModelPartData head = trunk.addChild("head", ModelPartBuilder.create(), ModelTransform.pivot(0.0F, 12.0F, -11.0F));
		head.addChild("head1", box("head1", -9.0F, -12.0F, -7.0F, 18, 16, 7), ModelTransform.NONE);
		head.addChild("head2", box("head2", -8.0F, -10.0F, -13.0F, 16, 13, 6), ModelTransform.NONE);
		head.addChild("head3", box("head3", -6.0F, -8.0F, -18.0F, 12, 10, 5), ModelTransform.NONE);
		for (float side : new float[] {-1.0F, 1.0F}) {
			boolean left = side < 0.0F;
			// (a left part is the right one mirrored: its box on the other side, its texture turned round)
			float x = left ? -1.0F : 1.0F;
			head.addChild("teeth_h1_" + side, plane("teeth_h1", 8.0F * x, 4.0F, -7.0F), ModelTransform.NONE);
			head.addChild("teeth_h2_" + side, plane("teeth_h2", 7.0F * x, 3.0F, -13.0F), ModelTransform.NONE);
			head.addChild("teeth_h3_" + side, plane("teeth_h3", 5.0F * x, 2.0F, -18.0F), ModelTransform.NONE);
			head.addChild("eye_a_" + side, eye("eyeA", left, 8.0F, -9.0F, -12.0F, 6, 6), ModelTransform.NONE);
			head.addChild("eye_b_" + side, eye("eyeB", left, 9.0F, -12.0F, -6.0F, 5, 5), ModelTransform.NONE);
			head.addChild("eye_c_" + side, eye("eyeC", left, 9.0F, -14.0F, -2.0F, 4, 4), ModelTransform.NONE);
		}
		for (float x : new float[] {-5.0F, -3.0F, -1.0F, 0.0F, 2.0F, 4.0F}) {
			head.addChild("tooth_up_" + x, tooth(x, 2.0F, -18.0F), ModelTransform.NONE);
		}
		ModelPartData jaw = head.addChild("jaw", box("jaw", -7.0F, 0.0F, -17.0F, 14, 6, 17), ModelTransform.pivot(0.0F, 4.0F, -1.0F));
		jaw.addChild("teeth_jaw_l", plane("teeth_jaw", -6.0F, -3.0F, -17.0F), ModelTransform.NONE);
		jaw.addChild("teeth_jaw_r", plane("teeth_jaw", 6.0F, -3.0F, -17.0F), ModelTransform.NONE);
		for (float x : new float[] {-5.0F, -3.0F, -1.0F, 0.0F, 2.0F, 4.0F}) {
			jaw.addChild("tooth_low_" + x, tooth(x, -3.0F, -17.0F), ModelTransform.NONE);
		}
		// The small pectoral fins, down and out of his sides and swept back, a smaller piece at each tip.
		ModelPartData right = trunk.addChild("pectoral_r", box("pect", 0.0F, 0.0F, 0.0F, 9, 1, 5), ModelTransform.pivot(8.0F, 15.0F, -3.0F));
		right.addChild("tip_r", box("pect_tip", 0.0F, 0.0F, 1.0F, 5, 1, 3), ModelTransform.pivot(9.0F, 0.0F, 0.0F));
		ModelPartData left = trunk.addChild("pectoral_l", box("pect", -9.0F, 0.0F, 0.0F, 9, 1, 5, true), ModelTransform.pivot(-8.0F, 15.0F, -3.0F));
		left.addChild("tip_l", box("pect_tip", -5.0F, 0.0F, 1.0F, 5, 1, 3, true), ModelTransform.pivot(-9.0F, 0.0F, 0.0F));
		// The four legs: the front pair a little forward of the middle, the rear pair near the end of the trunk.
		leg(trunk, "leg_fr", 1.0F, -5.0F);
		leg(trunk, "leg_fl", -1.0F, -5.0F);
		leg(trunk, "leg_br", 1.0F, 8.0F);
		leg(trunk, "leg_bl", -1.0F, 8.0F);
		// The rear of the body, hinged at the back of the trunk, with the last spike on it; then the stalk, then the crescent
		// tail fin: a base, the long upper lobe in two steps and the shorter lower lobe.
		ModelPartData rear = trunk.addChild("rear", box("rear", -7.0F, 2.0F, 0.0F, 14, 18, 8), ModelTransform.pivot(0.0F, 0.0F, 11.0F));
		rear.addChild("spike4", plane("spike4", 0.0F, -6.0F, -1.0F), ModelTransform.NONE);
		ModelPartData stalk = rear.addChild("stalk", box("stalk", -3.0F, 6.0F, 0.0F, 6, 10, 6), ModelTransform.pivot(0.0F, 0.0F, 8.0F));
		ModelPartData tail = stalk.addChild("tail", box("tail_base", -1.0F, 5.0F, 0.0F, 2, 8, 4), ModelTransform.pivot(0.0F, 0.0F, 6.0F));
		tail.addChild("tail_up1", box("tail_up1", -1.0F, -1.0F, 1.0F, 2, 6, 4), ModelTransform.NONE);
		tail.addChild("tail_up2", box("tail_up2", -1.0F, -6.0F, 2.0F, 2, 5, 4), ModelTransform.NONE);
		tail.addChild("tail_low", box("tail_low", -1.0F, 13.0F, 1.0F, 2, 5, 3), ModelTransform.NONE);
		return TexturedModelData.of(data, 128, 144);
	}

	/**
	 * One leg, at the trunk's side ({@code side} +1 the right, -1 the left), its hip {@code z} along the trunk: the upper part
	 * out and up from the hip, the shin hanging from its end (the knee), and the shin's narrowing tip. The left leg is the
	 * right one mirrored.
	 */
	private static void leg(ModelPartData trunk, String name, float side, float z) {
		boolean left = side < 0.0F;
		ModelPartData hip = trunk.addChild(name, box("leg_up", left ? -11.0F : 0.0F, -2.0F, -2.0F, 11, 4, 4, left), ModelTransform.pivot(9.0F * side, 9.0F, z));
		ModelPartData shin = hip.addChild("shin", box("leg_shin", -2.0F, -2.0F, -2.0F, 4, 24, 4, left), ModelTransform.pivot(11.0F * side, 0.0F, 0.0F));
		ModelPartData mid = shin.addChild("mid", box("leg_mid", -1.5F, 0.0F, -1.5F, 3, 8, 3, left), ModelTransform.pivot(0.0F, 22.0F, 0.0F));
		ModelPartData tip = mid.addChild("tip", box("leg_tip", -1.0F, 0.0F, -1.0F, 2, 5, 2, left), ModelTransform.pivot(0.0F, 8.0F, 0.0F));
		tip.addChild("point", box("leg_point", -0.5F, 0.0F, -0.5F, 1, 3, 1, left), ModelTransform.pivot(0.0F, 5.0F, 0.0F));
	}

	/** The texture corners and sizes of the boxes, as the painter lays them out ({@code BEAM_BOXES} in {@code tools/gen_chainsaw_textures.py}). */
	private static int[] corner(String name) {
		return switch (name) {
			case "trunk" -> new int[] {0, 0, 18, 22, 22};
			case "head1" -> new int[] {44, 44, 18, 16, 7};
			case "head2" -> new int[] {0, 93, 16, 13, 6};
			case "head3" -> new int[] {92, 93, 12, 10, 5};
			case "jaw" -> new int[] {0, 70, 14, 6, 17};
			case "eyeA" -> new int[] {10, 112, 2, 6, 6};
			case "eyeB" -> new int[] {50, 112, 2, 5, 5};
			case "eyeC" -> new int[] {114, 112, 2, 4, 4};
			case "rear" -> new int[] {0, 44, 14, 18, 8};
			case "stalk" -> new int[] {56, 93, 6, 10, 6};
			case "tail_base" -> new int[] {26, 112, 2, 8, 4};
			case "tail_up1" -> new int[] {64, 112, 2, 6, 4};
			case "tail_up2" -> new int[] {90, 112, 2, 5, 4};
			case "tail_low" -> new int[] {0, 126, 2, 5, 3};
			case "leg_up" -> new int[] {10, 126, 11, 4, 4};
			case "leg_shin" -> new int[] {80, 0, 4, 24, 4};
			case "leg_mid" -> new int[] {38, 112, 3, 8, 3};
			case "leg_tip" -> new int[] {50, 126, 2, 5, 2};
			case "leg_point" -> new int[] {86, 126, 1, 3, 1};
			case "pect" -> new int[] {58, 126, 9, 1, 5};
			case "pect_tip" -> new int[] {90, 126, 5, 1, 3};
			case "spike0" -> new int[] {62, 70, 0, 15, 7};
			case "spike1" -> new int[] {76, 70, 0, 14, 7};
			case "spike2" -> new int[] {44, 93, 0, 11, 6};
			case "spike3" -> new int[] {80, 93, 0, 10, 6};
			case "spike4" -> new int[] {0, 112, 0, 9, 5};
			case "teeth_jaw" -> new int[] {90, 70, 0, 3, 17};
			case "teeth_h1" -> new int[] {76, 112, 0, 3, 7};
			case "teeth_h2" -> new int[] {102, 112, 0, 3, 6};
			case "teeth_h3" -> new int[] {40, 126, 0, 3, 5};
			case "tooth" -> new int[] {106, 126, 1, 3, 1};
			default -> throw new IllegalArgumentException(name);
		};
	}

	private static ModelPartBuilder box(String name, float x, float y, float z, int w, int h, int d) {
		return box(name, x, y, z, w, h, d, false);
	}

	/** A box of {@code name}'s size at its texture corner, at {@code x}, {@code y}, {@code z}; {@code mirrored} turns its texture round (a left part). */
	private static ModelPartBuilder box(String name, float x, float y, float z, int w, int h, int d, boolean mirrored) {
		int[] c = corner(name);
		return ModelPartBuilder.create().uv(c[0], c[1]).mirrored(mirrored).cuboid(x, y, z, w, h, d);
	}

	/** A flat piece (no thickness: a spike, a row of teeth) of {@code name}'s size, its texture's two sides painted alike. */
	private static ModelPartBuilder plane(String name, float x, float y, float z) {
		int[] c = corner(name);
		return box(name, x, y, z, c[2], c[3], c[4]);
	}

	/** An eye: a small box standing out of his side, its outer face painted (for a left eye, the right one's texture turned round). */
	private static ModelPartBuilder eye(String name, boolean left, float x, float y, float z, int h, int d) {
		return box(name, left ? -x - 2.0F : x, y, z, 2, h, d, left);
	}

	/** A tooth: one pixel wide and deep and three tall, with its base at {@code y}. */
	private static ModelPartBuilder tooth(float x, float y, float z) {
		return box("tooth", x, y, z, 1, 3, 1);
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
			boolean front = i < 2;
			float side = i % 2 == 0 ? 1.0F : -1.0F;
			float lift = LIFT * lifting[i] + 0.55F * air;
			// The upper part: angled up from the side (the left one the other way round); yawed forward or back, swinging.
			float yaw = (front ? LEG_SPLAY : -LEG_SPLAY) + STRIDE_HIP * forward[i] * (1.0F - air);
			hips[i].yaw = side * yaw;
			float up = LEG_UP + lift;
			hips[i].roll = -side * up;
			// The shin: hangs from the knee, leaning out a little at the foot; swinging forward and back with the stride.
			knees[i].roll = side * (up - SHIN_LEAN);
			knees[i].pitch = -STRIDE_KNEE * forward[i] * (1.0F - air) - 0.35F * air;
		}
		// The body rocks a little with his steps and rises and falls with them.
		trunk.roll = MathHelper.sin(walk) * 0.025F * pace;
		trunk.pivotY = -Math.abs(MathHelper.sin(walk)) * 0.9F * pace;
		float sway = 0.35F + 0.65F * pace;
		trunk.yaw = MathHelper.sin(time) * 0.03F * sway;
		rear.yaw = MathHelper.sin(time - 0.9F) * 0.14F * sway;
		stalk.yaw = MathHelper.sin(time - 1.8F) * 0.24F * sway;
		tail.yaw = MathHelper.sin(time - 2.7F) * 0.32F * sway;
		stalk.pitch = 0.18F;
		// The bite: the jaw opens wide, snaps shut and falls back open; the head lifts with it.
		float open = JAW_REST + 0.06F * MathHelper.sin(time * 0.5F);
		float lift = 0.0F;
		if (bite < 9.0F) {
			if (bite < 1.5F) {
				float k = bite / 1.5F;
				open = MathHelper.lerp(k, JAW_REST, JAW_WIDE);
				lift = k * 0.28F;
			} else if (bite < 3.5F) {
				float k = (bite - 1.5F) / 2.0F;
				open = MathHelper.lerp(k, JAW_WIDE, 0.04F);
				lift = MathHelper.lerp(k, 0.28F, 0.12F);
			} else {
				float k = MathHelper.clamp((bite - 3.5F) / 5.5F, 0.0F, 1.0F);
				open = MathHelper.lerp(k, 0.04F, open);
				lift = MathHelper.lerp(k, 0.12F, 0.0F);
			}
		}
		jaw.pitch = open;
		head.pitch = -lift - 0.12F * air;
		rightPectoral.roll = PECTORAL_DOWN + MathHelper.sin(time + 1.0F) * 0.06F;
		leftPectoral.roll = -PECTORAL_DOWN - MathHelper.sin(time + 1.0F) * 0.06F;
		rightPectoral.yaw = -PECTORAL_BACK;
		leftPectoral.yaw = PECTORAL_BACK;
	}

	public void render(MatrixStack matrices, VertexConsumer vertices, int light) {
		root.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
	}
}
