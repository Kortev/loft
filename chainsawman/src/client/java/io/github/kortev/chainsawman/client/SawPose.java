package io.github.kortev.chainsawman.client;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * The body fighting in the form, as everyone sees it, in place of the game's punch. The combo ({@link ClientCombat#SWING}
 * ticks a slash): the right arm drawn back out to the side and swept across the body, the torso twisting into it; the
 * left the same way back; then both arms raised overhead and brought down together, the head nodding its saw down with
 * them. The lunge: bent forward headfirst, the head's saw leading, arms swept back. Grinding (or cutting through a
 * block): both arms held out in front, shaking with the engine. The chain out: the right arm held out along it. And
 * the rest of the time his own stance, not a player's: standing, arms a little forward and out, saws low; running,
 * leaning in with the arms swept back, saws trailing. In a frenzy the arms hang wider and the head comes forward.
 *
 * <p>Angles in radians, as in {@link CordPull}: an arm out in front is pitched -π/2; a raised arm swings out from the
 * body with a positive yaw on the right.
 */
public final class SawPose {
	private static final float FORWARD = -MathHelper.HALF_PI;
	/** A sweep: from how far out to the side, to how far across the body, and the torso's twist with it. */
	private static final float SWEEP_OUT = 1.15F;
	private static final float SWEEP_ACROSS = -0.85F;
	private static final float TWIST = 0.45F;
	/** The overhead chop: raised, and brought down. */
	private static final float RAISED = -2.9F;
	private static final float CHOPPED = -0.55F;

	private SawPose() {
	}

	/** Poses {@code player} in the form: a slash, grinding, the chain, or standing ready. */
	public static void pose(BipedEntityModel<?> model, PlayerEntity player, float tickDelta) {
		float swing = ClientCombat.swingAge(player, tickDelta);
		ClientCombat.Swing which = ClientCombat.swing(player);
		ClientCombat.Chain chain = ClientCombat.chain(player);
		if (swing >= 0.0F && which != null && which.step() == 3) {
			lunge(model, swing / ClientCombat.SWING);
		} else if (swing >= 0.0F && which != null) {
			slash(model, which.step(), swing / ClientCombat.SWING);
		} else if (ClientCombat.grinding(player) || player.handSwinging) {
			grind(model, player.age + tickDelta);
		} else {
			stance(model, player);
		}
		if (ClientCombat.frenzy(player)) {
			model.rightArm.roll += 0.2F;
			model.leftArm.roll -= 0.2F;
			model.head.pitch += 0.15F;
		}
		if (chain != null) {
			model.rightArm.pitch = FORWARD + model.head.pitch;
			model.rightArm.yaw = model.head.yaw;
			model.rightArm.roll = 0.0F;
		}
	}

	private static void slash(BipedEntityModel<?> model, int step, float k) {
		if (step == 2) {
			// Both up overhead, then down together; the head's saw comes down with them.
			float up = ease(k / 0.3F);
			float down = ease((k - 0.3F) / 0.25F);
			float back = ease((k - 0.6F) / 0.4F);
			float pitch = MathHelper.lerp(down, MathHelper.lerp(up, 0.0F, RAISED), CHOPPED) * (1.0F - back);
			model.rightArm.pitch = pitch;
			model.leftArm.pitch = pitch;
			model.rightArm.yaw = -0.2F * (1.0F - back);
			model.leftArm.yaw = 0.2F * (1.0F - back);
			model.rightArm.roll = 0.0F;
			model.leftArm.roll = 0.0F;
			model.head.pitch += (0.45F * down - 0.25F * up * (1.0F - down)) * (1.0F - back);
			model.body.pitch = 0.3F * down * (1.0F - back);
			return;
		}
		// One arm swept across: out to the side, across the body, back to rest.
		float side = step == 0 ? 1.0F : -1.0F;
		float out = ease(k / 0.2F);
		float across = ease((k - 0.2F) / 0.3F);
		float back = ease((k - 0.5F) / 0.5F);
		float yaw = MathHelper.lerp(across, SWEEP_OUT * out, SWEEP_ACROSS) * (1.0F - back);
		float pitch = FORWARD * out * (1.0F - back);
		float twist = MathHelper.lerp(across, TWIST * 0.6F * out, -TWIST) * (1.0F - back);
		model.body.yaw = twist * side;
		// The shoulders go round with the torso.
		model.rightArm.pivotZ = MathHelper.sin(model.body.yaw) * 5.0F;
		model.rightArm.pivotX = -MathHelper.cos(model.body.yaw) * 5.0F;
		model.leftArm.pivotZ = -MathHelper.sin(model.body.yaw) * 5.0F;
		model.leftArm.pivotX = MathHelper.cos(model.body.yaw) * 5.0F;
		if (step == 0) {
			model.rightArm.pitch = pitch;
			model.rightArm.yaw = yaw + model.body.yaw;
			model.rightArm.roll = 0.0F;
			model.leftArm.yaw += model.body.yaw;
		} else {
			model.leftArm.pitch = pitch;
			model.leftArm.yaw = -yaw + model.body.yaw;
			model.leftArm.roll = 0.0F;
			model.rightArm.yaw += model.body.yaw;
		}
	}

	/** Standing or walking: arms a little forward and out, swinging less than a player's; running: leaning in, arms back. */
	private static void stance(BipedEntityModel<?> model, PlayerEntity player) {
		if (player.isSprinting()) {
			float swing = model.rightArm.pitch * 0.25F;
			model.rightArm.pitch = 0.85F + swing;
			model.leftArm.pitch = 0.85F - swing;
			model.rightArm.roll = 0.3F;
			model.leftArm.roll = -0.3F;
			model.body.pitch = 0.3F;
			model.head.pitch -= 0.25F;
			return;
		}
		model.rightArm.pitch = model.rightArm.pitch * 0.4F - 0.3F;
		model.leftArm.pitch = model.leftArm.pitch * 0.4F - 0.3F;
		model.rightArm.roll += 0.18F;
		model.leftArm.roll -= 0.18F;
	}

	/** Headfirst: bent forward, the head's saw leading, the arms swept back. */
	private static void lunge(BipedEntityModel<?> model, float k) {
		float in = ease(k / 0.2F) * (1.0F - ease((k - 0.7F) / 0.3F));
		model.body.pitch = 0.5F * in;
		model.head.pitch += 0.25F * in;
		model.rightArm.pitch = 1.1F * in;
		model.leftArm.pitch = 1.1F * in;
		model.rightArm.roll = 0.25F * in;
		model.leftArm.roll = -0.25F * in;
	}

	/** Both arms out in front, a little in, shaking with the engine. */
	private static void grind(BipedEntityModel<?> model, float time) {
		float shake = MathHelper.sin(time * 9.0F) * 0.04F + MathHelper.sin(time * 23.0F) * 0.025F;
		model.rightArm.pitch = FORWARD + 0.15F + shake;
		model.leftArm.pitch = FORWARD + 0.15F - shake;
		model.rightArm.yaw = -0.3F + shake;
		model.leftArm.yaw = 0.3F - shake;
		model.rightArm.roll = 0.0F;
		model.leftArm.roll = 0.0F;
		model.body.pitch = 0.12F;
		model.head.pitch += 0.15F;
	}

	private static float ease(float x) {
		x = MathHelper.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
