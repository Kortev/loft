package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.form.FormTimeline;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * The body through the transformation, as everyone sees it ({@link FormTimeline}): the right hand comes up to the cord
 * on the chest (REACH) and yanks it out in one long arc up and away from the body, the head snapping back (YANK); the
 * arm hangs out there as the engine catches and coughs; at the burst it drops and both arms brace out from the sides as
 * the saws push out through the fists; at the roar the arms are flung down and wide and the head thrown back; then it
 * all settles (END).
 *
 * <p>Angles are radians. A hanging arm turns outward with a positive roll on the right and a negative on the left; a
 * raised arm swings out from the body with a positive yaw on the right (a roll would swing it inward).
 */
public final class CordPull {
	/** The hand at the cord: forward and across the chest. */
	private static final float REACH_PITCH = -1.15F;
	private static final float REACH_YAW = -0.55F;
	/** The yank's end: high, out to the side. */
	private static final float YANK_PITCH = -2.15F;
	private static final float YANK_YAW = 1.05F;
	private static final float HEAD_SNAP = -0.45F;
	/** The arms braced out as the saws come through. */
	private static final float BRACE_ROLL = 0.32F;
	private static final float BRACE_PITCH = -0.25F;
	/** The roar: arms flung down and wide, the head thrown back. */
	private static final float ROAR_ROLL = 0.95F;
	private static final float ROAR_PITCH = -0.35F;
	private static final float ROAR_HEAD = -0.6F;

	private CordPull() {
	}

	/**
	 * Lying dead through the merge ({@code MergeTimeline}): limp, arms flung out, head fallen to one side; each beat of
	 * the heart jerks the fingers and the head; at the fourth the right hand comes up to the cord on the chest, and holds
	 * it as you rise.
	 */
	public static void lie(BipedEntityModel<?> model, float t) {
		int[] beats = io.github.kortev.chainsawman.form.MergeTimeline.BEATS;
		float jerk = 0.0F;
		for (int beat : beats) {
			jerk = Math.max(jerk, t >= beat ? (float) Math.exp(-(t - beat) / 1.5) : 0.0F);
		}
		float stand = ease((t - io.github.kortev.chainsawman.form.MergeTimeline.RISE)
				/ (io.github.kortev.chainsawman.form.MergeTimeline.END - io.github.kortev.chainsawman.form.MergeTimeline.RISE));
		float reach = ease((t - beats[3]) / 5.0F) * (1.0F - stand);
		model.rightArm.pitch = REACH_PITCH * reach;
		model.rightArm.yaw = REACH_YAW * reach;
		model.rightArm.roll = (0.45F - 0.15F * jerk) * (1.0F - reach) * (1.0F - stand) + 0.06F * stand;
		model.leftArm.pitch = -0.15F * jerk;
		model.leftArm.yaw = 0.0F;
		model.leftArm.roll = (-0.45F + 0.15F * jerk) * (1.0F - stand) - 0.06F * stand;
		model.rightLeg.pitch = 0.0F;
		model.leftLeg.pitch = 0.0F;
		model.rightLeg.roll = 0.08F * (1.0F - stand);
		model.leftLeg.roll = -0.08F * (1.0F - stand);
		// Fallen to one side; turned up to him as he talks.
		model.head.yaw = 0.55F * (1.0F - ease((t - io.github.kortev.chainsawman.form.MergeTimeline.TALK) / 12.0F));
		model.head.pitch = -0.25F * jerk + 0.4F * stand;
		model.hat.copyTransform(model.head);
	}

	/** Poses the arms and head {@code t} ticks after the pull. */
	public static void pose(BipedEntityModel<?> model, float t) {
		float rPitch = 0.0F;
		float rYaw = 0.0F;
		float rRoll = 0.0F;
		float lPitch = 0.0F;
		float lRoll = 0.0F;
		float head = 0.0F;
		if (t < FormTimeline.REACH) {
			float k = ease(t / FormTimeline.REACH);
			rPitch = REACH_PITCH * k;
			rYaw = REACH_YAW * k;
		} else if (t < FormTimeline.YANK) {
			// Slow out of the chest, then whipping out: the pull's weight is at its start.
			float k = (float) Math.pow((t - FormTimeline.REACH) / (FormTimeline.YANK - FormTimeline.REACH), 0.6);
			rPitch = MathHelper.lerp(k, REACH_PITCH, YANK_PITCH);
			rYaw = MathHelper.lerp(k, REACH_YAW, YANK_YAW);
			head = HEAD_SNAP * ease(k * 1.4F);
		} else if (t < FormTimeline.BURST) {
			// Held out there, sagging a little, as the engine catches.
			float k = (t - FormTimeline.YANK) / (FormTimeline.BURST - FormTimeline.YANK);
			rPitch = YANK_PITCH + 0.35F * k;
			rYaw = YANK_YAW;
			head = HEAD_SNAP * (1.0F - 0.6F * k);
		} else if (t < FormTimeline.ROAR) {
			// Dropped, and both arms braced out as the saws push through.
			float k = ease((t - FormTimeline.BURST) / 4.0F);
			rPitch = MathHelper.lerp(k, YANK_PITCH + 0.35F, BRACE_PITCH);
			rYaw = MathHelper.lerp(k, YANK_YAW, 0.0F);
			float brace = ease((t - FormTimeline.ARMS) / 4.0F);
			rRoll = BRACE_ROLL * brace;
			lRoll = -BRACE_ROLL * brace;
			lPitch = BRACE_PITCH * brace;
			head = HEAD_SNAP * 0.4F * (1.0F - k);
		} else {
			float in = ease((t - FormTimeline.ROAR) / 3.0F);
			float out = 1.0F - ease((t - FormTimeline.ROAR_END) / (FormTimeline.END - FormTimeline.ROAR_END));
			rPitch = MathHelper.lerp(in, BRACE_PITCH, ROAR_PITCH) * out;
			lPitch = rPitch;
			rRoll = MathHelper.lerp(in, BRACE_ROLL, ROAR_ROLL) * out;
			lRoll = -rRoll;
			head = ROAR_HEAD * in * out;
		}
		model.rightArm.pitch = rPitch;
		model.rightArm.yaw = rYaw;
		model.rightArm.roll = rRoll;
		if (t >= FormTimeline.ARMS) {
			model.leftArm.pitch = lPitch;
			model.leftArm.yaw = 0.0F;
			model.leftArm.roll = lRoll;
		}
		model.head.pitch += head;
		model.hat.pitch = model.head.pitch;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
