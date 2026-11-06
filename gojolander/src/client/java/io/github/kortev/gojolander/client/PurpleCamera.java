package io.github.kortev.gojolander.client;

import io.github.kortev.gojolander.purple.PurpleTimeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * The caster's shots as he casts Hollow Purple, cut together:
 *
 * <ul>
 *   <li>BLUE: low and close in front of him and to his left, pushing in on the blue orb at his fingertips;</li>
 *   <li>RED: a hard cut to the other side, pushing in on the red;</li>
 *   <li>SWEEP: front and centre, close, as his hands sweep together;</li>
 *   <li>MERGE: pulled out wide beside him, the flash and the violet sphere swelling, the picture rumbling;</li>
 *   <li>RIDE: after the release the camera rides in the sphere's wake (the tunnel it is cutting), the world tearing past;</li>
 *   <li>WIDE: cut high over him, looking down the whole length of its path, craning up as the trench opens behind it.</li>
 * </ul>
 *
 * The close shots stop short of anything solid between him and where they would be, so they never look out of a hill.
 */
public final class PurpleCamera {
	private PurpleCamera() {
	}

	@Nullable
	public static DomainCamera.Shot current(float tickDelta) {
		ClientPurple p = ClientPurples.mine();
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (p == null || player == null || !ClientPurples.shooting()) {
			return null;
		}
		double t = p.time(tickDelta);
		PurplePose.Anchor at = PurplePose.anchor(client, p, tickDelta);
		Vec3d feet = p.eye.subtract(0.0, 1.62, 0.0);
		Vec3d f = p.flat(player.getYaw());
		Vec3d l = new Vec3d(f.z, 0.0, -f.x);
		Vec3d face = p.eye;
		Vec3d shake = rumble(t);
		int merge = PurpleTimeline.MERGE;
		int release = PurpleTimeline.RELEASE;
		if (t < PurpleTimeline.RED) {
			double k = ease(t / PurpleTimeline.RED);
			Vec3d orb = PurplePose.hand(p, true, t, at);
			Vec3d cam = feet.add(f.multiply(5.2 - 0.9 * k)).add(l.multiply(2.5 - 0.4 * k)).add(0.0, 1.35, 0.0);
			return clear(client, face, look(cam.add(shake), orb.lerp(face, 0.3).add(l.multiply(-0.1))));
		}
		if (t < PurpleTimeline.SWEEP) {
			double k = ease((t - PurpleTimeline.RED) / (PurpleTimeline.SWEEP - PurpleTimeline.RED));
			Vec3d orb = PurplePose.hand(p, false, t, at);
			Vec3d cam = feet.add(f.multiply(5.2 - 0.9 * k)).add(l.multiply(-2.5 + 0.4 * k)).add(0.0, 1.35, 0.0);
			return clear(client, face, look(cam.add(shake), orb.lerp(face, 0.3).add(l.multiply(0.1))));
		}
		if (t < merge) {
			double k = ease((t - PurpleTimeline.SWEEP) / (merge - PurpleTimeline.SWEEP));
			Vec3d cam = feet.add(f.multiply(5.0 - 1.0 * k)).add(0.0, 1.35 + 0.1 * k, 0.0);
			Vec3d m = PurplePose.meet(p, at);
			return clear(client, face, look(cam.add(shake), m.lerp(feet.add(0.0, 1.5, 0.0).add(f.multiply(1.0)), 0.0)));
		}
		if (t < release) {
			double k = ease((t - merge) / (release - merge));
			Vec3d m = PurplePose.meet(p, at);
			Vec3d s = p.sphereAt(t, m);
			// Far enough out to have the whole sphere, whatever its size, and him beside it.
			double out = 4.0 + 2.8 * p.radius;
			Vec3d cam = feet.add(f.multiply(2.6 + 1.6 * k)).add(l.multiply(out + 2.0 * k)).add(0.0, 1.4 + 0.3 * p.radius + 0.9 * k, 0.0);
			Vec3d target = s.lerp(feet.add(0.0, 1.5, 0.0), 0.0).add(0.0, 0.2, 0.0);
			return clear(client, face, look(cam.add(shake), target));
		}
		double ride = release + PurpleTimeline.RIDE;
		if (t < ride) {
			// Behind it, in the tunnel it is cutting, then lifting out of the trench and swinging off to the side: the sphere
			// stays big in the picture, the world tearing past.
			double k = ease((t - release) / PurpleTimeline.RIDE);
			Vec3d s = p.form.add(p.dir.multiply(p.front(t)));
			double r = p.radius;
			double back = 3.0 * r + 5.0 + (3.0 * r - 3.0) * k;
			Vec3d cam = s.subtract(p.dir.multiply(back)).add(l.multiply(1.6 * r * k)).add(0.0, 0.7 * r + 2.4 * r * k, 0.0);
			DomainCamera.Shot shot = look(cam.add(shake), s.add(p.dir.multiply(1.5)));
			// Just after the release the camera is still behind him, where a hill may be.
			return t < release + 8 ? clear(client, face, shot) : shot;
		}
		return wide(p, t, player.getYaw());
	}

