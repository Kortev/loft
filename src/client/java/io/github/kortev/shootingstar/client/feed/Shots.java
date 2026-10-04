package io.github.kortev.shootingstar.client.feed;

import io.github.kortev.shootingstar.client.gfx.Cam;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.Mesh;
import io.github.kortev.shootingstar.client.render.Gfx;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import java.util.Locale;
import java.util.Random;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The uplink feed. Every shot is a continuous camera move through a real 3D scene; scene changes hide
 * behind motivated transitions (cloud whiteout, warp streaks, a zoom dive, lap flashes, the release
 * flash and the re-entry whiteout) so the sequence reads as one piece.
 */
final class Shots {
	// --- Earth scene: radius 1 at the origin ---------------------------------------------------
	private static final Vector3f TARGET = Mesh.direction(39.5, -98.5);
	private static final Vector3f NORTH = new Vector3f(0, 1, 0).sub(new Vector3f(TARGET).mul(TARGET.y)).normalize();
	private static final Vector3f EAST = new Vector3f(NORTH).cross(TARGET).normalize().negate();
	private static final Vector3f EARTH_SUN = new Vector3f(TARGET).mul(0.62F).add(new Vector3f(EAST).mul(0.72F))
			.add(new Vector3f(NORTH).mul(0.3F)).normalize();
	/** Where the relay beam points: along the horizon, out towards Jupiter, over the sunlit side. */
	private static final Vector3f TO_JUPITER = new Vector3f(EAST).mul(0.85F).add(new Vector3f(NORTH).mul(0.4F))
			.add(new Vector3f(TARGET).mul(0.1F)).normalize();
	/** The relay rides a low orbit, so Earth fills the frame below it. */
	private static final Vector3f RELAY_POS = new Vector3f(TARGET).mul(0.86F).add(new Vector3f(EAST).mul(-0.42F))
			.add(new Vector3f(NORTH).mul(0.3F)).normalize().mul(1.08F);
	/** The galactic centre sits behind the climbing camera, so only the faint anticentre lies behind Earth. */
	private static final Matrix4f EARTH_SKY = skyFrame(slerp(TARGET, new Vector3f(TARGET).mul(0.8F)
			.add(new Vector3f(EAST).mul(-0.5F)).add(new Vector3f(NORTH).mul(-0.14F)).normalize(), 0.9F), NORTH);

	// --- Jupiter scene: radius 1 at the origin, ring in the equatorial plane --------------------
	static final float RING = 1.22F;
	private static final Vector3f JUPITER_SUN = new Vector3f(0.9F, 0.18F, 0.45F).normalize();
	private static final Matrix4f JUPITER_SKY = new Matrix4f().rotateXYZ(-0.5F, 1.1F, 0.35F);
	/** Io (1,821 km, so 0.0255 Jupiter radii), sunward of Jupiter so that it and its shadow cross the lit face. */
	private static final float IO_RADIUS = 0.0255F;
	private static final Vector3f IO_POS = new Vector3f(JUPITER_SUN).mul(2.6F)
			.add(new Vector3f(JUPITER_SUN).cross(0, 1, 0, new Vector3f()).normalize().mul(0.38F)).add(0, -0.1F, 0);

	// --- Breech scene: the beam runs along +Z, Jupiter far below ---------------------------------
	private static final float ROUND_SCALE = 0.42F;
	private static final Vector3f LOCAL_SUN = new Vector3f(0.55F, 0.62F, 0.55F).normalize();
	private static final Matrix4f LOCAL_SKY = new Matrix4f().rotateXYZ(0.2F, -0.7F, 1.25F);
	private static final Matrix4f LOCAL_JUPITER = new Matrix4f().translation(0, -1586, 0).rotateZ((float) (Math.PI / 2)).scale(1300);
	/** Out in the belt the sun is behind the camera, so the rocks and the round show their lit faces. */
	private static final Vector3f DEBRIS_SUN = new Vector3f(-0.35F, 0.55F, -0.75F).normalize();

	private static final int ORANGE = 0xFF7A1E;
	private static final int HOT = 0xFFB070;
	/** The colour the coils and the armature flash when they fire: an electric white-blue. */
	private static final Vector3f ARC = new Vector3f(0.75F, 0.88F, 1.0F);
	/** Out at the muzzle the sun is ahead, where the round is going. */
	private static final Vector3f RELEASE_SUN = new Vector3f(0.65F, 0.45F, 0.6F).normalize();
	/** Where the round's centre is (coil spacings past the muzzle) when the sabot is clear and falls away. */
	private static final double SABOT_FREE = 1.1;
	/** How far each coil is turned from the one before it (radians). */
	private static final double RIFLING = Math.toRadians(4.0);
	/**
	 * Where round the ring the camera skims the cloud tops on lap 6: seventy degrees from the subsolar point (the sun
	 * twenty degrees up behind the camera), looking back towards the terminator.
	 */
	private static final double LAP6_WHERE = 4.6;
	/** Angle of the first sabot petal round the spear (the others follow at 120 degrees). */
	private static final float PETAL_PHASE = 0.4F;

	private final Space space = new Space();
	private final Cam cam = new Cam();
	private final Random random = new Random();
	/** How long the frame being drawn lasts, in ticks: the barrel smears its coils over this much travel. */
	float frameTicks = 0.5F;
	private float width;
	private float height;
	private float guiW;
	private float guiH;
	private float time;

	Overlay render(double t, float fbWidth, float fbHeight, float guiWidth, float guiHeight) {
		space.ensure();
		width = fbWidth;
		height = fbHeight;
		guiW = guiWidth;
		guiH = guiHeight;
		time = (float) t;
		Overlay o = new Overlay();
		space.resetLights();
		if (t < StrikeTimeline.RELAY) {
			orbit(t - StrikeTimeline.ORBIT, o);
		} else if (t < StrikeTimeline.WAKE) {
			relay(t - StrikeTimeline.RELAY, o);
		} else if (t < StrikeTimeline.LOADING) {
			wake(t - StrikeTimeline.WAKE, o);
		} else if (t < StrikeTimeline.LAPS) {
			loading(t - StrikeTimeline.LOADING, o);
		} else if (t < StrikeTimeline.RELEASE) {
			laps(t, o);
		} else if (t < StrikeTimeline.DEBRIS) {
			release(t - StrikeTimeline.RELEASE, o);
		} else if (t < StrikeTimeline.TERMINAL) {
			debris(t - StrikeTimeline.DEBRIS, o);
		} else {
			terminal(t - StrikeTimeline.TERMINAL, o);
		}
		return o;
	}

	// =============================================================================================
	// 1. Up through the clouds and out to orbit.
	// =============================================================================================

	private record Pose(Vector3f eye, Vector3f at, Vector3f up, float fov) {
	}

	private static Pose orbitPose(double s) {
		float e = smoother(s / 26.0);
		double altitude = 0.09 * Math.pow(4.4 / 0.09, e);
		Vector3f away = new Vector3f(TARGET).mul(0.8F).add(new Vector3f(EAST).mul(-0.5F)).add(new Vector3f(NORTH).mul(-0.14F)).normalize();
		Vector3f dir = slerp(TARGET, away, e * 0.9F);
		Vector3f eye = new Vector3f(dir).mul((float) (1.0 + altitude));
		// Late in the move the planet slides left to leave room for the labels.
		Vector3f side = new Vector3f(NORTH).cross(dir).normalize();
		Vector3f at = new Vector3f(side).mul(0.55F * e);
		return new Pose(eye, at, new Vector3f(NORTH), 50.0F);
	}

	private void orbit(double s, Overlay o) {
		Pose pose = orbitPose(s);
		earthCamera(pose, 0.0005F, 40.0F);
		float e = smoother(s / 26.0);
		space.sky(cam, EARTH_SKY, 1.0F, 0, cam.forward(), 0, 0, 0, time);
		space.earth(cam, new Matrix4f(), EARTH_SUN, time * 0.00005F, 1.0F - smooth(e * 2.0F), 1.05F);
		// The cloud deck the in-world shot rose into, thinning as we climb.
		o.flash = (float) Math.pow(Math.max(0.0, 1.0 - s / 8.0), 1.6);
		o.flashColor = 0xF2F5FA;

		targetLabel(o, s);
		if (s > 12) {
			Vector3f limb = new Vector3f(cam.right()).mul(0.7F).add(new Vector3f(cam.up()).mul(0.72F)).normalize();
			label(o, limb, 10, -6, "EARTH", Feed.CYAN, "6,371 KM", Feed.GREY, smooth((s - 12) / 4.0));
		}
		o.header = "[ KINETIC LOCK · SOL-3 ]";
		o.headerReveal = smooth(s / 6.0);
	}

