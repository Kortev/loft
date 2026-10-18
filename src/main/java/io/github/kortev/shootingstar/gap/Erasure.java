package io.github.kortev.shootingstar.gap;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
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
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Takes the world out of the zone, once it has all gone black: a shaft round the target from the build limit down
 * through bedrock, its edge ragged rather than a drawn circle, and fissures split out across the ground from its rim,
 * deep at the rim and closing up as they go.
 * <p>
 * Nothing is sent to anyone block by block: the blocks are changed quietly, and when it is done every chunk it touched
 * is sent again, whole, once. Changing them one at a time had every client rebuild the same chunks over and over,
 * which was the lag. Technical blocks (command, structure, barrier, jigsaw) are left alone.
 * <p>
 * Nor is the light worked out block by block. Each block taken out would queue a light check of its own, millions of
 * them, which kept the light engine busy for half a minute after the black came; and the clients, who are only sent
 * light that changes at the edge of what they can see, kept the hole pitch black until the next time its chunks came.
 * Instead the checks are gathered, one to a column at the lowest block taken from it (the sky's light runs straight
 * down an open column from there), and made when the hole is done; and the chunks are sent once the light has settled,
 * while the world is still all black.
 */
public final class Erasure {
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
	private final Set<Long> lids = new HashSet<>();
	private int columnTop;
	private int cursor;
	private int crackCursor;
	private int columnY = Integer.MIN_VALUE;
	private int erased;
	private boolean sent;
	/** The erasure taking blocks out on this thread just now, gathering their light checks; and where they go. */
	@Nullable
	private static Erasure gathering;
	@Nullable
	private static Thread gatheringOn;
	/** Each column changed (x and z packed), and the lowest block taken out of it; and any light source taken out. */
	private final Long2IntOpenHashMap lowest = new Long2IntOpenHashMap();
	private final LongArrayList glowing = new LongArrayList();
	/** Ticks since the light checks went in, and how many ticks running the light engine has had nothing to do. */
	private int lighting = -1;
	private int quiet;
	/**
	 * Finishing one that was cut short (the server went down in the middle of it): the floor it laid over the hole is
	 * taken out with everything else, and no new one is laid, since there is nobody in the black to walk on it.
	 */
	private final boolean recovering;

	Erasure(ServerWorld world, BlockPos center, int radius, boolean recovering) {
		this.world = world;
		this.center = center;
		this.radius = radius;
		this.recovering = recovering;
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

	/**
	 * Called in place of the light check for a block whose state has just changed (WorldChunkMixin): true if an erasure
	 * on this thread is taking blocks out and has kept the check for later.
	 */
	public static boolean defer(BlockPos pos) {
		Erasure erasure = gathering;
		if (erasure == null || gatheringOn != Thread.currentThread()) {
			return false;
		}
		erasure.lightFrom(pos.getX(), pos.getZ(), pos.getY());
		return true;
	}

	/**
	 * Has the light of column (x, z) worked out again when the hole is done, from {@code y} (the lowest block taken out of
	 * it) up.
	 */
	private void lightFrom(int x, int z, int y) {
		long column = ((long) x << 32) | (z & 0xFFFFFFFFL);
		lowest.put(column, Math.min(lowest.getOrDefault(column, Integer.MAX_VALUE), y));
	}

	/** Works on, a budget's worth per call; true when the hole and the fissures are all done, lit and sent. */
	boolean step(double reach) {
		return carving(reach) && settle();
	}

	/** Every block taken out now, however long it takes (the light is still to settle: step goes on with that). */
	void finishBlocks() {
		while (!carving(Double.MAX_VALUE)) {
			// A budget's worth at a time.
		}
	}

	/**
	 * Done now, with nobody to send it to (the server is going down, or has just come back up): every block taken out,
	 * and every chunk it touched marked to have its light worked out afresh the next time it is loaded, since the light
	 * engine will not get through it first. What is still loaded has the checks made as well.
	 */
	void finishOffline() {
		finishBlocks();
		if (lighting < 0) {
			checkLight();
		}
		for (long packed : touched) {
			ChunkPos chunkPos = new ChunkPos(packed);
			WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkPos.x, chunkPos.z);
			if (chunk != null) {
				chunk.setLightOn(false);
				chunk.setNeedsSaving(true);
			}
		}
		sent = true;
	}

	/** Takes blocks out, a budget's worth, with their light checks gathered; true once they are all out. */
	private boolean carving(double reach) {
		gathering = this;
		gatheringOn = Thread.currentThread();
		try {
			return carve(reach);
		} finally {
			gathering = null;
			gatheringOn = null;
		}
	}

