package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.domain.DomainTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The caster's one shot as he casts it, from the key until the void: the camera comes out of his eyes and round in
 * front of him, close on his face as the blindfold comes up and his fingers cross, drifting a little round him; as the
 * black climbs out of the ring it pulls back and up and away to one side, to see it close over him; then it flies back
 * into his eyes as the world goes, and he is free.
 */
public final class DomainCamera {
	/** How far in front of his face the close-up starts and ends, and how far round him it drifts (degrees). */
	private static final double CLOSE_FROM = 2.5;
	private static final double CLOSE_TO = 1.65;
	private static final double DRIFT = 14.0;
	/** Where the wide shot ends up: this far out, this far round to his right and this far up. */
	private static final double WIDE_OUT = 8.5;
	private static final double WIDE_ROUND = 38.0;
	private static final double WIDE_UP = 3.6;
	/** How long the camera takes out of his eyes, and when it starts back into them. */
	private static final double LEAVE = 6.0;
	private static final double BACK = DomainTimeline.CLOSE + 2.0;

	/** Where the camera is and which way it looks. */
	public record Shot(double x, double y, double z, float yaw, float pitch) {
	}

	private DomainCamera() {
	}

	@Nullable
	public static Shot current(float tickDelta) {
		ClientDomain domain = ClientDomains.mine();
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (domain == null || player == null || !ClientDomains.shooting()) {
			return null;
		}
		double t = domain.time(tickDelta);
		Vec3d eye = player.getCameraPosVec(tickDelta);
		Vec3d face = player.getLerpedPos(tickDelta).add(0.0, player.getStandingEyeHeight() - 0.08, 0.0);
		Shot eyes = new Shot(eye.x, eye.y, eye.z, player.getYaw(tickDelta), player.getPitch(tickDelta));
		Shot close = around(domain, face, MathHelper.lerp(ease(t / DomainTimeline.RISE), CLOSE_FROM, CLOSE_TO),
				DRIFT * ease(t / DomainTimeline.RISE), -0.12, face);
		Shot shot;
		if (t < DomainTimeline.RISE) {
			shot = close;
		} else {
			double k = ease((t - DomainTimeline.RISE) / (BACK - DomainTimeline.RISE));
			Shot wide = around(domain, face, WIDE_OUT, DRIFT + WIDE_ROUND, WIDE_UP, face.add(0.0, -0.5, 0.0));
			shot = blend(close, wide, k);
		}
		if (t < LEAVE) {
			return blend(eyes, shot, ease(t / LEAVE));
		}
		if (t > BACK) {
			return blend(shot, eyes, ease((t - BACK) / (DomainTimeline.VOID - BACK)));
		}
		return shot;
	}

	/**
	 * The camera {@code distance} out in front of the caster (as he faced when he cast it), {@code round} degrees round
	 * to his right and {@code up} above his face, looking at {@code look}.
	 */
	private static Shot around(ClientDomain domain, Vec3d face, double distance, double round, double up, Vec3d look) {
		float yaw = domain.yaw + (float) round;
		float r = yaw * MathHelper.RADIANS_PER_DEGREE;
		Vec3d out = new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
		Vec3d at = face.add(out.multiply(distance)).add(0.0, up, 0.0);
		Vec3d to = look.subtract(at);
		float lookYaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float lookPitch = (float) (-(MathHelper.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * MathHelper.DEGREES_PER_RADIAN));
		return new Shot(at.x, at.y, at.z, lookYaw, lookPitch);
	}

	private static Shot blend(Shot a, Shot b, double k) {
		return new Shot(MathHelper.lerp(k, a.x, b.x), MathHelper.lerp(k, a.y, b.y), MathHelper.lerp(k, a.z, b.z),
				a.yaw + MathHelper.wrapDegrees(b.yaw - a.yaw) * (float) k, MathHelper.lerp((float) k, a.pitch, b.pitch));
	}

	private static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
