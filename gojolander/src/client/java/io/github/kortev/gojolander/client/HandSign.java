package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.domain.DomainTimeline;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * How he moves as he casts it, seen from outside, and how the caught are held:
 *
 * <ul>
 *   <li>his left hand comes up to his brow, hooks the blindfold and pushes it up onto his forehead (the cloth follows,
 *   {@link BlindfoldFeature#lift}), his head dipping a little as it does and coming up as his eyes open, and drops
 *   again;</li>
 *   <li>his right hand snaps up in front of his chin at HAND, the first two fingers crossed (drawn by
 *   {@link BlindfoldFeature}), held there until it closes, then let down;</li>
 *   <li>as the barrier cracks, his left hand comes up again and pulls the blindfold back down over his eyes;</li>
 *   <li>a player caught in the void, the flood pouring in, has their head tipped back, their arms a little out from
 *   them, trembling.</li>
 * </ul>
 */
public final class HandSign {
	/** How long the right hand takes to snap up (arriving at HAND), and to go down again once it has closed. */
	private static final double UP = 3.0;
	private static final double DOWN = 8.0;
	/** The sign: the right arm forward and up to the chin, turned in across the chest, the face clear over it. */
	private static final float PITCH = -1.8F;
	private static final float YAW = -0.38F;
	private static final float ROLL = 0.1F;
	/** The left arm at his brow: up past level, turned in across the face, the hand at the cloth. */
	private static final float BROW_PITCH = -2.45F;
	private static final float BROW_YAW = 0.62F;
	private static final float BROW_ROLL = -0.2F;
	/** How far his head dips as he lifts it (radians, down), and how far the caught have theirs tipped back. */
	private static final float NOD = 0.28F;
	private static final float TIPPED = -0.5F;

	private HandSign() {
	}

	/** How far up the right hand is at {@code t}, from 0 to 1. */
	public static float raise(ClientDomain domain, double t) {
		double up = MathHelper.clamp((t - DomainTimeline.HAND + UP) / UP, 0.0, 1.0);
		double down = MathHelper.clamp((t - DomainTimeline.CLOSE) / DOWN, 0.0, 1.0);
		double k = Math.min(up, 1.0 - down);
		return (float) (k * k * (3.0 - 2.0 * k));
	}

	/**
	 * How far up at his brow the left hand is at {@code t}, from 0 to 1: up to lift the blindfold at the start, and again
	 * to pull it down as the barrier cracks.
	 */
	public static float brow(ClientDomain domain, double t) {
		int c = domain.collapse();
		return (float) Math.max(envelope(t, 0.0, 5.0, 15.0, 22.0), envelope(t, c + 4.0, c + 9.0, c + 16.0, c + 23.0));
	}

	/** How far the cloth is up off his eyes at {@code t}, from 0 to 1: pushed up by the hand, and pulled down by it. */
	public static float lift(ClientDomain domain, double t) {
		int c = domain.collapse();
		double up = ease((t - 7.0) / 8.0);
		double down = ease((t - (c + 9.0)) / 7.0);
		return (float) Math.min(up, 1.0 - down);
	}

	/** How far his head is dipped at {@code t}: a little as the hand comes up, back up as his eyes open. */
	public static float nod(double t) {
		return (float) (NOD * envelope(t, 2.0, 8.0, 12.0, 18.0));
	}

	/** Puts the right arm (and its sleeve) of {@code model} where the sign holds it, {@code k} of the way there. */
	public static void pose(PlayerEntityModel<?> model, float k) {
		model.rightArm.pitch = MathHelper.lerp(k, model.rightArm.pitch, PITCH + model.head.pitch * 0.5F);
		model.rightArm.yaw = MathHelper.lerp(k, model.rightArm.yaw, YAW + model.head.yaw);
		model.rightArm.roll = MathHelper.lerp(k, model.rightArm.roll, ROLL);
		model.rightSleeve.copyTransform(model.rightArm);
	}

	/** Puts the left arm (and its sleeve) up at his brow, {@code k} of the way there. */
	public static void poseBrow(PlayerEntityModel<?> model, float k) {
		model.leftArm.pitch = MathHelper.lerp(k, model.leftArm.pitch, BROW_PITCH + model.head.pitch * 0.6F);
		model.leftArm.yaw = MathHelper.lerp(k, model.leftArm.yaw, BROW_YAW + model.head.yaw);
		model.leftArm.roll = MathHelper.lerp(k, model.leftArm.roll, BROW_ROLL);
		model.leftSleeve.copyTransform(model.leftArm);
	}

	/** Dips the head (and its hat layer) by {@code nod}. */
	public static void poseNod(PlayerEntityModel<?> model, float nod) {
		model.head.pitch += nod;
		model.hat.copyTransform(model.head);
	}

	/** A caught player, {@code k} into the flood: head tipped back, arms a little out, trembling. */
	public static void poseHeld(PlayerEntityModel<?> model, float k, double t, int id) {
		float tremble = (float) (Math.sin(t * 2.7 + id) * 0.04 + Math.sin(t * 6.1 + id * 3.0) * 0.025);
		model.head.pitch = MathHelper.lerp(k, model.head.pitch, TIPPED + tremble);
		model.hat.copyTransform(model.head);
		model.rightArm.roll = MathHelper.lerp(k, model.rightArm.roll, 0.35F + tremble);
		model.leftArm.roll = MathHelper.lerp(k, model.leftArm.roll, -0.35F - tremble);
		model.rightArm.pitch = MathHelper.lerp(k, model.rightArm.pitch, -0.15F + tremble);
		model.leftArm.pitch = MathHelper.lerp(k, model.leftArm.pitch, -0.15F - tremble);
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftSleeve.copyTransform(model.leftArm);
	}

	/** 0 before {@code r0}, up to 1 by {@code r1}, held, and down to 0 again from {@code f0} to {@code f1}. */
	private static double envelope(double t, double r0, double r1, double f0, double f1) {
		return Math.min(ease((t - r0) / (r1 - r0)), 1.0 - ease((t - f0) / (f1 - f0)));
	}

	private static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
