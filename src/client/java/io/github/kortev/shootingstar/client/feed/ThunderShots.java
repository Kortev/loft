package io.github.kortev.shootingstar.client.feed;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.Cam;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.Mesh;
import io.github.kortev.shootingstar.client.gfx.NoiseTex;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Mjölnir's storm feed. Out of the cloud deck into orbit over the night side, where every thunderstorm on Earth is
 * flickering and red sprites leap above them; the global circuit's charge relayed in to the target storm to storm by
 * megaflashes, from all round the planet, each storm going dark as it passes on; down onto the storm over the target as
 * it winds into one vortex, MJÖLNIR; and out of the storm's base after the stepped leader as it starts to feel its way
 * down, until the world takes it on. Each shot is one continuous move; the cuts hide in flashes.
 */
final class ThunderShots implements Feed.Sequence {
	// --- Earth: radius 1 at the origin. The target is the far north of Europe, on the night side, dawn behind the limb.
	private static final Vector3f TARGET = Mesh.direction(59.91, 10.75);
	private static final Vector3f NORTH = new Vector3f(0, 1, 0).sub(new Vector3f(TARGET).mul(TARGET.y)).normalize();
	private static final Vector3f EAST = new Vector3f(NORTH).cross(TARGET).normalize().negate();
	private static final Vector3f SUN = new Vector3f(TARGET).mul(-0.55F).add(new Vector3f(EAST).mul(0.83F)).normalize();
	/** Where the orbit shot ends up: south and west of the target, looking back at it over the night side. */
	private static final Vector3f AWAY = new Vector3f(TARGET).mul(0.85F).add(new Vector3f(EAST).mul(-0.3F))
			.add(new Vector3f(NORTH).mul(-0.42F)).normalize();
	private static final Matrix4f SKY = skyFrame(new Vector3f(TARGET).negate().add(new Vector3f(NORTH).mul(0.4F)).normalize(),
			NORTH);
	/** How far from the target (radians) the relay of lightning starts, out at the limb as the camera sees it. */
	private static final float FRONT_START = 1.95F;
	/** The vortex's angular radius over the target once it has wound up: about two hundred kilometres. */
	private static final float VORTEX = 0.03F;
	/**
	 * How big the storm over the target is, as a part of {@link #VORTEX}, through the feed: already the storm the camera
	 * rose through as the feed opens over it, growing as the relay feeds it, winding up to its full size in the dive.
	 */
	private static final float STORM_OPEN = 0.45F;
	private static final float STORM_FED = 0.55F;
	private static final int STORM_COUNT = 1812;
	/** Height of the storm's base over the ground in the leader shot (nine kilometres). */
	private static final float LOCAL_BASE = 90.0F;

	private final Space space = new Space();
	private final Cam cam = new Cam();
	private float width;
	private float height;
	private float guiW;
	private float guiH;
	private float time;

	@Override
	public boolean ready() {
		return Shaders.thunderReady();
	}

	@Override
	public Overlay render(double t, float fbWidth, float fbHeight, float guiWidth, float guiHeight) {
		space.ensure();
		width = fbWidth;
		height = fbHeight;
		guiW = guiWidth;
		guiH = guiHeight;
		time = (float) t;
		Overlay o = new Overlay();
		// A clean picture: only a hint of the feed's video lines, which show as coarse lines at lower resolutions.
		o.scanlines = 0.35F;
		o.headerColor = Feed.CYAN;
		o.accent = Feed.CYAN;
		space.resetLights();
		if (t < ThunderTimeline.DRAW) {
			orbit(t - ThunderTimeline.FEED, o);
		} else if (t < ThunderTimeline.FORGE) {
			draw(t - ThunderTimeline.DRAW, o);
		} else if (t < ThunderTimeline.LEADER) {
			forge(t - ThunderTimeline.FORGE, o);
		} else {
			leader(t - ThunderTimeline.LEADER, o);
		}
		return o;
	}

	// =============================================================================================
	// 1. Out of the storm cloud into orbit over the night side, every thunderstorm on Earth flickering.
	// =============================================================================================

	private record Pose(Vector3f eye, Vector3f at, Vector3f up, float fov) {
	}

	/** The flash the relay ends in as the draw shot cuts to the dive. */
	private static final float RING_FLASH = 0.75F;
	private static final int RING_FLASH_COLOR = 0xE6EEFF;
	private static final int ORBIT_LENGTH = ThunderTimeline.DRAW - ThunderTimeline.FEED;
	private static final int DRAW_LENGTH = ThunderTimeline.FORGE - ThunderTimeline.DRAW;
	private static final int FORGE_LENGTH = ThunderTimeline.LEADER - ThunderTimeline.FORGE;
	private static final int LEADER_LENGTH = ThunderTimeline.INBOUND - ThunderTimeline.LEADER;

	private static Pose orbitPose(double s) {
		float e = smoother(s / (ORBIT_LENGTH * 0.75));
		double altitude = 0.05 * Math.pow(3.0 / 0.05, e);
		Vector3f dir = slerp(TARGET, AWAY, e);
		Vector3f eye = new Vector3f(dir).mul((float) (1.0 + altitude));
		Vector3f at = new Vector3f(TARGET).mul(0.25F * e);
		return new Pose(eye, at, new Vector3f(NORTH), 50.0F);
	}

