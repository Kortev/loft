package io.github.kortev.gojolander.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.gojolander.domain.DomainTimeline;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import io.github.kortev.shootingstar.client.gfx.Timings;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Draws Domains over the world in one full-screen pass each ({@code gl_domain}): the ink, the black flooding up into a
 * sphere, from outside the sphere itself, from inside the black and the void opening out of the eye, and its end:
 * fading away, let go of, as in the show, or shattering, cut short.
 *
 * <p>The void takes everything inside it, people too, so whatever is in there with the camera is drawn again, into a
 * picture of its own, and laid back over the void ({@code gl_rim}): dark, lit only by the great eye, its edges towards
 * the eye burning blue-white. The depth is kept throughout, so the walls of the world still hide what they hid.
 *
 * <p>It is drawn every frame over the whole screen, so it is kept cheap: the void's smooth sky is painted into a small
 * panorama ({@code gl_sky}, every other frame) that the pass looks up instead of working out; a Domain that is nowhere
 * on screen is not drawn at all; and the people pass only runs with someone there to draw.
 */
public final class DomainRender {
	private static final Target DEPTH = new Target(true, false);
	private static final Target COPY = new Target(false, false);
	private static final Target PEOPLE = new Target(true, false);
	/** The void's sky, all of it, longitude across and latitude down (HDR: the eye's glow goes past white). */
	private static final Target SKY = new Target(false, true);
	private static final int SKY_W = 1024;
	private static final int SKY_H = 512;
	private static int frames;
	private static boolean skyPainted;
	/** How far off a Domain is drawn at all. */
	private static final double DRAWN = 512.0;
	/** How long the world inside takes to go to black as it closes. */
	private static final double BLACKOUT = 2.0;
	/** How far above the way he faced the great eye lies. */
	private static final float EYE_RISE = 0.28F;

	public static ShaderProgram domain;
	public static ShaderProgram rim;
	public static ShaderProgram sky;

	private DomainRender() {
	}

	/** The world's frame, kept for drawing after a shader pack has finished it. */
	private record Frame(Camera camera, Matrix4f view, Matrix4f proj, float tickDelta) {
	}

	@Nullable
	private static Frame deferred;

	/**
	 * At the end of the world's drawing: draws the Domains now, over the world as it stands; or, with an Iris shader pack
	 * in use (which draws the world into its own pictures and lays them over the screen only at the very end, over
	 * anything drawn here), keeps what it needs and draws them once the pack is done ({@link #afterWorld}).
	 */
	public static void render(WorldRenderContext context) {
		if (domain == null || ClientDomains.all().isEmpty()) {
			return;
		}
		Frame frame = new Frame(context.camera(), new Matrix4f(context.positionMatrix()), new Matrix4f(context.projectionMatrix()),
				context.tickCounter().getTickDelta(false));
		if (ShaderPacks.inUse()) {
			deferred = frame;
			return;
		}
		draw(frame);
	}

	/** After the whole world (a shader pack's last pass and all) is on the screen: the Domains held back for it. */
	public static void afterWorld() {
		Frame frame = deferred;
		deferred = null;
		if (frame != null && domain != null) {
			draw(frame);
		}
	}

