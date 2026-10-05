package io.github.kortev.shootingstar.client.feed;

import io.github.kortev.shootingstar.client.gfx.Cam;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.Mesh;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.gap.GapTimeline;
import java.util.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Ginnungagap's feed. Out of the clouds over the target and up to Bifröst, the gate that hangs in orbit
 * over it; the gate wakes, opens onto another universe, cuts a block out of it and draws it through, then
 * reaches down to the target with a bridge of light and drops the block into it, and the camera chases it
 * down into the clouds.
 *
 * <p>Everything is in the gate's own frame, in gate units (the frame is 20 across; one unit is 620 m): the
 * gate at the origin, straight up from the target along +Y, east along +X, north along +Z, the ground
 * 1,600 km below.
 */
final class GapShots implements Feed.Sequence {
	/** Gate units in a kilometre. */
	private static final float KM = 1.0F / 0.62F;
	private static final float EARTH_R = 6371.0F * KM;
	private static final float HEIGHT_KM = 1600.0F;
	private static final float HEIGHT = HEIGHT_KM * KM;
	private static final Vector3f EARTH_CENTER = new Vector3f(0.0F, -(EARTH_R + HEIGHT), 0.0F);
	/** The target, on the ground straight under the gate. */
	private static final Vector3f GROUND = new Vector3f(0.0F, -HEIGHT, 0.0F);
	private static final Vector3f ORIGIN = new Vector3f();

	// Earth's map is laid out for the feed's Earth scene (radius 1): turn it so the target is under the gate.
	private static final Vector3f TARGET = Mesh.direction(39.5, -98.5);
	private static final Vector3f NORTH = new Vector3f(0, 1, 0).sub(new Vector3f(TARGET).mul(TARGET.y)).normalize();
	private static final Vector3f EAST = new Vector3f(TARGET).cross(NORTH).normalize();
	/** Earth-scene directions into the gate's frame: rows east, up and north. */
	private static final Matrix4f TO_LOCAL = new Matrix4f(EAST.x, TARGET.x, NORTH.x, 0, EAST.y, TARGET.y, NORTH.y, 0, EAST.z, TARGET.z,
			NORTH.z, 0, 0, 0, 0, 1);
	private static final Matrix4f EARTH = new Matrix4f().translation(EARTH_CENTER).mul(TO_LOCAL).scale(EARTH_R);
	/** Mid-morning over the target, the sun to the south-east, so the gate's southern faces are in sunlight. */
	private static final Vector3f SUN = new Vector3f(0.6F, 0.55F, -0.58F).normalize();
	/** The Milky Way's core to the north, beyond the gate, the band arching over it. */
	private static final Matrix4f SKY = Shots.skyFrame(new Vector3f(-0.25F, 0.2F, 1.0F).normalize(), new Vector3f(0.9F, 0.35F, 0.1F));

	/** How far the gate leans back: its face looks 50 degrees up out of the horizontal, towards the south. */
	private static final float TILT = (float) Math.toRadians(50.0);
	private static final Matrix4f GATE = new Matrix4f().rotateX(TILT);
	/** The way out of the gate's open face (its -Z side, turned with it): up and to the south. */
	private static final Vector3f FACE = GATE.transformDirection(new Vector3f(0, 0, -1)).normalize();
	private static final Vector3f GATE_UP = GATE.transformDirection(new Vector3f(0, 1, 0)).normalize();
	private static final Vector3f GATE_RIGHT = new Vector3f(1, 0, 0);
	/** Half the width of the window inside the frame. */
	private static final float WINDOW = 8.4F;
	/** Half the size of the block of the other universe. */
	private static final float BLOCK = 3.2F;
	/** Where the block waits behind the window, and where it hangs once it is through. */
	private static final float BEHIND = 15.0F;
	private static final float OUT = 17.0F;

