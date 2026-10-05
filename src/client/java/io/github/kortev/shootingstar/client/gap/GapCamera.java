package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.client.camera.CameraDirector.Shot;
import io.github.kortev.shootingstar.gap.GapTimeline;
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
 * The shooter's shots through a Ginnungagap event: first person while the key turns; up out of their eyes over
 * the target into the clouds; the feed; then from behind them, low and well back, the bridge of light standing
 * over the target and the block coming down it; a hard cut for every impact frame; craning up and back as the
 * universe in the block bursts out; higher still as the black spreads; then in on them, alone, and back to their
 * eyes. No shot ever starts inside a hill.
 */
public final class GapCamera {
	/** Impact frame shots. */
	public static final int EXTREME = 0;
	public static final int CONTACT = 1;
	public static final int WIDE = 2;
	public static final int SIDE = 3;
	public static final int LOW = 4;
	public static final int EYES = 5;

	private GapCamera() {
	}

	@Nullable
	public static Shot current(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientGap gap = ClientGaps.mine();
		if (player == null || gap == null || client.world == null) {
			return null;
		}
		double t = gap.time(tickDelta);
		Vec3d eye = player.getCameraPosVec(tickDelta);
		Vec3d feet = player.getLerpedPos(tickDelta);
		if (t < GapTimeline.RISE || t >= GapTimeline.END) {
			return null;
		}
		if (t < GapTimeline.FEED) {
			return rise(gap, player, tickDelta, (t - GapTimeline.RISE) / (GapTimeline.FEED - GapTimeline.RISE));
		}
		if (t < GapTimeline.INBOUND) {
			// Behind the feed the world goes on from the shooter's eyes; without it, the bridge is watched coming down.
			return gap.feedSkipped ? inbound(gap, feet, GapTimeline.INBOUND) : null;
		}
		if (t < GapTimeline.CONTACT) {
			return inbound(gap, feet, t);
		}
		if (t < GapTimeline.BLAST) {
			return frameShot(gap, GapFrames.at(t - GapTimeline.FRAMES).shot(), eye, feet, t);
		}
		if (t < GapTimeline.ERASURE) {
			return blast(gap, feet, t);
		}
		if (t < GapTimeline.NOTHING) {
			return erasure(gap, feet, t);
		}
		return nothing(gap, feet, t, tickDelta, player);
	}

	/**
	 * From the shooter's eyes up into a top-down view of the target, ending high enough to pass up through the clouds
	 * into the feed but low enough that the fog has not swallowed the ground.
	 */
	private static Shot rise(ClientGap gap, ClientPlayerEntity player, float tickDelta, double p) {
		double e = ease(p);
		Vec3d start = player.getCameraPosVec(tickDelta);
		Vec3d end = gap.contact.add(0, 60 + 90 * p * p, 0);
		double x = MathHelper.lerp(e, start.x, end.x);
		double z = MathHelper.lerp(e, start.z, end.z);
		double y = MathHelper.lerp(e, start.y, end.y) + Math.sin(Math.PI * e) * 12.0;
		float pitch = (float) MathHelper.lerp(e, player.getPitch(tickDelta), 89.9);
		return new Shot(x, y, z, player.getYaw(tickDelta), pitch);
	}

	/** Half the size of the block of the other universe as it comes down. */
	public static final double BLOCK = 5.0;

	/** Half the size of the block of the other universe once it has burst open over the target. */
	public static double blastHalf(ClientGap gap) {
		return MathHelper.clamp(gap.radius * 0.4, 14.0, 70.0);
	}

