package io.github.kortev.shootingstar.gap;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * For the film self test: where a player gathered in the void is sent home to when the world comes back, set while
 * they wait (the event remembers where each came from in its saved state, which only this package can reach).
 */
public final class FilmHomes {
	private FilmHomes() {
	}

	public static void set(ServerPlayerEntity player, ServerWorld world, Vec3d feet, float yaw, float pitch) {
		GapState state = GapState.get(world.getServer());
		state.homes.put(player.getUuid(), new GapState.Home(world.getRegistryKey(), feet, yaw, pitch));
		state.markDirty();
	}
}
