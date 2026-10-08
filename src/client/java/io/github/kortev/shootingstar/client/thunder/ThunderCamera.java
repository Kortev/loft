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
 * The shooter's camera shots for Mjölnir, applied through {@link CameraDirector}: at the call their eyes turn up after the
 * bolt as it leaps into the sky; then the camera climbs from their eyes towards the target, tipping back to look up into
 * the storm boiling out over it, and rises into its base as the feed cuts in. After the feed it waits low at the edge of
 * the zone, looking up at the storm as the leader steps down out of it and the streamers rise, closing in on it, and
 * holds there through the stroke (flung wide by it); then it cuts high over the strike, looking straight down as the
 * scar burns out across the ground, and cranes down and round to a long three-quarter view; and at the end it arcs home
 * over the ground into their eyes.
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
		if (t < ThunderTimeline.RISE) {
			return call(player, thunder, tickDelta, t);
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
		double x = MathHelper.lerp(k, above.x(), eye.x);
		double z = MathHelper.lerp(k, above.z(), eye.z);
		// Home over the ground in an arc, never through a hill on the way.
		double y = MathHelper.lerp(k, above.y(), eye.y) + Math.sin(Math.PI * k) * 14.0;
		if (k < 0.9) {
			y = Math.max(y, clearance(client.world, x, z));
		}
		return new CameraDirector.Shot(x, y, z, MathHelper.lerpAngleDegrees((float) k, above.yaw(), player.getYaw(tickDelta)),
				(float) MathHelper.lerp(k, above.pitch(), player.getPitch(tickDelta)));
	}

	/** A few blocks over the highest ground round {@code x, z}. */
	private static double clearance(ClientWorld world, double x, double z) {
		int top = Integer.MIN_VALUE;
		for (int dx = -3; dx <= 3; dx += 3) {
			for (int dz = -3; dz <= 3; dz += 3) {
				top = Math.max(top, world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x) + dx, MathHelper.floor(z) + dz));
			}
		}
		return top + 3.0;
	}

	/** Where the shooter's eyes are turned at {@code t} while the call goes up: following it up towards the storm. */
	private static float[] callLook(ClientPlayerEntity player, ClientThunder thunder, float tickDelta, double t) {
		Vec3d eye = player.getCameraPosVec(tickDelta);
		CameraDirector.Shot storm = look(eye, thunder.top());
		double k = 0.8 * ease((t - ThunderTimeline.CALL) / (ThunderTimeline.RISE - ThunderTimeline.CALL - 2.0));
		return new float[] {MathHelper.lerpAngleDegrees((float) k, player.getYaw(tickDelta), storm.yaw()),
				(float) MathHelper.lerp(k, player.getPitch(tickDelta), storm.pitch())};
	}

	/** Still in the shooter's eyes, the head turning up after the call as it leaps into the sky and the storm boils out. */
	private static CameraDirector.Shot call(ClientPlayerEntity player, ClientThunder thunder, float tickDelta, double t) {
		Vec3d eye = player.getCameraPosVec(tickDelta);
		float[] look = callLook(player, thunder, tickDelta, t);
		return new CameraDirector.Shot(eye.x, eye.y, eye.z, look[0], look[1]);
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
		// On from where the call left the shooter looking.
		float[] from = callLook(player, thunder, tickDelta, ThunderTimeline.RISE);
		float yaw = from[0] + (float) (70.0 * p * p);
		float pitch = (float) MathHelper.lerp(ease(p * 1.6), from[1], -88.0);
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

	/**
	 * High over the strike looking straight down while the scar burns out across the ground, turning slowly; then a crane
	 * down and round to a long three-quarter view of it all, the petrified bolt standing in its crater.
	 */
	private static CameraDirector.Shot overhead(ClientThunder thunder, double t) {
		double e = t - ThunderTimeline.FRAMES_END;
		double span = ThunderTimeline.WIDE_END - ThunderTimeline.FRAMES_END;
		// Under what is left of the storm.
		double height = Math.min(Math.max(70.0, thunder.radius * 2.1), thunder.cloudBase - thunder.center.y - 12.0);
		float spin = (float) (thunder.seed % 360 + e * 0.35);
		double p = ease((e - span * 0.35) / (span * 0.65));
		double bearing = Math.toRadians(spin + 90.0);
		double out = thunder.radius * 1.3 * p;
		Vec3d eye = thunder.center.add(Math.cos(bearing) * out, height * (1.0 - 0.5 * p), Math.sin(bearing) * out);
		if (p <= 0.0) {
			return new CameraDirector.Shot(eye.x, eye.y, eye.z, spin, 89.9F);
		}
		CameraDirector.Shot toward = look(eye, thunder.center.add(0.0, thunder.radius * 0.2 * p, 0.0));
		return new CameraDirector.Shot(eye.x, eye.y, eye.z, MathHelper.lerpAngleDegrees((float) p, spin, toward.yaw()),
				(float) MathHelper.lerp(p, 89.9, toward.pitch()));
	}

	/**
	 * How much wider or narrower than the player's own the field of view is while a shot holds the camera: closing in on
	 * the leader as it comes down, flung wide by the stroke and settling back while the impact frames run.
	 */
	public static float fovScale(float tickDelta) {
		ClientThunder thunder = ClientThunders.cinematic();
		if (thunder == null) {
			return 1.0F;
		}
		double t = thunder.time(tickDelta);
		if (!ClientThunders.shotActive(thunder, t) || t < ThunderTimeline.INBOUND) {
			return 1.0F;
		}
		double in = ease((t - ThunderTimeline.INBOUND) / (ThunderTimeline.STROKE - ThunderTimeline.INBOUND));
		if (t < ThunderTimeline.STROKE) {
			return (float) (1.0 - 0.16 * in);
		}
		double e = t - ThunderTimeline.STROKE;
		double kick = 1.0 - Math.exp(-e / 1.2);
		double settle = ease(e / (ThunderTimeline.FRAMES_END - ThunderTimeline.STROKE));
		return (float) MathHelper.lerp(settle, MathHelper.lerp(kick, 0.84, 1.18), 1.0);
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