	/**
	 * Low and well back on the shooter's side, beyond them, so they stand small in the foreground: far enough that the
	 * whole of the burst fits in the frame, inside the loaded world. Found once.
	 */
	static Vec3d witness(ClientGap gap, Vec3d feet) {
		if (gap.wideEye == null) {
			double d = Math.hypot(gap.contact.x - feet.x, gap.contact.z - feet.z);
			double loaded = MinecraftClient.getInstance().options.getClampedViewDistance() * 16.0 - 40.0;
			double reach = Math.min(Math.max(d + 14.0, 2.3 * blastHalf(gap) + 24.0), Math.max(60.0, loaded));
			Vec3d foot = gap.contact.add(gap.along.multiply(-reach)).add(gap.across.multiply(reach * 0.2));
			ClientWorld world = MinecraftClient.getInstance().world;
			int ground = gap.surface;
			if (world != null) {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						ground = Math.max(ground, world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(foot.x) + dx,
								MathHelper.floor(foot.z) + dz));
					}
				}
			}
			gap.wideEye = above(new Vec3d(foot.x, ground + 3.0, foot.z), gap.contact.add(0, 4, 0));
		}
		return gap.wideEye;
	}

	/** How high the block is over the point of contact as it comes down in the world. */
	public static double blockHeight(double t) {
		double u = MathHelper.clamp((t - GapTimeline.INBOUND) / (GapTimeline.CONTACT - GapTimeline.INBOUND), 0.0, 1.0);
		return 300.0 * Math.pow(1.0 - u, 1.5);
	}

	/** The bridge standing over the target, the block coming down it, the shooter small in the foreground. */
	private static Shot inbound(ClientGap gap, Vec3d feet, double t) {
		Vec3d eye = witness(gap, feet);
		double d = Math.hypot(gap.contact.x - eye.x, gap.contact.z - eye.z);
		// The point of contact low in the frame, the sky over it filling the rest; tilting down after the block.
		double k = ease((t - GapTimeline.INBOUND - 6.0) / (GapTimeline.CONTACT - GapTimeline.INBOUND - 6.0));
		double pitch = Math.toRadians(MathHelper.lerp(k, 27.0, 14.0));
		double drop = Math.atan2(gap.contact.y - eye.y, d);
		Vec3d flat = new Vec3d(gap.contact.x - eye.x, 0, gap.contact.z - eye.z).normalize();
		double a = drop + pitch;
		Vec3d at = eye.add(flat.multiply(Math.cos(a) * d)).add(0, Math.sin(a) * d, 0);
		// The bridge hums through everything.
		double s = 0.05 + 0.25 * k * k;
		at = at.add(Math.sin(t * 13.0) * s, Math.cos(t * 11.0) * s, Math.sin(t * 9.0 + 1.3) * s);
		return lookAt(eye, at);
	}

	/** Craning up and back from the witness as the burst grows, holding all of it. */
	private static Shot blast(ClientGap gap, Vec3d feet, double t) {
		Vec3d base = witness(gap, feet);
		double k = ease((t - GapTimeline.BLAST) / (GapTimeline.ERASURE - GapTimeline.BLAST));
		Vec3d out = new Vec3d(base.x - gap.contact.x, 0, base.z - gap.contact.z).normalize();
		double d = Math.hypot(base.x - gap.contact.x, base.z - gap.contact.z);
		Vec3d eye = base.add(out.multiply(0.18 * d * k)).add(0, 0.3 * d * k, 0);
		Vec3d at = gap.contact.add(0, blastHalf(gap) * MathHelper.lerp(k, 0.75, 0.55), 0);
		// The burst shakes the ground the camera stands on, hard at first.
		double shake = 0.6 * Math.exp(-(t - GapTimeline.BLAST) / 10.0) + 0.08;
		eye = eye.add(Math.sin(t * 31.0) * shake, Math.cos(t * 27.0) * shake, Math.sin(t * 23.0 + 1.3) * shake);
		return lookAt(eye, at);
	}

	/** Higher and further back from where the burst left the camera, looking down on the black spreading out over everything. */
	private static Shot erasure(ClientGap gap, Vec3d feet, double t) {
		Shot b = blast(gap, feet, GapTimeline.ERASURE);
		double k = ease((t - GapTimeline.ERASURE) / (GapTimeline.NOTHING - GapTimeline.ERASURE - 20.0));
		Vec3d from = new Vec3d(b.x(), b.y(), b.z());
		Vec3d away = new Vec3d(from.x - gap.contact.x, 0, from.z - gap.contact.z);
		double d = away.length();
		away = away.normalize();
		Vec3d to = gap.contact.add(away.multiply(d * 1.1)).add(0, Math.max(0.0, from.y - gap.contact.y) + 50.0, 0);
		Vec3d lookFrom = gap.contact.add(0, blastHalf(gap) * 0.55, 0);
		return lookAt(from.lerp(to, k), lookFrom.lerp(gap.contact.lerp(feet, 0.3), k));
	}

	/** The impact frames' shots, standing off far enough that the burst growing out of the point of contact never swallows them. */
	static Shot frameShot(ClientGap gap, int shot, Vec3d eye, Vec3d feet, double t) {
		Vec3d side = gap.across.multiply(-1).add(gap.along.multiply(-0.6)).normalize();
		Vec3d c = gap.contact;
		double h = burstHalf(gap, t);
		return switch (shot) {
			case EXTREME -> framed(c.add(side.multiply(1.5 * h + 5.0)).add(0, 1.0, 0), c.add(0, 0.6 * h, 0));
			case CONTACT -> framed(c.add(side.multiply(2.1 * h + 9.0)).add(0, 4.0, 0), c.add(0, 0.8 * h, 0));
			case WIDE -> lookAt(witness(gap, feet), c.add(0, 0.9 * h, 0));
			case SIDE -> framed(c.add(gap.across.multiply(2.6 * h + 16.0)).add(gap.along.multiply(-6.0)).add(0, 3.0, 0), c.add(0, h, 0));
			case LOW -> framed(c.add(side.multiply(1.7 * h + 6.0)).add(0, 0.7, 0), c.add(0, 1.4 * h, 0));
			default -> lookAt(eye, c.add(0, 0.6 * h, 0));
		};
	}

	/**
	 * Half the size of the burst {@code t} ticks into the event: the block's own size when it lands, swelling fast
	 * through the impact frames, slower through the explosion, then falling in on itself to nothing.
	 */
	public static double burstHalf(ClientGap gap, double t) {
		double full = blastHalf(gap);
		double e = t - GapTimeline.CONTACT;
		if (e < 0.0) {
			return 0.0;
		}
		double grown = BLOCK + (full * 0.62 - BLOCK) * (1.0 - Math.exp(-e / 6.0));
		if (t >= GapTimeline.BLAST) {
			grown = MathHelper.lerp(1.0 - Math.exp(-(t - GapTimeline.BLAST) / 22.0), grown, full);
		}
		if (t >= GapTimeline.COLLAPSE) {
			grown *= 1.0 - Math.pow(MathHelper.clamp((t - GapTimeline.COLLAPSE) / (GapTimeline.ERASURE - GapTimeline.COLLAPSE), 0.0, 1.0), 2.0);
		}
		return grown;
	}

	private static Shot framed(Vec3d eye, Vec3d at) {
		return lookAt(clear(eye, at), at);
	}

	/** In on the shooter, alone in the black, round to the front of them, then back to their eyes. */
	@Nullable
	private static Shot nothing(ClientGap gap, Vec3d feet, double t, float tickDelta, ClientPlayerEntity player) {
		Vec3d chest = feet.add(0, 1.2, 0);
		Vec3d facing = Vec3d.fromPolar(0, player.getYaw(tickDelta)).normalize();
		Vec3d right = new Vec3d(-facing.z, 0, facing.x);
		if (t < GapTimeline.NOTHING + 68) {
			// Starting near enough that they are a figure, not a speck, and always looking straight at them.
			Vec3d out = new Vec3d(-gap.across.x - gap.along.x * 0.4, 0, -gap.across.z - gap.along.z * 0.4).normalize();
			Vec3d from = chest.add(out.multiply(11.0)).add(0, 3.0, 0);
			Vec3d to = chest.add(right.multiply(3.4)).add(0, 0.1, 0);
			return lookAt(from.lerp(to, ease((t - GapTimeline.NOTHING) / 50.0)), chest);
		}
		if (t < GapTimeline.RETURN) {
			Vec3d face = feet.add(0, 1.55, 0);
			double k = ease((t - GapTimeline.NOTHING - 68.0) / (GapTimeline.RETURN - GapTimeline.NOTHING - 68.0));
			return lookAt(face.add(facing.multiply(3.2 - 0.5 * k)).add(0, -0.15, 0), face.add(0, -0.25, 0));
		}
		return null;
	}

	/** Pulls {@code eye} in towards {@code at} until nothing stands between them. */
	static Vec3d clear(Vec3d eye, Vec3d at) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null || client.player == null) {
			return eye;
		}
		HitResult hit = world.raycast(new RaycastContext(at, eye, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE,
				client.player));
		if (hit.getType() == HitResult.Type.MISS) {
			return eye;
		}
		Vec3d toward = at.subtract(eye).normalize();
		return hit.getPos().add(toward.multiply(0.8));
	}

	/** Raises {@code eye} until it can see {@code at}. */
	private static Vec3d above(Vec3d eye, Vec3d at) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null || client.player == null) {
			return eye;
		}
		for (int i = 0; i < 24; i++) {
			HitResult hit = world.raycast(new RaycastContext(eye, at, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE,
					client.player));
			if (hit.getType() == HitResult.Type.MISS || hit.getPos().distanceTo(at) < 6.0) {
				break;
			}
			eye = eye.add(0, 5, 0);
		}
		return eye;
	}

	static Shot lookAt(Vec3d eye, Vec3d at) {
		Vec3d d = at.subtract(eye);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) (MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(d.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);
		return new Shot(eye.x, eye.y, eye.z, yaw, pitch);
	}

	static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