	private void orbit(double s, Overlay o) {
		Pose pose = orbitPose(s);
		earthCamera(pose, 0.0005F, 60.0F);
		float e = smoother(s / (ORBIT_LENGTH * 0.75));
		space.sky(cam, SKY, 0.8F, 0, cam.forward(), 0, 0, 0, time);
		// The storm the camera rose up through, under it as the feed opens and in sight all the way out.
		space.stormEarth(cam, new Matrix4f(), SUN, time * 0.00002F, 1.05F, time, TARGET, 1.0F, 4.0F, 0.0F,
				VORTEX * STORM_OPEN, spin(time), 0.0F);
		sprites(s);
		// Out of the dark of the storm cloud the camera rose into, lit once by its lightning on the way.
		float cloud = (float) Math.pow(Math.max(0.0, 1.0 - s / 9.0), 1.4);
		o.flash = cloud;
		// The same slate as the inside of the storm the rise ended in, lit through by its lightning: a stroke and a second.
		o.flashColor = 0x2E3444;
		float lit = (float) (s < 2.0 ? 0.0 : Math.exp(-(s - 2.0) / 0.5) * 0.45 + (s < 3.4 ? 0.0 : Math.exp(-(s - 3.4) / 0.6) * 0.3));
		if (lit > 0.01F) {
			o.flash = Math.min(1.0F, cloud + lit);
			o.flashColor = lerpColor(0x2E3444, 0xD8E4FF, lit / (cloud + lit));
		}
		o.header = "[ Þ-01 MJÖLNIR · GLOBAL CIRCUIT ]";
		o.headerReveal = smooth(s / 8.0);
		if (s > 14) {
			Vector3f limb = new Vector3f(cam.right()).mul(0.72F).add(new Vector3f(cam.up()).mul(0.7F)).normalize();
			label(o, limb, 10, -6, "EARTH · NIGHT SIDE", Feed.CYAN, "6,371 KM", Feed.GREY, smooth((s - 14) / 4.0));
		}
		if (s > 22) {
			int storms = (int) (STORM_COUNT * smooth((s - 22) / 26.0));
			o.footer = String.format(Locale.ROOT, "THUNDERSTORMS · %s ACTIVE", Feed.commas(storms));
			o.footerSmall = "IONOSPHERE 80 KM · +250 KV · 1,000 A";
		}
	}

