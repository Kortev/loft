package io.github.kortev.shootingstar.client.world;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.Mesh;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * The strike in the world: the falling star, the fireball, the condensation shell, the shock ring,
 * flying debris, fire and dust clouds, then the impact frames and grading over the whole picture.
 * Light goes into an HDR buffer (depth-tested against the world) that is bloomed and laid over the
 * frame, so the fireball glows instead of clipping.
 */
public final class WorldFx {
	private static final List<ImpactScene> SCENES = new ArrayList<>();
	private static final Target DEPTH = new Target(true, false);
	private static final Target FX = new Target(true, true);
	private static final Target COPY = new Target(false, false);
	private static final Fx BATCH = new Fx();
	private static final float[][] FACES = {
			{1, 0, 0, 1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1},
			{-1, 0, 0, -1, -1, 1, -1, 1, 1, -1, 1, -1, -1, -1, -1},
			{0, 1, 0, -1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1},
			{0, -1, 0, -1, -1, 1, -1, -1, -1, 1, -1, -1, 1, -1, 1},
			{0, 0, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1, -1, -1, 1},
			{0, 0, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1, -1}};
	@Nullable
	private static Mesh sphere;

	private record Grade(int mode, float mix, float cx, float cy, float zoom, float warp, float warpRadius, float chroma,
			float exposure, float tint, float flash) {
	}

	private record PuffRef(ImpactScene.Puff puff, ImpactScene scene, double distance) {
	}

	private WorldFx() {
	}

	public static void add(ImpactScene scene) {
		SCENES.add(scene);
	}

	public static void clear() {
		SCENES.clear();
	}

	public static void tick(ClientWorld world) {
		for (Iterator<ImpactScene> it = SCENES.iterator(); it.hasNext(); ) {
			ImpactScene scene = it.next();
			scene.tick(world);
			if (scene.done()) {
				it.remove();
			}
		}
	}

	// --- frame -----------------------------------------------------------------------------

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = context.world();
		if (!Shaders.ready() || world == null || client.player == null) {
			return;
		}
		float tickDelta = context.tickCounter().getTickDelta(false);
		List<ClientStrike> inbound = new ArrayList<>();
		for (ClientStrike strike : ClientStrikes.all()) {
			double t = strike.time(tickDelta);
			if (!strike.impacted && t >= StrikeTimeline.INBOUND - 2) {
				inbound.add(strike);
			}
		}
		Vec3d cam = context.camera().getPos();
		Matrix4f view = new Matrix4f(context.positionMatrix());
		Matrix4f proj = new Matrix4f(context.projectionMatrix());
		Grade grade = grade(client, tickDelta, cam, view, proj);
		if (SCENES.isEmpty() && inbound.isEmpty() && grade == null) {
			return;
		}
		if (sphere == null) {
			sphere = Mesh.sphere(64, 32);
		}
		Framebuffer main = client.getFramebuffer();
		int w = main.textureWidth;
		int h = main.textureHeight;
		Vector3f right = new Vector3f(view.m00(), view.m10(), view.m20());
		Vector3f up = new Vector3f(view.m01(), view.m11(), view.m21());
		float far = proj.m32() / (proj.m22() + 1.0F);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		if (!SCENES.isEmpty() || !inbound.isEmpty()) {
			// Solid debris goes straight into the world so it is lit, depth-tested and shows up in the depth copy.
			main.beginWrite(true);
			for (ImpactScene scene : SCENES) {
				drawDebris(world, scene, cam, view, proj, tickDelta);
			}

			DEPTH.ensure(w, h);
			DEPTH.copyDepthFrom(main);
			FX.ensure(w, h);
			FX.copyDepthFrom(main);
			FX.bind();
			RenderSystem.colorMask(true, true, true, true);
			RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
			RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

			for (ClientStrike strike : inbound) {
				drawStar(client, strike, strike.time(tickDelta), cam, view, proj, right, up, far);
			}
			for (ImpactScene scene : SCENES) {
				drawBlast(scene, scene.age + tickDelta, cam, view, proj, right, up);
			}
			drawSmoke(world, cam, view, proj, right, up, tickDelta, w, h);

			Post.begin();
			int[] bloom = Post.bloom(FX.color(), w, h, 1.0F);
			main.beginWrite(true);
			RenderSystem.enableBlend();
			RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
					GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
			RenderSystem.setShaderTexture(0, FX.color());
			RenderSystem.setShaderTexture(1, bloom[0]);
			RenderSystem.setShaderTexture(2, bloom[1]);
			Shaders.set(Shaders.fxcomp, "BloomStrength", 1.0F);
			Shaders.set(Shaders.fxcomp, "WideStrength", 0.8F);
			Post.quad(Shaders.fxcomp);
			RenderSystem.disableBlend();
		}

