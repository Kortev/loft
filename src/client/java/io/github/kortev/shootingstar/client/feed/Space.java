package io.github.kortev.shootingstar.client.feed;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.shootingstar.client.gfx.Cam;
import io.github.kortev.shootingstar.client.gfx.Fx;
import io.github.kortev.shootingstar.client.gfx.Mesh;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Tex;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/** Drawing helpers shared by the feed's shots: sky, planets, the feed's meshes and plasma. */
final class Space {
	Mesh sphere;
	Mesh stars;
	Mesh ring;
	Mesh ringHalo;
	Mesh round;
	Mesh sabot;
	Mesh coil;
	Mesh relay;
	Mesh cone;
	/** The accelerator's barrel seen from inside, drawn by {@code ss_bore}. */
	Mesh tube;
	/** Io, shaded by {@code ss_mesh}'s moon material. */
	Mesh io;
	/** The Moon, shaded by {@code ss_mesh}'s lunar material. */
	Mesh moon;
	final Mesh[] rocks = new Mesh[4];
	/** Bifröst, the orbital gate (tools/models.py). */
	Mesh gate;
	final Fx fx = new Fx();
	/** Secondary light for meshes (what a nearby planet bounces back), in the scene's world space. */
	final Vector3f fillDir = new Vector3f(0, -1, 0);
	final Vector3f fillColor = new Vector3f();
	/** A point light for meshes (a coil firing beside the round), in world space; fades with distance squared. */
	final Vector3f pointPos = new Vector3f();
	final Vector3f pointColor = new Vector3f();
	/** Shadows on Jupiter: the ring's radius and width in Jupiter radii (0 for none), and a moon in world space. */
	float ringShadow;
	/** How much fine cloud detail to stir into Jupiter's map, for views close over the cloud tops. */
	float jupiterDetail;
	float ringShadowWidth = 0.012F;
	final Vector3f moonPos = new Vector3f();
	float moonRadius;

	private int earthDay;
	private int earthNight;
	private int earthClouds;
	private int jupiter;
	private int milkyWay;

	/** Creates the GPU resources on first use (render thread). */
	void ensure() {
		if (sphere != null) {
			return;
		}
		sphere = Mesh.sphere(160, 80);
		stars = Mesh.stars();
		ring = Mesh.ribbon(1440, 0.012F);
		ringHalo = Mesh.ribbon(1440, 0.07F);
		round = Mesh.load("round");
		sabot = Mesh.load("sabot");
		coil = Mesh.load("coil");
		relay = Mesh.load("relay");
		cone = Mesh.cone(64, 16);
		tube = Mesh.tube(128, 96, -30.0F, 900.0F);
		io = Mesh.sphere(64, 32, 15);
		moon = Mesh.sphere(96, 48, 16);
		for (int i = 0; i < rocks.length; i++) {
			rocks[i] = Mesh.load("asteroid" + i);
		}
		gate = Mesh.load("gate");
		earthDay = Tex.get("feed/earth_day.jpg", true);
		earthNight = Tex.get("feed/earth_night.jpg", true);
		earthClouds = Tex.get("feed/earth_clouds.jpg", true);
		jupiter = Tex.get("feed/jupiter.jpg", true);
		milkyWay = Tex.get("feed/milkyway.jpg", true);
	}

	// --- sky -------------------------------------------------------------------------------

