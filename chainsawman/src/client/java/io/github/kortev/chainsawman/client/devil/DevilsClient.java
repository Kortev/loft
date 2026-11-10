package io.github.kortev.chainsawman.client.devil;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** The devils on the client: their renderers, and the juice a Tomato leaves on the ground when it bursts (gone again in 15 s). */
public final class DevilsClient {
	static final Identifier SPLAT = Identifier.of("chainsawman", "textures/entity/tomato_splat.png");
	/** How long a splat lies, and how long it takes to fade (ticks). */
	static final int SPLAT_FOR = 300;
	static final int SPLAT_FADE = 80;

	private record Splat(Vec3d at, long when, float turn) {
	}

	private static final List<Splat> SPLATS = new ArrayList<>();

	private DevilsClient() {
	}

	/** Called once from the client's init. */
	public static void init() {
		EntityRendererRegistry.register(Devils.TOMATO_DEVIL, TomatoDevilRenderer::new);
		EntityRendererRegistry.register(Devils.TOMATO_SEED, TomatoSeedRenderer::new);
		EntityRendererRegistry.register(Devils.TOMATO_SPIT, TomatoSpitRenderer::new);
		TomatoDevilEntity.clientBurst = e -> {
			double y = TomatoBody.ground(MinecraftClient.getInstance().world, e.getPos(), e.getY());
			SPLATS.add(new Splat(new Vec3d(e.getX(), y, e.getZ()), e.getWorld().getTime(), e.getWorld().random.nextFloat() * MathHelper.TAU));
		};
		WorldRenderEvents.AFTER_ENTITIES.register(DevilsClient::splats);
	}

	private static void splats(WorldRenderContext context) {
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.world == null || SPLATS.isEmpty()) {
			SPLATS.clear();
			return;
		}
		long now = c.world.getTime();
		SPLATS.removeIf(s -> now - s.when() > SPLAT_FOR || s.when() > now + 20);
		VertexConsumerProvider consumers = context.consumers();
		if (consumers == null) {
			return;
		}
		Vec3d cam = context.camera().getPos();
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucent(SPLAT));
		Matrix4f m = new Matrix4f(context.matrixStack() != null ? context.matrixStack().peek().getPositionMatrix() : new Matrix4f());
		for (Splat s : SPLATS) {
			float age = now - s.when();
			float a = MathHelper.clamp((SPLAT_FOR - age) / SPLAT_FADE, 0.0F, 1.0F);
			float grow = MathHelper.clamp(age / 4.0F, 0.3F, 1.0F);
			float r = 3.2F * grow;
			float x = (float) (s.at().x - cam.x);
			float y = (float) (s.at().y - cam.y) + 0.02F;
			float z = (float) (s.at().z - cam.z);
			float cs = MathHelper.cos(s.turn()) * r;
			float sn = MathHelper.sin(s.turn()) * r;
			int light = LightmapTextureManager.pack(10, 15);
			int alpha = (int) (230 * a);
			v(vc, m, x - cs + sn, y, z - sn - cs, 0, 0, alpha, light);
			v(vc, m, x - cs - sn, y, z - sn + cs, 0, 1, alpha, light);
			v(vc, m, x + cs - sn, y, z + sn + cs, 1, 1, alpha, light);
			v(vc, m, x + cs + sn, y, z + sn - cs, 1, 0, alpha, light);
		}
	}

	private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float vv, int a, int light) {
		vc.vertex(m, x, y, z).color(255, 255, 255, a).texture(u, vv).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0, 1, 0);
	}
}
