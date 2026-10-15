package io.github.kortev.shootingstar.test;

import com.mojang.datafixers.util.Pair;
import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Where the film's scene is and what is built there: Kortev's cabin, Woomerd's base (forty-odd blocks east, a big red
 * W on the wall that faces the cabin), and the world spawn well south of both, where gerald_2009 turns up. Found on
 * flat plains near the test world's spawn, so it looks like any survival server's. Everything is axis-aligned: the
 * base is east (+x) of the cabin, the spawn south (+z).
 */
final class FilmScene {
	/** How far east of the cabin the base is. */
	static final int BASE_EAST = 45;
	/** The spawn, from the key's target: west and well south, outside the hole (the zone is 96 blocks round). */
	static final int SPAWN_WEST = 20;
	static final int SPAWN_SOUTH = 105;

	/** The cabin's middle, on its floor. */
	final BlockPos house;
	/** The base's middle, on its floor. */
	final BlockPos base;
	/** Where Kortev stands to watch (south-east of the cabin), Woomerd's spot by the cabin's east wall. */
	final Vec3d kortev;
	final Vec3d woomerdAtHouse;
	/** Woomerd's spot in front of the W, and the block the key is aimed at (just in front of it). */
	final Vec3d woomerdAtBase;
	final BlockPos target;
	/** The world spawn: gerald_2009's place, and where anyone killed comes back. */
	final Vec3d gerald;
	final BlockPos spawn;
	/** The three TNT spots against the cabin's east wall. */
	final BlockPos[] tnt;

	private FilmScene(BlockPos house, BlockPos base, int kortevY, int geraldY) {
		this.house = house;
		this.base = base;
		this.kortev = new Vec3d(house.getX() + 2.5, kortevY, house.getZ() + 11.5);
		this.woomerdAtHouse = new Vec3d(house.getX() + 6.5, house.getY(), house.getZ() + 0.5);
		this.woomerdAtBase = new Vec3d(base.getX() - 4.5, base.getY(), base.getZ() + 0.5);
		this.target = new BlockPos(base.getX() - 6, base.getY() - 1, base.getZ());
		this.gerald = new Vec3d(target.getX() - SPAWN_WEST + 0.5, geraldY, target.getZ() + SPAWN_SOUTH + 0.5);
		this.spawn = BlockPos.ofFloored(gerald).add(3, 0, 0);
		this.tnt = new BlockPos[] {house.add(4, 0, -1), house.add(4, 0, 0), house.add(4, 0, 1)};
	}

	/** Finds flat plains near the spawn (falling back to the flattest ground round it), and builds the scene there. */
	static FilmScene build(ServerWorld world) {
		BlockPos origin = world.getSpawnPos();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		Pair<BlockPos, RegistryEntry<Biome>> plains = world.locateBiome(
				entry -> entry.matchesKey(BiomeKeys.PLAINS) || entry.matchesKey(BiomeKeys.SUNFLOWER_PLAINS), origin.withY(80), 6400, 64, 64);
		BlockPos around = plains != null ? plains.getFirst() : origin;
		ShootingStar.LOGGER.info("[film] plains near {}: {}", origin.toShortString(), plains == null ? "none" : around.toShortString());
		int bestX = around.getX();
		int bestZ = around.getZ();
		double best = Double.MAX_VALUE;
		for (int dx = -320; dx <= 320; dx += 40) {
			for (int dz = -320; dz <= 320; dz += 40) {
				int x = around.getX() + dx;
				int z = around.getZ() + dz;
				double score = score(world, generator, noise, x, z);
				if (score < best) {
					best = score;
					bestX = x;
					bestZ = z;
				}
			}
		}
		ShootingStar.LOGGER.info("[film] the cabin goes at {} {} (score {})", bestX, bestZ, String.format("%.1f", best));
		// The ground is real only once its chunks are made: everything the scene covers, with a margin.
		for (int cx = (bestX - 24) >> 4; cx <= (bestX + BASE_EAST + 24) >> 4; cx++) {
			for (int cz = (bestZ - 24) >> 4; cz <= (bestZ + SPAWN_SOUTH + 32) >> 4; cz++) {
				world.getChunk(cx, cz);
			}
		}
		int houseY = ground(world, bestX, bestZ);
		int baseY = ground(world, bestX + BASE_EAST, bestZ);
		BlockPos house = new BlockPos(bestX, houseY, bestZ);
		BlockPos base = new BlockPos(bestX + BASE_EAST, baseY, bestZ);
		int gx = base.getX() - 6 - SPAWN_WEST;
		int gz = base.getZ() + SPAWN_SOUTH;
		FilmScene scene = new FilmScene(house, base, ground(world, bestX + 2, bestZ + 11), ground(world, gx, gz));
		scene.clear(world);
		scene.pad(world, house, 6);
		scene.pad(world, base, 6);
		scene.cabin(world);
		scene.farm(world);
		scene.fort(world);
		// The scene's chunks kept loaded whoever is where.
		for (int cx = (bestX - 16) >> 4; cx <= (bestX + BASE_EAST + 16) >> 4; cx++) {
			for (int cz = (bestZ - 16) >> 4; cz <= (gz + 16) >> 4; cz++) {
				world.setChunkForced(cx, cz, true);
			}
		}
		world.setSpawnPos(scene.spawn, 0.0F);
		ShootingStar.LOGGER.info("[film] cabin {} base {} target {} spawn {}", house.toShortString(), base.toShortString(),
				scene.target.toShortString(), scene.spawn.toShortString());
		return scene;
	}