	private static void draw(Frame frame) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null || client.player == null) {
			return;
		}
		Camera camera = frame.camera();
		float tickDelta = frame.tickDelta();
		Vec3d cam = camera.getPos();
		Matrix4f view = frame.view();
		Matrix4f proj = frame.proj();
		Framebuffer main = client.getFramebuffer();
		int w = main.textureWidth;
		int h = main.textureHeight;
		float time = (float) (world.getTime() % 24000L) / 20.0F + tickDelta / 20.0F;
		frames++;
		for (ClientDomain d : ClientDomains.all()) {
			double t = d.time(tickDelta);
			if (t >= d.end() || cam.distanceTo(d.center) > DRAWN + d.radius || !onScreen(client, camera, d, cam)) {
				continue;
			}
			boolean inside = d.contains(cam);
			float black = (float) MathHelper.clamp((t - DomainTimeline.CLOSE) / BLACKOUT, 0.0, 1.0);
			float open = (float) DomainTimeline.open(t);
			float ending = (float) MathHelper.clamp((t - d.collapse()) / DomainTimeline.SHATTER, 0.0, 1.0);
			// Let go of, it fades away, as in the show; cut short, it shatters.
			float shatter = d.shattered ? ending : 0.0F;
			float fade = d.shattered ? 0.0F : ending;
			Vec3d facing = d.facing();
			Vector3f eye = new Vector3f((float) facing.x, EYE_RISE, (float) facing.z).normalize();
			Vec3d offset = cam.subtract(d.center);
			Vector3f impact = inside ? new Vector3f(eye) : new Vector3f((float) offset.x, (float) offset.y, (float) offset.z).normalize();
			Vec3d walker = walker(world, d, tickDelta).subtract(d.center);

			boolean voidShows = inside && open > 0.0F && sky != null;
			if (voidShows && (!skyPainted || frames % 2 == 0)) {
				Timings.begin("domain.sky");
				paintSky(time, eye);
				Timings.end();
			}
			Timings.begin("domain.pass");
			DEPTH.ensure(w, h);
			DEPTH.copyDepthFrom(main);
			COPY.ensure(w, h);
			COPY.copyColorFrom(main);
			main.beginWrite(true);
			Post.begin();
			// The pass writes the depth back as it found it, so it needs the depth test on to write at all.
			RenderSystem.enableDepthTest();
			RenderSystem.depthFunc(GL11.GL_ALWAYS);
			RenderSystem.depthMask(true);
			RenderSystem.setShaderTexture(0, COPY.color());
			RenderSystem.setShaderTexture(1, DEPTH.depth());
			RenderSystem.setShaderTexture(2, voidShows ? SKY.color() : 0);
			Shaders.set(domain, "InvViewProj", new Matrix4f(proj).mul(view).invert());
			Shaders.set(domain, "ScreenSize", (float) w, (float) h);
			Shaders.set(domain, "Time", time);
			Shaders.set(domain, "CamOffset", (float) offset.x, (float) offset.y, (float) offset.z);
			Shaders.set(domain, "Radius", d.radius);
			Shaders.set(domain, "Ring", (float) DomainTimeline.ring(t));
			Shaders.set(domain, "Rise", (float) DomainTimeline.rise(t));
			Shaders.set(domain, "Black", black);
			Shaders.set(domain, "Open", open);
			Shaders.set(domain, "Shatter", shatter);
			Shaders.set(domain, "Fade", fade);
			Shaders.set(domain, "Inside", inside ? 1.0F : 0.0F);
			Shaders.set(domain, "Eye", eye);
			Shaders.set(domain, "Walker", (float) walker.x, (float) walker.y, (float) walker.z);
			Shaders.set(domain, "Impact", impact);
			Post.quad(domain);
			Timings.end();
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			RenderSystem.setShaderTexture(0, 0);
			RenderSystem.setShaderTexture(1, 0);
			RenderSystem.setShaderTexture(2, 0);
			// Whatever is in the void with the camera, drawn again over it, until the shards have fallen.
			if (inside && black > 0.0F && ending < 0.85F) {
				Timings.begin("domain.people");
				people(client, world, d, camera, cam, view, proj, eye, tickDelta, Math.min(open, 1.0F - ending), w, h);
				Timings.end();
			}
		}
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		main.beginWrite(true);
	}

	/**
	 * Whether any of a Domain (its sphere, and the ink round its foot) can be on screen: from inside, always; from
	 * outside, only if it lies within the view's widest angle (its diagonal) from where the camera looks.
	 */
	private static boolean onScreen(MinecraftClient client, Camera camera, ClientDomain d, Vec3d cam) {
		Vec3d to = d.center.subtract(cam);
		double dist = to.length();
		double reach = d.radius * 1.3 + 8.0;
		if (dist <= reach) {
			return true;
		}
		Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
		double aspect = (double) client.getWindow().getFramebufferWidth() / Math.max(1, client.getWindow().getFramebufferHeight());
		double halfV = Math.toRadians(client.options.getFov().getValue()) * 0.5;
		double halfDiagonal = Math.atan(Math.tan(halfV) * Math.sqrt(1.0 + aspect * aspect));
		double spread = Math.asin(Math.min(1.0, reach / dist));
		return to.multiply(1.0 / dist).dotProduct(look) > Math.cos(Math.min(Math.PI, halfDiagonal * 1.15 + spread));
	}

	/** Paints the void's smooth sky into the panorama, which wraps round side to side. */
	private static void paintSky(float time, Vector3f eye) {
		boolean made = SKY.width() != SKY_W;
		SKY.ensure(SKY_W, SKY_H);
		if (made) {
			GlStateManager._bindTexture(SKY.color());
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
			GlStateManager._bindTexture(0);
		}
		SKY.bind();
		Post.begin();
		Shaders.set(sky, "Time", time);
		Shaders.set(sky, "Eye", eye);
		Post.quad(sky);
		skyPainted = true;
		MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
	}

	/** Where the caster stands now (the void's water ripples out round him), or the middle if there is none here. */
	private static Vec3d walker(ClientWorld world, ClientDomain d, float tickDelta) {
		PlayerEntity caster = d.caster != null ? world.getPlayerByUuid(d.caster) : null;
		return caster != null ? caster.getLerpedPos(tickDelta) : d.center;
	}

	/**
	 * Draws everything in the sphere with the camera into a picture of its own (against the world's depth), then lays it
	 * over the void lit by the eye: {@code strength} how much it is in the void's light rather than its own.
	 */
	private static void people(MinecraftClient client, ClientWorld world, ClientDomain d, Camera camera, Vec3d cam,
			Matrix4f view, Matrix4f proj, Vector3f eye, float tickDelta, float strength, int w, int h) {
		Framebuffer main = client.getFramebuffer();
		double r = d.radius + 1.0;
		Entity viewer = camera.getFocusedEntity();
		java.util.List<Entity> drawn = new java.util.ArrayList<>();
		for (Entity entity : world.getOtherEntities(null, Box.of(d.center, r * 2.0, r * 2.0, r * 2.0))) {
			if (entity == viewer && !camera.isThirdPerson() || entity instanceof PlayerEntity player && player.isSpectator()
					|| entity.isInvisible() || !d.contains(entity.getLerpedPos(tickDelta))) {
				continue;
			}
			drawn.add(entity);
		}
		if (drawn.isEmpty()) {
			return;
		}
		PEOPLE.begin(w, h, 0.0F, 0.0F, 0.0F, 0.0F);
		PEOPLE.copyDepthFrom(main);
		PEOPLE.bind();
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.set(view);
		RenderSystem.applyModelViewMatrix();
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
		for (Entity entity : drawn) {
			Vec3d p = entity.getLerpedPos(tickDelta);
			client.getEntityRenderDispatcher().render(entity, p.x - cam.x, p.y - cam.y, p.z - cam.z, entity.getYaw(tickDelta), tickDelta,
					new MatrixStack(), consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		}
		consumers.draw();
		modelView.popMatrix();
		RenderSystem.applyModelViewMatrix();

		main.beginWrite(true);
		if (rim == null) {
			return;
		}
		// Where the eye is on the screen, to light the edges facing it.
		Vector4f clip = new Matrix4f(proj).mul(view).transform(new Vector4f(eye.x * 1000.0F, eye.y * 1000.0F, eye.z * 1000.0F, 1.0F));
		boolean ahead = clip.w > 0.0F;
		float ex = ahead ? (clip.x / clip.w) * 0.5F + 0.5F : 0.5F;
		float ey = ahead ? (clip.y / clip.w) * 0.5F + 0.5F : 0.5F;
		Post.begin();
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		RenderSystem.setShaderTexture(0, PEOPLE.color());
		Shaders.set(rim, "ScreenSize", (float) w, (float) h);
		Shaders.set(rim, "EyeUv", ex, ey);
		Shaders.set(rim, "EyeAhead", ahead ? 1.0F : 0.0F);
		Shaders.set(rim, "Strength", MathHelper.clamp(strength, 0.0F, 1.0F));
		Post.quad(rim);
		RenderSystem.setShaderTexture(0, 0);
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
	}
}
