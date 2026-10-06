package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.client.camera.CameraDirector.Shot;
import io.github.kortev.shootingstar.gap.GapTimeline;
import java.util.function.DoubleFunction;
import java.util.function.DoubleToIntFunction;
import java.util.function.Function;
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
 * eyes.
 * <p>
 * Every shot is a pure function of time, and every one goes through the same guard: wherever the world can be seen,
 * the lens is kept clear over the ground under it and lifted until it can see what it is looking at, smoothly, by
 * looking ahead at where the shot is going and back at where it has been. It is never pulled in at its subject, and
 * it never leaves the ground the client has loaded. Where everything is black there is nothing to keep clear of.
 */
public final class GapCamera {
	/** Impact frame shots. */
	public static final int EXTREME = 0;
	public static final int CONTACT = 1;
	public static final int WIDE = 2;
	public static final int SIDE = 3;
	public static final int LOW = 4;
	public static final int EYES = 5;

	/** How high the lens stays over the ground under it, and over the ground between it and what it looks at. */
	private static final double CLEARANCE = 2.2;
	private static final double SIGHT_MARGIN = 1.2;
	/** Most it is lifted to see over something in front of its subject: past that, the subject is simply behind it. */
	private static final double MOST_FOR_SIGHT = 22.0;
	/** How far ahead (ticks) the guard sees something coming and starts to rise, how long it holds and settles after. */
	private static final int AHEAD = 16;
	private static final int BEHIND = 28;
	private static final int HOLD = 2;

	private GapCamera() {
	}

	/** Where the camera is and what it looks at, and how much the guard may move it (0 in the shooter's eyes, or in the black). */
	private record Pose(Vec3d eye, Vec3d at, double guard) {
	}

	/** True while the shooter's camera is away from their eyes (cheap: for the HUD and the screen shake). */
	public static boolean away(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientGap gap = ClientGaps.mine();
		if (client.player == null || gap == null || client.world == null || gap.feedSkipped) {
			return false;
		}
		if (gap.rebuildAt >= 0) {
			return gap.rebuildFrom != null && gap.rebuild(tickDelta) < GapTimeline.REBUILD_END;
		}
		double t = gap.time(tickDelta);
		return t >= GapTimeline.RISE && t < GapTimeline.FEED || t >= GapTimeline.INBOUND && t < GapTimeline.RETURN;
	}

	@Nullable
	public static Shot current(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientGap gap = ClientGaps.mine();
		if (player == null || gap == null || client.world == null || !away(tickDelta)) {
			return null;
		}
		Vec3d feet = player.getLerpedPos(tickDelta);
		if (gap.rebuildAt >= 0) {
			return guarded(r -> rebuild(gap, player, tickDelta, r), r -> 0, gap.rebuild(tickDelta), feet);
		}
		double t = gap.time(tickDelta);
		if (t >= GapTimeline.FRAMES && t < GapTimeline.BLAST && GapFrames.index(t - GapTimeline.FRAMES) < LAST_FRAME) {
			return frame(gap, player, tickDelta, t, feet);
		}
		return guarded(s -> shot(gap, player, tickDelta, s), GapCamera::run, t, feet);
	}

	/**
	 * Which unbroken run of camera {@code t} is in: the guard only looks along the shot it is guarding, never across a
	 * cut to another (a lift the last shot needed has nothing to do with the next).
	 */
	private static int run(double t) {
		if (t < GapTimeline.FEED) {
			return 1;
		}
		if (t < GapTimeline.FRAMES) {
			return 2;
		}
		if (t < GapTimeline.NOTHING) {
			int frame = GapFrames.index(t - GapTimeline.FRAMES);
			return t < GapTimeline.BLAST && frame < LAST_FRAME ? 10 + frame : 3;
		}
		return 4;
	}

	/** The impact frame from which the camera runs on unbroken into the burst; those before it are hard cuts. */
	private static final int LAST_FRAME = 7;

