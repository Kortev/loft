package io.github.kortev.chainsawman.client;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.PlayerEntity;

/** Who is in the form, as the server has told us. */
public final class ClientForms {
	private static final Set<UUID> ON = ConcurrentHashMap.newKeySet();

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

	static void clear() {
		ON.clear();
	}
}
