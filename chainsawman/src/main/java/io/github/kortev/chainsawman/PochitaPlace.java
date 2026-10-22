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
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
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
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

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

	/** How many places to try for him, and how far up or down the ground there can go. */
	private static final int TRIES = 32;
	private static final int LEVEL = 4;
	/** What's built where he waits: 0 the hollow log of the first worlds, {@link #DEN} the fallen tree. */
	private static final int DEN = 2;

	private boolean placed;
	private int built;
	private BlockPos at = BlockPos.ORIGIN;
	/** The way the fallen tree lies. */
	private Direction.Axis axis = Direction.Axis.X;
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
			} else if (place.built < DEN && !place.merged) {
				// A world from before the fallen tree: his old log made over into it, round where he lies.
				ServerWorld world = server.getOverworld();
				place.axis = Random.create(place.at.asLong()).nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
				den(world, place.at, place.axis);
				place.built = DEN;
				place.markDirty();
				ChainsawMan.LOGGER.info("Pochita's place made over as a fallen tree at {}", place.at);
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

	/** Where the world's own Pochita waits under his fallen tree, if it's built yet (null if not): for a test to look at. */
	@org.jetbrains.annotations.Nullable
	public static BlockPos tree(MinecraftServer server) {
		PochitaPlace place = of(server);
		return place.placed && place.built >= DEN ? place.at : null;
	}

	/** The way the world's own fallen tree lies. */
	public static Direction.Axis treeAxis(MinecraftServer server) {
		return of(server).axis;
	}

	/** He's really gone: he'll be back at his place before long. */
	public static void lost(MinecraftServer server) {
		PochitaPlace place = of(server);
		place.lost = server.getOverworld().getTime();
		place.markDirty();
	}

	/**
	 * Somewhere some way from spawn for the fallen tree, and him under it: in a forest, a taiga, a plains or a meadow if
	 * there's one, on ground level enough for it; failing that any dry level ground; failing that, by spawn.
	 */
	private void choose(ServerWorld world) {
		Random random = Random.create(world.getSeed() ^ 0x5EED_C4A1L);
		BlockPos spawn = world.getSpawnPos();
		BlockPos dry = null;
		for (int tries = 0; tries < TRIES; tries++) {
			float angle = random.nextFloat() * MathHelper.TAU;
			int distance = NEAR + random.nextInt(FAR - NEAR);
			BlockPos spot = fits(world, spawn.getX() + (int) (MathHelper.cos(angle) * distance), spawn.getZ() + (int) (MathHelper.sin(angle) * distance));
			if (spot == null) {
				continue;
			}
			if (wooded(world.getBiome(spot.down()))) {
				place(world, spot, random);
				return;
			}
			if (dry == null) {
				dry = spot;
			}
		}
		// None in the ring (a world that starts in a desert or badlands, say): the woods nearest a place in it.
		float angle = random.nextFloat() * MathHelper.TAU;
		int distance = (NEAR + FAR) / 2;
		BlockPos woods = woods(world, spawn.add((int) (MathHelper.cos(angle) * distance), 0, (int) (MathHelper.sin(angle) * distance)), random);
		if (woods != null) {
			place(world, woods, random);
			return;
		}
		if (dry != null) {
			place(world, dry, random);
			return;
		}
		// Nowhere good: by spawn, then.
		at = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn);
		spawn(world);
		placed = true;
		built = DEN;
		markDirty();
	}

	private void place(ServerWorld world, BlockPos spot, Random random) {
		at = spot;
		axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
		den(world, at, axis);
		spawn(world);
		placed = true;
		built = DEN;
		markDirty();
		ChainsawMan.LOGGER.info("Pochita is waiting at {}", at);
	}

	/**
	 * A place for the fallen tree in the woods or grassland nearest {@code from} (within a few thousand blocks), on
	 * level ground: where he'd lie, or null if there's none.
	 */
	@org.jetbrains.annotations.Nullable
	public static BlockPos woods(ServerWorld world, BlockPos from, Random random) {
		var found = world.locateBiome(PochitaPlace::wooded, from, 3200, 32, 64);
		ChainsawMan.LOGGER.info("Woods nearest {} for Pochita: {}", from, found == null ? "none" : found.getFirst() + " " + found.getSecond().getIdAsString());
		if (found == null) {
			return null;
		}
		BlockPos near = found.getFirst();
		// (Its nearest edge is often hilly, against whatever it borders: look round further in.)
		for (int tries = 0; tries < TRIES * 3; tries++) {
			int r = tries < 2 ? 0 : 8 + random.nextInt(8 + tries * 2);
			float angle = random.nextFloat() * MathHelper.TAU;
			BlockPos spot = fits(world, near.getX() + (int) (MathHelper.cos(angle) * r), near.getZ() + (int) (MathHelper.sin(angle) * r));
			if (spot != null && wooded(world.getBiome(spot.down()))) {
				return spot;
			}
		}
		return null;
	}

	/** Where he'd lie at ({@code x}, {@code z}), if the fallen tree fits there: dry, earthy, level ground; else null. */
	@org.jetbrains.annotations.Nullable
	private static BlockPos fits(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos ground = new BlockPos(x, y - 1, z);
		if (y <= world.getBottomY() + 1 || !world.getBlockState(ground).isIn(BlockTags.DIRT) || !world.getFluidState(ground).isEmpty()
				|| world.getFluidState(ground.up()).isIn(FluidTags.WATER) || !level(world, x, y, z)) {
			return null;
		}
		return ground.up();
	}

	/**
	 * Whether the ground round ({@code x}, {@code z}) is level enough for the fallen tree: no more than {@link #LEVEL}
	 * blocks above or below {@code y} across it (a tree's trunk can stand in one or two of the places looked at).
	 */
	private static boolean level(ServerWorld world, int x, int y, int z) {
		int off = 0;
		for (int[] d : new int[][] {{10, 0}, {-10, 0}, {0, 7}, {0, -7}, {7, 5}, {-7, -5}, {7, -5}, {-7, 5}}) {
			if (Math.abs(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x + d[0], z + d[1]) - y) > LEVEL) {
				off++;
			}
		}
		return off <= 2;
	}

	/** Whether {@code biome} is woods or grassland, where a fallen tree belongs. */
	private static boolean wooded(RegistryEntry<Biome> biome) {
		return biome.isIn(BiomeTags.IS_FOREST) || biome.isIn(BiomeTags.IS_TAIGA) || biome.matchesKey(BiomeKeys.PLAINS)
				|| biome.matchesKey(BiomeKeys.SUNFLOWER_PLAINS) || biome.matchesKey(BiomeKeys.MEADOW);
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
	 * they left; the trunk round, mossy, vined, held up off the ground by its roots so there's a gap under it where he
	 * lies, to be seen from either side, and down on the ground at its other end, snapped off there, splinters standing;
	 * its crown down in a heap of leaves beyond. Round it the signs of a fight (scratched-up ground, broken branches,
	 * leaves torn off), and a trail of his blood leading in to him. {@code at} is where he lies, on the ground.
	 */
	private static void den(ServerWorld world, BlockPos at, Direction.Axis axis) {
		Random random = Random.create(at.asLong());
		Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Direction across = along.rotateYClockwise();
		Den den = new Den(world, at, along, across);
		// The clearing: levelled (holes filled, bumps and undergrowth cleared), grass, the ground under the tree mossy.
		for (int i = -12; i <= 12; i++) {
			for (int j = -9; j <= 9; j++) {
				if (i * i / 156.0 + j * j / 90.0 > 1.0) {
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
				boolean shade = Math.abs(i) <= 6 && Math.abs(j) <= 2;
				float f = random.nextFloat();
				BlockState ground = shade ? (f < 0.5F ? Blocks.MOSS_BLOCK : f < 0.8F ? Blocks.PODZOL : Blocks.COARSE_DIRT).getDefaultState()
						: (f < 0.85F ? Blocks.GRASS_BLOCK : f < 0.93F ? Blocks.PODZOL : Blocks.COARSE_DIRT).getDefaultState();
				den.set(i, j, -1, ground);
			}
		}
		// The hole the roots tore out of, at the foot of the tree.
		for (int i = -8; i <= -7; i++) {
			for (int j = -1; j <= 1; j++) {
				den.set(i, j, -1, Blocks.AIR.getDefaultState());
				den.set(i, j, -2, (random.nextBoolean() ? Blocks.ROOTED_DIRT : Blocks.COARSE_DIRT).getDefaultState());
			}
		}
		// Its roots, torn up and standing on edge: a plate of earth, roots sticking out round its rim and hanging under it.
		for (int j = -3; j <= 3; j++) {
			for (int k = -1; k <= 5; k++) {
				double d = j * j + (k - 2.0) * (k - 2.0);
				if (d > 11.5) {
					continue;
				}
				if (d > 7.5 && random.nextFloat() < 0.6F) {
					// The rim ragged: a root sticking out here and there, not a fringe all round.
					continue;
				}
				// Earth packed in the middle; out from it, roots through the earth; past that, roots alone.
				float f = random.nextFloat();
				BlockState earth = d > 7.5 ? Blocks.MANGROVE_ROOTS.getDefaultState()
						: d > 3.5 ? (f < 0.45F ? Blocks.MANGROVE_ROOTS : f < 0.8F ? Blocks.ROOTED_DIRT : Blocks.COARSE_DIRT).getDefaultState()
						: (f < 0.4F ? Blocks.ROOTED_DIRT : f < 0.7F ? Blocks.DIRT : Blocks.COARSE_DIRT).getDefaultState();
				den.set(-6, j, k, earth);
				if (d <= 7.5 && random.nextFloat() < 0.08F) {
					den.set(-7, j, k, Blocks.MANGROVE_ROOTS.getDefaultState());
				}
			}
		}
		for (int j = -2; j <= 2; j++) {
			if (den.get(-6, j, 0).isOpaqueFullCube(world, den.at(-6, j, 0)) && den.get(-6, j, -1).isAir()) {
				den.set(-6, j, -1, Blocks.HANGING_ROOTS.getDefaultState());
			}
		}
		// The trunk: round (a plus through it, its corners open), raised a block off the ground from the roots to near
		// its middle, then down on the ground to the break; bark here and there, moss over it, vines down its sides.
		BlockState log = Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, axis);
		BlockState bark = Blocks.OAK_WOOD.getDefaultState().with(PillarBlock.AXIS, axis);
		BlockState moss = Blocks.MOSS_CARPET.getDefaultState();
		for (int i = -5; i <= 4; i++) {
			int b = i <= 1 ? 1 : 0;
			for (int[] part : new int[][] {{0, 0}, {-1, 1}, {0, 1}, {1, 1}, {0, 2}}) {
				den.set(i, part[0], b + part[1], random.nextFloat() < 0.25F ? bark : log);
			}
			if (random.nextFloat() < 0.7F) {
				den.set(i, 0, b + 3, moss);
			}
			for (int j : new int[] {-1, 1}) {
				if (random.nextFloat() < 0.55F) {
					den.set(i, j, b + 2, moss);
				}
				if (random.nextFloat() < 0.35F) {
					// Hanging off its side, down toward the ground.
					Direction to = j < 0 ? across : across.getOpposite();
					den.set(i, j * 2, b + 1, Blocks.VINE.getDefaultState().with(VineBlock.getFacingProperty(to), true));
					if (b > 0 && random.nextBoolean()) {
						den.set(i, j * 2, b, Blocks.VINE.getDefaultState().with(VineBlock.getFacingProperty(to), true));
					}
				}
			}
		}
		// Snapped off: a jagged end, splinters standing out of it.
		den.set(5, 0, 0, log);
		den.set(5, 0, 1, log);
		den.set(5, 1, 1, log);
		den.set(6, 0, 0, log);
		// (The splinters themselves are put in last, below, so nothing joins them up into a fence.)
		// Its crown, down in a heap of leaves past the break, a couple of its boughs through it.
		BlockState leaves = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
		for (int i = 8; i <= 12; i++) {
			for (int j = -3; j <= 3; j++) {
				for (int k = 0; k <= 3; k++) {
					double d = (i - 10.0) * (i - 10.0) / 6.0 + j * j / 9.0 + k * k / 7.0;
					if (d <= 1.0 && random.nextFloat() < 0.85F) {
						den.set(i, j, k, leaves);
					}
				}
			}
		}
		for (int i = 8; i <= 11; i++) {
			den.set(i, 0, 0, log);
		}
		den.set(9, -2, 1, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, across.getAxis()));
		den.set(10, 2, 1, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, across.getAxis()));
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
			if (den.get(i, j, -1).isOf(Blocks.PODZOL)) {
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
			if (Math.abs(p.getY() - at.getY()) > 3) {
				continue;
			}
			// (Under the trunk, the top is the trunk: the drop goes on the ground under it.)
			BlockPos under = den.at((int) Math.round(drop[0]), (int) Math.round(drop[1]), 0);
			BlockPos onto = world.getBlockState(under).isAir() ? under : p;
			if ((world.getBlockState(onto).isAir() || world.getBlockState(onto).isReplaceable())
					&& world.getBlockState(onto.down()).isOpaqueFullCube(world, onto.down())) {
				world.setBlockState(onto, Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
		// And the gap under the trunk where he lies, clear.
		den.set(0, 0, 0, Blocks.AIR.getDefaultState());
		// Splinters: bare fence posts, put in without touching their neighbours (or each other), so they stay posts.
		for (int[] post : new int[][] {{5, 0, 2}, {5, 1, 2}, {6, 0, 1}, {-2, 5, 0}}) {
			world.setBlockState(den.at(post[0], post[1], post[2]), Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		}
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
		nbt.putString("Axis", axis.asString());
		nbt.putLong("Lost", lost);
		nbt.putBoolean("Merged", merged);
		nbt.putInt("Built", built);
		return nbt;
	}

	private static PochitaPlace fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		PochitaPlace place = new PochitaPlace();
		place.placed = nbt.getBoolean("Placed");
		place.at = BlockPos.fromLong(nbt.getLong("At"));
		Direction.Axis axis = Direction.Axis.fromName(nbt.getString("Axis"));
		place.axis = axis != null && axis != Direction.Axis.Y ? axis : Direction.Axis.X;
		place.lost = nbt.contains("Lost") ? nbt.getLong("Lost") : -1;
		place.merged = nbt.getBoolean("Merged");
		place.built = nbt.getInt("Built");
		return place;
	}
}