	/** The shot at {@code t} ticks into the event, or null where the shooter's own eyes have it. */
	@Nullable
	private static Pose shot(ClientGap gap, ClientPlayerEntity player, float tickDelta, double t) {
		Vec3d eye = player.getCameraPosVec(tickDelta);
		Vec3d feet = player.getLerpedPos(tickDelta);
		if (t < GapTimeline.RISE || t >= GapTimeline.RETURN || t >= GapTimeline.FEED && t < GapTimeline.INBOUND) {
			return null;
		}
		if (t < GapTimeline.FEED) {
			return rise(gap, player, tickDelta, (t - GapTimeline.RISE) / (GapTimeline.FEED - GapTimeline.RISE));
		}
		if (t < GapTimeline.CONTACT) {
			// Out of the white of the cloud deck: from the ground beside the point of contact, looking straight up the
			// bridge at it coming down, a star getting bigger; then swinging out to the wide shot for the last of it.
			double out = ease((t - GapTimeline.INBOUND - 9.0) / 7.0);
			Pose wide = inbound(gap, feet, t);
			return out >= 1.0 ? wide : mix(below(gap, feet, t), wide, out);
		}
		if (t < GapTimeline.BLAST) {
			double e = t - GapTimeline.FRAMES;
			if (GapFrames.index(e) < LAST_FRAME) {
				// The cuts are each guarded on their own (frame); here they only stand aside for the guard's look round.
				return null;
			}
			return slammed(gap, eye, feet, t);
		}
		if (t < GapTimeline.ERASURE) {
			// Out of the last impact frame not on a cut but a swing: the frame's camera carries on and gives way to the
			// blast's as the burst swells, so the explosion opens out of the frame that showed it hitting.
			double into = (t - GapTimeline.BLAST) / 14.0;
			if (into < 1.0) {
				Pose last = framePose(gap, GapFrames.at(GapTimeline.BLAST - GapTimeline.FRAMES - 0.01).shot(), eye, feet, t);
				return mix(last, blast(gap, feet, t), ease(into));
			}
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
	private static Pose rise(ClientGap gap, ClientPlayerEntity player, float tickDelta, double p) {
		double e = ease(p);
		Vec3d start = player.getCameraPosVec(tickDelta);
		Vec3d end = gap.contact.add(0, 60 + 90 * p * p, 0);
		double x = MathHelper.lerp(e, start.x, end.x);
		double z = MathHelper.lerp(e, start.z, end.z);
		double y = MathHelper.lerp(e, start.y, end.y) + Math.sin(Math.PI * e) * 12.0;
		float pitch = (float) MathHelper.lerp(e, player.getPitch(tickDelta), 89.9);
		Vec3d eye = new Vec3d(x, y, z);
		// Guarded once it is out of their head.
		return new Pose(eye, eye.add(Vec3d.fromPolar(pitch, player.getYaw(tickDelta)).multiply(40.0)), leaving(eye, start));
	}

	/** Half the size of the block of the other universe as it comes down. */
	public static final double BLOCK = GapTimeline.BLOCK;

	/** Half the size of the block of the other universe once it has burst open over the target. */
	public static double blastHalf(ClientGap gap) {
		return GapTimeline.blastHalf(gap.radius);
	}

	/**
	 * Low and well back on the shooter's side, beyond them, so they stand small in the foreground: far enough that the
	 * whole of the burst fits in the frame, inside the loaded world. Of a spread of places round that, the one that sees
	 * the burst from lowest down, so it looms over the camera and the camera never climbs into the sky. Found once.
	 */
	static Vec3d witness(ClientGap gap, Vec3d feet) {
		if (gap.wideEye == null) {
			MinecraftClient client = MinecraftClient.getInstance();
			ClientWorld world = client.world;
			double d = Math.hypot(gap.contact.x - feet.x, gap.contact.z - feet.z);
			double loaded = client.options.getClampedViewDistance() * 16.0 - 40.0;
			double reach = MathHelper.clamp(Math.max(d + 12.0, 2.5 * blastHalf(gap) + 10.0), 50.0, Math.max(60.0, loaded));
			Vec3d look = gap.contact.add(0, 10, 0);
			Vec3d best = null;
			double bestScore = Double.MAX_VALUE;
			for (double turn : new double[] {0, 25, -25, 50, -50, 75, -75}) {
				double a = Math.atan2(-gap.along.z, -gap.along.x) + Math.toRadians(turn);
				Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
				for (double k : new double[] {1.0, 0.85, 1.15}) {
					Vec3d foot = gap.contact.add(dir.multiply(reach * k));
					int ground = gap.surface;
					if (world != null) {
						for (int dx = -2; dx <= 2; dx++) {
							for (int dz = -2; dz <= 2; dz++) {
								ground = Math.max(ground, world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(foot.x) + dx,
										MathHelper.floor(foot.z) + dz));
							}
						}
					}
					Vec3d start = new Vec3d(foot.x, ground + 3.0, foot.z);
					Vec3d eye = above(start, look);
					// Lowest over the target wins, then least climbed, then nearest the shooter's own line of sight.
					double score = (eye.y - gap.contact.y) + 2.0 * (eye.y - start.y) + Math.abs(turn) * 0.08 + Math.abs(k - 1.0) * 8.0;
					if (score < bestScore) {
						bestScore = score;
						best = eye;
					}
				}
			}
			gap.wideEye = best;
		}
		return gap.wideEye;
	}

	/** How high the block is over the point of contact as it comes down in the world. */
	public static double blockHeight(double t) {
		double u = MathHelper.clamp((t - GapTimeline.INBOUND) / (GapTimeline.CONTACT - GapTimeline.INBOUND), 0.0, 1.0);
		return 300.0 * Math.pow(1.0 - u, 1.5);
	}

	/** Low beside the point of contact, on the shooter's side, looking up the bridge at the block. */
	private static Pose below(ClientGap gap, Vec3d feet, double t) {
		Vec3d toShooter = new Vec3d(feet.x - gap.contact.x, 0.0, feet.z - gap.contact.z);
		Vec3d away = toShooter.lengthSquared() < 1.0 ? gap.across : toShooter.normalize();
		double k = (t - GapTimeline.INBOUND) / 16.0;
		Vec3d eye = gap.contact.add(away.multiply(16.0 + 4.0 * k)).add(gap.along.multiply(5.0)).add(0.0, 2.0 + 1.5 * k, 0.0);
		Vec3d at = gap.contact.add(0.0, GapRender.blockHeight(t) * 0.8, 0.0);
		double shake = 0.08 + 0.25 * k * k;
		at = at.add(Math.sin(t * 17.0) * shake, Math.cos(t * 13.0) * shake, Math.sin(t * 11.0 + 0.4) * shake);
		return new Pose(eye, at, 1.0);
	}

	/** The bridge standing over the target, the block coming down it, the shooter small in the foreground. */
	private static Pose inbound(ClientGap gap, Vec3d feet, double t) {
		Vec3d eye = witness(gap, feet);
		// Closing on the point of contact and drifting round it as the block comes down, so the shot is never still and
		// the next one (the impact frames) cuts in from a different angle each time.
		double run = ease((t - GapTimeline.INBOUND) / (double) (GapTimeline.CONTACT - GapTimeline.INBOUND));
		eye = orbit(eye, gap.contact, Math.toRadians(-14.0) * run).add(0, 2.5 * run, 0);
		double close = 1.0 - 0.08 * run;
		eye = new Vec3d(gap.contact.x + (eye.x - gap.contact.x) * close, eye.y, gap.contact.z + (eye.z - gap.contact.z) * close);
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
		return new Pose(eye, at, 1.0);
	}

	/** Low under the burst as it swells, creeping in on it, the ground shaking: it towers over everything. */
	private static Pose blast(ClientGap gap, Vec3d feet, double t) {
		Vec3d base = witness(gap, feet);
		double k = ease((t - GapTimeline.BLAST) / (GapTimeline.ERASURE - GapTimeline.BLAST));
		Vec3d toward = new Vec3d(gap.contact.x - base.x, 0, gap.contact.z - base.z).normalize();
		double d = Math.hypot(base.x - gap.contact.x, base.z - gap.contact.z);
		Vec3d eye = base.add(toward.multiply(0.1 * d * k));
		// Sweeping round the burst as it swells, rising a little, the way the erasure's shot goes on from.
		eye = orbit(eye, gap.contact, Math.toRadians(32.0) * k).add(0, 0.06 * blastHalf(gap) * k, 0);
		Vec3d at = gap.contact.add(0, blastHalf(gap) * MathHelper.lerp(k, 0.55, 0.7), 0);
		double shake = 0.7 * Math.exp(-(t - GapTimeline.BLAST) / 9.0) + 0.1;
		eye = eye.add(Math.sin(t * 31.0) * shake, Math.cos(t * 27.0) * shake, Math.sin(t * 23.0 + 1.3) * shake);
		return new Pose(eye, at, 1.0);
	}

	/**
	 * Backing away and up a little from where the burst left the camera, looking at the point of contact as the black
	 * opens there and comes on over everything towards it. Kept low, so the black is seen travelling over the ground.
	 */
	private static Pose erasure(ClientGap gap, Vec3d feet, double t) {
		Pose b = blast(gap, feet, GapTimeline.ERASURE);
		double k = ease((t - GapTimeline.ERASURE) / (GapTimeline.NOTHING - GapTimeline.ERASURE - 20.0));
		Vec3d from = b.eye();
		Vec3d away = new Vec3d(from.x - gap.contact.x, 0, from.z - gap.contact.z).normalize();
		// Low over the ground and backing away from it, drifting on round a little the way the burst's shot was going: the
		// black is seen opening at the point of contact and racing across the ground at the camera, catching it up.
		Vec3d to = from.add(away.multiply(18.0)).add(0, 5.0, 0);
		Vec3d lookFrom = gap.contact.add(0, blastHalf(gap) * 0.7, 0);
		Vec3d eye = orbit(from.lerp(to, k), gap.contact, Math.toRadians(12.0) * k);
		Vec3d at = lookFrom.lerp(gap.contact.add(0, 2, 0), k);
		// Shaking harder and harder as the black comes on at the camera.
		double front = GapTimeline.eraseFront(t);
		double far = Math.abs(eye.x - gap.target.getX() - 0.5) + Math.abs(eye.z - gap.target.getZ() - 0.5);
		double near = MathHelper.clamp(1.0 - (far - front) / 120.0, 0.0, 1.0);
		double shake = 0.15 + 0.9 * near * near;
		at = at.add(Math.sin(t * 29.0) * shake, Math.cos(t * 23.0) * shake, Math.sin(t * 31.0 + 0.7) * shake);
		return new Pose(eye, at, 1.0);
	}

	/**
	 * The impact frames before the last, each a hard cut and each guarded on its own, so no frame is lifted for
	 * something only another one had in the way. Each a crash zoom: it opens pulled back and slams in on the impact
	 * over its few ticks, so every cut carries the motion on into the next.
	 */
	private static Shot frame(ClientGap gap, ClientPlayerEntity player, float tickDelta, double t, Vec3d feet) {
		double e = t - GapTimeline.FRAMES;
		GapFrames.Frame frame = GapFrames.at(e);
		Vec3d eye = player.getCameraPosVec(tickDelta);
		// The guard is worked out once for the whole frame, so it holds still through it.
		double lift = 0.0;
		double length = GapFrames.length(e);
		for (double s = frame.start(); s <= frame.start() + length; s += 1.0) {
			Pose p = slam(gap, framePose(gap, frame.shot(), eye, feet, GapTimeline.FRAMES + s), s, frame);
			lift = Math.max(lift, need(keepLoaded(p, feet)));
		}
		return toShot(keepLoaded(slam(gap, framePose(gap, frame.shot(), eye, feet, t), e, frame), feet), lift);
	}

	/** The last impact frame: the crash zoom, unguarded here, since the look-round guards it with what follows. */
	private static Pose slammed(ClientGap gap, Vec3d eye, Vec3d feet, double t) {
		double e = t - GapTimeline.FRAMES;
		GapFrames.Frame frame = GapFrames.at(e);
		return slam(gap, framePose(gap, frame.shot(), eye, feet, t), e, frame);
	}

	private static Pose slam(ClientGap gap, Pose shot, double e, GapFrames.Frame frame) {
		double p = MathHelper.clamp((e - frame.start()) / GapFrames.length(e), 0.0, 1.0);
		double slam = 0.2 * Math.pow(1.0 - p, 3.0);
		Vec3d dir = shot.at().subtract(shot.eye()).normalize();
		Vec3d back = shot.eye().subtract(dir.multiply(shot.eye().distanceTo(gap.contact) * slam));
		return new Pose(back, shot.at().subtract(shot.eye()).add(back), shot.guard());
	}

	/** The impact frames' shots, standing off far enough that the burst growing out of the point of contact never swallows them. */
	private static Pose framePose(ClientGap gap, int shot, Vec3d eye, Vec3d feet, double t) {
		Vec3d side = gap.across.multiply(-1).add(gap.along.multiply(-0.6)).normalize();
		Vec3d c = gap.contact;
		double h = burstHalf(gap, t);
		return switch (shot) {
			case EXTREME -> new Pose(c.add(side.multiply(1.5 * h + 5.0)).add(0, 1.0, 0), c.add(0, 0.6 * h, 0), 1.0);
			case CONTACT -> new Pose(c.add(side.multiply(2.1 * h + 9.0)).add(0, 4.0, 0), c.add(0, 0.8 * h, 0), 1.0);
			case WIDE -> new Pose(witness(gap, feet), c.add(0, 0.9 * h, 0), 1.0);
			case SIDE -> new Pose(c.add(gap.across.multiply(2.6 * h + 16.0)).add(gap.along.multiply(-6.0)).add(0, 3.0, 0), c.add(0, h, 0), 1.0);
			case LOW -> new Pose(c.add(side.multiply(1.7 * h + 6.0)).add(0, 0.7, 0), c.add(0, 1.4 * h, 0), 1.0);
			default -> new Pose(eye, c.add(0, 0.6 * h, 0), 0.0);
		};
	}

	/**
	 * Half the size of the burst {@code t} ticks into the event: the block's own size when it lands, swelling fast
	 * through the impact frames, slower through the explosion, then falling in on itself to nothing.
	 */
	public static double burstHalf(ClientGap gap, double t) {
		// The same on the server, which kills whoever it swallows.
		return GapTimeline.burstHalf(gap.radius, t);
	}

	/**
	 * In on them, alone in the black, round to the front of them, then back to their eyes. There is nothing in the
	 * black to keep clear of: the world is all gone, and nothing of it stands in front of them (GapRender).
	 */
	private static Pose nothing(ClientGap gap, Vec3d feet, double t, float tickDelta, ClientPlayerEntity player) {
		Vec3d chest = feet.add(0, 1.2, 0);
		Vec3d facing = Vec3d.fromPolar(0, player.getYaw(tickDelta)).normalize();
		Vec3d right = new Vec3d(-facing.z, 0, facing.x);
		if (t < GapTimeline.NOTHING + 68) {
			// Round them on an arc rather than through them, closing from a figure in the black to near beside them,
			// always looking straight at them.
			Vec3d out = new Vec3d(-gap.across.x - gap.along.x * 0.4, 0, -gap.across.z - gap.along.z * 0.4).normalize();
			double k = ease((t - GapTimeline.NOTHING) / 50.0);
			double a0 = Math.atan2(out.z, out.x);
			double a1 = Math.atan2(right.z, right.x);
			double turn = MathHelper.wrapDegrees(Math.toDegrees(a1 - a0));
			double a = a0 + Math.toRadians(turn) * k;
			double r = MathHelper.lerp(k, 11.0, 3.4);
			Vec3d eye = chest.add(Math.cos(a) * r, MathHelper.lerp(k, 3.0, 0.1), Math.sin(a) * r);
			return new Pose(eye, chest, 0.0);
		}
		Vec3d face = feet.add(0, 1.55, 0);
		double k = ease((t - GapTimeline.NOTHING - 68.0) / (GapTimeline.RETURN - GapTimeline.NOTHING - 68.0));
		return new Pose(face.add(facing.multiply(3.2 - 0.5 * k)).add(0, -0.15, 0), face.add(0, -0.25, 0), 0.0);
	}

	/** When each of the rebuild's shots takes over (ticks into the rebuild), and how long the camera takes to get there. */
	private static final double[] REBUILD_SHOTS = {40.0, GapTimeline.REBUILD_SWEEP - 22.0, GapTimeline.REBUILD_SWEEP + 24.0, 300.0, 420.0,
			GapTimeline.REBUILD_END - 62.0};
	private static final double REBUILD_BLEND = 26.0;

	/**
	 * The rebuild, in shots that each glide into the next, all of them on the shooter's side of the hole, where their
	 * world is loaded: out of their eyes to well back behind them, the whole tree growing out of the hole beyond them;
	 * over their shoulder as the light comes down the root to their feet and the ground comes back round them; out
	 * ahead of the edge of the world being put back, looking in at it coming on over the land; up and round as it
	 * passes under and runs away out over the land; wide on all of it as the tree draws back down into the hole; and
	 * back into their eyes.
	 */
	@Nullable
	private static Pose rebuild(ClientGap gap, ClientPlayerEntity player, float tickDelta, double r) {
		if (gap.rebuildFrom == null || r < 0.0 || r >= GapTimeline.REBUILD_END) {
			return null;
		}
		// From the last shot the camera has fully arrived at: the ones before it no longer count.
		int first = 0;
		for (int i = REBUILD_SHOTS.length; i >= 1; i--) {
			if (r >= REBUILD_SHOTS[i - 1] + REBUILD_BLEND) {
				first = i;
				break;
			}
		}
		Pose pose = rebuildShot(first, gap, player, tickDelta, r);
		for (int i = first + 1; i <= REBUILD_SHOTS.length; i++) {
			double w = ease((r - REBUILD_SHOTS[i - 1]) / REBUILD_BLEND);
			if (w > 0.0) {
				pose = mix(pose, rebuildShot(i, gap, player, tickDelta, r), w);
			}
		}
		Vec3d at = pose.at();
		if (r >= GapTimeline.REBUILD_SWEEP) {
			// The light landing at the shooter's feet jolts the picture.
			double jolt = 1.4 * Math.exp(-(r - GapTimeline.REBUILD_SWEEP) / 12.0);
			at = at.add(Math.sin(r * 2.9) * jolt, Math.cos(r * 2.3) * jolt, Math.sin(r * 3.7 + 1.0) * jolt);
		}
		// Guarded all the way back to the shooter's eyes, and only let go of right at them.
		return new Pose(pose.eye(), at, pose.guard() * leaving(pose.eye(), player.getCameraPosVec(tickDelta)));
	}

	/** One of the rebuild's shots. 0 and the last are the shooter's own eyes. */
	private static Pose rebuildShot(int shot, ClientGap gap, ClientPlayerEntity player, float tickDelta, double r) {
		Vec3d eyes = player.getCameraPosVec(tickDelta);
		// Out from the middle of the hole towards the shooter, and across.
		Vec3d out = TreeRender.toward(gap);
		Vec3d side = new Vec3d(-out.z, 0.0, out.x);
		Vec3d centre = gap.contact;
		Vec3d feet = gap.rebuildFrom;
		Vec3d root = TreeRender.foot(gap);
		double tall = TreeRender.height(gap);
		double rim = gap.radius;
		// Until the light goes out through the roots, all of it is black but the tree: nothing to keep clear of.
		double seen = ease((r - GapTimeline.REBUILD_SWEEP + 20.0) / 30.0);
		double ceiling = underClouds();
		return switch (shot) {
			case 1 -> {
				// Well back behind the shooter and up a little, easing in: the whole tree growing out of the hole, the shooter
				// a figure at its rim in front of it.
				double in = 1.0 - 0.12 * MathHelper.clamp((r - REBUILD_SHOTS[0]) / 160.0, 0.0, 1.0);
				Vec3d eye = feet.add(out.multiply(rim * 0.8 * in)).add(side.multiply(rim * 0.22));
				double y = Math.min(Math.max(feet.y + rim * 0.18, root.y + tall * 0.12), Math.max(ceiling, feet.y + 8.0));
				yield new Pose(new Vec3d(eye.x, y, eye.z), root.add(0.0, tall * 0.42, 0.0), seen);
			}
			case 2 -> {
				// Over the shooter's shoulder, looking along the root to the tree's foot as the light comes down it at them and
				// the ground comes back round them: from whichever side behind them the land leaves room.
				double along = MathHelper.clamp((r - GapTimeline.REBUILD_SWEEP + 26.0) / 26.0, 0.0, 1.0);
				double[] v = view(gap, player, 2, new double[] {25, -25, 55, -55, 0, 85, -85}, new double[] {7.0, 9.5},
						p -> shoulder(gap, p[0], p[1], 0.35));
				Pose p = shoulder(gap, v[0], v[1], MathHelper.lerp(along, 0.45, 0.2));
				yield new Pose(p.eye(), p.at(), seen);
			}
			case 3 -> {
				// High over the land on the shooter's side, looking down across the hole at the tree's foot: the ring of white-hot
				// blocks running out from the edge of the hole over the land in every direction, its near side coming on at
				// the camera. Looking down on it, the land hardly gets in the way; where it would, another place is taken.
				double[] v = highView(gap, player);
				yield overHole(gap, v[0], v[1], 28.0, 0.04);
			}
			case 4 -> {
				// Drifting on round the hole from there and a little higher, as the ring runs away out over the land and the
				// sky comes back over it; tilting up from the hole to the tree.
				double[] from = highView(gap, player);
				double[] v = view(gap, player, 4, new double[] {from[0] - 22.0, from[0] + 22.0}, new double[] {from[1]},
						p -> overHole(gap, p[0], p[1], 34.0, 0.2));
				double k = ease((r - REBUILD_SHOTS[3]) / 120.0);
				yield overHole(gap, MathHelper.lerp(k, from[0], v[0]), v[1], MathHelper.lerp(k, 28.0, 34.0), MathHelper.lerp(k, 0.04, 0.2));
			}
			case 5 -> {
				// Wide on all of it from the shooter's side, looking down into the hole: the hole, the land round it, the tree
				// drawing back down into it; from somewhere the floor of the hole can be seen over the rim, and the way back to
				// the shooter's eyes is clear.
				double[] v = view(gap, player, 5, new double[] {20, -20, 0, 40, -40, 60, -60, 90, -90},
						new double[] {rim * 1.55, rim * 1.3, rim * 1.8}, p -> intoHole(gap, p[0], p[1]));
				yield intoHole(gap, v[0], v[1]);
			}
			default -> new Pose(eyes, eyes.add(player.getRotationVec(tickDelta).multiply(10.0)), seen);
		};
	}

	/** Where the high shot over the hole stands (shot 3, and shot 4 drifts on from it). */
	private static double[] highView(ClientGap gap, ClientPlayerEntity player) {
		double rim = gap.radius;
		return view(gap, player, 3, new double[] {28, 10, 46, -10, 64, -28}, new double[] {rim * 1.6, rim * 1.45, rim * 1.8},
				p -> overHole(gap, p[0], p[1], 28.0, 0.04));
	}

	/** As {@link #overHole}, looking down at the floor of the hole where the tree stands, under the rim on the far side. */
	private static Pose intoHole(ClientGap gap, double degrees, double reach) {
		Pose over = overHole(gap, degrees, reach, 14.0, 0.0);
		return new Pose(over.eye(), gap.contact.add(0.0, 2.0, 0.0), 1.0);
	}

	/** Behind the shooter, {@code degrees} round from straight back and {@code reach} off, looking past them at the tree's foot. */
	private static Pose shoulder(ClientGap gap, double degrees, double reach, double towardTree) {
		Vec3d feet = gap.rebuildFrom;
		Vec3d eye = feet.add(TreeRender.toward(gap).rotateY((float) Math.toRadians(degrees)).multiply(reach)).add(0.0, 2.6, 0.0);
		Vec3d root = TreeRender.foot(gap).add(0.0, TreeRender.height(gap) * 0.08, 0.0);
		return new Pose(eye, feet.add(0.0, 1.0, 0.0).lerp(root, towardTree), 1.0);
	}

	/**
	 * Out from the middle of the hole, {@code degrees} round from the way to the shooter and {@code reach} off, high up
	 * ({@code over} clear of the land round it), looking down across the hole at the tree, {@code up} of the way up it.
	 */
	private static Pose overHole(ClientGap gap, double degrees, double reach, double over, double up) {
		Vec3d eye = gap.contact.add(TreeRender.toward(gap).rotateY((float) Math.toRadians(degrees)).multiply(reach));
		return new Pose(new Vec3d(eye.x, high(eye, gap, over), eye.z), TreeRender.foot(gap).add(0.0, TreeRender.height(gap) * up, 0.0),
				1.0);
	}

	/**
	 * Where one of the rebuild's shots stands, as {degrees, reach}, chosen once for each gap from a spread of places:
	 * the one the land least gets in the way of (least lifted to clear it, a clear sight of what it looks at, not up in
	 * the clouds, a clear way back to the shooter's eyes), keeping near the shooter's own side and the first reach given.
	 */
	private static double[] view(ClientGap gap, ClientPlayerEntity player, int shot, double[] degrees, double[] reaches,
			Function<double[], Pose> place) {
		double[] chosen = gap.views.get(shot);
		if (chosen != null) {
			return chosen;
		}
		ClientWorld world = MinecraftClient.getInstance().world;
		Vec3d home = player.getEyePos();
		double clouds = underClouds() + 14.0;
		double best = Double.MAX_VALUE;
		for (double a : degrees) {
			for (double d : reaches) {
				Pose p = keepLoaded(place.apply(new double[] {a, d}), player.getPos());
				double lift = need(p);
				Vec3d eye = p.eye().add(0.0, lift, 0.0);
				double cost = lift * 2.0 + Math.abs(a) * 0.08 + Math.abs(d - reaches[0]) * 0.05;
				if (world != null) {
					// Land close in front of the lens filling much of the picture (a mountainside down one side of it).
					cost += 60.0 * crowded(world, eye, p.at());
				}
				if (world != null && !sees(world, player, eye, p.at())) {
					cost += 40.0;
				}
				if (eye.y > clouds - 6.0) {
					cost += 30.0;
				}
				if (shot == 5 && world != null && !sees(world, player, eye, home)) {
					cost += 15.0;
				}
				if (Boolean.getBoolean("shootingstar.debugViews")) {
					io.github.kortev.shootingstar.ShootingStar.LOGGER.info("[views] shot {} at {} deg {} out: eye {} lift {} crowded {} sees {} cost {}",
							shot, a, Math.round(d), eye, String.format("%.1f", lift), world == null ? -1 : String.format("%.2f", crowded(world, eye, p.at())),
							world != null && sees(world, player, eye, p.at()), String.format("%.1f", cost));
				}
				if (cost < best) {
					best = cost;
					chosen = new double[] {a, d};
				}
			}
		}
		gap.views.put(shot, chosen);
		return chosen;
	}

	/**
	 * How much of the top half of the picture from {@code eye} looking at {@code at} is land standing between the lens and
	 * what it looks at, 0 to 1: a mountainside reaching up one side of the frame. (The land below the middle of the
	 * picture is the land it is meant to be looking down on.)
	 */
	private static double crowded(ClientWorld world, Vec3d eye, Vec3d at) {
		Vec3d look = at.subtract(eye);
		double reach = Math.min(160.0, look.length());
		look = look.normalize();
		double yaw = Math.atan2(look.z, look.x);
		double pitch = Math.asin(MathHelper.clamp(look.y, -1.0, 1.0));
		int hit = 0;
		int rays = 0;
		for (double dy : new double[] {-0.6, -0.4, -0.2, 0.0, 0.2, 0.4, 0.6}) {
			for (double dp : new double[] {0.0, 0.15, 0.3}) {
				double a = yaw + dy;
				double b = MathHelper.clamp(pitch + dp, -1.5, 1.5);
				Vec3d dir = new Vec3d(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
				rays++;
				for (double s = 3.0; s <= reach; s += 3.0) {
					Vec3d p = eye.add(dir.multiply(s));
					if (p.y < world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(p.x), MathHelper.floor(p.z))) {
						hit++;
						break;
					}
				}
			}
		}
		return hit / (double) rays;
	}

	/** Whether nothing stands between {@code eye} and {@code at} (to within a few blocks of it). */
	private static boolean sees(ClientWorld world, ClientPlayerEntity player, Vec3d eye, Vec3d at) {
		HitResult hit = world.raycast(new RaycastContext(eye, at, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getPos().distanceTo(at) < 4.0;
	}

	// --- the guard ------------------------------------------------------------------------

	/**
	 * The shot at {@code t}, lifted clear of the world: by as much as it needs now, and as much as it will need over
	 * the next few ticks and did over the last few, faded with how far off that is, so it is already rising when a hill
	 * comes and settles slowly after it. The same answer however many times it is asked in a frame.
	 */
	@Nullable
	private static Shot guarded(DoubleFunction<Pose> poses, DoubleToIntFunction runs, double t, Vec3d anchor) {
		Pose now = poses.apply(t);
		if (now == null) {
			return null;
		}
		now = keepLoaded(now, anchor);
		double lift = need(now);
		int run = runs.applyAsInt(t);
		for (int s = MathHelper.ceil(t - BEHIND); s <= MathHelper.floor(t + AHEAD); s++) {
			double w = window(s - t);
			if (w <= 0.0 || runs.applyAsInt(s) != run) {
				continue;
			}
			Pose p = poses.apply(s);
			if (p != null && p.guard() > 0.0) {
				lift = Math.max(lift, need(keepLoaded(p, anchor)) * w);
			}
		}
		return toShot(now, lift);
	}

	/** How much a sample {@code d} ticks away (ahead if positive) counts for. */
	private static double window(double d) {
		double a = Math.abs(d);
		if (a <= HOLD) {
			return 1.0;
		}
		return ease(1.0 - (a - HOLD) / ((d > 0.0 ? AHEAD : BEHIND) - HOLD));
	}

	/** How far the lens has to go up to be clear of the ground under it and see over what lies between it and its subject. */
	private static double need(Pose p) {
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null || p.guard() <= 0.0) {
			return 0.0;
		}
		Vec3d eye = p.eye();
		double ground = 0.0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				ground = Math.max(ground, world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(eye.x) + dx, MathHelper.floor(eye.z) + dz)
						+ CLEARANCE - eye.y);
			}
		}
		// Nor right up against a slope or a cliff beside it: what stands within a few blocks is kept below it too, by
		// less the further off it is.
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			for (double d = 2.5; d <= 5.0; d += 2.5) {
				int x = MathHelper.floor(eye.x + Math.cos(a) * d);
				int z = MathHelper.floor(eye.z + Math.sin(a) * d);
				ground = Math.max(ground, world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) + CLEARANCE - 0.5 * d - eye.y);
			}
		}
		double sight = Math.min(MOST_FOR_SIGHT, overTerrain(world, eye, p.at()) - eye.y);
		return Math.max(ground, sight) * p.guard();
	}

	/** How high {@code eye} must be for the line from it to {@code at} to pass over the ground all the way (but the last stretch). */
	private static double overTerrain(ClientWorld world, Vec3d eye, Vec3d at) {
		double y = eye.y;
		for (int i = 1; i <= 34; i++) {
			double t = i / 40.0;
			Vec3d p = eye.lerp(at, t);
			double top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(p.x), MathHelper.floor(p.z)) + SIGHT_MARGIN;
			// The line's height there is y + (at.y - y) * t: raise y until that clears the ground.
			y = Math.max(y, (top - at.y * t) / (1.0 - t));
		}
		return y;
	}

	/**
	 * Keeps the camera over ground the client has: past the distance it loads round the player there is nothing under
	 * it but sky. Pulled straight back towards them, which leaves a path that only grazes the edge unbroken.
	 */
	private static Pose keepLoaded(Pose p, Vec3d anchor) {
		MinecraftClient client = MinecraftClient.getInstance();
		double reach = Math.max(48.0, client.options.getClampedViewDistance() * 16.0 - 24.0);
		Vec3d d = new Vec3d(p.eye().x - anchor.x, 0.0, p.eye().z - anchor.z);
		double l = d.length();
		if (l <= reach) {
			return p;
		}
		Vec3d eye = new Vec3d(anchor.x + d.x * reach / l, p.eye().y, anchor.z + d.z * reach / l);
		return new Pose(eye, p.at(), p.guard());
	}

	private static Shot toShot(Pose p, double lift) {
		return lookAt(p.eye().add(0.0, Math.max(0.0, lift), 0.0), p.at());
	}

	/** How much a camera that started in the shooter's head is clear of it: nothing there, all of it a few blocks out. */
	private static double leaving(Vec3d eye, Vec3d head) {
		return MathHelper.clamp((eye.distanceTo(head) - 0.8) / 2.5, 0.0, 1.0);
	}

	/** {@code p} set on the ground under it (the highest of the blocks round it), {@code height} up. */
	private static Vec3d surface(Vec3d p, double height) {
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) {
			return p;
		}
		int top = Integer.MIN_VALUE;
		for (int dx = -2; dx <= 2; dx += 2) {
			for (int dz = -2; dz <= 2; dz += 2) {
				top = Math.max(top, world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(p.x) + dx, MathHelper.floor(p.z) + dz));
			}
		}
		return new Vec3d(p.x, top <= world.getBottomY() ? p.y : top + height, p.z);
	}

	/**
	 * How high a camera over {@code p} stands to look down across the hole: {@code over} clear of the highest ground
	 * round it and at least as high as the hole is wide, under the clouds where the land leaves room.
	 */
	private static double high(Vec3d p, ClientGap gap, double over) {
		ClientWorld world = MinecraftClient.getInstance().world;
		double ground = gap.contact.y;
		if (world != null) {
			for (int dx = -12; dx <= 12; dx += 6) {
				for (int dz = -12; dz <= 12; dz += 6) {
					ground = Math.max(ground, world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(p.x) + dx, MathHelper.floor(p.z) + dz));
				}
			}
		}
		double wanted = Math.max(ground + over, gap.contact.y + 0.9 * gap.radius);
		return Math.min(wanted, Math.max(underClouds(), ground + 8.0));
	}

	/** A little under where the clouds are, so a camera up high looks out under them rather than through them. */
	private static double underClouds() {
		ClientWorld world = MinecraftClient.getInstance().world;
		float clouds = world == null ? Float.NaN : world.getDimensionEffects().getCloudsHeight();
		return Float.isNaN(clouds) ? 180.0 : clouds - 20.0;
	}

	/** {@code eye} turned {@code radians} round the vertical through {@code centre}, at the same height. */
	private static Vec3d orbit(Vec3d eye, Vec3d centre, double radians) {
		Vec3d flat = new Vec3d(eye.x - centre.x, 0.0, eye.z - centre.z).rotateY((float) radians);
		return new Vec3d(centre.x + flat.x, eye.y, centre.z + flat.z);
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

	/**
	 * Part way from one pose to another: the eye along the line between, the look turned the short way round (and
	 * the point it looks at as far off as the two make it), the guard faded across.
	 */
	private static Pose mix(Pose a, Pose b, double k) {
		Vec3d eye = a.eye().lerp(b.eye(), k);
		Vec3d da = a.at().subtract(a.eye());
		Vec3d db = b.at().subtract(b.eye());
		double yawA = Math.atan2(da.z, da.x);
		double yaw = yawA + MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(db.z, db.x) - yawA)) * Math.PI / 180.0 * k;
		double pitch = MathHelper.lerp(k, Math.atan2(da.y, Math.hypot(da.x, da.z)), Math.atan2(db.y, Math.hypot(db.x, db.z)));
		double length = MathHelper.lerp(k, da.length(), db.length());
		Vec3d dir = new Vec3d(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
		return new Pose(eye, eye.add(dir.multiply(length)), MathHelper.lerp(k, a.guard(), b.guard()));
	}

	static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
