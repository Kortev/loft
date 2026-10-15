package io.github.kortev.shootingstar.test;

import com.mojang.datafixers.util.Pair;
import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.registry.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * Where the film's scene is and what is built there: Kortev's cabin with a wheat farm south-west of it and a chest by
 * the farm (the Genesis Key in it), Woomerd's base (forty-odd blocks east, a big red W on the wall that faces the
 * cabin), and the world spawn south of both, where anyone killed comes back. Found on flat plains near the test world's
 * spawn, so it looks like any survival server's. Everything is axis-aligned: the base is east (+x) of the cabin, the
 * spawn south (+z).
 */
final class FilmScene {
	/** How far east of the cabin the base is. */
	static final int BASE_EAST = 45;
	/**
	 * The spawn, from the key's target: west and south, outside the hole and its fissures (the film's zone is
	 * {@link FilmSelfTest#RADIUS} round, the fissures reach seven tenths of that again), near enough to see it all.
	 */
	static final int SPAWN_WEST = 14;
	static final int SPAWN_SOUTH = 40;
	/** The yard levelled round the cabin (the farm to the south-west, the chest to the south), and round the base. */
	private static final int YARD_WEST = 15;
	private static final int YARD_SOUTH = 16;
	private static final int YARD = 6;
	/** How far the ground is blended back from a levelled yard to its own height. */
	private static final int BLEND = 8;

	/** The cabin's middle, on its floor. */
	final BlockPos house;
	/** The base's middle, on its floor. */
	final BlockPos base;
	/** The farm's middle (its water), on the farmland's level, and the chest with the key in it beside it. */
	final BlockPos farm;
	final BlockPos chest;
	/** Where Kortev starts (on the path down the farm's east side) and stands to open the chest (and turn the key). */
	final Vec3d kortev;
	final Vec3d atChest;
	/**
	 * Where Woomerd starts (north-east of the cabin, out of Kortev's sight), their spot by the cabin's east wall, and
	 * where they stop on the way back to the base to watch it go up.
	 */
	Vec3d woomerdStart;
	final Vec3d woomerdAtHouse;
	Vec3d woomerdSafe;
	/** Woomerd's spot in front of the W, and the block the key is aimed at (just in front of it). */
	final Vec3d woomerdAtBase;
	final BlockPos target;
	/** Where anyone killed comes back: the world spawn, where it can all be seen from. */
	final Vec3d respawn;
	final BlockPos spawn;
	/** The three TNT spots against the cabin's east wall. */
	final BlockPos[] tnt;

