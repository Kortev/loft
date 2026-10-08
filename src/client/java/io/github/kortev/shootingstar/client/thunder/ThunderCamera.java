package io.github.kortev.shootingstar.client.thunder;

import io.github.kortev.shootingstar.client.camera.CameraDirector;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
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
 * The shooter's camera shots for Mjölnir, applied through {@link CameraDirector}: after the call it climbs from their
 * eyes towards the target, turning to look up into the storm winding up over it, and rises into the cloud base as the
 * feed cuts in. After the feed it waits low at the edge of the zone, looking up at the vortex as the leader steps down
 * out of it and the streamers rise, and holds there through the stroke; then it cuts high over the strike, looking
 * straight down as the scar burns out across the ground, turning slowly; and at the end it eases back into their eyes.
 */
public final class ThunderCamera {
	private ThunderCamera() {
	}

	@Nullable
	public static CameraDirector.Shot current(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientThunder thunder = ClientThunders.cinematic();
		if (player == null || client.world == null || thunder == null) {
			return null;
		}
		double t = thunder.time(tickDelta);
		if (!ClientThunders.shotActive(thunder, t)) {
			return null;
		}
		if (t < ThunderTimeline.FEED) {
			return rise(player, thunder, tickDelta, (t - ThunderTimeline.RISE) / (ThunderTimeline.FEED - ThunderTimeline.RISE));
		}
		if (t < ThunderTimeline.FRAMES_END) {
			return witness(client.world, player, thunder, t);
		}
		CameraDirector.Shot above = overhead(thunder, t);
		if (t < ThunderTimeline.WIDE_END) {
			return above;
		}
		double k = ease((t - ThunderTimeline.WIDE_END) / (ThunderTimeline.CAMERA_END - ThunderTimeline.WIDE_END));
		Vec3d eye = player.getCameraPosVec(tickDelta);
		return new CameraDirector.Shot(MathHelper.lerp(k, above.x(), eye.x), MathHelper.lerp(k, above.y(), eye.y),
				MathHelper.lerp(k, above.z(), eye.z), MathHelper.lerpAngleDegrees((float) k, above.yaw(), player.getYaw(tickDelta)),
				(float) MathHelper.lerp(k, above.pitch(), player.getPitch(tickDelta)));
	}

	/**
	 * From the shooter's eyes out over the target and up, the view tipping back to look straight up into the storm, the
	 * whole sky turning with it, until the camera is in the cloud base.
	 */
	private static CameraDirector.Shot rise(ClientPlayerEntity player, ClientThunder thunder, float tickDelta, double p) {
		double e = ease(p);
		Vec3d start = player.getCameraPosVec(tickDelta);
		Vec3d end = new Vec3d(thunder.center.x, thunder.cloudBase + 8.0, thunder.center.z);
		double x = MathHelper.lerp(e, start.x, end.x);
		double z = MathHelper.lerp(e, start.z, end.z);
		// Up slowly at first and then fast: the pull of the storm.
		double y = MathHelper.lerp(Math.pow(p, 2.2), start.y, end.y);
		float yaw = player.getYaw(tickDelta) + (float) (70.0 * p * p);
		float pitch = (float) MathHelper.lerp(ease(p * 1.6), player.getPitch(tickDelta), -88.0);
		return new CameraDirector.Shot(x, y, z, yaw, pitch);
	}

	/**
	 * Low at the edge of the zone on the shooter's side, looking up past the target into the vortex: the leader steps
	 * down into frame, and the bolt fills it.
	 */
	private static CameraDirector.Shot witness(ClientWorld world, ClientPlayerEntity player, ClientThunder thunder, double t) {
		Vec3d center = thunder.center;
		double r = thunder.radius;
		if (thunder.witness == null) {
			thunder.witness = placeWitness(world, player, center, r);
		}
		Vec3d base = thunder.witness;
		Vec3d in = new Vec3d(center.x - base.x, 0, center.z - base.z).normalize();
		// A slow push in while the leader comes down, then the jolt of the stroke pushes it back.
		double push = ease((t - ThunderTimeline.INBOUND) / (ThunderTimeline.STROKE - ThunderTimeline.INBOUND)) * 0.05 * r;
		double e = t - ThunderTimeline.STROKE;
		double back = e > 0 ? (1.0 - Math.exp(-e / 3.0)) * 0.04 * r : 0.0;
		Vec3d eye = base.add(in.multiply(push - back));
		double height = thunder.cloudBase - center.y;
		// Tilt up enough to have the cloud base in the top of the frame and the ground under the target in the bottom.
		Vec3d at = center.add(0, height * 0.42, 0);
		return look(eye, at);
	}

	private static Vec3d placeWitness(ClientWorld world, ClientPlayerEntity player, Vec3d center, double r) {
		Vec3d away = player.getPos().subtract(center);
		Vec3d dir = new Vec3d(away.x, 0, away.z);
		dir = dir.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : dir.normalize();
		Vec3d foot = center.add(dir.multiply(1.25 * r + 6.0));
		int ground = Integer.MIN_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				ground = Math.max(ground, world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(foot.x) + dx,
						MathHelper.floor(foot.z) + dz));
			}
		}
		Vec3d eye = new Vec3d(foot.x, Math.max(ground, center.y) + 2.5, foot.z);
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

	/** High over the strike looking straight down, turning slowly and sinking a little as the scar burns out. */
	private static CameraDirector.Shot overhead(ClientThunder thunder, double t) {
		double e = t - ThunderTimeline.FRAMES_END;
		// Under what is left of the storm.
		double height = Math.min(Math.max(70.0, thunder.radius * 2.1), thunder.cloudBase - thunder.center.y - 12.0);
		double sink = 1.0 - 0.12 * ease(e / (ThunderTimeline.WIDE_END - ThunderTimeline.FRAMES_END));
		Vec3d eye = thunder.center.add(0.0, height * sink, 0.0);
		float yaw = (float) (thunder.seed % 360 + e * 0.35);
		return new CameraDirector.Shot(eye.x, eye.y, eye.z, yaw, 89.9F);
	}

	private static CameraDirector.Shot look(Vec3d eye, Vec3d at) {
		Vec3d d = at.subtract(eye);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) (MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(d.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);
		return new CameraDirector.Shot(eye.x, eye.y, eye.z, yaw, pitch);
	}

	private static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
