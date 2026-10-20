package io.github.kortev.chainsawman.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * The chains fired from the arm ({@link ClientCombat.Chain}), as the anime draws them: the saw's own chain, teeth and
 * all ({@code form_saw_chain.png}), off the end of the right arm's saw. Thrown, it whips out in a wave that dies down as
 * it goes; caught on something it's pulled taut, humming; missing, it comes back slack. Drawn into the world with the
 * entities, so it's lit and fogged like them, a strip turned to face you all along its length.
 */
public final class ChainRender {
	private static final Identifier CHAIN = io.github.kortev.chainsawman.ChainsawMan.id("textures/entity/form_saw_chain.png");
	private static final Identifier LINK = io.github.kortev.chainsawman.ChainsawMan.id("textures/entity/form_chain.png");
	/** How wide the chain is, how long one link of its texture, how long each drawn piece of it (blocks). */
	private static final double HALF_WIDE = 0.075;
	private static final double LINK_LONG = 0.3;
	private static final double PIECE = 0.15;
	/** How long the chain takes to reach (ticks), and when one that missed starts back. */
	private static final float OUT = 3.0F;
	private static final float BACK_FROM = 7.0F;

	/** Where each player's right saw ends, as it was last drawn (world space, and when). */
	private record Tip(Vec3d at, float time) {
	}

	private static final Map<UUID, Tip> TIPS = new ConcurrentHashMap<>();

	private ChainRender() {
	}