	/**
	 * Milky Way and stars. {@code skyRot} turns world directions into the galactic frame of the maps;
	 * {@code beta} is the viewer's speed along {@code forward} as a fraction of c; {@code streak}
	 * stretches the stars away from the screen point ({@code sx}, {@code sy}) in clip space.
	 */
	void sky(Cam cam, Matrix4f skyRot, float brightness, float beta, Vector3f forward, float streak, float sx, float sy,
			float time) {
		Post.begin();
		Matrix4f invViewProj = new Matrix4f(cam.proj).mul(cam.viewRot).invert();
		RenderSystem.setShaderTexture(0, milkyWay);
		Shaders.set(Shaders.sky, "InvViewProj", invViewProj);
		Shaders.set(Shaders.sky, "SkyRot", skyRot);
		// The Milky Way map is bright; keep it a backdrop so the planets and the round carry the frame.
		Shaders.set(Shaders.sky, "Brightness", brightness * 0.42F * (1.0F - 0.55F * Math.min(1.0F, streak)));
		Shaders.set(Shaders.sky, "Beta", beta);
		Shaders.set(Shaders.sky, "Forward", forward);
		Post.quad(Shaders.sky);

		additive();
		RenderSystem.disableDepthTest();
		Matrix4f galacticToView = new Matrix4f(cam.viewRot).mul(new Matrix4f(skyRot).transpose());
		Vector3f forwardGalactic = skyRot.transformDirection(new Vector3f(forward));
		Shaders.set(Shaders.stars, "Brightness", brightness * 1.4F * (1.0F + streak * 2.5F));
		Shaders.set(Shaders.stars, "Beta", beta);
		Shaders.set(Shaders.stars, "Forward", forwardGalactic);
		Shaders.set(Shaders.stars, "Time", time);
		Shaders.set(Shaders.stars, "Streak", streak, sx, sy);
		stars.draw(Shaders.stars, galacticToView, cam.proj);
	}

	/** Only the stars, added over whatever is drawn (for the transfer plot's backdrop). */
	void stars(Cam cam, Matrix4f skyRot, float brightness, float time) {
		additive();
		RenderSystem.disableDepthTest();
		Matrix4f galacticToView = new Matrix4f(cam.viewRot).mul(new Matrix4f(skyRot).transpose());
		Shaders.set(Shaders.stars, "Brightness", brightness);
		Shaders.set(Shaders.stars, "Beta", 0.0F);
		Shaders.set(Shaders.stars, "Forward", 0.0F, 0.0F, 1.0F);
		Shaders.set(Shaders.stars, "Time", time);
		Shaders.set(Shaders.stars, "Streak", 0.0F, 0.0F, 0.0F);
		stars.draw(Shaders.stars, galacticToView, cam.proj);
	}

	/**
	 * Darkens everything drawn so far by {@code amount} (0 leaves it, 1 is black): the first half of a cross-fade,
	 * before the next shot is added over it.
	 */
	static void dim(float amount) {
		if (amount <= 0.0F) {
			return;
		}
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
		int a = Math.round(Math.min(amount, 1.0F) * 255.0F);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		b.vertex(-1.0F, -1.0F, 0.0F).color(0, 0, 0, a);
		b.vertex(1.0F, -1.0F, 0.0F).color(0, 0, 0, a);
		b.vertex(1.0F, 1.0F, 0.0F).color(0, 0, 0, a);
		b.vertex(-1.0F, 1.0F, 0.0F).color(0, 0, 0, a);
		Post.draw(b, GameRenderer.getPositionColorProgram(), new Matrix4f(), new Matrix4f());
	}

	// --- planets ---------------------------------------------------------------------------

	void earth(Cam cam, Matrix4f model, Vector3f sun, float cloudShift, float detail, float exposure) {
		opaque();
		RenderSystem.setShaderTexture(0, earthDay);
		RenderSystem.setShaderTexture(1, earthNight);
		RenderSystem.setShaderTexture(2, earthClouds);
		Shaders.set(Shaders.planet, "LightDir", cam.viewDir(sun));
		Shaders.set(Shaders.planet, "CloudShift", cloudShift);
		Shaders.set(Shaders.planet, "Exposure", exposure);
		Shaders.set(Shaders.planet, "Detail", detail);
		sphere.draw(Shaders.planet, cam.modelView(model), cam.proj);
		atmosphere(cam, model, sun, 1.025F, 0.3F, 0.55F, 1.0F, 1.5F, 2.6F);
	}

