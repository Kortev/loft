package io.github.kortev.gojolander.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.kortev.gojolander.domain.DomainTimeline;
import io.github.kortev.shootingstar.client.gfx.Post;
import io.github.kortev.shootingstar.client.gfx.Shaders;
import io.github.kortev.shootingstar.client.gfx.Target;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Draws Domains over the world in one full-screen pass each ({@code gl_domain}): the ink, the black flooding up into a
 * sphere, from outside the sphere itself, from inside the black and the void opening out of the eye, and the shattering.
 *
 * <p>The void takes everything inside it, people too, so whatever is in there with the camera is drawn again, into a
 * picture of its own, and laid back over the void ({@code gl_rim}): dark, lit only by the great eye, its edges towards
 * the eye burning blue-white. The depth is kept throughout, so the walls of the world still hide what they hid.
 */
public final class DomainRender {
	private static final Target DEPTH = new Target(true, false);
	private static final Target COPY = new Target(false, false);
	private static final Target PEOPLE = new Target(true, false);
	/** How far off a Domain is drawn at all. */
	private static final double DRAWN = 512.0;
	/** How long the world inside takes to go to black as it closes. */
	private static final double BLACKOUT = 2.0;
	/** How far above the way he faced the great eye lies. */
	private static final float EYE_RISE = 0.28F;

	public static ShaderProgram domain;
	public static ShaderProgram rim;

	private DomainRender() {
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = context.world();
		if (domain == null || world == null || client.player == null || ClientDomains.all().isEmpty()) {
			return;
		}
		float tickDelta = context.tickCounter().getTickDelta(false);
		Vec3d cam = context.camera().getPos();
		Matrix4f view = new Matrix4f(context.positionMatrix());
		Matrix4f proj = new Matrix4f(context.projectionMatrix());
		Framebuffer main = client.getFramebuffer();
		int w = main.textureWidth;
		int h = main.textureHeight;
		float time = (float) (world.getTime() % 24000L) / 20.0F + tickDelta / 20.0F;
		for (ClientDomain d : ClientDomains.all()) {
			double t = d.time(tickDelta);
			if (t >= d.end() || cam.distanceTo(d.center) > DRAWN + d.radius) {
				continue;
			}
			boolean inside = d.contains(cam);
			float black = (float) MathHelper.clamp((t - DomainTimeline.CLOSE) / BLACKOUT, 0.0, 1.0);
			float open = (float) DomainTimeline.open(t);
			float shatter = (float) MathHelper.clamp((t - d.collapse()) / DomainTimeline.SHATTER, 0.0, 1.0);
			Vec3d facing = d.facing();
			Vector3f eye = new Vector3f((float) facing.x, EYE_RISE, (float) facing.z).normalize();
			Vec3d offset = cam.subtract(d.center);
			Vector3f impact = inside ? new Vector3f(eye) : new Vector3f((float) offset.x, (float) offset.y, (float) offset.z).normalize();
			Vec3d walker = walker(world, d, tickDelta).subtract(d.center);

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
			Shaders.set(domain, "Inside", inside ? 1.0F : 0.0F);
			Shaders.set(domain, "Eye", eye);
			Shaders.set(domain, "Walker", (float) walker.x, (float) walker.y, (float) walker.z);
			Shaders.set(domain, "Impact", impact);
			Post.quad(domain);
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			RenderSystem.setShaderTexture(0, 0);
			RenderSystem.setShaderTexture(1, 0);
			// Whatever is in the void with the camera, drawn again over it, until the shards have fallen.
			if (inside && black > 0.0F && shatter < 0.85F) {
				people(client, world, d, context, cam, view, proj, eye, tickDelta, Math.min(open, 1.0F - shatter), w, h);
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

	/** Where the caster stands now (the void's water ripples out round him), or the middle if there is none here. */
	private static Vec3d walker(ClientWorld world, ClientDomain d, float tickDelta) {
		PlayerEntity caster = d.caster != null ? world.getPlayerByUuid(d.caster) : null;
		return caster != null ? caster.getLerpedPos(tickDelta) : d.center;
	}

	/**
	 * Draws everything in the sphere with the camera into a picture of its own (against the world's depth), then lays it
	 * over the void lit by the eye: {@code strength} how much it is in the void's light rather than its own.
	 */
	private static void people(MinecraftClient client, ClientWorld world, ClientDomain d, WorldRenderContext context, Vec3d cam,
			Matrix4f view, Matrix4f proj, Vector3f eye, float tickDelta, float strength, int w, int h) {
		Framebuffer main = client.getFramebuffer();
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
		double r = d.radius + 1.0;
		Entity viewer = context.camera().getFocusedEntity();
		for (Entity entity : world.getOtherEntities(null, Box.of(d.center, r * 2.0, r * 2.0, r * 2.0))) {
			if (entity == viewer && !context.camera().isThirdPerson()) {
				continue;
			}
			if (entity instanceof PlayerEntity player && player.isSpectator() || entity.isInvisible()
					|| !d.contains(entity.getLerpedPos(tickDelta))) {
				continue;
			}
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