	// Beats, in ticks from the start of the feed.
	private static final double GATE_S = GapTimeline.GATE - GapTimeline.FEED;
	private static final double OPEN_S = GapTimeline.OPEN - GapTimeline.FEED;
	private static final double CUT_S = GapTimeline.CUT - GapTimeline.FEED;
	private static final double SEND_S = GapTimeline.SEND - GapTimeline.FEED;
	private static final double FALL_S = GapTimeline.FALL - GapTimeline.FEED;
	private static final double END_S = GapTimeline.INBOUND - GapTimeline.FEED;
	/** When the emitters start to light, and when they all have. */
	private static final double SWEEP_FROM = GATE_S + 10.0;
	private static final double SWEEP_TO = GATE_S + 40.0;
	/** Where the climb out of the clouds ends: well south of the gate and below it, looking up at it. */
	private static final Vector3f CLIMB = new Vector3f(0.18F, 0.62F, -0.76F).normalize();
	private static final float CLIMB_FROM = 480.0F;
	private static final float CLIMB_TO = 3200.0F;
	/** The window opening and closing. */
	private static final double REVEAL_FROM = OPEN_S + 8.0;
	private static final double REVEAL_TO = OPEN_S + 24.0;
	private static final double PULL_FROM = CUT_S + 12.0;
	private static final double PULL_TO = CUT_S + 28.0;
	private static final double CLOSE_FROM = CUT_S + 31.0;
	private static final double CLOSE_TO = CUT_S + 39.0;
	/** The bridge reaching down, and the block dropping into it. */
	private static final double BRIDGE_S = SEND_S + 3.0;
	private static final double DROP_S = SEND_S + 8.0;
	/** When the block is in the cloud deck and the picture goes white. */
	private static final double DECK_S = END_S - 4.0;

	private static final int VIOLET = 0xB98CFF;

	private final Space space = new Space();
	private final Cam cam = new Cam();
	private final Random random = new Random();
	private float width;
	private float height;
	private float guiW;
	private float guiH;
	private float time;

	@Override
	public Overlay render(double t, float fbWidth, float fbHeight, float guiWidth, float guiHeight) {
		space.ensure();
		width = fbWidth;
		height = fbHeight;
		guiW = guiWidth;
		guiH = guiHeight;
		time = (float) t;
		Overlay o = new Overlay();
		o.accent = 0xFF000000 | VIOLET;
		o.headerColor = 0xFF000000 | VIOLET;
		o.bloom = 0.9F;
		o.streak = 0.5F;
		space.resetLights();
		double s = t - GapTimeline.FEED;
		if (s < GATE_S) {
			orbit(s, o);
		} else if (s < OPEN_S) {
			reveal(s, o);
		} else if (s < CUT_S) {
			open(s, o);
		} else if (s < SEND_S) {
			cut(s, o);
		} else if (s < FALL_S) {
			send(s, o);
		} else {
			fall(s, o);
		}
		return o;
	}

	// =============================================================================================
	// The gate and the block, as they are at any moment
	// =============================================================================================

	private static float sweep(double s) {
		return s < SWEEP_FROM ? -0.05F : s < SWEEP_TO ? Shots.smooth((s - SWEEP_FROM) / (SWEEP_TO - SWEEP_FROM)) : 1.2F;
	}

	private static float reveal(double s) {
		if (s < CLOSE_FROM) {
			return Shots.smoother((s - REVEAL_FROM) / (REVEAL_TO - REVEAL_FROM));
		}
		return 1.0F - Shots.smootherIn((s - CLOSE_FROM) / (CLOSE_TO - CLOSE_FROM));
	}

	/** How hard the emitters burn: waking, flaring as the window opens and as the bridge goes out. */
	private static float glow(double s) {
		float base = 2.2F * Shots.smooth((s - SWEEP_FROM) / 6.0);
		float open = 2.5F * (float) Math.exp(-Math.max(0.0, s - REVEAL_FROM) / 6.0) * (s >= REVEAL_FROM ? 1.0F : 0.0F);
		float pull = 1.4F * Shots.smooth((s - PULL_FROM) / 4.0) * (1.0F - Shots.smooth((s - CLOSE_TO) / 8.0));
		float send = 4.0F * (float) Math.exp(-Math.max(0.0, s - BRIDGE_S) / 5.0) * (s >= BRIDGE_S ? 1.0F : 0.0F);
		return base + open + pull + send;
	}

	/** Distance of the block from the window, along the way out of the gate's face (negative is still behind it). */
	private static float blockOut(double s) {
		if (s < PULL_FROM) {
			return -BEHIND;
		}
		if (s < PULL_TO) {
			return Shots.lerp(-BEHIND, OUT, Shots.smoother((s - PULL_FROM) / (PULL_TO - PULL_FROM)));
		}
		return OUT + 1.5F * Shots.smooth((s - PULL_TO) / 30.0);
	}

	/** Where the block is before it drops. */
	private static Vector3f blockHang(double s) {
		return new Vector3f(FACE).mul(blockOut(s));
	}

	/** How far the block is above the ground (gate units) once it has dropped into the bridge. */
	private static float altitude(double s) {
		double u = Math.max(0.0, (s - DROP_S) / (DECK_S - DROP_S));
		double fallen = 1.6 * u + 4.3 * Math.pow(Math.min(u, 1.2), 3.2);
		return (float) (start().distance(GROUND) * Math.exp(-fallen));
	}