	/**
	 * Red sprites: for a frame or two at a time, jellyfish of red light leap tens of kilometres up out of the tops of
	 * storms, somewhere on the night side.
	 */
	private void sprites(double s) {
		Fx red = space.glow(cam, Fx.BLOB, 0.6F);
		Vector3f toCam = new Vector3f(cam.pos).normalize();
		for (int i = 0; i < 40; i++) {
			Random r = new Random(i * 7919L);
			Vector3f at = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian()).normalize();
			// Only the half facing the camera, on the night side.
			if (at.dot(toCam) < 0.3F || at.dot(SUN) > -0.05F) {
				continue;
			}
			double rate = 0.02 + 0.02 * r.nextDouble();
			double phase = (time * rate + r.nextDouble()) % 1.0;
			if (phase > 0.06) {
				continue;
			}
			float b = (float) (1.0 - phase / 0.06);
			Vector3f top = new Vector3f(at).mul(1.012F);
			red.stretched(top, at, 0.006F, 0.0025F, Fx.argb(1.0F, 0.22F, 0.12F, b));
		}
		red.end(true, 3.0F);
	}

	// =============================================================================================
	// 2. The global circuit's charge drawn in across the planet to the target: lightning relayed storm to storm.
	// =============================================================================================

	/**
	 * One leap of the relay: a megaflash (lightning that runs hundreds of kilometres through the tops of the clouds, the
	 * longest ever seen from orbit) from one storm to the next one nearer the target; its channel and forks along the
	 * cloud tops, and how far from the target (radians) it starts and ends.
	 */
	private record Hop(List<Vector3f> channel, List<List<Vector3f>> forks, float from, float to, float seed) {
	}

	/**
	 * One line of storms relaying the charge in: its leaps, how far out (radians) it starts, and when (0 to 1 through the
	 * draw) it starts and reaches the target.
	 */
	private record Chain(List<Hop> hops, float start, float lag, float end) {
	}

	private static final int CHAINS = 18;
	/** The height of the cloud tops the megaflashes run through: sixteen kilometres. */
	private static final float CLOUD_TOPS = 1.0025F;
	private static final List<Chain> RELAY = relay();

	/**
	 * The relay's lines of storms, the same every time: from all round the target, each from out near the limb as the draw
	 * shot sees the planet, each leap four to eight hundred kilometres and wandering a little either side of the way to
	 * the target, so the lines are crooked and draw together only as they near it. They start and arrive at times of
	 * their own, so they never close on it as a ring or all at once.
	 */
	private static List<Chain> relay() {
		Random random = new Random(1234);
		Vector3f seen = new Vector3f(TARGET).mul(0.94F).add(new Vector3f(NORTH).mul(-0.34F)).normalize();
		List<Chain> chains = new ArrayList<>();
		for (int c = 0; c < CHAINS; c++) {
			double bearing = Math.PI * 2.0 * (c + random.nextDouble() * 0.7) / CHAINS;
			Vector3f across = new Vector3f(EAST).mul((float) Math.cos(bearing)).add(new Vector3f(NORTH).mul((float) Math.sin(bearing)));
			// Out along this bearing until the planet turns away from the camera.
			double limb = 0.3;
			while (limb < FRONT_START && out(across, limb).dot(seen) > 0.3F) {
				limb += 0.02;
			}
			double start = limb - 0.15 * random.nextDouble();
			Vector3f at = out(across, start);
			double curve = (random.nextDouble() - 0.5) * 0.5;
			List<Hop> hops = new ArrayList<>();
			float from = (float) start;
			while (from > 0.004F) {
				float length = (float) Math.min(from, 0.06 + 0.06 * random.nextDouble());
				Vector3f next;
				if (from - length < 0.03F) {
					next = new Vector3f(TARGET);
				} else {
					double turn = Math.max(-1.0, Math.min(1.0, curve * Math.min(1.0, from / 0.6) + random.nextGaussian() * 0.3));
					next = travel(at, turn, length);
				}
				float to = angle(next, TARGET);
				hops.add(new Hop(onSphere(jagged(at, next, 0.22F, 5, random)), forks(at, next, random), from, to,
						random.nextFloat() * 100.0F));
				at = next;
				from = to;
			}
			chains.add(new Chain(hops, (float) start, (float) (random.nextDouble() * 0.15), (float) (0.72 + 0.23 * random.nextDouble())));
		}
		return chains;
	}

	/** The place {@code distance} radians from the target along the ground the way {@code across} (a direction there). */
	private static Vector3f out(Vector3f across, double distance) {
		return new Vector3f(TARGET).mul((float) Math.cos(distance)).add(new Vector3f(across).mul((float) Math.sin(distance))).normalize();
	}

	/** From {@code at}, {@code length} radians along the ground, heading for the target turned {@code turn} radians off it. */
	private static Vector3f travel(Vector3f at, double turn, float length) {
		Vector3f toward = new Vector3f(TARGET).sub(new Vector3f(at).mul(at.dot(TARGET))).normalize();
		Vector3f side = new Vector3f(at).cross(toward);
		Vector3f heading = toward.mul((float) Math.cos(turn)).add(side.mul((float) Math.sin(turn)));
		return new Vector3f(at).mul((float) Math.cos(length)).add(heading.mul((float) Math.sin(length))).normalize();
	}

	private static float angle(Vector3f a, Vector3f b) {
		return (float) Math.acos(Math.max(-1.0F, Math.min(1.0F, new Vector3f(a).normalize().dot(b))));
	}

	/** A leap's forks: two to four, branching off sideways along the cloud tops. */
	private static List<List<Vector3f>> forks(Vector3f from, Vector3f to, Random random) {
		List<List<Vector3f>> forks = new ArrayList<>();
		float length = angle(from, to);
		int count = 2 + random.nextInt(3);
		for (int i = 0; i < count; i++) {
			float k = 0.15F + 0.7F * random.nextFloat();
			Vector3f root = slerp(from, to, k);
			Vector3f toward = new Vector3f(to).sub(new Vector3f(root).mul(root.dot(to))).normalize();
			Vector3f side = new Vector3f(root).cross(toward);
			double turn = (random.nextBoolean() ? 1.0 : -1.0) * (0.6 + 0.6 * random.nextDouble());
			Vector3f heading = toward.mul((float) Math.cos(turn)).add(side.mul((float) Math.sin(turn)));
			float reach = length * (0.2F + 0.25F * random.nextFloat());
			Vector3f tip = new Vector3f(root).mul((float) Math.cos(reach)).add(heading.mul((float) Math.sin(reach))).normalize();
			forks.add(onSphere(jagged(root, tip, 0.3F, 3, random)));
		}
		return forks;
	}

	/** A polyline's points brought out to the planet's surface (a straight line between two places runs under it). */
	private static List<Vector3f> onSphere(List<Vector3f> points) {
		List<Vector3f> out = new ArrayList<>(points.size());
		for (Vector3f p : points) {
			out.add(new Vector3f(p).normalize());
		}
		return out;
	}

	/** How many of the relay's lines have reached the target {@code k} through the draw, 0 to 1, each arriving over a moment. */
	private static float relayArrived(float k) {
		float arrived = 0.0F;
		for (Chain chain : RELAY) {
			arrived += smooth((k - chain.end()) * (DRAW_LENGTH - 4) / 4.0 + 0.5);
		}
		return arrived / RELAY.size();
	}

	/**
	 * How many ticks ago a line's front passed {@code distance} radians from the target, {@code k} through the draw
	 * (negative before it has): the inverse of {@link #relayFront}.
	 */
	private static float sincePassed(Chain chain, float distance, float k) {
		float rest = 1.0F - distance / chain.start();
		float u = (float) ((-0.6 + Math.sqrt(0.36 + 1.6 * rest)) / 0.8);
		float passed = chain.lag() + u * (chain.end() - chain.lag());
		return (k - passed) * (DRAW_LENGTH - 4);
	}

	/**
	 * Where a relay's front is {@code k} (0 to 1) through the draw, for a line that starts {@code start} radians out at
	 * {@code lag} and reaches the target at {@code end}: radians from the target, gathering speed as it comes in.
	 */
	private static float relayFront(float k, float start, float lag, float end) {
		float u = Math.max(0.0F, Math.min(1.0F, (k - lag) / (end - lag)));
		return start * (1.0F - u * (0.6F + 0.4F * u));
	}

	/**
	 * The relay as it stands {@code k} through the draw: on each line, the leap the front is crossing crawls out from the
	 * storm behind towards the one ahead, flickering; the ones it has passed flicker on a moment and fade.
	 */
	private void drawRelay(float k) {
		Vector3f toCam = new Vector3f(cam.pos).normalize();
		Fx glow = space.glow(cam, Fx.BEAM, 0.0F);
		List<float[]> lit = new ArrayList<>();
		for (Chain chain : RELAY) {
			float front = relayFront(k, chain.start(), chain.lag(), chain.end());
			for (Hop hop : chain.hops()) {
				if (front > hop.from() || sincePassed(chain, hop.to(), k) > 15.0F) {
					continue;
				}
				float crawl = Math.min(1.0F, (hop.from() - front) / Math.max(hop.from() - hop.to(), 1.0E-4F));
				// Once across, a leap lingers a moment and dies away, the last one into the target too.
				float since = sincePassed(chain, hop.to(), k);
				float fade = since <= 0.0F ? 1.0F : (float) Math.exp(-since / 3.0F);
				float flicker = 0.7F + 0.3F * (float) Math.sin(time * 2.1F + hop.seed());
				// Where the lines crowd together at the target, each is fainter, or together they burn it out.
				float b = fade * flicker * (0.4F + 0.6F * smooth((hop.to() - 0.03F) / 0.2F));
				if (b < 0.02F) {
					continue;
				}
				int shown = Math.max(1, Math.round(crawl * (hop.channel().size() - 1)));
				segments(glow, hop.channel(), shown, 0.012F, Fx.argb(0.55F, 0.62F, 1.0F, 0.45F * b));
				if (crawl >= 1.0F) {
					for (List<Vector3f> fork : hop.forks()) {
						segments(glow, fork, fork.size() - 1, 0.007F, Fx.argb(0.5F, 0.58F, 1.0F, 0.3F * b));
					}
				}
				Vector3f middle = hop.channel().get(shown / 2);
				if (middle.dot(toCam) > 0.2F) {
					lit.add(new float[] {middle.x, middle.y, middle.z, b, angleSpan(hop)});
				}
			}
		}
		glow.end(true, 3.0F);
		Fx core = space.glow(cam, Fx.BEAM, 0.0F);
		for (Chain chain : RELAY) {
			float front = relayFront(k, chain.start(), chain.lag(), chain.end());
			for (Hop hop : chain.hops()) {
				float since = sincePassed(chain, hop.to(), k);
				if (front > hop.from() || since > 8.0F) {
					continue;
				}
				float crawl = Math.min(1.0F, (hop.from() - front) / Math.max(hop.from() - hop.to(), 1.0E-4F));
				float fade = (since <= 0.0F ? 1.0F : (float) Math.exp(-since / 1.5F)) * (0.4F + 0.6F * smooth((hop.to() - 0.03F) / 0.2F));
				int shown = Math.max(1, Math.round(crawl * (hop.channel().size() - 1)));
				segments(core, hop.channel(), shown, 0.0022F, Fx.argb(0.92F, 0.95F, 1.0F, fade));
				if (crawl >= 1.0F) {
					for (List<Vector3f> fork : hop.forks()) {
						segments(core, fork, fork.size() - 1, 0.0013F, Fx.argb(0.9F, 0.93F, 1.0F, 0.7F * fade));
					}
				}
			}
		}
		core.end(true, 8.0F);
		// The cloud tops round each leap lit by it.
		Fx clouds = space.glow(cam, Fx.BLOB, 1.0F);
		for (float[] l : lit) {
			clouds.sprite(new Vector3f(l[0], l[1], l[2]).mul(1.004F), l[4] * 0.45F, 0.0F, Fx.argb(0.5F, 0.6F, 1.0F, 0.35F * l[3]));
		}
		clouds.end(false, 1.5F);
	}

	private static float angleSpan(Hop hop) {
		return Math.max(0.03F, hop.from() - hop.to());
	}

	/** The first {@code count} segments of a polyline on the cloud tops, as beams. */
	private void segments(Fx fx, List<Vector3f> points, int count, float width, int argb) {
		for (int i = 0; i < count && i + 1 < points.size(); i++) {
			fx.beam(new Vector3f(points.get(i)).mul(CLOUD_TOPS), new Vector3f(points.get(i + 1)).mul(CLOUD_TOPS), cam.pos, width, argb,
					argb);
		}
	}

	private static Pose drawPose(double s) {
		Pose start = orbitPose(ORBIT_LENGTH);
		float k = smoother(s / DRAW_LENGTH);
		// Round to face the target square on, closing in as the relay closes on it.
		Vector3f from = new Vector3f(start.eye()).normalize();
		Vector3f to = new Vector3f(TARGET).mul(0.94F).add(new Vector3f(NORTH).mul(-0.34F)).normalize();
		Vector3f dir = slerp(from, to, k);
		float distance = lerp(start.eye().length(), 2.3, k);
		Vector3f at = new Vector3f(start.at()).lerp(new Vector3f(TARGET).mul(0.5F), k);
		return new Pose(new Vector3f(dir).mul(distance), at, new Vector3f(NORTH), 50.0F);
	}

	private void draw(double s, Overlay o) {
		Pose pose = drawPose(s);
		earthCamera(pose, 0.0005F, 60.0F);
		float k = (float) Math.min(1.0, s / (DRAW_LENGTH - 4.0));
		// The storms the relay has passed have given up their charge: as far out as the middle of its lines.
		float front = relayFront(k, 1.6F, 0.07F, 0.84F);
		float drain = smooth(s / 20.0);
		// The storm over the target grows as the lines of the relay reach it, and its charge with them.
		float charge = 0.7F * relayArrived(k);
		space.sky(cam, SKY, 0.8F, 0, cam.forward(), 0, 0, 0, time);
		space.stormEarth(cam, new Matrix4f(), SUN, time * 0.00002F, 1.05F, time, TARGET, 1.0F, front, drain,
				VORTEX * (STORM_OPEN + (STORM_FED - STORM_OPEN) * relayArrived(k)), spin(time), charge);
		sprites(s + ORBIT_LENGTH);
		drawRelay(k);
		o.header = "[ EVERY STORM ON EARTH · RELAYING ]";
		o.headerReveal = smooth(s / 6.0);
		Overlay.Label label = label(o, TARGET, 10, -5, "TARGET", Feed.CYAN, "59.91 N · 10.75 E", Feed.GREY, smooth((s - 4) / 4.0));
		if (label != null) {
			label.marker = true;
		}
		double drawn = 4.8 * smoothIn(k);
		int drained = (int) (STORM_COUNT * smooth(k * 1.05));
		o.footer = String.format(Locale.ROOT, "CHARGE %.2f MC · POTENTIAL %.2f GV", drawn, 0.25 + 1.95 * smoothIn(k));
		o.footerSmall = String.format(Locale.ROOT, "MEGAFLASH RELAY · STORMS %s / %s", Feed.commas(drained), Feed.commas(STORM_COUNT));
		// The relay rings once as it reaches the target: a flash the cut to the dive goes through.
		if (s > DRAW_LENGTH - 3) {
			o.flash = RING_FLASH * smooth((s - (DRAW_LENGTH - 3)) / 3.0);
			o.flashColor = RING_FLASH_COLOR;
		}
	}

	// =============================================================================================
	// 3. Down onto the storm over the target as it winds into one vortex: MJÖLNIR.
	// =============================================================================================

	private void forge(double s, Overlay o) {
		Pose start = drawPose(DRAW_LENGTH);
		float k = smoother(s / (FORGE_LENGTH - 6.0));
		// Down to about two hundred kilometres: any lower and the storm's sunlit tops fill the frame white.
		float altitude = (float) ((start.eye().length() - 1.0) * Math.pow(0.03 / (start.eye().length() - 1.0), k));
		Vector3f dir = slerp(new Vector3f(start.eye()).normalize(), TARGET, k);
		Vector3f eye = new Vector3f(dir).mul(1.0F + altitude);
		Vector3f at = new Vector3f(start.at()).lerp(TARGET, smooth(s / 20.0));
		// Looking straight down at the end the view would roll with the dive; keep north up.
		earthCamera(new Pose(eye, at, new Vector3f(NORTH), 50.0F - 8.0F * k), Math.max(0.00005F, altitude * 0.2F), 60.0F);
		float wound = smooth(s / 40.0);
		space.sky(cam, SKY, 0.8F * (1.0F - k), 0, cam.forward(), 0, 0, 0, time);
		// Close in, the storm is dark cloud lit from inside: its lightning and the charge at its heart are turned down so they
		// flicker in it rather than fill the frame.
		float close = smooth((k - 0.5) * 2.0);
		// On from the draw just as it left the storm, charge and all, winding it up to its full size.
		float size = STORM_FED + (1.0F - STORM_FED) * wound;
		space.stormEarth(cam, new Matrix4f(), SUN, time * 0.00002F, lerp(1.05, 1.1, wound), time, TARGET, 1.0F - 0.5F * close, 0.0F,
				1.0F, VORTEX * size, spin(time) * (1.0F + wound), (0.7F - 0.4F * wound) * (1.0F - 0.95F * close));
		// Out of the flash the circuit closed with.
		if (s < 8.0) {
			o.flash = RING_FLASH * (float) Math.exp(-s / 2.0);
			o.flashColor = RING_FLASH_COLOR;
		}
		o.header = "[ SUPERCELL · TARGET ]";
		o.headerReveal = smooth(s / 6.0);
		float title = smooth((s - 8.0) / 6.0) * (1.0F - smooth((s - 46.0) / 8.0));
		if (title > 0.0F) {
			o.title = "MJÖLNIR";
			o.subtitle = "Þ-01 · THE GLOBAL CIRCUIT IN ONE BOLT";
			o.titleAlpha = title;
		}
		o.footer = String.format(Locale.ROOT, "ROTATION %d KM/H · DIAMETER %d KM", (int) (60 + 260 * wound),
				Math.round(2.0F * VORTEX * size * 6371.0F));
		o.footerSmall = String.format(Locale.ROOT, "CLOUD TOPS %,d M", (int) (11000 + 7000 * wound));
		// Down into the dark of the eye, where the next shot opens.
		if (s > FORGE_LENGTH - 8) {
			o.flash = smooth((s - (FORGE_LENGTH - 8)) / 7.0);
			o.flashColor = 0x0C0E14;
		}
	}

	// =============================================================================================
	// 4. Out of the storm's base after the stepped leader as it starts to feel its way down to the ground.
	// =============================================================================================

	/**
	 * Lightning in the storm's base while the leader comes down, one flash at a time: when (ticks into the shot), where
	 * (in the shot's units, beyond the column as the camera sees it and across), and how bright.
	 */
	private static final float[][] CLOUD_FLASHES = {{3.0F, 60.0F, -50.0F, 1.0F}, {13.0F, 180.0F, 90.0F, 0.7F},
			{22.0F, 20.0F, 30.0F, 1.2F}, {31.0F, 120.0F, -110.0F, 0.8F}, {39.0F, 90.0F, 40.0F, 1.0F}};
	/** Which way the leader shot looks across the column, on average, in radians round it. */
	private static final double LEADER_BEARING = Math.toRadians(41.0);

	private void leader(double s, Overlay o) {
		// In units of a hundred metres, the ground under the target at y = 0. The leader comes out of the storm's base and
		// feels its way down; the feed follows it LEADER_HANDOFF of the way, and the world picks it up from there.
		double reach = ThunderTimeline.LEADER_HANDOFF * leaderReach(s / (LEADER_LENGTH - 4.0));
		float tip = LOCAL_BASE * (1.0F - (float) reach);
		// Eleven kilometres off and under three up, looking a little up at the column as a photograph of lightning at night
		// would have it: the storm's base a ceiling over the upper two thirds of the frame, running away to the horizon, the
		// leader hanging out of it, the dark country and its towns below, and the ground under the target in frame for the
		// streamer; drifting in and round as the leader comes down.
		float fall = smoother(s / (LEADER_LENGTH - 2.0));
		float dist = lerp(110.0, 88.0, fall);
		double round = Math.toRadians(35.0 + 12.0 * fall);
		Vector3f eye = new Vector3f((float) (Math.cos(round) * dist), 28.0F, (float) (Math.sin(round) * dist));
		Vector3f at = new Vector3f(0.0F, lerp(48.0, 44.0, fall), 0.0F);
		cam.perspective(62.0F, width, height, 0.3F, 200000.0F);
		cam.look(eye, at, new Vector3f(0, 1, 0));

		// The night beyond the storm's edge, low over the horizon: the dark of the rain the country is lost in, not black.
		RenderSystem.clearColor(0.05F, 0.056F, 0.08F, 1.0F);
		RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
		RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);

		Random random = new Random(42);
		List<Vector3f> channel = jagged(new Vector3f(0.0F, LOCAL_BASE, 0.0F), new Vector3f(0, 0, 0), 0.16F, 6, random);
		List<List<Vector3f>> branches = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			Vector3f root = channel.get(4 + random.nextInt(channel.size() - 12));
			double a = random.nextDouble() * Math.PI * 2.0;
			Vector3f dir = new Vector3f((float) Math.cos(a), -1.1F, (float) Math.sin(a)).normalize();
			branches.add(jagged(root, new Vector3f(root).add(dir.mul(root.y * (0.2F + 0.35F * random.nextFloat()))), 0.3F, 4, random));
		}
		// Each step of the leader flares at its tip; the storm's own lightning lights its base and the country now and then.
		float step = (float) Math.exp(-(s - Math.floor(s)) * 3.0);
		float[] cloud = cloudFlash(s);
		ground(channelAt(channel, tip), 0.5F + 0.4F * step * (float) reach, 0.05F + 0.35F * cloud[2]);
		Space.clearDepth();
		// The streamer reaching up off the ground at the target to meet the leader.
		Fx streamer = space.glow(cam, Fx.BLOB, 1.0F);
		streamer.sprite(new Vector3f(0.0F, 0.5F, 0.0F), 3.0F + 6.0F * (float) reach, 0.0F, Fx.argb(0.7F, 0.65F, 1.0F, (float) reach));
		streamer.end(true, 2.0F + 6.0F * (float) reach);
		stormLayers(cloud);
		leaderBolt(channel, branches, reach, step);

		o.header = "[ STEPPED LEADER ]";
		o.headerReveal = smooth(s / 4.0);
		int altitude = Math.max(0, Math.round(tip * 100.0F));
		o.footer = String.format(Locale.ROOT, "LEADER ALTITUDE %,d M", altitude);
		int steps = (int) Math.round(reach * ThunderTimeline.LEADER_STEPS * 3);
		o.footerSmall = String.format(Locale.ROOT, "STEP %d · 50 M EVERY 50 µS", steps);
		// A touch of zoom blur as the camera drifts in (one pass, not the shutter, which would draw the shot six times).
		o.zoomBlur = 0.015F;
		// Up out of the dark of the eye the dive ended in.
		if (s < 6.0) {
			o.flash = 1.0F - smooth(s / 6.0);
			o.flashColor = 0x0C0E14;
		}
		// A step brighter than any before it hands over to the world, where the leader comes on down.
		if (s > LEADER_LENGTH - 3) {
			o.flash = smooth((s - (LEADER_LENGTH - 3)) / 2.5) * 0.9F;
			o.flashColor = 0xE8ECFF;
		}
	}

	/**
	 * The brightest of the storm's flashes at {@code s} ticks into the leader shot: where it is across the storm's base (in
	 * the base's UV) and how bright, flickering as lightning does: a stroke, and a second a moment later.
	 */
	private static float[] cloudFlash(double s) {
		float[] best = {0.0F, 0.0F, 0.0F};
		double c = Math.cos(LEADER_BEARING);
		double n = Math.sin(LEADER_BEARING);
		for (float[] f : CLOUD_FLASHES) {
			double e = s - f[0];
			if (e < 0.0) {
				continue;
			}
			double b = (Math.exp(-e / 1.2) + (e > 2.5 ? 0.7 * Math.exp(-(e - 2.5) / 1.5) : 0.0)) * f[3];
			if (b > best[2]) {
				// Beyond the column is away from the camera; across is square to that.
				best[0] = (float) ((-c * f[1] - n * f[2]) / STORM_LAYER_RADIUS);
				best[1] = (float) ((-n * f[1] + c * f[2]) / STORM_LAYER_RADIUS);
				best[2] = (float) b;
			}
		}
		return best;
	}

	/** The ground under the storm, a plane at y = 0 out to two hundred kilometres each way, in ss_ground. */
	private void ground(Vector3f tip, float tipLight, float flash) {
		Space.opaque();
		float r = 2000.0F;
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		b.vertex(-r, 0.0F, -r).texture(-r, -r).color(255, 255, 255, 255);
		b.vertex(-r, 0.0F, r).texture(-r, r).color(255, 255, 255, 255);
		b.vertex(r, 0.0F, r).texture(r, r).color(255, 255, 255, 255);
		b.vertex(r, 0.0F, -r).texture(r, -r).color(255, 255, 255, 255);
		RenderSystem.setShaderTexture(0, NoiseTex.get());
		Shaders.set(Shaders.ground, "Time", time);
		Shaders.set(Shaders.ground, "Tip", tip.x, tip.y, tip.z);
		Shaders.set(Shaders.ground, "TipLight", tipLight);
		Shaders.set(Shaders.ground, "Flash", flash);
		// Rain at night: twenty kilometres or so and the ground is lost.
		Shaders.set(Shaders.ground, "Haze", 0.004F);
		Post.draw(b, Shaders.ground, cam.view, cam.proj);
	}

	/** How far the leader shot's storm base runs each way: two hundred kilometres, out to the horizon. */
	private static final float STORM_LAYER_RADIUS = 1900.0F;

	/**
	 * Layers of the storm's base: the ceiling the leader comes down out of, its lumps lit faintly from below by the glow of
	 * the leader and the country, and brightly from inside wherever the storm's lightning ({@code flash}, as
	 * {@link #cloudFlash}) is.
	 */
	private void stormLayers(float[] flash) {
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
		float radius = STORM_LAYER_RADIUS;
		RenderSystem.setShaderTexture(0, NoiseTex.get());
		for (int layer = 0; layer < 3; layer++) {
			float y = LOCAL_BASE + 14.0F - layer * 7.0F;
			BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
			b.vertex(-radius, y, -radius).texture(-1.0F, -1.0F).color(255, 255, 255, 255);
			b.vertex(radius, y, -radius).texture(1.0F, -1.0F).color(255, 255, 255, 255);
			b.vertex(radius, y, radius).texture(1.0F, 1.0F).color(255, 255, 255, 255);
			b.vertex(-radius, y, radius).texture(-1.0F, 1.0F).color(255, 255, 255, 255);
			Shaders.set(Shaders.vortex, "Time", time);
			Shaders.set(Shaders.vortex, "Spin", spin(time) * 3.0F + layer * 0.4F);
			Shaders.set(Shaders.vortex, "Density", 1.0F);
			Shaders.set(Shaders.vortex, "Layer", (float) layer);
			Shaders.set(Shaders.vortex, "Daylight", 0.1F);
			// A flash lights a few kilometres of cloud round it.
			Shaders.set(Shaders.vortex, "Flash", flash[0], flash[1], 2.4F * flash[2]);
			Shaders.set(Shaders.vortex, "FlashFalloff", 700.0F);
			// The cloud a few kilometres across, where the disc is two hundred.
			Shaders.set(Shaders.vortex, "Detail", 8.0F);
			Shaders.set(Shaders.vortex, "Stroke", 0.16F);
			// No eye here: the leader comes out of solid cloud.
			Shaders.set(Shaders.vortex, "Eye", 0.002F);
			Shaders.set(Shaders.vortex, "FadeEnd", 1.0E6F);
			Post.draw(b, Shaders.vortex, cam.view, cam.proj);
		}
		RenderSystem.disableBlend();
	}

	/** The leader itself: violet-white, as far down as it has reached, its branches feeling out round it. */
	private void leaderBolt(List<Vector3f> channel, List<List<Vector3f>> branches, double reach, float flash) {
		float bottom = LOCAL_BASE * (1.0F - (float) reach);
		Fx glow = space.glow(cam, Fx.BEAM, 0.0F);
		polyline(glow, channel, bottom, 2.4F, Fx.argb(0.62F, 0.5F, 1.0F, 0.5F + 0.4F * flash));
		for (List<Vector3f> branch : branches) {
			polyline(glow, branch, bottom, 1.2F, Fx.argb(0.55F, 0.45F, 1.0F, 0.35F + 0.3F * flash));
		}
		glow.end(true, 3.5F);
		Fx core = space.glow(cam, Fx.BEAM, 0.0F);
		polyline(core, channel, bottom, 0.3F, Fx.argb(0.95F, 0.95F, 1.0F, 0.7F + 0.3F * flash));
		for (List<Vector3f> branch : branches) {
			polyline(core, branch, bottom, 0.1F, Fx.argb(0.9F, 0.9F, 1.0F, 0.5F));
		}
		core.end(true, 10.0F);
		// The tip, flaring with each step.
		Fx tipGlow = space.glow(cam, Fx.SPIKES, 0.0F);
		tipGlow.sprite(new Vector3f(channelAt(channel, bottom)), 2.5F + 3.0F * flash, 0.0F, Fx.argb(0.85F, 0.8F, 1.0F, 1.0F));
		tipGlow.end(true, 6.0F);
	}

	private static Vector3f channelAt(List<Vector3f> channel, float y) {
		Vector3f last = channel.get(0);
		for (Vector3f p : channel) {
			if (p.y < y) {
				break;
			}
			last = p;
		}
		return last;
	}

	private void polyline(Fx fx, List<Vector3f> points, float bottom, float width, int argb) {
		for (int i = 0; i + 1 < points.size(); i++) {
			Vector3f a = points.get(i);
			Vector3f b = points.get(i + 1);
			if (a.y < bottom) {
				return;
			}
			fx.beam(a, b, cam.pos, width, argb, argb);
		}
	}

	private static List<Vector3f> jagged(Vector3f a, Vector3f b, float roughness, int levels, Random random) {
		List<Vector3f> points = new ArrayList<>();
		points.add(a);
		subdivide(points, a, b, roughness, levels, random);
		return points;
	}

	private static void subdivide(List<Vector3f> points, Vector3f a, Vector3f b, float roughness, int levels, Random random) {
		if (levels == 0) {
			points.add(b);
			return;
		}
		Vector3f d = new Vector3f(b).sub(a);
		float len = d.length();
		Vector3f axis = new Vector3f(d).div(Math.max(len, 1.0E-6F));
		Vector3f jitter = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian());
		jitter.sub(new Vector3f(axis).mul(jitter.dot(axis)));
		Vector3f mid = new Vector3f(a).add(new Vector3f(d).mul(0.5F)).add(jitter.mul(len * roughness * 0.5F));
		subdivide(points, a, mid, roughness * 0.72F, levels - 1, random);
		subdivide(points, mid, b, roughness * 0.72F, levels - 1, random);
	}

	/** The leader's reach down the shot, 0 to 1, a step at a time from the start, quickening a little. */
	private static double leaderReach(double p) {
		p = Math.max(0.0, Math.min(1.0, p));
		double steps = Math.floor(ThunderTimeline.LEADER_STEPS * 3 * (0.7 * p + 0.3 * p * p));
		return Math.min(1.0, steps / (ThunderTimeline.LEADER_STEPS * 3.0));
	}

	// =============================================================================================
	// Cameras and helpers
	// =============================================================================================

	/** How far the storm over the target has turned, in radians. */
	private static float spin(float time) {
		return time * 0.012F;
	}

	private void earthCamera(Pose pose, float near, float far) {
		cam.perspective(pose.fov(), width, height, near, far);
		cam.look(pose.eye(), pose.at(), pose.up());
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

	private static int lerpColor(int a, int b, float t) {
		int r = Math.round(lerp(a >> 16 & 255, b >> 16 & 255, t));
		int g = Math.round(lerp(a >> 8 & 255, b >> 8 & 255, t));
		int bl = Math.round(lerp(a & 255, b & 255, t));
		return r << 16 | g << 8 | bl;
	}

	private static float smooth(double x) {
		x = x < 0 ? 0 : x > 1 ? 1 : x;
		return (float) (x * x * (3 - 2 * x));
	}

	private static float smoother(double x) {
		x = x < 0 ? 0 : x > 1 ? 1 : x;
		return (float) (x * x * x * (x * (x * 6 - 15) + 10));
	}

	/** Eases in only: slow to start, at full speed at the end. */
	private static float smoothIn(double x) {
		x = x < 0 ? 0 : x > 1 ? 1 : x;
		return (float) (x * x);
	}

	private static float lerp(double a, double b, float t) {
		return (float) (a + (b - a) * t);
	}
}
