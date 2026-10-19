package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.form.FormTimeline;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Who is in the form, as the server has told us; who is pulling the cord on the way into it, and since when; and the
 * engine idling on each of them.
 */
public final class ClientForms {
	private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();
	/** Who pulled the cord, and the world time they pulled it at. */
	private static final Map<UUID, Long> PULLED = new ConcurrentHashMap<>();
	private static final Map<UUID, FormIdleSound> IDLING = new ConcurrentHashMap<>();

	private ClientForms() {
	}

	public static boolean on(PlayerEntity player) {
		return ON.contains(player.getUuid());
	}

	/** Puts {@code player} in or out of the form here (as the server says; or a test's stand-in, which the server never sees). */
	public static void set(UUID player, boolean on) {
		if (on) {
			ON.add(player);
		} else {
			ON.remove(player);
		}
	}

	/** {@code player} pulled the cord at {@code worldTime} (as the server says; or a test's stand-in). */
	public static void pulled(UUID player, long worldTime) {
		PULLED.put(player, worldTime);
	}

	/** How many ticks ago {@code player} pulled the cord, while the yank is still playing out; otherwise -1. */
	public static float pullAge(PlayerEntity player, float tickDelta) {
		Long at = PULLED.get(player.getUuid());
		if (at == null) {
			return -1.0F;
		}
		float age = player.getWorld().getTime() - at + tickDelta;
		return age >= 0.0F && age <= FormTimeline.SETTLE ? age : -1.0F;
	}

	/** Every tick: the engine idling on whoever is in the form here, and not on anyone else; old pulls forgotten. */
	static void tick(MinecraftClient client) {
		if (client.world == null) {
			return;
		}
		long now = client.world.getTime();
		PULLED.values().removeIf(at -> now - at > FormTimeline.SETTLE + 20);
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (ON.contains(player.getUuid()) && !IDLING.containsKey(player.getUuid())) {
				FormIdleSound sound = new FormIdleSound(player);
				IDLING.put(player.getUuid(), sound);
				client.getSoundManager().play(sound);
			}
		}
		IDLING.values().removeIf(FormIdleSound::isDone);
	}

	static void clear() {
		ON.clear();
		PULLED.clear();
		IDLING.clear();
	}
}
