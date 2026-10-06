package io.github.kortev.shootingstar.gap;

import io.github.kortev.shootingstar.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * Takes the world out of the zone, in three passes, once everyone has been taken into the void (so no client is sent a
 * single change):
 * <ol>
 * <li>the hole: every block in a ragged-edged shaft round the target, from the build limit down through bedrock;</li>
 * <li>its walls: everything solid in a band a block or three thick round the shaft, top to bottom, left Unmade,
 * matter taken, cracked through with the other universe's light;</li>
 * <li>the cracks: Unmade fractures running out across the ground from the rim.</li>
 * </ol>
 * Technical blocks (command, structure, barrier, jigsaw) are left alone.
 */
final class Erasure {
	/** Blocks changed (and read) a tick at most: nobody is there to see it, but the server still has to keep up. */
	private static final int BLOCK_BUDGET = 30_000;
	private static final int READ_BUDGET = 160_000;
	/** How far the edge wanders in and out, and how thick the Unmade band round it is at most. */
	private static final double RAGGED = 4.0;
	private static final int WALL = 3;
	private static final int CRACKS = 14;
	static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

	private final ServerWorld world;
	private final BlockPos center;
	private final int radius;
	private final double[] phase;
	/** Columns to empty, nearest first, and columns to leave Unmade, as packed (d squared, dx, dz). */
	private final long[] hole;
	private final double[] distances;
	private final long[] wall;
	private final List<long[]> cracks = new ArrayList<>();
	private int cursor;
	private int wallCursor;
	private int crackCursor;
	private int columnY = Integer.MIN_VALUE;
	private int erased;

