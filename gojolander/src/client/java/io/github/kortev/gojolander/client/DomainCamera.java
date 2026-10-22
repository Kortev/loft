package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.domain.DomainTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * The caster's shots as he casts it, from the key until the barrier closes, cut together:
 *
 * <ul>
 *   <li>SIGN: a hard cut to his face, close, the camera drifting slowly round as the blindfold comes up and his eyes
 *   light;</li>
 *   <li>HAND: his hand snaps up into the sign and the camera punches in on it (a zoom, {@link #fovScale});</li>
 *   <li>WIDE: a cut to wide, low beside him, craning back and up and round to watch the ink flood up out of the ring
 *   and close over him;</li>
 *   <li>CLOSE: a hard cut back into his eyes, into the black.</li>
 * </ul>
 *
 * The wide shot stops short of anything solid between him and where it would be, so it never looks out of a hill.
 */
public final class DomainCamera {
	/** How far in front of his face the close-up starts and ends, and how far round him it drifts (degrees). */
	private static final double CLOSE_FROM = 1.9;
	private static final double CLOSE_TO = 1.45;
	private static final double DRIFT = 12.0;
	/** The punch-in on the sign: how close, and how far the zoom closes in. */
	private static final double PUNCH = 1.75;
	private static final float ZOOM = 0.82F;
	/** The wide shot: from close beside him, low, to far out and up, swinging round. */
	private static final double WIDE_FROM_OUT = 3.5;
	private static final double WIDE_FROM_ROUND = 28.0;
	private static final double WIDE_FROM_UP = 0.6;
	private static final double WIDE_TO_OUT = 10.0;
	private static final double WIDE_TO_ROUND = 62.0;
	private static final double WIDE_TO_UP = 4.5;

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
		Vec3d face = player.getLerpedPos(tickDelta).add(0.0, player.getStandingEyeHeight() - 0.06, 0.0);
		if (t < DomainTimeline.HAND) {
			double k = ease(t / DomainTimeline.HAND);
			return around(domain, face, MathHelper.lerp(k, CLOSE_FROM, CLOSE_TO), DRIFT * k, -0.08, face);
		}
		if (t < DomainTimeline.WIDE) {
			double k = ease((t - DomainTimeline.HAND) / 4.0);
			Vec3d sign = face.add(0.0, -0.14, 0.0);
			return around(domain, face, MathHelper.lerp(k, CLOSE_TO, PUNCH), DRIFT + 3.0 * k, -0.18, sign);
		}
		double k = ease((t - DomainTimeline.WIDE) / (DomainTimeline.CLOSE - DomainTimeline.WIDE));
		Vec3d look = face.add(0.0, MathHelper.lerp(k, -0.6, -0.2), 0.0);
		Shot wide = around(domain, face, MathHelper.lerp(k, WIDE_FROM_OUT, WIDE_TO_OUT), MathHelper.lerp(k, WIDE_FROM_ROUND, WIDE_TO_ROUND),
				MathHelper.lerp(k, WIDE_FROM_UP, WIDE_TO_UP), look);
		return clear(client, face, wide);
	}

	/** How far the camera's view is zoomed for the shot (1 for not at all): in on the sign as it snaps up. */
	public static float fovScale(float tickDelta) {
		ClientDomain domain = ClientDomains.mine();
		if (domain == null || !ClientDomains.shooting()) {
			return 1.0F;
		}
		double t = domain.time(tickDelta);
		if (t < DomainTimeline.HAND || t >= DomainTimeline.WIDE) {
			return 1.0F;
		}
		return MathHelper.lerp((float) ease((t - DomainTimeline.HAND) / 3.0), 1.0F, ZOOM);
	}

	/**
	 * The camera {@code distance} out in front of the caster (as he faced when he cast it), {@code round} degrees round
	 * to his right and {@code up} above his face, looking at {@code look}.
	 */
	private static Shot around(ClientDomain domain, Vec3d face, double distance, double round, double up, Vec3d look) {
		float yaw = domain.yaw + (float) round;
		float r = yaw * MathHelper.RADIANS_PER_DEGREE;
		Vec3d out = new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
		return look(face.add(out.multiply(distance)).add(0.0, up, 0.0), look);
	}

	private static Shot look(Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float lookYaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float lookPitch = (float) (-(MathHelper.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * MathHelper.DEGREES_PER_RADIAN));
		return new Shot(at.x, at.y, at.z, lookYaw, lookPitch);
	}

	/** The shot brought in short of anything solid between his face and where it would stand. */
	private static Shot clear(MinecraftClient client, Vec3d face, Shot shot) {
		if (client.world == null || client.player == null) {
			return shot;
		}
		Vec3d to = new Vec3d(shot.x, shot.y, shot.z);
		HitResult hit = client.world.raycast(new RaycastContext(face, to, RaycastContext.ShapeType.VISUAL,
				RaycastContext.FluidHandling.NONE, client.player));
		if (hit.getType() == HitResult.Type.MISS) {
			return shot;
		}
		Vec3d back = to.subtract(face);
		double length = back.length();
		double reach = Math.max(0.6, face.distanceTo(hit.getPos()) - 0.4);
		Vec3d at = face.add(back.multiply(reach / Math.max(length, 1.0E-4)));
		return new Shot(at.x, at.y, at.z, shot.yaw, shot.pitch);
	}

	private static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
