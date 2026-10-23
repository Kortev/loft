package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.BeamEntity;
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
 * dropped from that corner over a big round chin, teeth along both lips, three round eyes up each side (each its own part, so
 * it can look about), five spikes down his
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
	/** His three eyes a side (the front, biggest one first), right then left: each is a part of its own, hinged at its middle, so it can roll. */
	private final ModelPart[][] eyes = new ModelPart[2][3];
	private final float[][] eyeBase = new float[2][3];
	/** The four legs, front right, front left, back right, back left: the hip (turned by the stride), the upper blade, the shin. */
	private final ModelPart[] hips = new ModelPart[4];
	private final ModelPart[] uppers = new ModelPart[4];
	private final ModelPart[] shins = new ModelPart[4];

	/** How far the mouth is open when he is not biting (radians, from shut), and open wide for a bite. */
	private static final float OPEN_REST = BeamLayout.MOUTH_OPEN;
	private static final float OPEN_WIDE = BeamLayout.MOUTH_OPEN + 0.42F;
	/** A stride: how far a leg swings at its hip (radians), and how far it lifts (a roll at the hip). */
	private static final float STRIDE_HIP = 0.55F;
	private static final float LIFT = 0.8F;

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
		for (int i = 0; i < 3; i++) {
			ModelPart parent = i == 0 ? head : trunk;
			eyes[0][i] = parent.getChild("eye_" + i + "_r");
			eyes[1][i] = parent.getChild("eye_" + i + "_l");
			eyeBase[0][i] = eyes[0][i].pivotZ;
			eyeBase[1][i] = eyes[1][i].pivotZ;
		}
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
	 * Poses him from what the entity says of himself, {@code d} of the way through the tick. Standing, he walks on his legs
	 * (diagonal pairs), wags his tail, darts his eyes, chatters his jaw and turns his head to what he notices; swimming, his legs
	 * fold up and his body ripples in an S, the tail swinging most, the faster the more; summoned, he thrashes for joy and turns
	 * his head back to look at the rider and screeches; after a kill he wiggles and chomps; and the bite is his jaw's.
	 */
	public void animate(BeamEntity e, float d) {
		float walk = e.walk(d);
		float time = e.time(d);
		float pace = e.pace();
		float swim = e.swim(d);
		float air = e.air(d);
		float fold = Math.max(swim, air);
		float bite = e.sinceBite(d);
		float lived = e.lived(d);
		// His gait: the diagonal pairs (front right with back left) stepping together. A leg's "forward" is positive.
		float[] forward = new float[4];
		float[] lifting = new float[4];
		float[] phases = {0.0F, MathHelper.PI, MathHelper.PI, 0.0F};
		float walking = e.gait() * (1.0F - fold);
		for (int i = 0; i < 4; i++) {
			float p = walk + phases[i];
			forward[i] = MathHelper.sin(p) * walking;
			lifting[i] = Math.max(0.0F, MathHelper.cos(p)) * walking;
		}
		for (int i = 0; i < 4; i++) {
			float side = i % 2 == 0 ? 1.0F : -1.0F;
			// The legs stand as drawn (the parts' own turns); the hip swings the whole leg about the vertical through it, and
			// rolls it up to lift the foot (the right-hand legs one way, the left the other). Swimming or in the air they are drawn up.
			hips[i].yaw = side * STRIDE_HIP * forward[i];
			hips[i].roll = -side * (LIFT * lifting[i] + 0.8F * fold);
			shins[i].pitch = BeamLayout.SHIN_REST[i][0] - 0.6F * fold;
		}
		// The body is lively on his legs: it bounces with every step (twice a cycle, up as each pair pushes off), rocks from side to side
		// and rolls its shoulders, and dips its nose with each landing.
		float bounce = Math.abs(MathHelper.sin(walk));
		trunk.roll = MathHelper.sin(walk) * 0.045F * walking;
		trunk.pivotY = -(bounce * 2.6F - 0.5F) * walking;
		trunk.pitch = MathHelper.sin(walk * 2.0F + 0.6F) * 0.025F * walking;
		// Swimming: an S travelling down him, the tail swinging most; it grows with his speed.
		float ripple = swim * (0.3F + 0.7F * pace);
		float trunkYaw = MathHelper.sin(time) * 0.08F * ripple;
		float rearYaw = MathHelper.sin(time - 0.9F) * 0.28F * ripple;
		float stalkYaw = MathHelper.sin(time - 1.8F) * 0.45F * ripple;
		float tailYaw = MathHelper.sin(time - 2.7F) * 0.7F * ripple;
		trunk.roll += MathHelper.sin(time + 0.5F) * 0.05F * ripple;
		// Scuttling: his hindquarters swing against his shoulders with each step and the tail whips along behind, a beat late.
		trunkYaw += walking * 0.05F * MathHelper.sin(walk + 0.4F);
		rearYaw += walking * 0.16F * MathHelper.sin(walk - 0.4F);
		stalkYaw += walking * 0.28F * MathHelper.sin(walk - 1.2F);
		tailYaw += walking * 0.42F * MathHelper.sin(walk - 2.0F);
		// Standing about: the tail wags, like a dog's, all of the rear of him swinging.
		float wag = e.wag(d);
		float glad = (1.0F - swim) * (1.0F - air) * (1.0F - MathHelper.clamp(e.gait() * 3.0F, 0.0F, 1.0F));
		trunkYaw += glad * 0.03F * MathHelper.sin(wag * 0.5F);
		rearYaw += glad * 0.1F * MathHelper.sin(wag - 0.5F);
		stalkYaw += glad * 0.25F * MathHelper.sin(wag - 1.0F);
		tailYaw += glad * 0.5F * MathHelper.sin(wag - 1.5F);
		// The summoning: he thrashes for joy, then curls round to look back at the rider on him, and screeches.
		float thrash = MathHelper.clamp(1.0F - lived / 34.0F, 0.0F, 1.0F);
		float back = smooth(lived, 6.0F, 12.0F) * (1.0F - smooth(lived, 23.0F, 30.0F));
		trunkYaw += thrash * 0.22F * MathHelper.sin(lived * 0.85F) + back * 0.15F;
		rearYaw += thrash * 0.3F * MathHelper.sin(lived * 0.85F - 0.8F) + back * 0.12F;
		stalkYaw += thrash * 0.5F * MathHelper.sin(lived * 0.85F - 1.6F) + back * 0.05F;
		tailYaw += thrash * 0.8F * MathHelper.sin(lived * 0.85F - 2.4F);
		// Pleased with a kill: a wiggle.
		float pleased = e.sinceHappy(d);
		float wiggle = pleased < 28.0F ? 1.0F - pleased / 28.0F : 0.0F;
		trunkYaw += wiggle * 0.12F * MathHelper.sin(pleased * 1.2F);
		rearYaw += wiggle * 0.22F * MathHelper.sin(pleased * 1.2F - 0.8F);
		stalkYaw += wiggle * 0.35F * MathHelper.sin(pleased * 1.2F - 1.6F);
		tailYaw += wiggle * 0.6F * MathHelper.sin(pleased * 1.2F - 2.4F);
		// Holding something in his jaws: his head whips side to side and the rest of him follows against it, the tail last.
		float grab = e.sinceGrab(d);
		float gripped = grab >= 0.0F && grab <= BeamEntity.GRAB_TICKS ? 1.0F : 0.0F;
		float swing = BeamEntity.shake(grab) / 0.8F;
		if (gripped > 0.0F) {
			trunkYaw -= 0.12F * swing;
			rearYaw -= 0.5F * BeamEntity.shake(grab - 1.5F) / 0.8F;
			stalkYaw -= 0.75F * BeamEntity.shake(grab - 3.0F) / 0.8F;
			tailYaw -= 1.0F * BeamEntity.shake(grab - 4.5F) / 0.8F;
		}
		// Bursting up under a mob, and throwing his rider: the tail cracks like a whip behind the head.
		float ambush = e.sinceAmbush(d);
		float launch = e.sinceLaunch(d);
		float cracked = Math.max(ambush < 16.0F ? 1.0F - ambush / 16.0F : 0.0F, launch < 12.0F ? 1.0F - launch / 12.0F : 0.0F);
		tailYaw += cracked * 0.9F * MathHelper.sin(Math.min(ambush, launch) * 1.1F - 2.4F);
		stalkYaw += cracked * 0.5F * MathHelper.sin(Math.min(ambush, launch) * 1.1F - 1.6F);
		trunk.yaw = trunkYaw;
		rear.yaw = rearYaw;
		stalk.yaw = stalkYaw;
		tail.yaw = tailYaw;
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
		// His teeth chatter: a quick run of snaps, shut and open, when he stands about.
		float chatter = e.chattering(d);
		if (chatter >= 0.0F) {
			float snap = 0.5F + 0.5F * MathHelper.sin(chatter * MathHelper.PI * 2.0F * 5.0F - MathHelper.HALF_PI);
			open = MathHelper.lerp(snap, 0.04F, OPEN_REST + 0.1F);
			lift += 0.03F * snap;
		}
		// A happy chomp or two after a kill; the screech of joy at the summoning, the jaw flung wide and shaking.
		if (pleased < 12.0F) {
			float snap = 0.5F + 0.5F * MathHelper.sin(pleased - MathHelper.HALF_PI);
			open = MathHelper.lerp(snap, 0.03F, OPEN_REST + 0.2F);
		}
		float screech = smooth(lived, 6.0F, 8.0F) * (1.0F - smooth(lived, 17.0F, 20.0F));
		if (screech > 0.0F) {
			open = MathHelper.lerp(screech, open, OPEN_WIDE + 0.15F + 0.07F * MathHelper.sin(lived * 2.6F));
			lift += screech * 0.1F;
		}
		// The grab: the jaws shut on it, gripping, and thrown open when it is flung; the ambush: flung wide as he bursts up, snapping shut
		// at the top; the throw: the head flicks up and snaps down, the jaw open for it.
		float flung = grab > BeamEntity.GRAB_TICKS ? Math.max(0.0F, 1.0F - (grab - BeamEntity.GRAB_TICKS) / 9.0F) : 0.0F;
		if (gripped > 0.0F) {
			open = 0.07F + 0.03F * MathHelper.sin(grab * 1.7F);
			lift += 0.12F;
		}
		if (flung > 0.0F) {
			open = MathHelper.lerp(flung, open, OPEN_WIDE + 0.3F);
			lift += 0.35F * flung;
		}
		if (ambush < 20.0F) {
			float wide = 1.0F - smooth(ambush, 11.0F, 14.0F);
			float snap = smooth(ambush, 11.0F, 13.0F) * (1.0F - smooth(ambush, 14.0F, 20.0F));
			open = MathHelper.lerp(wide, MathHelper.lerp(snap, open, 0.03F), OPEN_WIDE + 0.32F + 0.05F * MathHelper.sin(ambush * 2.4F));
			lift += 0.4F * wide;
		}
		if (launch < 12.0F) {
			float up = smooth(launch, 0.0F, 2.5F) * (1.0F - smooth(launch, 3.0F, 5.0F));
			float down = smooth(launch, 3.0F, 5.0F) * (1.0F - smooth(launch, 6.0F, 12.0F));
			lift += 0.65F * up - 0.35F * down;
			open = MathHelper.lerp(up, MathHelper.lerp(down, open, 0.03F), OPEN_WIDE + 0.25F);
		}
		// The jaw is built as drawn (open by MOUTH_OPEN, its frame turned along the rim by JAW_REST): shut is MOUTH_OPEN less.
		jaw.pitch = BeamLayout.JAW_REST - BeamLayout.MOUTH_OPEN + open;
		// His head: it looks where he is looking (a mob he has noticed; the rider, at the summoning), nodding with his porpoise.
		float nod = 0.1F * MathHelper.sin(time * 0.5F - 0.6F) * swim * pace;
		// (On his legs it bobs with every step, and sways from side to side a little as he looks about.)
		float bob = 0.09F * MathHelper.sin(walk * 2.0F - 0.5F) * walking;
		float sway = 0.1F * MathHelper.sin(walk * 0.5F + 1.0F) * walking;
		head.pitch = -lift - 0.08F * air - e.lookPitch(d) - back * 0.85F + nod + bob;
		head.yaw = -trunkYaw * 0.7F + e.lookYaw(d) + back * 0.45F + BeamEntity.shake(grab) + sway;
		// His eyes dart.
		for (int i = 0; i < 3; i++) {
			float yaw = e.eyeYaw(i, d);
			float pitch = e.eyePitch(i, d);
			for (int s = 0; s < 2; s++) {
				float flip = s == 0 ? 1.0F : -1.0F;
				// (Turned about the vertical to look forward or back, tilted to look up or down, slid a hair with it.)
				eyes[s][i].yaw = flip * yaw * 0.7F;
				eyes[s][i].roll = flip * pitch * 0.7F;
				eyes[s][i].pivotZ = eyeBase[s][i] - yaw * 1.2F;
			}
		}
		rightPectoral.roll = BeamLayout.PECTORAL_ROLL + MathHelper.sin(time + 1.0F) * (0.06F + 0.1F * swim) + thrash * 0.4F * MathHelper.sin(lived * 1.3F);
		leftPectoral.roll = -BeamLayout.PECTORAL_ROLL - MathHelper.sin(time + 1.0F) * (0.06F + 0.1F * swim) - thrash * 0.4F * MathHelper.sin(lived * 1.3F);
	}

	/** 0 before {@code from}, 1 after {@code to}, eased between. */
	private static float smooth(float t, float from, float to) {
		float k = MathHelper.clamp((t - from) / (to - from), 0.0F, 1.0F);
		return k * k * (3.0F - 2.0F * k);
	}

	public void render(MatrixStack matrices, VertexConsumer vertices, int light) {
		root.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
	}
}
