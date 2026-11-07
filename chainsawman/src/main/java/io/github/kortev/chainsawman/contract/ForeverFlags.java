package io.github.kortev.chainsawman.contract;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which living things are locked in an endless moment ({@link Forever}), by entity id: one set for the server's entities and one
 * for this client's (the client is told with {@code ForeverLockPayload}). The mixins that freeze a locked thing read these; an
 * integrated server and its client share ids, so each side asks its own set.
 */
public final class ForeverFlags {
	public static final Set<Integer> SERVER = ConcurrentHashMap.newKeySet();
	public static final Set<Integer> CLIENT = ConcurrentHashMap.newKeySet();

	private ForeverFlags() {
	}

	public static boolean locked(net.minecraft.entity.Entity entity) {
		return (entity.getWorld().isClient ? CLIENT : SERVER).contains(entity.getId());
	}
}
