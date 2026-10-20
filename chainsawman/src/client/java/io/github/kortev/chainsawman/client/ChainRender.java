package io.github.kortev.chainsawman.client;

import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The chains fired from the arm ({@link ClientCombat.Chain}), drawn into the world with the entities so they are lit
 * and fogged like them: from the right fist (or, in your own eyes, from low on the right of the view) out to what they
 * caught, a run of steel links ({@code form_chain.png}), each turned a quarter round from the last, overlapping. Out in a
 * few ticks, held, and (missing) rattled back in.
 */
public final class ChainRender {
	private static final net.minecraft.util.Identifier LINK = io.github.kortev.chainsawman.ChainsawMan.id("textures/entity/form_chain.png");
	/** How far apart the links are, and half how long and wide each is (blocks): they overlap, as a chain's do. */
	private static final double PITCH = 0.17;
	private static final float LINK_HALF = 0.12F;
	private static final float LINK_WIDE = 0.06F;
	/** How long the chain takes to reach (ticks), and when one that missed starts back. */
	private static final float OUT = 3.0F;
	private static final float BACK_FROM = 7.0F;

	private ChainRender() {
	}

	public static void render(WorldRenderContext context) {
		Map<UUID, ClientCombat.Chain> chains = ClientCombat.chains();
		ClientWorld world = context.world();
		VertexConsumerProvider consumers = context.consumers();
		if (chains.isEmpty() || world == null || consumers == null) {
			return;
		}
		float tickDelta = context.tickCounter().getTickDelta(false);
		Vec3d cam = context.camera().getPos();
		MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
		VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(LINK));
		for (Map.Entry<UUID, ClientCombat.Chain> e : chains.entrySet()) {
			PlayerEntity shooter = world.getPlayerByUuid(e.getKey());
			ClientCombat.Chain chain = e.getValue();
			if (shooter == null) {
				continue;
			}
			float age = world.getTime() - chain.at() + tickDelta;
			if (age < 0.0F || !chain.held() && age >= ClientCombat.CHAIN) {
				continue;
			}
			Vec3d from = fist(shooter, tickDelta);
			Vec3d to = chain.to();
			if (chain.entity() >= 0) {
				Entity caught = world.getEntityById(chain.entity());
				if (caught != null) {
					to = caught.getLerpedPos(tickDelta).add(0.0, caught.getHeight() * 0.6, 0.0);
				}
			}
			float reach = MathHelper.clamp(age / OUT, 0.0F, 1.0F);
			if (chain.entity() == -2) {
				reach *= 1.0F - MathHelper.clamp((age - BACK_FROM) / (ClientCombat.CHAIN - BACK_FROM), 0.0F, 1.0F);
			}
			links(matrices.peek().getPositionMatrix(), out, world, from.subtract(cam), from.lerp(to, reach).subtract(cam), from.lerp(to, reach));
		}
	}

	/** Where the chain leaves {@code shooter}: their right fist held out, or, in your own eyes, low on the right of the view. */
	private static Vec3d fist(PlayerEntity shooter, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d look = shooter.getRotationVec(tickDelta);
		Vec3d right = Vec3d.fromPolar(0.0F, shooter.getYaw(tickDelta) + 90.0F);
		if (shooter == client.player && !client.gameRenderer.getCamera().isThirdPerson()) {
			return client.gameRenderer.getCamera().getPos().add(look.multiply(0.5)).add(right.multiply(0.32)).add(0.0, -0.28, 0.0);
		}
		Vec3d shoulder = shooter.getLerpedPos(tickDelta).add(0.0, shooter.getHeight() * 0.78, 0.0).add(right.multiply(0.37));
		return shoulder.add(look.multiply(0.75));
	}

	/** A run of links from {@code a} to {@code b} (both from the camera); {@code end} where it ends in the world, for its light. */
	private static void links(Matrix4f m, VertexConsumer out, ClientWorld world, Vec3d a, Vec3d b, Vec3d end) {
		Vec3d d = b.subtract(a);
		double length = d.length();
		if (length < 1.0E-3) {
			return;
		}
		Vec3d dir = d.multiply(1.0 / length);
		Vec3d up = Math.abs(dir.y) > 0.95 ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0);
		Vec3d side = dir.crossProduct(up).normalize();
		Vec3d flat = side.crossProduct(dir).normalize();
		int count = Math.max(1, (int) (length / PITCH));
		Vec3d start = a;
		for (int i = 0; i < count; i++) {
			Vec3d mid = start.add(dir.multiply(PITCH * (i + 0.5)));
			Vec3d across = i % 2 == 0 ? side : flat;
			Vec3d at = mid.add(MinecraftClient.getInstance().gameRenderer.getCamera().getPos());
			int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(i == count - 1 ? end : at));
			quad(m, out, mid, dir, across, 1.0F, light);
		}
		// The hook at the end: a bigger link, crossed.
		int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(end));
		quad(m, out, b, dir, side, 1.6F, light);
		quad(m, out, b, dir, flat, 1.6F, light);
	}

	/** One link: the ring along {@code dir} round {@code mid}, lying in the plane of {@code across}, {@code size} times as big. */
	private static void quad(Matrix4f m, VertexConsumer out, Vec3d mid, Vec3d dir, Vec3d across, float size, int light) {
		Vec3d l = dir.multiply(LINK_HALF * size);
		Vec3d w = across.multiply(LINK_WIDE * size);
		Vector3f n = across.crossProduct(dir).toVector3f();
		Vec3d[] corners = {mid.subtract(l).subtract(w), mid.subtract(l).add(w), mid.add(l).add(w), mid.add(l).subtract(w)};
		float[][] uv = {{0.0F, 0.0F}, {0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}};
		for (int i = 0; i < 4; i++) {
			Vec3d p = corners[i];
			out.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(0xFFFFFFFF).texture(uv[i][0], uv[i][1]).overlay(OverlayTexture.DEFAULT_UV)
					.light(light).normal(n.x, n.y, n.z);
		}
	}
}
