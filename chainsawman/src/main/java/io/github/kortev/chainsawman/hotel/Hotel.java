package io.github.kortev.chainsawman.hotel;

import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** What a hotel does, run by its home's block entity every tick ({@link HomeBlockEntity}). */
public final class Hotel {
	private Hotel() {
	}

	/** What is going on in one hotel right now (not saved). */
	public static final class Run {
	}

	public static void tick(ServerWorld world, HomeBlockEntity home) {
	}

	/** Puts a hotel down at {@code origin} turned {@code rot}: its home, or null if the home could not be found. */
	public static HomeBlockEntity place(ServerWorld world, BlockPos origin, int rot) {
		HotelBuilder.place(world, origin, rot, null);
		BlockPos at = HotelBuilder.world(origin, rot, HotelPlan.HOME_X, HotelPlan.HOME_Y, HotelPlan.HOME_Z);
		return world.getBlockEntity(at) instanceof HomeBlockEntity home ? home : null;
	}

	/** {@code /chainsaw eternity}: a hotel whose front faces {@code player}, its plaza starting three blocks ahead. */
	public static HomeBlockEntity placeInFront(ServerPlayerEntity player) {
		Direction facing = player.getHorizontalFacing();
		// The front's outward direction (local -z) is north, east, south, west for the turns 0 to 3: it must point at the player.
		int rot = switch (facing) {
			case NORTH -> 2;
			case EAST -> 3;
			case SOUTH -> 0;
			default -> 1;
		};
		BlockPos ahead = player.getBlockPos().offset(facing, 3);
		Vec3d off = HotelBuilder.world(BlockPos.ORIGIN, rot, HotelPlan.SX / 2.0, 0.0, -HotelBuilder.PLAZA);
		BlockPos origin = new BlockPos(ahead.getX() - (int) Math.round(off.x), player.getBlockPos().getY() - 1, ahead.getZ() - (int) Math.round(off.z));
		return place(player.getServerWorld(), origin, rot);
	}
}
