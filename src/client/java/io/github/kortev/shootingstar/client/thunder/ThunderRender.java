package io.github.kortev.shootingstar.client.thunder;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.NoiseTex;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import io.github.kortev.shootingstar.client.gfx.Timings;
import io.github.kortev.shootingstar.thunder.Lichtenberg;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Mjölnir's strike in the world, as everyone in range sees it. Over the target a storm winds up into a vortex, lit from
 * inside, while rings of light on the ground mark the zone; the call leaps from the hammer up into it; the stepped leader
 * feels its way down out of it in jumps while streamers rise from everything standing under it; then the return stroke,
 * the restrikes down the same channel, the channel breaking up into beads as it dies, spider lightning racing across the
 * cloud base, the scar burning out across the ground along its branches, and the arcs jumping. The light goes into an
 * HDR buffer depth-tested against the world, is bloomed and laid over the frame, and lights the ground; the stroke's
 * impact frames and the gloom under the storm are graded over the whole picture.
 */
public final class ThunderRender {
	private static final Target DEPTH = new Target(true, false);
	private static final Target FX = new Target(true, true);
	private static final Target COPY = new Target(false, false);
	private static final Fx BATCH = new Fx();
	/** How far off a strike is still drawn. */
	private static final double RANGE = 1600.0;
	/** Points round the warning rings. */
	private static final int RING_POINTS = 160;
	/** The storm's decks, top first: height over its base, reach (times the vortex radius) and density. */
	private static final float[] DECK_HEIGHT = {26.0F, 18.0F, 11.0F, 5.0F, 0.0F};
	private static final float[] DECK_REACH = {1.6F, 1.42F, 1.26F, 1.12F, 1.0F};
	private static final float[] DECK_DENSITY = {0.55F, 0.62F, 0.72F, 0.85F, 1.0F};
	/** The wall cloud's radius, times the vortex radius, and the segments of its curtain. */
	private static final double WALL_REACH = 0.2;
	private static final int CURTAIN_SEGMENTS = 48;
	/** Colours, as linear light. */
	private static final float[] ARC = {0.62F, 0.78F, 1.0F};
	private static final float[] CORE = {0.93F, 0.96F, 1.0F};
	private static final float[] LEADER = {0.62F, 0.5F, 1.0F};
	private static final float[] AFTERGLOW = {1.0F, 0.42F, 0.86F};
	/** What a flash fades the picture to: white for lightning, and the inside of the storm's cloud for the rise's end. */
	private static final float[] WHITE_FLASH = {0.9F, 0.95F, 1.0F};
	private static final float[] CLOUD_FLASH = {0.18F, 0.205F, 0.265F};

	/** A point light on the world (relative to the camera), as in Gungnir's light pass. */
	private record Light(float x, float y, float z, float range, float r, float g, float b, float wrap) {
		float weight() {
			float d2 = x * x + y * y + z * z;
			return (r + g + b) * range * range / (range * range + d2);
		}
	}

	private record Grade(int mode, float mix, float cx, float cy, float zoom, float chroma, float exposure, float flash, float glow,
			float[] flashColor) {
	}

	private ThunderRender() {
	}

	// --- set-up ------------------------------------------------------------------------------------

	/** Samples the ground round the warning rings when a strike is first seen. */
	public static void prepare(MinecraftClient client, ClientThunder thunder) {
		if (client.world == null) {
			return;
		}
		thunder.zoneHeights = ring(client.world, thunder.center, thunder.radius);
		thunder.scarEdgeHeights = ring(client.world, thunder.center, ThunderTimeline.scarRadius(thunder.radius));
	}

	/** The bolt has landed: samples the ground under the scar before the server's changes arrive, and grows the bolt. */
	public static void struck(MinecraftClient client, ClientThunder thunder) {
		if (client.world == null) {
			return;
		}
		if (thunder.zoneHeights == null) {
			prepare(client, thunder);
		}
		List<Lichtenberg.Segment> segments = thunder.figure().segments();
		float[] heights = new float[segments.size() * 2];
		for (int i = 0; i < segments.size(); i++) {
			Lichtenberg.Segment s = segments.get(i);
			heights[i * 2] = ground(client.world, thunder.center.x + s.x0(), thunder.center.z + s.z0(), thunder.center.y);
			heights[i * 2 + 1] = ground(client.world, thunder.center.x + s.x1(), thunder.center.z + s.z1(), thunder.center.y);
		}
		thunder.scarHeights = heights;
		path(thunder);
	}

	private static BoltPath path(ClientThunder thunder) {
		if (thunder.path == null) {
			thunder.path = BoltPath.grow(thunder.seed, thunder.wallBase(), thunder.center, thunder.radius);
		}
		return thunder.path;
	}

	private static float[] ring(ClientWorld world, Vec3d center, double radius) {
		float[] heights = new float[RING_POINTS];
		for (int i = 0; i < RING_POINTS; i++) {
			double a = Math.PI * 2.0 * i / RING_POINTS;
			heights[i] = ground(world, center.x + Math.cos(a) * radius, center.z + Math.sin(a) * radius, center.y);
		}
		return heights;
	}

