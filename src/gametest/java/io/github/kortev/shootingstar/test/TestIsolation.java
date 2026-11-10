package io.github.kortev.shootingstar.test;

import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTestState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.ChunkStatus;

/**
 * What the game tests share and must not leak into each other: the server's players, the entities in its worlds, its game rules, its time,
 * weather and spawn, and its forced chunks. {@link io.github.kortev.shootingstar.test.mixin.TestIsolationMixin} calls {@link #reset} before
 * every batch (the batches run one after another, so nothing of the one before is wanted by the next) and {@link #load} once a batch's test
 * boxes are placed.
 *
 * <p>Why the chunks: a test box's chunk is kept loaded by the runner, but the chunks beside it are not, and the box lies wherever the world
 * puts it. A player or a mob standing in a chunk that is not yet (or no longer) ticking its entities does not tick at all: its age stays 0,
 * a hit finds it still inside its join protection, a beam walking across a chunk border stops. Whether a test did that depended on where its
 * box fell on the chunk grid, which is random each run.
 */
public final class TestIsolation {
	/** The chunks round a test box that are loaded and ticking before the test starts (a radius in chunks). */
	private static final int RADIUS = 3;

	private static GameRules rules;
	private static final java.util.Map<ServerWorld, long[]> TIMES = new java.util.HashMap<>();
	private static final java.util.Map<ServerWorld, Object[]> SPAWNS = new java.util.HashMap<>();

	private TestIsolation() {
	}

	/** Called before each batch: everything an earlier one left is gone and the world's settings are what the first batch found. */
	public static void reset(ServerWorld any) {
		MinecraftServer server = any.getServer();
		if (rules == null) {
			rules = server.getGameRules().copy();
			for (ServerWorld world : server.getWorlds()) {
				TIMES.put(world, new long[] {world.getTimeOfDay()});
				SPAWNS.put(world, new Object[] {world.getSpawnPos(), world.getSpawnAngle()});
			}
		}
		// (A Domain or the Gap that a test left running holds, hides and hurts whatever lies inside it, and its zone is hundreds of blocks wide.)
		io.github.kortev.gojolander.domain.DomainManager.collapseAll();
		io.github.kortev.shootingstar.gap.GapManager.endNow(server);
		for (ServerPlayerEntity player : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
			server.getPlayerManager().remove(player);
		}
		// (The hybrids the world remembers, gone or not, take its hearts: an earlier test's stand-in would leave none for the next to merge.)
		io.github.kortev.chainsawman.form.TeamState hearts = io.github.kortev.chainsawman.form.TeamState.of(server);
		hearts.seen.clear();
		hearts.markDirty();
		for (ServerWorld world : server.getWorlds()) {
			List<Entity> all = new ArrayList<>();
			for (Entity entity : world.iterateEntities()) {
				if (!(entity instanceof PlayerEntity)) {
					all.add(entity);
				}
			}
			for (Entity entity : all) {
				entity.discard();
			}
			for (LongIterator it = new it.unimi.dsi.fastutil.longs.LongArrayList(world.getForcedChunks()).iterator(); it.hasNext();) {
				long chunk = it.nextLong();
				world.setChunkForced(net.minecraft.util.math.ChunkPos.getPackedX(chunk), net.minecraft.util.math.ChunkPos.getPackedZ(chunk), false);
			}
			world.setTimeOfDay(TIMES.get(world)[0]);
			world.resetWeather();
			Object[] spawn = SPAWNS.get(world);
			world.setSpawnPos((BlockPos) spawn[0], (Float) spawn[1]);
		}
		server.getGameRules().setAllValues(rules, server);
	}

	/** Called once the boxes of the batch are placed: the chunks round each are loaded now, and kept so. */
	public static void load(Collection<GameTestState> states) {
		for (GameTestState state : states) {
			ServerWorld world = state.getWorld();
			BlockPos at = state.getPos();
			for (int cx = (at.getX() >> 4) - RADIUS; cx <= (at.getX() >> 4) + RADIUS; cx++) {
				for (int cz = (at.getZ() >> 4) - RADIUS; cz <= (at.getZ() >> 4) + RADIUS; cz++) {
					world.setChunkForced(cx, cz, true);
					world.getChunk(cx, cz, ChunkStatus.FULL, true);
				}
			}
		}
	}
}
