package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.BeamEntity;
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
 * Beam the shark in Minecraft's own way of making animals, like Pochita: boxes, painted pixel by pixel
 * ({@code tools/gen_chainsaw_textures.py}, {@code beam_entity}, lays the texture out for exactly these boxes and their
 * texture corners; change one, change the other). A big shark, about 3.5 blocks long and 1.2 high: a trunk, the head in
 * front of it with a blunt snout over a lower jaw that drops to bite (white teeth in rows along both jaws, the mouth dark
 * red), a tall dorsal fin swept back, two pectoral fins, and behind the trunk the rear of the body, the tail's stalk and a
 * crescent tail fin, each hinged to the one before so the body undulates as he swims.
 *
 * <p>Pixels in the model's space: y down, the top of his back at 0 (the renderer puts that {@link BeamEntity#BACK} above
 * where he is, so the rest of him is under the surface), his front toward -z.
 */
public class BeamModel {
	private final ModelPart root;
	/** The hinges: the body's front half, the rear, the tail's stalk, the tail fin, the head and its lower jaw, the pectorals. */
	private final ModelPart front;
	private final ModelPart rear;
	private final ModelPart stalk;
	private final ModelPart tail;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart rightPectoral;
	private final ModelPart leftPectoral;

	/** How far the jaw hangs open when he is not biting (radians), and open wide. */
	private static final float JAW_REST = 0.1F;
	private static final float JAW_WIDE = 0.85F;
	/** The pectorals' angle down and out of his sides, and their sweep back (radians). */
	private static final float PECTORAL_DOWN = 0.5F;
	private static final float PECTORAL_BACK = 0.35F;

	public BeamModel(ModelPart root) {
		this.root = root;
		this.front = root.getChild("front");
		this.head = front.getChild("head_group");
		this.jaw = head.getChild("jaw");
		this.rightPectoral = front.getChild("right_pectoral");
		this.leftPectoral = front.getChild("left_pectoral");
		this.rear = root.getChild("rear");
		this.stalk = rear.getChild("stalk");
		this.tail = stalk.getChild("tail");
	}

