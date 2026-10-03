package io.github.kortev.shootingstar.client.camera;

import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The shooter's camera shots: rising over the target before the feed, looking up at the incoming
 * round, the aerial impact frame and a wide shot of the spire. Applied by {@code CameraMixin}.
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
		Vec3d target = strike.center;
		if (t < StrikeTimeline.ORBIT) {
			return rise(player, target, tickDelta, (t - StrikeTimeline.RISE) / (StrikeTimeline.ORBIT - StrikeTimeline.RISE));
		}
		if (t < StrikeTimeline.IMPACT) {
			return sky(client.world, target, (t - StrikeTimeline.INBOUND) / (StrikeTimeline.IMPACT - StrikeTimeline.INBOUND));
		}
		if (t < StrikeTimeline.IMPACT_FRAME_END) {
			double r = strike.radius;
			Vec3d eye = target.add(-r * 0.55, r * 0.8 + 12, -r * 0.55);
			return look(eye, target);
		}
		return wide(client.world, player, strike, (t - StrikeTimeline.IMPACT_FRAME_END)
				/ (StrikeTimeline.WIDE_END - StrikeTimeline.IMPACT_FRAME_END));
	}

	/** From the shooter's eyes up into a top-down view of the reticle. */
	private static Shot rise(ClientPlayerEntity player, Vec3d target, float tickDelta, double p) {
		double e = ease(p);
		Vec3d start = player.getCameraPosVec(tickDelta);
		Vec3d end = target.add(0, 60 + 140 * p * p, 0);
		double x = MathHelper.lerp(e, start.x, end.x);
		double z = MathHelper.lerp(e, start.z, end.z);
		double y = MathHelper.lerp(e, start.y, end.y) + Math.sin(Math.PI * e) * 12.0;
		float yaw = player.getYaw(tickDelta);
		float pitch = (float) MathHelper.lerp(e, player.getPitch(tickDelta), 89.9);
		return new Shot(x, y, z, yaw, pitch);
	}

	/** On the ground beside the target, looking straight up the beam. */
	private static Shot sky(ClientWorld world, Vec3d target, double p) {
		BlockPos base = BlockPos.ofFloored(target.add(4, 0, 3));
		double y = Math.max(target.y, world.getTopY(Heightmap.Type.MOTION_BLOCKING, base.getX(), base.getZ())) + 2.5;
		Vec3d eye = new Vec3d(base.getX() + 0.5, y + p * 1.5, base.getZ() + 0.5);
		Vec3d up = target.add(0, 400, 0);
		Shot shot = look(eye, up);
		return new Shot(shot.x(), shot.y(), shot.z(), shot.yaw(), Math.max(-89.5F, shot.pitch()));
	}

	/** A slow push-in on the spire from the shooter's side of the crater. */
	private static Shot wide(ClientWorld world, ClientPlayerEntity player, ClientStrike strike, double p) {
		Vec3d target = strike.center;
		Vec3d away = player.getPos().subtract(target);
		Vec3d dir = new Vec3d(away.x, 0, away.z);
		dir = dir.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : dir.normalize();
		double distance = strike.radius * MathHelper.lerp(ease(p), 1.9, 1.55);
		Vec3d foot = target.add(dir.multiply(distance));
		int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(foot.x), MathHelper.floor(foot.z));
		Vec3d eye = new Vec3d(foot.x, Math.max(ground, target.y) + 4.0, foot.z);
		return look(eye, target.add(0, strike.radius * 1.4, 0));
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