	private void targetLabel(Overlay o, double s) {
		Vector3f facing = new Vector3f(cam.pos).sub(TARGET);
		if (facing.dot(TARGET) <= 0.05F) {
			return;
		}
		Overlay.Label label = label(o, TARGET, 10, -5, "TARGET", Feed.RED, "KINETIC LOCK", Feed.GREY, smooth((s - 3) / 3.0));
		if (label != null) {
			label.marker = true;
		}
	}

	// =============================================================================================
	// 2. The relay satellite fires, the camera rides the signal out to Jupiter.
	// =============================================================================================

	private void relay(double s, Overlay o) {
		o.header = "[ RELAY · JUPITER 5.2 AU ]";
		o.headerReveal = smooth((s - 1) / 6.0);
		if (s < 13) {
			relayNearEarth(s, o);
		} else {
			relayApproach(s, o);
		}
	}

	private void relayNearEarth(double s, Overlay o) {
		Pose start = orbitPose(26);
		Vector3f relayUp = new Vector3f(RELAY_POS).normalize();
		Vector3f side = new Vector3f(TO_JUPITER).cross(relayUp).normalize();
		Vector3f nearEye = new Vector3f(RELAY_POS).add(new Vector3f(TO_JUPITER).mul(-0.035F)).add(new Vector3f(relayUp).mul(0.03F))
				.add(new Vector3f(side).mul(0.025F));
		Vector3f nearAt = new Vector3f(RELAY_POS).add(new Vector3f(TO_JUPITER).mul(0.03F)).add(new Vector3f(relayUp).mul(-0.012F));

		// Dolly from the wide Earth shot to the satellite, distance falling exponentially.
		float k = smoother(s / 8.0);
		Vector3f fromRelay0 = new Vector3f(start.eye()).sub(RELAY_POS);
		Vector3f fromRelay1 = new Vector3f(nearEye).sub(RELAY_POS);
		float d0 = fromRelay0.length();
		float d1 = fromRelay1.length();
		Vector3f dir = slerp(fromRelay0.normalize(), fromRelay1.normalize(), k);
		Vector3f eye = new Vector3f(dir).mul((float) (d0 * Math.pow(d1 / d0, k))).add(RELAY_POS);
		// Turn onto the satellite early, so it is the subject of the shot while the camera closes in.
		Vector3f at = new Vector3f(start.at()).lerp(nearAt, smooth(s / 3.5));
		float fov = 50.0F;
		// Whip round to look down the beam, then accelerate along it.
		float whip = smoother((s - 8) / 4.0);
		if (whip > 0) {
			Vector3f behind = new Vector3f(RELAY_POS).add(new Vector3f(TO_JUPITER).mul(-0.03F)).add(new Vector3f(relayUp).mul(0.006F));
			eye.lerp(behind, whip);
			at.lerp(new Vector3f(RELAY_POS).add(new Vector3f(TO_JUPITER).mul(3.0F)), whip);
			fov = 50.0F + 22.0F * whip;
		}
		float warp = smooth((s - 10.5) / 2.5);
		eye.add(new Vector3f(TO_JUPITER).mul((float) (0.35 * Math.pow(Math.max(0, s - 11), 2.0))));
		earthCamera(new Pose(eye, at, new Vector3f(relayUp), fov), 0.0005F, 60.0F);

		space.sky(cam, EARTH_SKY, 1.0F, 0, cam.forward(), warp * 0.9F, 0, 0, time);
		space.earth(cam, new Matrix4f(), EARTH_SUN, time * 0.00005F, 0.0F, 1.05F);
		Matrix4f satellite = new Matrix4f().translation(RELAY_POS).rotateTowards(TO_JUPITER, relayUp).scale(0.011F);
		// Earthshine: the bright planet below lights the satellite's underside blue-white.
		space.fillDir.set(relayUp).negate();
		space.fillColor.set(0.28F, 0.33F, 0.4F);
		space.mesh(space.relay, cam, satellite, EARTH_SUN, 1.15F, 0xFFD27A, 1.0F, 0);
		space.fillColor.zero();

		Vector3f emitter = new Vector3f(RELAY_POS).add(new Vector3f(TO_JUPITER).mul(0.0165F));
		float charge = smooth((s - 5) / 3.0);
		Fx fx = space.glow(cam, Fx.BLOB, 1.0F);
		fx.sprite(emitter, 0.004F + 0.01F * charge, 0, Fx.argb(1.0F, 0.85F, 0.6F, 0.4F + 0.6F * charge));
		fx.end(true);
		if (s >= 8) {
			double reach = 0.02 + 6.0 * Math.pow(s - 8, 2.0);
			Vector3f head = new Vector3f(emitter).add(new Vector3f(TO_JUPITER).mul((float) reach));
			Fx beam = space.glow(cam, Fx.BEAM, 0);
			beam.beam(emitter, head, cam.pos, 0.0035F, Fx.argb(1.0F, 0.75F, 0.5F, 1.0F), Fx.argb(1.0F, 0.9F, 0.75F, 1.0F));
			beam.end(true, 3.0F);
			Fx pulse = space.glow(cam, Fx.SPIKES, 0);
			pulse.sprite(head, 0.03F + (float) reach * 0.01F, 0, Fx.argb(1.0F, 0.9F, 0.8F, 1.0F));
			pulse.end(true, 4.0F);
		}
		if (s < 7) {
			label(o, RELAY_POS, 12, 6, "RELAY", Feed.RED, "SS-03 UPLINK", Feed.GREY, smooth((s - 2) / 3.0) * (1 - smooth((s - 5) / 2.0)));
		}
		o.zoomBlur = warp * 0.35F;
	}

	private void relayApproach(double s, Overlay o) {
		Pose end = wakePose(0);
		float k = smoother((s - 13) / 11.0);
		Vector3f dir = new Vector3f(end.eye()).normalize();
		float distance = (float) (end.eye().length() * Math.pow(260.0 / end.eye().length(), 1.0 - k));
		Vector3f eye = new Vector3f(dir).mul(distance);
		jupiterCamera(new Pose(eye, end.at(), end.up(), end.fov() + 30.0F * (1 - k)), 0.002F, 2000.0F);
		float streak = 0.9F * (1.0F - smooth((s - 13) / 7.0));
		space.sky(cam, JUPITER_SKY, 1.0F, 0, cam.forward(), streak, 0, 0, time);
		jupiterScene(0.0F, 0.04F, 0.0F);
		o.zoomBlur = streak * 0.35F;
		if (s > 18) {
			label(o, new Vector3f(0.55F, 0.7F, 0.3F).normalize(), 10, -8, "JUPITER", Feed.RED, "5.2 AU · SS-03", Feed.GREY,
					smooth((s - 18) / 3.0));
		}
	}

	// =============================================================================================
	// 3. The accelerator ring wakes around Jupiter.
	// =============================================================================================

	private static Pose wakePose(double s) {
		float e = smoother(s / 44.0);
		float distance = (float) (9.0 * Math.pow(4.7 / 9.0, e));
		double az = Math.toRadians(lerp(36, 16, e));
		double el = Math.toRadians(lerp(9.5, 5.0, e));
		Vector3f eye = new Vector3f((float) (Math.cos(el) * Math.cos(az)), (float) Math.sin(el), (float) (Math.cos(el) * Math.sin(az)))
				.mul(distance);
		return new Pose(eye, new Vector3f(0, -0.12F, 0), new Vector3f(0, 1, 0), 38.0F);
	}

	private void wake(double s, Overlay o) {
		Pose pose = wakePose(s);
		Vector3f eye = new Vector3f(pose.eye());
		Vector3f at = new Vector3f(pose.at());
		float fov = pose.fov();
		// Dive at the breech, ending outside the ring and above it, looking along the beam with the planet's limb
		// below, and flash into the close-up.
		float dive = (float) Math.pow(smooth((s - 37) / 7.0), 2.0);
		Vector3f breech = new Vector3f(RING, 0, 0);
		if (dive > 0) {
			eye.lerp(new Vector3f(breech).add(0.06F, 0.035F, 0.1F), dive);
			at.lerp(new Vector3f(breech).add(0, 0, -0.12F), dive);
			fov -= 14.0F * dive;
		}
		jupiterCamera(new Pose(eye, at, pose.up(), fov), 0.002F, 200.0F);
		space.sky(cam, JUPITER_SKY, 1.0F, 0, cam.forward(), 0, 0, 0, time);
		float progress = smoother((s - 4) / 30.0);
		jupiterScene(progress, 0.05F, 0.6F + 0.4F * (float) Math.sin(s * 0.3));
		if (progress > 0.001F && progress < 0.999F) {
			// The front of the wave of coils coming online, racing round the planet.
			Vector3f front = ringPoint(progress * Math.PI * 2);
			Fx head = space.glow(cam, Fx.SPIKES, 0);
			head.sprite(front, 0.07F, (float) (s * 0.05), Fx.argb(1.0F, 0.9F, 0.75F, 1.0F));
			head.end(true, 3.0F);
			Fx halo = space.glow(cam, Fx.BLOB, 1.0F);
			halo.sprite(front, 0.16F, 0, Fx.argb(1.0F, 0.55F, 0.2F, 0.6F));
			halo.end(true, 1.5F);
		}
		o.zoomBlur = dive * 0.3F;
		o.flash = (float) Math.pow(dive, 3.0) * 0.75F;
		o.flashColor = 0xF4F7FF;

		o.header = progress >= 0.999F ? "[ ACCELERATOR ONLINE ]" : "[ ACCELERATOR WAKING ]";
		o.headerColor = progress >= 0.999F ? Feed.ORANGE : Feed.RED;
		o.headerReveal = smooth(s / 5.0);
		o.title = "THE SHOOTING STAR";
		o.subtitle = "SS-03 GUNGNIR · MASS DRIVER · JUPITER · RING 1.22 RJ · 536,000 KM ROUND";
		o.titleAlpha = smooth((s - 2) / 5.0) * (1.0F - smooth((s - 26) / 6.0));
		o.footer = "COILS " + Feed.commas(Math.round(progress * 670_000)) + " / 670,000";
		if (s > 24 && dive < 0.2F) {
			label(o, breech, 10, 6, "BREECH", Feed.RED, "SS-03", Feed.GREY, smooth((s - 24) / 4.0) * (1 - dive * 5));
		}
	}