	/**
	 * How unsuited a cabin at {@code x, z} is: the spread of the ground's height over the whole scene (cabin, base, the
	 * way between and down to the spawn), plus heavy penalties for water and for anything but plains.
	 */
	private static double score(ServerWorld world, ChunkGenerator generator, NoiseConfig noise, int x, int z) {
		double sum = 0;
		double sumSq = 0;
		int n = 0;
		int wet = 0;
		int other = 0;
		for (int dx = -12; dx <= BASE_EAST + 12; dx += 12) {
			for (int dz = -12; dz <= SPAWN_SOUTH + 12; dz += 12) {
				int h = generator.getHeight(x + dx, z + dz, Heightmap.Type.WORLD_SURFACE_WG, world, noise);
				sum += h;
				sumSq += (double) h * h;
				n++;
				if (h <= world.getSeaLevel()) {
					wet++;
				}
				RegistryEntry<Biome> biome = generator.getBiomeSource().getBiome((x + dx) >> 2, h >> 2, (z + dz) >> 2,
						noise.getMultiNoiseSampler());
				if (!biome.matchesKey(BiomeKeys.PLAINS) && !biome.matchesKey(BiomeKeys.SUNFLOWER_PLAINS)) {
					other++;
				}
			}
		}
		double mean = sum / n;
		return sumSq / n - mean * mean + 400.0 * wet / n * 10 + 300.0 * other / n * 10;
	}

	/** The top of the ground at {@code x, z}: the first air above the highest solid block, leaves and plants aside. */
	static int ground(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		while (y > world.getBottomY() && isPlant(world.getBlockState(new BlockPos(x, y - 1, z)))) {
			y--;
		}
		return y;
	}