	/** The top of the ground at a point: down through leaves, trunks and plants to what you would stand on. */
	private static float ground(ClientWorld world, double x, double z, double fallback) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
			return (float) fallback;
		}
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz) - 1;
		BlockPos.Mutable pos = new BlockPos.Mutable(bx, y, bz);
		for (int i = 0; i < 40 && y > world.getBottomY(); i++) {
			BlockState state = world.getBlockState(pos.set(bx, y, bz));
			if (state.isAir() || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)
					|| state.getCollisionShape(world, pos).isEmpty() && state.getFluidState().isEmpty()) {
				y--;
				continue;
			}
			break;
		}
		return y + 1.0F;
	}

	// --- the storm ---------------------------------------------------------------------------------

	/** How thick the storm over the target is at {@code t}: it gathers from the call and is spent soon after the stroke. */
	static double stormDensity(double t) {
		double gather = ThunderTimeline.smooth((t - ThunderTimeline.CALL) / 18.0);
		// Spent, it hangs over the aftermath and thins away over ten seconds, gone a little before the strike ends.
		double spent = ThunderTimeline.smooth((t - ThunderTimeline.STROKE - 20.0) / 180.0);
		return gather * (1.0 - spent);
	}

	/** How far the storm's eye has opened over the crater since the stroke: 0 before it, 1 at the end. */
	static double clearing(double t) {
		return ThunderTimeline.smooth((t - ThunderTimeline.STROKE - 10.0) / 150.0);
	}

	/** How fast the storm turns, in radians per tick, tightening as it winds up. */
	static double spin(double t) {
		// The integral of a spin rate that rises from 0.004 to 0.02 rad/tick over the gathering.
		double p = ThunderTimeline.clamp01((t - ThunderTimeline.CALL) / (ThunderTimeline.STROKE - ThunderTimeline.CALL));
		double span = ThunderTimeline.STROKE - ThunderTimeline.CALL;
		double before = 0.004 * t + 0.016 * span * p * p * p / 3.0;
		return t > ThunderTimeline.STROKE ? before + 0.02 * (t - ThunderTimeline.STROKE) * Math.exp(-(t - ThunderTimeline.STROKE) / 120.0)
				: before;
	}

	/**
	 * Lightning inside the storm: flashes at moments and places that only the seed decides, coming faster as the stroke
	 * nears. Returns the brightest one now: its x and z across the vortex (-1..1), how bright it is, and the tick it
	 * started on (which seeds the bolt drawn for it).
	 */
	static float[] stormFlash(ClientThunder thunder, double t) {
		float[] best = {0.0F, 0.0F, 0.0F, 0.0F};
		if (t < ThunderTimeline.FEED - 20) {
			return best;
		}
		int now = MathHelper.floor(t);
		for (int k = now - 4; k <= now; k++) {
			long h = mix(thunder.seed + k * 0x9E3779B97F4A7C15L);
			double roll = (h & 0xFFFF) / 65536.0;
			double p = ThunderTimeline.clamp01((k - ThunderTimeline.FEED) / (double) (ThunderTimeline.STROKE - ThunderTimeline.FEED));
			double after = k > ThunderTimeline.STROKE ? Math.exp(-(k - ThunderTimeline.STROKE) / 50.0) : 1.0;
			double rate = (0.05 + 0.35 * p * p) * after;
			if (roll >= rate) {
				continue;
			}
			double age = t - k;
			// Each flash flickers: a bright stroke, a dip, a second stroke, fading.
			double b = age < 1 ? 1.0 : age < 2 ? 0.35 : age < 3 ? 0.8 : Math.exp(-(age - 3) / 0.8) * 0.6;
			if (b > best[2]) {
				double a = ((h >>> 16) & 0xFFFF) / 65536.0 * Math.PI * 2.0;
				double r = Math.sqrt(((h >>> 32) & 0xFFFF) / 65536.0) * 0.75;
				best[0] = (float) (Math.cos(a) * r);
				best[1] = (float) (Math.sin(a) * r);
				best[2] = (float) b;
				best[3] = k;
			}
		}
		return best;
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** How much the storm darkens the world at {@code cam}, for the sky and fog colour (0..1). */
	public static float gloom(Vec3d cam, float tickDelta) {
		float gloom = 0.0F;
		for (ClientThunder thunder : ClientThunders.all()) {
			double t = thunder.time(tickDelta);
			double density = stormDensity(t);
			if (density < 0.01) {
				continue;
			}
			double reach = ThunderTimeline.vortexRadius(t, thunder.radius) * 1.4 + 40.0;
			double d = Math.hypot(cam.x - thunder.center.x, cam.z - thunder.center.z);
			gloom = Math.max(gloom, (float) (density * 0.35 * (1.0 - ThunderTimeline.smooth((d - reach * 0.5) / (reach * 0.8)))));
		}
		return gloom;
	}

	// --- frame -------------------------------------------------------------------------------------

	/**
	 * Whether this strike draws anything bright enough to want the light pass and the bloom at {@code t}: the call, the
	 * leader and the stroke with everything after it. Before and between them there are only the warning rings.
	 */
	private static boolean bright(ClientThunder thunder, double t) {
		if (thunder.callFrom != null && t >= ThunderTimeline.CALL && t < ThunderTimeline.CALL + 12) {
			return true;
		}
		if (t >= ThunderTimeline.INBOUND && t < ThunderTimeline.STROKE) {
			return true;
		}
		double e = t - ThunderTimeline.STROKE;
		return thunder.struck && e >= 0 && e < Math.max(90.0, thunder.figure().reach() / ThunderTimeline.SCAR_SPEED + 90.0);
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = context.world();
		if (!Shaders.thunderReady() || world == null || client.player == null
				|| ClientThunders.all().isEmpty() && ThunderDust.isEmpty()) {
			return;
		}
		float tickDelta = context.tickCounter().getTickDelta(false);
		Vec3d cam = context.camera().getPos();
		List<ClientThunder> live = new ArrayList<>();
		for (ClientThunder thunder : ClientThunders.all()) {
			if (Math.hypot(cam.x - thunder.center.x, cam.z - thunder.center.z) < RANGE) {
				live.add(thunder);
			}
		}
		if (live.isEmpty() && ThunderDust.isEmpty()) {
			return;
		}
		Matrix4f view = new Matrix4f(context.positionMatrix());
		Matrix4f proj = new Matrix4f(context.projectionMatrix());
		Framebuffer main = client.getFramebuffer();
		int w = main.textureWidth;
		int h = main.textureHeight;
		Vector3f right = new Vector3f(view.m00(), view.m10(), view.m20());
		Vector3f up = new Vector3f(view.m01(), view.m11(), view.m21());
		float daylight = daylight(world, tickDelta);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		edge(client, cam, view, proj, main, w, h, tickDelta);

		// The world goes dark under the storm, then the storm itself goes over the sky.
		Timings.begin("thunder.storm");
		main.beginWrite(true);
		float gloom = gloom(cam, tickDelta);
		if (gloom > 0.01F) {
			darken(gloom);
		}
		for (ClientThunder thunder : live) {
			drawStorm(thunder, thunder.time(tickDelta), cam, view, proj, daylight);
		}
		Timings.end();

		List<Light> lights = lights(live, tickDelta, cam);
		boolean bright = !lights.isEmpty();
		for (ClientThunder thunder : live) {
			bright |= bright(thunder, thunder.time(tickDelta));
		}
		// The depth of the world as drawn, for the grading's depth effects, the light pass and the dust.
		DEPTH.ensure(w, h);
		DEPTH.copyDepthFrom(main);
		Timings.begin("thunder.dust");
		main.beginWrite(true);
		ThunderDust.render(cam, view, proj, right, up, tickDelta, DEPTH.depth(), w, h, projA(proj), daylight,
				strokeFlash(live, tickDelta));
		Timings.end();
		if (!bright) {
			// Only the warning rings: straight onto the picture, without the light pass and the bloom, which cost more than
			// everything else here put together and have nothing to work on.
			Timings.begin("thunder.rings");
			main.beginWrite(true);
			for (ClientThunder thunder : live) {
				drawLight(world, thunder, thunder.time(tickDelta), cam, view, proj, right, up, tickDelta);
			}
			Timings.end();
		} else {
			Timings.begin("thunder.light");
			if (!lights.isEmpty()) {
				COPY.ensure(w, h);
				COPY.copyColorFrom(main);
			}
			FX.ensure(w, h);
			FX.copyDepthFrom(main);
			FX.bind();
			RenderSystem.colorMask(true, true, true, true);
			RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
			RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
			if (!lights.isEmpty()) {
				drawLights(lights, view, proj, w, h, Math.max(0.3F, daylight));
			}
			for (ClientThunder thunder : live) {
				drawLight(world, thunder, thunder.time(tickDelta), cam, view, proj, right, up, tickDelta);
			}
			Timings.end();
			Timings.begin("thunder.bloom");
			Post.begin();
			// No streak: lightning wants a round glow, and the streak is more than half of the bloom's passes.
			int[] bloom = Post.bloom(FX.color(), w, h, 0.9F, false);
			main.beginWrite(true);
			RenderSystem.enableBlend();
			RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
					GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
			RenderSystem.setShaderTexture(0, FX.color());
			RenderSystem.setShaderTexture(1, bloom[0]);
			RenderSystem.setShaderTexture(2, bloom[1]);
			RenderSystem.setShaderTexture(3, bloom[2]);
			// Lightning wants a wide, photographic glow round a hard white core.
			Shaders.set(Shaders.fxcomp, "StreakStrength", 0.0F);
			Shaders.set(Shaders.fxcomp, "Dirt", 0.25F);
			Shaders.set(Shaders.fxcomp, "BloomStrength", 0.9F);
			Shaders.set(Shaders.fxcomp, "WideStrength", 0.8F);
			Post.quad(Shaders.fxcomp);
			RenderSystem.disableBlend();
			Timings.end();
		}

		Timings.begin("thunder.grade");
		Grade grade = grade(client, live, tickDelta, cam, view, proj);
		if (grade != null) {
			COPY.ensure(w, h);
			COPY.copyColorFrom(main);
			main.beginWrite(true);
			Post.begin();
			RenderSystem.setShaderTexture(0, COPY.color());
			RenderSystem.setShaderTexture(1, DEPTH.depth());
			Shaders.setInt(Shaders.thunder, "Mode", grade.mode());
			Shaders.set(Shaders.thunder, "Mix", grade.mix());
			Shaders.set(Shaders.thunder, "Center", grade.cx(), grade.cy());
			Shaders.set(Shaders.thunder, "ScreenSize", w, h);
			Shaders.set(Shaders.thunder, "Time", (float) (world.getTime() + tickDelta));
			Shaders.set(Shaders.thunder, "ProjA", projA(proj));
			Shaders.set(Shaders.thunder, "ProjB", proj.m32());
			Shaders.set(Shaders.thunder, "Zoom", grade.zoom());
			Shaders.set(Shaders.thunder, "Chroma", grade.chroma());
			Shaders.set(Shaders.thunder, "Exposure", grade.exposure());
			Shaders.set(Shaders.thunder, "Flash", grade.flash());
			Shaders.set(Shaders.thunder, "FlashColor", grade.flashColor()[0], grade.flashColor()[1], grade.flashColor()[2]);
			Shaders.set(Shaders.thunder, "Glow", grade.glow());
			Post.quad(Shaders.thunder);
		}
		Timings.end();

		for (int i = 0; i < 4; i++) {
			RenderSystem.setShaderTexture(i, 0);
		}
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		main.beginWrite(true);
	}

	/** Multiplies everything drawn so far by a dim storm light, bluer the darker it gets. */
	private static void darken(float gloom) {
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.SRC_COLOR,
				GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
		int r = Math.round(255.0F * (1.0F - gloom * 0.95F));
		int g = Math.round(255.0F * (1.0F - gloom * 0.88F));
		int b = Math.round(255.0F * (1.0F - gloom * 0.7F));
		BufferBuilder q = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		q.vertex(-1.0F, -1.0F, 0.0F).color(r, g, b, 255);
		q.vertex(1.0F, -1.0F, 0.0F).color(r, g, b, 255);
		q.vertex(1.0F, 1.0F, 0.0F).color(r, g, b, 255);
		q.vertex(-1.0F, 1.0F, 0.0F).color(r, g, b, 255);
		Post.draw(q, GameRenderer.getPositionColorProgram(), new Matrix4f(), new Matrix4f());
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
	}

	/**
	 * The storm over the target, drawn top down: five decks of cloud, the higher ones wider and slower, so the storm
	 * covers the sky and has depth wherever it is seen from; then the wall cloud, a turning column of cloud hanging under
	 * its middle (a disc at its foot, a curtain round it up to the decks), which the bolt comes down out of. All of it lit
	 * from inside by the storm's lightning and from below by the stroke.
	 */
	private static void drawStorm(ClientThunder thunder, double t, Vec3d cam, Matrix4f view, Matrix4f proj, float daylight) {
		double density = stormDensity(t);
		if (density < 0.01) {
			return;
		}
		double radius = ThunderTimeline.vortexRadius(t, thunder.radius);
		float[] flash = stormFlash(thunder, t);
		double e = t - ThunderTimeline.STROKE;
		float stroke = thunder.struck && e >= 0 ? (float) ThunderTimeline.channelFlash(e) * 3.0F : 0.0F;
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
		float fogEnd = Math.max(RenderSystem.getShaderFogEnd(), 64.0F);
		float time = (float) (t % 100000.0);
		RenderSystem.setShaderTexture(0, NoiseTex.get());
		for (int layer = 0; layer < DECK_HEIGHT.length; layer++) {
			double reach = radius * DECK_REACH[layer];
			BufferBuilder deck = disc(thunder.center, thunder.cloudBase + DECK_HEIGHT[layer], reach, cam);
			Shaders.set(Shaders.vortex, "Time", time);
			Shaders.set(Shaders.vortex, "Spin", (float) (spin(t) * (0.7 + 0.12 * layer) % (Math.PI * 200.0)));
			Shaders.set(Shaders.vortex, "Density", (float) density * DECK_DENSITY[layer]);
			Shaders.set(Shaders.vortex, "Layer", (float) (layer + (thunder.seed & 7)));
			Shaders.set(Shaders.vortex, "Daylight", daylight);
			// The flash and the eye stay where they are over the ground, whatever the deck's size.
			float scale = 1.0F / DECK_REACH[layer];
			Shaders.set(Shaders.vortex, "Flash", flash[0] * scale, flash[1] * scale, flash[2] * 2.5F);
			Shaders.set(Shaders.vortex, "FlashFalloff", 11.0F);
			Shaders.set(Shaders.vortex, "Detail", 1.0F);
			Shaders.set(Shaders.vortex, "Stroke", stroke * scale);
			// Spent, the storm opens over the crater, the sky showing through the hole.
			Shaders.set(Shaders.vortex, "Eye", (float) (0.05 + 0.3 * clearing(t)) * scale);
			Shaders.set(Shaders.vortex, "FadeEnd", fogEnd);
			Post.draw(deck, Shaders.vortex, view, proj);
		}

		// The wall cloud: it lowers out of the storm as the storm winds up, and draws back up into it once the bolt is spent.
		double lower = ThunderTimeline.smooth((t - ThunderTimeline.DRAW) / (ThunderTimeline.INBOUND - ThunderTimeline.DRAW))
				* (1.0 - ThunderTimeline.smooth((t - ThunderTimeline.STROKE - 10.0) / 60.0));
		double drop = thunder.wallDrop() * lower;
		if (drop > 0.5) {
			double wall = radius * WALL_REACH;
			float near = (float) Math.exp(-(flash[0] * flash[0] + flash[1] * flash[1]) * 6.0);
			Shaders.set(Shaders.wall, "Time", time);
			Shaders.set(Shaders.wall, "Spin", (float) (spin(t) * 1.8 % (Math.PI * 200.0)));
			Shaders.set(Shaders.wall, "Density", (float) density);
			Shaders.set(Shaders.wall, "Daylight", daylight);
			Shaders.set(Shaders.wall, "Flash", flash[2] * near * 2.0F);
			Shaders.set(Shaders.wall, "Stroke", stroke);
			Shaders.set(Shaders.wall, "FadeEnd", fogEnd);
			BufferBuilder column = curtain(thunder.center, thunder.cloudBase - drop, thunder.cloudBase + 3.0, wall, cam);
			Post.draw(column, Shaders.wall, view, proj);
			BufferBuilder foot = disc(thunder.center, thunder.cloudBase - drop, wall * 1.15, cam);
			Shaders.set(Shaders.vortex, "Spin", (float) (spin(t) * 1.8 % (Math.PI * 200.0)));
			Shaders.set(Shaders.vortex, "Density", (float) density);
			Shaders.set(Shaders.vortex, "Layer", (float) (11 + (thunder.seed & 7)));
			Shaders.set(Shaders.vortex, "Flash", 0.0F, 0.0F, flash[2] * near * 1.5F);
			Shaders.set(Shaders.vortex, "FlashFalloff", 11.0F);
			Shaders.set(Shaders.vortex, "Detail", 1.0F);
			Shaders.set(Shaders.vortex, "Stroke", stroke * 1.5F);
			// The channel comes down through the middle of it.
			Shaders.set(Shaders.vortex, "Eye", 0.09F);
			Post.draw(foot, Shaders.vortex, view, proj);
		}
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
	}

	/** A horizontal square at height {@code y} round {@code center}, {@code radius} to each side, UV -1..1 across it. */
	private static BufferBuilder disc(Vec3d center, double y, double radius, Vec3d cam) {
		float x0 = (float) (center.x - radius - cam.x);
		float x1 = (float) (center.x + radius - cam.x);
		float z0 = (float) (center.z - radius - cam.z);
		float z1 = (float) (center.z + radius - cam.z);
		float yy = (float) (y - cam.y);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		b.vertex(x0, yy, z0).texture(-1.0F, -1.0F).color(255, 255, 255, 255);
		b.vertex(x1, yy, z0).texture(1.0F, -1.0F).color(255, 255, 255, 255);
		b.vertex(x1, yy, z1).texture(1.0F, 1.0F).color(255, 255, 255, 255);
		b.vertex(x0, yy, z1).texture(-1.0F, 1.0F).color(255, 255, 255, 255);
		return b;
	}

	/** An open cylinder round {@code center} from {@code y0} up to {@code y1}: UV x once round it, y 0 at its foot. */
	private static BufferBuilder curtain(Vec3d center, double y0, double y1, double radius, Vec3d cam) {
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		float bottom = (float) (y0 - cam.y);
		float top = (float) (y1 - cam.y);
		for (int i = 0; i < CURTAIN_SEGMENTS; i++) {
			double a0 = Math.PI * 2.0 * i / CURTAIN_SEGMENTS;
			double a1 = Math.PI * 2.0 * (i + 1) / CURTAIN_SEGMENTS;
			float x0 = (float) (center.x + Math.cos(a0) * radius - cam.x);
			float z0 = (float) (center.z + Math.sin(a0) * radius - cam.z);
			float x1 = (float) (center.x + Math.cos(a1) * radius - cam.x);
			float z1 = (float) (center.z + Math.sin(a1) * radius - cam.z);
			float u0 = (float) i / CURTAIN_SEGMENTS;
			float u1 = (float) (i + 1) / CURTAIN_SEGMENTS;
			b.vertex(x0, bottom, z0).texture(u0, 0.0F).color(255, 255, 255, 255);
			b.vertex(x1, bottom, z1).texture(u1, 0.0F).color(255, 255, 255, 255);
			b.vertex(x1, top, z1).texture(u1, 1.0F).color(255, 255, 255, 255);
			b.vertex(x0, top, z0).texture(u0, 1.0F).color(255, 255, 255, 255);
		}
		return b;
	}

	// --- light -------------------------------------------------------------------------------------

	private static void drawLight(ClientWorld world, ClientThunder thunder, double t, Vec3d cam, Matrix4f view, Matrix4f proj,
			Vector3f right, Vector3f up, float tickDelta) {
		double e = t - ThunderTimeline.STROKE;
		if (t < ThunderTimeline.STROKE) {
			rings(thunder, t, cam, view, proj, right, up);
		}
		if (thunder.callFrom != null && t >= ThunderTimeline.CALL && t < ThunderTimeline.CALL + 12) {
			call(thunder, t - ThunderTimeline.CALL, cam, view, proj, right, up);
		}
		if (t >= ThunderTimeline.INBOUND && t < ThunderTimeline.STROKE) {
			leader(thunder, t, cam, view, proj, right, up);
			streamers(world, thunder, t, cam, view, proj, right, up, tickDelta);
		}
		if (e < 60.0) {
			stormBolts(world, thunder, t, cam, view, proj, right, up);
		}
		if (thunder.struck && e >= 0) {
			stroke(thunder, e, cam, view, proj, right, up);
			scar(thunder, e, cam, view, proj, right, up);
			arcs(thunder, e, cam, view, proj, right, up);
			crawlers(thunder, t, e, cam, view, proj, right, up);
		}
	}

	/**
	 * Rings of light on the ground round the zone (where nothing will survive) and fainter round the edge of the scar
	 * (where the arcs reach), with a glow at the target: a warning everyone can see, quickening as the stroke nears.
	 */
	private static void rings(ClientThunder thunder, double t, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		double on = ThunderTimeline.smooth((t - ThunderTimeline.CALL) / 20.0);
		if (on <= 0.01 || thunder.zoneHeights == null || thunder.scarEdgeHeights == null) {
			return;
		}
		double p = ThunderTimeline.clamp01((t - ThunderTimeline.CALL) / (ThunderTimeline.STROKE - ThunderTimeline.CALL));
		// The pulse quickens from once every two seconds to five times a second.
		double phase = 0.3 * t + 2.4 * p * p * t * 0.25;
		float pulse = (float) (0.55 + 0.45 * Math.sin(phase));
		ring(thunder, thunder.zoneHeights, thunder.radius, cam, view, proj, right, up, (float) (on * (0.6 + 0.6 * pulse)), 1.0F);
		ring(thunder, thunder.scarEdgeHeights, ThunderTimeline.scarRadius(thunder.radius), cam, view, proj, right, up,
				(float) (on * 0.35 * (0.6 + 0.4 * pulse)), 0.6F);
		Fx glow = BATCH.begin(Fx.BLOB, 1.0F, view, proj, right, up);
		Vector3f at = rel(thunder.center.x, thunder.center.y + 1.0, thunder.center.z, cam);
		glow.sprite(at, (float) (3.0 + 5.0 * p), 0.0F, Fx.argb(ARC[0], ARC[1], ARC[2], (float) on * pulse));
		glow.end(true, (float) (1.0 + 2.5 * p * p));
	}

	private static void ring(ClientThunder thunder, float[] heights, double radius, Vec3d cam, Matrix4f view, Matrix4f proj,
			Vector3f right, Vector3f up, float alpha, float width) {
		Fx line = BATCH.begin(Fx.LINE, 0.0F, view, proj, right, up);
		Vector3f eye = new Vector3f();
		int argb = Fx.argb(ARC[0], ARC[1], ARC[2], alpha);
		for (int i = 0; i < RING_POINTS; i++) {
			int j = (i + 1) % RING_POINTS;
			double a0 = Math.PI * 2.0 * i / RING_POINTS;
			double a1 = Math.PI * 2.0 * j / RING_POINTS;
			Vector3f p0 = rel(thunder.center.x + Math.cos(a0) * radius, heights[i] + 0.25, thunder.center.z + Math.sin(a0) * radius, cam);
			Vector3f p1 = rel(thunder.center.x + Math.cos(a1) * radius, heights[j] + 0.25, thunder.center.z + Math.sin(a1) * radius, cam);
			// Wide enough to read as a line from far off.
			float w = width * (0.18F + 0.0035F * p0.length());
			line.beam(p0, p1, eye, w, argb, argb);
		}
		// Soft: drawn straight onto the picture most of the time, where anything brighter clips to white and reads as
		// lightning running along the ground.
		line.end(true, 1.3F);
	}

	/**
	 * While a shot holds the shooter's camera far from them, it can see past the edge of the world the game has loaded
	 * round them (their render distance), where the ground stops in a hard square: there the ground fades into the murk
	 * the void shows, over the last stretch before the edge.
	 */
	private static void edge(MinecraftClient client, Vec3d cam, Matrix4f view, Matrix4f proj, Framebuffer main, int w, int h,
			float tickDelta) {
		ClientThunder thunder = ClientThunders.cinematic();
		if (thunder == null || !ClientThunders.shotActive(thunder, thunder.time(tickDelta))) {
			return;
		}
		Vec3d player = client.player.getCameraPosVec(tickDelta);
		if (player.squaredDistanceTo(cam) < 16.0 * 16.0) {
			return;
		}
		DEPTH.ensure(w, h);
		DEPTH.copyDepthFrom(main);
		COPY.ensure(w, h);
		COPY.copyColorFrom(main);
		main.beginWrite(true);
		Post.begin();
		RenderSystem.setShaderTexture(0, COPY.color());
		RenderSystem.setShaderTexture(1, DEPTH.depth());
		Shaders.set(Shaders.edge, "InvProj", new Matrix4f(proj).invert());
		Shaders.set(Shaders.edge, "InvView", new Matrix4f(view).invert());
		Shaders.set(Shaders.edge, "Player", (float) (player.x - cam.x), (float) (player.y - cam.y), (float) (player.z - cam.z));
		Shaders.set(Shaders.edge, "Radius", client.options.getClampedViewDistance() * 16.0F);
		float[] murk = RenderSystem.getShaderFogColor();
		Shaders.set(Shaders.edge, "Murk", murk[0], murk[1], murk[2]);
		Post.quad(Shaders.edge);
		RenderSystem.setShaderTexture(1, 0);
	}

	/** The call: a bolt leaping from the raised hammer up into the heart of the storm over the target. */
	private static void call(ClientThunder thunder, double since, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		double b = since < 1.0 ? 1.0 : since < 2.0 ? 0.4 : since < 3.0 ? 0.9 : Math.exp(-(since - 3.0) / 2.0) * 0.8;
		Vec3d from = thunder.callFrom;
		MinecraftClient client = MinecraftClient.getInstance();
		if (thunder.mine && !client.gameRenderer.getCamera().isThirdPerson()) {
			// From the head of the hammer where the shooter sees it held up, top right, whichever way they are looking.
			Vector3f forward = new Vector3f(right).cross(up).negate();
			from = cam.add(forward.x * 1.1 + up.x * 0.55 + right.x * 0.35, forward.y * 1.1 + up.y * 0.55 + right.y * 0.35,
					forward.z * 1.1 + up.z * 0.55 + right.z * 0.35);
		}
		Vec3d to = thunder.top().add(0, 4, 0);
		Random random = new Random(thunder.seed * 7 + (long) (since / 2.0));
		List<Vec3d> channel = BoltPath.jagged(from, to, 0.12, 7, random);
		List<List<Vec3d>> forks = new ArrayList<>();
		for (int f = 0; f < 7; f++) {
			Vec3d root = channel.get(10 + random.nextInt(channel.size() - 20));
			Vec3d dir = to.subtract(from).normalize().add(random.nextGaussian() * 0.5, random.nextGaussian() * 0.3, random.nextGaussian() * 0.5)
					.normalize();
			forks.add(BoltPath.jagged(root, root.add(dir.multiply(from.distanceTo(to) * (0.05 + 0.08 * random.nextDouble()))), 0.3, 4, random));
		}
		// Thinner near the camera: it leaves the hammer right in front of the shooter's eyes.
		bolt(channel, forks, cam, view, proj, right, up, (float) b * 1.2F, 2.8F, 0.32F, ARC, 1.0F, 24.0F);
		// Where it leaves the hammer, and where it goes into the storm.
		Fx ends = BATCH.begin(Fx.BLOB, 1.0F, view, proj, right, up);
		// A spark on the hammer's head, a few degrees across however near it is.
		float head = (float) Math.min(1.6, from.distanceTo(cam) * 0.06);
		ends.sprite(rel(from.x, from.y, from.z, cam), head, 0.0F, Fx.argb(CORE[0], CORE[1], CORE[2], (float) b));
		ends.sprite(rel(to.x, to.y, to.z, cam), 9.0F, 0.0F, Fx.argb(ARC[0], ARC[1], ARC[2], (float) b * 0.6F));
		ends.end(true, 6.0F);
	}

	/** The stepped leader: dim and violet, a jump at a time, its branches feeling out round it. */
	private static void leader(ClientThunder thunder, double t, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		BoltPath path = path(thunder);
		double reach = ThunderTimeline.leaderReach(t);
		if (reach <= 0.0) {
			return;
		}
		// Each jump flashes as it is taken.
		double step = Math.floor(t * 1.0);
		float jump = (float) (0.55 + 0.45 * Math.exp(-(t - step) * 2.0));
		List<Vec3d> channel = reveal(path.channel, path, reach);
		List<List<Vec3d>> forks = new ArrayList<>();
		for (BoltPath.Branch branch : path.branches) {
			if (path.depth(branch.points().get(0).y) < reach) {
				forks.add(reveal(branch.points(), path, reach));
			}
		}
		bolt(channel, forks, cam, view, proj, right, up, 0.7F * jump, 2.0F, 0.22F, LEADER, 0.85F);
		if (channel.size() > 1) {
			Vec3d tip = channel.get(channel.size() - 1);
			Fx glow = BATCH.begin(Fx.BLOB, 1.0F, view, proj, right, up);
			glow.sprite(rel(tip.x, tip.y, tip.z, cam), 5.0F, 0.0F, Fx.argb(LEADER[0], LEADER[1], LEADER[2], jump));
			glow.end(true, 6.0F);
		}
	}

	private static List<Vec3d> reveal(List<Vec3d> points, BoltPath path, double reach) {
		List<Vec3d> out = new ArrayList<>();
		for (Vec3d p : points) {
			if (path.depth(p.y) > reach) {
				break;
			}
			out.add(p);
		}
		return out;
	}

	/**
	 * Streamers: as the leader nears, sparks reach up off everything under the storm that stands proud of the ground
	 * (the target itself, and every creature in the zone), longer and brighter the closer the leader is.
	 */
	private static void streamers(ClientWorld world, ClientThunder thunder, double t, Vec3d cam, Matrix4f view, Matrix4f proj,
			Vector3f right, Vector3f up, float tickDelta) {
		double p = ThunderTimeline.clamp01((t - ThunderTimeline.INBOUND) / (ThunderTimeline.STROKE - ThunderTimeline.INBOUND));
		if (p < 0.15) {
			return;
		}
		long frame = (long) (t / 2.0);
		List<Vec3d> roots = new ArrayList<>();
		roots.add(thunder.center);
		double zone = thunder.radius * 1.05;
		for (Entity entity : world.getEntities()) {
			if (!(entity instanceof LivingEntity) || !entity.isAlive() || roots.size() > 48) {
				continue;
			}
			double dx = entity.getX() - thunder.center.x;
			double dz = entity.getZ() - thunder.center.z;
			if (dx * dx + dz * dz < zone * zone && entity.squaredDistanceTo(cam) < 200.0 * 200.0) {
				roots.add(entity.getLerpedPos(tickDelta).add(0.0, entity.getHeight(), 0.0));
			}
		}
		List<List<Vec3d>> sparks = new ArrayList<>();
		List<Float> widths = new ArrayList<>();
		for (int i = 0; i < roots.size(); i++) {
			Vec3d root = roots.get(i);
			Random random = new Random(thunder.seed + i * 977L + frame * 31L);
			if (random.nextDouble() > 0.35 + 0.6 * p) {
				continue;
			}
			double length = (i == 0 ? 6.0 + 22.0 * p * p : 0.8 + 4.5 * p) * (0.6 + 0.6 * random.nextDouble());
			Vec3d tip = root.add(random.nextGaussian() * length * 0.25, length, random.nextGaussian() * length * 0.25);
			sparks.add(BoltPath.jagged(root, tip, 0.35, 3, random));
			widths.add(i == 0 ? 1.0F : 0.4F);
		}
		float a = (float) (0.4 + 0.6 * p);
		Fx glow = BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
		for (int i = 0; i < sparks.size(); i++) {
			chain(glow, sparks.get(i), cam, 0.6F * widths.get(i), Fx.argb(LEADER[0], LEADER[1], LEADER[2], a * 0.6F));
		}
		glow.end(true, 2.5F);
		Fx core = BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
		for (int i = 0; i < sparks.size(); i++) {
			chain(core, sparks.get(i), cam, 0.1F * widths.get(i), Fx.argb(CORE[0], CORE[1], CORE[2], a));
		}
		core.end(true, 6.0F);
	}

	/**
	 * The return stroke and what follows it: the channel blazing white with its branches, again with each restrike, then
	 * glowing violet as it cools and breaking up into beads of light; and a blinding glow on the ground where it landed.
	 * The dust the thunderclap throws up is {@link ThunderDust}'s.
	 */
	private static void stroke(ClientThunder thunder, double e, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		BoltPath path = path(thunder);
		float b = (float) ThunderTimeline.channelFlash(e);
		double scale = thunder.radius / 64.0;
		if (b > 0.02F) {
			List<List<Vec3d>> forks = new ArrayList<>();
			if (e < 8.0) {
				for (BoltPath.Branch branch : path.branches) {
					forks.add(branch.points());
				}
			}
			bolt(path.channel, forks, cam, view, proj, right, up, b, (float) (3.0 + 4.0 * scale), (float) (0.55 + 0.6 * scale), ARC,
					e < 8.0 ? (float) Math.exp(-e / 2.0) : 0.0F);
			Fx flash = BATCH.begin(Fx.BLOB, 1.0F, view, proj, right, up);
			flash.sprite(rel(thunder.center.x, thunder.center.y + 2.0, thunder.center.z, cam), (float) (thunder.radius * 0.35), 0.0F,
					Fx.argb(CORE[0], CORE[1], CORE[2], 1.0F));
			flash.end(true, 18.0F * b);
		}
		// The channel cooling: violet, thinner, breaking into beads.
		if (e > 1.5 && e < 70.0) {
			float cool = (float) (Math.exp(-(e - 1.5) / 14.0));
			if (e < 14.0) {
				Fx glow = BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
				chain(glow, path.channel, cam, (float) (1.2 + 1.5 * scale), Fx.argb(AFTERGLOW[0], AFTERGLOW[1], AFTERGLOW[2], cool));
				glow.end(true, 2.0F);
			} else {
				Fx beads = BATCH.begin(Fx.BLOB, 0.0F, view, proj, right, up);
				float fade = (float) Math.exp(-(e - 14.0) / 16.0);
				for (int i = 0; i < path.channel.size(); i += 2) {
					long h = mix(thunder.seed + i * 131L);
					double on = (h & 0xFF) / 255.0;
					if (on < 0.35) {
						continue;
					}
					Vec3d q = path.channel.get(i);
					float flicker = (float) (0.7 + 0.3 * Math.sin(e * 0.9 + i));
					beads.sprite(rel(q.x, q.y, q.z, cam), (float) (0.9 + 1.4 * scale * on), 0.0F,
							Fx.argb(AFTERGLOW[0], AFTERGLOW[1], AFTERGLOW[2], fade * flicker * (float) on));
				}
				beads.end(true, 3.0F);
			}
		}
	}

	/**
	 * The scar burning out across the ground along the figure's branches: white at the burning front, electric blue
	 * behind it, fading as the fulgurite left in the ground takes over the glow.
	 */
	private static void scar(ClientThunder thunder, double e, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		float[] heights = thunder.scarHeights;
		if (heights == null) {
			return;
		}
		double front = ThunderTimeline.scarFront(e);
		Lichtenberg figure = thunder.figure();
		if (e > figure.reach() / ThunderTimeline.SCAR_SPEED + 90.0) {
			return;
		}
		List<Lichtenberg.Segment> segments = figure.segments();
		Vector3f eye = new Vector3f();
		Vector3f a = new Vector3f();
		Vector3f b = new Vector3f();
		for (int pass = 0; pass < 2; pass++) {
			Fx fx = BATCH.begin(Fx.LINE, 0.0F, view, proj, right, up);
			for (int i = 0; i < segments.size(); i++) {
				Lichtenberg.Segment s = segments.get(i);
				if (s.from() > front) {
					continue;
				}
				double since = (front - s.to()) / ThunderTimeline.SCAR_SPEED;
				float hot = since < 0 ? 1.0F : (float) Math.exp(-since / 3.0);
				float blue = (float) Math.exp(-Math.max(0.0, since) / 30.0);
				// The finest branches go out first: thousands of them, and too thin to see once cooled.
				if (blue < 0.02F || since > 20.0 && s.width() < 0.6F) {
					continue;
				}
				double t1 = Math.min(1.0, (front - s.from()) / Math.max(1.0E-3, s.to() - s.from()));
				float x1 = (float) MathHelper.lerp(t1, s.x0(), s.x1());
				float z1 = (float) MathHelper.lerp(t1, s.z0(), s.z1());
				float y1 = (float) MathHelper.lerp(t1, heights[i * 2], heights[i * 2 + 1]);
				a.set((float) (thunder.center.x + s.x0() - cam.x), (float) (heights[i * 2] + 0.2 - cam.y),
						(float) (thunder.center.z + s.z0() - cam.z));
				b.set((float) (thunder.center.x + x1 - cam.x), (float) (y1 + 0.2 - cam.y), (float) (thunder.center.z + z1 - cam.z));
				float width = 0.25F + s.width() * 0.35F;
				if (pass == 0) {
					int c = Fx.argb(ARC[0], ARC[1], ARC[2], blue * 0.7F);
					fx.beam(a, b, eye, width * 2.2F, c, c);
				} else {
					int c = Fx.argb(CORE[0], CORE[1], CORE[2], Math.max(hot, 0.25F * blue));
					fx.beam(a, b, eye, width * 0.45F, c, c);
				}
			}
			fx.end(true, pass == 0 ? 2.0F : 5.0F);
		}
	}

	/** The arcs: each crackles from where it left the bolt to whatever it hit, for a few ticks, re-forking as it does. */
	private static void arcs(ClientThunder thunder, double e, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up) {
		for (ClientThunder.Arc arc : thunder.arcs) {
			double since = e - arc.delay();
			if (since < 0.0 || since >= 7.0) {
				continue;
			}
			float b = new float[] {1.0F, 0.35F, 1.0F, 0.8F, 0.4F, 0.6F, 0.2F}[(int) since];
			Random random = new Random(arc.seed() + (long) (since / 2.0) * 7919L);
			List<Vec3d> channel = BoltPath.jagged(arc.from(), arc.to(), 0.22, 6, random);
			List<List<Vec3d>> forks = new ArrayList<>();
			for (int f = 0; f < 3; f++) {
				Vec3d root = channel.get(4 + random.nextInt(channel.size() - 8));
				Vec3d dir = arc.to().subtract(arc.from()).normalize().add(random.nextGaussian() * 0.7, random.nextGaussian() * 0.5,
						random.nextGaussian() * 0.7).normalize();
				forks.add(BoltPath.jagged(root, root.add(dir.multiply(arc.from().distanceTo(arc.to()) * 0.12)), 0.35, 3, random));
			}
			bolt(channel, forks, cam, view, proj, right, up, b, 1.0F, 0.14F, ARC, 0.7F);
		}
	}

	/** Spider lightning: the storm's leftover charge racing out across its own base for a while after the stroke. */
	private static void crawlers(ClientThunder thunder, double t, double e, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right,
			Vector3f up) {
		if (e < 3.0 || e > 90.0) {
			return;
		}
		double radius = ThunderTimeline.vortexRadius(t, thunder.radius) * 0.85;
		Vec3d top = thunder.top().add(0, -2, 0);
		for (int i = 0; i < 6; i++) {
			double start = 3.0 + i * 13.0 + (mix(thunder.seed + i) & 7);
			double since = e - start;
			if (since < 0.0 || since > 12.0) {
				continue;
			}
			Random random = new Random(thunder.seed * 13 + i);
			double a = random.nextDouble() * Math.PI * 2.0;
			Vec3d from = top.add(random.nextGaussian() * radius * 0.15, 0, random.nextGaussian() * radius * 0.15);
			Vec3d to = from.add(Math.cos(a) * radius * 0.9, random.nextGaussian() * 3.0, Math.sin(a) * radius * 0.9);
			List<Vec3d> channel = flatten(BoltPath.jagged(from, to, 0.3, 6, random), from.y);
			List<List<Vec3d>> forks = new ArrayList<>();
			for (int f = 0; f < 5; f++) {
				Vec3d root = channel.get(6 + random.nextInt(channel.size() - 12));
				double fa = a + random.nextGaussian() * 0.9;
				Vec3d tip = root.add(Math.cos(fa) * radius * 0.3, 0, Math.sin(fa) * radius * 0.3);
				forks.add(flatten(BoltPath.jagged(root, tip, 0.35, 4, random), from.y));
			}
			// It races out over three ticks, then flickers away.
			double out = Math.min(1.0, since / 3.0);
			List<Vec3d> shown = channel.subList(0, Math.max(2, (int) (channel.size() * out)));
			float b = (float) (since < 3.0 ? 1.0 : Math.exp(-(since - 3.0) / 2.5) * (0.6 + 0.4 * Math.sin(since * 3.1)));
			bolt(shown, out >= 1.0 ? forks : List.of(), cam, view, proj, right, up, b, 1.6F, 0.2F, ARC, 0.6F);
		}
	}

	/**
	 * The bolts of the storm's own lightning, one for each flash in it: a crawler racing across its base from where the
	 * flash is; and one flash in four a bolt down to the ground somewhere out under the storm's edge, beyond the zone.
	 */
	private static void stormBolts(ClientWorld world, ClientThunder thunder, double t, Vec3d cam, Matrix4f view, Matrix4f proj,
			Vector3f right, Vector3f up) {
		float[] flash = stormFlash(thunder, t);
		double density = stormDensity(t);
		if (flash[2] < 0.25F || density < 0.3) {
			return;
		}
		Random random = new Random(thunder.seed * 31L + (long) flash[3]);
		double radius = ThunderTimeline.vortexRadius(t, thunder.radius);
		Vec3d at = thunder.top().add(flash[0] * radius, -1.5, flash[1] * radius);
		double a = random.nextDouble() * Math.PI * 2.0;
		double length = radius * (0.25 + 0.35 * random.nextDouble());
		Vec3d to = at.add(Math.cos(a) * length, random.nextGaussian() * 2.0, Math.sin(a) * length);
		List<Vec3d> channel = flatten(BoltPath.jagged(at, to, 0.3, 6, random), at.y);
		List<List<Vec3d>> forks = new ArrayList<>();
		for (int f = 0; f < 3; f++) {
			Vec3d root = channel.get(6 + random.nextInt(channel.size() - 12));
			double fa = a + random.nextGaussian() * 0.9;
			forks.add(flatten(BoltPath.jagged(root, root.add(Math.cos(fa) * length * 0.35, 0, Math.sin(fa) * length * 0.35), 0.35, 4,
					random), at.y));
		}
		float b = flash[2] * (float) density;
		bolt(channel, forks, cam, view, proj, right, up, b, 1.3F, 0.15F, ARC, 0.6F);
		if (random.nextInt(4) == 0) {
			// Down to the ground, out beyond the zone where the storm's edge is.
			double ga = random.nextDouble() * Math.PI * 2.0;
			double out = thunder.radius * (1.2 + 0.8 * random.nextDouble());
			double gx = thunder.center.x + Math.cos(ga) * out;
			double gz = thunder.center.z + Math.sin(ga) * out;
			double gy = world.isChunkLoaded(MathHelper.floor(gx) >> 4, MathHelper.floor(gz) >> 4)
					? world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(gx), MathHelper.floor(gz)) : thunder.center.y;
			Vec3d top = new Vec3d(gx + random.nextGaussian() * 12.0, thunder.cloudBase, gz + random.nextGaussian() * 12.0);
			List<Vec3d> down = BoltPath.jagged(top, new Vec3d(gx, gy, gz), 0.16, 6, random);
			List<List<Vec3d>> branches = new ArrayList<>();
			for (int f = 0; f < 3; f++) {
				Vec3d root = down.get(4 + random.nextInt(down.size() / 2));
				Vec3d dir = new Vec3d(random.nextGaussian(), -1.0, random.nextGaussian()).normalize();
				branches.add(BoltPath.jagged(root, root.add(dir.multiply((root.y - gy) * 0.3)), 0.3, 4, random));
			}
			bolt(down, branches, cam, view, proj, right, up, b, 2.0F, 0.25F, ARC, 0.6F);
		}
	}

	/** Holds a crawler near the cloud base: only gentle rises and dips. */
	private static List<Vec3d> flatten(List<Vec3d> points, double y) {
		List<Vec3d> out = new ArrayList<>(points.size());
		for (Vec3d p : points) {
			out.add(new Vec3d(p.x, y + (p.y - y) * 0.15, p.z));
		}
		return out;
	}

	/**
	 * A bolt: a wide coloured glow, then a hard white core, along the channel and (dimmer, by {@code forks}) its forks.
	 */
	private static void bolt(List<Vec3d> channel, List<List<Vec3d>> forks, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right,
			Vector3f up, float brightness, float glowWidth, float coreWidth, float[] color, float forkBrightness) {
		bolt(channel, forks, cam, view, proj, right, up, brightness, glowWidth, coreWidth, color, forkBrightness, 0.0F);
	}

	/**
	 * A bolt as above, thinner where it passes nearer the camera than {@code near} blocks (in step with the distance), so
	 * that one leaving the shooter's own hand is a bolt there and not a glow over the whole picture.
	 */
	private static void bolt(List<Vec3d> channel, List<List<Vec3d>> forks, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right,
			Vector3f up, float brightness, float glowWidth, float coreWidth, float[] color, float forkBrightness, float near) {
		if (brightness <= 0.01F || channel.size() < 2) {
			return;
		}
		// Batches share one buffer, so the glow is drawn before the core is begun.
		float glowA = 0.6F * brightness;
		Fx glow = BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
		chain(glow, channel, cam, glowWidth, Fx.argb(color[0], color[1], color[2], glowA), near);
		if (forkBrightness > 0.01F) {
			for (List<Vec3d> fork : forks) {
				chain(glow, fork, cam, glowWidth * 0.55F, Fx.argb(color[0], color[1], color[2], glowA * forkBrightness * 0.7F), near);
			}
		}
		glow.end(true, 3.0F);
		Fx core = BATCH.begin(Fx.BEAM, 0.0F, view, proj, right, up);
		chain(core, channel, cam, coreWidth, Fx.argb(CORE[0], CORE[1], CORE[2], brightness), near);
		if (forkBrightness > 0.01F) {
			for (List<Vec3d> fork : forks) {
				chain(core, fork, cam, coreWidth * 0.5F, Fx.argb(CORE[0], CORE[1], CORE[2], brightness * forkBrightness * 0.7F), near);
			}
		}
		core.end(true, 12.0F);
	}

	/** Lays beams end to end along a polyline, into a batch that is open. */
	private static void chain(Fx fx, List<Vec3d> points, Vec3d cam, float width, int argb) {
		chain(fx, points, cam, width, argb, 0.0F);
	}

	/** As above, each beam thinner by its distance from the camera inside {@code near} blocks (none if 0). */
	private static void chain(Fx fx, List<Vec3d> points, Vec3d cam, float width, int argb, float near) {
		Vector3f eye = new Vector3f();
		for (int i = 0; i + 1 < points.size(); i++) {
			Vec3d a = points.get(i);
			Vec3d b = points.get(i + 1);
			Vector3f from = rel(a.x, a.y, a.z, cam);
			Vector3f to = rel(b.x, b.y, b.z, cam);
			float w = width;
			if (near > 0.0F) {
				float d = Math.min(from.length(), to.length());
				w *= MathHelper.clamp(d / near, 0.03F, 1.0F);
			}
			fx.beam(from, to, eye, w, argb, argb);
		}
	}

	// --- lights and grading ------------------------------------------------------------------------

	/** The lights the strikes cast on the world this frame, strongest first (at most four). */
	private static List<Light> lights(List<ClientThunder> live, float tickDelta, Vec3d cam) {
		List<Light> lights = new ArrayList<>();
		for (ClientThunder thunder : live) {
			double t = thunder.time(tickDelta);
			double e = t - ThunderTimeline.STROKE;
			if (thunder.callFrom != null && t >= ThunderTimeline.CALL && t < ThunderTimeline.CALL + 8) {
				float i = (float) (10.0 * Math.exp(-(t - ThunderTimeline.CALL) / 1.5));
				lights.add(light(thunder.callFrom, cam, 30.0F, 0.75F * i, 0.85F * i, i, 0.4F));
			}
			float[] flash = stormFlash(thunder, t);
			double density = stormDensity(t);
			if (flash[2] > 0.05F && density > 0.05) {
				double radius = ThunderTimeline.vortexRadius(t, thunder.radius);
				Vec3d at = thunder.top().add(flash[0] * radius, -8.0, flash[1] * radius);
				float i = (float) (2.2 * flash[2] * density);
				lights.add(light(at, cam, (float) (thunder.cloudBase - thunder.center.y) * 1.2F, 0.7F * i, 0.8F * i, i, 0.8F));
			}
			if (t >= ThunderTimeline.INBOUND && t < ThunderTimeline.STROKE && thunder.path != null) {
				double reach = ThunderTimeline.leaderReach(t);
				double y = thunder.path.top - reach * (thunder.path.top - thunder.path.bottom);
				lights.add(light(new Vec3d(thunder.center.x, y, thunder.center.z), cam, 40.0F, 1.2F, 1.0F, 2.2F, 0.5F));
			}
			if (thunder.struck && e >= 0) {
				float b = (float) ThunderTimeline.channelFlash(e);
				if (b > 0.02F) {
					float i = 40.0F * b;
					lights.add(light(thunder.center.add(0, 4, 0), cam, thunder.radius * 1.4F, 0.85F * i, 0.9F * i, i, 0.4F));
					double mid = (thunder.cloudBase - thunder.center.y) * 0.4;
					lights.add(light(thunder.center.add(0, mid, 0), cam, thunder.radius * 1.8F, 0.4F * i, 0.45F * i, 0.5F * i, 0.6F));
				}
				float glow = (float) (2.5 * Math.exp(-e / 40.0));
				if (glow > 0.05F) {
					lights.add(light(thunder.center.add(0, 2, 0), cam, thunder.radius * 0.8F, 0.35F * glow, 0.55F * glow, glow, 0.2F));
				}
				for (ClientThunder.Arc arc : thunder.arcs) {
					double since = e - arc.delay();
					if (since >= 0 && since < 6) {
						float i = (float) (8.0 * Math.exp(-since / 2.0));
						lights.add(light(arc.to(), cam, 14.0F, 0.7F * i, 0.8F * i, i, 0.4F));
					}
				}
			}
		}
		lights.sort((a, b) -> Float.compare(b.weight(), a.weight()));
		return lights.size() > 4 ? lights.subList(0, 4) : lights;
	}

	/** How brightly the strokes in sight light the dust this frame. */
	private static float strokeFlash(List<ClientThunder> live, float tickDelta) {
		float flash = 0.0F;
		for (ClientThunder thunder : live) {
			double e = thunder.sinceStroke(tickDelta);
			if (thunder.struck && e >= 0) {
				flash = Math.max(flash, (float) ThunderTimeline.channelFlash(e) * 1.5F);
			}
		}
		return flash;
	}

	private static Light light(Vec3d pos, Vec3d cam, float range, float r, float g, float b, float wrap) {
		return new Light((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z), range, r, g, b, wrap);
	}

	private static void drawLights(List<Light> lights, Matrix4f view, Matrix4f proj, int w, int h, float ambient) {
		Post.begin();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ZERO,
				GlStateManager.DstFactor.ONE);
		RenderSystem.setShaderTexture(0, COPY.color());
		RenderSystem.setShaderTexture(1, DEPTH.depth());
		Shaders.set(Shaders.light, "InvViewProj", new Matrix4f(proj).mul(view).invert());
		Shaders.set(Shaders.light, "ScreenSize", w, h);
		Shaders.set(Shaders.light, "Ambient", ambient);
		float fogEnd = RenderSystem.getShaderFogEnd();
		float fogStart = Math.min(RenderSystem.getShaderFogStart(), fogEnd * 0.8F);
		Shaders.set(Shaders.light, "Fog", fogStart * 0.85F, Math.max(fogEnd, fogStart * 0.85F + 1.0F));
		for (int i = 0; i < 4; i++) {
			Light l = i < lights.size() ? lights.get(i) : null;
			if (l == null) {
				Shaders.set(Shaders.light, "Light" + i + "Pos", 0.0F, 0.0F, 0.0F, 1.0F);
				Shaders.set(Shaders.light, "Light" + i + "Color", 0.0F, 0.0F, 0.0F, 0.0F);
			} else {
				Shaders.set(Shaders.light, "Light" + i + "Pos", l.x(), l.y(), l.z(), l.range());
				Shaders.set(Shaders.light, "Light" + i + "Color", l.r(), l.g(), l.b(), l.wrap());
			}
		}
		Post.quad(Shaders.light);
		RenderSystem.disableBlend();
	}

	/**
	 * The stroke's impact frames, in ticks after it: the white flash, then hard cuts between stylised frames, each
	 * restrike cutting back to white (see ss_thunder for the styles), and a white pop at the end. The shooter always gets
	 * them; anyone else near enough and looking that way does too. The call flashes the shooter's view too.
	 */
	private static final float[] FRAME_AT = {0.0F, 1.0F, 2.5F, 4.0F, 5.0F, 6.0F, 8.0F, 10.0F, 11.0F, 12.5F, 15.0F, 17.5F, 19.0F,
			20.0F, 22.5F, 25.0F};
	private static final int[] FRAME_MODE = {0, 2, 1, 2, 0, 3, 1, 4, 0, 2, 3, 1, 0, 4, 2};

	private static int frameBeat(double e) {
		if (e < 0.0 || e >= FRAME_AT[FRAME_AT.length - 1]) {
			return -1;
		}
		int beat = 0;
		while (beat + 1 < FRAME_MODE.length && e >= FRAME_AT[beat + 1]) {
			beat++;
		}
		return beat;
	}

	@Nullable
	private static Grade grade(MinecraftClient client, List<ClientThunder> live, float tickDelta, Vec3d cam, Matrix4f view,
			Matrix4f proj) {
		Grade best = null;
		float bestWeight = 0.0F;
		Vector3f forward = new Vector3f(-view.m02(), -view.m12(), -view.m22());
		for (ClientThunder thunder : live) {
			double t = thunder.time(tickDelta);
			boolean cinematic = thunder.cinematic();
			float flash = 0.0F;
			if (thunder.mine && t >= ThunderTimeline.CALL && t < ThunderTimeline.CALL + 4) {
				flash = (float) (0.6 * Math.exp(-(t - ThunderTimeline.CALL) / 0.7));
			}
			float[] flashColor = WHITE_FLASH;
			if (cinematic && t >= ThunderTimeline.FEED - 8 && t < ThunderTimeline.FEED) {
				// Into the storm's cloud at the top of the rise, the colour the feed opens in.
				flash = (float) ThunderTimeline.smooth((t - (ThunderTimeline.FEED - 8)) / 7.0);
				flashColor = CLOUD_FLASH;
			}
			if (cinematic && t >= ThunderTimeline.INBOUND && t < ThunderTimeline.INBOUND + 5) {
				// Out of the feed's last flash into the world.
				flash = Math.max(flash, (float) (0.9 * (1.0 - ThunderTimeline.smooth((t - ThunderTimeline.INBOUND) / 5.0))));
			}
			int mode = 0;
			float mix = 0.0F;
			float zoom = 1.0F;
			float chroma = 0.0F;
			float exposure = 1.0F;
			float glow = 0.0F;
			float cx = 0.5F;
			float cy = 0.5F;
			double e = t - ThunderTimeline.STROKE;
			float weight = cinematic ? 1.0F : 0.0F;
			if (thunder.struck && e >= 0 && e < 60) {
				Vec3d to = thunder.center.subtract(cam);
				double distance = to.length();
				double near = MathHelper.clamp(1.0 - distance / (thunder.radius * 9.0 + 160.0), 0.0, 1.0);
				Vector3f dir = new Vector3f((float) to.x, (float) to.y, (float) to.z).normalize();
				float looking = Math.max(0.0F, forward.dot(dir));
				float[] screen = screen(thunder.center.add(0, (thunder.cloudBase - thunder.center.y) * 0.3, 0), cam, view, proj);
				if (screen != null) {
					cx = screen[0];
					cy = screen[1];
				}
				boolean frames = cinematic || near > 0.3 && looking > 0.45F;
				int beat = frameBeat(e);
				if (frames && beat >= 0) {
					mode = FRAME_MODE[beat];
					mix = mode == 0 ? 0.0F : 1.0F;
					double since = e - FRAME_AT[beat];
					zoom += (float) (0.05 * Math.exp(-since / 1.0) + Math.min(since, 4.0) * 0.006);
					if (mode == 0) {
						flash = Math.max(flash, (float) Math.exp(-since / 0.7));
					}
				}
				double pop = e - FRAME_AT[FRAME_AT.length - 1];
				if (frames && pop >= 0.0) {
					flash = Math.max(flash, (float) (0.9 * Math.exp(-pop / 0.8)) * (cinematic ? 1.0F : (float) near));
				}
				// Everyone near enough sees the sky flash, whichever way they face.
				float sky = (float) (near * (0.25 + 0.5 * looking) * ThunderTimeline.channelFlash(e));
				if (!frames) {
					flash = Math.max(flash, sky);
				}
				chroma = (float) (0.01 * Math.exp(-e / 10.0) * (0.3 + 0.7 * near));
				exposure = (float) (1.0 + 0.4 * near * Math.exp(-e / 8.0));
				glow = (float) (0.12 * near * ThunderTimeline.channelFlash(e));
				weight += (float) near + flash;
			} else {
				weight += flash;
			}
			if (weight > bestWeight && (mix > 0 || flash > 0.005F || chroma > 0.0005F || glow > 0.005F)) {
				best = new Grade(mode, mix, cx, cy, zoom, chroma, exposure, flash, glow, flashColor);
				bestWeight = weight;
			}
		}
		return best;
	}

	// --- helpers -----------------------------------------------------------------------------------

	/** World point to screen (0..1, y up), or null behind the camera. */
	@Nullable
	private static float[] screen(Vec3d world, Vec3d cam, Matrix4f view, Matrix4f proj) {
		Vector4f v = new Vector4f((float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z), 1.0F);
		view.transform(v);
		proj.transform(v);
		if (v.w <= 1.0E-3F) {
			return null;
		}
		return new float[] {v.x / v.w * 0.5F + 0.5F, v.y / v.w * 0.5F + 0.5F};
	}

	/** The projection's depth term, undoing camera shake's tilt (see WorldFx). */
	private static float projA(Matrix4f proj) {
		return -(float) Math.sqrt(proj.m02() * proj.m02() + proj.m12() * proj.m12() + proj.m22() * proj.m22());
	}

	private static Vector3f rel(double x, double y, double z, Vec3d cam) {
		return new Vector3f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z));
	}

	private static float daylight(ClientWorld world, float tickDelta) {
		float angle = world.getSkyAngle(tickDelta) * MathHelper.TAU;
		return MathHelper.clamp(MathHelper.cos(angle) * 2.0F + 0.5F, 0.15F, 1.0F);
	}
}