	void jupiter(Cam cam, Matrix4f model, Vector3f sun, float time, float exposure) {
		opaque();
		RenderSystem.setShaderTexture(0, jupiter);
		Shaders.set(Shaders.gas, "LightDir", cam.viewDir(sun));
		Shaders.set(Shaders.gas, "Time", time);
		Shaders.set(Shaders.gas, "Exposure", exposure);
		Matrix4f toObject = new Matrix4f(model).invert();
		Shaders.set(Shaders.gas, "SunObj", toObject.transformDirection(new Vector3f(sun)).normalize());
		Shaders.set(Shaders.gas, "RingRadius", ringShadow);
		Shaders.set(Shaders.gas, "RingWidth", ringShadowWidth);
		Shaders.set(Shaders.gas, "MoonPos", toObject.transformPosition(new Vector3f(moonPos)));
		Shaders.set(Shaders.gas, "MoonRadius", moonRadius / model.getScale(new Vector3f()).x);
		Shaders.set(Shaders.gas, "Detail", jupiterDetail);
		sphere.draw(Shaders.gas, cam.modelView(model), cam.proj);
		atmosphere(cam, model, sun, 1.012F, 0.95F, 0.78F, 0.55F, 0.7F, 3.5F);
	}

	/** Additive rim on a shell {@code scale} times the planet's size. */
	void atmosphere(Cam cam, Matrix4f model, Vector3f sun, float scale, float r, float g, float b, float intensity, float falloff) {
		additive();
		RenderSystem.enableDepthTest();
		Shaders.set(Shaders.atmo, "LightDir", cam.viewDir(sun));
		Shaders.set(Shaders.atmo, "GlowColor", r, g, b);
		Shaders.set(Shaders.atmo, "Intensity", intensity);
		Shaders.set(Shaders.atmo, "Falloff", falloff);
		sphere.draw(Shaders.atmo, cam.modelView(new Matrix4f(model).scale(scale)), cam.proj);
	}

	// --- meshes ----------------------------------------------------------------------------

	/** A Blender mesh lit by the sun; {@code glow} drives the emissive panels, {@code heat} the round's nose. */
	void mesh(Mesh mesh, Cam cam, Matrix4f model, Vector3f sun, float light, int glowRgb, float glow, float heat) {
		mesh(mesh, cam, model, sun, light, (glowRgb >> 16 & 255) / 255.0F, (glowRgb >> 8 & 255) / 255.0F, (glowRgb & 255) / 255.0F,
				glow, heat);
	}

	void mesh(Mesh mesh, Cam cam, Matrix4f model, Vector3f sun, float light, float glowR, float glowG, float glowB, float glow,
			float heat) {
		opaque();
		Shaders.set(Shaders.mesh, "LightDir", cam.viewDir(sun));
		Shaders.set(Shaders.mesh, "LightColor", 1.0F * light, 0.96F * light, 0.9F * light);
		Shaders.set(Shaders.mesh, "AmbientColor", 0.05F, 0.055F, 0.07F);
		Shaders.set(Shaders.mesh, "RimColor", 0.25F, 0.2F, 0.18F);
		Shaders.set(Shaders.mesh, "GlowColor", glowR, glowG, glowB);
		Shaders.set(Shaders.mesh, "GlowStrength", glow);
		Shaders.set(Shaders.mesh, "Heat", heat);
		Shaders.set(Shaders.mesh, "Fade", 1.0F);
		Shaders.set(Shaders.mesh, "FillDir", cam.viewDir(fillDir));
		Shaders.set(Shaders.mesh, "FillColor", fillColor);
		Shaders.set(Shaders.mesh, "PointPos", cam.view.transformPosition(new Vector3f(pointPos)));
		Shaders.set(Shaders.mesh, "PointColor", pointColor);
		mesh.draw(Shaders.mesh, cam.modelView(model), cam.proj);
	}

	/** Clears the fill and point lights; each shot sets the ones it wants. */
	void resetLights() {
		fillDir.set(0, -1, 0);
		fillColor.zero();
		pointColor.zero();
		ringShadow = 0.0F;
		moonRadius = 0.0F;
	}

