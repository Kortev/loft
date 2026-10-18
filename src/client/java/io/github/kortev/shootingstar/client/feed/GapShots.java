package io.github.kortev.shootingstar.client.feed;

import io.github.kortev.shootingstar.client.gfx.Cam;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.Mesh;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import io.github.kortev.shootingstar.client.gfx.Universe;
import io.github.kortev.shootingstar.gap.GapTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Ginnungagap's feed. Out of the clouds over the target and up to Bifröst, the gate that hangs in orbit
 * over it; the gate wakes and opens onto the void between universes, where they hang in a lattice, each in a
 * block, and the camera dives through into universe 4,096,113, beside one of its galaxies. Then it pulls back
 * out, past the web of its two trillion galaxies, until the whole of that universe is a block among the others,
 * and it is selected. The gate draws that block through, then reaches down to the target with a bridge of light
 * and drops the block into it, and the camera chases it down into the clouds.
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
	private static final double MAP_S = GapTimeline.MAP - GapTimeline.FEED;
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
	// The block keeps moving from the multiverse shot: pulled from the cut on, through the window a second in.
	private static final double PULL_FROM = CUT_S;
	private static final double PULL_TO = SEND_S;
	private static final double CLOSE_FROM = CUT_S + 31.0;
	private static final double CLOSE_TO = CUT_S + 39.0;
	/** The bridge reaching down, and the block dropping into it. */
	private static final double BRIDGE_S = SEND_S + 3.0;
	private static final double DROP_S = SEND_S + 8.0;
	/** When the block is in the cloud deck and the picture goes white. */
	private static final double DECK_S = END_S - 4.0;
	/** The dive through the open window into that universe. */
	private static final double DIVE_FROM = MAP_S - 20.0;

	// The void between universes, in a frame of its own: universes in blocks, each 2 across, MULTI_SPACING apart
	// in a lattice, universe 4,096,113 in the middle of it. Its own space is that of its universe (tools/gen_universe.py).
	private static final float MULTI_SPACING = 3.4F;
	/**
	 * Ticks into the map shot: the pull back from beside a galaxy; the hold on the lattice of universes, pulled all the
	 * way back (GapTimeline.MAP_HOLD); and the block being selected and rising out of the lattice.
	 */
	private static final double MAP_PULL_FROM = 16.0;
	private static final double MAP_PULL_TO = 46.0;
	private static final double MAP_PICK = GapTimeline.PICK - GapTimeline.MAP;
	private static final double MAP_LIFT = MAP_PICK + 4.0;
	/** How high the selected block has risen out of the lattice by the end of the shot. */
	private static final float MAP_LIFT_HEIGHT = 1.7F;
	/** When the camera starts round to the front of the selected block, and how far off it ends: the cut's first eye. */
	private static final double MAP_SWING = MAP_PICK + 30.0;
	/** When the block settles to how the window will show it. */
	private static final double MAP_SETTLE = MAP_PICK + 36.0;
	/** The selected universe crushed down into its block, from and to (ticks into the shot), the camera holding close. */
	private static final double MAP_CRUSH = MAP_PICK + 8.0;
	private static final double MAP_CRUSHED = MAP_PICK + 28.0;
	/** How far round (radians, about the lattice's pole) and how much further out the camera drifts while it holds. */
	private static final double MAP_HOLD_TURN = Math.toRadians(9.0);
	private static final double MAP_HOLD_OUT = 0.08;
	private static final float CUT_EYE = (BEHIND + 4.0F) / BLOCK;
	/** The big spiral the shot starts beside (the first galaxy of universe.bin): where it is and which way it faces. */
	private static final Vector3f MAP_GALAXY = new Vector3f(0.12F, 0.05F, -0.12F);
	private static final Vector3f MAP_POLE = new Vector3f(0.45F, 1.0F, 0.55F).normalize();
	/** Where the camera starts from the galaxy, over its face; and the way it looks at the lattice in the end. */
	private static final Vector3f MAP_START = new Vector3f(MAP_POLE).mul(0.9F)
			.add(new Vector3f(MAP_POLE).cross(0, 0, 1).normalize().mul(0.45F)).normalize();
	private static final Vector3f MAP_END = new Vector3f(0.30F, 0.62F, 0.72F).normalize();
	/**
	 * How far the camera is from what it looks at: coming in over the galaxy, beside it, with the void and its
	 * universes all round, and closing on the block.
	 */
	private static final float MAP_IN = 0.036F;
	private static final float MAP_NEAR = 0.028F;
	private static final float MAP_FAR = 24.0F;
	private static final float MAP_CLOSE = 8.0F;
	/** How wide the universe is: its two trillion galaxies in view at three of its widths off. */
	private static final double GALAXIES = 2.0E12;
	/** The other universes' light, so no two neighbours look alike. */
	private static final Vector3f[] TINTS = {new Vector3f(1.0F, 0.75F, 1.0F), new Vector3f(0.55F, 0.8F, 1.0F), new Vector3f(1.0F, 0.78F, 0.5F),
			new Vector3f(0.5F, 1.0F, 0.85F), new Vector3f(1.0F, 0.5F, 0.62F), new Vector3f(0.8F, 0.65F, 1.0F)};
	private static final Vector3f WHITE = new Vector3f(1.0F, 1.0F, 1.0F);
	/** What lies beyond the window, drawn from the same eye. */
	private static final Target BEYOND = new Target(false, true);

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
		// No shot cuts hard into the next one in the same space: for a while after each change the camera is a blend of
		// where the old shot would have carried on to and where the new one wants it, so it glides from one to the other.
		handoff = null;
		for (double[] h : HANDOFFS) {
			if (s >= h[0] && s < h[0] + h[1]) {
				handoff = pose(s, (int) h[2]);
				handoffWeight = 1.0F - Shots.smoother((s - h[0]) / h[1]);
			}
		}
		shot(shotAt(s), s, o);
		return o;
	}

	/** Each change of shot that is blended: when, over how long, and the shot it blends out of. */
	private static final double[][] HANDOFFS = {{GATE_S, 16.0, 0}, {OPEN_S, 18.0, 1}, {SEND_S, 16.0, 4}, {FALL_S, 22.0, 5}};

	private static int shotAt(double s) {
		return s < GATE_S ? 0 : s < OPEN_S ? 1 : s < MAP_S ? 2 : s < CUT_S ? 3 : s < SEND_S ? 4 : s < FALL_S ? 5 : 6;
	}

	private void shot(int shot, double s, Overlay o) {
		switch (shot) {
			case 0 -> orbit(s, o);
			case 1 -> reveal(s, o);
			case 2 -> open(s, o);
			case 3 -> map(s, o);
			case 4 -> cut(s, o);
			case 5 -> send(s, o);
			default -> fall(s, o);
		}
	}

	/** Where a shot would put the camera at time s, found by running it up to its scene() call and no further. */
	private float[] pose(double s, int shot) {
		posing = true;
		try {
			shot(shot, s, new Overlay());
		} catch (Posed posed) {
			return posed.pose;
		} finally {
			posing = false;
		}
		return null;
	}

	/** Thrown by scene() while posing, carrying eye, at, up, fov, near and far, before anything is drawn. */
	private static final class Posed extends RuntimeException {
		final float[] pose;

		Posed(float[] pose) {
			super(null, null, false, false);
			this.pose = pose;
		}
	}

	private boolean posing;
	@Nullable
	private float[] handoff;
	private float handoffWeight;

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
		// Turned as far as the multiverse shot left it.
		float turned = s >= CUT_S ? 0.6F : 0.0F;
		m.rotateY((float) (turned + spin * 0.008 + Math.max(0.0, s - DROP_S) * 0.01)).rotateX((float) (spin * 0.005));
		// Crushed down as the multiverse shot left it, springing back to its size as it comes through.
		float size = s < CUT_S ? 1.0F : Shots.lerp(0.45, 1.0, Shots.smooth((s - CUT_S) / 14.0));
		return m.scale(BLOCK * size);
	}

	/** The scene: the sky, Earth, the gate with its window, the block and the bridge. */
	private void scene(double s, float near, float far, Vector3f eye, Vector3f at, Vector3f up, float fov, boolean drawBlock) {
		if (posing) {
			throw new Posed(new float[] {eye.x, eye.y, eye.z, at.x, at.y, at.z, up.x, up.y, up.z, fov, near, far});
		}
		if (handoff != null) {
			float[] h = handoff;
			float w = handoffWeight;
			// The eye eases across; the way it looks swings round on the blend of where both look, so the frame never jumps.
			eye = new Vector3f(eye).lerp(new Vector3f(h[0], h[1], h[2]), w);
			at = new Vector3f(at).lerp(new Vector3f(h[3], h[4], h[5]), w);
			up = new Vector3f(up).lerp(new Vector3f(h[6], h[7], h[8]), w).normalize();
			fov = Shots.lerp(fov, h[9], w);
			near = Math.min(near, h[10]);
			far = Math.max(far, h[11]);
		}
		cam.perspective(fov, width, height, near, far);
		cam.look(eye, at, up);
		space.sky(cam, SKY, 0.6F, 0.0F, cam.forward(), 0.0F, 0.0F, 0.0F, time);
		float detail = Shots.smooth(1.0 - eye.distance(GROUND) / 400.0);
		space.earth(cam, EARTH, SUN, time * 0.00005F, detail, 1.05F);

		// The gate, lit from below by Earth.
		space.fillDir.set(0, 1, 0);
		space.fillColor.set(0.22F, 0.28F, 0.4F);
		Shaders.set(Shaders.mesh, "Sweep", sweep(s));
		Shaders.set(Shaders.mesh, "Phase", (float) (s * 0.45));
		// The crew's windows and the beacons burn from the start; the emitters stay dark until the sweep reaches them.
		space.mesh(space.gate, cam, GATE, SUN, 1.25F, VIOLET, Math.max(0.8F, glow(s)), 0.0F);
		Shaders.set(Shaders.mesh, "Sweep", 2.0F);
		Shaders.set(Shaders.mesh, "Phase", 0.0F);
		space.fillColor.zero();

		float open = reveal(s);
		if (open > 0.0F) {
			// What lies beyond the window is drawn from this same eye into a picture of its own, which the window shows.
			Matrix4f window = new Matrix4f(GATE).scale(WINDOW);
			BEYOND.begin((int) width, (int) height, 0.0F, 0.0F, 0.0F, 1.0F);
			beyond(s, window);
			Feed.bindScene();
			Universe.window(cam.modelView(window), cam.proj, width, height, BEYOND.color(), open, time);
			windowLight(s, open);
		}
		if (drawBlock && s >= CUT_S) {
			selection(s);
			// Until it is pulled it is only beyond the window; from then on it is here, coming through it.
			if (s >= PULL_FROM) {
				// A flash as it breaks through the window, not all the way.
				float out = blockOut(s) / BLOCK;
				float compacted = 0.45F * (1.0F - Shots.smooth((s - CUT_S) / 14.0));
				block(blockModel(s), Math.max(compacted, 0.55F * (float) Math.exp(-out * out * 1.5F)));
			}
		}
		if (s >= BRIDGE_S) {
			bridge(s, s < FALL_S);
		}
	}

	/** Universe 4,096,113 in its block. */
	private void block(Matrix4f model, float heat) {
		Universe.draw(cam.modelView(model), cam.proj, width, height, Universe.FULL, 0, 1.0F, 1.0F, WHITE, 0.45F, 1.0F, 0xE6DCFF, heat);
	}

	/**
	 * Beyond the window: the void, the lattice of universes going back from it, and, until it is pulled, the one
	 * Ginnungagap takes, waiting just behind it.
	 */
	private void beyond(double s, Matrix4f window) {
		// Only what shows through the window is worth drawing: the window's corners on screen.
		float[] rect = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (int i = 0; i < 4; i++) {
			Vector3f p = cam.screen(window.transformPosition(new Vector3f((i & 1) == 0 ? -1 : 1, (i & 2) == 0 ? -1 : 1, 0)), width, height);
			if (p == null) {
				rect = new float[] {-width, -height, 2.0F * width, 2.0F * height};
				break;
			}
			rect[0] = Math.min(rect[0], p.x);
			rect[1] = Math.min(rect[1], p.y);
			rect[2] = Math.max(rect[2], p.x);
			rect[3] = Math.max(rect[3], p.y);
		}
		// Once the multiverse shot has lifted it out of its row, the lattice is lower about it by as much.
		float lifted = s >= MAP_S ? MAP_LIFT_HEIGHT * BLOCK : 0.0F;
		Matrix4f frame = new Matrix4f().translation(new Vector3f(FACE).mul(-BEHIND).sub(new Vector3f(GATE_UP).mul(lifted))).mul(GATE).scale(BLOCK);
		multiverse(frame, -3, 3, -2, 2, 0, 4, 1.0F, 90.0F, rect, s < PULL_FROM ? () -> block(blockModel(s), 0.0F) : null);
	}

	/**
	 * Universes in their blocks, a lattice of them through the void: {@code frame} takes the lattice's space (each block 2
	 * across, MULTI_SPACING apart, the one Ginnungagap takes at the origin) to the shot's. Those whose middle is off
	 * {@code rect} on screen (x0, y0, x1, y1 in pixels) by more than their size are left out; the rest are drawn from the
	 * far side in, so the nearer glass darkens what is behind it, at {@code fade} of their light, with every galaxy
	 * sampled closer than {@code near} and fewer beyond. {@code middle} draws the one at the origin, never left out, when
	 * its turn comes. Returns how many were drawn.
	 */
	private int multiverse(Matrix4f frame, int i0, int i1, int j0, int j1, int k0, int k1, float fade, float near, float[] rect,
			@Nullable Runnable middle) {
		float scale = frame.getScale(new Vector3f()).x;
		List<float[]> blocks = new ArrayList<>();
		for (int i = i0; i <= i1; i++) {
			for (int j = j0; j <= j1; j++) {
				for (int k = k0; k <= k1; k++) {
					boolean origin = i == 0 && j == 0 && k == 0;
					Vector3f p = frame.transformPosition(new Vector3f(i, j, k).mul(MULTI_SPACING));
					if (origin) {
						// Always there, even with the camera inside it.
						if (middle != null) {
							blocks.add(new float[] {cam.pos.distance(p), i, j, k});
						}
						continue;
					}
					if (fade <= 0.01F) {
						continue;
					}
					Vector3f on = cam.screen(p, width, height);
					if (on == null) {
						continue;
					}
					float size = scale * 1.8F * cam.proj.m11() * height * 0.5F / Math.max(on.z, 1.0E-4F);
					if (on.x < rect[0] - size || on.y < rect[1] - size || on.x > rect[2] + size || on.y > rect[3] + size) {
						continue;
					}
					blocks.add(new float[] {cam.pos.distance(p), i, j, k});
				}
			}
		}
		blocks.sort((a, b) -> Float.compare(b[0], a[0]));
		for (float[] b : blocks) {
			int i = (int) b[1];
			int j = (int) b[2];
			int k = (int) b[3];
			if (i == 0 && j == 0 && k == 0) {
				middle.run();
				continue;
			}
			int hash = Math.floorMod(i * 7349 + j * 3407 + k * 1361 + 99991, 65536);
			Vector3f tint = TINTS[hash % TINTS.length];
			Matrix4f model = new Matrix4f(frame).translate(new Vector3f(i, j, k).mul(MULTI_SPACING));
			int edge = (int) (Math.min(1.0F, 0.5F * tint.x + 0.2F) * 255) << 16 | (int) (Math.min(1.0F, 0.5F * tint.y + 0.2F) * 255) << 8
					| (int) (Math.min(1.0F, 0.5F * tint.z + 0.2F) * 255);
			Universe.draw(cam.modelView(model), cam.proj, width, height, b[0] < near ? Universe.LOW : Universe.TINY, 1 + hash % 47, 1.6F * fade,
					1.0F, tint, 0.35F * fade, 0.22F * fade, edge, 0.0F);
		}
		return blocks.size();
	}

	/** Light thrown out of the open window across the frame, and the white seam of its rim. */
	private void windowLight(double s, float open) {
		float flash = (float) Math.exp(-Math.max(0.0, s - REVEAL_FROM) / 4.0) * (s >= REVEAL_FROM ? 1.0F : 0.0F);
		float closing = s > CLOSE_FROM ? Shots.smooth((s - CLOSE_FROM) / (CLOSE_TO - CLOSE_FROM)) : 0.0F;
		boolean shutting = closing > 0.0F && closing < 1.0F;
		Fx fx = space.glow(cam, Fx.BLOB, 1.0F);
		fx.sprite(new Vector3f(FACE).mul(0.5F), WINDOW * (0.6F + open * 0.9F), 0.0F, Fx.argb(0.6F, 0.45F, 1.0F, 0.18F + 0.5F * flash));
		if (shutting) {
			fx.sprite(new Vector3f(), 3.0F + 6.0F * closing, 0.0F, Fx.argb(1.0F, 0.95F, 1.0F, 1.0F - closing * 0.5F));
		}
		fx.end(true, 2.0F);
		if (shutting) {
			// Shutting like an old screen: a bright bar, then a point.
			float k = 1.0F - closing;
			Fx bar = space.glow(cam, Fx.BEAM, 0.0F);
			Vector3f a = new Vector3f(GATE_RIGHT).mul(-WINDOW * k);
			Vector3f b = new Vector3f(GATE_RIGHT).mul(WINDOW * k);
			bar.beam(a, b, cam.pos, 0.25F + 0.6F * k, Fx.argb(0.9F, 0.85F, 1.0F, 1.0F), Fx.argb(0.9F, 0.85F, 1.0F, 1.0F));
			bar.end(true, 3.0F);
		}
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

	/**
	 * The bridge: a shaft of light from the block (or, while the whole of it is in view, from where the block set off)
	 * down to the target, with a rainbow at its edges.
	 */
	private void bridge(double s, boolean whole) {
		double since = s - BRIDGE_S;
		Vector3f top = blockPos(Math.min(s, DROP_S));
		Vector3f from = whole || s < DROP_S ? top : blockPos(s);
		float reach = Shots.smootherIn(since / 4.0);
		Vector3f bottom = new Vector3f(top).lerp(GROUND, reach);
		float fade = 1.0F - Shots.smooth((s - (END_S - 2.0)) / 2.0);
		float k = (float) Math.exp(-since / 6.0);
		// Never thinner than a couple of pixels, however far off the camera is.
		float width = Math.max(BLOCK * (0.55F + 0.9F * k), cam.pos.distance(from) * 0.0011F);
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
		// While the title is up, look above the gate so it sits under the words.
		float titled = Shots.smooth((r - 8.0) / 8.0) * (1.0F - Shots.smooth((r - 38.0) / 6.0));
		at.add(new Vector3f(0, 1, 0).mul(distance * 0.16F * titled));
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
	// 3. The gate opens onto another universe, and the camera dives through the window into it.
	// =============================================================================================

	private void open(double s, Overlay o) {
		double r = s - OPEN_S;
		float k = Shots.smoother(Math.min(1.0, r / (DIVE_FROM - OPEN_S)));
		// Out in front of the open face, looking down through the gate at Earth: the window full of that universe,
		// framed by the blue of our planet.
		Vector3f sideways = new Vector3f(GATE_RIGHT).mul(Shots.lerp(16.0, 6.0, k));
		Vector3f eye = new Vector3f(FACE).mul(Shots.lerp(78.0, 52.0, k)).add(sideways).add(new Vector3f(GATE_UP).mul(5.0F));
		Vector3f at = new Vector3f(GATE_UP).mul(-1.0F);
		float dive = s < DIVE_FROM ? 0.0F : (float) ((s - DIVE_FROM) / (MAP_S - DIVE_FROM));
		if (dive <= 0.0F) {
			scene(s, 0.4F, 30000.0F, eye, at, new Vector3f(GATE_UP), 46.0F, true);
		} else {
			dive(s, dive, eye, at);
		}

		float open = reveal(s);
		o.header = open < 0.05F ? "[ OPENING ]" : "[ UNIVERSE 4,096,113 ]";
		o.headerReveal = open < 0.05F ? Shots.smooth(r / 4.0) : Shots.smooth((s - REVEAL_FROM) / 5.0);
		Vector3f edge = GATE.transformPosition(new Vector3f(WINDOW * 0.75F, WINDOW * 0.4F, 0.0F));
		if (dive <= 0.0F) {
			label(o, edge, 70, -26, "UNIVERSE 4,096,113", Feed.VIOLET, "13.7 BILLION YEARS OLD", Feed.GREY, Shots.smooth((s - REVEAL_TO) / 4.0));
		}
		o.flash = 0.55F * (float) Math.exp(-Math.max(0.0, s - REVEAL_FROM) / 2.5) * (s >= REVEAL_FROM ? 1.0F : 0.0F);
		o.flashColor = 0xE8DDFF;
		o.zoomBlur = 0.12F * (float) Math.exp(-Math.pow((s - REVEAL_FROM - 3.0) / 3.0, 2.0)) + 0.25F * (float) Math.sin(Math.PI * dive);
		o.aberration = 0.006F * (float) Math.sin(Math.PI * dive);
	}

	/** The lattice of universes as the window shows it: the one Ginnungagap takes at its origin, waiting behind the window. */
	private static final Matrix4f LATTICE = new Matrix4f().translation(new Vector3f(FACE).mul(-BEHIND)).mul(GATE).scale(BLOCK);
	/** Where the window is in the lattice's own space: the camera has gone through it once it is past this along z. */
	private static final float WINDOW_Z = -BEHIND / BLOCK;

	/**
	 * Through the window and on into that universe, all in one move, a thousand times closer and closer: from where the
	 * open shot is, straight in through the window at the block behind it, through its glass and in among its galaxies,
	 * turning as it goes, to come to rest exactly where the next shot begins, beside the one galaxy.
	 */
	private void dive(double s, float u, Vector3f eye0, Vector3f at0) {
		Matrix4f toLattice = new Matrix4f(LATTICE).invert();
		Vector3f from = toLattice.transformPosition(new Vector3f(eye0));
		Vector3f look = toLattice.transformPosition(new Vector3f(at0));
		float w = Shots.smoother(u);
		float d0 = from.distance(MAP_GALAXY);
		float distance = (float) (d0 * Math.pow(MAP_IN / d0, w));
		// Straight in at first, so it goes through the window and not the frame; round to the galaxy's side once inside.
		Vector3f dir = Shots.slerp(new Vector3f(from).sub(MAP_GALAXY).normalize(), MAP_START, Shots.smoother((w - 0.55) / 0.45));
		Vector3f eye = new Vector3f(dir).mul(distance).add(MAP_GALAXY);
		Vector3f at = new Vector3f(look).lerp(MAP_GALAXY, Shots.smoother(Math.min(1.0, u * 2.2)));
		float fov = Shots.lerp(46.0, 55.0, w);
		if (eye.z < WINDOW_Z) {
			// Still this side of the window: the gate and all, the lattice through it.
			scene(s, 0.4F, 30000.0F, LATTICE.transformPosition(new Vector3f(eye)), LATTICE.transformPosition(new Vector3f(at)),
					new Vector3f(GATE_UP), fov, true);
			return;
		}
		// Through it: only the void and the universes in it, the camera inside their lattice from here on.
		cam.perspective(fov, width, height, Math.max(1.0E-4F, distance * 0.01F), 200.0F);
		cam.look(eye, at, new Vector3f(0, 1, 0));
		float outside = Shots.smooth((Math.max(Math.abs(eye.x), Math.max(Math.abs(eye.y), Math.abs(eye.z))) - 1.0) / 0.6);
		float others = Shots.smooth((distance - 2.6) / 4.0);
		Matrix4f origin = new Matrix4f();
		multiverse(new Matrix4f(), -3, 3, -2, 2, 0, 4, others, 20.0F, new float[] {0.0F, 0.0F, width, height},
				() -> Universe.draw(cam.modelView(origin), cam.proj, width, height, Universe.FULL, 0, 1.0F, 1.0F, WHITE, 0.45F * outside,
						Shots.lerp(0.3F, 1.0F, outside) * outside, 0xE6DCFF, 0.0F));
	}

	// =============================================================================================
	// 4. Beside one galaxy of universe 4,096,113; then back out, faster and faster, past the web of all the rest, until
	//    the whole of that universe is a block in the void among the others, and it is selected and lifted out.
	// =============================================================================================

	private void map(double s, Overlay o) {
		double m = s - MAP_S;
		double length = CUT_S - MAP_S;
		float pull = Shots.smoother((m - MAP_PULL_FROM) / (MAP_PULL_TO - MAP_PULL_FROM));
		float pick = Shots.smooth((m - MAP_PICK) / 6.0);
		// In over the galaxy's face; out at an ever faster rate, a thousand times as far; then a slow push back in.
		float near = Shots.lerp(MAP_IN, MAP_NEAR, Shots.smooth(m / MAP_PULL_FROM));
		float distance = (float) (near * Math.pow(MAP_FAR / near, pull));
		// In close on it once it is picked, and held there while it is crushed.
		distance = Shots.lerp(distance, MAP_CLOSE, Shots.smooth((m - MAP_PICK) / (MAP_CRUSH - MAP_PICK)));
		// Drifting round the galaxy at first, then round to the angle the lattice is seen from.
		double drift = m / length * 0.7;
		Vector3f round = new Vector3f(MAP_START).mul((float) Math.cos(drift))
				.add(new Vector3f(MAP_POLE).cross(MAP_START).mul((float) Math.sin(drift))).normalize();
		Vector3f dir = Shots.slerp(round, MAP_END, Shots.smooth((pull - 0.05) * 1.25));
		// Held there on the void between universes, all of them in the picture, before the one is picked: never stopped
		// dead, but still turning slowly the way it was going and easing a little further out, until it goes in.
		float holding = Shots.smooth((m - 30.0) / (MAP_PICK + 10.0 - 30.0));
		dir.rotateAxis((float) (MAP_HOLD_TURN * holding), MAP_POLE.x, MAP_POLE.y, MAP_POLE.z);
		distance *= (float) (1.0 + MAP_HOLD_OUT * Shots.smooth((m - 36.0) / (MAP_PICK - 36.0)) * (1.0 - Shots.smooth((m - MAP_PICK) / 6.0)));
		// The selected block rising up out of the lattice, faster and faster, the camera lifting a little with it.
		float lift = MAP_LIFT_HEIGHT * (float) Math.pow(Shots.smooth((m - MAP_LIFT) / (length - MAP_LIFT)), 1.6);
		Vector3f at = new Vector3f(MAP_GALAXY).lerp(new Vector3f(), Shots.smooth(pull * 1.6)).add(0.0F, lift * 0.5F, 0.0F);
		Vector3f eye = new Vector3f(dir).mul(distance).add(at);
		// Then it is crushed down, 2 trillion galaxies packed into less and less, while the camera swings up over it and
		// round in front of it, the near universes falling away from between, and settles where the next shot begins:
		// just outside the gate's window, looking through it at the block waiting behind. That shot is seen through the
		// window, so the two meet exactly, and the pull out through the window is one move.
		float crushing = (float) MathHelper.clamp((m - MAP_CRUSH) / (MAP_CRUSHED - MAP_CRUSH), 0.0, 1.0);
		float compact = Shots.smooth(crushing);
		Vector3f home = new Vector3f(0.0F, lift, 0.0F);
		float swing = Shots.smoother((m - MAP_SWING) / (length - MAP_SWING));
		float settle = Shots.smooth((m - MAP_SETTLE) / (length - MAP_SETTLE));
		Vector3f from = new Vector3f(eye).sub(home);
		Vector3f swung = Shots.slerp(new Vector3f(from).normalize(), new Vector3f(0.0F, 0.0F, -1.0F), swing);
		float reach = (float) (from.length() * Math.pow(CUT_EYE / from.length(), swing));
		eye = new Vector3f(swung).mul(reach).add(home);
		at = new Vector3f(at).lerp(home, swing);
		float size = 1.0F - 0.55F * compact;
		Matrix4f held = new Matrix4f().translation(home).rotateY(compact * 0.6F).scale(size);
		cam.perspective(Shots.lerp(Shots.lerp(55.0, 46.0, Shots.smooth(pull * 1.5)), 46.0, swing), width, height,
				Math.max(1.0E-4F, Math.min(distance, reach) * 0.01F), 200.0F);
		cam.look(eye, at, new Vector3f(0, 1, 0));

		// Its glass shows once the camera is out of it; the other universes come up as it pulls away.
		float outside = Shots.smooth((Math.max(Math.abs(eye.x), Math.max(Math.abs(eye.y - lift), Math.abs(eye.z))) - 1.0) / 0.6);
		float others = Shots.smooth((distance - 2.6) / 4.0) * Shots.lerp(1.0F - 0.4F * pick, 1.0F, settle);
		float[] screen = {0.0F, 0.0F, width, height};
		// Ending as the window will show it: lit as the block is outside, the lattice as it is beyond the gate.
		float bright = 1.0F + (1.2F * pick + 1.5F * compact) * (1.0F - settle);
		float rim = Shots.lerp((0.3F + 1.4F * pick) * outside, 1.0F, settle);
		float sampled = Shots.lerp(20.0, 90.0, settle);
		int universes = multiverse(new Matrix4f(), -3, 3, -2, 2, -3, -1, others * (1.0F - Shots.smooth((m - MAP_SWING) / 8.0)), sampled, screen, null);
		universes += multiverse(new Matrix4f(), -3, 3, -2, 2, 0, 4, others, sampled, screen, () -> Universe.draw(cam.modelView(held), cam.proj, width,
				height, Universe.FULL, 0, bright, 1.0F, WHITE, 0.45F * outside, rim, 0xE6DCFF, 0.8F * compact * (1.0F - 0.45F * settle)));
		if (m >= MAP_PICK) {
			mapSelection(m - MAP_PICK, held);
		}
		crush(m, home, size, crushing);

		// The count of galaxies in view, from the one to all two trillion by the time the whole universe is.
		double seen = Math.pow(GALAXIES, Math.max(0.0, Math.min(1.0, Math.log(distance / MAP_NEAR) / Math.log(3.0 / MAP_NEAR))));
		boolean picked = m >= MAP_PICK;
		boolean taken = m >= MAP_CRUSH;
		boolean amongOthers = others > 0.5F;
		o.header = taken ? (m < MAP_CRUSHED ? "[ UNIVERSE 4,096,113 · COMPACTING ]" : "[ UNIVERSE 4,096,113 · EXTRACTING ]") : picked ? "[ UNIVERSE 4,096,113 · SELECTED ]"
				: amongOthers ? "[ THE VOID · NEIGHBOURING UNIVERSES ]" : "[ UNIVERSE 4,096,113 ]";
		o.headerReveal = Shots.smooth((taken ? m - (m < MAP_CRUSHED ? MAP_CRUSH : MAP_CRUSHED) : picked ? m - MAP_PICK : amongOthers ? m - 35.0 : m - 2.0) / 4.0);
		if (taken) {
			// Counted down as they are packed in: two trillion galaxies to one block.
			o.footer = m < MAP_CRUSHED ? "COMPACTING " + Math.round(100.0F * compact) + "%" : "1 BLOCK";
			o.footerSmall = m < MAP_CRUSHED ? "GALAXIES UNCOMPACTED " + galaxies(Math.max(1.0, GALAXIES * Math.pow(1.0 - compact, 2.0)))
					: "UNIVERSE 4,096,113 · 2 TRILLION GALAXIES";
		} else if (picked) {
			o.footer = "1 UNIVERSE SELECTED";
			o.footerSmall = "UNIVERSE 4,096,113 · 2 TRILLION GALAXIES";
		} else if (amongOthers) {
			o.footer = "UNIVERSES IN VIEW " + Feed.commas(universes);
			o.footerSmall = "GINNUNGAGAP · THE VOID BETWEEN UNIVERSES";
		} else if (m > MAP_PULL_FROM) {
			o.footer = "GALAXIES IN VIEW " + galaxies(seen);
			o.footerSmall = outside > 0.5F ? "UNIVERSE 4,096,113 · 93 BILLION LIGHT YEARS ACROSS" : "UNIVERSE 4,096,113";
		}
		label(o, MAP_GALAXY, 40, -24, "1 GALAXY", Feed.VIOLET, "OF 2,000,000,000,000", Feed.GREY,
				Shots.smooth((m - 4.0) / 4.0) * (1.0F - Shots.smooth((m - MAP_PULL_FROM) / 6.0)));
		Overlay.Label block = label(o, held.transformPosition(new Vector3f(1.0F, 1.0F, 1.0F)), 10, -6, "SELECTED", Feed.VIOLET, "UNIVERSE 4,096,113",
				Feed.GREY, Shots.smooth((m - MAP_PICK - 2.0) / 3.0) * (1.0F - Shots.smooth((m - MAP_CRUSH) / 4.0)));
		if (block != null) {
			block.marker = true;
		}
		// The pull back blurs out from the middle; a blink as the block is picked.
		float select = picked ? (float) Math.exp(-(m - MAP_PICK) / 2.5) : 0.0F;
		// The snap as it is crushed home.
		float snapped = m >= MAP_CRUSHED ? (float) Math.exp(-(m - MAP_CRUSHED) / 2.0) : 0.0F;
		o.flash = Math.max(0.25F * select, 0.45F * snapped);
		o.flashColor = 0xFFFFFF;
		o.zoomBlur = 0.1F * (float) Math.sin(Math.PI * pull) + 0.12F * (float) Math.sin(Math.PI * swing) + 0.18F * compact * (1.0F - compact) * 4.0F;
		o.aberration = 0.006F * (float) Math.sin(Math.PI * swing) + 0.01F * snapped;
		// Close over the galaxy its core would burn out the picture.
		o.exposure = 0.8F + 0.2F * Shots.smooth(pull * 2.0);
	}

	/**
	 * The crush: the light of its galaxies streaming in from all round into the block as it shrinks, faster and denser
	 * the harder it is crushed; then, as it snaps down to its last size, a ring of light blown out from it.
	 */
	private void crush(double m, Vector3f home, float size, float crushing) {
		float hard = (float) Math.sin(Math.PI * crushing);
		if (hard > 0.01F) {
			Fx streaks = space.glow(cam, Fx.BEAM, 0.0F);
			random.setSeed(4096113L);
			for (int i = 0; i < 180; i++) {
				Vector3f dir = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian()).normalize();
				double f = (m * (0.05 + 0.1 * crushing) + random.nextDouble()) % 1.0;
				float r0 = (float) (size * 1.1 + 6.0 * (1.0 - f));
				float r1 = Math.max(size * 1.0F, r0 - 0.8F - 1.6F * crushing);
				Vector3f a = new Vector3f(dir).mul(r0).add(home);
				Vector3f b = new Vector3f(dir).mul(r1).add(home);
				float alpha = hard * (float) f * 0.9F;
				streaks.beam(a, b, cam.pos, 0.012F + 0.02F * (float) f, Fx.argb(0.75F, 0.6F, 1.0F, 0.0F), Fx.argb(1.0F, 0.95F, 1.0F, alpha));
			}
			streaks.end(true, 2.6F);
		}
		double since = m - MAP_CRUSHED;
		if (since >= 0.0 && since < 9.0) {
			Fx ring = space.glow(cam, Fx.RING, 0.12F);
			float fade = (float) (1.0 - since / 9.0);
			ring.sprite(home, (float) (size * 1.4 + since * 1.1), 0.0F, Fx.argb(0.85F, 0.8F, 1.0F, fade * 0.9F));
			ring.sprite(home, (float) (size * 1.2 + since * 0.6), 0.0F, Fx.argb(1.0F, 1.0F, 1.0F, fade * 0.6F));
			ring.end(true, 2.4F);
		}
	}

	/** A count of galaxies the way the feed reads it out: in full up to a million, then in millions, billions, trillions. */
	private static String galaxies(double n) {
		if (n < 1.0E6) {
			return Feed.commas(Math.round(n));
		}
		String[] names = {"MILLION", "BILLION", "TRILLION"};
		int k = Math.min(2, (int) (Math.log10(n) / 3.0) - 2);
		return String.format(Locale.ROOT, "%.1f %s", n / Math.pow(10.0, 6 + 3 * k), names[k]);
	}

	/** The selection box round the middle cell, thin and white, blinking as it appears, as it does round the block later. */
	private void mapSelection(double since, Matrix4f block) {
		boolean blink = since < 2.0 || (since >= 4.0 && since < 6.0) || since >= 8.0;
		if (!blink) {
			return;
		}
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = block.transformPosition(new Vector3f((i & 1) == 0 ? -1.02F : 1.02F, (i & 2) == 0 ? -1.02F : 1.02F, (i & 4) == 0 ? -1.02F : 1.02F));
		}
		int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		Fx lines = space.glow(cam, Fx.LINE, 0.0F);
		float w = Math.max(0.004F, cam.pos.distance(c[0]) * 0.0016F);
		for (int[] e : edges) {
			lines.beam(c[e[0]], c[e[1]], cam.pos, w, Fx.argb(1.0F, 1.0F, 1.0F, 0.9F), Fx.argb(1.0F, 1.0F, 1.0F, 0.9F));
		}
		lines.end(true, 2.0F);
	}

	// =============================================================================================
	// 5. Back outside the gate: the selected block is cut out and drawn through; the window shuts behind it.
	// =============================================================================================

	private void cut(double s, Overlay o) {
		double r = s - CUT_S;
		// Straight on from the multiverse shot: just in front of the window, so close it fills the picture and the block is
		// still seen coming at the camera out of the void, the same size the last shot left it. Then backing away ahead of
		// it as it comes through, faster than it, the window's frame, the gate and Earth opening out round it.
		float back = Shots.smoother(r / (SEND_S - CUT_S));
		float distance = Math.max(Shots.lerp(4.0, 60.0, back), blockOut(s) + 19.0F);
		float aside = Shots.smooth((r - 8.0) / 30.0);
		Vector3f eye = new Vector3f(FACE).mul(distance).add(new Vector3f(GATE_RIGHT).mul(14.0F * aside))
				.add(new Vector3f(GATE_UP).mul(10.0F * aside));
		Vector3f at = blockPos(s).lerp(new Vector3f(FACE).mul(blockOut(s) * 0.55F).add(new Vector3f(GATE_UP).mul(-1.0F)), aside);
		scene(s, 0.4F, 30000.0F, eye, at, new Vector3f(GATE_UP), Shots.lerp(46.0, 50.0, back), true);

		o.header = "[ EXTRACTION ]";
		o.headerReveal = Shots.smooth(r / 4.0);
		Vector3f block = blockPos(s);
		Vector3f corner = new Matrix4f().translation(block).mul(GATE).scale(BLOCK).transformPosition(new Vector3f(1, 0.6F, 0));
		boolean through = s >= PULL_TO;
		label(o, corner, 46, -20, through ? "1 BLOCK" : "SELECTED", Feed.VIOLET, through ? "UNIVERSE 4,096,113" : "1 BLOCK",
				Feed.GREY, Shots.smooth((r - 1.0) / 3.0));
		o.footer = through ? "EXTRACTED" : "EXTRACTING";
		o.footerSmall = "UNIVERSE 4,096,113 · 1 BLOCK";
		// The punch of it coming through, and the snap of the window shutting.
		float burst = (float) Math.exp(-Math.pow((s - (PULL_FROM + PULL_TO) * 0.5) / 2.5, 2.0));
		o.zoomBlur = 0.1F * burst;
		o.aberration = 0.006F * burst;
		o.flash = 0.35F * (float) Math.exp(-Math.max(0.0, s - CLOSE_TO) / 2.0) * (s >= CLOSE_TO ? 1.0F : 0.0F);
		o.flashColor = 0xFFFFFF;
	}

	// =============================================================================================
	// 6. The bridge reaches down to the target; the block drops into it and is gone down it.
	// =============================================================================================

	private void send(double s, Overlay o) {
		double r = s - SEND_S;
		Vector3f block = blockHang(DROP_S);
		// Beside the block as the bridge lances down from it; then, as it drops, out to the whole bridge seen from far off
		// over the limb, the gate at its top and the target at its foot, the block a point of light racing down it.
		float out = Shots.smoother((s - DROP_S + 1.0) / 8.0);
		Vector3f nearEye = new Vector3f(block).add(46.0F, 6.0F, -16.0F);
		Vector3f wideEye = new Vector3f(block).add(1300.0F, 520.0F, -900.0F);
		float d0 = nearEye.distance(block);
		float d1 = wideEye.distance(block);
		Vector3f dir = Shots.slerp(new Vector3f(nearEye).sub(block).normalize(), new Vector3f(wideEye).sub(block).normalize(), out);
		Vector3f eye = new Vector3f(dir).mul((float) (d0 * Math.pow(d1 / d0, out))).add(block);
		Vector3f nearAt = blockPos(Math.min(s, DROP_S));
		Vector3f wideAt = new Vector3f(block).lerp(GROUND, 0.42F);
		Vector3f at = new Vector3f(nearAt).lerp(wideAt, out);
		float far = eye.distance(EARTH_CENTER) + EARTH_R;
		scene(s, Math.max(0.4F, eye.distance(block) * 0.004F), far, eye, at, new Vector3f(0, 1, 0), Shots.lerp(44.0, 54.0, out), true);
		if (out > 0.0F) {
			// The block far off: a point of light.
			Fx point = space.glow(cam, Fx.SPIKES, 0.0F);
			Vector3f p = blockPos(s);
			point.sprite(p, cam.pos.distance(p) * 0.02F * out, 0.0F, Fx.argb(0.95F, 0.9F, 1.0F, out));
			point.end(true, 3.0F);
		}

		o.header = "[ BRIDGE · SOL-3 ]";
		o.headerReveal = Shots.smooth(r / 4.0);
		float range = blockPos(s).distance(GROUND) / KM;
		o.footer = "RANGE " + Feed.commas(Math.round(range)) + " KM";
		Overlay.Label target = label(o, GROUND, 10, -5, "TARGET", Feed.VIOLET, "39.50 N · 98.50 W", Feed.GREY,
				Shots.smooth((s - DROP_S - 4.0) / 3.0));
		if (target != null) {
			target.marker = true;
		}
		float bridge = s >= BRIDGE_S ? (float) Math.exp(-(s - BRIDGE_S) / 3.0) : 0.0F;
		o.flash = 0.35F * bridge;
		o.flashColor = 0xD8C8FF;
		o.aberration = 0.008F * bridge;
		o.zoomBlur = 0.06F * bridge + 0.08F * (float) Math.sin(Math.PI * out);
	}

	// =============================================================================================
	// 7. Down the bridge after it, into the air, through the cloud deck.
	// =============================================================================================

	private void fall(double s, Overlay o) {
		double r = s - FALL_S;
		Vector3f block = blockPos(s);
		float altitude = block.y - GROUND.y;
		Vector3f down = new Vector3f(GROUND).sub(block).normalize();
		Vector3f back = new Vector3f(0.42F, 0.0F, -0.9F).normalize();
		float heat = Shots.smooth((150.0F - altitude) / 110.0F) * (1.0F - Shots.smooth((r - 64.0) / 4.0));
		// Four angles on it, each whipping round into the next: above and behind, looking down the bridge; then below it,
		// looking up as it comes straight at the camera; then from off to the side as it tears past; then in close, the
		// camera corkscrewing round it down into the cloud deck.
		float[] pose = fallPose(0, s, heat);
		for (int i = 1; i < FALL_ANGLES.length; i++) {
			float w = Shots.smoother((r - FALL_ANGLES[i]) / FALL_WHIP);
			if (w > 0.0F) {
				pose = mix(pose, fallPose(i, s, heat), w);
			}
		}
		Vector3f eye = new Vector3f(pose[0], pose[1], pose[2]);
		Vector3f at = new Vector3f(pose[3], pose[4], pose[5]);
		float shake = heat * 0.012F + 0.004F;
		float distance = eye.distance(block);
		at.add(Shots.noise(s * 4.1) * shake * distance, Shots.noise(s * 3.3 + 4) * shake * distance, Shots.noise(s * 3.7 + 8) * shake * distance);
		Vector3f up = new Vector3f(pose[6], pose[7], pose[8]).normalize();
		scene(s, 0.3F, altitude + EARTH_R * 0.6F, eye, at, up, pose[9], false);
		// Where it tears past the side camera, the air round it breaks into a ring of vapour and a crack of light.
		double past = r - (FALL_ANGLES[2] + 9.0);
		if (past > -1.0 && past < 10.0) {
			Fx ring = space.glow(cam, Fx.RING, 0.15F);
			float grow = (float) Math.max(0.0, past + 1.0);
			Vector3f u = new Vector3f(down).cross(back).normalize();
			Vector3f v = new Vector3f(u).cross(down).normalize();
			for (int k = 0; k < 3; k++) {
				// Rings round the way it is going, trailing back up behind it, each wider than the one before.
				Vector3f c = new Vector3f(block).sub(new Vector3f(down).mul(BLOCK * (1.0F + 2.6F * k + grow * 0.8F)));
				float size = BLOCK * (2.0F + grow * (0.9F + 0.4F * k));
				ring.flat(c, new Vector3f(u).mul(size), new Vector3f(v).mul(size), Fx.argb(0.9F, 0.88F, 1.0F, 0.7F * (1.0F - grow / 11.0F)));
			}
			ring.end(true, 2.2F);
		}

		// The block at its own scale, lit hot from below as the air piles up in front of it.
		block(blockModel(s), heat * 0.35F);
		if (heat > 0.01F) {
			sheath(block, down, heat);
		}
		clouds(s, block, down, back);

		o.header = "[ DESCENT · SOL-3 ]";
		o.headerReveal = Shots.smooth(r / 4.0);
		o.footer = "ALTITUDE " + Feed.commas(Math.max(0, Math.round(altitude / KM))) + " KM";
		o.footerSmall = heat > 0.2F ? "SHOCK LAYER " + Math.round(2000 + heat * 26000) + " K" : "UNIVERSE 4,096,113 · 1 BLOCK";
		o.aberration = heat * 0.01F;
		o.exposure = 1.0F + heat * 0.3F;
		o.saturation = 1.0F + heat * 0.2F;
		o.zoomBlur = 0.06F + 0.1F * heat;
		float deck = Shots.smooth((s - (DECK_S - 7.0)) / 7.0);
		o.flash = Math.max(deck, heat * 0.06F);
		o.flashColor = deck > 0.0F ? 0xFFFFFF : 0xC090FF;
	}

	/** When each of the fall's angles takes over, ticks into it, and how fast the camera whips round into it. */
	private static final double[] FALL_ANGLES = {0.0, 16.0, 31.0, 47.0};
	private static final double FALL_WHIP = 5.0;

	/** One of the fall's camera angles at time s: eye, at, up, fov. */
	private static float[] fallPose(int angle, double s, float heat) {
		double r = s - FALL_S;
		Vector3f block = blockPos(s);
		Vector3f down = new Vector3f(GROUND).sub(block).normalize();
		Vector3f back = new Vector3f(0.42F, 0.0F, -0.9F).normalize();
		Vector3f side = new Vector3f(down).cross(back).normalize();
		Vector3f eye;
		Vector3f at;
		Vector3f up;
		float fov;
		switch (angle) {
			case 0 -> {
				// Above it and a little behind, looking down the bridge at the target.
				float distance = 26.0F;
				eye = new Vector3f(block).add(new Vector3f(back).mul(distance * 0.42F)).sub(new Vector3f(down).mul(distance));
				at = new Vector3f(block).add(new Vector3f(down).mul(distance * 2.0F));
				up = new Vector3f(back).negate();
				fov = 52.0F;
			}
			case 1 -> {
				// Below it, a little off the bridge, falling more slowly than it: it comes on at the camera, sheath first.
				float k = Shots.smooth((r - FALL_ANGLES[1]) / 18.0);
				eye = new Vector3f(block).add(new Vector3f(down).mul(Shots.lerp(70.0, 26.0, k))).add(new Vector3f(side).mul(9.0F))
						.add(new Vector3f(back).mul(-5.0F));
				at = new Vector3f(block).add(new Vector3f(down).mul(4.0F));
				up = new Vector3f(back).negate();
				fov = Shots.lerp(34.0, 44.0, k) + 4.0F * heat;
			}
			case 2 -> {
				// Off to the side where it will pass, long lens, the camera still: it streaks across the frame and away.
				Vector3f where = blockPos(FALL_S + FALL_ANGLES[2] + 9.0);
				Vector3f d = new Vector3f(GROUND).sub(where).normalize();
				Vector3f across = new Vector3f(d).cross(back).normalize();
				eye = new Vector3f(where).add(new Vector3f(across).mul(48.0F)).add(new Vector3f(back).mul(10.0F)).sub(new Vector3f(d).mul(6.0F));
				at = new Vector3f(block);
				up = new Vector3f(d).negate();
				fov = 30.0F;
			}
			default -> {
				// Close in behind it, turning round it as it goes, the world rolling over under it; nearer as the air thickens.
				float near = Shots.smooth((r - FALL_ANGLES[3]) / 14.0);
				float distance = Shots.lerp(24.0, 16.0, near);
				double turn = (r - FALL_ANGLES[3]) * 0.06;
				Vector3f side2 = new Vector3f(side).cross(down).normalize();
				Vector3f round = new Vector3f(side).mul((float) Math.cos(turn)).add(new Vector3f(side2).mul((float) Math.sin(turn)));
				// Well out to the side of the sheath of shocked air, so it is seen burning rather than looked through.
				eye = new Vector3f(block).sub(new Vector3f(down).mul(distance * 0.7F)).add(new Vector3f(round).mul(distance * 0.9F));
				at = new Vector3f(block).add(new Vector3f(down).mul(distance * 2.2F));
				up = new Vector3f(round).negate();
				fov = Shots.lerp(56.0, 64.0, near) + 6.0F * heat;
			}
		}
		return new float[] {eye.x, eye.y, eye.z, at.x, at.y, at.z, up.x, up.y, up.z, fov};
	}

	/** Part way from one pose to another. */
	private static float[] mix(float[] a, float[] b, float k) {
		float[] out = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			out[i] = a[i] + (b[i] - a[i]) * k;
		}
		return out;
	}

	/** The violet sheath of shocked air in front of the block, and the ionised streaks it sheds. */
	private void sheath(Vector3f block, Vector3f down, float heat) {
		Matrix4f bow = new Matrix4f().translation(new Vector3f(block).add(new Vector3f(down).mul(BLOCK * 1.25F)))
				.rotateTowards(new Vector3f(down), new Vector3f(0, 0, 1)).scale(BLOCK * 2.6F, BLOCK * 2.6F, BLOCK * 6.5F);
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
