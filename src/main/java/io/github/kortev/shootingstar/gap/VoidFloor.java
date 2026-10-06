package io.github.kortev.shootingstar.gap;

import java.util.function.ToDoubleFunction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The floor of nothing everyone walks on while their world is gone and coming back. The world is still there under
 * the black, hills, trees, water and all, but none of it can be seen, so none of it is there to walk into: a player
 * on the floor goes through it all at one height, flat, the floor holding them wherever the ground has gone.
 * <p>
 * The server lets them go where they like then ({@link GapManager#held}); each client keeps its own player on its
 * floor (the floor's height comes from the server) and out of the water and the walls it cannot see.
 */
public final class VoidFloor {
	/** Set by the client: how high the local player's floor is, for that player on the client; NaN for anyone else or none. */
	public static ToDoubleFunction<Entity> client = entity -> Double.NaN;

	private VoidFloor() {
	}

	/** The height of the floor {@code entity} walks on, if it is the client's own player on one; NaN if not. */
	public static double floorFor(Entity entity) {
		return entity.getWorld().isClient() ? client.applyAsDouble(entity) : Double.NaN;
	}

	/** Whether {@code entity} is a player walking on the floor of nothing, on whichever side this is. */
	public static boolean floating(Entity entity) {
		if (!(entity instanceof PlayerEntity)) {
			return false;
		}
		if (entity.getWorld().isClient()) {
			return !Double.isNaN(client.applyAsDouble(entity));
		}
		return entity instanceof ServerPlayerEntity player && GapManager.held(player);
	}
}
