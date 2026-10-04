package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.client.camera.CameraDirector.Shot;
import io.github.kortev.shootingstar.gap.GapTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * The shooter's shots through a Ginnungagap event: first person while the key turns; then, from the white, down low
 * behind them tilting up at the sky over the target as it breaks; a long way off to the side for the shard of the
 * other universe falling out of it, with a cutaway up from their eyes; on the ground under the shard as it comes
 * down; low at the point of contact; a hard cut for every impact frame; the wide shot again as everything is
 * erased; then in on them, alone, and back to their eyes. No shot ever starts inside a hill.
 */
public final class GapCamera {
	/** Impact frame shots. */
	public static final int EXTREME = 0;
	public static final int CONTACT = 1;
	public static final int WIDE = 2;
	public static final int SIDE = 3;
	public static final int LOW = 4;
	public static final int EYES = 5;

	/** The cuts: out to the wide shot, round behind the target and back, and down under the shard. */
	public static final int CUT_WIDE = GapTimeline.TEAR + 30;
	public static final int CUT_REVERSE = GapTimeline.CLOSING + 36;
	public static final int CUT_REVERSE_END = CUT_REVERSE + 22;
	public static final int CUT_UNDER = GapTimeline.CONTACT - 26;

	/** The places tried for a shot, nearest the one it would like first. */
	private static final double[] TURNS = {0, 45, -45, 90, -90, 135, -135, 180};

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
		if (t < GapTimeline.TURNED || t >= GapTimeline.END) {
			return null;
		}
		if (t < GapTimeline.CONTACT) {
			if (t < CUT_WIDE) {
				return lookUp(gap, feet, t);
			}
			if (t >= CUT_REVERSE && t < CUT_REVERSE_END) {
				return reverse(gap, t);
			}
			if (t >= CUT_UNDER) {
				return under(gap, t);
			}
			return wide(gap, feet, t);
		}
		if (t < GapTimeline.FRAMES) {
			return contact(gap, t);
		}
		if (t < GapTimeline.ERASURE) {
			return frameShot(gap, GapFrames.at(t - GapTimeline.FRAMES).shot(), eye, feet);
		}
		if (t < GapTimeline.NOTHING) {
			return erasure(gap, feet, t);
		}
		return nothing(gap, feet, t, tickDelta, player);
	}

	/**
	 * Over the shooter's shoulder, their head and shoulders dark against the sky over the target as it starts to
	 * break, then tilting up off them as it opens.
	 */
	private static Shot lookUp(ClientGap gap, Vec3d feet, double t) {
		double k = ease((t - GapTimeline.TEAR - 4.0) / (CUT_WIDE - GapTimeline.TEAR - 4.0));
		Vec3d head = feet.add(0, 1.6, 0);
		return lookAt(clear(lookUpEye(gap, feet, side(gap, feet), k), head), gap.contact.add(0, 22 + 52 * k, 0));
	}

	private static Vec3d lookUpEye(ClientGap gap, Vec3d feet, double side, double k) {
		return feet.add(gap.along.multiply(-3.2 + 0.6 * k)).add(gap.across.multiply(1.1 * side)).add(0, 2.0 - 0.6 * k, 0);
	}

	/** Which side of the shooter the shot behind them stands on: whichever has more open space. Chosen once. */
	private static double side(ClientGap gap, Vec3d feet) {
		if (gap.side == 0) {
			Vec3d chest = feet.add(0, 1.4, 0);
			double best = -1.0;
			for (int s = 1; s >= -1; s -= 2) {
				Vec3d eye = clear(lookUpEye(gap, feet, s, 0.0), chest);
				double score = score(eye, gap.contact.add(0, 22, 0), 3.0);
				if (score > best + 0.25) {
					best = score;
					gap.side = s;
				}
			}
		}
		return gap.side;
	}

	/**
	 * Of eight places round {@code at}, {@code distance} out and {@code height} up, the one with the most open space
	 * in front of it while it looks at {@code look}.
	 */
	private static Vec3d roomiest(Vec3d at, double distance, double height, Vec3d look, Vec3d prefer) {
		Vec3d best = null;
		double bestScore = -1.0;
		double base = Math.atan2(prefer.z, prefer.x);
		for (int i = 0; i < TURNS.length; i++) {
			double a = base + Math.toRadians(TURNS[i]);
			Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
			Vec3d eye = clear(at.add(dir.multiply(distance)).add(0, height, 0), look);
			// The preferred side wins a tie.
			double score = score(eye, look, distance * 0.7) - 0.05 * i;
			if (score > bestScore) {
				bestScore = score;
				best = eye;
			}
		}
		return best;
	}

	/** How good a camera at {@code eye} looking at {@code at} is: open space across its frame, and not pulled in too close. */
	private static double score(Vec3d eye, Vec3d at, double wanted) {
		return Math.min(room(eye, at), 12.0 * Math.min(1.0, eye.distanceTo(at) / wanted));
	}

	/** The nearest terrain across the middle and top of the frame of a camera at {@code eye} looking at {@code at}, up to 12 blocks. */
	private static double room(Vec3d eye, Vec3d at) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null || client.player == null) {
			return 12.0;
		}
		Vec3d f = at.subtract(eye).normalize();
		Vec3d r = f.crossProduct(new Vec3d(0, 1, 0));
		r = r.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : r.normalize();
		Vec3d u = r.crossProduct(f);
		double room = 12.0;
		for (int y = 0; y <= 1; y++) {
			for (int x = -1; x <= 1; x++) {
				Vec3d dir = f.add(r.multiply(0.6 * x)).add(u.multiply(0.35 * y)).normalize();
				HitResult hit = world.raycast(new RaycastContext(eye, eye.add(dir.multiply(12.0)), RaycastContext.ShapeType.VISUAL,
						RaycastContext.FluidHandling.NONE, client.player));
				if (hit.getType() != HitResult.Type.MISS) {
					room = Math.min(room, hit.getPos().distanceTo(eye));
				}
			}
		}
		return room;
	}

	/**
	 * From well beyond the target, looking back the way the shooter faces: the shard hanging huge over the target in
	 * the top of the frame, the shooter small on their hill at the bottom. It shows how big the thing is.
	 */
	private static Shot reverse(ClientGap gap, double t) {
		if (gap.reverseEye == null) {
			gap.reverseEye = clear(gap.contact.add(gap.along.multiply(110.0)).add(gap.across.multiply(-15.0)).add(0, 40.0, 0),
					gap.contact.add(0, 20, 0));
		}
		Vec3d eye = gap.reverseEye;
		Vec3d back = new Vec3d(gap.shooterPos.x - eye.x, 0, gap.shooterPos.z - eye.z).normalize();
		double pitch = Math.toRadians(18.0 - 4.0 * ease((t - CUT_REVERSE) / (CUT_REVERSE_END - CUT_REVERSE)));
		return lookAt(eye, eye.add(back.multiply(Math.cos(pitch))).add(0, Math.sin(pitch), 0));
	}

	/** Rising and pulling back from the wide shot until it looks down on the point of contact, the black spreading out from it. */
	private static Shot erasure(ClientGap gap, Vec3d feet, double t) {
		Shot w = wide(gap, feet, GapTimeline.CONTACT);
		double k = ease((t - GapTimeline.ERASURE) / (GapTimeline.NOTHING - GapTimeline.ERASURE - 30.0));
		Vec3d from = new Vec3d(w.x(), w.y(), w.z());
		Vec3d away = new Vec3d(from.x - gap.contact.x, 0, from.z - gap.contact.z);
		double d = away.length();
		away = away.normalize();
		Vec3d to = gap.contact.add(away.multiply(d * 1.05)).add(0, Math.max(0.0, from.y - gap.contact.y) + 40.0, 0);
		return lookAt(from.lerp(to, k), gap.contact.lerp(feet, 0.2 * (1.0 - k)).add(0, 10.0 * (1.0 - k), 0));
	}

	/** On the ground near the target, looking up at the shard as it comes down on top of it. */
	private static Shot under(ClientGap gap, double t) {
		if (gap.underEye == null) {
			Vec3d side = gap.across.multiply(-1).add(gap.along.multiply(-0.6)).normalize();
			gap.underEye = roomiest(gap.contact, 17.0, 1.2, gap.contact.add(0, 3, 0), side);
		}
		double tip = Math.max(0.0, GapTimeline.shardTip(t));
		return lookAt(gap.underEye, gap.contact.add(0, 3.0 + 0.8 * tip, 0));
	}

	/**
	 * A long way off to the side and level with the middle of it all, so one frame holds our ground, the shooter,
	 * the target, the broken sky and the shard coming down out of it.
	 */
	static Shot wide(ClientGap gap, Vec3d feet, double t) {
		Vec3d mid = feet.lerp(gap.contact, 0.62);
		if (gap.wideEye == null) {
			double d = Math.hypot(gap.contact.x - feet.x, gap.contact.z - feet.z);
			// Far enough back to hold it all, but inside the loaded world, so the ground under the camera is there.
			double loaded = MinecraftClient.getInstance().options.getClampedViewDistance() * 16.0 - 40.0;
			double room = Math.sqrt(Math.max(0.0, loaded * loaded - 0.62 * d * 0.62 * d));
			double back = Math.max(50.0, Math.min(Math.max(d * 1.6, 116.0), room));
			Vec3d eye = new Vec3d(mid.x, gap.surface + 22, mid.z).add(gap.across.multiply(back));
			gap.wideEye = above(eye, gap.contact.add(0, 3, 0));
		}
		double drift = ease((t - GapTimeline.TEAR) / (GapTimeline.CONTACT - GapTimeline.TEAR));
		Vec3d eye = gap.wideEye.lerp(new Vec3d(mid.x, gap.wideEye.y, mid.z), 0.1 * drift).add(0, 5 * drift, 0);
		// Tilted up to hold the shard's tip in the upper part of the frame while it hangs high, down with it as it
		// falls; never so far up that only sky is left, nor below the ground at the target.
		double d = Math.hypot(mid.x - eye.x, mid.z - eye.z);
		double tip = gap.surface + Math.max(0.0, GapTimeline.shardTip(t));
		double lowest = Math.atan2(gap.surface + 3.0 - eye.y, d);
		double highest = Math.max(lowest, Math.atan2(gap.surface - eye.y, d) + Math.toRadians(42.0));
		double pitch = MathHelper.clamp(Math.atan2(tip - eye.y, d) - Math.toRadians(8.0), lowest, highest);
		return lookAt(eye, new Vec3d(mid.x, eye.y + d * Math.tan(pitch), mid.z));
	}

	/** Down at the point of contact as the tip comes down and touches. */
	private static Shot contact(ClientGap gap, double t) {
		double k = ease((t - GapTimeline.CONTACT) / (GapTimeline.FRAMES - GapTimeline.CONTACT));
		Vec3d side = gap.across.multiply(-1).add(gap.along.multiply(-0.6)).normalize();
		Vec3d at = gap.contact.add(0, 2.6, 0);
		Vec3d eye = clear(gap.contact.add(side.multiply(9.0 - 1.5 * k)).add(0, 1.4, 0), at);
		// The hit shakes it.
		double s = Math.exp(-(t - GapTimeline.CONTACT) / 3.0) * 0.35;
		eye = eye.add(Math.sin(t * 41.0) * s, Math.cos(t * 37.0) * s, Math.sin(t * 29.0 + 1.3) * s);
		return lookAt(eye, at);
	}

	static Shot frameShot(ClientGap gap, int shot, Vec3d eye, Vec3d feet) {
		Vec3d side = gap.across.multiply(-1).add(gap.along.multiply(-0.6)).normalize();
		Vec3d c = gap.contact;
		return switch (shot) {
			case EXTREME -> framed(c.add(side.multiply(6.5)).add(0, 1.0, 0), c.add(0, 3.0, 0));
			case CONTACT -> framed(c.add(side.multiply(15.0)).add(0, 4.0, 0), c.add(0, 9.0, 0));
			case WIDE -> wide(gap, feet, GapTimeline.CONTACT);
			case SIDE -> framed(c.add(gap.across.multiply(32.0)).add(gap.along.multiply(-6.0)).add(0, 3.0, 0), c.add(0, 16.0, 0));
			case LOW -> framed(c.add(side.multiply(10.0)).add(0, 0.7, 0), c.add(0, 28.0, 0));
			default -> lookAt(eye, c.add(0, 8.0, 0));
		};
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
