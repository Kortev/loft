package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.form.FormTimeline;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * The cord pulled, as everyone sees it: the right hand comes up to the cord in the middle of the chest
 * ({@link FormTimeline#REACH}), yanks it out and away, flinging the arm out to the side and up ({@link FormTimeline#YANK}),
 * holds there as the engine screams and the saws burst out, and comes back down ({@link FormTimeline#SETTLE}). The head
 * jerks back with the yank.
 */
public final class CordPull {
	/** The arm at the cord: forward and across the chest (radians). */
	private static final float REACH_PITCH = -1.15F;
	private static final float REACH_YAW = -0.55F;
	/** The arm flung out with the cord: raised a little above level and swung out to the side (with the arm raised, a roll would swing it inward). */
	private static final float YANK_PITCH = -1.7F;
	private static final float YANK_YAW = 0.95F;
	private static final float YANK_ROLL = 0.0F;
	private static final float HEAD_JERK = -0.35F;

	private CordPull() {
	}

	/** Poses the arm and head {@code t} ticks after the pull. */
	public static void pose(BipedEntityModel<?> model, float t) {
		float pitch;
		float yaw;
		float roll;
		float head;
		if (t < FormTimeline.REACH) {
			float k = ease(t / FormTimeline.REACH);
			pitch = REACH_PITCH * k;
			yaw = REACH_YAW * k;
			roll = 0.0F;
			head = 0.0F;
		} else if (t < FormTimeline.YANK) {
			float k = ease((t - FormTimeline.REACH) / (FormTimeline.YANK - FormTimeline.REACH));
			pitch = MathHelper.lerp(k, REACH_PITCH, YANK_PITCH);
			yaw = MathHelper.lerp(k, REACH_YAW, YANK_YAW);
			roll = YANK_ROLL * k;
			head = HEAD_JERK * k;
		} else if (t < FormTimeline.BURST) {
			pitch = YANK_PITCH;
			yaw = YANK_YAW;
			roll = YANK_ROLL;
			head = HEAD_JERK * (1.0F - (t - FormTimeline.YANK) / (FormTimeline.BURST - FormTimeline.YANK) * 0.5F);
		} else {
			float k = 1.0F - ease((t - FormTimeline.BURST) / (FormTimeline.SETTLE - FormTimeline.BURST));
			pitch = YANK_PITCH * k;
			yaw = YANK_YAW * k;
			roll = YANK_ROLL * k;
			head = HEAD_JERK * 0.5F * k;
		}
		ModelPart arm = model.rightArm;
		arm.pitch = pitch;
		arm.yaw = yaw;
		arm.roll = roll;
		model.head.pitch += head;
		model.hat.pitch = model.head.pitch;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
