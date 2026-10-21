package io.github.kortev.chainsawman;

import io.github.kortev.chainsawman.form.Heart;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;

/**
 * There's one Pochita in a world, and he's waiting somewhere: fainted under a big fallen tree ({@link #den}), {@link #NEAR} to
 * {@link #FAR} blocks from the world's spawn, placed once when the world first starts with this mod. You have to go and
 * find him: within {@link #HEAR} blocks kortev hears him whimpering. If he's ever really lost (the void, the kill
 * command) he turns up there again a while later; once he's someone's heart, never again.
 */
public final class PochitaPlace extends PersistentState {
	private static final int NEAR = 250;
	private static final int FAR = 450;
	private static final double HEAR = 40.0;
	/** How long after he's lost he turns up again (ticks). */
	private static final int BACK_AFTER = 2400;
	private static final Type<PochitaPlace> TYPE = new Type<>(PochitaPlace::new, PochitaPlace::fromNbt, null);

	private boolean placed;
	private BlockPos at = BlockPos.ORIGIN;
	/** When he was lost (world time), or -1. */
	private long lost = -1;
	private boolean merged;

	private static PochitaPlace of(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "chainsawman_pochita");
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PochitaPlace place = of(server);
			if (!place.placed) {
				place.choose(server.getOverworld());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 40 != 0) {
				return;
			}
			PochitaPlace place = of(server);
			ServerWorld world = server.getOverworld();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (Heart.merged(player)) {
					if (!place.merged) {
						place.merged = true;
						place.markDirty();
					}
					continue;
				}
				if (player.getGameProfile().getName().equalsIgnoreCase(ChainsawMan.OWNER) && player.getWorld() == world
						&& !world.getEntitiesByClass(PochitaEntity.class, player.getBoundingBox().expand(HEAR), p -> !p.isTamed()).isEmpty()) {
					player.sendMessage(Text.translatable("message.chainsawman.pochita_near").formatted(Formatting.GRAY, Formatting.ITALIC), true);
				}
			}
			if (place.lost >= 0 && !place.merged && world.getTime() - place.lost > BACK_AFTER) {
				place.lost = -1;
				place.spawn(world);
				place.markDirty();
			}
		});
	}

	/** He's really gone: he'll be back at his place before long. */
	public static void lost(MinecraftServer server) {
		PochitaPlace place = of(server);
		place.lost = server.getOverworld().getTime();
		place.markDirty();
	}

	/** Somewhere dry and open some way from spawn, the fallen tree laid there, and him under it. */
	private void choose(ServerWorld world) {
		Random random = Random.create(world.getSeed() ^ 0x5EED_C4A1L);
		BlockPos spawn = world.getSpawnPos();
		for (int tries = 0; tries < 12; tries++) {
			float angle = random.nextFloat() * MathHelper.TAU;
			int distance = NEAR + random.nextInt(FAR - NEAR);
			int x = spawn.getX() + (int) (MathHelper.cos(angle) * distance);
			int z = spawn.getZ() + (int) (MathHelper.sin(angle) * distance);
			world.getChunk(x >> 4, z >> 4);
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos ground = new BlockPos(x, y - 1, z);
			if (y <= world.getBottomY() + 1 || !world.getFluidState(ground).isEmpty() || world.getFluidState(ground.up()).isIn(FluidTags.WATER)) {
				continue;
			}
			at = ground.up();
			den(world, at, random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z);
			spawn(world);
			placed = true;
			markDirty();
			ChainsawMan.LOGGER.info("Pochita is waiting at {}", at);
			return;
		}
		// Nowhere good: by spawn, then.
		at = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn);
		spawn(world);
		placed = true;
		markDirty();
	}

	/** A wild Pochita fainted under a fallen tree at {@code at} lying along {@code axis}, apart from the world's own (for trying him out). */
	public static void waiting(ServerWorld world, BlockPos at, Direction.Axis axis) {
		den(world, at, axis);
		PochitaEntity pochita = ChainsawMan.POCHITA_ENTITY.create(world);
		if (pochita != null) {
			pochita.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360.0F, 0.0F);
			pochita.initialize(world, world.getLocalDifficulty(at), SpawnReason.COMMAND, null);
			pochita.setPersistent();
			world.spawnEntity(pochita);
		}
	}

	/**
	 * Where he lies (kortev's pick, 2026-10-20, the hollow log looking poor): a big old tree fallen across a little
	 * clearing, along {@code axis}. Its roots torn up out of the ground at one end, standing on edge over the hole
	 * they left; the trunk thick, mossy, vined, held up off the ground by its roots and a broken limb, so there's a gap
	 * under it where he lies, to be seen from either side; snapped off at the other end, splinters standing, its crown
	 * down in a heap of leaves beyond. Round it the signs of a fight (scratched-up ground, broken branches, leaves torn
	 * off), and a trail of his blood leading in to him. {@code at} is where he lies, on the ground.
	 */
	private static void den(ServerWorld world, BlockPos at, Direction.Axis axis) {
		Random random = Random.create(at.asLong());
		Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Direction across = along.rotateYClockwise();
		Den den = new Den(world, at, along, across);
		// The clearing: levelled (holes filled, bumps and undergrowth cleared), grass, the ground under the tree mossy.
		for (int i = -12; i <= 12; i++) {
			for (int j = -7; j <= 8; j++) {
				double r = i * i / 156.0 + (j - 0.5) * (j - 0.5) / 60.0;
				if (r > 1.0) {
					continue;
				}
				for (int k = 0; k <= 12; k++) {
					den.set(i, j, k, Blocks.AIR.getDefaultState());
				}
				for (int k = -4; k <= -2; k++) {
					if (!den.get(i, j, k).isOpaqueFullCube(world, den.at(i, j, k))) {
						den.set(i, j, k, Blocks.DIRT.getDefaultState());
					}
				}
				boolean shade = Math.abs(i) <= 6 && j >= -1 && j <= 2;
				float f = random.nextFloat();
				BlockState ground = shade ? (f < 0.5F ? Blocks.MOSS_BLOCK : f < 0.8F ? Blocks.PODZOL : Blocks.COARSE_DIRT).getDefaultState()
						: (f < 0.85F ? Blocks.GRASS_BLOCK : f < 0.93F ? Blocks.PODZOL : Blocks.COARSE_DIRT).getDefaultState();
				den.set(i, j, -1, ground);
			}
		}
		// The hole the roots tore out of, at the foot of the tree.
		for (int i = -8; i <= -7; i++) {
			for (int j = -1; j <= 2; j++) {
				den.set(i, j, -1, Blocks.AIR.getDefaultState());
				den.set(i, j, -2, (random.nextBoolean() ? Blocks.ROOTED_DIRT : Blocks.COARSE_DIRT).getDefaultState());
			}
		}
		// Its roots, torn up and standing on edge: a plate of earth and roots, roots sticking out round it, hanging under.
		for (int j = -3; j <= 4; j++) {
			for (int k = -1; k <= 5; k++) {
				double d = (j - 0.5) * (j - 0.5) + (k - 1.5) * (k - 1.5);
				if (d > 12.5) {
					continue;
				}
				BlockState earth = d > 8.5 ? Blocks.MANGROVE_ROOTS.getDefaultState()
						: (random.nextFloat() < 0.4F ? Blocks.ROOTED_DIRT : random.nextFloat() < 0.6F ? Blocks.DIRT : Blocks.COARSE_DIRT).getDefaultState();
				den.set(-6, j, k, earth);
				if (d <= 8.5 && random.nextFloat() < 0.5F) {
					den.set(-7, j, k, Blocks.MANGROVE_ROOTS.getDefaultState());
				}
			}
		}
		for (int j = -2; j <= 3; j++) {
			if (den.get(-6, j, 0).isOpaqueFullCube(world, den.at(-6, j, 0)) && den.get(-6, j, -1).isAir()) {
				den.set(-6, j, -1, Blocks.HANGING_ROOTS.getDefaultState());
			}
		}
		// The trunk, two thick, raised a block off the ground; bark-knotted here and there.
		BlockState log = Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, axis);
		BlockState bark = Blocks.OAK_WOOD.getDefaultState().with(PillarBlock.AXIS, axis);
		for (int i = -5; i <= 4; i++) {
			for (int j = 0; j <= 1; j++) {
				for (int k = 1; k <= 2; k++) {
					den.set(i, j, k, random.nextFloat() < 0.2F ? bark : log);
				}
				// Moss over its top; vines down its sides.
				if (random.nextFloat() < 0.65F) {
					den.set(i, j, 3, Blocks.MOSS_CARPET.getDefaultState());
				}
			}
			for (int k = 1; k <= 2; k++) {
				if (random.nextFloat() < 0.3F) {
					den.set(i, -1, k, Blocks.VINE.getDefaultState().with(VineBlock.getFacingProperty(across), true));
				}
				if (random.nextFloat() < 0.3F) {
					den.set(i, 2, k, Blocks.VINE.getDefaultState().with(VineBlock.getFacingProperty(across.getOpposite()), true));
				}
			}
		}
		// The broken limb it rests on, near the break, down into the ground.
		den.set(3, -1, 0, Blocks.OAK_LOG.getDefaultState());
		den.set(3, -1, 1, Blocks.OAK_WOOD.getDefaultState());
		// Snapped off: a jagged end, pale splinters standing out of it.
		BlockState splinter = Blocks.STRIPPED_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, axis);
		den.set(5, 0, 1, log);
		den.set(5, 1, 2, log);
		den.set(5, 1, 1, splinter);
		den.set(6, 0, 1, splinter);
		den.set(6, 1, 2, Blocks.STRIPPED_OAK_LOG.getDefaultState());
		den.set(6, 1, 3, Blocks.STRIPPED_OAK_LOG.getDefaultState());
		// Its crown, down in a heap of leaves past the break, a couple of its boughs through it.
		BlockState leaves = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
		for (int i = 8; i <= 12; i++) {
			for (int j = -2; j <= 4; j++) {
				for (int k = 0; k <= 3; k++) {
					double d = (i - 10.0) * (i - 10.0) / 6.0 + (j - 1.0) * (j - 1.0) / 9.0 + k * k / 7.0;
					if (d <= 1.0 && random.nextFloat() < 0.85F) {
						den.set(i, j, k, leaves);
					}
				}
			}
		}
		for (int i = 7; i <= 11; i++) {
			den.set(i, 1, 0, log);
		}
		den.set(9, -1, 1, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, across.getAxis()));
		den.set(10, 3, 1, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, across.getAxis()));
		// The fight: broken branches about, leaves torn off and scattered, the ground scratched up in streaks.
		den.set(2, -4, 0, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, across.getAxis()));
		den.set(2, -5, 0, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, across.getAxis()));
		den.set(-3, 5, 0, log);
		for (int[] leaf : new int[][] {{1, -3}, {-2, -4}, {4, -5}, {-4, 4}, {0, 5}, {5, 3}, {-1, -6}}) {
			den.set(leaf[0], leaf[1], 0, leaves);
		}
		for (int[] streak : new int[][] {{-1, -3, 1, 0}, {-3, -2, 1, 1}, {1, 3, 1, 0}}) {
			for (int n = 0; n < 4; n++) {
				den.set(streak[0] + n * streak[2], streak[1] + n * streak[3], -1, Blocks.COARSE_DIRT.getDefaultState());
			}
		}
		// Ferns and mushrooms in the shade of it.
		for (int n = 0; n < 26; n++) {
			int i = random.nextInt(19) - 9;
			int j = random.nextInt(13) - 6;
			if (!den.get(i, j, 0).isAir() || !den.get(i, j, -1).isOpaqueFullCube(world, den.at(i, j, -1))) {
				continue;
			}
			boolean podzol = den.get(i, j, -1).isOf(Blocks.PODZOL);
			if (podzol) {
				den.set(i, j, 0, (random.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).getDefaultState());
			} else if (den.get(i, j, -1).isOf(Blocks.GRASS_BLOCK) || den.get(i, j, -1).isOf(Blocks.MOSS_BLOCK)) {
				den.set(i, j, 0, (random.nextFloat() < 0.7F ? Blocks.FERN : Blocks.SHORT_GRASS).getDefaultState());
			}
		}
		// His blood: a trail in from the edge of the clearing to him, drops a step or two apart, and a little where he lies.
		double[][] trail = {{3.0, -11.0}, {2.6, -9.2}, {1.8, -7.6}, {1.4, -6.0}, {0.6, -4.4}, {0.2, -3.0}, {0.0, -1.6}, {0.6, -1.0}, {-0.6, 0.0}};
		for (double[] drop : trail) {
			BlockPos p = den.at((int) Math.round(drop[0]), (int) Math.round(drop[1]), 0);
			p = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p);
			if (Math.abs(p.getY() - at.getY()) <= 3 && (world.getBlockState(p).isAir() || world.getBlockState(p).isReplaceable())
					&& world.getBlockState(p.down()).isOpaqueFullCube(world, p.down())) {
				world.setBlockState(p, Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
		// And the gap under the trunk where he lies, clear.
		den.set(0, 0, 0, Blocks.AIR.getDefaultState());
		den.set(0, 1, 0, Blocks.AIR.getDefaultState());
	}

	/** Blocks placed round {@code at}: {@code i} along the tree, {@code j} across it, {@code k} up. */
	private record Den(ServerWorld world, BlockPos origin, Direction along, Direction across) {
		BlockPos at(int i, int j, int k) {
			return origin.offset(along, i).offset(across, j).up(k);
		}

		BlockState get(int i, int j, int k) {
			return world.getBlockState(at(i, j, k));
		}

		void set(int i, int j, int k, BlockState state) {
			world.setBlockState(at(i, j, k), state, Block.NOTIFY_LISTENERS);
		}
	}

	/** Him, fainted, under the tree. */
	private void spawn(ServerWorld world) {
		world.getChunk(at.getX() >> 4, at.getZ() >> 4);
		PochitaEntity pochita = ChainsawMan.POCHITA_ENTITY.create(world);
		if (pochita == null) {
			return;
		}
		pochita.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360.0F, 0.0F);
		pochita.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null);
		pochita.setPersistent();
		world.spawnEntity(pochita);
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		nbt.putBoolean("Placed", placed);
		nbt.putLong("At", at.asLong());
		nbt.putLong("Lost", lost);
		nbt.putBoolean("Merged", merged);
		return nbt;
	}

	private static PochitaPlace fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		PochitaPlace place = new PochitaPlace();
		place.placed = nbt.getBoolean("Placed");
		place.at = BlockPos.fromLong(nbt.getLong("At"));
		place.lost = nbt.contains("Lost") ? nbt.getLong("Lost") : -1;
		place.merged = nbt.getBoolean("Merged");
		return place;
	}
}
