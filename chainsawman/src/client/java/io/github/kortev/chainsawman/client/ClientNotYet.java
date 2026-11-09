package io.github.kortev.chainsawman.client;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Who is out of time ("Not yet", {@code NotYetPayload}): they are drawn grey, with copies of them trailing behind (where they were a few
 * ticks ago, in the stride they were in, each fainter), and their own screen drains grey.
 */
public final class ClientNotYet {
	/** One tick of where a player was. */
	private record Pose(Vec3d pos, float yaw, float stride, float speed) {
	}

	private static final class State {
		long until;
		final ArrayDeque<Pose> trail = new ArrayDeque<>();
	}

	/** How many copies trail, and how many ticks apart. */
	private static final int COPIES = 5;
	private static final int GAP = 3;
	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private ClientNotYet() {
	}

	public static void set(UUID player, int ticks) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ticks <= 0 || client.world == null) {
			STATES.remove(player);
			return;
		}
		State s = new State();
		s.until = client.world.getTime() + ticks;
		STATES.put(player, s);
	}

	public static boolean out(PlayerEntity player) {
		return STATES.containsKey(player.getUuid());
	}

	/** How far through it the player here is (0 to 1), or -1 when they are not out of time. */
	public static float mine(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return -1.0F;
		}
		State s = STATES.get(client.player.getUuid());
		return s == null ? -1.0F : 1.0F - MathHelper.clamp((s.until - client.world.getTime()) / 30.0F, 0.0F, 1.0F);
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			STATES.clear();
			return;
		}
		long now = client.world.getTime();
		for (Map.Entry<UUID, State> e : STATES.entrySet()) {
			PlayerEntity p = client.world.getPlayerByUuid(e.getKey());
			if (p == null || now > e.getValue().until + 5) {
				STATES.remove(e.getKey());
				continue;
			}
			e.getValue().trail.addFirst(new Pose(p.getPos(), p.getBodyYaw(), p.limbAnimator.getPos(), Math.max(0.5F, p.limbAnimator.getSpeed())));
			while (e.getValue().trail.size() > COPIES * GAP + 2) {
				e.getValue().trail.removeLast();
			}
		}
	}

	/** The grey wash on the body itself and the copies trailing it, in the world. */
	public static void render(WorldRenderContext context) {
		if (STATES.isEmpty() || context.consumers() == null || context.world() == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		float delta = context.tickCounter().getTickDelta(false);
		Vec3d cam = context.camera().getPos();
		for (Map.Entry<UUID, State> e : STATES.entrySet()) {
			PlayerEntity p = context.world().getPlayerByUuid(e.getKey());
			if (!(p instanceof AbstractClientPlayerEntity player)) {
				continue;
			}
			Vec3d here = player.getLerpedPos(delta);
			MatrixStack matrices = context.matrixStack() != null ? context.matrixStack() : new MatrixStack();
			int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
			Pose[] trail = e.getValue().trail.toArray(new Pose[0]);
			for (int i = 1; i <= COPIES; i++) {
				int at = i * GAP;
				if (at >= trail.length) {
					break;
				}
				Pose pose = trail[at];
				float[] tint = {0.82F, 0.84F, 0.86F, 0.5F * (1.0F - (i - 1) / (float) COPIES)};
				matrices.push();
				matrices.translate(here.x - cam.x, here.y - cam.y, here.z - cam.z);
				ForeverCopyRenderer.drawTarget(player, client.getEntityRenderDispatcher(), pose.yaw, pose.stride, pose.speed, false, tint, true, 1.0F,
						pose.pos.subtract(here), matrices, context.consumers(), light, delta);
				matrices.pop();
			}
			boolean firstPerson = player == client.player && client.options.getPerspective().isFirstPerson();
			if (!firstPerson) {
				float[] tint = {0.8F, 0.82F, 0.85F, 0.6F};
				matrices.push();
				matrices.translate(here.x - cam.x, here.y - cam.y, here.z - cam.z);
				ForeverCopyRenderer.drawTarget(player, client.getEntityRenderDispatcher(), 0.0F, 0.0F, 0.0F, true, tint, true, 1.012F, Vec3d.ZERO, matrices,
						context.consumers(), light, delta);
				matrices.pop();
			}
		}
	}

	public static void clear() {
		STATES.clear();
	}
}