		if (grade != null) {
			if (SCENES.isEmpty() && inbound.isEmpty()) {
				DEPTH.ensure(w, h);
				DEPTH.copyDepthFrom(main);
			}
			COPY.ensure(w, h);
			COPY.copyColorFrom(main);
			main.beginWrite(true);
			Post.begin();
			RenderSystem.setShaderTexture(0, COPY.color());
			RenderSystem.setShaderTexture(1, DEPTH.depth());
			ShaderSet.impact(grade, w, h, proj, (float) (world.getTime() + tickDelta));
			Post.quad(Shaders.impact);
		}

		for (int i = 0; i < 3; i++) {
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

	/** Uniform plumbing for the impact pass, kept apart from the drawing code. */
	private static final class ShaderSet {
		static void impact(Grade g, int w, int h, Matrix4f proj, float time) {
			Shaders.setInt(Shaders.impact, "Mode", g.mode());
			Shaders.set(Shaders.impact, "Mix", g.mix());
			Shaders.set(Shaders.impact, "Center", g.cx(), g.cy());
			Shaders.set(Shaders.impact, "ScreenSize", w, h);
			Shaders.set(Shaders.impact, "Time", time);
			Shaders.set(Shaders.impact, "ProjA", proj.m22());
			Shaders.set(Shaders.impact, "ProjB", proj.m32());
			Shaders.set(Shaders.impact, "Zoom", g.zoom());
			Shaders.set(Shaders.impact, "Warp", g.warp());
			Shaders.set(Shaders.impact, "WarpRadius", g.warpRadius());
			Shaders.set(Shaders.impact, "Chroma", g.chroma());
			Shaders.set(Shaders.impact, "Darken", 0.0F);
			Shaders.set(Shaders.impact, "Exposure", g.exposure());
			Shaders.set(Shaders.impact, "Tint", 0.55F * g.tint(), 0.22F * g.tint(), 0.06F * g.tint());
			Shaders.set(Shaders.impact, "Flash", g.flash());
			Shaders.set(Shaders.impact, "FlashColor", 1.0F, 0.98F, 0.94F);
		}
	}

	// --- the falling star ------------------------------------------------------------------

	/** Distance of the round from the target along its path: slow at first, then a streak at the end. */
	public static double range(double p) {
		p = MathHelper.clamp(p, 0.0, 1.0);
		return 3600.0 * (1.0 - p * p);
	}

	/**
	 * The round comes in at about 45 degrees from beyond and to the side of the target as seen by this
	 * viewer (the shooter's camera, or the player): the whole fall crosses the sky in front of them as
	 * a diagonal streak, with the trail drawn out behind it rather than hidden end-on.
	 */
	private static Vec3d approach(MinecraftClient client, ClientStrike strike) {
		if (strike.approach == null) {
			Vec3d viewer = strike.cinematic() && strike.witness != null ? strike.witness : client.player.getPos();
			Vec3d away = new Vec3d(strike.center.x - viewer.x, 0, strike.center.z - viewer.z);
			away = away.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : away.normalize();
			Vec3d side = new Vec3d(away.z, 0, -away.x);
			strike.approach = away.multiply(0.6).add(side.multiply(0.4)).add(0, 0.55, 0).normalize();
		}
		return strike.approach;
	}

	private static void drawStar(MinecraftClient client, ClientStrike strike, double t, Vec3d cam, Matrix4f view, Matrix4f proj,
			Vector3f right, Vector3f up, float far) {
		double p = MathHelper.clamp((t - StrikeTimeline.INBOUND) / (StrikeTimeline.IMPACT - StrikeTimeline.INBOUND), 0.0, 1.0);
		Vec3d c = strike.center;
		Vec3d dir = approach(client, strike);
		double range = range(p);
		Vector3f head = rel(c.x + dir.x * range, c.y + dir.y * range, c.z + dir.z * range, cam);
		float trail = (float) (500.0 + 1400.0 * p * p);
		Vector3f tail = new Vector3f(head).add((float) dir.x * trail, (float) dir.y * trail, (float) dir.z * trail);
		// Keep everything inside the far plane: pulling points towards the eye leaves them where they are on screen.
		float reach = Math.max(head.length(), tail.length());
		float pull = Math.min(1.0F, far * 0.85F / Math.max(reach, 1.0F));
		head.mul(pull);
		tail.mul(pull);
		float distance = head.length();
		float core = Math.max(2.0F * pull, distance * 0.012F);
		float fadeIn = (float) MathHelper.clamp((t - StrikeTimeline.INBOUND + 2) / 6.0, 0.0, 1.0);
		Vector3f eye = new Vector3f();

		Fx halo = BATCH.begin(Fx.BLOB, 1.0F, view, proj, right, up);
		halo.sprite(head, core * 10.0F, 0, Fx.argb(1.0F, 0.5F, 0.22F, 0.3F * fadeIn));
		halo.end(true, 1.0F + 1.5F * (float) p);

		Fx glow = BATCH.begin(Fx.BEAM, 0, view, proj, right, up);
		glow.beam(head, tail, eye, core * 3.2F, Fx.argb(1.0F, 0.5F, 0.2F, 0.7F * fadeIn), Fx.argb(1.0F, 0.3F, 0.1F, 0.0F));
		glow.end(true, 1.6F);
		Fx beam = BATCH.begin(Fx.BEAM, 0, view, proj, right, up);
		beam.beam(head, tail, eye, core * 0.9F, Fx.argb(1.0F, 0.9F, 0.7F, fadeIn), Fx.argb(1.0F, 0.45F, 0.15F, 0.0F));
		beam.end(true, 5.0F);

		Fx star = BATCH.begin(Fx.SPIKES, 0, view, proj, right, up);
		star.sprite(head, core * 5.0F, (float) (t * 0.02), Fx.argb(1.0F, 0.96F, 0.88F, fadeIn));
		star.end(true, 7.0F + 10.0F * (float) (p * p));

		// The ground below lights up as it comes in.
		float pool = (float) (strike.radius * (0.25 + 1.1 * p * p));
		Vector3f ground = rel(c.x, c.y + 0.4, c.z, cam);
		Fx light = BATCH.begin(Fx.BLOB, 1.0F, view, proj, right, up);
		light.flat(ground, new Vector3f(pool, 0, 0), new Vector3f(0, 0, pool), Fx.argb(1.0F, 0.6F, 0.3F, (float) (p * p) * fadeIn));
		light.end(true, 1.6F);
	}

	// --- the blast -------------------------------------------------------------------------

	private static void drawBlast(ImpactScene scene, double e, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right,
			Vector3f up) {
		int r = scene.radius;
		Vector3f c = rel(scene.center.x, scene.center.y, scene.center.z, cam);

		// The flash at the moment of impact: a searing point; the whole picture flashing is the grading's job.
		if (e < 10) {
			Fx flash = BATCH.begin(Fx.BLOB, 1.0F, view, proj, right, up);
			flash.sprite(new Vector3f(c).add(0, r * 0.1F, 0), (float) (r * (0.45 + e * 0.06)), 0, Fx.argb(1.0F, 0.92F, 0.8F, 1.0F));
			flash.end(true, (float) (14.0 * Math.exp(-e / 1.3)));
		}

		// Condensation shell racing out ahead of the fireball.
		if (e > 0.5 && e < 44) {
			double rs = r * (0.35 + 2.3 * (1.0 - Math.exp(-e / 9.0)));
			float k = (float) Math.sin(Math.PI * MathHelper.clamp((e - 0.5) / 43.5, 0.0, 1.0));
			Matrix4f model = new Matrix4f().translation(c).scale((float) rs, (float) (rs * 0.72), (float) rs);
			RenderSystem.enableDepthTest();
			additive();
			Shaders.set(Shaders.shell, "GlowColor", 0.85F, 0.9F, 1.0F);
			Shaders.set(Shaders.shell, "Intensity", 0.9F * k);
			Shaders.set(Shaders.shell, "Falloff", 3.5F);
			sphere.draw(Shaders.shell, new Matrix4f(view).mul(model), proj);
		}

		// The fireball: a dome of turbulent plasma that cools from white through orange to red.
		double dome = scene.domeRadius(e);
		double intensity = scene.domeIntensity(e);
		if (intensity > 0.03 && dome > 0.5) {
			float rise = (float) (Math.max(0.0, e - 8.0) * r * 0.004);
			Matrix4f model = new Matrix4f().translation(c.x, c.y - (float) dome * 0.12F + rise, c.z)
					.scale((float) dome, (float) dome * 0.86F, (float) dome);
			RenderSystem.enableDepthTest();
			additive();
			Shaders.set(Shaders.plasma, "Time", (float) (e * 0.04));
			Shaders.set(Shaders.plasma, "Intensity", (float) intensity);
			Shaders.set(Shaders.plasma, "Heat", (float) MathHelper.clamp(0.9 - e / 110.0, 0.25, 0.9));
			Shaders.set(Shaders.plasma, "Flow", 0.0F, -1.6F, 0.0F);
			Shaders.set(Shaders.plasma, "Scale", 1.7F);
			sphere.draw(Shaders.plasma, new Matrix4f(view).mul(model), proj);
		}

		// The shock front sweeping over the ground: bright while it is still carving, then a dust edge.
		double front = scene.front(e);
		if (e > 0.5 && e < scene.waveTicks * 2.4) {
			float fade = (float) Math.exp(-e / (scene.waveTicks * 0.8));
			Vector3f ground = new Vector3f(c).add(0, 0.8F, 0);
			Vector3f u = new Vector3f((float) front, 0, 0);
			Vector3f v = new Vector3f(0, 0, (float) front);
			Fx ring = BATCH.begin(Fx.RING, 0.035F, view, proj, right, up);
			ring.flat(ground, u, v, Fx.argb(1.0F, 0.75F, 0.45F, fade));
			ring.end(true, 3.5F);
			Fx edge = BATCH.begin(Fx.RING, 0.16F, view, proj, right, up);
			edge.flat(ground, u, v, Fx.argb(1.0F, 0.6F, 0.4F, 0.5F * fade));
			edge.end(true, 1.0F);
		}

		// Sparks: white-hot streaks flung out of the bowl.
		if (!scene.sparks.isEmpty()) {
			Fx sparks = BATCH.begin(Fx.STREAK, 0, view, proj, right, up);
			Vector3f axis = new Vector3f();
			for (ImpactScene.Spark s : scene.sparks) {
				float life = 1.0F - (float) s.age / s.life;
				Vector3f pos = rel(s.px + (s.x - s.px) * 0.5, s.py + (s.y - s.py) * 0.5, s.pz + (s.z - s.pz) * 0.5, cam);
				axis.set((float) s.vx, (float) s.vy, (float) s.vz);
				float speed = axis.length();
				if (speed < 1.0E-3F) {
					continue;
				}
				sparks.stretched(pos, axis, speed * 1.4F + s.size, s.size, Fx.argb(1.0F, 0.7F + 0.3F * life, 0.35F + 0.4F * life, life));
			}
			sparks.end(true, 4.0F);
		}
	}

	// --- smoke -----------------------------------------------------------------------------

	private static void drawSmoke(ClientWorld world, Vec3d cam, Matrix4f view, Matrix4f proj, Vector3f right, Vector3f up,
			float tickDelta, int w, int h) {
		List<PuffRef> all = new ArrayList<>();
		for (ImpactScene scene : SCENES) {
			for (ImpactScene.Puff p : scene.puffs) {
				double x = MathHelper.lerp(tickDelta, p.px, p.x) - cam.x;
				double y = MathHelper.lerp(tickDelta, p.py, p.y) - cam.y;
				double z = MathHelper.lerp(tickDelta, p.pz, p.z) - cam.z;
				all.add(new PuffRef(p, scene, x * x + y * y + z * z));
			}
		}
		if (all.isEmpty()) {
			return;
		}
		// Back to front, so nearer smoke covers farther smoke.
		all.sort((a, b) -> Double.compare(b.distance(), a.distance()));
		float daylight = daylight(world, tickDelta);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		Vector3f rx = new Vector3f();
		Vector3f uy = new Vector3f();
		for (PuffRef ref : all) {
			ImpactScene.Puff p = ref.puff();
			ImpactScene scene = ref.scene();
			float x = (float) (MathHelper.lerp(tickDelta, p.px, p.x) - cam.x);
			float y = (float) (MathHelper.lerp(tickDelta, p.py, p.y) - cam.y);
			float z = (float) (MathHelper.lerp(tickDelta, p.pz, p.z) - cam.z);
			float size = MathHelper.lerp(tickDelta, p.prevSize, p.size);
			float age = p.age + tickDelta;
			float fadeIn = MathHelper.clamp(age / 4.0F, 0.0F, 1.0F);
			float fadeOut = MathHelper.clamp((p.life - age) / (p.life * 0.35F), 0.0F, 1.0F);
			// A puff right on top of the camera would fill the screen; thin it out instead.
			float distance = (float) Math.sqrt(ref.distance());
			float nearFade = MathHelper.clamp((distance - size * 0.3F) / (size * 1.2F), 0.0F, 1.0F);
			float a = p.alpha * fadeIn * fadeOut * nearFade;
			if (a < 0.01F) {
				continue;
			}
			// Daylight from above plus the fireball's orange light on the smoke around it.
			double dx = x + cam.x - scene.center.x;
			double dy = y + cam.y - scene.center.y;
			double dz = z + cam.z - scene.center.z;
			double fromFire = Math.sqrt(dx * dx + dy * dy + dz * dz) / Math.max(1.0, scene.radius * 1.6);
			float fire = (float) (scene.domeIntensity(scene.age + tickDelta) * 0.5 * Math.max(0.0, 1.0 - fromFire));
			float light = 0.35F + 0.75F * daylight;
			float red = p.r * light + fire * 0.9F;
			float green = p.g * light + fire * 0.42F;
			float blue = p.b * light + fire * 0.12F;
			rx.set(right).mul(size);
			uy.set(up).mul(size);
			float spin = p.spin * age * 0.004F;
			float glow = p.glow;
			vertex(b, x - rx.x - uy.x, y - rx.y - uy.y, z - rx.z - uy.z, -1, -1, red, green, blue, a, p.seed, spin, glow);
			vertex(b, x + rx.x - uy.x, y + rx.y - uy.y, z + rx.z - uy.z, 1, -1, red, green, blue, a, p.seed, spin, glow);
			vertex(b, x + rx.x + uy.x, y + rx.y + uy.y, z + rx.z + uy.z, 1, 1, red, green, blue, a, p.seed, spin, glow);
			vertex(b, x - rx.x + uy.x, y - rx.y + uy.y, z - rx.z + uy.z, -1, 1, red, green, blue, a, p.seed, spin, glow);
		}
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.setShaderTexture(1, DEPTH.depth());
		Shaders.set(Shaders.smoke, "ScreenSize", w, h);
		Shaders.set(Shaders.smoke, "ProjA", proj.m22());
		Shaders.set(Shaders.smoke, "ProjB", proj.m32());
		Shaders.set(Shaders.smoke, "Softness", 4.0F);
		Post.draw(b, Shaders.smoke, view, proj);
	}

	private static void vertex(BufferBuilder b, float x, float y, float z, float u, float v, float r, float g, float bl, float a,
			float seed, float spin, float glow) {
		b.vertex(x, y, z).texture(u, v).color(clamp(r), clamp(g), clamp(bl), clamp(a)).normal(seed, spin, glow);
	}

	// --- debris ----------------------------------------------------------------------------

	private static void drawDebris(ClientWorld world, ImpactScene scene, Vec3d cam, Matrix4f view, Matrix4f proj, float tickDelta) {
		if (scene.chunks.isEmpty() || scene.sprites.isEmpty()) {
			return;
		}
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		Quaternionf q = new Quaternionf();
		Vector3f n = new Vector3f();
		Vector3f corner = new Vector3f();
		for (ImpactScene.Chunk c : scene.chunks) {
			float x = (float) (MathHelper.lerp(tickDelta, c.px, c.x) - cam.x);
			float y = (float) (MathHelper.lerp(tickDelta, c.py, c.y) - cam.y);
			float z = (float) (MathHelper.lerp(tickDelta, c.pz, c.z) - cam.z);
			float half = c.size * 0.5F;
			if (c.landed && c.landedAge > 120) {
				half *= Math.max(0.0F, 1.0F - (c.landedAge - 120 + tickDelta) / 40.0F);
			}
			if (half <= 0.01F) {
				continue;
			}
			q.identity().rotateAxis(MathHelper.lerp(tickDelta, c.prevAngle, c.angle), c.ax, c.ay, c.az);
			Sprite sprite = scene.sprites.get(c.sprite);
			float[] tint = scene.tints.get(c.sprite);
			float u0 = sprite.getMinU();
			float u1 = sprite.getMaxU();
			float v0 = sprite.getMinV();
			float v1 = sprite.getMaxV();
			float heat = MathHelper.clamp(c.heat, 0.0F, 1.0F);
			for (float[] f : FACES) {
				q.transform(n.set(f[0], f[1], f[2]));
				for (int i = 0; i < 4; i++) {
					q.transform(corner.set(f[3 + i * 3], f[4 + i * 3], f[5 + i * 3])).mul(half).add(x, y, z);
					float u = i < 2 ? u0 : u1;
					float v = i == 0 || i == 3 ? v1 : v0;
					b.vertex(corner.x, corner.y, corner.z).texture(u, v).color(tint[0], tint[1], tint[2], heat).normal(n.x, n.y, n.z);
				}
			}
		}
		float angle = world.getSkyAngle(tickDelta) * MathHelper.TAU;
		float daylight = daylight(world, tickDelta);
		double e = scene.age + tickDelta;
		float fire = (float) Math.min(1.5, scene.domeIntensity(e));
		Vector3f firePos = rel(scene.center.x, scene.center.y + scene.radius * 0.3, scene.center.z, cam);
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		RenderSystem.disableCull();
		RenderSystem.setShaderTexture(0, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
		Shaders.set(Shaders.debris, "LightDir", -MathHelper.sin(angle), Math.max(0.2F, MathHelper.cos(angle)), 0.25F);
		Shaders.set(Shaders.debris, "SkyLight", 0.25F + 0.5F * daylight, 0.26F + 0.52F * daylight, 0.3F + 0.55F * daylight);
		Shaders.set(Shaders.debris, "FireLight", 1.6F * fire, 0.7F * fire, 0.25F * fire);
		Shaders.set(Shaders.debris, "FirePos", firePos);
		Shaders.set(Shaders.debris, "FireRange", scene.radius * 2.5F);
		Post.draw(b, Shaders.debris, view, proj);
	}

	// --- grading and impact frames ---------------------------------------------------------

	@Nullable
	private static Grade grade(MinecraftClient client, float tickDelta, Vec3d cam, Matrix4f view, Matrix4f proj) {
		Grade best = null;
		float bestWeight = 0.0F;
		Vector3f forward = new Vector3f(-view.m02(), -view.m12(), -view.m22());
		for (ClientStrike strike : ClientStrikes.all()) {
			double t = strike.time(tickDelta);
			boolean cinematic = strike.cinematic();
			float flash = 0.0F;
			if (cinematic && t >= StrikeTimeline.INBOUND && t < StrikeTimeline.INBOUND + 8) {
				// Out of the feed's re-entry whiteout into the world.
				double k = (t - StrikeTimeline.INBOUND) / 8.0;
				flash = (float) (1.0 - k * k * (3 - 2 * k));
			}
			if (!strike.impacted || strike.scene == null) {
				if (flash > 0.0F && flash > bestWeight) {
					best = new Grade(0, 0, 0.5F, 0.5F, 1, 0, 0, 0, 1, 0, flash);
					bestWeight = flash;
				}
				continue;
			}
			ImpactScene scene = strike.scene;
			double e = scene.age + tickDelta;
			if (e > 120) {
				continue;
			}
			Vec3d to = scene.center.subtract(cam);
			double distance = to.length();
			double near = MathHelper.clamp(1.0 - distance / (scene.radius * 9.0 + 120.0), 0.0, 1.0);
			Vector3f dir = new Vector3f((float) to.x, (float) to.y, (float) to.z).normalize();
			float looking = Math.max(0.0F, forward.dot(dir));
			float[] screen = screen(scene.center, cam, view, proj);
			float cx = screen != null ? screen[0] : 0.5F;
			float cy = screen != null ? screen[1] : 0.5F;

			boolean frames = cinematic || near > 0.25 && looking > 0.55F;
			int mode = 0;
			float mix = 0.0F;
			if (frames && e < 11.0) {
				mode = e < 1.0 ? 0 : e < 2.5 ? 1 : e < 4.0 ? 2 : e < 5.0 ? 1 : e < 7.0 ? 3 : e < 9.5 ? 4 : 5;
				mix = mode == 0 ? 0.0F : 1.0F;
			}
			float white = (float) (cinematic ? (e < 1.0 ? 1.0 : 0.0) : near * (0.35 + 0.65 * looking) * Math.exp(-e / 2.5));
			flash = Math.max(flash, white);
			float zoom = e < 14 ? (float) (1.0 + 0.05 * Math.exp(-e / 4.0)) : 1.0F;
			float chroma = (float) (0.012 * Math.exp(-e / 14.0) * (0.3 + 0.7 * near));
			float exposure = (float) (1.0 + 0.5 * near * Math.exp(-e / 10.0));
			float tint = (float) (near * 0.45 * Math.exp(-e / 28.0));
			// The shock ring as a refraction wave round the impact, sized from where the front is on screen.
			float warp = 0.0F;
			float warpRadius = 0.0F;
			double front = scene.front(e);
			if (screen != null && e > 1.5 && e < scene.waveTicks * 2.2) {
				Vec3d side = new Vec3d(right(view).x, 0, right(view).z).normalize().multiply(front);
				float[] edge = screen(scene.center.add(side), cam, view, proj);
				if (edge != null) {
					float aspect = (float) client.getWindow().getFramebufferWidth() / client.getWindow().getFramebufferHeight();
					warpRadius = (float) Math.hypot((edge[0] - cx) * aspect, edge[1] - cy);
					warp = (float) (0.025 * Math.exp(-e / (scene.waveTicks * 0.9)) * (0.4 + 0.6 * near));
				}
			}
			float weight = (float) (near + (cinematic ? 1.0 : 0.0) + flash);
			if (weight > bestWeight && (mix > 0 || flash > 0.005F || warp > 0.0005F || tint > 0.01F || chroma > 0.0005F)) {
				best = new Grade(mode, mix, cx, cy, zoom, warp, warpRadius, chroma, exposure, tint, flash);
				bestWeight = weight;
			}
		}
		return best;
	}

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

	private static Vector3f right(Matrix4f view) {
		return new Vector3f(view.m00(), view.m10(), view.m20());
	}

	// --- helpers ---------------------------------------------------------------------------

	private static Vector3f rel(double x, double y, double z, Vec3d cam) {
		return new Vector3f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z));
	}

	private static float daylight(ClientWorld world, float tickDelta) {
		float angle = world.getSkyAngle(tickDelta) * MathHelper.TAU;
		return MathHelper.clamp(MathHelper.cos(angle) * 2.0F + 0.5F, 0.15F, 1.0F);
	}

	private static void additive() {
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ZERO,
				GlStateManager.DstFactor.ONE);
	}

	private static float clamp(float v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}
}