	/** Jupiter with its ring ({@code progress} lit), aurora, and the sun. */
	private void jupiterScene(float progress, float ringBase, float aurora) {
		Matrix4f jupiter = new Matrix4f().rotateZ((float) Math.toRadians(3.1));
		// The ring and Io both throw shadows across the cloud tops.
		space.ringShadow = RING;
		space.moonPos.set(IO_POS);
		space.moonRadius = IO_RADIUS;
		space.jupiter(cam, jupiter, JUPITER_SUN, time, 1.0F);
		space.fillDir.set(IO_POS).negate().normalize();
		space.fillColor.set(0.22F, 0.15F, 0.09F);
		space.mesh(space.io, cam, new Matrix4f().translation(IO_POS).rotateY(time * 0.002F).scale(IO_RADIUS), JUPITER_SUN, 1.5F, 0, 0, 0);
		space.fillColor.zero();
		space.ring(cam, new Matrix4f(jupiter).scale(RING), progress, ringBase, 1.0F, 0.42F, 0.1F);
		Fx polar = space.glow(cam, Fx.RING, 0.35F);
		Vector3f pole = new Vector3f(0, 1.0F, 0);
		polar.flat(pole, new Vector3f(0.26F, 0, 0), new Vector3f(0, 0, 0.26F), Fx.argb(0.35F, 0.75F, 1.0F, 0.5F * aurora));
		polar.end(true);
		Fx sun = space.glow(cam, Fx.SPIKES, 0);
		sun.sprite(new Vector3f(JUPITER_SUN).mul(600), 26.0F, 0, Fx.argb(1.0F, 0.95F, 0.85F, 0.8F));
		sun.end(true);
	}

	// =============================================================================================
	// 4. The round seats in the breech.
	// =============================================================================================

	private void loading(double s, Overlay o) {
		Vector3f eye = new Vector3f(2.6F + (float) s * 0.01F, 0.95F, -2.9F);
		Vector3f at = new Vector3f(0, 0, 0.7F);
		float chase = smoother((s - 12) / 8.0);
		eye.lerp(new Vector3f(0, 0.42F, -2.7F), chase);
		at.lerp(new Vector3f(0, 0.05F, 3.0F), chase);
		localCamera(eye, at, 0, 58.0F - 4.0F * chase);
		float round = -7.0F + 7.0F * smoother(s / 10.0);
		boolean locked = s >= 11;
		float lockFlash = locked ? (float) Math.exp(-(s - 11) / 3.0) : 0;
		breechScene(0.0, round, 1.0F + lockFlash * 2.5F, 0.0F, lockFlash * 4.0F);
		if (lockFlash > 0.05F) {
			// The breech coil takes hold of the sabot: a pulse through it and arcs to the copper bands.
			arcs(round, lockFlash, 2);
		}
		o.zoomBlur = 0.55F * (1.0F - smooth(s / 4.0));
		o.header = locked ? "[ BREECH LOCKED ]" : "[ LOADING ]";
		o.headerColor = locked ? Feed.ORANGE : Feed.RED;
		o.headerReveal = locked ? smooth((s - 11) / 3.0) : smooth(s / 4.0);
		// Out of the flash that ended the dive, then the breech's own orange pulse when it locks.
		float pop = (float) Math.pow(Math.max(0.0, 1.0 - s / 1.2), 2.0) * 0.75F;
		o.flash = Math.max(pop, lockFlash * 0.25F);
		o.flashColor = pop > lockFlash * 0.25F ? 0xF4F7FF : 0xFFB070;
		if (s < 12) {
			label(o, new Vector3f(0, 1.05F, 0), 8, -4, "BREECH", Feed.RED, "COIL 000,001", Feed.GREY, smooth((s - 2) / 3.0));
		}
	}

	/**
	 * Coils along +Z with the round at {@code roundZ}; {@code travel} slides the tunnel (in coil spacings),
	 * {@code glow} is the coils' idle glow and {@code speed} (0..1) how hard they fire as the round goes through.
	 */
	private void breechScene(double travel, float roundZ, float glow, float speed) {
		breechScene(travel, roundZ, glow, speed, speed > 0 ? 0.8F + 2.5F * speed : 0.0F);
	}

	/** As above with the sabot's armature glow given ({@code armature}). */
	private void breechScene(double travel, float roundZ, float glow, float speed, float armature) {
		space.sky(cam, LOCAL_SKY, 0.9F, 0, cam.forward(), 0, 0, 0, time);
		// Jupiter as a backdrop, drawn without depth so it never cuts into the coils.
		Space.clearDepth();
		space.jupiter(cam, LOCAL_JUPITER, LOCAL_SUN, time * 3, 1.0F);
		Space.clearDepth();
		// Jupiter fills the scene with orange light from below.
		space.fillDir.set(0, -1, 0);
		space.fillColor.set(0.3F, 0.17F, 0.08F);

		double offset = travel - Math.floor(travel);
		float light = speed > 0 ? 0.55F : 1.0F;
		long first = (long) Math.floor(travel);
		for (int k = -12; k <= 70; k++) {
			float z = (float) (k - offset);
			coil(first + k, z, z - roundZ, glow, speed, light);
		}
		// The coil firing just ahead of the round lights it up.
		if (speed > 0) {
			space.pointPos.set(0, 0, roundZ + 0.6F);
			space.pointColor.set(ARC).mul(5.0F + 9.0F * speed);
		}
		Matrix4f model = new Matrix4f().translation(0, 0, roundZ).scale(ROUND_SCALE);
		drawRound(model, LOCAL_SUN, 1.1F, 0.6F + speed, speed * 0.4F, armature, true);
		space.pointColor.zero();
		if (speed > 0.05F) {
			// Light trails along the emitters.
			Fx trails = space.glow(cam, Fx.STREAK, 0);
			for (int i = 0; i < 40; i++) {
				double angle = (i * 2.399963) % (Math.PI * 2);
				float r = 0.7F;
				float z = (float) ((i * 7.31 + travel * 0.5) % 60.0) - 6.0F;
				float length = 0.5F + speed * 6.0F;
				Vector3f c = new Vector3f((float) Math.cos(angle) * r, (float) Math.sin(angle) * r, z);
				trails.stretched(c, new Vector3f(0, 0, 1), length, 0.03F, Fx.argb(0.8F, 0.85F, 1.0F, Math.min(1.0F, speed * 1.2F)));
			}
			trails.end(true);
		}
	}

	/**
	 * The round riding the barrel: the sky and Jupiter beyond the coils (the stars crowding forward as it nears c),
	 * the round lit by the coil firing round it, and the barrel itself, its coils smeared over this frame's travel.
	 */
	private void boreScene(double t, float speed, float cameraZ) {
		space.sky(cam, LOCAL_SKY, 0.9F, speed * 0.9F, new Vector3f(0, 0, 1), 0, 0, 0, time);
		Space.clearDepth();
		space.jupiter(cam, LOCAL_JUPITER, LOCAL_SUN, time * 3, 1.0F);
		Space.clearDepth();
		space.fillDir.set(0, -1, 0);
		space.fillColor.set(0.3F, 0.17F, 0.08F);
		space.pointPos.set(0, 0, 0.6F);
		space.pointColor.set(ARC).mul(5.0F + 9.0F * speed);
		drawRound(new Matrix4f().scale(ROUND_SCALE), LOCAL_SUN, 1.1F, 0.6F + speed, speed * 0.4F, 0.8F + 2.5F * speed, true);
		space.pointColor.zero();
		space.bore(cam, boreTravel(t), (float) (boreRate(t) * frameTicks), 0.0F, cameraZ, speed, 0.05F);
	}

