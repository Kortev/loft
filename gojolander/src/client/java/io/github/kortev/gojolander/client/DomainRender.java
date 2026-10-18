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
import org.lwjgl.opengl.GL11;

/**
 * Draws Domains over the world in one full-screen pass each ({@code gl_domain}): the ring of ink spreading over the
 * ground, the black climbing out of it into a sphere, from outside the sphere itself, from inside the void, and the
 * shattering. The void takes everything inside it, people too, so whatever is in there with the camera is drawn again
 * over it, lit as if by the void's own light, where it stood (the depth is kept, so the walls of the world still hide
 * what they hid).
 */
public final class DomainRender {
	private static final Target DEPTH = new Target(true, false);
	private static final Target COPY = new Target(false, false);
	/** How far off a Domain is drawn at all. */
	private static final double DRAWN = 512.0;
	/** How long the world inside takes to go into the void, up to {@link DomainTimeline#VOID}. */
	private static final double FADE = 6.0;
	/** How far above the way he faced the great eye lies, and how far away it seems. */
	private static final float EYE_RISE = 0.28F;

	public static ShaderProgram domain;

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
			float ring = (float) DomainTimeline.ring(t);
			float rise = (float) DomainTimeline.rise(t);
			float voidness = (float) MathHelper.clamp((t - (DomainTimeline.VOID - FADE)) / FADE, 0.0, 1.0);
			float shatter = (float) MathHelper.clamp((t - d.collapse()) / DomainTimeline.SHATTER, 0.0, 1.0);
			Vec3d facing = d.facing();
			Vector3f eye = new Vector3f((float) facing.x, EYE_RISE, (float) facing.z).normalize();

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
			Vec3d offset = cam.subtract(d.center);
			Shaders.set(domain, "InvViewProj", new Matrix4f(proj).mul(view).invert());
			Shaders.set(domain, "ScreenSize", (float) w, (float) h);
			Shaders.set(domain, "Time", time);
			Shaders.set(domain, "CamOffset", (float) offset.x, (float) offset.y, (float) offset.z);
			Shaders.set(domain, "Radius", d.radius);
			Shaders.set(domain, "Ring", ring);
			Shaders.set(domain, "Rise", rise);
			Shaders.set(domain, "Void", voidness);
			Shaders.set(domain, "Shatter", shatter);
			Shaders.set(domain, "Inside", inside ? 1.0F : 0.0F);
			Shaders.set(domain, "Eye", eye);
			Post.quad(domain);
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			RenderSystem.setShaderTexture(0, 0);
			RenderSystem.setShaderTexture(1, 0);
			// Whatever is in the void with the camera, drawn again over it, until the shards have fallen.
			if (inside && voidness > 0.0F && shatter < 0.8F) {
				redraw(client, world, d, context, cam, view, tickDelta);
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

	private static void redraw(MinecraftClient client, ClientWorld world, ClientDomain d, WorldRenderContext context, Vec3d cam,
			Matrix4f view, float tickDelta) {
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
	}
}
