package io.github.kortev.gojolander.domain;

import java.util.function.Predicate;
import net.minecraft.entity.Entity;

/**
 * Who a Domain holds still, asked by the movement mixin on either side: on the server its {@link DomainManager}, on a
 * client what that client was told (set by the client mod: the local player, once it has been caught).
 */
public final class DomainHold {
	/** On a client: whether this entity is the local player, caught and held. */
	public static Predicate<Entity> client = entity -> false;

	private DomainHold() {
	}

	/** Whether {@code entity} is held still by a Domain: no moving, falling, being knocked about or thinking. */
	public static boolean held(Entity entity) {
		return entity.getWorld().isClient() ? client.test(entity) : DomainManager.isHeld(entity);
	}
}
