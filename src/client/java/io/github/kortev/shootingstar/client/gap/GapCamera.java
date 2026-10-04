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
 * The shooter's shots through a Ginnungagap event: first person while the key turns; on the hill with them as
 * they aim; a long way off to the side for the tear and the universe coming through it, with cutaways up from
 * their eyes and a tilt that follows a patch of ground up into the other universe; low at the point of contact;
 * a hard cut for every impact frame; the wide shot again as everything is erased; then in on them, alone, and
 * back to their eyes. No shot ever starts inside a hill.
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
		if (t < GapTimeline.AIM || t >= GapTimeline.END) {
			return null;
		}
		if (t < GapTimeline.TEAR) {
			return aim(gap, feet, t);
		}
		if (t < GapTimeline.CLOSING) {
			return wide(gap, feet, t);
		}
		if (t < GapTimeline.CONTACT) {
			if (t >= 236 && t < 248) {
				return lookAt(eye, gap.contact.add(0, GapTimeline.TEAR_HEIGHT * 0.7 + 0.2 * (t - 236), 0));
			}
			if (t >= 248 && t < 294) {
				return swapShot(gap, t);
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
			Shot w = wide(gap, feet, GapTimeline.CONTACT);
			double k = ease((t - GapTimeline.ERASURE) / (GapTimeline.NOTHING - GapTimeline.ERASURE));
			Vec3d from = new Vec3d(w.x(), w.y(), w.z());
			Vec3d away = from.subtract(gap.contact.x, from.y, gap.contact.z).normalize();
			return lookAt(from.add(away.multiply(40 * k)).add(0, 30 * k, 0), gap.contact.lerp(feet, 0.3).add(0, 12, 0));
		}
		return nothing(gap, feet, t, tickDelta, player);
	}

	/** On the hill with the shooter: close at their side as they aim, then back over their shoulder, then out wide. */
	private static Shot aim(ClientGap gap, Vec3d feet, double t) {
		Vec3d chest = feet.add(0, 1.3, 0);
		Vec3d front = chest.add(gap.along.multiply(1.4));
		Shot close = lookAt(clear(chest.add(gap.across.multiply(3.2)).add(gap.along.multiply(1.2)).add(0, 0.2, 0), front), front);
		Vec3d target = gap.contact.add(0, 2, 0);
		Shot shoulder = lookAt(clear(feet.add(gap.along.multiply(-5.5)).add(gap.across.multiply(1.8)).add(0, 3.4, 0), chest), target);
		if (t < 92) {
			Shot pushed = lookAt(clear(chest.add(gap.across.multiply(2.6)).add(gap.along.multiply(1.0)).add(0, 0.25, 0), front), front);
			return blend(close, pushed, ease((t - GapTimeline.AIM) / 32.0));
		}
		if (t < 126) {
			return blend(close, shoulder, ease((t - 92) / 26.0));
		}
		return blend(shoulder, wide(gap, feet, t), ease((t - 126) / 24.0));
	}

	/**
	 * A long way off to the side and level with the middle of it all, so one frame holds our ground, the shooter,
	 * the target, the tear and the whole upside-down universe hanging in it.
	 */
	static Shot wide(ClientGap gap, Vec3d feet, double t) {
		Vec3d mid = feet.lerp(gap.contact, 0.62);
		if (gap.wideEye == null) {
			double d = Math.hypot(gap.contact.x - feet.x, gap.contact.z - feet.z);
			double back = Math.max(d * 1.6, 116.0);
			Vec3d eye = new Vec3d(mid.x, gap.surface + 22, mid.z).add(gap.across.multiply(back));
			gap.wideEye = above(eye, gap.contact.add(0, 3, 0));
		}
		double drift = ease((t - GapTimeline.TEAR) / (GapTimeline.CONTACT - GapTimeline.TEAR));
		Vec3d eye = gap.wideEye.lerp(new Vec3d(mid.x, gap.wideEye.y, mid.z), 0.1 * drift).add(0, 5 * drift, 0);
		return lookAt(eye, new Vec3d(mid.x, gap.surface + 36, mid.z));
	}

	/** Beside the patch that trades places: on it as it goes, then tilting up after it into the other universe. */
	private static Shot swapShot(ClientGap gap, double t) {
		Vec3d spot = Vec3d.ofBottomCenter(gap.swapSpot.up());
		Vec3d out = gap.across.multiply(-1).add(gap.along.multiply(-0.5)).normalize();
		Vec3d low = spot.add(0, gap.tree ? 3.0 : 0.6, 0);
		Vec3d eye = clear(spot.add(out.multiply(gap.tree ? 18 : 13)).add(0, 2.4, 0), low);
		Vec3d twin = new Vec3d(spot.x, gap.mirrorY(gap.swapSpot.getY() + (gap.tree ? 3 : 0), t), spot.z);
		double tilt = ease((t - GapTimeline.TREE_SWAP - 3) / 16.0);
		return lookAt(eye, low.lerp(twin, tilt * 0.55));
	}

	/** Down at the point of contact as the tip comes down and touches. */
	private static Shot contact(ClientGap gap, double t) {
		double k = ease((t - GapTimeline.CONTACT) / (GapTimeline.FRAMES - GapTimeline.CONTACT));
		Vec3d side = gap.across.multiply(-1).add(gap.along.multiply(-0.6)).normalize();
		Vec3d at = gap.contact.add(0, 2.6, 0);
		return lookAt(clear(gap.contact.add(side.multiply(9.0 - 1.5 * k)).add(0, 1.4, 0), at), at);
	}

	static Shot frameShot(ClientGap gap, int shot, Vec3d eye, Vec3d feet) {
		Vec3d side = gap.across.multiply(-1).add(gap.along.multiply(-0.6)).normalize();
		Vec3d c = gap.contact;
		return switch (shot) {
			case EXTREME -> framed(c.add(side.multiply(4.5)).add(0, 1.0, 0), c.add(0, 2.0, 0));
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

	/** In on the shooter from the wide shot, round to the front of them, then back to their eyes. */
	@Nullable
	private static Shot nothing(ClientGap gap, Vec3d feet, double t, float tickDelta, ClientPlayerEntity player) {
		Vec3d chest = feet.add(0, 1.2, 0);
		Vec3d facing = Vec3d.fromPolar(0, player.getYaw(tickDelta)).normalize();
		Vec3d right = new Vec3d(-facing.z, 0, facing.x);
		Shot side = lookAt(chest.add(right.multiply(3.4)).add(0, 0.1, 0), chest);
		if (t < 688) {
			Shot w = wide(gap, feet, GapTimeline.CONTACT);
			Shot from = lookAt(new Vec3d(w.x(), w.y() + 30, w.z()), chest);
			return blend(from, side, ease((t - GapTimeline.NOTHING) / 40.0));
		}
		if (t < 720) {
			Vec3d face = feet.add(0, 1.55, 0);
			double k = ease((t - 688) / 32.0);
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

	private static Shot blend(Shot a, Shot b, double k) {
		return new Shot(MathHelper.lerp(k, a.x(), b.x()), MathHelper.lerp(k, a.y(), b.y()), MathHelper.lerp(k, a.z(), b.z()),
				MathHelper.lerpAngleDegrees((float) k, a.yaw(), b.yaw()), (float) MathHelper.lerp(k, a.pitch(), b.pitch()));
	}

	static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