	/**
	 * Coil number {@code index}, at {@code z}, {@code rel} spacings ahead of the round (negative: behind). It flashes
	 * white-blue as the round arrives and cools through orange behind it, its radiators glowing as it cools.
	 */
	private void coil(long index, float z, float rel, float glow, float speed, float light) {
		float front = 0.0F;
		float trail = 0.0F;
		if (speed > 0) {
			if (rel >= 0) {
				front = (float) Math.exp(-rel * rel * 3.0);
			} else {
				front = (float) Math.exp(-rel * rel * 6.0);
				trail = (float) Math.exp(rel / (1.5F + 10.0F * speed));
			}
		}
		float flash = front * (3.0F + 5.0F * speed);
		float warm = glow + trail * (0.8F + 1.3F * speed);
		float strength = flash + warm;
		float r = (ARC.x * flash + 1.0F * warm) / strength;
		float g = (ARC.y * flash + 0.42F * warm) / strength;
		float b = (ARC.z * flash + 0.1F * warm) / strength;
		// Rifling: each coil is turned a little further than the one before, so the joins between its housings
		// spiral down the barrel instead of lining up into spokes.
		Matrix4f coil = new Matrix4f().translation(0, 0, z).rotateZ((float) ((index % 90) * RIFLING));
		space.mesh(space.coil, cam, coil, LOCAL_SUN, light, r, g, b, strength, trail * 0.8F);
	}

	/**
	 * The spear at {@code model} (one unit is a tenth of its length) and, when {@code sabot}, the three petals of
	 * its sabot round it: {@code glow} lights the runes and the blade's edges, {@code heat} the blade, and
	 * {@code armature} the sabot's copper bands (white-blue while the coils push on them).
	 */
	private void drawRound(Matrix4f model, Vector3f sun, float light, float glow, float heat, float armature, boolean sabot) {
		space.mesh(space.round, cam, model, sun, light, ORANGE, glow, heat);
		if (sabot) {
			for (int k = 0; k < 3; k++) {
				Matrix4f petal = new Matrix4f(model).rotateZ(k * 2.0943951F + PETAL_PHASE);
				space.mesh(space.sabot, cam, petal, sun, light, ARC.x, ARC.y, ARC.z, armature, 0);
			}
		}
	}

	// =============================================================================================
	// 5. Seven laps to 0.96c.
	// =============================================================================================

	private void laps(double t, Overlay o) {
		double p = StrikeTimeline.lapProgress(t);
		int lap = StrikeTimeline.lapNumber(p);
		double velocity = StrikeTimeline.lapVelocity(p);
		double covered = StrikeTimeline.lapDistance(p);
		double sinceLap = t - lapStart(lap);
		float v = (float) velocity;
		if (lap % 2 == 1) {
			tunnel(t, lap, v, sinceLap, o);
		} else {
			exterior(t, lap, covered, v, sinceLap, o);
		}
		// Each lap opens with a burst of light; after the first, a quick white cut hides the change of shot.
		o.exposure = 1.0F + 2.2F * (float) Math.exp(-sinceLap / 1.3);
		if (lap > 1 && sinceLap < 1.5) {
			o.flash = Math.max(o.flash, (float) (0.55 * (1.0 - sinceLap / 1.5)));
		}
		o.header = "[ LAP " + lap + " / " + StrikeTimeline.LAP_COUNT + " ]";
		o.headerColor = lap == StrikeTimeline.LAP_COUNT ? Feed.WHITE : Feed.RED;
		o.footer = String.format(Locale.ROOT, "VELOCITY %.4f c", velocity);
		double lapCovered = covered;
		o.bars = (b, m, w, h) -> lapBars(b, m, w, h, lapCovered, lap, t);
	}

	/** Time at which {@code lap} began (constant acceleration through the laps). */
	private static double lapStart(int lap) {
		double v0 = StrikeTimeline.ENTRY_VELOCITY;
		double a = StrikeTimeline.EXIT_VELOCITY - v0;
		double target = (lap - 1) / (double) StrikeTimeline.LAP_COUNT * (v0 + a / 2);
		double p = (-v0 + Math.sqrt(v0 * v0 + 2 * a * target)) / a;
		return StrikeTimeline.LAPS + p * (StrikeTimeline.RELEASE - StrikeTimeline.LAPS);
	}

	/**
	 * Coils passing per tick inside the barrel. The real figure (tens of thousands a tick near c) would only read as
	 * an even blur from the first lap, so the picture keeps its own: the first coils go by one at a time and the
	 * rate builds until the rings have blurred into a tube of light.
	 */
	private static double boreRate(double t) {
		double p = MathHelper.clamp((t - StrikeTimeline.LAPS) / (StrikeTimeline.RELEASE - StrikeTimeline.LAPS), 0.0, 1.0);
		return 0.25 + 9.75 * Math.pow(p, 1.6);
	}

	/** Coil spacings the barrel has slid past the round since the launch: {@link #boreRate} integrated. */
	private static double boreTravel(double t) {
		double span = StrikeTimeline.RELEASE - StrikeTimeline.LAPS;
		double u = MathHelper.clamp(t - StrikeTimeline.LAPS, 0.0, span);
		return 0.25 * u + 9.75 * span / 2.6 * Math.pow(u / span, 2.6) + boreRate(t) * Math.max(0.0, t - StrikeTimeline.RELEASE);
	}

	private void tunnel(double t, int lap, float velocity, double since, Overlay o) {
		Vector3f eye;
		Vector3f at;
		float roll = 0;
		float fov = 62.0F + velocity * 8.0F;
		float sway = (float) Math.sin(t * 0.21) * 0.05F;
		switch (lap) {
			case 1 -> {
				// Riding behind the fins as the first coils take hold.
				eye = new Vector3f(sway, 0.42F, -2.7F);
				at = new Vector3f(0, 0.05F, 3.0F);
			}
			case 3 -> {
				// Low beside the shaft, banked.
				eye = new Vector3f(0.36F, -0.22F + sway, -2.0F);
				at = new Vector3f(0, 0, 3.5F);
				roll = 0.16F;
			}
			case 5 -> {
				// Out in front, looking back at the point coming on through the firing coils, the barrel ablaze behind it.
				eye = new Vector3f(0.2F + sway * 0.5F, 0.14F, 5.4F);
				at = new Vector3f(0, -0.02F, 0.2F);
				roll = (float) (-0.1 - since * 0.08);
				fov = 58.0F;
			}
			default -> {
				// Tight on the fins as it nears c, everything round it gone to light.
				eye = new Vector3f(0.16F, 0.3F, -1.35F);
				at = new Vector3f(0, -0.02F, 6.0F);
				roll = (float) (since * 0.03);
			}
		}
		float shake = velocity * 0.012F;
		eye.add(noise(t * 3.1) * shake, noise(t * 2.7 + 9) * shake, 0);
		localCamera(eye, at, roll, fov);
		boreScene(t, velocity, eye.z);
		o.zoomBlur = 0.02F + velocity * 0.06F;
		// The barrel blurs itself, exactly, so the shot needs no shutter.
		o.shutter = 0.0F;
		o.streak = 0.25F;
		o.aberration = velocity * 0.01F;
		if (lap == StrikeTimeline.LAP_COUNT) {
			// The last lap burns out into the muzzle shot.
			double end = StrikeTimeline.RELEASE - lapStart(lap);
			o.flash = Math.max(o.flash, (float) Math.pow(smooth((since - end * 0.8) / (end * 0.2)), 2.0) * 0.8F);
		}
	}

