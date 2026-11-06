package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.purple.PurpleTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * How he moves as he casts it, seen from outside, and where his hands are (the orbs sit in them):
 *
 * <ul>
 *   <li>BLUE: his left arm comes up and out to the side, the palm forward; RED: the right, the same;</li>
 *   <li>SWEEP: both sweep in across in front of him until the hands meet;</li>
 *   <li>RELEASE: the right hand flicks forward, and both arms drop.</li>
 * </ul>
 *
 * A hand's place is worked out from the arm's angles in the model's own frame (the arm turned about its shoulder:
 * pitch then yaw), so the light sits at his fingertips whichever way he faces.
 */
public final class PurplePose {
	/** How far out to the side the arms start (radians), and how far across they sweep (the hands meet). */
	private static final float OUT = 0.6F;
	private static final float MEET = 0.34F;
	/** Where the shoulder is from his feet: up, and out to the side. */
	private static final double SHOULDER_UP = 1.375;
	private static final double SHOULDER_SIDE = 0.3125;
	/** From the shoulder to the middle of the light in his hand: the arm, and a little beyond his fingers. */
	private static final double REACH = 0.62 + 0.34;

	/** Where he stands and which way his body faces. */
	public record Anchor(Vec3d feet, float bodyYaw) {
	}

	private PurplePose() {
	}

	/** Where {@code p}'s caster stands now. */
	public static Anchor anchor(MinecraftClient client, ClientPurple p, float tickDelta) {
		PlayerEntity caster = p.caster != null && client.world != null ? client.world.getPlayerByUuid(p.caster) : null;
		if (caster != null) {
			return new Anchor(caster.getLerpedPos(tickDelta), MathHelper.lerpAngleDegrees(tickDelta, caster.prevBodyYaw, caster.bodyYaw));
		}
		float yaw = (float) (MathHelper.atan2(p.dir.z, p.dir.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		return new Anchor(p.eye.subtract(0.0, 1.62, 0.0), yaw);
	}

	/** How far up the arm at one side is at {@code t}, 0 (down at his side) to 1 (out), and lowered again after the release. */
	public static double raise(double t, boolean left) {
		double up = PurpleTimeline.grow(t, left ? PurpleTimeline.BLUE : PurpleTimeline.RED, 10.0);
		double down = PurpleTimeline.grow(t, PurpleTimeline.RELEASE + 5.0, 14.0);
		return up * (1.0 - down);
	}

	/** The arm's angles in the model's frame at {@code t}: pitch, yaw. */
	public static float[] arm(ClientPurple p, boolean left, double t) {
		double raise = raise(t, left);
		float forward = -MathHelper.HALF_PI + p.pitch();
		float pitch = (float) MathHelper.lerp(raise, 0.04, forward);
		// Outward at first, then across as the hands sweep together.
		double out = MathHelper.lerp(PurpleTimeline.sweep(t), OUT, -MEET);
		float yaw = (float) (raise * out * (left ? -1.0 : 1.0));
		if (!left && t >= PurpleTimeline.RELEASE) {
			// The flick: the right hand thrown forward and up, and back.
			pitch -= (float) (0.4 * Math.exp(-(t - PurpleTimeline.RELEASE) / 3.0));
		}
		if (t < PurpleTimeline.RELEASE) {
			// The strain of holding it: a faint tremor.
			double shake = 0.012 * raise * (1.0 + 2.0 * PurpleTimeline.sweep(t));
			pitch += (float) (Math.sin(t * 2.9 + (left ? 0.0 : 1.7)) * shake);
			yaw += (float) (Math.cos(t * 3.7 + (left ? 0.4 : 2.1)) * shake);
		}
		return new float[] {pitch, yaw};
	}

	/** Where the light in one of his hands is at {@code t}. */
	public static Vec3d hand(ClientPurple p, boolean left, double t, Anchor at) {
		float[] a = arm(p, left, t);
		double pitch = a[0];
		double yaw = a[1];
		// In the body's frame: out to his left, up, and ahead.
		double lateral = Math.sin(pitch) * Math.sin(yaw);
		double up = -Math.cos(pitch);
		double ahead = -Math.sin(pitch) * Math.cos(yaw);
		double side = left ? SHOULDER_SIDE : -SHOULDER_SIDE;
		double l = side + lateral * REACH;
		double u = SHOULDER_UP + up * REACH;
		double f = ahead * REACH;
		double r = at.bodyYaw() * MathHelper.RADIANS_PER_DEGREE;
		double sin = Math.sin(r);
		double cos = Math.cos(r);
		// Forward is (-sin, cos), his left is (cos, sin).
		return at.feet().add(l * cos - f * sin, u, l * sin + f * cos);
	}

	/** Where the two meet as the sphere forms: halfway between his hands as they touch. */
	public static Vec3d meet(ClientPurple p, Anchor at) {
		Vec3d a = hand(p, true, PurpleTimeline.MERGE, at);
		Vec3d b = hand(p, false, PurpleTimeline.MERGE, at);
		return a.add(b).multiply(0.5);
	}

	/** Poses the arms of {@code model} (and their sleeves) as the cast has them at {@code t}. */
	public static void pose(PlayerEntityModel<?> model, ClientPurple p, double t) {
		if (raise(t, true) > 0.0) {
			float[] a = arm(p, true, t);
			model.leftArm.pitch = a[0];
			model.leftArm.yaw = a[1];
			model.leftArm.roll = 0.0F;
			model.leftSleeve.copyTransform(model.leftArm);
		}
		if (raise(t, false) > 0.0) {
			float[] a = arm(p, false, t);
			model.rightArm.pitch = a[0];
			model.rightArm.yaw = a[1];
			model.rightArm.roll = 0.0F;
			model.rightSleeve.copyTransform(model.rightArm);
		}
	}
}