	/**
	 * The barrel round the round, along +Z: coils {@code travel} spacings on, smeared over the {@code smear} spacings
	 * they move while the shutter is open; the round at {@code roundZ}, firing as hard as {@code speed}; the camera at
	 * {@code cameraZ} along the barrel. Premultiplied over what is drawn, depth-tested but not writing depth.
	 */
	void bore(Cam cam, double travel, float smear, float roundZ, float cameraZ, float speed, float idle) {
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
		// The pattern repeats every 720 coils (the rifling's 90 and the stations' 12 both divide it), which keeps the
		// travel small enough for single precision.
		Shaders.set(Shaders.bore, "Travel", (float) (travel % 720.0));
		Shaders.set(Shaders.bore, "Smear", smear);
		Shaders.set(Shaders.bore, "RoundZ", roundZ);
		Shaders.set(Shaders.bore, "CameraZ", cameraZ);
		Shaders.set(Shaders.bore, "Speed", speed);
		Shaders.set(Shaders.bore, "Idle", idle);
		Shaders.set(Shaders.bore, "Light", 0.55F);
		Shaders.set(Shaders.bore, "Twist", (float) Math.toRadians(4.0));
		Shaders.set(Shaders.bore, "ArcColor", 0.75F, 0.88F, 1.0F);
		Shaders.set(Shaders.bore, "HotColor", 1.0F, 0.42F, 0.1F);
		// Radius 0.715: just inside the housings' inner faces (tools/models.py).
		tube.draw(Shaders.bore, cam.modelView(new Matrix4f().scale(0.715F, 0.715F, 1.0F)), cam.proj);
	}

	/** Turbulent additive plasma on {@code mesh} (the re-entry sheath or the impact fireball). */
	void plasma(Mesh mesh, Cam cam, Matrix4f model, float time, float intensity, float heat, Vector3f flow, float scale) {
		plasma(mesh, cam, model, time, intensity, heat, flow, scale, 0.0F);
	}

	/** As above; {@code cool} 1 turns the fire violet, for the block of another universe coming down. */
	void plasma(Mesh mesh, Cam cam, Matrix4f model, float time, float intensity, float heat, Vector3f flow, float scale, float cool) {
		additive();
		RenderSystem.enableDepthTest();
		Shaders.set(Shaders.plasma, "Time", time);
		Shaders.set(Shaders.plasma, "Intensity", intensity);
		Shaders.set(Shaders.plasma, "Heat", heat);
		Shaders.set(Shaders.plasma, "Flow", flow);
		Shaders.set(Shaders.plasma, "Scale", scale);
		Shaders.set(Shaders.plasma, "Toon", 0.0F);
		Shaders.set(Shaders.plasma, "Cool", cool);
		mesh.draw(Shaders.plasma, cam.modelView(model), cam.proj);
		Shaders.set(Shaders.plasma, "Cool", 0.0F);
	}

	/**
	 * The accelerator ring as a ribbon that lights up as {@code progress} sweeps round from angle 0, with
	 * a wide faint halo so it still reads from far away.
	 */
	void ring(Cam cam, Matrix4f model, float progress, float base, float r, float g, float b) {
		additive();
		RenderSystem.enableDepthTest();
		Shaders.setInt(Shaders.glow, "Mode", Fx.PROGRESS);
		Shaders.set(Shaders.glow, "Param", base);
		Shaders.set(Shaders.glow, "Progress", progress);
		Shaders.set(Shaders.glow, "Tint", r * 3.0F, g * 3.0F, b * 3.0F);
		ring.draw(Shaders.glow, cam.modelView(model), cam.proj);
		Shaders.set(Shaders.glow, "Tint", r * 0.35F, g * 0.35F, b * 0.35F);
		ringHalo.draw(Shaders.glow, cam.modelView(model), cam.proj);
		Shaders.set(Shaders.glow, "Tint", 1.0F, 1.0F, 1.0F);
	}

	/** Starts a batch of glow shapes in the scene's world space. */
	Fx glow(Cam cam, int mode, float param) {
		return fx.begin(mode, param, cam.view, cam.proj, cam.right(), cam.up());
	}

	// --- state -----------------------------------------------------------------------------

	/** Clears depth so the next layer draws over everything so far (glClear honours the depth mask). */
	static void clearDepth() {
		RenderSystem.depthMask(true);
		RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, net.minecraft.client.MinecraftClient.IS_SYSTEM_MAC);
	}

	static void opaque() {
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		RenderSystem.disableCull();
	}

	static void additive() {
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ZERO,
				GlStateManager.DstFactor.ONE);
	}
}