	private void exterior(double t, int lap, double covered, float velocity, double since, Overlay o) {
		double angle = (covered * StrikeTimeline.LAP_COUNT % 1.0) * Math.PI * 2;
		Vector3f eye;
		Vector3f at;
		Vector3f up = new Vector3f(0, 1, 0);
		float fov;
		boolean low = false;
		switch (lap) {
			case 2 -> {
				eye = new Vector3f(3.1F, 1.15F, 2.5F);
				at = new Vector3f(0, -0.12F, 0);
				fov = 40.0F;
			}
			case 6 -> {
				// Skimming the cloud tops just south of the equator in the late afternoon, the sun low behind the
				// camera, looking back along the ring as it climbs from the horizon across the sky: the round comes
				// up over the edge of the world and screams overhead.
				double where = LAP6_WHERE;
				Vector3f ground = ringPoint(where).normalize();
				eye = new Vector3f(ground).mul(1.018F).add(0, -0.03F, 0);
				Vector3f back = new Vector3f((float) Math.sin(where), 0, (float) Math.cos(where));
				at = new Vector3f(eye).add(back).add(new Vector3f(ground).mul(0.34F));
				up = new Vector3f(ground);
				fov = 74.0F;
				low = true;
			}
			case 4 -> {
				// Beside the ring where the round passes mid-lap, looking back along it.
				double mid = Math.PI;
				Vector3f point = ringPoint(mid + 0.035);
				eye = new Vector3f(point).add(new Vector3f(point).normalize().mul(0.04F)).add(0, 0.018F, 0);
				at = ringPoint(mid - 0.35);
				fov = 46.0F;
			}
			default -> {
				eye = new Vector3f(4.6F, 2.9F, -1.9F);
				at = new Vector3f(0, -0.2F, 0);
				fov = 34.0F;
			}
		}
		if (!low) {
			eye.rotateY((float) (since * 0.0025));
		}
		jupiterCamera(new Pose(eye, at, up, fov), low ? 0.0002F : 0.0008F, 200.0F);
		space.sky(cam, JUPITER_SKY, 1.0F, 0, cam.forward(), 0, 0, 0, time);
		// Close over the cloud tops the map's resolution runs out: let the shader stir in fine detail.
		space.jupiterDetail = low ? 1.0F : 0.0F;
		jupiterScene(1.0F, 0.05F, 1.0F);
		space.jupiterDetail = 0.0F;
		// The round: a white-hot point and a trail that lengthens with speed.
		Vector3f round = ringPoint(angle);
		float trailAngle = (float) Math.min(Math.PI * 1.2, 0.05 + velocity * 2.4);
		Fx trail = space.glow(cam, Fx.BLOB, 0);
		for (int i = 0; i < 60; i++) {
			float f = i / 59.0F;
			Vector3f p = ringPoint(angle - trailAngle * f);
			float size = 0.012F + 0.02F * (1 - f);
			trail.sprite(p, size, 0, Fx.argb(1.0F, 0.55F + 0.4F * (1 - f), 0.2F + 0.6F * (1 - f), (1 - f) * 0.7F));
		}
		trail.end(true);
		Fx head = space.glow(cam, Fx.SPIKES, 0);
		head.sprite(round, 0.09F + velocity * 0.12F, 0, Fx.argb(1.0F, 0.95F, 0.88F, 1.0F));
		head.end(true);
		if (lap == 4 || lap == 6) {
			// A flash as it tears past the camera.
			double pass = Math.abs(angle - (lap == 6 ? LAP6_WHERE : Math.PI));
			o.flash = Math.max(o.flash, (float) ((lap == 6 ? 0.35 : 0.6) * Math.exp(-pass * pass * 400)));
		}
		o.shutter = low ? 1.0F : 0.0F;
		o.zoomBlur = 0;
	}

	private static Vector3f ringPoint(double angle) {
		float tilt = (float) Math.toRadians(3.1);
		Vector3f p = new Vector3f((float) Math.cos(angle) * RING, 0, (float) -Math.sin(angle) * RING);
		return p.rotateZ(tilt);
	}

	private static void lapBars(net.minecraft.client.render.BufferBuilder b, Matrix4f m, float w, float h, double covered, int lap,
			double age) {
		float width = Math.min(170, w * 0.42F);
		float x0 = (w - width) / 2;
		float y = h - 24;
		Gfx.rect(b, m, x0, y, x0 + width, y + 1, 0x55FFFFFF);
		Gfx.rect(b, m, x0, y, x0 + width * (float) covered, y + 1, Feed.RED);
		float dash = width / StrikeTimeline.LAP_COUNT;
		for (int i = 0; i < StrikeTimeline.LAP_COUNT; i++) {
			int color = i < lap - 1 ? Feed.RED : i == lap - 1 ? ((int) (age * 0.5) % 2 == 0 ? Feed.RED : 0xFF7A2A20) : 0x55FFFFFF;
			float dx = x0 + i * dash + 1.5F;
			Gfx.rect(b, m, dx, y + 5, dx + dash - 3, y + 7, color);
		}
	}

	// =============================================================================================
	// 6. Release: out of the muzzle in slow motion, shedding the sabot.
	// =============================================================================================

	/**
	 * The round's centre in coil spacings past the muzzle (the last coil, at z = 0). It races up the barrel, time
	 * all but stops as it leaves the muzzle, and then it is gone: a sinh is fast, slow and fast again. Scene time
	 * runs with this, so everything else in the shot is timed against it.
	 */
	private static double releaseZ(double s) {
		return 0.5 * Math.sinh((s - 11.0) / 2.2);
	}

	private void release(double s, Overlay o) {
		double z = releaseZ(s);
		double speed = (releaseZ(s + 0.05) - releaseZ(s - 0.05)) / 0.1;
		// Down the barrel from the muzzle as the flash races up it; beside the round as it comes out; then the
		// camera whips round after it.
		float toSide = smooth((s - 6.0) / 4.0);
		float after = smoother((s - 19.0) / 6.0);
		Vector3f eye = new Vector3f(2.3F, 1.05F, 4.6F)
				.lerp(new Vector3f(1.35F, 0.5F, 2.6F).lerp(new Vector3f(1.05F, 0.38F, 4.4F), smooth((s - 8.0) / 12.0)), toSide)
				.lerp(new Vector3f(0.9F, 0.45F, 3.5F), after);
		Vector3f at = new Vector3f(0, 0, -14.0F)
				.lerp(new Vector3f(0, 0, (float) Math.min(z, 6.0) + 0.3F), toSide)
				.lerp(new Vector3f(0, 0, (float) Math.max(z, 40.0)), after);
		// The blast at the muzzle shakes the camera.
		float blast = (float) Math.exp(-Math.pow((z + 0.6) / 1.4, 2.0));
		float shake = blast * 0.035F + (float) Math.min(1.0, Math.max(0.0, speed - 3.0) / 20.0) * 0.01F;
		eye.add(noise(s * 7.1) * shake, noise(s * 6.3 + 5.0) * shake, 0);
		localCamera(eye, at, 0, 52.0F - 8.0F * toSide + 16.0F * after);

		space.sky(cam, LOCAL_SKY, 0.9F, 0, cam.forward(), (float) Math.min(0.5, Math.max(0.0, speed - 2.0) * 0.02), 0, 0, time);
		Space.clearDepth();
		space.jupiter(cam, LOCAL_JUPITER, LOCAL_SUN, time * 3, 1.0F);
		Space.clearDepth();
		Fx sun = space.glow(cam, Fx.SPIKES, 0);
		sun.sprite(new Vector3f(RELEASE_SUN).mul(1800.0F), 80.0F, 0, Fx.argb(1.0F, 0.95F, 0.85F, 0.9F));
		sun.end(true);
		space.fillDir.set(0, -1, 0);
		space.fillColor.set(0.3F, 0.17F, 0.08F);

		// The last ninety coils of the barrel. At this speed the trail behind the round is as long as ever, however
		// slowly the shot runs, so the coils fire at full strength.
		for (int k = -90; k <= 0; k++) {
			coil(k, k, (float) (k - z), 0.25F, 1.0F, 0.75F);
		}
		// From outside the barrel the wave shows as a ring of light round each coil as it fires.
		Fx rings = space.glow(cam, Fx.RING, 0.18F);
		for (int k = -90; k <= 0; k++) {
			double rel = k - z;
			float front = (float) (rel >= 0 ? Math.exp(-rel * rel * 2.0) : Math.exp(-rel * rel * 0.5));
			if (front > 0.02F) {
				rings.flat(new Vector3f(0, 0, k), new Vector3f(1.08F, 0, 0), new Vector3f(0, 1.08F, 0), Fx.argb(ARC.x, ARC.y, ARC.z, front));
			}
		}
		rings.end(true, 2.5F);
		// The muzzle blast lights the round and the petals.
		space.pointPos.set(0, 0, 0.4F);
		space.pointColor.set(ARC).mul(2.0F + 26.0F * blast);
		Matrix4f model = new Matrix4f().translation(0, 0, (float) z).scale(ROUND_SCALE);
		boolean attached = z < SABOT_FREE;
		drawRound(model, RELEASE_SUN, 1.2F, 1.8F, 0.12F, attached ? 3.3F : 0.0F, attached);
		if (!attached) {
			petals(z);
		}
		space.pointColor.zero();
		muzzle(z, blast);

		o.header = "[ RELEASE ]";
		o.headerColor = Feed.WHITE;
		o.headerReveal = smooth(s / 4.0);
		o.footer = String.format(Locale.ROOT, "VELOCITY %.4f c", StrikeTimeline.EXIT_VELOCITY);
		// Time all but stands still at the muzzle: the feed switches to its high-speed camera.
		boolean slow = speed < 3.0;
		if (slow) {
			o.footerSmall = z < SABOT_FREE ? "HIGH-SPEED · 1/800" : "HIGH-SPEED · 1/800 · SABOT SEPARATION";
		}
		if (!attached && z < SABOT_FREE + 14.0) {
			Vector3f petal = new Vector3f(0.0F, 0.0F, (float) z).add(petalOffset(z, 0));
			label(o, petal, 10, -4, "SABOT", Feed.RED, "SHED", Feed.GREY, smooth((z - SABOT_FREE - 0.6) / 2.0)
					* (1.0F - smooth((z - SABOT_FREE - 10.0) / 4.0)));
		}
		o.shutter = slow ? 0.0F : 1.0F;
		o.streak = 0.3F;
		o.zoomBlur = (float) Math.min(0.45, Math.max(0.0, speed - 3.0) * 0.012);
		o.exposure = 1.0F + 0.8F * blast;
		o.flash = Math.max(blast * 0.06F, s < 1.5 ? (float) (0.8 * (1.0 - s / 1.5)) : 0.0F);
		o.flashColor = 0xFFFFFF;
		o.aberration = blast * 0.01F;
	}