	Erasure(ServerWorld world, BlockPos center, int radius) {
		this.world = world;
		this.center = center;
		this.radius = radius;
		Random random = Random.create(center.asLong() ^ 0x5EEDL);
		this.phase = new double[] {random.nextDouble() * 6.28, random.nextDouble() * 6.28, random.nextDouble() * 6.28};
		int reach = radius + (int) Math.ceil(RAGGED) + WALL + 2;
		List<Long> inside = new ArrayList<>();
		List<Long> band = new ArrayList<>();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				double edge = edge(dx, dz);
				long packed = ((long) (dx * dx + dz * dz) << 32) | ((long) (dx + 1024) << 16) | (dz + 1024);
				if (d <= edge) {
					inside.add(packed);
				} else if (d <= edge + thickness(dx, dz)) {
					band.add(packed);
				}
			}
		}
		this.hole = inside.stream().mapToLong(Long::longValue).sorted().toArray();
		this.distances = new double[hole.length];
		for (int i = 0; i < hole.length; i++) {
			distances[i] = Math.sqrt(hole[i] >>> 32);
		}
		this.wall = band.stream().mapToLong(Long::longValue).sorted().toArray();
		// Fractures out from the rim, wandering, forking now and then, thinning out.
		for (int i = 0; i < CRACKS; i++) {
			double angle = (i + random.nextDouble() * 0.6) / CRACKS * Math.PI * 2.0;
			double length = radius * (0.25 + random.nextDouble() * 0.45);
			crack(angle, radius + RAGGED * 0.5, length, random, 0);
		}
	}

	/** How far out the edge of the hole is in the direction of (dx, dz): the radius, wandering in and out. */
	private double edge(int dx, int dz) {
		double a = Math.atan2(dz, dx);
		double n = 0.55 * Math.sin(5.0 * a + phase[0]) + 0.3 * Math.sin(11.0 * a + phase[1]) + 0.15 * Math.sin(23.0 * a + phase[2]);
		double jag = ((dx * 73856093 ^ dz * 19349663) & 0xFF) / 255.0 - 0.5;
		return radius + RAGGED * n + 1.2 * jag;
	}

	/** How thick the Unmade band is there: one to WALL blocks. */
	private double thickness(int dx, int dz) {
		double a = Math.atan2(dz, dx);
		return 1.0 + (WALL - 1) * (0.5 + 0.5 * Math.sin(7.0 * a + phase[1] * 2.0));
	}

	private void crack(double angle, double from, double length, Random random, int depth) {
		double x = Math.cos(angle) * from;
		double z = Math.sin(angle) * from;
		double heading = angle;
		int steps = Math.max(1, (int) length);
		long[] line = new long[steps];
		for (int s = 0; s < steps; s++) {
			heading += (random.nextDouble() - 0.5) * 0.5;
			x += Math.cos(heading);
			z += Math.sin(heading);
			// The width, from three at the rim to one at the tip, packed with the position.
			int width = s < steps / 3 ? 3 : s < steps * 2 / 3 ? 2 : 1;
			line[s] = ((long) width << 32) | ((long) (MathHelper.floor(x) + 1024) << 16) | (MathHelper.floor(z) + 1024);
			if (depth < 1 && s > 4 && random.nextInt(18) == 0) {
				crack(heading + (random.nextBoolean() ? 0.6 : -0.6), Math.hypot(x, z), (steps - s) * 0.6, random, depth + 1);
			}
		}
		cracks.add(line);
	}

	/** Works on through the passes, a budget's worth per call, the hole out to {@code reach}; true when all are done. */
	boolean step(double reach) {
		int budget = BLOCK_BUDGET;
		int reads = READ_BUDGET;
		int bottom = world.getBottomY();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		// The hole.
		while (cursor < hole.length && distances[cursor] <= reach && budget > 0 && reads > 0) {
			int x = center.getX() + (int) ((hole[cursor] >>> 16) & 0xFFFF) - 1024;
			int z = center.getZ() + (int) (hole[cursor] & 0xFFFF) - 1024;
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
		if (cursor < hole.length) {
			return false;
		}
		// Its walls, top to bottom.
		BlockState unmade = ModBlocks.UNMADE.getDefaultState();
		while (wallCursor < wall.length && budget > 0 && reads > 0) {
			int x = center.getX() + (int) ((wall[wallCursor] >>> 16) & 0xFFFF) - 1024;
			int z = center.getZ() + (int) (wall[wallCursor] & 0xFFFF) - 1024;
			if (columnY == Integer.MIN_VALUE) {
				columnY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
			}
			while (columnY >= bottom && budget > 0 && reads > 0) {
				pos.set(x, columnY, z);
				BlockState state = world.getBlockState(pos);
				reads--;
				if (unmakeable(state, pos)) {
					world.setBlockState(pos, unmade, FLAGS);
					budget--;
				}
				columnY--;
			}
			if (columnY < bottom) {
				wallCursor++;
				columnY = Integer.MIN_VALUE;
			}
		}
		if (wallCursor < wall.length) {
			return false;
		}
		// The cracks: the top few blocks of the ground along each, one crack a tick at most.
		if (crackCursor < cracks.size() && budget > 0) {
			for (long c : cracks.get(crackCursor)) {
				int width = (int) (c >>> 32);
				int cx = center.getX() + (int) ((c >>> 16) & 0xFFFF) - 1024;
				int cz = center.getZ() + (int) (c & 0xFFFF) - 1024;
				for (int ox = -(width / 2); ox <= width / 2; ox++) {
					for (int oz = -(width / 2); oz <= width / 2; oz++) {
						int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx + ox, cz + oz) - 1;
						for (int k = 0; k < 1 + width; k++) {
							pos.set(cx + ox, top - k, cz + oz);
							if (unmakeable(world.getBlockState(pos), pos)) {
								world.setBlockState(pos, unmade, FLAGS);
							}
						}
					}
				}
			}
			crackCursor++;
		}
		return crackCursor >= cracks.size();
	}

	private boolean unmakeable(BlockState state, BlockPos pos) {
		return !state.isAir() && state.getFluidState().isEmpty() && erasable(state) && !state.isOf(Blocks.BEDROCK)
				&& !state.isOf(ModBlocks.UNMADE) && state.isFullCube(world, pos);
	}

	boolean done() {
		return crackCursor >= cracks.size();
	}

	int erased() {
		return erased;
	}

	static boolean erasable(BlockState state) {
		return !(state.isOf(Blocks.COMMAND_BLOCK) || state.isOf(Blocks.CHAIN_COMMAND_BLOCK) || state.isOf(Blocks.REPEATING_COMMAND_BLOCK)
				|| state.isOf(Blocks.STRUCTURE_BLOCK) || state.isOf(Blocks.JIGSAW) || state.isOf(Blocks.BARRIER));
	}
}
