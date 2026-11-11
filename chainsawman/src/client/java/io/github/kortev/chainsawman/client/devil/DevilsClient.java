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
import net.minecraft.util.math.BlockPos;
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

	/** A chunk of skin thrown off by the burst: where from, its throw, its spin, where it lands (the ground's height), when. */
	private record Chunk(Vec3d from, Vec3d v, org.joml.Vector3f axis, float spin, float turn, double groundY, long when, float size) {
	}

	private static final List<Chunk> CHUNKS = new ArrayList<>();
	/** The chunks: how many, their fall, how long they lie, how long they take to sink away. */
	static final int CHUNK_COUNT = 7;
	static final double CHUNK_G = 0.06;
	static final int CHUNK_LIE = 70;
	static final int CHUNK_FADE = 30;

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
			// The skin splits into big chunks that fly out, land and fade.
			var r = e.getWorld().random;
			double start = r.nextDouble() * MathHelper.TAU;
			for (int i = 0; i < CHUNK_COUNT; i++) {
				double a = start + i * MathHelper.TAU / CHUNK_COUNT + (r.nextDouble() - 0.5) * 0.6;
				double sp = 0.16 + r.nextDouble() * 0.14;
				Vec3d v = new Vec3d(Math.cos(a) * sp, 0.28 + r.nextDouble() * 0.22, Math.sin(a) * sp);
				Vec3d from = e.getPos().add(Math.cos(a) * 0.9, 2.2 + (r.nextDouble() - 0.5) * 1.2, Math.sin(a) * 0.9);
				org.joml.Vector3f axis = new org.joml.Vector3f(r.nextFloat() - 0.5F, r.nextFloat() - 0.5F, r.nextFloat() - 0.5F).normalize();
				// where it will come down
				double t = flight(from.y, v.y, y);
				Vec3d land = from.add(v.x * t, 0, v.z * t);
				double gy = TomatoBody.ground(MinecraftClient.getInstance().world, land, y);
				CHUNKS.add(new Chunk(from, v, axis, 0.25F + r.nextFloat() * 0.35F, (float) a, gy, e.getWorld().getTime(), 0.9F + r.nextFloat() * 0.5F));
			}
		};
		WorldRenderEvents.AFTER_ENTITIES.register(DevilsClient::splats);
		WorldRenderEvents.AFTER_ENTITIES.register(DevilsClient::chunks);
	}

	/** Ticks till something thrown up at {@code vy} from {@code y0} comes down to {@code ground}. */
	private static double flight(double y0, double vy, double ground) {
		double a = 0.5 * CHUNK_G;
		double c = ground + 0.1 - y0;
		double disc = vy * vy - 4 * a * c;
		return disc < 0 ? 20.0 : (vy + Math.sqrt(disc)) / (2 * a);
	}

	private static void chunks(WorldRenderContext context) {
		MinecraftClient c = MinecraftClient.getInstance();
		if (c.world == null || CHUNKS.isEmpty() || context.matrixStack() == null) {
			CHUNKS.clear();
			return;
		}
		float now = c.world.getTime() + c.getRenderTickCounter().getTickDelta(true);
		CHUNKS.removeIf(k -> now - k.when() > 100 + CHUNK_LIE + CHUNK_FADE || k.when() > now + 20);
		io.github.kortev.chainsawman.client.FleshMesh parts = io.github.kortev.chainsawman.client.FleshMesh.file("tomato_parts");
		io.github.kortev.chainsawman.client.FleshMesh.Part part = parts.byName.get("chunk");
		if (part == null) {
			return;
		}
		Vec3d cam = context.camera().getPos();
		Matrix4f view = new Matrix4f(context.matrixStack().peek().getPositionMatrix());
		for (Chunk k : CHUNKS) {
			float age = now - k.when();
			double tl = flight(k.from().y, k.v().y, k.groundY());
			double t = Math.min(age, tl);
			Vec3d p = k.from().add(k.v().x * t, k.v().y * t - 0.5 * CHUNK_G * t * t, k.v().z * t);
			boolean down = age >= tl;
			float lie = (float) (age - tl);
			float sink = down ? MathHelper.clamp((lie - CHUNK_LIE) / CHUNK_FADE, 0.0F, 1.0F) : 0.0F;
			if (sink >= 1.0F) {
				continue;
			}
			BlockPos bp = BlockPos.ofFloored(p.x, p.y + 0.2, p.z);
			int light = Math.max(c.world.getLightLevel(net.minecraft.world.LightType.BLOCK, bp),
					c.world.getLightLevel(net.minecraft.world.LightType.SKY, bp) - c.world.getAmbientDarkness());
			io.github.kortev.chainsawman.client.FleshMesh.Look look = new io.github.kortev.chainsawman.client.FleshMesh.Look();
			float kk = 0.9F * (0.36F + 0.64F * MathHelper.clamp(light / 15.0F, 0.0F, 1.0F));
			look.r = kk;
			look.g = kk;
			look.b = kk;
			look.rim = 0.3F;
			// tumbling in the air; landed, it lies skin up, settling, then sinks into the ground and is gone
			float tumble = (float) t * k.spin();
			Matrix4f m = new Matrix4f(view).translate((float) (p.x - cam.x), (float) (p.y - cam.y) + (down ? 0.06F - 0.25F * sink : 0.0F), (float) (p.z - cam.z));
			m.rotateY(k.turn());
			if (down) {
				m.rotateX(-MathHelper.HALF_PI + 0.25F * MathHelper.sin(k.turn() * 3.0F));
			} else {
				m.rotate(tumble, k.axis());
			}
			m.scale(k.size() * (1.0F - 0.4F * sink));
			parts.drawPart(part, m, look, view);
		}
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