	public static TexturedModelData texturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		// The front half: the trunk, the dorsal fin, the pectorals, and the head.
		ModelPartData front = root.addChild("front", box(0, 0, -7.0F, 0.0F, -7.0F, 14, 17, 14), ModelTransform.NONE);
		// The dorsal fin, swept back in three steps, its back edge straight.
		front.addChild("dorsal0", box(40, 31, -1.0F, -7.0F, -1.0F, 2, 8, 10), ModelTransform.NONE);
		front.addChild("dorsal1", box(100, 13, -1.0F, -12.0F, 2.0F, 2, 5, 7), ModelTransform.NONE);
		front.addChild("dorsal2", box(88, 53, -1.0F, -15.0F, 5.0F, 2, 3, 4), ModelTransform.NONE);
		// The head hinged where it meets the trunk, so it can lift to bite: the head, the snout, the lower jaw (hinged
		// at the back of the mouth) and the teeth along both jaws.
		ModelPartData head = front.addChild("head_group", ModelPartBuilder.create(), ModelTransform.pivot(0.0F, 9.0F, -7.0F));
		head.addChild("head", box(56, 0, -6.0F, -9.0F, -10.0F, 12, 15, 10), ModelTransform.NONE);
		head.addChild("snout", box(64, 31, -4.0F, -8.0F, -18.0F, 8, 8, 8), ModelTransform.NONE);
		ModelPartData jaw = head.addChild("jaw", box(0, 53, -4.0F, 0.0F, -8.0F, 8, 3, 8), ModelTransform.pivot(0.0F, 0.0F, -10.0F));
		// Upper teeth: along the snout's front and down both sides of its roof; lower: along the jaw.
		for (float x : new float[] {-3.5F, -1.5F, 0.5F, 2.5F}) {
			head.addChild("upper_front_" + x, tooth(x, 0.0F, -18.0F), ModelTransform.NONE);
			jaw.addChild("lower_front_" + x, tooth(x, -2.0F, -8.0F), ModelTransform.NONE);
		}
		for (float side : new float[] {-1.0F, 1.0F}) {
			float x = side * 3.5F - 0.5F;
			for (float z : new float[] {-16.0F, -13.0F}) {
				head.addChild("upper_" + side + "_" + z, tooth(x, 0.0F, z), ModelTransform.NONE);
			}
			for (float z : new float[] {-6.0F, -3.0F}) {
				jaw.addChild("lower_" + side + "_" + z, tooth(x, -2.0F, z), ModelTransform.NONE);
			}
		}
		// The pectoral fins, down and out of his sides and swept back, a smaller piece at each tip.
		ModelPartData right = front.addChild("right_pectoral", box(32, 53, 0.0F, 0.0F, 0.0F, 8, 1, 7),
				ModelTransform.pivot(7.0F, 12.0F, -3.0F));
		right.addChild("right_tip", box(56, 25, 0.0F, 0.0F, 3.0F, 4, 1, 4), ModelTransform.pivot(8.0F, 0.0F, 0.0F));
		ModelPartData left = front.addChild("left_pectoral", box(32, 53, -8.0F, 0.0F, 0.0F, 8, 1, 7),
				ModelTransform.pivot(-7.0F, 12.0F, -3.0F));
		left.addChild("left_tip", box(56, 25, -4.0F, 0.0F, 3.0F, 4, 1, 4), ModelTransform.pivot(-8.0F, 0.0F, 0.0F));
		// The rear of the body, hinged at the back of the trunk, with its small second dorsal fin; then the stalk, then the
		// crescent tail fin: a base, the long upper lobe in two steps and the shorter lower lobe.
		ModelPartData rear = root.addChild("rear", box(0, 31, -5.5F, 1.0F, 0.0F, 11, 13, 9), ModelTransform.pivot(0.0F, 0.0F, 7.0F));
		rear.addChild("fin2", box(72, 25, -0.5F, -2.0F, 3.0F, 1, 3, 3), ModelTransform.NONE);
		ModelPartData stalk = rear.addChild("stalk", box(96, 31, -2.5F, 4.0F, 0.0F, 5, 7, 7), ModelTransform.pivot(0.0F, 0.0F, 9.0F));
		ModelPartData tail = stalk.addChild("tail", box(100, 0, -1.0F, 3.0F, 0.0F, 2, 8, 5), ModelTransform.pivot(0.0F, 0.0F, 7.0F));
		tail.addChild("tail_up1", box(114, 0, -1.0F, -2.0F, 2.0F, 2, 5, 5), ModelTransform.NONE);
		tail.addChild("tail_up2", box(62, 53, -1.0F, -7.0F, 4.0F, 2, 5, 5), ModelTransform.NONE);
		tail.addChild("tail_low", box(76, 53, -1.0F, 11.0F, 1.0F, 2, 4, 4), ModelTransform.NONE);
		return TexturedModelData.of(data, 128, 64);
	}

	private static ModelPartBuilder box(int u, int v, float x, float y, float z, int w, int h, int d) {
		return ModelPartBuilder.create().uv(u, v).cuboid(x, y, z, w, h, d);
	}

	/** A tooth: one pixel wide and deep and two tall, hanging from {@code y} (or rising to it: pass the top). */
	private static ModelPartBuilder tooth(float x, float y, float z) {
		return box(80, 25, x, y, z, 1, 2, 1);
	}

	/**
	 * Poses him: the body undulating as he swims ({@code swim} the phase, {@code speed} how fast he's going, 0 to 1), the
	 * jaw dropping and the head lifting as he bites ({@code bite}: ticks since his last bite).
	 */
	public void animate(float swim, float speed, float bite) {
		float sway = 0.35F + 0.65F * speed;
		front.yaw = MathHelper.sin(swim) * 0.04F * sway;
		rear.yaw = MathHelper.sin(swim - 0.9F) * 0.16F * sway;
		stalk.yaw = MathHelper.sin(swim - 1.8F) * 0.26F * sway;
		tail.yaw = MathHelper.sin(swim - 2.7F) * 0.34F * sway;
		// The tail fin beats a little from side to side only; the body rolls ever so slightly with it.
		front.roll = MathHelper.sin(swim - 0.4F) * 0.02F * sway;
		// The bite: the jaw opens fast, snaps shut; the head lifts with it.
		float open = JAW_REST;
		float lift = 0.0F;
		if (bite < 6.0F) {
			float k = bite < 2.0F ? bite / 2.0F : 1.0F - (bite - 2.0F) / 4.0F;
			open = MathHelper.lerp(MathHelper.clamp(k, 0.0F, 1.0F), JAW_REST, JAW_WIDE);
			lift = MathHelper.clamp(k, 0.0F, 1.0F) * 0.32F;
		} else {
			open += 0.04F * MathHelper.sin(swim * 0.5F);
		}
		jaw.pitch = open;
		head.pitch = -lift;
		rightPectoral.roll = PECTORAL_DOWN + MathHelper.sin(swim + 1.0F) * 0.06F;
		leftPectoral.roll = -PECTORAL_DOWN - MathHelper.sin(swim + 1.0F) * 0.06F;
		rightPectoral.yaw = -PECTORAL_BACK;
		leftPectoral.yaw = PECTORAL_BACK;
	}

	public void render(MatrixStack matrices, VertexConsumer vertices, int light) {
		root.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
	}
}
