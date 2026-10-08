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
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Mjölnir's storm feed. Out of the cloud deck into orbit over the night side, where every thunderstorm on Earth is
 * flickering and red sprites leap above them; the global circuit's charge drawn in across the planet as a ring of light
 * closing on the target, the storms it passes going dark; down onto the storm over the target as it winds into one
 * vortex, MJÖLNIR; and down the eye after the stepped leader as it feels its way to the ground, until the ground rushes up
 * white. Each shot is one continuous move; the cuts hide in flashes.
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
	/** The ring of charge starts this far from the target (radians) and closes on it over the draw. */
	private static final float FRONT_START = 2.9F;
	/** The vortex's angular radius over the target once it has wound up: about two hundred kilometres. */
	private static final float VORTEX = 0.03F;
	private static final int STORM_COUNT = 1812;
	/** Earth's radius in the leader shot's units (a hundred metres), where the ground under the target is y = 0. */
	private static final float LOCAL_R = 63710.0F;
	/** Height of the storm's base over the ground in the leader shot (nine kilometres). */
	private static final float LOCAL_BASE = 90.0F;

	private final Space space = new Space();
	private final Cam cam = new Cam();
	@Nullable
	private Mesh patch;
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
		if (patch == null) {
			// The ground round the target, for the leader shot: about five degrees each way of the map.
			float u = (float) ((Math.toRadians(10.75) + Math.PI) / (Math.PI * 2.0));
			float v = (float) ((Math.PI / 2.0 - Math.toRadians(59.91)) / Math.PI);
			patch = Mesh.spherePatch(u - 0.02F, u + 0.02F, v - 0.012F, v + 0.012F, 96);
		}
		width = fbWidth;
		height = fbHeight;
		guiW = guiWidth;
		guiH = guiHeight;
		time = (float) t;
		Overlay o = new Overlay();
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
		space.stormEarth(cam, new Matrix4f(), SUN, time * 0.00002F, 1.0F - smooth(e * 2.0F), 1.05F, time, TARGET, 1.0F,
				4.0F, 0.0F, VORTEX * 0.15F * e, spin(time), 0.0F);
		sprites(s);
		// Out of the dark of the storm cloud the camera rose into, lit once by its lightning on the way.
		float cloud = (float) Math.pow(Math.max(0.0, 1.0 - s / 9.0), 1.4);
		o.flash = cloud;
		o.flashColor = s > 2.0 && s < 3.5 ? 0xD8E4FF : 0x161A24;
		if (s > 2.0 && s < 3.5) {
			o.flash = Math.max(o.flash, 0.8F);
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
	// 2. The global circuit's charge drawn in across the planet to the target.
	// =============================================================================================

	private static Pose drawPose(double s) {
		Pose start = orbitPose(ORBIT_LENGTH);
		float k = smoother(s / DRAW_LENGTH);
		// Round to face the target square on, closing in as the ring closes.
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
		float front = lerp(FRONT_START, 0.0, smoother(k));
		float drain = smooth(s / 20.0);
		float charge = smooth((s - 30.0) / 60.0) * 0.7F;
		space.sky(cam, SKY, 0.8F, 0, cam.forward(), 0, 0, 0, time);
		space.stormEarth(cam, new Matrix4f(), SUN, time * 0.00002F, 0.0F, 1.05F, time, TARGET, 1.0F, front, drain,
				VORTEX * (0.15F + 0.25F * k), spin(time), charge);
		sprites(s + ORBIT_LENGTH);
		o.header = "[ DRAWING THE CIRCUIT ]";
		o.headerReveal = smooth(s / 6.0);
		Overlay.Label label = label(o, TARGET, 10, -5, "TARGET", Feed.CYAN, "59.91 N · 10.75 E", Feed.GREY, smooth((s - 4) / 4.0));
		if (label != null) {
			label.marker = true;
		}
		double drawn = 4.8 * smoothIn(k);
		int drained = (int) (STORM_COUNT * smooth(k * 1.05));
		o.footer = String.format(Locale.ROOT, "CHARGE %.2f MC · POTENTIAL %.2f GV", drawn, 0.25 + 1.95 * smoothIn(k));
		o.footerSmall = String.format(Locale.ROOT, "STORMS DRAINED %s / %s", Feed.commas(drained), Feed.commas(STORM_COUNT));
		// The ring closing in rings once as it meets the target.
		if (s > DRAW_LENGTH - 6) {
			o.flash = (float) (0.6 * smooth((s - (DRAW_LENGTH - 6)) / 4.0));
			o.flashColor = 0xE6EEFF;
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
		space.stormEarth(cam, new Matrix4f(), SUN, time * 0.00002F, smooth((k - 0.4) * 2.0) * 0.8F, 1.1F, time, TARGET, 1.0F, 0.0F,
				1.0F, VORTEX * (0.4F + 0.6F * wound), spin(time) * (1.0F + wound), 0.7F - 0.4F * wound);
		o.header = "[ SUPERCELL · TARGET ]";
		o.headerReveal = smooth(s / 6.0);
		float title = smooth((s - 8.0) / 6.0) * (1.0F - smooth((s - 46.0) / 8.0));
		if (title > 0.0F) {
			o.title = "MJÖLNIR";
			o.subtitle = "Þ-01 · THE GLOBAL CIRCUIT IN ONE BOLT";
			o.titleAlpha = title;
		}
		o.footer = String.format(Locale.ROOT, "ROTATION %d KM/H · DIAMETER %d KM", (int) (60 + 260 * wound),
				(int) (120 + 260 * wound));
		o.footerSmall = String.format(Locale.ROOT, "CLOUD TOPS %,d M", (int) (11000 + 7000 * wound));
		// Into the eye in a flash of its lightning.
		if (s > FORGE_LENGTH - 4) {
			o.flash = smooth((s - (FORGE_LENGTH - 4)) / 3.0);
			o.flashColor = 0xDCE6FF;
		}
	}

	// =============================================================================================
	// 4. Down the eye after the stepped leader as it feels its way to the ground.
	// =============================================================================================

	private void leader(double s, Overlay o) {
		// Earth in units of a hundred metres, turned so the target is straight up and moved so it is at the origin.
		Matrix4f ground = new Matrix4f().translation(0.0F, -LOCAL_R, 0.0F)
				.rotate(new Quaternionf().rotationTo(TARGET, new Vector3f(0, 1, 0))).scale(LOCAL_R);
		Vector3f localSun = new Quaternionf().rotationTo(TARGET, new Vector3f(0, 1, 0)).transform(new Vector3f(SUN));
		double reach = leaderReach(s / (LEADER_LENGTH - 4.0));
		float tip = LOCAL_BASE * (1.0F - (float) reach);
		// The camera falls with the tip, a little above it and off to one side, looking down past it.
		float fall = smoother(s / (LEADER_LENGTH - 2.0));
		float camY = lerp(LOCAL_BASE + 22.0, 4.0, fall);
		Vector3f eye = new Vector3f(6.0F + 10.0F * fall, Math.max(camY, tip + 6.0F), 5.0F);
		Vector3f at = new Vector3f(0.0F, Math.max(0.0F, tip - 25.0F), 0.0F);
		cam.perspective(62.0F, width, height, 0.3F, 200000.0F);
		cam.look(eye, at, new Vector3f(0, 0, -1));

		Random random = new Random(42);
		List<Vector3f> channel = jagged(new Vector3f(0.0F, LOCAL_BASE, 0.0F), new Vector3f(0, 0, 0), 0.16F, 6, random);
		List<List<Vector3f>> branches = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			Vector3f root = channel.get(4 + random.nextInt(channel.size() - 12));
			double a = random.nextDouble() * Math.PI * 2.0;
			Vector3f dir = new Vector3f((float) Math.cos(a), -1.1F, (float) Math.sin(a)).normalize();
			branches.add(jagged(root, new Vector3f(root).add(dir.mul(root.y * (0.2F + 0.35F * random.nextFloat()))), 0.3F, 4, random));
		}
		float flash = (float) Math.exp(-(s - Math.floor(s)) * 3.0);
		// Under the storm: the ground lit only by the leader and by the storm's own flashes.
		// Night under the storm: the ground dark, lit only as the leader steps (and its cities' lights).
		space.stormEarthPatch(patch, cam, ground, localSun, 0.0F, 1.0F, 0.3F + 0.5F * flash, time, TARGET, 0.0F, 0.0F, 1.0F, 0.0F,
				0.0F, 0.0F);
		Space.clearDepth();
		// The streamer reaching up off the ground at the target to meet the leader.
		Fx streamer = space.glow(cam, Fx.BLOB, 1.0F);
		streamer.sprite(new Vector3f(0.0F, 0.5F, 0.0F), 3.0F + 6.0F * (float) reach, 0.0F, Fx.argb(0.7F, 0.65F, 1.0F, (float) reach));
		streamer.end(true, 2.0F + 6.0F * (float) reach);
		stormLayers(s, flash, tip);
		leaderBolt(channel, branches, reach, flash);

		o.header = "[ STEPPED LEADER ]";
		o.headerReveal = smooth(s / 4.0);
		int altitude = Math.max(0, Math.round(tip * 100.0F));
		o.footer = String.format(Locale.ROOT, "LEADER ALTITUDE %,d M", altitude);
		int step = (int) Math.round(reach * ThunderTimeline.LEADER_STEPS * 3);
		o.footerSmall = String.format(Locale.ROOT, "STEP %d · 50 M EVERY 50 µS", step);
		// Falling: a zoom blur (one pass) rather than the shutter, which would draw the whole shot six times a frame.
		o.zoomBlur = 0.03F + 0.09F * fall;
		// The ground rushes up: white.
		if (s > LEADER_LENGTH - 6) {
			o.flash = smooth((s - (LEADER_LENGTH - 6)) / 5.0);
			o.flashColor = 0xF4F7FF;
		}
	}

	/** Layers of the storm's base round the eye, which the camera falls through. */
	private void stormLayers(double s, float flash, float tip) {
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
		float radius = 1900.0F;
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
			Shaders.set(Shaders.vortex, "Daylight", 0.0F);
			Shaders.set(Shaders.vortex, "Flash", 0.0F, 0.0F, 2.6F * flash + 0.12F);
			Shaders.set(Shaders.vortex, "Stroke", 0.0F);
			Shaders.set(Shaders.vortex, "Eye", 0.012F);
			Shaders.set(Shaders.vortex, "FogEnd", 1.0E6F);
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

	/** The leader's reach down the shot, 0 to 1, in steps that come faster as it nears the ground. */
	private static double leaderReach(double p) {
		p = Math.max(0.0, Math.min(1.0, p));
		double steps = Math.floor(ThunderTimeline.LEADER_STEPS * 3 * (0.35 * p + 0.65 * p * p));
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
