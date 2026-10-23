package io.github.kortev.chainsawman.form;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldEvents;

/**
 * Saw through a tree's trunk in the form and the whole tree comes down (kortev, 2026-10-23: a diamond axe gets you a log,
 * the saws drop the tree): every log above the cut that's joined to it, and the tree's own leaves, topple away from you
 * as falling blocks, the higher the faster, and burst into their drops where they land. Only a tree: the logs must carry
 * leaves the tree grew (not placed ones), so a log cabin's wall is cut a block at a time as before.
 */
public final class Felling {
	/** At most this many logs come down, and this many leaves, within this far of the cut (blocks). */
	private static final int MAX_LOGS = 160;
	private static final int MAX_LEAVES = 480;
	private static final int REACH = 9;
	/** How far leaves can be from the tree's logs and still be its own. */
	private static final int LEAF_REACH = 4;
	/** A trunk with fewer of its own leaves than this isn't a tree. */
	private static final int MIN_LEAVES = 6;
	/** Falling still after this long (ticks), it bursts anyway. */
	private static final int LONGEST = 100;

	private static final List<FallingBlockEntity> FALLING = new ArrayList<>();

	private Felling() {
	}

	public static void init() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
			if (world instanceof ServerWorld server && state.isIn(BlockTags.LOGS) && Form.in(player)) {
				fell(server, player, pos);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> FALLING.removeIf(Felling::landed));
	}

	/** The tree whose trunk was cut at {@code cut} brought down, away from {@code player}. Returns how many blocks fell. */
	public static int fell(ServerWorld world, PlayerEntity player, BlockPos cut) {
		Set<BlockPos> logs = joined(world, cut, p -> world.getBlockState(p).isIn(BlockTags.LOGS) && p.getY() >= cut.getY(), cut.up(), MAX_LOGS);
		if (logs.isEmpty()) {
			return 0;
		}
		Set<BlockPos> leaves = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		for (BlockPos log : logs) {
			for (BlockPos p : BlockPos.iterate(log.add(-1, -1, -1), log.add(1, 1, 1))) {
				if (own(world, p) && leaves.add(p.toImmutable())) {
					open.add(p.toImmutable());
				}
			}
		}
		while (!open.isEmpty() && leaves.size() < MAX_LEAVES) {
			BlockPos p = open.poll();
			for (BlockPos q : BlockPos.iterate(p.add(-1, -1, -1), p.add(1, 1, 1))) {
				if (nearLog(q, logs) && own(world, q) && leaves.add(q.toImmutable())) {
					open.add(q.toImmutable());
				}
			}
		}
		if (leaves.size() < MIN_LEAVES) {
			return 0;
		}
		Vec3d away = Vec3d.ofCenter(cut).subtract(player.getPos()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSquared() < 1.0E-4 ? player.getRotationVector().multiply(1.0, 0.0, 1.0).normalize() : away.normalize();
		int fell = 0;
		for (Set<BlockPos> blocks : List.of(logs, leaves)) {
			for (BlockPos p : blocks) {
				BlockState state = world.getBlockState(p);
				FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, p, state);
				falling.setDestroyedOnLanding();
				falling.dropItem = false;
				// Toppling: the higher up the tree, the faster it swings out.
				double height = p.getY() - cut.getY();
				falling.setVelocity(away.multiply(0.04 + 0.055 * height).add(0.0, 0.08, 0.0));
				falling.velocityModified = true;
				FALLING.add(falling);
				fell++;
			}
		}
		world.playSound(null, cut, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 1.2F, 0.6F);
		world.playSound(null, cut, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.5F, 0.5F);
		return fell;
	}

	/** Leaves the tree grew (not ones placed by someone). */
	private static boolean own(ServerWorld world, BlockPos p) {
		BlockState state = world.getBlockState(p);
		return state.isIn(BlockTags.LEAVES) && state.contains(LeavesBlock.PERSISTENT) && !state.get(LeavesBlock.PERSISTENT);
	}

	private static boolean nearLog(BlockPos p, Set<BlockPos> logs) {
		for (BlockPos q : BlockPos.iterate(p.add(-LEAF_REACH, -LEAF_REACH, -LEAF_REACH), p.add(LEAF_REACH, LEAF_REACH, LEAF_REACH))) {
			if (logs.contains(q)) {
				return true;
			}
		}
		return false;
	}

	/** The blocks joined to {@code from} (corners count) that pass {@code test}, within {@link #REACH} of {@code cut}. */
	private static Set<BlockPos> joined(ServerWorld world, BlockPos cut, Predicate<BlockPos> test, BlockPos from, int max) {
		Set<BlockPos> found = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		for (BlockPos p : BlockPos.iterate(from.add(-1, 0, -1), from.add(1, 0, 1))) {
			if (test.test(p) && found.add(p.toImmutable())) {
				open.add(p.toImmutable());
			}
		}
		while (!open.isEmpty() && found.size() < max) {
			BlockPos p = open.poll();
			for (BlockPos q : BlockPos.iterate(p.add(-1, -1, -1), p.add(1, 1, 1))) {
				if (Math.abs(q.getX() - cut.getX()) <= REACH && Math.abs(q.getZ() - cut.getZ()) <= REACH && test.test(q) && found.add(q.toImmutable())) {
					open.add(q.toImmutable());
				}
			}
		}
		return found;
	}

	/** A falling piece of the tree down (or falling too long): it bursts into its drops where it is. */
	private static boolean landed(FallingBlockEntity falling) {
		if (!falling.isRemoved() && falling.age < LONGEST) {
			return false;
		}
		falling.discard();
		if (falling.getWorld() instanceof ServerWorld world) {
			BlockPos at = falling.getBlockPos();
			BlockState state = falling.getBlockState();
			world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, at, Block.getRawIdFromState(state));
			Block.dropStacks(state, world, at);
		}
		return true;
	}
}
