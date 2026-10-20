package io.github.kortev.chainsawman.client;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Getting up off the ground, as a body does, not tipped up like a plank: from lying on your back (the end of the merge)
 * you sit up with your legs out in front, rock forward into a crouch over your feet and stand; from lying on your front
 * (back up after being downed) you push yourself up on your hands, get a foot under you and stand, your arms and legs
 * growing back out of their stumps as you go.
 *
 * <p>A pose here is where the hips are and how far the body is tipped about them (the renderer's space, after the
 * body's turn: y up from the feet, +z behind, blocks; tipped back positive, in degrees), and how the limbs are held
 * (the model's angles, radians: a limb pitched -π/2 points forward). Each way up is a few such poses, eased between.
 */
public final class GetUp {
	/** How high the hips are standing: the legs' length, the player model being 15/16 of a block of pixels. */
	private static final float HIPS = 12.0F / 16.0F * 0.9375F;

	/**
	 * One pose: the hips' height and how far behind the feet they are; the tip about them; each leg's pitch; each arm's
	 * pitch and roll; the head's pitch.
	 */
	private record Frame(float hipY, float hipZ, float tilt, float rightLeg, float leftLeg, float rightArm, float leftArm, float rightRoll,
			float leftRoll, float head) {
		Frame lerp(Frame to, float k) {
			return new Frame(MathHelper.lerp(k, hipY, to.hipY), MathHelper.lerp(k, hipZ, to.hipZ), MathHelper.lerp(k, tilt, to.tilt),
					MathHelper.lerp(k, rightLeg, to.rightLeg), MathHelper.lerp(k, leftLeg, to.leftLeg), MathHelper.lerp(k, rightArm, to.rightArm),
					MathHelper.lerp(k, leftArm, to.leftArm), MathHelper.lerp(k, rightRoll, to.rightRoll), MathHelper.lerp(k, leftRoll, to.leftRoll),
					MathHelper.lerp(k, head, to.head));
		}
	}

	/** Up from your back: lying (as the merge lays you), sat up with legs out, crouched over your feet, standing. */
	private static final float[] BACK_AT = {0.0F, 0.38F, 0.7F, 1.0F};
	private static final Frame[] FROM_BACK = {
			new Frame(0.15F, HIPS, 90.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.45F, -0.45F, 0.0F),
			new Frame(0.12F, HIPS, 12.0F, -1.3F, -1.38F, 0.55F, 0.5F, 0.25F, -0.25F, 0.35F),
			new Frame(0.4F, 0.16F, -32.0F, 0.31F, -1.52F, -0.95F, -0.35F, 0.1F, -0.35F, 0.25F),
			new Frame(HIPS, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.05F, -0.05F, 0.0F)};
	/** Up from your front: face down (as the game lays the downed), pushed up on your hands, a foot under you, standing. */
	private static final float[] FRONT_AT = {0.0F, 0.35F, 0.68F, 1.0F};
	private static final Frame[] FROM_FRONT = {
			new Frame(0.3F, 0.3F, -90.0F, 0.0F, 0.0F, -2.6F, -2.6F, 0.2F, -0.2F, -0.9F),
			new Frame(0.2F, 0.3F, -55.0F, 0.6F, 0.65F, -0.96F, -0.96F, 0.05F, -0.05F, -0.5F),
			new Frame(0.4F, 0.14F, -30.0F, 0.4F, -1.55F, -0.9F, -0.25F, 0.1F, -0.4F, -0.1F),
			new Frame(HIPS, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.05F, -0.05F, 0.0F)};

	private GetUp() {
	}

	private static Frame at(boolean fromBack, float k) {
		float[] at = fromBack ? BACK_AT : FRONT_AT;
		Frame[] frames = fromBack ? FROM_BACK : FROM_FRONT;
		k = MathHelper.clamp(k, 0.0F, 1.0F);
		for (int i = 1; i < at.length; i++) {
			if (k <= at[i]) {
				return frames[i - 1].lerp(frames[i], ease((k - at[i - 1]) / (at[i] - at[i - 1])));
			}
		}
		return frames[frames.length - 1];
	}

	/** Moves the whole body {@code k} of the way up (in the renderer's transforms, after the body's turn). */
	public static void root(MatrixStack matrices, boolean fromBack, float k) {
		Frame f = at(fromBack, k);
		matrices.translate(0.0F, f.hipY, f.hipZ);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f.tilt));
		matrices.translate(0.0F, -HIPS, 0.0F);
	}

	/** Holds the limbs {@code k} of the way up; the head ends pitched {@code headEnd} (looking down at the cord, say). */
	public static void limbs(BipedEntityModel<?> model, boolean fromBack, float k, float headEnd) {
		Frame f = at(fromBack, k);
		model.rightLeg.pitch = f.rightLeg;
		model.leftLeg.pitch = f.leftLeg;
		model.rightLeg.yaw = 0.0F;
		model.leftLeg.yaw = 0.0F;
		model.rightLeg.roll = 0.04F;
		model.leftLeg.roll = -0.04F;
		model.rightArm.pitch = f.rightArm;
		model.leftArm.pitch = f.leftArm;
		model.rightArm.yaw = 0.0F;
		model.leftArm.yaw = 0.0F;
		model.rightArm.roll = f.rightRoll;
		model.leftArm.roll = f.leftRoll;
		model.head.pitch = f.head + headEnd * ease((k - 0.7F) / 0.3F);
		model.head.yaw = 0.0F;
		model.body.pitch = 0.0F;
		model.hat.copyTransform(model.head);
	}

	/**
	 * How far grown back an arm or leg is, {@code k} of the way up from being downed: out of its stump in the first third,
	 * a little past its length and settling.
	 */
	public static float regrown(float k) {
		float g = MathHelper.clamp(k / 0.3F, 0.0F, 1.0F);
		return g >= 1.0F ? 1.0F : 0.05F + 0.95F * (1.0F - (1.0F - g) * (1.0F - g)) + 0.08F * MathHelper.sin(g * MathHelper.PI);
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
