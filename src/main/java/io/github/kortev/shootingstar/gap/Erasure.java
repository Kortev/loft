package io.github.kortev.shootingstar.gap;

import java.util.Arrays;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/**
 * Carves the crater: removes every block in a bowl round the target, from the build limit down to the bowl's
 * floor (deepest in the middle, rising to the ground at the rim), working outward column by column. Run once
 * everyone has been taken into the void, so no client is there to be sent a single change. Technical blocks
 * (command, structure, barrier, jigsaw) are left alone.
 */
final class Erasure {
	/**
	 * Blocks removed (and read) a tick at most. Every removal is sent to the clients, which relight and rebuild what it
	 * touches, so it is spread thin: nobody sees it happen, in the black, and at the default radius it is still done
	 * well before the camera is back in the shooter's eyes.
	 */
	private static final int BLOCK_BUDGET = 30_000;
	private static final int READ_BUDGET = 160_000;
	/** How deep the crater is in the middle, as a fraction of its radius. */
	private static final double DEPTH = 0.4;
	static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

	private final ServerWorld world;
	private final BlockPos center;
	/** Columns as packed (dx, dz), nearest first. */
	private final long[] columns;
	private final double[] distances;
	private int cursor;
	private int columnY = Integer.MIN_VALUE;
	private int erased;

	private final int radius;

	Erasure(ServerWorld world, BlockPos center, int radius) {
		this.world = world;
		this.center = center;
		this.radius = radius;
		int r2 = radius * radius;
		long[] packed = new long[(2 * radius + 1) * (2 * radius + 1)];
		int n = 0;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 <= r2) {
					packed[n++] = ((long) d2 << 32) | ((long) (dx + 1024) << 16) | (dz + 1024);
				}
			}
		}
		long[] sorted = Arrays.copyOf(packed, n);
		Arrays.sort(sorted);
		this.columns = sorted;
		this.distances = new double[n];
		for (int i = 0; i < n; i++) {
			distances[i] = Math.sqrt(sorted[i] >>> 32);
		}
	}

	/** Erases every column out to {@code reach} blocks from the centre, a budget's worth per call; true when all are gone. */
	boolean step(double reach) {
		int budget = BLOCK_BUDGET;
		int reads = READ_BUDGET;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		while (cursor < columns.length && distances[cursor] <= reach && budget > 0 && reads > 0) {
			int bottom = floor(distances[cursor]) + 1;
			int x = center.getX() + (int) ((columns[cursor] >>> 16) & 0xFFFF) - 1024;
			int z = center.getZ() + (int) (columns[cursor] & 0xFFFF) - 1024;
			if (columnY == Integer.MIN_VALUE) {
				columnY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
			}
			while (columnY >= bottom && budget > 0 && reads > 0) {
				pos.set(x, columnY, z);
				BlockState state = world.getBlockState(pos);
				reads--;
				if (!state.isAir() && erasable(state)) {
					world.setBlockState(pos, Blocks.AIR.getDefaultState(), FLAGS);
					budget--;
					erased++;
				}
				columnY--;
			}
			if (columnY < bottom) {
				cursor++;
				columnY = Integer.MIN_VALUE;
			}
		}
		return cursor >= columns.length;
	}

	/** The crater's floor, {@code d} blocks out from the middle: the last block left standing in that column. */
	int floor(double d) {
		double k = Math.min(1.0, d / Math.max(1, radius));
		return Math.max(world.getBottomY(), center.getY() - (int) Math.round(radius * DEPTH * (1.0 - k * k)));
	}

	boolean done() {
		return cursor >= columns.length;
	}

	int erased() {
		return erased;
	}

	static boolean erasable(BlockState state) {
		return !(state.isOf(Blocks.COMMAND_BLOCK) || state.isOf(Blocks.CHAIN_COMMAND_BLOCK) || state.isOf(Blocks.REPEATING_COMMAND_BLOCK)
				|| state.isOf(Blocks.STRUCTURE_BLOCK) || state.isOf(Blocks.JIGSAW) || state.isOf(Blocks.BARRIER));
	}
}