	private static Vector3f start() {
		return blockHang(DROP_S);
	}

	private static Vector3f blockPos(double s) {
		if (s < DROP_S) {
			return blockHang(s);
		}
		// Down the bridge, a straight line from where it hung to the target.
		Vector3f from = start();
		float total = from.distance(GROUND);
		float k = 1.0F - altitude(s) / total;
		return new Vector3f(from).lerp(GROUND, k);
	}

	/** The block turns over slowly once it is through, and tumbles a little faster as it falls. */
	private static Matrix4f blockModel(double s) {
		double spin = Math.max(0.0, s - PULL_TO + 6.0);
		Matrix4f m = new Matrix4f().translation(blockPos(s)).mul(GATE);
		m.rotateY((float) (spin * 0.008 + Math.max(0.0, s - DROP_S) * 0.01)).rotateX((float) (spin * 0.005));
		return m.scale(BLOCK);
	}

	/** The scene: the sky, Earth, the gate with its window, the block and the bridge. */
	private void scene(double s, float near, float far, Vector3f eye, Vector3f at, Vector3f up, float fov, boolean drawBlock) {
		cam.perspective(fov, width, height, near, far);
		cam.look(eye, at, up);
		space.sky(cam, SKY, 1.0F, 0.0F, cam.forward(), 0.0F, 0.0F, 0.0F, time);
		float detail = Shots.smooth(1.0 - eye.distance(GROUND) / 400.0);
		space.earth(cam, EARTH, SUN, time * 0.00005F, detail, 1.05F);

		// The gate, lit from below by Earth.
		space.fillDir.set(0, 1, 0);
		space.fillColor.set(0.22F, 0.28F, 0.4F);
		Shaders.set(Shaders.mesh, "Sweep", sweep(s));
		Shaders.set(Shaders.mesh, "Phase", (float) (s * 0.45));
		space.mesh(space.gate, cam, GATE, SUN, 1.25F, VIOLET, glow(s), 0.0F);
		Shaders.set(Shaders.mesh, "Sweep", 2.0F);
		Shaders.set(Shaders.mesh, "Phase", 0.0F);
		space.fillColor.zero();

		float open = reveal(s);
		if (open > 0.0F) {
			Matrix4f window = new Matrix4f(GATE).scale(WINDOW);
			space.universe(space.quad, cam, window, 1, time, 0.0F, 0.0F, 0.0F, open, 40, 1.0F);
			windowLight(s, open);
		}
		if (drawBlock && s >= CUT_S) {
			selection(s);
			float heat = 0.8F * (float) Math.exp(-Math.max(0.0, s - PULL_TO) / 4.0) * (s >= PULL_FROM ? 1.0F : 0.0F);
			// While it is still behind the window it can only be seen through it.
			if (s >= PULL_FROM) {
				space.universe(space.cube, cam, blockModel(s), 0, time, 0.0F, 1.0F, heat, 1.0F, 48, 1.0F);
			}
		}
		if (s >= BRIDGE_S) {
			bridge(s);
		}
	}

	/** Light thrown out of the open window across the frame, and the white seam of its rim. */
	private void windowLight(double s, float open) {
		float flash = (float) Math.exp(-Math.max(0.0, s - REVEAL_FROM) / 4.0) * (s >= REVEAL_FROM ? 1.0F : 0.0F);
		float closing = s > CLOSE_FROM ? Shots.smooth((s - CLOSE_FROM) / (CLOSE_TO - CLOSE_FROM)) : 0.0F;
		Fx fx = space.glow(cam, Fx.BLOB, 1.0F);
		fx.sprite(new Vector3f(FACE).mul(0.5F), WINDOW * (0.6F + open * 0.9F), 0.0F, Fx.argb(0.6F, 0.45F, 1.0F, 0.18F + 0.5F * flash));
		if (closing > 0.0F && closing < 1.0F) {
			// Shutting like an old screen: a bright bar, then a point.
			float k = 1.0F - closing;
			Fx bar = space.glow(cam, Fx.BEAM, 0.0F);
			Vector3f a = new Vector3f(GATE_RIGHT).mul(-WINDOW * k);
			Vector3f b = new Vector3f(GATE_RIGHT).mul(WINDOW * k);
			bar.beam(a, b, cam.pos, 0.25F + 0.6F * k, Fx.argb(0.9F, 0.85F, 1.0F, 1.0F), Fx.argb(0.9F, 0.85F, 1.0F, 1.0F));
			bar.end(true, 3.0F);
			fx.sprite(new Vector3f(), 3.0F + 6.0F * closing, 0.0F, Fx.argb(1.0F, 0.95F, 1.0F, 1.0F - closing * 0.5F));
		}
		fx.end(true, 2.0F);
	}

