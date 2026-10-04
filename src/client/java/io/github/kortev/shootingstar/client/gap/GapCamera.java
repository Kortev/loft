package io.github.kortev.shootingstar.client.gap;

import io.github.kortev.shootingstar.client.camera.CameraDirector.Shot;
import io.github.kortev.shootingstar.gap.GapTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The shooter's shots through a Ginnungagap event: first person while the key turns; on the hill with them as
 * they aim; wide on the tear and the universe coming through it, with cutaways up from their eyes and a close-up
 * of the tree that trades places; low at the point of contact; a hard cut for every impact frame; the wide shot
 * again as everything is erased; then in on them, alone, and back to their eyes.
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
		if (player == null || gap == null) {
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
				return lookAt(eye, gap.contact.add(0, 30 + 0.3 * (t - 236), 0));
			}
			if (t >= 248 && t < 292) {
				return swapShot(gap, t);
			}
			return wide(gap, feet, t);
		}
		if (t < GapTimeline.FRAMES) {
			return contact(gap, t);
		}
		if (t < GapTimeline.ERASURE) {
			GapFrames.Frame frame = GapFrames.at(t - GapTimeline.FRAMES);
			return frameShot(gap, frame.shot(), eye, feet);
		}
		if (t < GapTimeline.NOTHING) {
			Shot w = wide(gap, feet, GapTimeline.CONTACT);
			double k = ease((t - GapTimeline.ERASURE) / (GapTimeline.NOTHING - GapTimeline.ERASURE));
			Vec3d back = gap.along.multiply(-30 * k).add(0, 26 * k, 0);
			return lookAt(new Vec3d(w.x(), w.y(), w.z()).add(back), gap.contact.lerp(feet, 0.35).add(0, 10, 0));
		}
		return nothing(gap, feet, t, tickDelta, player);
	}

	/** On the hill with the shooter: close at their side as they aim, then back over their shoulder, then up and wide. */
	private static Shot aim(ClientGap gap, Vec3d feet, double t) {
		Vec3d chest = feet.add(0, 1.3, 0);
		Shot close = lookAt(chest.add(gap.across.multiply(3.2)).add(gap.along.multiply(1.2)).add(0, 0.2, 0),
				chest.add(gap.along.multiply(1.4)));
		Shot shoulder = lookAt(feet.add(gap.along.multiply(-5.5)).add(gap.across.multiply(1.8)).add(0, 3.4, 0),
				gap.contact.add(0, 2, 0));
		if (t < 92) {
			double k = ease((t - GapTimeline.AIM) / 32.0);
			Shot pushed = lookAt(chest.add(gap.across.multiply(2.6)).add(gap.along.multiply(1.0)).add(0, 0.25, 0),
					chest.add(gap.along.multiply(1.4)));
			return blend(close, pushed, k);
		}
		if (t < 126) {
			return blend(close, shoulder, ease((t - 92) / 26.0));
		}
		return blend(shoulder, wide(gap, feet, t), ease((t - 126) / 24.0));
	}

	/** From the side, far enough to hold the shooter, the target and the sky over it where the tear opens. */
	static Shot wide(ClientGap gap, Vec3d feet, double t) {
		Vec3d mid = feet.add(gap.contact).multiply(0.5);
		double d = Math.hypot(gap.contact.x - feet.x, gap.contact.z - feet.z);
		double back = Math.max(d * 1.1, 72.0);
		double drift = ease((t - GapTimeline.TEAR) / (GapTimeline.CONTACT - GapTimeline.TEAR));
		Vec3d eye = mid.add(gap.across.multiply(back * (1.0 - 0.12 * drift))).add(gap.along.multiply(4.0 * drift))
				.add(0, 8 + 4 * drift, 0);
		Vec3d at = new Vec3d(mid.x, gap.surface + 34, mid.z).add(gap.along.multiply(d * 0.12));
		return lookAt(eye, at);
	}

	/** Low beside the tree that trades places, looking up at its twin hanging from the mirror universe. */
	private static Shot swapShot(ClientGap gap, double t) {
		Vec3d spot = Vec3d.ofBottomCenter(gap.swapSpot);
		Vec3d out = gap.across.multiply(-1).add(gap.along.multiply(-0.5)).normalize();
		Vec3d eye = spot.add(out.multiply(36 - 3 * ease((t - 248) / 44.0))).add(0, 2.5, 0);
		double twin = gap.mirrorY(gap.swapSpot.getY() + 4, t);
		Vec3d at = new Vec3d(spot.x, MathHelper.lerp(0.5, spot.y + 3, twin), spot.z);
		return lookAt(eye, at);
	}

	/** Down at the point of contact as the tip comes down and touches. */
	private static Shot contact(ClientGap gap, double t) {
		double k = ease((t - GapTimeline.CONTACT) / (GapTimeline.FRAMES - GapTimeline.CONTACT));
		Vec3d side = gap.across.multiply(-1).add(gap.along.multiply(-0.6)).normalize();
		Vec3d eye = gap.contact.add(side.multiply(7.5 - 1.5 * k)).add(0, 1.6, 0);
		return lookAt(eye, gap.contact.add(0, 1.8, 0));
	}

	static Shot frameShot(ClientGap gap, int shot, Vec3d eye, Vec3d feet) {
		Vec3d side = gap.across.multiply(-1).add(gap.along.multiply(-0.6)).normalize();
		return switch (shot) {
			case EXTREME -> lookAt(gap.contact.add(side.multiply(4.0)).add(0, 1.0, 0), gap.contact.add(0, 1.5, 0));
			case CONTACT -> lookAt(gap.contact.add(side.multiply(14.0)).add(0, 4.0, 0), gap.contact.add(0, 7.0, 0));
			case WIDE -> wide(gap, feet, GapTimeline.CONTACT);
			case SIDE -> lookAt(gap.contact.add(gap.across.multiply(30.0)).add(gap.along.multiply(-6.0)).add(0, 2.0, 0),
					gap.contact.add(0, 14.0, 0));
			case LOW -> lookAt(gap.contact.add(side.multiply(9.0)).add(0, 0.6, 0), gap.contact.add(0, 26.0, 0));
			default -> lookAt(eye, gap.contact.add(0, 6.0, 0));
		};
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
			Shot from = lookAt(new Vec3d(w.x(), w.y(), w.z()).add(gap.along.multiply(-30)).add(0, 26, 0), chest);
			return blend(from, side, ease((t - GapTimeline.NOTHING) / 40.0));
		}
		if (t < 720) {
			Vec3d face = feet.add(0, 1.55, 0);
			double k = ease((t - 688) / 32.0);
			return lookAt(face.add(facing.multiply(3.2 - 0.5 * k)).add(0, -0.15, 0), face.add(0, -0.25, 0));
		}
		return null;
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
