package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.client.camera.CameraDirector.Shot;
import io.github.kortev.shootingstar.gap.GapTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
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
		// Skipped: the shooter's own eyes for all of it, the rebuild too, free to move, as anyone else sees it.
		if (gap.feedSkipped) {
			return null;
		}
		if (gap.rebuildAt >= 0) {
			return rebuild(gap, player, tickDelta, gap.rebuild(tickDelta));
		}
		if (t < GapTimeline.RISE || t >= GapTimeline.END) {
			return null;
		}
		if (t < GapTimeline.FEED) {
			return rise(gap, player, tickDelta, (t - GapTimeline.RISE) / (GapTimeline.FEED - GapTimeline.RISE));
		}
		if (t < GapTimeline.INBOUND) {
			// Behind the feed the world goes on from the shooter's eyes.
			return null;
		}
		if (t < GapTimeline.CONTACT) {
			// Out of the white of the cloud deck: from the ground beside the point of contact, looking straight up the
			// bridge at it coming down, a star getting bigger; then swinging out to the wide shot for the last of it.
			double out = ease((t - GapTimeline.INBOUND - 9.0) / 7.0);
			Shot wide = inbound(gap, feet, t);
			return out >= 1.0 ? wide : mix(below(gap, feet, t), wide, out);
		}
		if (t < GapTimeline.BLAST) {
			// Each frame a crash zoom: it opens pulled back and slams in on the impact over its few ticks, so every cut
			// carries the motion on into the next.
			double e = t - GapTimeline.FRAMES;
			GapFrames.Frame frame = GapFrames.at(e);
			Shot shot = frameShot(gap, frame.shot(), eye, feet, t);
			double p = MathHelper.clamp((e - frame.start()) / GapFrames.length(e), 0.0, 1.0);
			double slam = 0.2 * Math.pow(1.0 - p, 3.0);
			Vec3d from = new Vec3d(shot.x(), shot.y(), shot.z());
			Vec3d dir = Vec3d.fromPolar(shot.pitch(), shot.yaw());
			Vec3d back = from.subtract(dir.multiply(from.distanceTo(gap.contact) * slam));
			return new Shot(back.x, back.y, back.z, shot.yaw(), shot.pitch());
		}
		if (t < GapTimeline.ERASURE) {
			// Out of the last impact frame not on a cut but a swing: the frame's camera carries on and gives way to the
			// blast's as the burst swells, so the explosion opens out of the frame that showed it hitting.
			double into = (t - GapTimeline.BLAST) / 14.0;
			if (into < 1.0) {
				Shot last = frameShot(gap, GapFrames.at(GapTimeline.BLAST - GapTimeline.FRAMES - 0.01).shot(), eye, feet, t);
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
	private static Shot below(ClientGap gap, Vec3d feet, double t) {
		Vec3d toShooter = new Vec3d(feet.x - gap.contact.x, 0.0, feet.z - gap.contact.z);
		Vec3d away = toShooter.lengthSquared() < 1.0 ? gap.across : toShooter.normalize();
		double k = (t - GapTimeline.INBOUND) / 16.0;
		Vec3d eye = gap.contact.add(away.multiply(16.0 + 4.0 * k)).add(gap.along.multiply(5.0)).add(0.0, 2.0 + 1.5 * k, 0.0);
		Vec3d at = gap.contact.add(0.0, GapRender.blockHeight(t) * 0.8, 0.0);
		double shake = 0.08 + 0.25 * k * k;
		at = at.add(Math.sin(t * 17.0) * shake, Math.cos(t * 13.0) * shake, Math.sin(t * 11.0 + 0.4) * shake);
		return lookAt(clear(eye, at), at);
	}

	/** The bridge standing over the target, the block coming down it, the shooter small in the foreground. */
	private static Shot inbound(ClientGap gap, Vec3d feet, double t) {
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
		return lookAt(eye, at);
	}

	/** Low under the burst as it swells, creeping in on it, the ground shaking: it towers over everything. */
	private static Shot blast(ClientGap gap, Vec3d feet, double t) {
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
		return lookAt(clear(eye, at), at);
	}

	/**
	 * Backing away and up a little from where the burst left the camera, looking at the point of contact as the black
	 * opens there and comes on over everything towards it. Kept low, so the black is seen travelling over the ground.
	 */
	private static Shot erasure(ClientGap gap, Vec3d feet, double t) {
		Shot b = blast(gap, feet, GapTimeline.ERASURE);
		double k = ease((t - GapTimeline.ERASURE) / (GapTimeline.NOTHING - GapTimeline.ERASURE - 20.0));
		Vec3d from = new Vec3d(b.x(), b.y(), b.z());
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
		return lookAt(clear(eye, at), at);
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
			return lookAt(eye, chest);
		}
		if (t < GapTimeline.RETURN) {
			Vec3d face = feet.add(0, 1.55, 0);
			double k = ease((t - GapTimeline.NOTHING - 68.0) / (GapTimeline.RETURN - GapTimeline.NOTHING - 68.0));
			return lookAt(face.add(facing.multiply(3.2 - 0.5 * k)).add(0, -0.15, 0), face.add(0, -0.25, 0));
		}
		return null;
	}

	/** When each of the rebuild's shots takes over (ticks into the rebuild), and how long the camera takes to get there. */
	private static final double[] REBUILD_SHOTS = {40.0, GapTimeline.REBUILD_SWEEP - 22.0, GapTimeline.REBUILD_SWEEP + 24.0, 300.0, 420.0,
			GapTimeline.REBUILD_END - 62.0};
	private static final double REBUILD_BLEND = 26.0;

	/**
	 * The rebuild, in shots that each glide into the next: out of the shooter's eyes to far back on their side of the
	 * hole, the whole of the tree, the hole and the ground round it in the picture as it grows; down low behind them as
	 * the light comes down the root; out ahead of the edge of the world being put back, looking back in as it comes on over
	 * the ground; high over everything, under the clouds, the tree to one side, as that ring spreads out across the land; wide on all of it as
	 * the tree draws back; and back into their eyes.
	 */
	@Nullable
	private static Shot rebuild(ClientGap gap, ClientPlayerEntity player, float tickDelta, double r) {
		if (gap.rebuildFrom == null || r >= GapTimeline.REBUILD_END) {
			return null;
		}
		Vec3d[] pose = rebuildPose(0, gap, player, tickDelta, r);
		for (int i = 1; i <= REBUILD_SHOTS.length; i++) {
			double w = ease((r - REBUILD_SHOTS[i - 1]) / REBUILD_BLEND);
			if (w > 0.0) {
				Vec3d[] next = rebuildPose(i, gap, player, tickDelta, r);
				pose = new Vec3d[] {pose[0].lerp(next[0], w), pose[1].lerp(next[1], w)};
			}
		}
		Vec3d eye = pose[0];
		Vec3d at = pose[1];
		if (r >= GapTimeline.REBUILD_SWEEP) {
			// The light landing at the shooter's feet jolts the picture.
			double jolt = 2.0 * Math.exp(-(r - GapTimeline.REBUILD_SWEEP) / 12.0);
			at = at.add(Math.sin(r * 2.9) * jolt, Math.cos(r * 2.3) * jolt, Math.sin(r * 3.7 + 1.0) * jolt);
		}
		if (r < 1.0) {
			rebuildLift = 0.0;
		}
		// Hills in the way: rise over them, smoothly, rather than being pulled in against them.
		double need = overTerrain(eye, at, 4.0).y - eye.y;
		rebuildLift = need > rebuildLift ? MathHelper.lerp(0.2, rebuildLift, need) : MathHelper.lerp(0.03, rebuildLift, need);
		// Only on the shots of their own: never lifting the shooter's eyes, coming out or going back in.
		double away = ease((r - REBUILD_SHOTS[0]) / REBUILD_BLEND) * (1.0 - ease((r - REBUILD_SHOTS[REBUILD_SHOTS.length - 1]) / REBUILD_BLEND));
		eye = eye.add(0.0, rebuildLift * away, 0.0);
		return lookAt(clear(eye, at), at);
	}

	/** How far the rebuild's camera is lifted over whatever stands between it and what it looks at. */
	private static double rebuildLift;

	/** One of the rebuild's shots: eye and the point it looks at. 0 and the last are the shooter's own eyes. */
	private static Vec3d[] rebuildPose(int shot, ClientGap gap, ClientPlayerEntity player, float tickDelta, double r) {
		Vec3d eyes = player.getCameraPosVec(tickDelta);
		Vec3d feet = player.getLerpedPos(tickDelta);
		// Out from the middle of the hole towards the shooter, and across.
		Vec3d out = TreeRender.toward(gap);
		Vec3d side = new Vec3d(-out.z, 0.0, out.x);
		Vec3d centre = gap.contact;
		double tall = TreeRender.height(gap);
		double rim = gap.radius;
		return switch (shot) {
			case 1 -> {
				// Far back on the shooter's side, a little up, easing in: the whole tree, the hole, the ground round it.
				double in = 1.0 - 0.12 * MathHelper.clamp((r - REBUILD_SHOTS[0]) / 160.0, 0.0, 1.0);
				Vec3d eye = centre.add(out.multiply(rim * 2.1 * in)).add(side.multiply(rim * 0.35)).add(0.0, rim * 0.3 + 6.0, 0.0);
				yield new Vec3d[] {aboveGround(eye, 3.0), centre.add(0.0, tall * 0.5, 0.0)};
			}
			case 2 -> {
				// Low behind the shooter, looking along the root to the tree's foot as the light comes down it at them.
				double along = MathHelper.clamp((r - GapTimeline.REBUILD_SWEEP + 26.0) / 26.0, 0.0, 1.0);
				Vec3d eye = feet.add(out.multiply(4.0)).add(side.multiply(2.5)).add(0.0, 1.6, 0.0);
				yield new Vec3d[] {aboveGround(eye, 1.2), centre.lerp(feet, 0.15 + 0.6 * along).add(0.0, 2.0 + tall * 0.1 * (1.0 - along), 0.0)};
			}
			case 3 -> {
				// Out ahead of the edge of the world being put back, up a little, looking back in at it as it comes on over
				// the ground towards the camera, the tree beyond.
				double front = MathHelper.clamp(GapRender.rebuildFront(gap, r), rim, rim + 90.0);
				Vec3d way = out.rotateY((float) Math.toRadians(-40.0));
				Vec3d eye = aboveGround(centre.add(way.multiply(front + 70.0)), 22.0);
				Vec3d at = aboveGround(centre.add(way.multiply(front - 10.0)), 2.0);
				yield new Vec3d[] {eye, at.lerp(centre.add(0.0, tall * 0.25, 0.0), 0.2)};
			}
			case 4 -> {
				// High over the land on the side with the least in the way, under the clouds, turning slowly, across the ring
				// spreading out over it to the tree.
				Vec3d way = openSide(gap);
				Vec3d across = new Vec3d(-way.z, 0.0, way.x);
				double turn = Math.toRadians(30.0) * MathHelper.clamp((r - REBUILD_SHOTS[3]) / 140.0, 0.0, 1.0);
				Vec3d high = centre.add(way.multiply(rim * 2.0)).add(across.multiply(rim * 0.5));
				high = new Vec3d(high.x, MathHelper.clamp(centre.y + tall, centre.y + 40.0, Math.max(centre.y + 40.0, underClouds())), high.z);
				yield new Vec3d[] {aboveGround(orbit(high, centre, turn), 12.0), centre.add(0.0, tall * 0.2, 0.0)};
			}
			case 5 -> {
				// Wide on all of it, from the same open side, as the tree draws back down into the hole.
				Vec3d way = openSide(gap);
				Vec3d eye = centre.add(way.multiply(rim * 2.6)).add(0.0, Math.min(tall * 0.6, Math.max(30.0, underClouds() - centre.y)), 0.0);
				yield new Vec3d[] {aboveGround(eye, 6.0), centre.add(0.0, tall * 0.35, 0.0)};
			}
			default -> new Vec3d[] {eyes, eyes.add(player.getRotationVec(tickDelta).multiply(10.0))};
		};
	}

	/** {@code p}, raised if need be to stand at least {@code clearance} over the ground under it. */
	private static Vec3d aboveGround(Vec3d p, double clearance) {
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) {
			return p;
		}
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(p.x), MathHelper.floor(p.z));
		return p.y < top + clearance ? new Vec3d(p.x, top + clearance, p.z) : p;
	}

	/** {@code eye}, raised if need be so that the line from it to {@code at} passes over the ground all the way. */
	private static Vec3d overTerrain(Vec3d eye, Vec3d at, double margin) {
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) {
			return eye;
		}
		double y = eye.y;
		for (int i = 1; i < 40; i++) {
			double t = i / 40.0;
			if (t > 0.9) {
				break;
			}
			Vec3d p = eye.lerp(at, t);
			double top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(p.x), MathHelper.floor(p.z)) + margin;
			// The line's height there is y + (at.y - y) * t: raise y until that clears the ground.
			y = Math.max(y, (top - at.y * t) / (1.0 - t));
		}
		return new Vec3d(eye.x, y, eye.z);
	}

	private static int openFor = -1;
	private static Vec3d openWay = new Vec3d(1.0, 0.0, 0.0);

	/**
	 * Which way out from the hole the land lies lowest: the way the wide shots look in from, so no hill stands between
	 * them and the tree. Chosen once for each gap, so the camera does not swing about as it goes.
	 */
	private static Vec3d openSide(ClientGap gap) {
		ClientWorld world = MinecraftClient.getInstance().world;
		if (openFor == gap.id || world == null) {
			return openWay;
		}
		Vec3d toward = TreeRender.toward(gap);
		double best = Double.MAX_VALUE;
		for (int i = 0; i < 16; i++) {
			// Turned either way from the shooter's side, the shooter's side itself first, favoured a little.
			double a = (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * Math.PI / 8.0;
			Vec3d way = toward.rotateY((float) a);
			double highest = Double.NEGATIVE_INFINITY;
			for (double d = gap.radius * 1.05; d <= gap.radius * 2.7; d += 4.0) {
				Vec3d p = gap.contact.add(way.multiply(d));
				highest = Math.max(highest, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(p.x), MathHelper.floor(p.z)));
			}
			double score = highest + 2.0 * (i + 1) / 2;
			if (score < best) {
				best = score;
				openWay = way;
			}
		}
		openFor = gap.id;
		return openWay;
	}

	/** A little under where the clouds are, so a camera up high looks down past them rather than through them. */
	private static double underClouds() {
		ClientWorld world = MinecraftClient.getInstance().world;
		float clouds = world == null ? Float.NaN : world.getDimensionEffects().getCloudsHeight();
		return Float.isNaN(clouds) ? 180.0 : clouds - 12.0;
	}

	/** {@code eye} turned {@code radians} round the vertical through {@code centre}, at the same height. */
	private static Vec3d orbit(Vec3d eye, Vec3d centre, double radians) {
		Vec3d flat = new Vec3d(eye.x - centre.x, 0.0, eye.z - centre.z).rotateY((float) radians);
		return new Vec3d(centre.x + flat.x, eye.y, centre.z + flat.z);
	}

	/** Pulls {@code eye} in towards {@code at} until nothing stands between them. */
	static Vec3d clear(Vec3d eye, Vec3d at) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null || client.player == null) {
			return eye;
		}
		if (world.getBlockState(BlockPos.ofFloored(at)).isOpaqueFullCube(world, BlockPos.ofFloored(at))) {
			// Looking at a point in the ground: anything at all would be "in the way".
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

	/** Part way from one shot to another: the eye along the line between, the look turned the short way round. */
	static Shot mix(Shot a, Shot b, double k) {
		float yaw = a.yaw() + (float) k * MathHelper.wrapDegrees(b.yaw() - a.yaw());
		return new Shot(MathHelper.lerp(k, a.x(), b.x()), MathHelper.lerp(k, a.y(), b.y()), MathHelper.lerp(k, a.z(), b.z()), yaw,
				MathHelper.lerp((float) k, a.pitch(), b.pitch()));
	}

	static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
