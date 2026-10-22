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
 * boxes and their texture corners; change one, change the other). A huge heavy torpedo of a shark, about 5 blocks long,
 * 2.2 deep and 2 wide, slung low on <em>four thin crab legs</em>: the body is stepped boxes, narrower at the top and bottom
 * and widest in the middle, the head (two fifths of him) tapering to a blunt snout, a huge mouth hanging open over a lower
 * jaw that snaps shut to bite (many small cream teeth painted along both jaws), three round eyes standing out up each side
 * in a diagonal, a row of dark spikes down his back, the rear of the body, the tail's stalk and its crescent fin hinged one to
 * the next so the tail sways, and the small pectoral fins. Each leg comes out of the lower side of his body: the upper part
 * out sideways and a little up, well past his flank, to a high knee, then a long shin angled down and a little in to a sharp
 * point on the ground (the shin is three boxes narrowing to the tip).
 *
 * <p>Pixels in the model's space: y down, the top of his back at 0 (the renderer puts that {@link #BACK} above the
 * ground, and the soles of his feet at {@link #HEIGHT} pixels), the middle of him at x 0, z 0, his front toward -z.
 */
public class BeamModel {
	/** How high the top of his back is above the ground (pixels): his belly clears it by 13. */
	public static final float HEIGHT = 48.0F;
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
	private static final float JAW_REST = 0.45F;
	private static final float JAW_WIDE = 0.85F;
	/** The upper parts of the legs: how far up they are angled from his side (radians), how far forward (front pair) or back (rear pair) they stand, and how far in the shins lean at their feet. */
	private static final float LEG_UP = 0.52F;
	private static final float LEG_SPLAY = 0.22F;
	private static final float SHIN_IN = 0.21F;
	/** A stride: how far a leg swings at its hip, and at its knee (radians), and how high it lifts its knee going forward. */
	private static final float STRIDE_HIP = 0.3F;
	private static final float STRIDE_KNEE = 0.4F;
	private static final float LIFT = 0.4F;
	/** The pectorals' angle down and out of his sides, and their sweep back (radians). */
	private static final float PECTORAL_DOWN = 0.6F;
	private static final float PECTORAL_BACK = 0.45F;
	/** The head's hinge where it meets the trunk, and the jaw's hinge (at the back of the mouth), in the model's space (pixels). */
	private static final float HEAD_Y = 16.0F;
	private static final float HEAD_Z = -8.0F;
	private static final float JAW_Y = 24.0F;
	private static final float JAW_Z = -10.0F;

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
		// The trunk, stepped: a wide middle, a narrower back above it and belly below.
		ModelPartData trunk = root.addChild("trunk", box("trunk_core", -16.0F, 6.0F, -8.0F), ModelTransform.NONE);
		trunk.addChild("trunk_top", box("trunk_top", -13.0F, 0.0F, -8.0F), ModelTransform.NONE);
		trunk.addChild("trunk_bot", box("trunk_bot", -12.0F, 28.0F, -8.0F), ModelTransform.NONE);
		// The row of dark spikes down his back, the front one the biggest, each a flat triangle swept back.
		trunk.addChild("spike0", plane("spike0", 0.0F, -12.0F, -10.0F), ModelTransform.NONE);
		trunk.addChild("spike1", plane("spike1", 0.0F, -10.0F, -6.0F), ModelTransform.NONE);
		trunk.addChild("spike2", plane("spike2", 0.0F, -8.0F, -2.0F), ModelTransform.NONE);
		trunk.addChild("spike3", plane("spike3", 0.0F, -6.0F, 14.0F), ModelTransform.NONE);
		// The head, hinged where it meets the trunk so it lifts to bite. The roof of the mouth in four steps narrowing to the
		// blunt snout, each with its dark back above it; many small cream teeth hanging along its edges; three eyes standing
		// out up each side; and the lower jaw hinged at the back of the mouth, its three steps with teeth rising along them.
		ModelPartData head = trunk.addChild("head", ModelPartBuilder.create(), ModelTransform.pivot(0.0F, HEAD_Y, HEAD_Z));
		head.addChild("h1_core", box("h1_core", -16.0F, 6.0F - HEAD_Y, -20.0F - HEAD_Z), ModelTransform.NONE);
		head.addChild("h1_top", box("h1_top", -13.0F, 0.0F - HEAD_Y, -20.0F - HEAD_Z), ModelTransform.NONE);
		head.addChild("h2_core", box("h2_core", -14.0F, 4.0F - HEAD_Y, -29.0F - HEAD_Z), ModelTransform.NONE);
		head.addChild("h2_top", box("h2_top", -11.0F, 1.0F - HEAD_Y, -29.0F - HEAD_Z), ModelTransform.NONE);
		head.addChild("h3_core", box("h3_core", -11.0F, 6.0F - HEAD_Y, -35.0F - HEAD_Z), ModelTransform.NONE);
		head.addChild("h3_top", box("h3_top", -8.0F, 4.0F - HEAD_Y, -35.0F - HEAD_Z), ModelTransform.NONE);
		head.addChild("h4", box("h4", -7.0F, 8.0F - HEAD_Y, -40.0F - HEAD_Z), ModelTransform.NONE);
		for (float side : new float[] {-1.0F, 1.0F}) {
			boolean left = side < 0.0F;
			head.addChild("teeth_h1_" + side, plane("teeth_h1", 15.0F * side, 24.0F - HEAD_Y, -20.0F - HEAD_Z), ModelTransform.NONE);
			head.addChild("teeth_h2_" + side, plane("teeth_h2", 13.0F * side, 22.0F - HEAD_Y, -29.0F - HEAD_Z), ModelTransform.NONE);
			head.addChild("teeth_h3_" + side, plane("teeth_h3", 10.0F * side, 20.0F - HEAD_Y, -35.0F - HEAD_Z), ModelTransform.NONE);
			head.addChild("teeth_h4_" + side, plane("teeth_h4", 6.0F * side, 18.0F - HEAD_Y, -40.0F - HEAD_Z), ModelTransform.NONE);
			// The eyes, in a diagonal row up and back above the back corner of the mouth, standing out a pixel.
			head.addChild("eye_a_" + side, eye("eyeA", left, 16.0F, 12.0F - HEAD_Y, -20.0F - HEAD_Z), ModelTransform.NONE);
			head.addChild("eye_b_" + side, eye("eyeB", left, 16.0F, 6.0F - HEAD_Y, -15.0F - HEAD_Z), ModelTransform.NONE);
			head.addChild("eye_c_" + side, eye("eyeC", left, 13.0F, 1.0F - HEAD_Y, -11.0F - HEAD_Z), ModelTransform.NONE);
		}
		head.addChild("teeth_front_up", plane("teeth_front_up", -6.0F, 18.0F - HEAD_Y, -40.0F - HEAD_Z), ModelTransform.NONE);
		ModelPartData jaw = head.addChild("jaw", ModelPartBuilder.create(), ModelTransform.pivot(0.0F, JAW_Y - HEAD_Y, JAW_Z - HEAD_Z));
		jaw.addChild("jaw1", box("jaw1", -13.0F, 0.0F, -12.0F), ModelTransform.NONE);
		jaw.addChild("jaw2", box("jaw2", -10.0F, -2.0F, -22.0F), ModelTransform.NONE);
		jaw.addChild("jaw3", box("jaw3", -7.0F, -4.0F, -30.0F), ModelTransform.NONE);
		for (float side : new float[] {-1.0F, 1.0F}) {
			jaw.addChild("teeth_j1_" + side, plane("teeth_j1", 12.0F * side, -4.0F, -12.0F), ModelTransform.NONE);
			jaw.addChild("teeth_j2_" + side, plane("teeth_j2", 9.0F * side, -6.0F, -22.0F), ModelTransform.NONE);
			jaw.addChild("teeth_j3_" + side, plane("teeth_j3", 6.0F * side, -8.0F, -30.0F), ModelTransform.NONE);
		}
		jaw.addChild("teeth_front_low", plane("teeth_front_low", -6.0F, -8.0F, -30.0F), ModelTransform.NONE);
		// The small pectoral fins, down and out of his lower sides and swept back, a smaller piece at each tip.
		ModelPartData right = trunk.addChild("pectoral_r", box("pect", 0.0F, 0.0F, 0.0F), ModelTransform.pivot(13.0F, 31.0F, 0.0F));
		right.addChild("tip_r", box("pect_tip", 0.0F, 0.0F, 1.0F), ModelTransform.pivot(8.0F, 0.0F, 0.0F));
		ModelPartData left = trunk.addChild("pectoral_l", box("pect", -8.0F, 0.0F, 0.0F, true), ModelTransform.pivot(-13.0F, 31.0F, 0.0F));
		left.addChild("tip_l", box("pect_tip", -4.0F, 0.0F, 1.0F, true), ModelTransform.pivot(-8.0F, 0.0F, 0.0F));
		// The four legs: the front pair just behind the head, the rear pair in the rear third.
		leg(trunk, "leg_fr", 1.0F, -3.0F);
		leg(trunk, "leg_fl", -1.0F, -3.0F);
		leg(trunk, "leg_br", 1.0F, 14.0F);
		leg(trunk, "leg_bl", -1.0F, 14.0F);
		// The rear of the body, hinged at the back of the trunk, with the last spike on it; then the stalk, then the crescent
		// tail fin: a base, the long upper lobe in two steps and the shorter lower lobe.
		ModelPartData rear = trunk.addChild("rear", box("rear", -11.0F, 4.0F, 0.0F), ModelTransform.pivot(0.0F, 0.0F, 18.0F));
		rear.addChild("spike4", plane("spike4", 0.0F, -1.0F, 3.0F), ModelTransform.NONE);
		ModelPartData stalk = rear.addChild("stalk", box("stalk", -6.0F, 9.0F, 0.0F), ModelTransform.pivot(0.0F, 0.0F, 9.0F));
		ModelPartData tail = stalk.addChild("tail", box("tail_base", -1.0F, 6.0F, 0.0F), ModelTransform.pivot(0.0F, 0.0F, 5.0F));
		tail.addChild("tail_up1", box("tail_up1", -1.0F, -2.0F, 1.0F), ModelTransform.NONE);
		tail.addChild("tail_up2", box("tail_up2", -1.0F, -8.0F, 2.0F), ModelTransform.NONE);
		tail.addChild("tail_low", box("tail_low", -1.0F, 18.0F, 1.0F), ModelTransform.NONE);
		return TexturedModelData.of(data, 256, 160);
	}

	/**
	 * One leg, out of the lower side of the trunk ({@code side} +1 the right, -1 the left), its hip {@code z} along the trunk:
	 * the upper part out sideways and a little up, the shin hanging from its end (the knee) and angled down and in, and the
	 * shin's narrowing tip ending in a point. The left leg is the right one mirrored.
	 */
	private static void leg(ModelPartData trunk, String name, float side, float z) {
		boolean left = side < 0.0F;
		ModelPartData hip = trunk.addChild(name, box("leg_up", left ? -16.0F : 0.0F, -1.0F, -1.0F, left), ModelTransform.pivot(16.0F * side, 28.0F, z));
		ModelPartData shin = hip.addChild("shin", box("leg_shin", -1.0F, -1.0F, -1.0F, left), ModelTransform.pivot(16.0F * side, 0.0F, 0.0F));
		ModelPartData mid = shin.addChild("mid", box("leg_mid", -1.0F, 0.0F, -1.0F, left), ModelTransform.pivot(0.0F, 14.0F, 0.0F));
		mid.addChild("point", box("leg_point", -0.5F, 0.0F, -0.5F, left), ModelTransform.pivot(0.0F, 6.0F, 0.0F));
	}

	/** The texture corners and sizes of the boxes, as the painter lays them out ({@code BEAM_BOXES} in {@code tools/gen_chainsaw_textures.py}). */
	private static int[] corner(String name) {
		return switch (name) {
			case "trunk_core" -> new int[] {0, 0, 32, 22, 26};
			case "trunk_top" -> new int[] {0, 48, 26, 6, 26};
			case "trunk_bot" -> new int[] {116, 0, 24, 7, 26};
			case "h1_core" -> new int[] {166, 48, 32, 18, 12};
			case "h1_top" -> new int[] {108, 107, 26, 6, 12};
			case "h2_core" -> new int[] {0, 80, 28, 18, 9};
			case "h2_top" -> new int[] {158, 127, 22, 3, 9};
			case "h3_core" -> new int[] {164, 80, 22, 14, 6};
			case "h3_top" -> new int[] {76, 143, 16, 2, 6};
			case "h4" -> new int[] {60, 127, 14, 10, 5};
			case "jaw1" -> new int[] {74, 80, 26, 11, 12};
			case "jaw2" -> new int[] {0, 107, 20, 10, 10};
			case "jaw3" -> new int[] {204, 107, 14, 8, 8};
			case "eyeA" -> new int[] {220, 127, 1, 6, 6};
			case "eyeB" -> new int[] {16, 143, 1, 5, 5};
			case "eyeC" -> new int[] {120, 143, 1, 4, 4};
			case "rear" -> new int[] {104, 48, 22, 22, 9};
			case "stalk" -> new int[] {74, 107, 12, 14, 5};
			case "tail_base" -> new int[] {0, 127, 2, 12, 4};
			case "tail_up1" -> new int[] {234, 127, 2, 8, 4};
			case "tail_up2" -> new int[] {28, 143, 2, 6, 4};
			case "tail_low" -> new int[] {52, 143, 2, 6, 3};
			case "leg_up" -> new int[] {162, 143, 16, 2, 2};
			case "leg_shin" -> new int[] {184, 107, 2, 16, 2};
			case "leg_mid" -> new int[] {130, 143, 2, 6, 2};
			case "leg_point" -> new int[] {62, 143, 1, 8, 1};
			case "pect" -> new int[] {138, 143, 8, 1, 4};
			case "pect_tip" -> new int[] {198, 143, 4, 1, 3};
			case "spike0" -> new int[] {150, 80, 0, 15, 7};
			case "spike1" -> new int[] {60, 107, 0, 13, 7};
			case "spike2" -> new int[] {192, 107, 0, 11, 6};
			case "spike3" -> new int[] {98, 127, 0, 9, 6};
			case "spike4" -> new int[] {130, 127, 0, 8, 5};
			case "teeth_h1" -> new int[] {12, 127, 0, 4, 12};
			case "teeth_h2" -> new int[] {140, 127, 0, 4, 9};
			case "teeth_h3" -> new int[] {40, 143, 0, 4, 6};
			case "teeth_h4" -> new int[] {66, 143, 0, 4, 5};
			case "teeth_j1" -> new int[] {36, 127, 0, 4, 12};
			case "teeth_j2" -> new int[] {110, 127, 0, 4, 10};
			case "teeth_j3" -> new int[] {0, 143, 0, 4, 8};
			case "teeth_front_up" -> new int[] {212, 143, 12, 4, 0};
			case "teeth_front_low" -> new int[] {0, 155, 12, 4, 0};
			default -> throw new IllegalArgumentException(name);
		};
	}

	private static ModelPartBuilder box(String name, float x, float y, float z) {
		return box(name, x, y, z, false);
	}

	/** A box of {@code name}'s size at its texture corner, at {@code x}, {@code y}, {@code z}; {@code mirrored} turns its texture round (a left part). */
	private static ModelPartBuilder box(String name, float x, float y, float z, boolean mirrored) {
		int[] c = corner(name);
		return ModelPartBuilder.create().uv(c[0], c[1]).mirrored(mirrored).cuboid(x, y, z, c[2], c[3], c[4]);
	}

	/** A flat piece (no thickness: a spike, a row of teeth) of {@code name}'s size, its texture's two sides painted alike. */
	private static ModelPartBuilder plane(String name, float x, float y, float z) {
		return box(name, x, y, z);
	}

	/** An eye: a thin box standing out of his side at {@code x} (the right one's; the left one is it mirrored, texture turned round). */
	private static ModelPartBuilder eye(String name, boolean left, float x, float y, float z) {
		return box(name, left ? -x - 1.0F : x, y, z, left);
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
			float lift = LIFT * lifting[i] + 0.5F * air;
			// The upper part: angled up from the side (the left one the other way round); yawed forward or back, swinging.
			float yaw = (front ? LEG_SPLAY : -LEG_SPLAY) + STRIDE_HIP * forward[i] * (1.0F - air);
			hips[i].yaw = side * yaw;
			float up = LEG_UP + lift;
			hips[i].roll = -side * up;
			// The shin: hangs from the knee, angled in at the foot (so it keeps its slant however the knee lifts); swinging
			// forward and back with the stride.
			knees[i].roll = side * (up + SHIN_IN);
			knees[i].pitch = -STRIDE_KNEE * forward[i] * (1.0F - air) - 0.3F * air;
		}
		// The body rocks a little with his steps and rises and falls with them.
		trunk.roll = MathHelper.sin(walk) * 0.02F * pace;
		trunk.pivotY = -Math.abs(MathHelper.sin(walk)) * 1.0F * pace;
		float sway = 0.35F + 0.65F * pace;
		trunk.yaw = MathHelper.sin(time) * 0.025F * sway;
		rear.yaw = MathHelper.sin(time - 0.9F) * 0.12F * sway;
		stalk.yaw = MathHelper.sin(time - 1.8F) * 0.2F * sway;
		tail.yaw = MathHelper.sin(time - 2.7F) * 0.28F * sway;
		stalk.pitch = 0.15F;
		// The bite: the jaw opens wide, snaps shut and falls back open; the head lifts with it.
		float open = JAW_REST + 0.05F * MathHelper.sin(time * 0.5F);
		float lift = 0.0F;
		if (bite < 9.0F) {
			if (bite < 1.5F) {
				float k = bite / 1.5F;
				open = MathHelper.lerp(k, JAW_REST, JAW_WIDE);
				lift = k * 0.2F;
			} else if (bite < 3.5F) {
				float k = (bite - 1.5F) / 2.0F;
				open = MathHelper.lerp(k, JAW_WIDE, 0.03F);
				lift = MathHelper.lerp(k, 0.2F, 0.08F);
			} else {
				float k = MathHelper.clamp((bite - 3.5F) / 5.5F, 0.0F, 1.0F);
				open = MathHelper.lerp(k, 0.03F, open);
				lift = MathHelper.lerp(k, 0.08F, 0.0F);
			}
		}
		jaw.pitch = open;
		head.pitch = -lift - 0.1F * air;
		rightPectoral.roll = PECTORAL_DOWN + MathHelper.sin(time + 1.0F) * 0.06F;
		leftPectoral.roll = -PECTORAL_DOWN - MathHelper.sin(time + 1.0F) * 0.06F;
		rightPectoral.yaw = -PECTORAL_BACK;
		leftPectoral.yaw = PECTORAL_BACK;
	}

	public void render(MatrixStack matrices, VertexConsumer vertices, int light) {
		root.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
	}
}
