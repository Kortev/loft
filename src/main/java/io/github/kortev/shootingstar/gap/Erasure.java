package io.github.kortev.shootingstar.gap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Takes the world out of the zone, once it has all gone black: a shaft round the target from the build limit down
 * through bedrock, its edge ragged rather than a drawn circle, and fissures split out across the ground from its rim,
 * deep at the rim and closing up as they go.
 * <p>
 * Nothing is sent to anyone block by block: the blocks are changed quietly, and when it is done every chunk it touched
 * is sent again, whole, once. Changing them one at a time had every client rebuild the same chunks over and over,
 * which was the lag. Technical blocks (command, structure, barrier, jigsaw) are left alone.
 */
final class Erasure {
	/** Blocks removed (and read) a tick at most: a budget for the server; the clients are told nothing until the end. */
	private static final int BLOCK_BUDGET = 40_000;
	private static final int READ_BUDGET = 200_000;
	/** How far the edge wanders in and out. */
	private static final double RAGGED = 4.0;
	private static final int CRACKS = 14;
	/** Quietly: no update sent, no neighbours told, nothing dropped. */
	private static final int QUIET = Block.FORCE_STATE | Block.SKIP_DROPS;

	private final ServerWorld world;
	private final BlockPos center;
	private final int radius;
	private final double[] phase;
	/** Columns to empty, nearest first, as packed (d squared, dx, dz). */
	private final long[] hole;
	private final double[] distances;
	private final List<long[]> cracks = new ArrayList<>();
	private final Set<Long> touched = new HashSet<>();
	/**
	 * Where the ground was, over every column taken: a barrier there, unseen, so anyone walking about in the black walks
	 * on where the ground used to be rather than falling into the hole. Taken away when the world comes back.
	 */
	private final List<Long> lids = new ArrayList<>();
	private int columnTop;
	private int cursor;
	private int crackCursor;
	private int columnY = Integer.MIN_VALUE;
	private int erased;
	private boolean sent;