	/**
	 * The block's outline while it is still in its own universe: a selection box, thin and white, blinking twice as
	 * it appears, then a square ripple across the window where the block breaks through.
	 */
	private void selection(double s) {
		double since = s - CUT_S;
		float out = blockOut(s);
		if (since < 0.0 || out > BLOCK + 0.5F) {
			return;
		}
		boolean blink = since < 2.0 || (since >= 4.0 && since < 6.0) || since >= 8.0;
		if (blink) {
			Matrix4f m = new Matrix4f().translation(blockPos(s)).mul(GATE).scale(BLOCK * 1.02F);
			Vector3f[] c = new Vector3f[8];
			for (int i = 0; i < 8; i++) {
				c[i] = m.transformPosition(new Vector3f((i & 1) == 0 ? -1 : 1, (i & 2) == 0 ? -1 : 1, (i & 4) == 0 ? -1 : 1));
			}
			int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
			Fx lines = space.glow(cam, Fx.LINE, 0.0F);
			float w = Math.max(0.05F, cam.pos.distance(c[0]) * 0.0016F);
			for (int[] e : edges) {
				lines.beam(c[e[0]], c[e[1]], cam.pos, w, Fx.argb(1.0F, 1.0F, 1.0F, 0.9F), Fx.argb(1.0F, 1.0F, 1.0F, 0.9F));
			}
			lines.end(true, 2.0F);
		}
		// Breaking through: a square of light running out across the window from the block's edges.
		float through = Shots.smooth((out + BLOCK) / (2.0F * BLOCK));
		if (through > 0.0F && through < 1.0F) {
			float half = BLOCK + (WINDOW - BLOCK) * through;
			float a = (1.0F - through) * 0.9F;
			Fx ring = space.glow(cam, Fx.LINE, 0.0F);
			Vector3f r = new Vector3f(GATE_RIGHT).mul(half);
			Vector3f u = new Vector3f(GATE_UP).mul(half);
			Vector3f[] q = {new Vector3f().sub(r).sub(u), new Vector3f(r).sub(u), new Vector3f(r).add(u), new Vector3f(u).sub(r)};
			for (int i = 0; i < 4; i++) {
				ring.beam(q[i], q[(i + 1) % 4], cam.pos, 0.35F, Fx.argb(0.85F, 0.75F, 1.0F, a), Fx.argb(0.85F, 0.75F, 1.0F, a));
			}
			ring.end(true, 3.0F);
		}
	}

	/** The bridge: a shaft of light from the block down to the target, with a rainbow at its edges. */
	private void bridge(double s) {
		double since = s - BRIDGE_S;
		Vector3f top = blockPos(Math.min(s, DROP_S));
		Vector3f from = s < DROP_S ? top : blockPos(s);
		float reach = Shots.smootherIn(since / 4.0);
		Vector3f bottom = new Vector3f(top).lerp(GROUND, reach);
		float fade = 1.0F - Shots.smooth((s - (END_S - 2.0)) / 2.0);
		float k = (float) Math.exp(-since / 6.0);
		float width = BLOCK * (0.55F + 0.9F * k);
		Fx beam = space.glow(cam, Fx.BEAM, 0.0F);
		beam.beam(from, bottom, cam.pos, width, Fx.argb(0.95F, 0.92F, 1.0F, fade), Fx.argb(0.8F, 0.7F, 1.0F, fade));
		beam.beam(from, bottom, cam.pos, width * 2.6F, Fx.argb(0.55F, 0.3F, 1.0F, 0.5F * fade), Fx.argb(0.4F, 0.2F, 0.9F, 0.4F * fade));
		beam.end(true, 2.6F);
		// The fringes: the colours split out either side of the shaft.
		Vector3f side = new Vector3f(GROUND).sub(from).cross(new Vector3f(cam.pos).sub(from)).normalize().mul(width * 1.25F);
		Fx fringe = space.glow(cam, Fx.BEAM, 0.0F);
		fringe.beam(new Vector3f(from).add(side), new Vector3f(bottom).add(side), cam.pos, width * 0.35F,
				Fx.argb(1.0F, 0.25F, 0.55F, 0.35F * fade), Fx.argb(1.0F, 0.3F, 0.4F, 0.25F * fade));
		fringe.beam(new Vector3f(from).sub(side), new Vector3f(bottom).sub(side), cam.pos, width * 0.35F,
				Fx.argb(0.2F, 0.9F, 1.0F, 0.35F * fade), Fx.argb(0.3F, 1.0F, 0.6F, 0.25F * fade));
		fringe.end(true, 2.0F);
		// Where it touches the ground, a bright point.
		if (reach >= 1.0F) {
			Fx foot = space.glow(cam, Fx.SPIKES, 0.0F);
			float d = cam.pos.distance(GROUND);
			foot.sprite(GROUND, d * 0.03F, 0.0F, Fx.argb(0.9F, 0.85F, 1.0F, fade));
			foot.end(true, 3.0F);
		}
	}

