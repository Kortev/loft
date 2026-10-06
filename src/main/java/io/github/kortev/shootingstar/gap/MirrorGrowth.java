package io.github.kortev.shootingstar.gap;

import io.github.kortev.shootingstar.registry.ModBlocks;
import java.util.Arrays;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * What the world comes back with where the hole was: the other universe, grown into it. A bowl of its ground across
 * the hole (mirror grass over mirror stone, a shell over the drop, deepest in the middle), its trees standing about
 * on it, and in the very middle, where Yggdrasil drew back down, its sapling. Grown out from the middle a ring at a
 * time over a few ticks, while the world is still coming back.
 */
final class MirrorGrowth {
	/** Columns grown a tick. */
	private static final int BUDGET = 3000;
	private static final int DEPTH = 18;
	private static final int SHELL = 3;

	private final ServerWorld world;
	private final BlockPos center;
	private final int radius;
	private final long[] columns;
	private final Random random;
	private int cursor;

	MirrorGrowth(ServerWorld world, BlockPos center, int radius) {
		this.world = world;
		this.center = center;
		this.radius = radius;
		this.random = Random.create(center.asLong() * 31L + radius);
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
		this.columns = Arrays.copyOf(packed, n);
		Arrays.sort(this.columns);
	}

	/** Grows the next ring or so; true when it is all grown. */
	boolean step() {
		int end = Math.min(columns.length, cursor + BUDGET);
		for (; cursor < end; cursor++) {
			long c = columns[cursor];
			int dx = (int) ((c >>> 16) & 0xFFFF) - 1024;
			int dz = (int) (c & 0xFFFF) - 1024;
			double d = Math.sqrt(c >>> 32) / radius;
			column(center.getX() + dx, center.getZ() + dz, d);
		}
		if (cursor >= columns.length) {
			sapling();
			return true;
		}
		return false;
	}

	/** The bowl's surface at a fraction {@code d} of the way out to the rim. */
	private int surface(double d) {
		return center.getY() - (int) Math.round(DEPTH * (1.0 - d * d));
	}

	private void column(int x, int z, double d) {
		int top = surface(d);
		// Only into the hole: wherever something is already there (the rim, what was left), it is left alone.
		for (int k = 0; k < SHELL; k++) {
			BlockPos p = new BlockPos(x, top - k, z);
			if (world.getBlockState(p).isAir()) {
				world.setBlockState(p, (k == 0 ? ModBlocks.MIRROR_GRASS : ModBlocks.MIRROR_STONE).getDefaultState(), Erasure.FLAGS);
			}
		}
		// A tree here and there, none too near the middle (the sapling's) or the rim.
		if (d > 0.12 && d < 0.88 && random.nextInt(110) == 0) {
			tree(new BlockPos(x, top + 1, z));
		}
	}

	private void tree(BlockPos foot) {
		int height = 4 + random.nextInt(3);
		BlockState log = ModBlocks.MIRROR_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.Y);
		for (int y = 0; y < height; y++) {
			place(foot.up(y), log);
		}
		BlockState leaves = ModBlocks.MIRROR_LEAVES.getDefaultState();
		BlockPos crown = foot.up(height - 1);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx * dx + dy * dy * 2 + dz * dz <= 5 + random.nextInt(2)) {
						place(crown.add(dx, dy, dz), leaves);
					}
				}
			}
		}
	}

	private void sapling() {
		BlockPos at = new BlockPos(center.getX(), surface(0.0) + 1, center.getZ());
		place(at, ModBlocks.YGGDRASIL_SAPLING.getDefaultState());
	}

	private void place(BlockPos p, BlockState state) {
		if (world.getBlockState(p).isAir()) {
			world.setBlockState(p, state, Erasure.FLAGS);
		}
	}
}