	/** Where petal {@code k} has drifted to, relative to the spear's centre, at scene time {@code z}. */
	private static Vector3f petalOffset(double z, int k) {
		double since = Math.max(0.0, z - SABOT_FREE);
		double angle = k * 2.0943951 + PETAL_PHASE;
		// Eddy currents in the muzzle's last coil brake the copper-banded petals as the bare spear flies on.
		float lag = (float) (since * 0.15);
		float out = (float) (0.03 + since * 0.07);
		return new Vector3f((float) Math.cos(angle) * out, (float) Math.sin(angle) * out, -lag);
	}

	/** The three petals of the sabot falling away from the spear, tumbling, the pyro bolts flashing as they go. */
	private void petals(double z) {
		double since = z - SABOT_FREE;
		for (int k = 0; k < 3; k++) {
			double angle = k * 2.0943951 + PETAL_PHASE;
			Vector3f off = petalOffset(z, k);
			float tumble = (float) (since * (0.035 + 0.012 * k));
			float cx = 0.3F * ROUND_SCALE;
			float cz = -0.15F * ROUND_SCALE;
			Matrix4f petal = new Matrix4f().translation(off.x, off.y, (float) z + off.z).rotateZ((float) angle)
					.translate(cx, 0, cz).rotateY(tumble).rotateX((float) (since * 0.01 * (k - 1))).translate(-cx, 0, -cz)
					.scale(ROUND_SCALE);
			float hot = (float) Math.exp(-since / 6.0);
			space.mesh(space.sabot, cam, petal, RELEASE_SUN, 1.2F, 1.0F, 0.45F, 0.12F, 2.0F * hot, 0);
		}
		if (since < 3.0) {
			// The separation charges along the seams.
			Fx bolts = space.glow(cam, Fx.BLOB, 1.0F);
			float pop = (float) Math.exp(-since / 0.7);
			for (int k = 0; k < 3; k++) {
				double seam = k * 2.0943951 + PETAL_PHASE + 1.0471976;
				float r = 0.44F * ROUND_SCALE + (float) since * 0.04F;
				for (int j = 0; j < 3; j++) {
					float along = (float) z + (0.7F - j * 0.6F) * ROUND_SCALE;
					Vector3f p = new Vector3f((float) Math.cos(seam) * r, (float) Math.sin(seam) * r, along);
					bolts.sprite(p, 0.06F + 0.1F * (1.0F - pop), 0, Fx.argb(1.0F, 0.85F, 0.55F, pop));
				}
			}
			bolts.end(true, 4.0F);
		}
	}

	/**
	 * Arcs jumping from the emitters of the {@code coils} coils at and behind z = 0 to the sabot's copper bands on a
	 * round centred at {@code roundZ}, re-struck every few frames; {@code strength} fades them.
	 */
	private void arcs(float roundZ, float strength, int coils) {
		Fx arcs = space.glow(cam, Fx.BEAM, 0);
		random.setSeed(9001L + (long) Math.floor((roundZ + 10.0) * 6.0) + (long) Math.floor(time * 0.7));
		for (int i = 0; i < 9; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			float coilZ = -random.nextInt(coils);
			Vector3f from = new Vector3f((float) Math.cos(a) * 0.72F, (float) Math.sin(a) * 0.72F, coilZ);
			float bandZ = roundZ + (0.85F - 0.7F * random.nextInt(4)) * ROUND_SCALE;
			Vector3f to = new Vector3f((float) Math.cos(a) * 0.19F, (float) Math.sin(a) * 0.19F, bandZ);
			Vector3f mid = new Vector3f(from).lerp(to, 0.5F).add((float) random.nextGaussian() * 0.08F,
					(float) random.nextGaussian() * 0.08F, (float) random.nextGaussian() * 0.05F);
			int c = Fx.argb(ARC.x, ARC.y, ARC.z, 0.9F * Math.min(1.0F, strength));
			arcs.beam(from, mid, cam.pos, 0.012F, c, c);
			arcs.beam(mid, to, cam.pos, 0.012F, c, c);
		}
		arcs.end(true, 3.0F);
	}

	/**
	 * The muzzle as the round leaves it: arcs jumping from the last coil to the sabot's bands, a burst of light,
	 * and a ring of plasma blown out across the mouth of the barrel.
	 */
	private void muzzle(double z, float blast) {
		if (z > -2.5 && z < 1.6) {
			arcs((float) z, 1.0F, 2);
		}
		double since = z + 1.0;
		if (since > 0) {
			Fx ring = space.glow(cam, Fx.RING, 0.12F);
			float radius = (float) (0.5 + since * 0.45);
			float fade = (float) Math.exp(-since / 5.0);
			ring.flat(new Vector3f(0, 0, 0.2F), new Vector3f(radius, 0, 0), new Vector3f(0, radius, 0),
					Fx.argb(ARC.x, ARC.y, ARC.z, fade));
			ring.end(true, 2.5F);
		}
		if (blast > 0.01F || since > 0) {
			float glow = blast + (float) (since > 0 ? 0.15 * Math.exp(-since / 4.0) : 0.0);
			Fx core = space.glow(cam, Fx.BLOB, 1.0F);
			core.sprite(new Vector3f(0, 0, 0.3F), 0.22F + 0.45F * glow, 0, Fx.argb(0.85F, 0.92F, 1.0F, glow));
			core.end(true, 2.0F);
			Fx flare = space.glow(cam, Fx.SPIKES, 0);
			flare.sprite(new Vector3f(0, 0, 0.3F), 0.5F + 1.3F * blast, 0, Fx.argb(1.0F, 0.95F, 0.9F, blast));
			flare.end(true, 4.0F);
		}
	}

	// =============================================================================================
	// 7. Across the main belt at 0.96c.
	// =============================================================================================

	private void debris(double s, Overlay o) {
		// Scene time slows almost to a stop around the rock, then picks up again.
		double scene = beltTime(s);
		double sinceHit = scene - beltTime(ROCK_HIT);
		boolean slow = beltRate(s) < 0.5;
		float orbitCam = (float) (s * 0.012);
		float aside = (float) Math.exp(-Math.pow((s - ROCK_HIT) / 3.5, 2.0));
		Vector3f eye = new Vector3f(0.95F, 0.62F, -3.5F).rotateZ(orbitCam).lerp(new Vector3f(1.55F, 0.5F, 1.4F), aside);
		float strike = (float) Math.exp(-Math.pow(sinceHit / 0.6, 2.0));
		float jolt = sinceHit > 0 ? (float) Math.exp(-sinceHit / 2.0) * 0.06F : 0.0F;
		eye.add(noise(s * 9.0) * jolt, noise(s * 8.0 + 3.0) * jolt, 0);
		localCamera(eye, new Vector3f(0, 0, 2.5F + 0.8F * aside), orbitCam * 0.4F * (1.0F - aside), 60.0F - 8.0F * aside);
		float beta = 0.9612F + 0.0112F * (float) (s / 26.0);
		space.sky(cam, LOCAL_SKY, 0.55F, beta * 0.6F, new Vector3f(0, 0, 1), 0.08F, 0, 0, time);

		// Earth: a bright blue point dead ahead.
		Fx earth = space.glow(cam, Fx.SPIKES, 0);
		Vector3f earthPos = new Vector3f(6, 3, 900);
		earth.sprite(earthPos, 3.0F + (float) s * 0.12F, 0, Fx.argb(0.55F, 0.75F, 1.0F, 0.9F));
		earth.end(true);

		Space.clearDepth();
		random.setSeed(4404L);
		double travel = scene * 13.0;
		Fx sparks = null;
		for (int i = 0; i < 230; i++) {
			double radius = 1.6 + Math.pow(random.nextDouble(), 0.6) * 34.0;
			double angle = random.nextDouble() * Math.PI * 2;
			double z0 = random.nextDouble() * 420.0;
			float size = (float) (0.15 + Math.pow(random.nextDouble(), 2.0) * (1.0 + radius * 0.08));
			Vector3f axis = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian()).normalize();
			float spin = (float) (random.nextGaussian() * 0.05);
			int variant = random.nextInt(space.rocks.length);
			float z = (float) (((z0 - travel) % 420.0 + 420.0) % 420.0) - 40.0F;
			Vector3f pos = new Vector3f((float) (Math.cos(angle) * radius), (float) (Math.sin(angle) * radius), z);
			Matrix4f rock = new Matrix4f().translation(pos).rotate((float) (s * spin + i), axis).scale(size);
			space.mesh(space.rocks[variant], cam, rock, DEBRIS_SUN, 1.6F, 0, 0, 0);
			if (radius < 4.0 && Math.abs(z) < 3.0) {
				if (sparks == null) {
					sparks = space.glow(cam, Fx.BLOB, 1.0F);
				}
				sparks.sprite(pos, size * 2.0F, 0, Fx.argb(1.0F, 0.6F, 0.25F, 0.8F));
			}
		}
		if (sparks != null) {
			sparks.end(true);
		}
		rockStrike(s, sinceHit);