	// =============================================================================================
	// 1. Out of the clouds and up from the target, until the camera finds what is waiting overhead.
	// =============================================================================================

	private void orbit(double s, Overlay o) {
		float e = Shots.smoother(s / GATE_S);
		float distance = (float) (CLIMB_FROM * Math.pow(CLIMB_TO / CLIMB_FROM, e));
		Vector3f dir = new Vector3f(0.0F, 1.0F, 0.0F).lerp(CLIMB, e).normalize();
		Vector3f eye = new Vector3f(GROUND).add(new Vector3f(dir).mul(distance));
		// Looking back down at the target, then up to the gate.
		float find = Shots.smoother((s - 12.0) / (GATE_S - 12.0));
		Vector3f at = new Vector3f(GROUND).lerp(ORIGIN, find);
		Vector3f up = new Vector3f(0, 0, 1).lerp(new Vector3f(0, 1, 0), find * 0.8F).normalize();
		float fov = Shots.lerp(55.0, 34.0, find);
		scene(s, Math.max(0.5F, distance * 0.002F), distance + EARTH_R * 2.5F, eye, at, up, fov, false);

		o.flash = (float) Math.pow(Math.max(0.0, 1.0 - s / 8.0), 1.6);
		o.flashColor = 0xF2F5FA;
		Overlay.Label target = label(o, GROUND, 10, -5, "TARGET", Feed.VIOLET, "GENESIS LOCK", Feed.GREY,
				Shots.smooth((s - 3.0) / 3.0) * (1.0F - find));
		if (target != null) {
			target.marker = true;
		}
		if (find > 0.6F) {
			Overlay.Label gate = label(o, ORIGIN, 10, -6, "BIFRÖST", Feed.VIOLET, "1,600 KM", Feed.GREY, Shots.smooth((find - 0.6) / 0.3));
			if (gate != null) {
				gate.marker = true;
			}
		}
		o.header = "[ GENESIS LOCK · SOL-3 ]";
		o.headerReveal = Shots.smooth(s / 6.0);
		o.footer = "ALTITUDE " + Feed.commas(Math.round((eye.y - GROUND.y) / KM)) + " KM";
	}

	// =============================================================================================
	// 2. Bifröst: the camera closes on the gate as its emitters light, round the frame both ways.
	// =============================================================================================

	private void reveal(double s, Overlay o) {
		double r = s - GATE_S;
		float k = Shots.smoother(r / (OPEN_S - GATE_S));
		// From far below and to the south, round and in to three quarters on from the east, just above the frame.
		Vector3f from = new Vector3f(CLIMB).mul(CLIMB_TO).add(GROUND);
		Vector3f fromDir = new Vector3f(from).normalize();
		Vector3f toDir = new Vector3f(0.62F, 0.34F, -0.71F).normalize();
		float d0 = from.length();
		float d1 = 52.0F;
		Vector3f dir = Shots.slerp(fromDir, toDir, Shots.smooth(r / 30.0));
		float distance = (float) (d0 * Math.pow(d1 / d0, Math.min(1.0, Math.pow(r / 34.0, 0.7))));
		distance = Math.max(distance, d1 * (1.0F - 0.12F * k));
		Vector3f eye = new Vector3f(dir).mul(distance);
		// Drift across the frame as the emitters light, so the scale shows against the pylons.
		eye.add(new Vector3f(GATE_RIGHT).mul(-6.0F * k)).add(new Vector3f(0, 4.0F * k, 0));
		Vector3f at = new Vector3f(0.0F, -1.5F, 0.0F).lerp(new Vector3f(FACE).mul(2.0F), k);
		float fov = Shots.lerp(34.0, 46.0, Shots.smooth(r / 20.0));
		scene(s, 0.4F, distance + EARTH_R * 2.5F, eye, at, new Vector3f(0, 1, 0), fov, false);

		float lit = Math.max(0.0F, Math.min(1.0F, sweep(s)));
		o.header = lit >= 1.0F ? "[ BIFRÖST · ONLINE ]" : "[ BIFRÖST · WAKING ]";
		o.headerReveal = Shots.smooth(r / 5.0);
		o.title = "GINNUNGAGAP";
		o.subtitle = "Ω-00 · GENESIS KEY · BIFRÖST GATE · SPAN 12.4 KM · 1,600 KM OVER THE TARGET";
		o.titleAlpha = Shots.smooth((r - 14.0) / 5.0) * (1.0F - Shots.smooth((r - 38.0) / 5.0));
		if (s >= SWEEP_FROM) {
			o.footer = "EMITTERS " + Feed.commas(Math.round(lit * 4096)) + " / 4,096";
		}
		Vector3f corner = GATE.transformPosition(new Vector3f(9.3F, -9.3F, 0.0F));
		label(o, corner, 10, 6, "BIFRÖST", Feed.VIOLET, "SPAN 12.4 KM", Feed.GREY, Shots.smooth((r - 4.0) / 4.0)
				* (1.0F - Shots.smooth((r - 12.0) / 3.0)));
	}