	private static boolean isPlant(BlockState state) {
		return state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES) || state.isReplaceable() || state.isOf(Blocks.BEE_NEST);
	}

	/** Trees and bushes off the scene: round the buildings, along the way between them and between them and the spawn. */
	private void clear(ServerWorld world) {
		clearAround(world, house, 10);
		clearAround(world, base, 10);
		for (int x = house.getX() + 3; x <= base.getX() - 3; x++) {
			for (int dz = -4; dz <= 4; dz++) {
				clearColumn(world, x, house.getZ() + dz);
			}
		}
		// Sight lines from the spawn to both buildings, and from Kortev's spot to the base.
		line(world, gerald, Vec3d.ofCenter(house));
		line(world, gerald, Vec3d.ofCenter(base));
		line(world, gerald, kortev);
		line(world, kortev, Vec3d.ofCenter(target));
		clearAround(world, BlockPos.ofFloored(gerald), 4);
		clearAround(world, BlockPos.ofFloored(kortev), 3);
	}

	private static void line(ServerWorld world, Vec3d from, Vec3d to) {
		int steps = (int) Math.ceil(from.distanceTo(to));
		for (int i = 0; i <= steps; i++) {
			Vec3d p = from.lerp(to, i / (double) steps);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					clearColumn(world, MathHelper.floor(p.x) + dx, MathHelper.floor(p.z) + dz);
				}
			}
		}
	}

	private static void clearAround(ServerWorld world, BlockPos at, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				clearColumn(world, at.getX() + dx, at.getZ() + dz);
			}
		}
	}

	/** Logs, leaves and tall plants out of a column, from the ground up (grass and flowers stay). */
	private static void clearColumn(ServerWorld world, int x, int z) {
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
		int floor = ground(world, x, z);
		for (int y = floor; y <= top + 1; y++) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState s = world.getBlockState(p);
			if (s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES) || s.isOf(Blocks.BEE_NEST) || s.isOf(Blocks.VINE)) {
				world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
			}
		}
	}

	/** Levels the ground round {@code at} to its height, grass on top. */
	private void pad(ServerWorld world, BlockPos at, int r) {
		int y = at.getY();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int x = at.getX() + dx;
				int z = at.getZ() + dz;
				for (int yy = y; yy <= y + 14; yy++) {
					set(world, new BlockPos(x, yy, z), Blocks.AIR.getDefaultState());
				}
				for (int yy = y - 4; yy < y - 1; yy++) {
					BlockPos p = new BlockPos(x, yy, z);
					if (world.getBlockState(p).isAir() || !world.getFluidState(p).isEmpty()) {
						set(world, p, Blocks.DIRT.getDefaultState());
					}
				}
				set(world, new BlockPos(x, y - 1, z), Blocks.GRASS_BLOCK.getDefaultState());
			}
		}
	}

	/** Kortev's cabin: a 7x7 oak cabin with log corners, windows, a door to the south and a stepped spruce roof. */
	private void cabin(ServerWorld world) {
		int x0 = house.getX();
		int y0 = house.getY();
		int z0 = house.getZ();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				set(world, new BlockPos(x0 + dx, y0 - 1, z0 + dz), Blocks.SPRUCE_PLANKS.getDefaultState());
				boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
				if (!edge) {
					continue;
				}
				boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
				for (int dy = 0; dy < 4; dy++) {
					BlockState s = corner ? Blocks.OAK_LOG.getDefaultState() : Blocks.OAK_PLANKS.getDefaultState();
					boolean window = !corner && (dy == 1 || dy == 2) && (Math.abs(dx) == 3 ? Math.abs(dz) == 1 : Math.abs(dx) == 1);
					if (window) {
						s = Blocks.GLASS_PANE.getDefaultState();
					}
					set(world, new BlockPos(x0 + dx, y0 + dy, z0 + dz), s);
				}
			}
		}
		// The door, in the middle of the south wall.
		BlockState door = Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH);
		set(world, new BlockPos(x0, y0, z0 + 3), door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(world, new BlockPos(x0, y0 + 1, z0 + 3), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		// Inside: a crafting table, a furnace, a chest and a light.
		set(world, new BlockPos(x0 - 2, y0, z0 - 2), Blocks.CRAFTING_TABLE.getDefaultState());
		set(world, new BlockPos(x0 - 1, y0, z0 - 2), Blocks.FURNACE.getDefaultState());
		set(world, new BlockPos(x0 + 2, y0, z0 - 2), Blocks.CHEST.getDefaultState());
		set(world, new BlockPos(x0, y0 + 2, z0 - 2), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, Direction.SOUTH));
		// Torches either side of the door, outside.
		set(world, new BlockPos(x0 - 1, y0 + 2, z0 + 4), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, Direction.SOUTH));
		set(world, new BlockPos(x0 + 1, y0 + 2, z0 + 4), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, Direction.SOUTH));
		// The roof: rings of spruce stairs stepping in, an overhang of one, planks to close it.
		for (int ring = 0; ring <= 4; ring++) {
			int r = 4 - ring;
			int y = y0 + 4 + ring;
			if (r == 0) {
				set(world, new BlockPos(x0, y, z0), Blocks.SPRUCE_SLAB.getDefaultState());
				break;
			}
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos p = new BlockPos(x0 + dx, y, z0 + dz);
					if (Math.abs(dx) < r && Math.abs(dz) < r) {
						set(world, p, Blocks.SPRUCE_PLANKS.getDefaultState());
						continue;
					}
					Direction facing = Math.abs(dz) == r ? (dz < 0 ? Direction.SOUTH : Direction.NORTH) : (dx < 0 ? Direction.EAST : Direction.WEST);
					set(world, p, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, facing));
				}
			}
		}
	}

	/** A little wheat field west of the cabin, fenced, with water in the middle. */
	private void farm(ServerWorld world) {
		int x0 = house.getX() - 10;
		int z0 = house.getZ();
		int y = house.getY() - 1;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				BlockPos p = new BlockPos(x0 + dx, y, z0 + dz);
				if (dx == 0 && dz == 0) {
					set(world, p, Blocks.WATER.getDefaultState());
					continue;
				}
				set(world, p, Blocks.FARMLAND.getDefaultState());
				set(world, p.up(), Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, 3 + ((dx * 7 + dz * 3) & 3) + ((dx + dz) & 1)));
			}
		}
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (Math.abs(dx) == 4 || Math.abs(dz) == 4) {
					set(world, new BlockPos(x0 + dx, y + 1, z0 + dz), Blocks.OAK_FENCE.getDefaultState());
				}
			}
		}
		set(world, new BlockPos(x0 + 4, y + 2, z0 + 4), Blocks.TORCH.getDefaultState());
		set(world, new BlockPos(x0 - 4, y + 2, z0 - 4), Blocks.TORCH.getDefaultState());
	}

	/**
	 * Woomerd's base: a squat cobblestone fort with battlements, a door on the south side, and the whole west wall a big red
	 * W, facing the cabin.
	 */
	private void fort(ServerWorld world) {
		int x0 = base.getX();
		int y0 = base.getY();
		int z0 = base.getZ();
		String[] w = {"X...X", "X...X", "X.X.X", "X.X.X", ".X.X."};
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				set(world, new BlockPos(x0 + dx, y0 - 1, z0 + dz), Blocks.STONE_BRICKS.getDefaultState());
				boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
				for (int dy = 0; dy < 6; dy++) {
					BlockPos p = new BlockPos(x0 + dx, y0 + dy, z0 + dz);
					if (dy == 5) {
						// Battlements round the top.
						if (edge && ((dx + dz) & 1) == 0) {
							set(world, p, Blocks.COBBLESTONE.getDefaultState());
						}
						continue;
					}
					if (!edge) {
						if (dy == 4) {
							set(world, p, Blocks.COBBLESTONE_SLAB.getDefaultState());
						}
						continue;
					}
					BlockState s = Math.abs(dx) == 3 && Math.abs(dz) == 3 ? Blocks.STONE_BRICKS.getDefaultState()
							: Blocks.COBBLESTONE.getDefaultState();
					if (dx == -3 && Math.abs(dz) <= 2 && dy <= 4) {
						char c = w[4 - dy].charAt(dz + 2);
						if (c == 'X') {
							s = Blocks.RED_CONCRETE.getDefaultState();
						}
					}
					set(world, p, s);
				}
			}
		}
		BlockState door = Blocks.SPRUCE_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH);
		set(world, new BlockPos(x0, y0, z0 + 3), door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(world, new BlockPos(x0, y0 + 1, z0 + 3), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		set(world, new BlockPos(x0 - 1, y0 + 2, z0 + 4), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, Direction.SOUTH));
		set(world, new BlockPos(x0 + 1, y0 + 2, z0 + 4), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, Direction.SOUTH));
		set(world, new BlockPos(x0 - 2, y0, z0 - 2), Blocks.CHEST.getDefaultState());
		set(world, new BlockPos(x0 - 1, y0, z0 - 2), Blocks.CHEST.getDefaultState());
		set(world, new BlockPos(x0 + 2, y0, z0 + 2), Blocks.ANVIL.getDefaultState());
		// A fence and a torch either side of where Woomerd stands.
		for (int dz : new int[] {-2, 2}) {
			set(world, new BlockPos(x0 - 5, y0, z0 + dz), Blocks.OAK_FENCE.getDefaultState().with(FenceBlock.NORTH, false));
			set(world, new BlockPos(x0 - 5, y0 + 1, z0 + dz), Blocks.TORCH.getDefaultState());
		}
	}

	private static void set(ServerWorld world, BlockPos p, BlockState s) {
		world.setBlockState(p, s, 3);
	}
}
