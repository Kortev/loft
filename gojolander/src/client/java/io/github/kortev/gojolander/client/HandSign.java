package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.domain.DomainTimeline;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * The sign he casts it with, seen from outside: his right hand comes up in front of his chin, the first two fingers
 * crossed (drawn by {@link BlindfoldFeature}), held there until the void, then let down.
 */
public final class HandSign {
	/** How long the hand takes to come up, and to go down again after the void. */
	private static final double UP = 5.0;
	private static final double DOWN = 8.0;
	/** The arm raised: forward and up to the chin, turned in across the chest, the face clear over it. */
	private static final float PITCH = -1.8F;
	private static final float YAW = -0.38F;
	private static final float ROLL = 0.1F;

	private HandSign() {
	}

	/** How far up the hand is at {@code t}, from 0 to 1. */
	public static float raise(ClientDomain domain, double t) {
		double up = MathHelper.clamp(t / UP, 0.0, 1.0);
		double down = MathHelper.clamp((t - DomainTimeline.VOID) / DOWN, 0.0, 1.0);
		double k = Math.min(up, 1.0 - down);
		return (float) (k * k * (3.0 - 2.0 * k));
	}

	/** Puts the right arm (and its sleeve) of {@code model} where the sign holds it, {@code k} of the way there. */
	public static void pose(PlayerEntityModel<?> model, float k) {
		model.rightArm.pitch = MathHelper.lerp(k, model.rightArm.pitch, PITCH + model.head.pitch * 0.5F);
		model.rightArm.yaw = MathHelper.lerp(k, model.rightArm.yaw, YAW + model.head.yaw);
		model.rightArm.roll = MathHelper.lerp(k, model.rightArm.roll, ROLL);
		model.rightSleeve.copyTransform(model.rightArm);
	}
}