	// =============================================================================================
	// 3. The gate opens onto another universe.
	// =============================================================================================

	private void open(double s, Overlay o) {
		double r = s - OPEN_S;
		float k = Shots.smoother(r / (CUT_S - OPEN_S));
		// Out in front of the open face, looking down through the gate at Earth: the window full of that universe,
		// framed by the blue of our planet.
		Vector3f sideways = new Vector3f(GATE_RIGHT).mul(Shots.lerp(16.0, 6.0, k));
		Vector3f eye = new Vector3f(FACE).mul(Shots.lerp(78.0, 52.0, k)).add(sideways).add(new Vector3f(GATE_UP).mul(5.0F));
		Vector3f at = new Vector3f(GATE_UP).mul(-1.0F);
		scene(s, 0.4F, 30000.0F, eye, at, new Vector3f(GATE_UP), 46.0F, true);

		float open = reveal(s);
		o.header = open < 0.05F ? "[ OPENING ]" : "[ UNIVERSE 4,096,113 ]";
		o.headerReveal = open < 0.05F ? Shots.smooth(r / 4.0) : Shots.smooth((s - REVEAL_FROM) / 5.0);
		Vector3f corner = GATE.transformPosition(new Vector3f(-WINDOW, WINDOW, 0.0F));
		label(o, corner, -96, -16, "UNIVERSE 4,096,113", Feed.VIOLET, "13.7 BILLION YEARS OLD", Feed.GREY,
				Shots.smooth((s - REVEAL_TO) / 4.0));
		o.flash = 0.55F * (float) Math.exp(-Math.max(0.0, s - REVEAL_FROM) / 2.5) * (s >= REVEAL_FROM ? 1.0F : 0.0F);
		o.flashColor = 0xE8DDFF;
		o.zoomBlur = 0.12F * (float) Math.exp(-Math.pow((s - REVEAL_FROM - 3.0) / 3.0, 2.0));
	}

	// =============================================================================================
	// 4. A block of it is selected, cut out and drawn through; the window shuts behind it.
	// =============================================================================================

	private void cut(double s, Overlay o) {
		double r = s - CUT_S;
		float pull = Shots.smoother((s - PULL_FROM) / (PULL_TO - PULL_FROM));
		// Close on the window, then back away as the block comes out at the camera, keeping it and the gate in frame.
		float distance = Shots.lerp(50.0, 40.0, Shots.smooth(r / 12.0)) + 22.0F * pull;
		Vector3f eye = new Vector3f(FACE).mul(distance).add(new Vector3f(GATE_RIGHT).mul(6.0F + 10.0F * pull))
				.add(new Vector3f(GATE_UP).mul(5.0F + 6.0F * pull));
		Vector3f at = new Vector3f(FACE).mul(blockOut(s) * 0.55F).add(new Vector3f(GATE_UP).mul(-1.0F));
		scene(s, 0.4F, 30000.0F, eye, at, new Vector3f(GATE_UP), Shots.lerp(46.0, 50.0, pull), true);

		o.header = "[ EXTRACTION ]";
		o.headerReveal = Shots.smooth(r / 4.0);
		Vector3f block = blockPos(s);
		Vector3f corner = new Matrix4f().translation(block).mul(GATE).scale(BLOCK).transformPosition(new Vector3f(1, 1, 0));
		boolean through = s >= PULL_TO;
		label(o, corner, 10, -8, through ? "1 BLOCK" : "SELECTED", Feed.VIOLET, through ? "UNIVERSE 4,096,113" : "1 BLOCK",
				Feed.GREY, Shots.smooth((r - 1.0) / 3.0));
		o.footer = through ? "EXTRACTED" : "EXTRACTING";
		o.footerSmall = "UNIVERSE 4,096,113 · 1 BLOCK";
		// The punch of it coming through, and the snap of the window shutting.
		float burst = (float) Math.exp(-Math.pow((s - (PULL_FROM + PULL_TO) * 0.5) / 2.5, 2.0));
		o.zoomBlur = 0.2F * burst;
		o.aberration = 0.006F * burst;
		o.flash = 0.35F * (float) Math.exp(-Math.max(0.0, s - CLOSE_TO) / 2.0) * (s >= CLOSE_TO ? 1.0F : 0.0F);
		o.flashColor = 0xFFFFFF;
	}

