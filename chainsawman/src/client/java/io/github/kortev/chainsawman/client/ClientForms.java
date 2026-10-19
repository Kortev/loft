package io.github.kortev.chainsawman.client;

import io.github.kortev.chainsawman.form.FormTimeline;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Who is in the form, as the server has told us; who is going through the transformation and since when; and the
 * engine idling on each of them.
 */
public final class ClientForms {
	private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();
	/** Who pulled the cord: the world time they pulled it at and which way they faced. */
	private static final Map<UUID, Pull> PULLED = new ConcurrentHashMap<>();
	private static final Map<UUID, FormIdleSound> IDLING = new ConcurrentHashMap<>();

	/** A pull of the cord: when, and which way its puller faced (the camera's shots are set round that). */
	public record Pull(long at, float yaw) {
	}

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

	/** {@code player} pulled the cord at {@code worldTime} facing {@code yaw} (as the server says; or a test's stand-in). */
	public static void pulled(UUID player, long worldTime, float yaw) {
		PULLED.put(player, new Pull(worldTime, yaw));
	}

	@Nullable
	public static Pull pull(PlayerEntity player) {
		return PULLED.get(player.getUuid());
	}

	/** How many ticks ago {@code player} pulled the cord, while the transformation is playing out; otherwise -1. */
	public static float pullAge(PlayerEntity player, float tickDelta) {
		Pull pull = PULLED.get(player.getUuid());
		if (pull == null) {
			return -1.0F;
		}
		float age = player.getWorld().getTime() - pull.at() + tickDelta;
		return age >= 0.0F && age <= FormTimeline.END ? age : -1.0F;
	}

	/**
	 * How far {@code player}'s saws have grown out (0 to 1): the head's, or the arms'. Whoever is in the form and not
	 * transforming has them out in full.
	 */
	public static float grown(PlayerEntity player, float tickDelta, boolean arms) {
		float t = pullAge(player, tickDelta);
		if (t < 0.0F) {
			return 1.0F;
		}
		float from = arms ? FormTimeline.ARMS : FormTimeline.BURST;
		float to = arms ? FormTimeline.ARMS_GROWN : FormTimeline.HEAD_GROWN;
		float k = Math.max(0.0F, Math.min(1.0F, (t - from) / (to - from)));
		// Out fast, then easing into place.
		return 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
	}

	/** Every tick: the engine idling on whoever is in the form here, and not on anyone else; old pulls forgotten. */
	static void tick(MinecraftClient client) {
		if (client.world == null) {
			return;
		}
		long now = client.world.getTime();
		PULLED.values().removeIf(pull -> now - pull.at() > FormTimeline.END + 20);
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			// Idling once the transformation (whose own sound runs into the idle) is over.
			if (ON.contains(player.getUuid()) && !IDLING.containsKey(player.getUuid()) && pullAge(player, 0.0F) < 0.0F) {
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
