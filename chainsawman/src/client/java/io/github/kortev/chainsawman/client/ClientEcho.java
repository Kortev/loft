package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.contract.EchoEntity;
import io.github.kortev.chainsawman.network.EchoPayload;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Echo as this client knows it: whose echo is going (and how they are disguised among their copies: past or future), and where
 * each copy and each disguised owner has been the last few ticks (the afterimages a past copy drags behind it, the path a
 * future copy is heading along).
 */
public final class ClientEcho {
	/** How many ticks of where something has been are kept. */
	public static final int KEEP = 16;

	public record Info(int kind, int target, long finaleAt) {
	}

	private static final Map<UUID, Info> OWNERS = new ConcurrentHashMap<>();
	private static final Map<Integer, ArrayDeque<Vec3d>> TRAILS = new ConcurrentHashMap<>();

	private ClientEcho() {
	}

	public static void told(EchoPayload payload) {
		if (payload.kind() < 0) {
			OWNERS.remove(payload.owner());
		} else {
			OWNERS.put(payload.owner(), new Info(payload.kind(), payload.target(), payload.finale()));
		}
	}

	public static Info of(UUID owner) {
		return OWNERS.get(owner);
	}

	/** Whether {@code player} is drawn as one of their copies (not the player looking: they know which one they are). */
	public static boolean disguised(PlayerEntity player) {
		MinecraftClient client = MinecraftClient.getInstance();
		return OWNERS.containsKey(player.getUuid()) && player != client.player;
	}

	/** How strong the time-warp look is for the player here: 1 for the owner of an echo, a little for anyone near one, else 0. */
	public static float strength(MinecraftClient client) {
		if (client.player == null || client.world == null || OWNERS.isEmpty()) {
			return 0.0F;
		}
		if (OWNERS.containsKey(client.player.getUuid())) {
			return 1.0F;
		}
		float best = 0.0F;
		for (UUID id : OWNERS.keySet()) {
			PlayerEntity other = client.world.getPlayerByUuid(id);
			if (other != null) {
				double d = other.distanceTo(client.player);
				best = Math.max(best, (float) Math.max(0.0, 1.0 - d / 28.0) * 0.35F);
			}
		}
		return best;
	}

	/** Ticks since this client's own player's finale began, or -1. */
	public static boolean mine(MinecraftClient client) {
		return client.player != null && OWNERS.containsKey(client.player.getUuid());
	}

	public static boolean any() {
		return !OWNERS.isEmpty();
	}

	/** Where {@code entity} was, newest first, one entry a tick (empty if not tracked). */
	public static List<Vec3d> trail(Entity entity) {
		ArrayDeque<Vec3d> trail = TRAILS.get(entity.getId());
		return trail == null ? List.of() : List.copyOf(trail);
	}

	public static void clear() {
		OWNERS.clear();
		TRAILS.clear();
	}

	/** Each tick: note where the copies and the disguised owners are. */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			TRAILS.clear();
			return;
		}
		if (OWNERS.isEmpty() && TRAILS.isEmpty()) {
			return;
		}
		for (Entity e : client.world.getEntities()) {
			boolean tracked = e instanceof EchoEntity || e instanceof PlayerEntity p && OWNERS.containsKey(p.getUuid());
			if (!tracked) {
				continue;
			}
			ArrayDeque<Vec3d> trail = TRAILS.computeIfAbsent(e.getId(), k -> new ArrayDeque<>());
			trail.addFirst(e.getPos());
			while (trail.size() > KEEP) {
				trail.removeLast();
			}
		}
		// Forget what is gone.
		for (Iterator<Integer> it = TRAILS.keySet().iterator(); it.hasNext();) {
			if (client.world.getEntityById(it.next()) == null) {
				it.remove();
			}
		}
	}
}