	// =============================================================================================
	// 5. The bridge reaches down to the target; the block drops into it and is gone down it.
	// =============================================================================================

	private void send(double s, Overlay o) {
		double r = s - SEND_S;
		Vector3f block = blockHang(DROP_S);
		// Off to the east of the block, level with it, looking down the bridge at Earth; the camera stays as the
		// block falls away from it.
		float k = Shots.smooth(r / (FALL_S - SEND_S));
		Vector3f eye = new Vector3f(block).add(new Vector3f(46.0F, 10.0F - 6.0F * k, -16.0F));
		Vector3f lookBlock = blockPos(s);
		Vector3f lookDown = new Vector3f(block).add(new Vector3f(GROUND).sub(block).mul(0.06F));
		Vector3f at = new Vector3f(lookBlock).lerp(lookDown, Shots.smooth((s - DROP_S) / 10.0));
		scene(s, 0.4F, 30000.0F, eye, at, new Vector3f(0, 1, 0), 44.0F, true);

		o.header = "[ BRIDGE · SOL-3 ]";
		o.headerReveal = Shots.smooth(r / 4.0);
		float range = blockPos(s).distance(GROUND) / KM;
		o.footer = "RANGE " + Feed.commas(Math.round(range)) + " KM";
		Overlay.Label target = label(o, GROUND, 10, -5, "TARGET", Feed.VIOLET, "39.50 N · 98.50 W", Feed.GREY,
				Shots.smooth((s - BRIDGE_S - 3.0) / 3.0));
		if (target != null) {
			target.marker = true;
		}
		float bridge = s >= BRIDGE_S ? (float) Math.exp(-(s - BRIDGE_S) / 3.0) : 0.0F;
		o.flash = 0.45F * bridge;
		o.flashColor = 0xD8C8FF;
		o.aberration = 0.008F * bridge;
		o.zoomBlur = 0.1F * bridge;
		o.shutter = s > DROP_S ? 0.6F : 0.0F;
	}

	// =============================================================================================
	// 6. Down the bridge after it, into the air, through the cloud deck.
	// =============================================================================================

	private void fall(double s, Overlay o) {
		double r = s - FALL_S;
		Vector3f block = blockPos(s);
		float altitude = block.y - GROUND.y;
		Vector3f down = new Vector3f(GROUND).sub(block).normalize();
		Vector3f back = new Vector3f(0.42F, 0.0F, -0.9F).normalize();
		// Above and behind it, looking down the bridge at the target, closing in as the air thickens.
		float near = Shots.smooth((r - 30.0) / 30.0);
		float distance = Shots.lerp(24.0, 13.0, near);
		Vector3f eye = new Vector3f(block).add(new Vector3f(back).mul(distance)).sub(new Vector3f(down).mul(distance * 0.75F));
		Vector3f at = new Vector3f(block).add(new Vector3f(down).mul(distance * 1.6F));
		float heat = Shots.smooth((150.0F - altitude) / 110.0F) * (1.0F - Shots.smooth((r - 64.0) / 4.0));
		float shake = heat * 0.012F + 0.004F;
		at.add(Shots.noise(s * 4.1) * shake * distance, Shots.noise(s * 3.3 + 4) * shake * distance, Shots.noise(s * 3.7 + 8) * shake * distance);
		Vector3f up = new Vector3f(back).negate();
		float fov = Shots.lerp(52.0, 62.0, near) + 6.0F * heat;
		scene(s, 0.3F, altitude + EARTH_R * 0.6F, eye, at, up, fov, false);

		// The block at its own scale, lit hot from below as the air piles up in front of it.
		space.universe(space.cube, cam, blockModel(s), 0, time, 0.0F, 1.0F + heat, heat * 0.35F, 1.0F, 48, 1.0F);
		if (heat > 0.01F) {
			sheath(block, down, heat);
		}
		clouds(s, block, down, back);

		o.header = "[ DESCENT · SOL-3 ]";
		o.headerReveal = Shots.smooth(r / 4.0);
		o.footer = "ALTITUDE " + Feed.commas(Math.max(0, Math.round(altitude / KM))) + " KM";
		o.footerSmall = heat > 0.2F ? "SHOCK LAYER " + Math.round(2000 + heat * 26000) + " K" : "UNIVERSE 4,096,113 · 1 BLOCK";
		o.shutter = 0.7F;
		o.aberration = heat * 0.01F;
		o.exposure = 1.0F + heat * 0.3F;
		o.saturation = 1.0F + heat * 0.2F;
		o.zoomBlur = 0.06F + 0.1F * heat;
		float deck = Shots.smooth((s - (DECK_S - 7.0)) / 7.0);
		o.flash = Math.max(deck, heat * 0.06F);
		o.flashColor = deck > 0.0F ? 0xFFFFFF : 0xC090FF;
	}

