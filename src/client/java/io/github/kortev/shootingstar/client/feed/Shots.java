package io.github.kortev.shootingstar.client.feed;

import io.github.kortev.shootingstar.client.gfx.Cam;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.Mesh;
import io.github.kortev.shootingstar.client.render.Gfx;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import java.util.Locale;
import java.util.Random;
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
	/** Where the relay beam points: along the horizon, out towards Jupiter. */
	private static final Vector3f TO_JUPITER = new Vector3f(EAST).mul(-0.8F).add(new Vector3f(NORTH).mul(0.55F))
			.add(new Vector3f(TARGET).mul(0.12F)).normalize();
	private static final Vector3f RELAY_POS = new Vector3f(TARGET).mul(0.86F).add(new Vector3f(EAST).mul(-0.42F))
			.add(new Vector3f(NORTH).mul(0.3F)).normalize().mul(1.32F);
	/** The galactic centre sits behind the climbing camera, so only the faint anticentre lies behind Earth. */
	private static final Matrix4f EARTH_SKY = skyFrame(slerp(TARGET, new Vector3f(TARGET).mul(0.8F)
			.add(new Vector3f(EAST).mul(-0.5F)).add(new Vector3f(NORTH).mul(-0.14F)).normalize(), 0.9F), NORTH);

	// --- Jupiter scene: radius 1 at the origin, ring in the equatorial plane --------------------
	static final float RING = 1.22F;
	private static final Vector3f JUPITER_SUN = new Vector3f(0.9F, 0.18F, 0.45F).normalize();
	private static final Matrix4f JUPITER_SKY = new Matrix4f().rotateXYZ(-0.5F, 1.1F, 0.35F);

	// --- Breech scene: the beam runs along +Z, Jupiter far below ---------------------------------
	private static final float ROUND_SCALE = 0.42F;
	private static final Vector3f LOCAL_SUN = new Vector3f(0.55F, 0.62F, 0.55F).normalize();
	private static final Matrix4f LOCAL_SKY = new Matrix4f().rotateXYZ(0.2F, -0.7F, 1.25F);
	private static final Matrix4f LOCAL_JUPITER = new Matrix4f().translation(0, -1586, 0).rotateZ((float) (Math.PI / 2)).scale(1300);
	/** Out in the belt the sun is behind the camera, so the rocks and the round show their lit faces. */
	private static final Vector3f DEBRIS_SUN = new Vector3f(-0.35F, 0.55F, -0.75F).normalize();

	private static final int ORANGE = 0xFF7A1E;
	private static final int HOT = 0xFFB070;

	private final Space space = new Space();
	private final Cam cam = new Cam();
	private final Random random = new Random();
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
		if (t < StrikeTimeline.RELAY) {
			orbit(t - StrikeTimeline.ORBIT, o);
		} else if (t < StrikeTimeline.WAKE) {
			relay(t - StrikeTimeline.RELAY, o);
		} else if (t < StrikeTimeline.LOADING) {
			wake(t - StrikeTimeline.WAKE, o);
		} else if (t < StrikeTimeline.LAPS) {
			loading(t - StrikeTimeline.LOADING, o);
		} else if (t < StrikeTimeline.DEBRIS) {
			laps(t, o);
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
		Vector3f at = new Vector3f(start.at()).lerp(nearAt, k);
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
		space.mesh(space.relay, cam, satellite, EARTH_SUN, 1.15F, 0xFFD27A, 1.0F, 0);

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
			beam.end(true);
			Fx pulse = space.glow(cam, Fx.SPIKES, 0);
			pulse.sprite(head, 0.03F + (float) reach * 0.01F, 0, Fx.argb(1.0F, 0.9F, 0.8F, 1.0F));
			pulse.end(true);
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
		// Dive at the breech to cut into the close-up.
		float dive = (float) Math.pow(smooth((s - 37) / 7.0), 2.0);
		Vector3f breech = new Vector3f(RING, 0, 0);
		if (dive > 0) {
			eye.lerp(new Vector3f(breech).add(0.05F, 0.03F, 0.08F), dive);
			at.lerp(breech, dive);
			fov -= 14.0F * dive;
		}
		jupiterCamera(new Pose(eye, at, pose.up(), fov), 0.002F, 200.0F);
		space.sky(cam, JUPITER_SKY, 1.0F, 0, cam.forward(), 0, 0, 0, time);
		float progress = smoother((s - 4) / 30.0);
		jupiterScene(progress, 0.05F, 0.6F + 0.4F * (float) Math.sin(s * 0.3));
		o.zoomBlur = dive * 0.55F;

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
		space.jupiter(cam, jupiter, JUPITER_SUN, time, 1.0F);
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
		breechScene(0.0, round, 1.0F + lockFlash * 2.5F, 0.0F);
		o.zoomBlur = 0.55F * (1.0F - smooth(s / 4.0));
		o.header = locked ? "[ BREECH LOCKED ]" : "[ LOADING ]";
		o.headerColor = locked ? Feed.ORANGE : Feed.RED;
		o.headerReveal = locked ? smooth((s - 11) / 3.0) : smooth(s / 4.0);
		o.flash = lockFlash * 0.25F;
		o.flashColor = 0xFFB070;
		if (s < 12) {
			label(o, new Vector3f(0, 1.05F, 0), 8, -4, "BREECH", Feed.RED, "COIL 000,001", Feed.GREY, smooth((s - 2) / 3.0));
		}
	}

	/**
	 * Coils along +Z with the round at {@code roundZ}; {@code travel} slides the tunnel (in coil spacings),
	 * {@code glow} drives the panels and {@code speed} adds light trails.
	 */
	private void breechScene(double travel, float roundZ, float glow, float speed) {
		space.sky(cam, LOCAL_SKY, 0.9F, 0, cam.forward(), 0, 0, 0, time);
		// Jupiter as a backdrop, drawn without depth so it never cuts into the coils.
		Space.clearDepth();
		space.jupiter(cam, LOCAL_JUPITER, LOCAL_SUN, time * 3, 1.0F);
		Space.clearDepth();

		double offset = travel - Math.floor(travel);
		// Each coil fires as the round reaches it: a sharp front just ahead, a long cooling trail behind.
		float fireGain = speed > 0 ? 1.2F + 3.5F * speed : 0.0F;
		for (int k = -12; k <= 70; k++) {
			float z = (float) (k - offset);
			float rel = z - roundZ;
			float fire = rel >= 0 ? (float) Math.exp(-rel * rel * 3.0) : (float) Math.exp(rel / (1.5F + 10.0F * speed));
			Matrix4f coil = new Matrix4f().translation(0, 0, z);
			space.mesh(space.coil, cam, coil, LOCAL_SUN, 1.0F, ORANGE, glow + fire * fireGain, 0);
		}
		Matrix4f model = new Matrix4f().translation(0, 0, roundZ).scale(ROUND_SCALE);
		space.mesh(space.round, cam, model, LOCAL_SUN, 1.1F, ORANGE, 1.4F + speed, speed * 0.6F);
		if (speed > 0.05F) {
			// Light trails along the panels.
			Fx trails = space.glow(cam, Fx.STREAK, 0);
			for (int i = 0; i < 40; i++) {
				double angle = (i * 2.399963) % (Math.PI * 2);
				float r = 0.78F;
				float z = (float) ((i * 7.31 + travel * 0.5) % 60.0) - 6.0F;
				float length = 0.5F + speed * 6.0F;
				Vector3f c = new Vector3f((float) Math.cos(angle) * r, (float) Math.sin(angle) * r, z);
				trails.stretched(c, new Vector3f(0, 0, 1), length, 0.035F, Fx.argb(1.0F, 0.6F, 0.25F, Math.min(1.0F, speed * 1.4F)));
			}
			trails.end(true);
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
		return StrikeTimeline.LAPS + p * (StrikeTimeline.DEBRIS - StrikeTimeline.LAPS);
	}

	/** Coil spacings covered since the launch: speed grows with velocity^1.3, integrated in closed form. */
	private static double travel(double t) {
		double u = Math.max(0, t - StrikeTimeline.LAPS);
		double b0 = StrikeTimeline.ENTRY_VELOCITY;
		double k = (StrikeTimeline.EXIT_VELOCITY - b0) / (StrikeTimeline.DEBRIS - StrikeTimeline.LAPS);
		double beta = b0 + k * Math.min(u, StrikeTimeline.DEBRIS - StrikeTimeline.LAPS);
		return 0.6 * u + 22.0 / (k * 2.3) * (Math.pow(beta, 2.3) - Math.pow(b0, 2.3));
	}

	private void tunnel(double t, int lap, float velocity, double since, Overlay o) {
		Vector3f eye;
		Vector3f at;
		float roll = 0;
		float sway = (float) Math.sin(t * 0.21) * 0.05F;
		switch (lap) {
			case 1 -> {
				eye = new Vector3f(sway, 0.42F, -2.7F);
				at = new Vector3f(0, 0.05F, 3.0F);
			}
			case 3 -> {
				eye = new Vector3f(0.36F, -0.22F + sway, -2.0F);
				at = new Vector3f(0, 0, 3.5F);
				roll = 0.16F;
			}
			case 5 -> {
				eye = new Vector3f(sway * 0.5F, 0.06F, 2.55F);
				at = new Vector3f(0, 0.02F, 10.0F);
			}
			default -> {
				eye = new Vector3f(0, 0.24F, -1.6F);
				at = new Vector3f(0, 0, 6.0F);
				roll = (float) (since * 0.01);
			}
		}
		float shake = velocity * 0.012F;
		eye.add(noise(t * 3.1) * shake, noise(t * 2.7 + 9) * shake, 0);
		localCamera(eye, at, roll, 62.0F + velocity * 8.0F);
		breechScene(travel(t), 0, 0.45F + 0.3F * velocity, velocity);
		// The sky crowds forward and turns blue as the round nears c.
		o.zoomBlur = 0.04F + velocity * (lap >= 5 ? 0.42F : 0.24F);
		o.aberration = velocity * 0.012F;
		if (lap == StrikeTimeline.LAP_COUNT) {
			double end = StrikeTimeline.DEBRIS - lapStart(lap);
			o.flash = Math.max(o.flash, (float) Math.pow(smooth((since - end * 0.45) / (end * 0.55)), 2.0));
		}
	}

	private void exterior(double t, int lap, double covered, float velocity, double since, Overlay o) {
		double angle = (covered * StrikeTimeline.LAP_COUNT % 1.0) * Math.PI * 2;
		Vector3f eye;
		Vector3f at;
		float fov;
		switch (lap) {
			case 2 -> {
				eye = new Vector3f(3.1F, 1.15F, 2.5F);
				at = new Vector3f(0, -0.12F, 0);
				fov = 40.0F;
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
		eye.rotateY((float) (since * 0.0025));
		jupiterCamera(new Pose(eye, at, new Vector3f(0, 1, 0), fov), 0.0008F, 200.0F);
		space.sky(cam, JUPITER_SKY, 1.0F, 0, cam.forward(), 0, 0, 0, time);
		jupiterScene(1.0F, 0.05F, 1.0F);
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
		if (lap == 4) {
			// A flash as it tears past the camera.
			double pass = Math.abs(angle - Math.PI);
			o.flash = Math.max(o.flash, (float) (0.6 * Math.exp(-pass * pass * 400)));
		}
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
	// 6. Release, then across the main belt at 0.96c.
	// =============================================================================================

	private void debris(double s, Overlay o) {
		float orbitCam = (float) (s * 0.012);
		Vector3f eye = new Vector3f(0.95F, 0.62F, -3.5F).rotateZ(orbitCam);
		localCamera(eye, new Vector3f(0, 0, 2.5F), orbitCam * 0.4F, 60.0F);
		float beta = 0.9612F + 0.0112F * (float) (s / 26.0);
		space.sky(cam, LOCAL_SKY, 1.0F, beta * 0.6F, new Vector3f(0, 0, 1), 0.12F, 0, 0, time);

		// Earth: a bright blue point dead ahead.
		Fx earth = space.glow(cam, Fx.SPIKES, 0);
		Vector3f earthPos = new Vector3f(6, 3, 900);
		earth.sprite(earthPos, 3.0F + (float) s * 0.12F, 0, Fx.argb(0.55F, 0.75F, 1.0F, 0.9F));
		earth.end(true);

		Space.clearDepth();
		random.setSeed(4404L);
		double travel = s * 13.0;
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
		space.mesh(space.round, cam, new Matrix4f().scale(ROUND_SCALE), DEBRIS_SUN, 1.3F, ORANGE, 1.6F, 0.35F);
		Fx bow = space.glow(cam, Fx.BLOB, 1.0F);
		bow.sprite(new Vector3f(0, 0, 5.1F * ROUND_SCALE), 0.25F, 0, Fx.argb(1.0F, 0.75F, 0.5F, 0.6F));
		bow.end(true);

		label(o, earthPos, 10, -6, "EARTH", Feed.CYAN, "TARGET", Feed.GREY, smooth((s - 6) / 4.0));
		o.header = "[ DEBRIS FIELD · MAIN BELT ]";
		o.headerReveal = smooth((s - 3) / 5.0);
		long range = (long) (843_406_388.0 * Math.pow(1.0 - s / 26.0, 3.0) + 604_785.0 * (s / 26.0));
		o.footer = "RANGE " + Feed.commas(range) + " KM";
		o.footerSmall = String.format(Locale.ROOT, "VELOCITY %.4f c", beta);
		o.zoomBlur = 0.08F;
		o.aberration = 0.006F;
		// The release: a white frame with the word on it.
		o.flash = Math.max(o.flash, s < 2 ? 1.0F : (float) Math.max(0, 1.0 - (s - 2) / 3.0));
		o.banner = "[ RELEASE ]";
		o.bannerAlpha = 1.0F - smooth((s - 3) / 2.0);
	}

	// =============================================================================================
	// 7. Terminal: down through the atmosphere onto the target.
	// =============================================================================================

	private void terminal(double s, Overlay o) {
		float e = smootherIn(s / 22.0);
		float altitude = (float) (28.0 * Math.pow(0.0015 / 28.0, e));
		Vector3f roundPos = new Vector3f(TARGET).mul(1.0F + altitude);
		Vector3f down = new Vector3f(TARGET).negate();
		// The camera rides behind and above the round; Earth is drawn at its own scale first.
		Vector3f back = new Vector3f(EAST).mul(0.35F).add(new Vector3f(TARGET).mul(1.0F)).normalize();
		float behind = Math.max(altitude * 0.15F, 0.00025F);
		Vector3f eye = new Vector3f(roundPos).add(new Vector3f(back).mul(behind));
		float shake = (float) smooth((s - 9) / 8.0) * 0.004F;
		Vector3f at = new Vector3f(roundPos).add(new Vector3f(down).mul(behind * 3.0F))
				.add(noise(s * 4.1) * shake * behind * 40, noise(s * 3.3 + 4) * shake * behind * 40, 0);
		earthCamera(new Pose(eye, at, new Vector3f(NORTH), 58.0F), Math.max(behind * 0.05F, 0.000005F), 80.0F);
		space.sky(cam, EARTH_SKY, 1.0F, 0, cam.forward(), 0, 0, 0, time);
		space.earth(cam, new Matrix4f(), EARTH_SUN, time * 0.00005F, smooth((s - 10) / 8.0), 1.05F);

		// The round at its own scale, framed the same way as the camera above.
		Space.clearDepth();
		float unit = behind / 3.2F;
		Matrix4f roundModel = new Matrix4f().translation(roundPos).rotateTowards(down, new Vector3f(NORTH)).scale(unit * ROUND_SCALE);
		float heat = smooth((s - 8) / 10.0);
		space.mesh(space.round, cam, roundModel, EARTH_SUN, 1.1F, ORANGE, 1.4F, heat);
		if (heat > 0.01F) {
			Matrix4f sheath = new Matrix4f().translation(roundPos).rotateTowards(down, new Vector3f(NORTH))
					.translate(0, 0, unit * 5.25F * ROUND_SCALE).scale(unit * 1.4F, unit * 1.4F, unit * 6.0F);
			space.plasma(space.cone, cam, sheath, time * 0.05F, heat * 1.6F, 0.4F + heat * 0.6F, new Vector3f(0, 0, -6.0F), 1.0F);
			Fx glow = space.glow(cam, Fx.BLOB, 1.0F);
			glow.sprite(new Vector3f(roundPos).add(new Vector3f(down).mul(unit * 5.5F * ROUND_SCALE)), unit * (1.5F + heat * 3.0F), 0,
					Fx.argb(1.0F, 0.8F, 0.55F, heat));
			glow.end(true);
		}
		o.header = "[ TERMINAL · SOL-3 ]";
		o.headerReveal = smooth(s / 4.0);
		long range = Math.max(0, (long) (altitude * 6371.0));
		o.footer = "RANGE " + Feed.commas(range) + " KM";
		o.footerSmall = "VELOCITY 0.9724 c";
		o.flashColor = s > 19 ? 0xFFFFFF : 0xFF9050;
		o.flash = s > 19 ? smooth((s - 19) / 5.0) : heat * 0.22F;
		o.aberration = heat * 0.01F;
		o.saturation = 1.0F + heat * 0.25F;
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