	/** The right saw's end where {@code player}'s arm is drawn now (as the form draws it in the world). */
	static void tip(PlayerEntity player, Vec3d at, float time) {
		TIPS.put(player.getUuid(), new Tip(at, time));
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
		Matrix4f m = matrices.peek().getPositionMatrix();
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
			Vec3d from = fist(shooter, world.getTime() + tickDelta, tickDelta);
			Vec3d to = chain.to();
			if (chain.entity() >= 0) {
				Entity caught = world.getEntityById(chain.entity());
				if (caught != null) {
					to = caught.getLerpedPos(tickDelta).add(0.0, caught.getHeight() * 0.6, 0.0);
				}
			}
			boolean missed = chain.entity() == -2;
			float reach = MathHelper.clamp(age / OUT, 0.0F, 1.0F);
			if (missed) {
				reach *= 1.0F - MathHelper.clamp((age - BACK_FROM) / (ClientCombat.CHAIN - BACK_FROM), 0.0F, 1.0F);
			}
			Vec3d end = from.lerp(to, reach);
			// Thrown: a wave running out along it, dying down; caught: taut, humming; missed: slack, coming back.
			double wave = 0.32 * Math.exp(-age / 3.5) + (missed ? 0.0 : 0.012);
			double sag = missed ? 0.45 * MathHelper.clamp((age - 4.0F) / 3.0F, 0.0F, 1.0F) : 0.0;
			strip(m, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(CHAIN)), world, cam, from, end, age, wave, sag, !chain.held() || missed);
			hook(m, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(LINK)), world, cam, end, end.subtract(from));
		}
	}

	/**
	 * Where the chain leaves {@code shooter}: the end of their right saw as it was just drawn; else (in your own eyes,
	 * where your arm isn't drawn in the world) low on the right of the view.
	 */
	private static Vec3d fist(PlayerEntity shooter, float now, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d look = shooter.getRotationVec(tickDelta);
		Vec3d right = Vec3d.fromPolar(0.0F, shooter.getYaw(tickDelta) + 90.0F);
		if (shooter == client.player && !client.gameRenderer.getCamera().isThirdPerson()) {
			return client.gameRenderer.getCamera().getPos().add(look.multiply(0.7)).add(right.multiply(0.3)).add(0.0, -0.25, 0.0);
		}
		Tip tip = TIPS.get(shooter.getUuid());
		if (tip != null && Math.abs(tip.time() - now) < 0.5F) {
			return tip.at();
		}
		Vec3d shoulder = shooter.getLerpedPos(tickDelta).add(0.0, shooter.getHeight() * 0.78, 0.0).add(right.multiply(0.37));
		return shoulder.add(look.multiply(1.4));
	}

	/**
	 * The chain from {@code a} to {@code b} (world space): pieces along it, each a strip turned to face the camera,
	 * {@code wave} blocks of wave running along it, {@code sag} blocks of sag in its middle; its links running along it
	 * as it goes out and comes back ({@code racing}).
	 */
	private static void strip(Matrix4f m, VertexConsumer out, ClientWorld world, Vec3d cam, Vec3d a, Vec3d b, float age, double wave, double sag,
			boolean racing) {
		Vec3d d = b.subtract(a);
		double length = d.length();
		if (length < 1.0E-3) {
			return;
		}
		Vec3d dir = d.multiply(1.0 / length);
		Vec3d up = Math.abs(dir.y) > 0.95 ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0);
		Vec3d across = dir.crossProduct(up).normalize();
		int count = Math.max(2, (int) Math.ceil(length / PIECE));
		Vec3d[] points = new Vec3d[count + 1];
		for (int i = 0; i <= count; i++) {
			double s = (double) i / count;
			double bulge = Math.sin(Math.PI * s);
			double w = wave * Math.sin(s * Math.PI * 3.0 - age * 1.7) * bulge;
			points[i] = a.add(d.multiply(s)).add(across.multiply(w)).add(0.0, -sag * bulge + w * 0.5, 0.0);
		}
		double scroll = racing ? age * 1.5 : 0.0;
		double along = 0.0;
		for (int i = 0; i < count; i++) {
			Vec3d p0 = points[i];
			Vec3d p1 = points[i + 1];
			double piece = p1.distanceTo(p0);
			Vec3d tangent = p1.subtract(p0).normalize();
			Vec3d w0 = width(tangent, p0, cam);
			Vec3d w1 = width(tangent, p1, cam);
			float u0 = (float) (along / LINK_LONG - scroll);
			float u1 = (float) ((along + piece) / LINK_LONG - scroll);
			along += piece;
			int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(p0));
			vertex(m, out, p0.subtract(w0).subtract(cam), u0, 1.0F, light);
			vertex(m, out, p0.add(w0).subtract(cam), u0, 0.0F, light);
			vertex(m, out, p1.add(w1).subtract(cam), u1, 0.0F, light);
			vertex(m, out, p1.subtract(w1).subtract(cam), u1, 1.0F, light);
		}
	}

	/** Half the strip's width at {@code at}, across it and turned to face the camera. */
	private static Vec3d width(Vec3d tangent, Vec3d at, Vec3d cam) {
		Vec3d w = tangent.crossProduct(cam.subtract(at));
		return w.lengthSquared() < 1.0E-8 ? new Vec3d(0.0, HALF_WIDE, 0.0) : w.normalize().multiply(HALF_WIDE);
	}

	/** The hook at the end: a big link, crossed. */
	private static void hook(Matrix4f m, VertexConsumer out, ClientWorld world, Vec3d cam, Vec3d at, Vec3d way) {
		if (way.lengthSquared() < 1.0E-6) {
			return;
		}
		Vec3d dir = way.normalize();
		Vec3d up = Math.abs(dir.y) > 0.95 ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0);
		Vec3d side = dir.crossProduct(up).normalize();
		Vec3d flat = side.crossProduct(dir).normalize();
		int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(at));
		for (Vec3d across : new Vec3d[] {side, flat}) {
			Vec3d l = dir.multiply(0.19);
			Vec3d w = across.multiply(0.1);
			Vec3d mid = at.subtract(cam);
			vertex(m, out, mid.subtract(l).subtract(w), 0.0F, 0.0F, light);
			vertex(m, out, mid.subtract(l).add(w), 0.0F, 1.0F, light);
			vertex(m, out, mid.add(l).add(w), 1.0F, 1.0F, light);
			vertex(m, out, mid.add(l).subtract(w), 1.0F, 0.0F, light);
		}
	}

	private static void vertex(Matrix4f m, VertexConsumer out, Vec3d p, float u, float v, int light) {
		out.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
				.normal(0.0F, 1.0F, 0.0F);
	}
}