		// The bare spear now, the blade warm from the muzzle.
		space.pointPos.set(0, 0, 5.0F * ROUND_SCALE + 1.0F);
		space.pointColor.set(1.0F, 0.8F, 0.6F).mul(30.0F * strike);
		drawRound(new Matrix4f().scale(ROUND_SCALE), DEBRIS_SUN, 1.3F, 1.2F, 0.3F + 0.6F * strike, 0, false);
		space.pointColor.zero();
		Fx bow = space.glow(cam, Fx.BLOB, 1.0F);
		bow.sprite(new Vector3f(0, 0, 5.1F * ROUND_SCALE), 0.25F, 0, Fx.argb(1.0F, 0.75F, 0.5F, 0.6F));
		bow.end(true);

		label(o, earthPos, 10, -6, "EARTH", Feed.CYAN, "TARGET", Feed.GREY, smooth((s - 6) / 4.0));
		o.header = "[ DEBRIS FIELD · MAIN BELT ]";
		o.headerReveal = smooth((s - 1) / 5.0);
		long range = (long) (843_406_388.0 * Math.pow(1.0 - s / 26.0, 3.0) + 604_785.0 * (s / 26.0));
		o.footer = "RANGE " + Feed.commas(range) + " KM";
		o.footerSmall = slow ? String.format(Locale.ROOT, "VELOCITY %.4f c · HIGH-SPEED · 1/400", beta)
				: String.format(Locale.ROOT, "VELOCITY %.4f c", beta);
		o.shutter = slow ? 0.0F : 0.6F;
		o.zoomBlur = 0.08F;
		o.aberration = 0.006F + strike * 0.02F;
		o.exposure = 1.0F + 2.5F * strike;
		// A quick white cut in from the muzzle shot.
		o.flash = Math.max(strike * 0.25F, s < 2 ? (float) (0.9 * (1.0 - s / 2.0)) : 0.0F);
	}

	/** Ticks into the belt at which a rock sits squarely in the round's path. */
	private static final double ROCK_HIT = 10.0;

	/** How fast scene time runs in the belt, ticks of scene time per tick: nearly stopped at the rock. */
	private static double beltRate(double s) {
		return 1.0 - 0.93 * Math.exp(-Math.pow((s - ROCK_HIT) / 2.2, 2.0));
	}

	/** Scene time {@code s} ticks into the belt (the integral of {@link #beltRate}). */
	private static double beltTime(double s) {
		int steps = 48;
		double h = Math.max(s, 0.0) / steps;
		double sum = 0.0;
		for (int i = 0; i < steps; i++) {
			sum += beltRate((i + 0.5) * h);
		}
		return sum * h;
	}

	/**
	 * A boulder dead ahead, and what is left of it after the round goes through: a flash, a ball of vaporised rock
	 * glowing and spreading, and shards thrown out and streaming back past the camera.
	 */
	private void rockStrike(double s, double since) {
		double travel = 13.0;
		float nose = 5.0F * ROUND_SCALE;
		if (since < 0) {
			float z = nose + (float) (-since * travel);
			Matrix4f rock = new Matrix4f().translation(0.0F, 0.05F, z).rotate((float) (s * 0.04), new Vector3f(0.3F, 1.0F, 0.2F).normalize())
					.scale(0.9F);
			space.mesh(space.rocks[2], cam, rock, DEBRIS_SUN, 1.6F, 0, 0, 0);
			return;
		}
		// Everything the rock was keeps its own speed, so in the round's frame it streams back past the camera.
		float back = (float) (since * travel);
		float fade = (float) Math.exp(-since / 3.0);
		Fx gas = space.glow(cam, Fx.BLOB, 1.0F);
		for (int i = 0; i < 6; i++) {
			float z = nose - back + i * 0.8F;
			gas.sprite(new Vector3f(0, 0, z), (float) (0.6 + since * (0.9 + i * 0.25)), 0,
					Fx.argb(1.0F, 0.55F + 0.3F * fade, 0.25F + 0.4F * fade, 0.55F * fade * (1.0F - i / 7.0F)));
		}
		gas.end(true, 2.5F);
		Fx shards = space.glow(cam, Fx.STREAK, 0);
		random.setSeed(77L);
		for (int i = 0; i < 70; i++) {
			Vector3f dir = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian() * 0.4F)
					.normalize();
			float speed = 0.3F + random.nextFloat() * 1.2F;
			Vector3f p = new Vector3f(dir).mul((float) (since * speed)).add(0, 0, nose - back);
			Vector3f motion = new Vector3f(dir).mul(speed).add(0, 0, (float) -travel);
			float heat = (float) Math.exp(-since / (1.0 + random.nextFloat() * 3.0));
			shards.stretched(p, motion, 0.4F + 2.5F * heat, 0.04F, Fx.argb(1.0F, 0.5F + 0.4F * heat, 0.15F + 0.5F * heat, heat));
		}
		shards.end(true, 2.0F);
		if (since < 2.5) {
			float pop = (float) Math.exp(-since / 0.5);
			Fx flash = space.glow(cam, Fx.SPIKES, 0);
			flash.sprite(new Vector3f(0, 0, nose - back * 0.3F), 1.5F + 4.0F * pop, 0, Fx.argb(1.0F, 0.95F, 0.85F, pop));
			flash.end(true, 4.0F);
		}
	}

	// =============================================================================================
	// 8. Terminal: down through the atmosphere onto the target.
	// =============================================================================================

	private void terminal(double s, Overlay o) {
		// From 28 Earth radii down to the cloud deck: most of the distance goes in the first half, then the long
		// burn down through the air.
		float e = smootherIn(s / 31.0);
		float altitude = (float) (28.0 * Math.pow(0.0012 / 28.0, e));
		Vector3f roundPos = new Vector3f(TARGET).mul(1.0F + altitude);
		Vector3f down = new Vector3f(TARGET).negate();
		Vector3f side = new Vector3f(down).cross(NORTH).normalize();
		float behind = Math.max(altitude * 0.15F, 0.00025F);
		float heat = smooth((s - 9.0) / 12.0);
		// Behind the round on the way in; out to its side through the worst of the heat, where the shock layer
		// shows; back behind it to punch through the clouds.
		float beside = smooth((s - 10.0) / 6.0) * (1.0F - smooth((s - 25.0) / 5.0));
		Vector3f backward = new Vector3f(EAST).mul(0.35F).add(TARGET).normalize();
		Vector3f eyeBehind = new Vector3f(roundPos).add(new Vector3f(backward).mul(behind));
		Vector3f atBehind = new Vector3f(roundPos).add(new Vector3f(down).mul(behind * 3.0F));
		Vector3f eyeSide = new Vector3f(roundPos).add(new Vector3f(side).mul(behind * 0.95F)).add(new Vector3f(TARGET).mul(behind * 0.25F));
		Vector3f atSide = new Vector3f(roundPos).add(new Vector3f(down).mul(behind * 0.7F));
		Vector3f eye = new Vector3f(eyeBehind).lerp(eyeSide, beside);
		Vector3f at = new Vector3f(atBehind).lerp(atSide, beside);
		Vector3f up = new Vector3f(NORTH).lerp(TARGET, beside).normalize();
		float shake = heat * 0.004F + smooth((s - 26.0) / 4.0) * 0.004F;
		at.add(noise(s * 4.1) * shake * behind * 40, noise(s * 3.3 + 4) * shake * behind * 40, noise(s * 3.7 + 8) * shake * behind * 20);
		earthCamera(new Pose(eye, at, up, 58.0F + 10.0F * beside), Math.max(behind * 0.05F, 0.000005F), 80.0F);
		space.sky(cam, EARTH_SKY, 1.0F, 0, cam.forward(), 0, 0, 0, time);
		space.earth(cam, new Matrix4f(), EARTH_SUN, time * 0.00005F, smooth((s - 10) / 8.0), 1.05F);

		// The round at its own scale, framed the same way as the camera above.
		Space.clearDepth();
		float unit = behind / 3.2F;
		float body = unit * ROUND_SCALE;
		Matrix4f roundModel = new Matrix4f().translation(roundPos).rotateTowards(down, new Vector3f(NORTH)).scale(body);
		// The shock layer lights the spear from the front.
		space.pointPos.set(roundPos).add(new Vector3f(down).mul(body * 6.0F));
		// (The scene is in Earth radii, so at this scale the light does not fall off over the spear's length.)
		space.pointColor.set(1.0F, 0.75F, 0.5F).mul(heat * 5.0F);
		drawRound(roundModel, EARTH_SUN, 1.1F, 1.4F, heat, 0, false);
		space.pointColor.zero();
		if (heat > 0.01F) {
			Vector3f tip = new Vector3f(roundPos).add(new Vector3f(down).mul(body * 5.0F));
			Matrix4f sheath = new Matrix4f().translation(roundPos).rotateTowards(down, new Vector3f(NORTH))
					.translate(0, 0, body * 5.4F).scale(body * 2.6F, body * 2.6F, body * 9.0F);
			space.plasma(space.cone, cam, sheath, time * 0.05F, heat * 1.15F, 0.35F + heat * 0.65F, new Vector3f(0, 0, -6.0F), 1.0F);
			// The cap of air at the point, compressed white-hot.
			Fx cap = space.glow(cam, Fx.BLOB, 1.0F);
			cap.sprite(new Vector3f(tip).add(new Vector3f(down).mul(body * 0.35F)), body * (0.6F + heat * 1.6F), 0,
					Fx.argb(1.0F, 0.95F, 0.9F, heat));
			cap.sprite(new Vector3f(roundPos).add(new Vector3f(down).mul(body * 3.5F)), body * (1.5F + heat * 4.0F), 0,
					Fx.argb(1.0F, 0.62F, 0.3F, heat * 0.7F));
			cap.end(true, 2.0F);
			// The wake: shocked air glowing for kilometres behind.
			Fx wake = space.glow(cam, Fx.BEAM, 0);
			Vector3f tail = new Vector3f(roundPos).sub(new Vector3f(down).mul(body * 5.0F));
			Vector3f far = new Vector3f(tail).sub(new Vector3f(down).mul(body * (40.0F + 120.0F * heat)));
			wake.beam(tail, far, cam.pos, body * (0.5F + 1.4F * heat), Fx.argb(1.0F, 0.6F, 0.3F, heat * 0.8F), Fx.argb(1.0F, 0.35F, 0.12F, 0.0F));
			wake.end(true, 2.0F);
			// Ionised air tearing off the sheath and streaming back past the camera.
			Vector3f side2 = new Vector3f(side).cross(down).normalize();
			Fx streaks = space.glow(cam, Fx.STREAK, 0);
			for (int i = 0; i < 110; i++) {
				double f = (time * (0.07 + 0.04 * heat) + (i * 0.618034) % 1.0) % 1.0;
				double angle = i * 2.399963;
				float r = body * (1.1F + (i % 9) * 0.35F) * (1.0F + (float) f * 0.8F);
				Vector3f p = new Vector3f(roundPos).add(new Vector3f(down).mul(body * (9.0F - 34.0F * (float) f)))
						.add(new Vector3f(side).mul((float) Math.cos(angle) * r)).add(new Vector3f(side2).mul((float) Math.sin(angle) * r));
				float fade = (float) Math.sin(Math.PI * f);
				streaks.stretched(p, down, body * (1.5F + 6.0F * heat), body * 0.05F,
						Fx.argb(1.0F, 0.72F + 0.2F * heat, 0.42F + 0.2F * heat, heat * fade * 0.85F));
			}
			streaks.end(true, 3.0F);
		}
		// The cloud deck: puffs rushing up past the camera until everything is white.
		float deck = smooth((s - 25.0) / 6.0);
		if (deck > 0.0F) {
			Vector3f side2 = new Vector3f(side).cross(down).normalize();
			Fx clouds = space.glow(cam, Fx.BLOB, 1.0F);
			random.setSeed(5150L);
			for (int i = 0; i < 40; i++) {
				float ahead = (float) (((random.nextDouble() * 40.0 - (s - 25.0) * 9.0) % 40.0 + 40.0) % 40.0) - 4.0F;
				double a = random.nextDouble() * Math.PI * 2;
				float r = behind * (0.6F + random.nextFloat() * 2.5F);
				Vector3f p = new Vector3f(roundPos).add(new Vector3f(down).mul(behind * ahead * 0.5F))
						.add(new Vector3f(side).mul((float) Math.cos(a) * r)).add(new Vector3f(side2).mul((float) Math.sin(a) * r));
				clouds.sprite(p, behind * (1.0F + random.nextFloat() * 2.0F), 0, Fx.argb(0.9F, 0.92F, 0.96F, 0.35F * deck));
			}
			clouds.end(true, 1.4F);
		}
		o.header = "[ TERMINAL · SOL-3 ]";
		o.headerReveal = smooth(s / 4.0);
		long range = Math.max(0, (long) (altitude * 6371.0));
		o.footer = "RANGE " + Feed.commas(range) + " KM";
		o.footerSmall = heat > 0.2F ? "VELOCITY 0.9724 c · HULL " + Math.round(1200 + heat * 30000) + " K" : "VELOCITY 0.9724 c";
		o.flashColor = s > 27 ? 0xFFFFFF : 0xFF9050;
		o.flash = s > 27 ? smooth((s - 27) / 6.5) : heat * 0.08F;
		o.aberration = heat * 0.01F;
		o.saturation = 1.0F + heat * 0.25F;
		o.exposure = 1.0F + heat * 0.35F;
	}

	// =============================================================================================
	// Cameras and helpers
	// =============================================================================================

	private void earthCamera(Pose pose, float near, float far) {
		cam.perspective(pose.fov(), width, height, near, far);
		cam.look(pose.eye(), pose.at(), pose.up());
	}

	private void jupiterCamera(Pose pose, float near, float far) {
		cam.perspective(pose.fov(), width, height, near, far);
		cam.look(pose.eye(), pose.at(), pose.up());
	}

	private void localCamera(Vector3f eye, Vector3f at, float roll, float fov) {
		cam.perspective(fov, width, height, 0.05F, 4000.0F);
		cam.lookDir(eye, new Vector3f(at).sub(eye).normalize(), new Vector3f(0, 1, 0), roll);
	}

	@Nullable
	private Overlay.Label label(Overlay o, Vector3f world, float dx, float dy, String line1, int color1, String line2, int color2,
			float alpha) {
		if (alpha <= 0.02F) {
			return null;
		}
		Vector3f p = cam.screen(world, guiW, guiH);
		if (p == null || p.x < 0 || p.y < 0 || p.x > guiW || p.y > guiH) {
			return null;
		}
		Overlay.Label label = new Overlay.Label(p.x + dx, p.y + dy, line1, color1, line2, color2);
		label.alpha = alpha;
		label.markerX = p.x;
		label.markerY = p.y;
		o.labels.add(label);
		return label;
	}

	/** World-to-map rotation for the sky with the galactic centre towards {@code center}, north towards {@code pole}. */
	private static Matrix4f skyFrame(Vector3f center, Vector3f pole) {
		Vector3f x = new Vector3f(center).normalize();
		Vector3f y = new Vector3f(pole).sub(new Vector3f(x).mul(pole.dot(x))).normalize();
		Vector3f z = new Vector3f(x).cross(y);
		// Columns are the galactic axes in world space; the transpose takes world directions into the map.
		return new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1).transpose();
	}

	private static Vector3f slerp(Vector3f a, Vector3f b, float t) {
		float dot = Math.max(-1.0F, Math.min(1.0F, a.dot(b)));
		float theta = (float) Math.acos(dot) * t;
		Vector3f rel = new Vector3f(b).sub(new Vector3f(a).mul(dot));
		if (rel.lengthSquared() < 1.0E-10F) {
			return new Vector3f(a);
		}
		rel.normalize();
		return new Vector3f(a).mul((float) Math.cos(theta)).add(rel.mul((float) Math.sin(theta)));
	}

	private static float noise(double x) {
		return (float) (Math.sin(x * 1.7) * 0.5 + Math.sin(x * 3.1 + 1.3) * 0.3 + Math.sin(x * 5.9 + 2.1) * 0.2);
	}

	static float smooth(double x) {
		x = x < 0 ? 0 : x > 1 ? 1 : x;
		return (float) (x * x * (3 - 2 * x));
	}

	static float smoother(double x) {
		x = x < 0 ? 0 : x > 1 ? 1 : x;
		return (float) (x * x * x * (x * (x * 6 - 15) + 10));
	}

	/** Eases in only: starts slow, arrives at full speed (for the fall to Earth). */
	static float smootherIn(double x) {
		x = x < 0 ? 0 : x > 1 ? 1 : x;
		return (float) (x * x * (2 - x));
	}

	private static float lerp(double a, double b, float t) {
		return (float) (a + (b - a) * t);
	}
}
