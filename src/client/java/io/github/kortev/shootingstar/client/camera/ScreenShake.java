package io.github.kortev.shootingstar.client.camera;

import io.github.kortev.shootingstar.client.ClientConfig;
import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.client.world.ImpactScene;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Camera shake: a rumble that builds while the round comes in, the jolt of the hit, and a violent
 * shake when the shock front itself reaches the camera, fading into a long rumble.
 */
public final class ScreenShake {
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
		Vec3d eye = client.gameRenderer.getCamera().getPos();
		double amplitude = 0.0;
		double time = 0.0;
		for (ClientStrike strike : ClientStrikes.all()) {
			double t = strike.time(tickDelta);
			double distance = eye.distanceTo(strike.center);
			double reach = strike.radius * 10.0 + 200.0;
			double near = MathHelper.clamp(1.0 - distance / reach, 0.0, 1.0);
			if (!strike.impacted && t >= StrikeTimeline.INBOUND) {
				double p = MathHelper.clamp((t - StrikeTimeline.INBOUND) / (StrikeTimeline.IMPACT - StrikeTimeline.INBOUND), 0.0, 1.0);
				amplitude = Math.max(amplitude, near * 0.7 * p * p);
			}
			ImpactScene scene = strike.scene;
			if (strike.impacted && scene != null) {
				double e = scene.age + tickDelta;
				double felt = strike.cinematic() ? 1.0 : near;
				double hit = felt * 2.4 * Math.exp(-Math.max(0.0, e) / 5.0);
				double late = e - scene.arrival(distance);
				double close = MathHelper.clamp(1.0 - distance / (strike.radius * 6.0), 0.0, 1.0);
				double wave = late >= 0 ? (0.6 + 6.5 * close * close) * near * Math.exp(-late / 14.0) : 0.0;
				double rumble = near * 0.45 * Math.exp(-Math.max(0.0, e) / 90.0);
				amplitude = Math.max(amplitude, Math.max(hit, Math.max(wave, rumble)));
			}
			time = Math.max(time, t);
		}
		if (amplitude < 0.01) {
			return;
		}
		amplitude *= ClientConfig.screenShake;
		float pitch = (float) (amplitude * (Math.sin(time * 1.9) * 0.5 + Math.sin(time * 4.3) * 0.3 + Math.sin(time * 9.7) * 0.2));
		float roll = (float) (amplitude * (Math.cos(time * 2.6) * 0.5 + Math.sin(time * 5.1) * 0.3 + Math.cos(time * 11.3) * 0.2));
		float yaw = (float) (amplitude * 0.6 * (Math.sin(time * 3.4 + 1.0) * 0.6 + Math.cos(time * 7.9) * 0.4));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
	}
}