	/** The light checks gathered while carving, all made now: one per column, from the lowest block taken out of it. */
	private void checkLight() {
		LightingProvider light = world.getChunkManager().getLightingProvider();
		BlockPos.Mutable at = new BlockPos.Mutable();
		for (Long2IntMap.Entry entry : lowest.long2IntEntrySet()) {
			long column = entry.getLongKey();
			light.checkBlock(at.set((int) (column >> 32), entry.getIntValue(), (int) column));
		}
		for (int i = 0; i < glowing.size(); i++) {
			light.checkBlock(at.set(glowing.getLong(i)));
		}
		lowest.clear();
		glowing.clear();
		lighting = 0;
	}

	/** Once the light has settled, every chunk sent again; true when it has been. */
	private boolean settle() {
		if (sent) {
			return true;
		}
		if (lighting < 0) {
			checkLight();
			return false;
		}
		// Sent once the light engine has had nothing to do for half a second (or, at the most, after twenty seconds).
		lighting++;
		quiet = world.getChunkManager().getLightingProvider().hasUpdates() ? 0 : quiet + 1;
		if (lighting >= 20 && quiet >= 10 || lighting >= 400) {
			sent = true;
			send();
			return true;
		}
		return false;
	}

	private boolean carve(double reach) {
		int budget = BLOCK_BUDGET;
		int reads = READ_BUDGET;
		int bottom = world.getBottomY();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		BlockState air = Blocks.AIR.getDefaultState();
		while (cursor < hole.length && distances[cursor] <= reach && budget > 0 && reads > 0) {
			int x = center.getX() + (int) ((hole[cursor] >>> 16) & 0xFFFF) - 1024;
			int z = center.getZ() + (int) (hole[cursor] & 0xFFFF) - 1024;
			if (columnY == Integer.MIN_VALUE) {
				// Loaded first: the height of a chunk that is not loaded reads as the bottom of the world, and the whole column
				// would be passed over (after a crash nothing holds the hole loaded; and the fissures run past the event's ticket).
				world.getChunk(x >> 4, z >> 4);
				columnY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				columnTop = columnY;
				columnY = Math.max(columnY, world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1);
				touched.add(ChunkPos.toLong(x >> 4, z >> 4));
			}
			while (columnY >= bottom && budget > 0 && reads > 0) {
				pos.set(x, columnY, z);
				BlockState state = world.getBlockState(pos);
				reads--;
				if (!state.isAir() && canErase(state)) {
					if (state.getLuminance() > 0) {
						glowing.add(pos.asLong());
					}
					world.setBlockState(pos, air, QUIET);
					budget--;
					erased++;
				}
				columnY--;
			}
			if (columnY < bottom) {
				// Every column of the hole has its light worked out from the bottom, whatever came out of it: one emptied before a
				// crash comes back with only its lid in it, and the light it had underground.
				lightFrom(x, z, bottom);
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
						world.getChunk(x >> 4, z >> 4);
						int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
						// Narrower further down: a wedge, not a trench.
						int depth = ox == 0 && oz == 0 ? split : split / 2;
						boolean opened = false;
						for (int k = 0; k < depth; k++) {
							pos.set(x, top - k, z);
							BlockState state = world.getBlockState(pos);
							if (!state.isAir() && state.getFluidState().isEmpty() && canErase(state) && !state.isOf(Blocks.BEDROCK)) {
								if (state.getLuminance() > 0) {
									glowing.add(pos.asLong());
								}
								world.setBlockState(pos, air, QUIET);
								opened = true;
							}
						}
						if (opened) {
							lid(pos.set(x, top, z));
						}
						if (depth > 0) {
							lightFrom(x, z, top - depth + 1);
						}
						touched.add(ChunkPos.toLong(x >> 4, z >> 4));
					}
				}
			}
			crackCursor++;
			return false;
		}
		return true;
	}

	private void lid(BlockPos pos) {
		if (!recovering && pos.getY() >= world.getBottomY() && world.getBlockState(pos).isAir()) {
			world.setBlockState(pos, Blocks.BARRIER.getDefaultState(), QUIET);
			lids.add(pos.asLong());
		}
	}

	/** Whether there is a barrier standing in for the ground that was, at this block. */
	boolean lid(long packed) {
		return lids.contains(packed);
	}

	/** The rebuild is done: the ground that was is taken away, quietly, and the chunks sent again. */
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

	/** Every block taken out (the light may still be settling). */
	boolean carved() {
		return cursor >= hole.length && crackCursor >= cracks.size();
	}

	int erased() {
		return erased;
	}

	/** What it takes: anything but the technical blocks; in recovery, the floor of barriers it laid as well. */
	private boolean canErase(BlockState state) {
		return erasable(state) || recovering && state.isOf(Blocks.BARRIER);
	}

	static boolean erasable(BlockState state) {
		return !(state.isOf(Blocks.COMMAND_BLOCK) || state.isOf(Blocks.CHAIN_COMMAND_BLOCK) || state.isOf(Blocks.REPEATING_COMMAND_BLOCK)
				|| state.isOf(Blocks.STRUCTURE_BLOCK) || state.isOf(Blocks.JIGSAW) || state.isOf(Blocks.BARRIER));
	}
}
