package io.github.kortev.shootingstar.client.camera;

import io.github.kortev.shootingstar.client.ClientConfig;
import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Ground shake: a rumble while the round comes in, a hard jolt when the shockwave arrives. */
public final class ScreenShake {
	private static final double WAVE_SPEED = 17.0;

	private ScreenShake() {
	}

	public static void apply(MatrixStack matrices, float tickDelta) {
		if (ClientConfig.screenShake <= 0.0F) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		double amplitude = 0.0;
		double time = 0.0;
		for (ClientStrike strike : ClientStrikes.all()) {
			double t = strike.time(tickDelta);
			double distance = client.player.getPos().distanceTo(strike.center);
			if (strike.cinematic() && ClientStrikes.shotActive(strike, t)) {
				distance = Math.min(distance, strike.radius * 1.5);
			}
			double near = MathHelper.clamp(1.0 - distance / 520.0, 0.0, 1.0);
			if (t >= StrikeTimeline.INBOUND && t < StrikeTimeline.IMPACT) {
				double p = (t - StrikeTimeline.INBOUND) / (StrikeTimeline.IMPACT - StrikeTimeline.INBOUND);
				amplitude = Math.max(amplitude, near * 0.35 * p * p);
			}
			if (strike.impacted) {
				double arrival = strike.impactAge + distance / WAVE_SPEED;
				double e = t - arrival;
				if (e >= 0 && e < 140) {
					amplitude = Math.max(amplitude, near * near * 3.2 * Math.exp(-e / 22.0));
				}
			}
			time = Math.max(time, t);
		}
		if (amplitude < 0.01) {
			return;
		}
		amplitude *= ClientConfig.screenShake;
		float pitch = (float) (amplitude * (Math.sin(time * 1.9) * 0.6 + Math.sin(time * 4.3) * 0.4));
		float roll = (float) (amplitude * (Math.cos(time * 2.6) * 0.6 + Math.sin(time * 5.1) * 0.4));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
	}
}