	Erasure(ServerWorld world, BlockPos center, int radius) {
		this.world = world;
		this.center = center;
		this.radius = radius;
		Random random = Random.create(center.asLong() ^ 0x5EEDL);
		this.phase = new double[] {random.nextDouble() * 6.28, random.nextDouble() * 6.28, random.nextDouble() * 6.28};
		int reach = radius + (int) Math.ceil(RAGGED) + 2;
		List<Long> inside = new ArrayList<>();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (Math.sqrt(dx * dx + dz * dz) <= edge(dx, dz)) {
					inside.add(((long) (dx * dx + dz * dz) << 32) | ((long) (dx + 1024) << 16) | (dz + 1024));
				}
			}
		}
		this.hole = inside.stream().mapToLong(Long::longValue).sorted().toArray();
		this.distances = new double[hole.length];
		for (int i = 0; i < hole.length; i++) {
			distances[i] = Math.sqrt(hole[i] >>> 32);
		}
		// Fissures out from the rim, wandering, forking now and then.
		for (int i = 0; i < CRACKS; i++) {
			double angle = (i + random.nextDouble() * 0.6) / CRACKS * Math.PI * 2.0;
			double length = radius * (0.25 + random.nextDouble() * 0.45);
			crack(angle, radius - 1.0, length, random, 0);
		}
	}

	/** How far out the edge of the hole is in the direction of (dx, dz): the radius, wandering in and out. */
	private double edge(int dx, int dz) {
		double a = Math.atan2(dz, dx);
		double n = 0.55 * Math.sin(5.0 * a + phase[0]) + 0.3 * Math.sin(11.0 * a + phase[1]) + 0.15 * Math.sin(23.0 * a + phase[2]);
		double jag = ((dx * 73856093 ^ dz * 19349663) & 0xFF) / 255.0 - 0.5;
		return radius + RAGGED * n + 1.2 * jag;
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
			// Width (three at the rim to one at the tip) and how deep it is split (deep at the rim, shallow at the tip),
			// packed with the position.
			double k = s / (double) steps;
			int width = k < 0.3 ? 3 : k < 0.65 ? 2 : 1;
			int split = (int) Math.round(MathHelper.lerp(k, 18.0, 3.0) + random.nextInt(4));
			line[s] = ((long) split << 40) | ((long) width << 32) | ((long) (MathHelper.floor(x) + 1024) << 16) | (MathHelper.floor(z) + 1024);
			if (depth < 1 && s > 4 && random.nextInt(18) == 0) {
				crack(heading + (random.nextBoolean() ? 0.6 : -0.6), Math.hypot(x, z), (steps - s) * 0.6, random, depth + 1);
			}
		}
		cracks.add(line);
	}

	/** Works on, a budget's worth per call; true when the hole and the fissures are all done and sent. */
	boolean step(double reach) {
		int budget = BLOCK_BUDGET;
		int reads = READ_BUDGET;
		int bottom = world.getBottomY();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		BlockState air = Blocks.AIR.getDefaultState();
		while (cursor < hole.length && distances[cursor] <= reach && budget > 0 && reads > 0) {
			int x = center.getX() + (int) ((hole[cursor] >>> 16) & 0xFFFF) - 1024;
			int z = center.getZ() + (int) (hole[cursor] & 0xFFFF) - 1024;
			if (columnY == Integer.MIN_VALUE) {
				columnY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				columnTop = columnY;
				columnY = Math.max(columnY, world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1);
				touched.add(ChunkPos.toLong(x >> 4, z >> 4));
			}
			while (columnY >= bottom && budget > 0 && reads > 0) {
				pos.set(x, columnY, z);
				BlockState state = world.getBlockState(pos);
				reads--;
				if (!state.isAir() && erasable(state)) {
					world.setBlockState(pos, air, QUIET);
					budget--;
					erased++;
				}
				columnY--;
			}
			if (columnY < bottom) {
				lid(pos.set(x, columnTop, z));
				cursor++;
				columnY = Integer.MIN_VALUE;
			}
		}
		if (cursor < hole.length) {
			return false;
		}
		// The fissures, one a tick.
		if (crackCursor < cracks.size()) {
			for (long c : cracks.get(crackCursor)) {
				int split = (int) (c >>> 40);
				int width = (int) ((c >>> 32) & 0xFF);
				int cx = center.getX() + (int) ((c >>> 16) & 0xFFFF) - 1024;
				int cz = center.getZ() + (int) (c & 0xFFFF) - 1024;
				for (int ox = -(width / 2); ox <= width / 2; ox++) {
					for (int oz = -(width / 2); oz <= width / 2; oz++) {
						int x = cx + ox;
						int z = cz + oz;
						int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
						// Narrower further down: a wedge, not a trench.
						int depth = ox == 0 && oz == 0 ? split : split / 2;
						boolean opened = false;
						for (int k = 0; k < depth; k++) {
							pos.set(x, top - k, z);
							BlockState state = world.getBlockState(pos);
							if (!state.isAir() && state.getFluidState().isEmpty() && erasable(state) && !state.isOf(Blocks.BEDROCK)) {
								world.setBlockState(pos, air, QUIET);
								opened = true;
							}
						}
						if (opened) {
							lid(pos.set(x, top, z));
						}
						touched.add(ChunkPos.toLong(x >> 4, z >> 4));
					}
				}
			}
			crackCursor++;
			return false;
		}
		if (!sent) {
			sent = true;
			send();
		}
		return true;
	}

	private void lid(BlockPos pos) {
		if (pos.getY() >= world.getBottomY() && world.getBlockState(pos).isAir()) {
			world.setBlockState(pos, Blocks.BARRIER.getDefaultState(), QUIET);
			lids.add(pos.asLong());
		}
	}

	/** The world is back: the ground that was is taken away from under the black, quietly, and the chunks sent again. */
	void unlid() {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (long packed : lids) {
			pos.set(packed);
			if (world.getBlockState(pos).isOf(Blocks.BARRIER)) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), QUIET);
			}
		}
		lids.clear();
		send();
	}

	/** Every chunk it touched, sent again whole to everyone who can see it: each client rebuilds each one once. */
	private void send() {
		int view = (world.getServer().getPlayerManager().getViewDistance() + 1) * 16;
		for (long packed : touched) {
			ChunkPos chunkPos = new ChunkPos(packed);
			WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkPos.x, chunkPos.z);
			if (chunk == null) {
				continue;
			}
			ChunkDataS2CPacket packet = new ChunkDataS2CPacket(chunk, world.getLightingProvider(), null, null);
			for (ServerPlayerEntity player : world.getPlayers()) {
				double dx = player.getX() - chunkPos.getCenterX();
				double dz = player.getZ() - chunkPos.getCenterZ();
				if (Math.abs(dx) <= view && Math.abs(dz) <= view) {
					player.networkHandler.sendPacket(packet);
				}
			}
		}
	}

	boolean done() {
		return sent;
	}

	int erased() {
		return erased;
	}

	static boolean erasable(BlockState state) {
		return !(state.isOf(Blocks.COMMAND_BLOCK) || state.isOf(Blocks.CHAIN_COMMAND_BLOCK) || state.isOf(Blocks.REPEATING_COMMAND_BLOCK)
				|| state.isOf(Blocks.STRUCTURE_BLOCK) || state.isOf(Blocks.JIGSAW) || state.isOf(Blocks.BARRIER));
	}
}