	/**
	 * The wide shot after the ride: beside the trench, well out ahead of him and high, looking at the sphere as it goes
	 * on, craning up and out as it reaches the mountain; the whole length of the trench behind it in the picture.
	 */
	public static DomainCamera.Shot wide(ClientPurple p, double t, float fallbackYaw) {
		double start = PurpleTimeline.RELEASE + PurpleTimeline.RIDE;
		double k = ease((t - start) / (PurpleTimeline.RELEASE + PurpleTimeline.CINEMA - start));
		Vec3d f = p.flat(fallbackYaw);
		Vec3d l = new Vec3d(f.z, 0.0, -f.x);
		double r = p.radius;
		Vec3d s = p.form.add(p.dir.multiply(p.front(t)));
		Vec3d cam = p.form.add(f.multiply(60.0 + 20.0 * k)).add(l.multiply(4.0 * r + 12.0 + 8.0 * k)).add(0.0, 18.0 + 1.5 * r + 10.0 * k, 0.0);
		return look(cam, p.form.add(p.dir.multiply(Math.min(p.range * 0.72, 150.0))).add(0.0, -2.0, 0.0));
	}

	/** How far the camera's view is zoomed for the shot (1 for not at all): pushing in, and widening as it flies. */
	public static float fovScale(float tickDelta) {
		ClientPurple p = ClientPurples.mine();
		if (p == null || !ClientPurples.shooting()) {
			return 1.0F;
		}
		double t = p.time(tickDelta);
		int merge = PurpleTimeline.MERGE;
		int release = PurpleTimeline.RELEASE;
		if (t < PurpleTimeline.SWEEP) {
			double seg = t < PurpleTimeline.RED ? t / PurpleTimeline.RED : (t - PurpleTimeline.RED) / (PurpleTimeline.SWEEP - PurpleTimeline.RED);
			return MathHelper.lerp((float) ease(seg), 0.95F, 0.86F);
		}
		if (t < merge) {
			return MathHelper.lerp((float) ease((t - PurpleTimeline.SWEEP) / (merge - PurpleTimeline.SWEEP)), 0.95F, 0.85F);
		}
		if (t < release) {
			return MathHelper.lerp((float) ease((t - merge) / (release - merge)), 1.0F, 0.93F);
		}
		if (t < release + PurpleTimeline.RIDE) {
			return MathHelper.lerp((float) ease((t - release) / PurpleTimeline.RIDE), 0.95F, 1.15F);
		}
		return 1.0F;
	}

	/** The picture's rumble: at the merge and at the release, dying away, with a low shudder while the sphere is held. */
	private static Vec3d rumble(double t) {
		double merge = PurpleTimeline.MERGE;
		double release = PurpleTimeline.RELEASE;
		double amp = 0.0;
		if (t >= merge) {
			amp += 0.2 * Math.exp(-(t - merge) / 5.0) + (t < release ? 0.035 : 0.0);
		}
		if (t >= release) {
			amp += 0.28 * Math.exp(-(t - release) / 4.0);
		}
		if (t >= PurpleTimeline.SWEEP && t < merge) {
			amp += 0.02 + 0.012 * (t - PurpleTimeline.SWEEP);
		}
		if (amp < 1.0E-3) {
			return Vec3d.ZERO;
		}
		return new Vec3d(Math.sin(t * 9.1) * amp, Math.cos(t * 11.7) * amp, Math.sin(t * 7.3 + 1.0) * amp);
	}

	private static DomainCamera.Shot look(Vec3d at, Vec3d look) {
		Vec3d to = look.subtract(at);
		float yaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) (-(MathHelper.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * MathHelper.DEGREES_PER_RADIAN));
		return new DomainCamera.Shot(at.x, at.y, at.z, yaw, pitch);
	}

	/** The shot brought in short of anything solid between his face and where it would stand. */
	private static DomainCamera.Shot clear(MinecraftClient client, Vec3d face, DomainCamera.Shot shot) {
		if (client.world == null || client.player == null) {
			return shot;
		}
		Vec3d to = new Vec3d(shot.x(), shot.y(), shot.z());
		HitResult hit = client.world.raycast(new RaycastContext(face, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
				client.player));
		if (hit.getType() == HitResult.Type.MISS) {
			return shot;
		}
		Vec3d back = to.subtract(face);
		double reach = Math.max(0.6, face.distanceTo(hit.getPos()) - 0.4);
		Vec3d at = face.add(back.multiply(reach / Math.max(back.length(), 1.0E-4)));
		return new DomainCamera.Shot(at.x, at.y, at.z, shot.yaw(), shot.pitch());
	}

	private static double ease(double x) {
		x = MathHelper.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