	/** The violet sheath of shocked air in front of the block, and the ionised streaks it sheds. */
	private void sheath(Vector3f block, Vector3f down, float heat) {
		Matrix4f bow = new Matrix4f().translation(new Vector3f(block).add(new Vector3f(down).mul(BLOCK * 1.25F)))
				.rotateTowards(new Vector3f(down).negate(), new Vector3f(0, 0, 1)).scale(BLOCK * 2.6F, BLOCK * 2.6F, BLOCK * 6.5F);
		space.plasma(space.cone, cam, bow, time * 0.05F, heat * 1.1F, 0.35F + heat * 0.65F, new Vector3f(0, 0, -6.0F), 1.0F, 1.0F);
		Fx cap = space.glow(cam, Fx.BLOB, 1.0F);
		cap.sprite(new Vector3f(block).add(new Vector3f(down).mul(BLOCK * 1.6F)), BLOCK * (1.2F + heat * 2.5F), 0.0F,
				Fx.argb(0.85F, 0.75F, 1.0F, heat));
		cap.end(true, 2.0F);
		Vector3f side = new Vector3f(down).cross(0, 0, 1).normalize();
		Vector3f side2 = new Vector3f(side).cross(down).normalize();
		Fx streaks = space.glow(cam, Fx.STREAK, 0.0F);
		for (int i = 0; i < 90; i++) {
			double f = (time * (0.08 + 0.05 * heat) + (i * 0.618034) % 1.0) % 1.0;
			double angle = i * 2.399963;
			float rr = BLOCK * (1.4F + (i % 9) * 0.3F) * (1.0F + (float) f * 0.9F);
			Vector3f p = new Vector3f(block).add(new Vector3f(down).mul(BLOCK * (2.0F - 12.0F * (float) f)))
					.add(new Vector3f(side).mul((float) Math.cos(angle) * rr)).add(new Vector3f(side2).mul((float) Math.sin(angle) * rr));
			float fade = (float) Math.sin(Math.PI * f);
			streaks.stretched(p, down, BLOCK * (0.6F + 2.4F * heat), BLOCK * 0.03F, Fx.argb(0.8F, 0.65F, 1.0F, heat * fade * 0.8F));
		}
		streaks.end(true, 3.0F);
	}

	/** The cloud deck rushing up until it is all there is. */
	private void clouds(double s, Vector3f block, Vector3f down, Vector3f back) {
		float deck = Shots.smooth((s - (DECK_S - 16.0)) / 10.0);
		if (deck <= 0.0F) {
			return;
		}
		Vector3f side = new Vector3f(back);
		Vector3f side2 = new Vector3f(side).cross(down).normalize();
		Fx puffs = space.glow(cam, Fx.BLOB, 1.0F);
		random.setSeed(4096113L);
		for (int i = 0; i < 46; i++) {
			float ahead = (float) (((random.nextDouble() * 40.0 - (s - DECK_S + 16.0) * 6.0) % 40.0 + 40.0) % 40.0) - 4.0F;
			double a = random.nextDouble() * Math.PI * 2;
			float rr = 8.0F + random.nextFloat() * 40.0F;
			Vector3f p = new Vector3f(block).add(new Vector3f(down).mul(ahead * 2.0F)).add(new Vector3f(side).mul((float) Math.cos(a) * rr))
					.add(new Vector3f(side2).mul((float) Math.sin(a) * rr));
			puffs.sprite(p, 10.0F + random.nextFloat() * 18.0F, 0.0F, Fx.argb(0.92F, 0.93F, 0.97F, 0.32F * deck));
		}
		puffs.end(true, 1.4F);
	}

	// =============================================================================================
	// Helpers
	// =============================================================================================

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
}
