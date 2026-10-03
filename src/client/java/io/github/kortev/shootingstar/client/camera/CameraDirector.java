package io.github.kortev.shootingstar.client.camera;

import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * The shooter's camera shots, applied by {@code CameraMixin}: rising from their eyes over the target
 * before the feed; then, after the feed, one continuous shot from the edge of the blast zone that
 * watches the round come in, holds through the hit and the shock wave, cranes up and back over the
 * crater, and finally eases back into the shooter's own eyes.
 */
public final class CameraDirector {
	public record Shot(double x, double y, double z, float yaw, float pitch) {
	}

	private CameraDirector() {
	}

	@Nullable
	public static Shot current(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientStrike strike = ClientStrikes.cinematic();
		if (player == null || client.world == null || strike == null) {
			return null;
		}
		double t = strike.time(tickDelta);
		if (!ClientStrikes.shotActive(strike, t)) {
			return null;
		}
		if (t < StrikeTimeline.ORBIT) {
			return rise(player, strike.center, tickDelta, (t - StrikeTimeline.RISE) / (StrikeTimeline.ORBIT - StrikeTimeline.RISE));
		}
		Shot watch = witness(client.world, player, strike, t);
		if (t < StrikeTimeline.WIDE_END) {
			return watch;
		}
		double k = ease((t - StrikeTimeline.WIDE_END) / (StrikeTimeline.CAMERA_END - StrikeTimeline.WIDE_END));
		Vec3d eye = player.getCameraPosVec(tickDelta);
		return new Shot(MathHelper.lerp(k, watch.x(), eye.x), MathHelper.lerp(k, watch.y(), eye.y), MathHelper.lerp(k, watch.z(), eye.z),
				MathHelper.lerpAngleDegrees((float) k, watch.yaw(), player.getYaw(tickDelta)),
				(float) MathHelper.lerp(k, watch.pitch(), player.getPitch(tickDelta)));
	}

	/**
	 * From the shooter's eyes up into a top-down view of the reticle, ending high enough to pass up
	 * through the clouds into the feed but low enough that the fog has not swallowed the ground.
	 */
	private static Shot rise(ClientPlayerEntity player, Vec3d target, float tickDelta, double p) {
		double e = ease(p);
		Vec3d start = player.getCameraPosVec(tickDelta);
		Vec3d end = target.add(0, 60 + 90 * p * p, 0);
		double x = MathHelper.lerp(e, start.x, end.x);
		double z = MathHelper.lerp(e, start.z, end.z);
		double y = MathHelper.lerp(e, start.y, end.y) + Math.sin(Math.PI * e) * 12.0;
		float yaw = player.getYaw(tickDelta);
		float pitch = (float) MathHelper.lerp(e, player.getPitch(tickDelta), 89.9);
		return new Shot(x, y, z, yaw, pitch);
	}

	/**
	 * Near the ground on the shooter's side, just outside the planed zone: the sky above the target is in
	 * frame for the round coming down, then the camera cranes up and back as the column rises.
	 */
	private static Shot witness(ClientWorld world, ClientPlayerEntity player, ClientStrike strike, double t) {
		Vec3d center = strike.center;
		double r = strike.radius;
		if (strike.witness == null) {
			strike.witness = placeWitness(world, player, center, r);
		}
		Vec3d base = strike.witness;
		Vec3d out = new Vec3d(base.x - center.x, 0, base.z - center.z).normalize();
		double push = ease((t - StrikeTimeline.INBOUND) / (StrikeTimeline.IMPACT - StrikeTimeline.INBOUND)) * 0.06 * r;
		double crane = ease((t - StrikeTimeline.IMPACT - 38) / (StrikeTimeline.WIDE_END - StrikeTimeline.IMPACT - 38));
		Vec3d eye = base.add(out.multiply(-push + 0.65 * r * crane)).add(0, 0.8 * r * crane, 0);
		// Tilt up with the crane to keep the column and its cap in frame.
		Vec3d at = center.add(0, MathHelper.lerp(crane, 0.42, 1.0) * r, 0);
		return look(eye, at);
	}

	private static Vec3d placeWitness(ClientWorld world, ClientPlayerEntity player, Vec3d center, double r) {
		Vec3d away = player.getPos().subtract(center);
		Vec3d dir = new Vec3d(away.x, 0, away.z);
		dir = dir.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : dir.normalize();
		Vec3d foot = center.add(dir.multiply(1.3 * r));
		int ground = Integer.MIN_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				ground = Math.max(ground, world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(foot.x) + dx,
						MathHelper.floor(foot.z) + dz));
			}
		}
		Vec3d eye = new Vec3d(foot.x, Math.max(ground, center.y) + 3.0, foot.z);
		// Climb until nothing stands between the camera and the target.
		Vec3d aim = center.add(0, 2, 0);
		for (int i = 0; i < 24; i++) {
			HitResult hit = world.raycast(new RaycastContext(eye, aim, RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE, player));
			if (hit.getType() == HitResult.Type.MISS || hit.getPos().distanceTo(aim) < 4.0) {
				break;
			}
			eye = eye.add(0, 4, 0);
		}
		return eye;
	}

	private static Shot look(Vec3d eye, Vec3d at) {
		Vec3d d = at.subtract(eye);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) (MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(d.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);
		return new Shot(eye.x, eye.y, eye.z, yaw, pitch);
	}

	private static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