	private FilmScene(BlockPos house, BlockPos base, int yardY, BlockPos respawn) {
		this.house = house;
		this.base = base;
		this.farm = house.add(-6, -1, 10);
		this.chest = house.add(1, 0, 10);
		this.kortev = new Vec3d(house.getX() - 1.5, yardY, house.getZ() + 8.5);
		this.atChest = new Vec3d(house.getX() - 0.5, yardY, house.getZ() + 10.5);
		this.woomerdStart = new Vec3d(house.getX() + 14.5, house.getY(), house.getZ() - 1.5);
		this.woomerdAtHouse = new Vec3d(house.getX() + 6.5, house.getY(), house.getZ() + 0.5);
		this.woomerdSafe = new Vec3d(house.getX() + 14.5, house.getY(), house.getZ() + 4.5);
		this.woomerdAtBase = new Vec3d(base.getX() - 4.5, base.getY(), base.getZ() + 0.5);
		this.target = new BlockPos(base.getX() - 6, base.getY() - 1, base.getZ());
		this.respawn = Vec3d.ofBottomCenter(respawn);
		this.spawn = respawn;
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
		// The base no lower than a couple of blocks under the cabin's yard: lower, the yard's edge hid Kortev from it.
		int baseY = Math.max(ground(world, bestX + BASE_EAST, bestZ), houseY - 2);
		BlockPos house = new BlockPos(bestX, houseY, bestZ);
		BlockPos base = new BlockPos(bestX + BASE_EAST, baseY, bestZ);
		int gx = base.getX() - 6 - SPAWN_WEST;
		int gz = base.getZ() + SPAWN_SOUTH;
		// Where everything goes, to clear the trees and shape the ground round it; then again, standing on that ground, with
		// the spawn where the ground lets anyone there see it all; and the way cleared from there too.
		FilmScene rough = new FilmScene(house, base, houseY, new BlockPos(gx, ground(world, gx, gz), gz));
		rough.clear(world);
		level(world, house.getX() - YARD_WEST, house.getZ() - YARD, house.getX() + YARD, house.getZ() + YARD_SOUTH, houseY);
		level(world, base.getX() - YARD, base.getZ() - YARD, base.getX() + YARD, base.getZ() + YARD, baseY);
		FilmScene scene = new FilmScene(house, base, houseY, lookout(world, gx, gz, rough));
		scene.clear(world);
		// Out of the yards, on the ground where it is.
		scene.woomerdStart = onGround(world, scene.woomerdStart);
		scene.woomerdSafe = onGround(world, scene.woomerdSafe);
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

	/**
	 * Where the spawn goes (where Woomerd comes back, to see the black coming): the place within a few blocks of (x, z)
	 * that sees the most of the scene over the ground (Kortev's chest, the base, the cabin, where the key comes down),
	 * nearest (x, z) of those; and never nearer the hole than its fissures reach. Trees in the way don't count: the way
	 * from there is cleared of them.
	 */
	private static BlockPos lookout(ServerWorld world, int x, int z, FilmScene at) {
		Vec3d[] sights = {at.atChest.add(0.0, 1.62, 0.0), at.woomerdAtBase.add(0.0, 1.62, 0.0), Vec3d.ofCenter(at.house).add(0.0, 2.0, 0.0),
				Vec3d.ofCenter(at.target).add(0.0, 1.5, 0.0)};
		BlockPos best = new BlockPos(x, ground(world, x, z), z);
		double bestScore = Double.MAX_VALUE;
		for (int dx = -12; dx <= 12; dx++) {
			for (int dz = -12; dz <= 12; dz++) {
				int px = x + dx;
				int pz = z + dz;
				if (Math.hypot(px - at.target.getX(), pz - at.target.getZ()) < 1.9 * FilmSelfTest.RADIUS) {
					continue;
				}
				int y = ground(world, px, pz);
				Vec3d eye = new Vec3d(px + 0.5, y + 1.62, pz + 0.5);
				int hidden = 0;
				for (Vec3d sight : sights) {
					if (!inSight(world, eye, sight)) {
						hidden++;
					}
				}
				double score = hidden * 1000.0 + Math.hypot(dx, dz);
				if (score < bestScore) {
					bestScore = score;
					best = new BlockPos(px, y, pz);
				}
			}
		}
		ShootingStar.LOGGER.info("[film] the spawn goes at {} ({} of the scene out of sight)", best.toShortString(), (int) (bestScore / 1000.0));
		return best;
	}

	/** Nothing but air, plants and trees between {@code from} and {@code to}. */
	private static boolean inSight(ServerWorld world, Vec3d from, Vec3d to) {
		int steps = (int) Math.ceil(from.distanceTo(to) * 2.0);
		for (int i = 1; i < steps - 2; i++) {
			BlockPos p = BlockPos.ofFloored(from.lerp(to, i / (double) steps));
			BlockState s = world.getBlockState(p);
			if (!s.isAir() && !isPlant(s) && s.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static Vec3d onGround(ServerWorld world, Vec3d at) {
		return new Vec3d(at.x, ground(world, MathHelper.floor(at.x), MathHelper.floor(at.z)), at.z);
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
		// Sight lines from the spawn to both buildings and the chest, from the chest to the base, and Woomerd's ways.
		line(world, respawn, Vec3d.ofCenter(house));
		line(world, respawn, Vec3d.ofCenter(base));
		line(world, respawn, atChest);
		line(world, atChest, Vec3d.ofCenter(target));
		line(world, atChest, woomerdSafe);
		line(world, woomerdStart, woomerdAtHouse);
		line(world, woomerdSafe, woomerdAtBase);
		clearAround(world, BlockPos.ofFloored(respawn), 6);
		clearAround(world, BlockPos.ofFloored(atChest), 3);
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

	/** Logs, leaves and plants as tall as a player's eyes out of a column, from the ground up (grass and flowers stay). */
	private static void clearColumn(ServerWorld world, int x, int z) {
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
		int floor = ground(world, x, z);
		for (int y = floor; y <= top + 1; y++) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState s = world.getBlockState(p);
			if (s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES) || s.isOf(Blocks.BEE_NEST) || s.isOf(Blocks.VINE)
					|| s.getBlock() instanceof TallPlantBlock || s.isOf(Blocks.SUGAR_CANE)) {
				world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
			}
		}
	}

	/**
	 * Levels the rectangle (x0, z0)-(x1, z1) to height {@code y}, grass on top, and shapes the ground round it back to its
	 * own height over {@link #BLEND} blocks, a step at a time, so a yard sits in the land rather than on a cliff of dirt.
	 */
	private static void level(ServerWorld world, int x0, int z0, int x1, int z1, int y) {
		for (int x = x0 - BLEND; x <= x1 + BLEND; x++) {
			for (int z = z0 - BLEND; z <= z1 + BLEND; z++) {
				int dx = Math.max(0, Math.max(x0 - x, x - x1));
				int dz = Math.max(0, Math.max(z0 - z, z - z1));
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > BLEND) {
					continue;
				}
				clearColumn(world, x, z);
				int natural = ground(world, x, z);
				double t = d / (BLEND + 1.0);
				t = t * t * (3.0 - 2.0 * t);
				int want = (int) Math.round(y + (natural - y) * t);
				shape(world, x, z, want, natural, d > 0.0);
			}
		}
	}

	/** Makes the top of the ground in (x, z) {@code want}: cut down or built up with dirt, grass on top. */
	private static void shape(ServerWorld world, int x, int z, int want, int natural, boolean wild) {
		for (int yy = want; yy <= Math.max(want, natural) + 14; yy++) {
			BlockPos p = new BlockPos(x, yy, z);
			if (!world.getBlockState(p).isAir()) {
				set(world, p, Blocks.AIR.getDefaultState());
			}
		}
		for (int yy = Math.min(want, natural) - 4; yy < want - 1; yy++) {
			BlockPos p = new BlockPos(x, yy, z);
			if (yy >= natural - 1 || world.getBlockState(p).isAir() || !world.getFluidState(p).isEmpty()) {
				set(world, p, Blocks.DIRT.getDefaultState());
			}
		}
		set(world, new BlockPos(x, want - 1, z), Blocks.GRASS_BLOCK.getDefaultState());
		// Out of the yard, grass grows on it again as round about.
		if (wild && Math.floorMod(x * 73856093 ^ z * 19349663, 7) < 2) {
			set(world, new BlockPos(x, want, z), Blocks.SHORT_GRASS.getDefaultState());
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

	/**
	 * A wheat field south-west of the cabin, water in the middle, fenced but for its east side (where Kortev works along
	 * it, the wheat nearest ripe); and by it the chest with the Genesis Key in it, among Kortev's things.
	 */
	private void farm(ServerWorld world) {
		int x0 = farm.getX();
		int z0 = farm.getZ();
		int y = farm.getY();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				BlockPos p = new BlockPos(x0 + dx, y, z0 + dz);
				if (dx == 0 && dz == 0) {
					set(world, p, Blocks.WATER.getDefaultState());
					continue;
				}
				set(world, p, Blocks.FARMLAND.getDefaultState());
				int age = dx >= 2 ? CropBlock.MAX_AGE : 3 + ((dx * 7 + dz * 3) & 3) + ((dx + dz) & 1);
				set(world, p.up(), Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, Math.min(CropBlock.MAX_AGE, age)));
			}
		}
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (dx == -4 || Math.abs(dz) == 4 && dx < 4) {
					set(world, new BlockPos(x0 + dx, y + 1, z0 + dz), Blocks.OAK_FENCE.getDefaultState());
				}
			}
		}
		set(world, new BlockPos(x0 - 4, y + 2, z0 - 4), Blocks.TORCH.getDefaultState());
		set(world, new BlockPos(x0 - 4, y + 2, z0 + 4), Blocks.TORCH.getDefaultState());
		set(world, chest, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST));
		set(world, chest.north(), Blocks.BARREL.getDefaultState());
		set(world, chest.south(), Blocks.COMPOSTER.getDefaultState());
		if (world.getBlockEntity(chest) instanceof ChestBlockEntity box) {
			box.setStack(0, new ItemStack(Items.IRON_INGOT, 23));
			box.setStack(1, new ItemStack(Items.COAL, 41));
			box.setStack(4, new ItemStack(Items.BREAD, 12));
			box.setStack(9, new ItemStack(Items.OAK_PLANKS, 64));
			box.setStack(13, new ItemStack(ModItems.GENESIS_KEY));
			box.setStack(17, new ItemStack(Items.DIAMOND, 5));
			box.setStack(22, new ItemStack(Items.ENDER_PEARL, 4));
			box.setStack(26, new ItemStack(Items.WHEAT, 37));
		}
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
