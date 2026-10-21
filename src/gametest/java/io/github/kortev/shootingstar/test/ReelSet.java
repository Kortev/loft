package io.github.kortev.shootingstar.test;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * Where the first reel happens, built by the reel itself so every take is the same: high in the open air (the light the
 * sky's), at night in the rain. Denji's shack (west), a stretch of wet asphalt, the abandoned warehouse (its big doors
 * in its west wall, a lamp hanging in the middle, pillars, crates), and behind it the alley (the dumpster is drawn, not
 * built: {@link ReelSelfTest}). Everything relative to the origin, on the floor: +x east, +z south.
 */
final class ReelSet {
	/** The warehouse: its walls' corners, and its height. */
	static final int WEST = 0;
	static final int EAST = 22;
	static final int NORTH = -8;
	static final int SOUTH = 8;
	static final int HIGH = 8;
	/** Its big doors in the west wall: from z, to z, and how tall. */
	static final int DOOR_FROM = -2;
	static final int DOOR_TO = 2;
	static final int DOOR_HIGH = 5;
	/** The back door into the alley, in the east wall. */
	static final int BACK = 0;
	/** The shack: its walls' corners, its door (in its east wall). */
	static final int SHACK_WEST = -26;
	static final int SHACK_EAST = -20;
	static final int SHACK_NORTH = -4;
	static final int SHACK_SOUTH = 2;
	static final int SHACK_DOOR = -1;
	/** The alley: out to its east wall. */
	static final int ALLEY_EAST = 32;

	private ReelSet() {
	}

	static void build(ServerWorld world, BlockPos o) {
		Random random = Random.create(1124);
		// Clear the air and lay the ground: wet asphalt.
		for (BlockPos p : BlockPos.iterate(o.add(-36, -1, -22), o.add(42, 14, 22))) {
			BlockState state = Blocks.AIR.getDefaultState();
			if (p.getY() == o.getY() - 1) {
				state = random.nextFloat() < 0.15F ? Blocks.GRAY_CONCRETE.getDefaultState() : Blocks.BLACK_CONCRETE.getDefaultState();
			}
			world.setBlockState(p, state, 2);
		}
		warehouse(world, o, random);
		shack(world, o, random);
		alley(world, o);
	}

	private static void warehouse(ServerWorld world, BlockPos o, Random random) {
		for (int x = WEST; x <= EAST; x++) {
			for (int z = NORTH; z <= SOUTH; z++) {
				boolean wall = x == WEST || x == EAST || z == NORTH || z == SOUTH;
				// The floor: cracked concrete.
				set(world, o.add(x, -1, z), random.nextFloat() < 0.12F ? Blocks.CRACKED_STONE_BRICKS : Blocks.GRAY_CONCRETE);
				for (int y = 0; y <= HIGH; y++) {
					if (wall) {
						float r = random.nextFloat();
						set(world, o.add(x, y, z), r < 0.7F ? Blocks.GRAY_CONCRETE : r < 0.9F ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS);
					}
				}
				// The roof, with a couple of holes the rain comes through.
				boolean hole = (x == 8 || x == 9) && z == 3 || x == 15 && (z == -4 || z == -5);
				set(world, o.add(x, HIGH + 1, z), hole ? Blocks.AIR : Blocks.DEEPSLATE_TILES);
			}
		}
		// High windows, barred, along the long walls.
		for (int x = WEST + 3; x < EAST; x += 4) {
			for (int y = 5; y <= 6; y++) {
				set(world, o.add(x, y, NORTH), Blocks.IRON_BARS);
				set(world, o.add(x, y, SOUTH), Blocks.IRON_BARS);
			}
		}
		doors(world, o, true);
		// The back door into the alley.
		for (int y = 0; y <= 2; y++) {
			set(world, o.add(EAST, y, BACK), Blocks.AIR);
			set(world, o.add(EAST, y, BACK + 1), Blocks.AIR);
		}
		// Pillars, crates and barrels along the walls.
		for (int[] pillar : new int[][] {{6, -5}, {6, 5}, {16, -5}, {16, 5}}) {
			for (int y = 0; y <= HIGH; y++) {
				set(world, o.add(pillar[0], y, pillar[1]), Blocks.STONE_BRICKS);
			}
		}
		for (int[] crate : new int[][] {{2, 0, -7}, {3, 0, -7}, {2, 1, -7}, {2, 0, -6}, {20, 0, 7}, {19, 0, 7}, {20, 1, 7}, {20, 0, -7}, {12, 0, 7}}) {
			set(world, o.add(crate[0], crate[1], crate[2]), crate[1] > 0 ? Blocks.BARREL : Blocks.SPRUCE_PLANKS);
		}
		// The lamps, hanging on their chains: in the middle, and a higher one in by the doors.
		for (int y = 6; y <= HIGH; y++) {
			set(world, o.add(11, y, 0), Blocks.CHAIN);
		}
		world.setBlockState(o.add(11, 5, 0), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), 2);
		set(world, o.add(4, HIGH, 0), Blocks.CHAIN);
		world.setBlockState(o.add(4, HIGH - 1, 0), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), 2);
	}

	/** The warehouse's big doors, shut (iron) or open (none of it); {@code open} of the way, from the middle out. */
	static void doors(ServerWorld world, BlockPos o, boolean shut) {
		doors(world, o, shut ? 0.0F : 1.0F);
	}

	static void doors(ServerWorld world, BlockPos o, float open) {
		int half = Math.round(open * 3);
		for (int z = DOOR_FROM; z <= DOOR_TO; z++) {
			boolean gone = Math.abs(z) < half;
			for (int y = 0; y < DOOR_HIGH; y++) {
				set(world, o.add(WEST, y, z), gone ? Blocks.AIR : Blocks.IRON_BLOCK);
			}
		}
	}

	private static void shack(ServerWorld world, BlockPos o, Random random) {
		for (int x = SHACK_WEST; x <= SHACK_EAST; x++) {
			for (int z = SHACK_NORTH; z <= SHACK_SOUTH; z++) {
				boolean wall = x == SHACK_WEST || x == SHACK_EAST || z == SHACK_NORTH || z == SHACK_SOUTH;
				set(world, o.add(x, -1, z), Blocks.SPRUCE_PLANKS);
				for (int y = 0; y <= 3; y++) {
					if (wall) {
						set(world, o.add(x, y, z), random.nextFloat() < 0.6F ? Blocks.OAK_PLANKS : Blocks.STRIPPED_SPRUCE_WOOD);
					}
				}
				set(world, o.add(x, 4, z), Blocks.SPRUCE_SLAB);
			}
		}
		// A window to the north; the door in the east wall.
		set(world, o.add(SHACK_WEST + 3, 2, SHACK_NORTH), Blocks.GLASS_PANE);
		world.setBlockState(o.add(SHACK_EAST, 0, SHACK_DOOR), Blocks.SPRUCE_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.WEST)
				.with(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
		world.setBlockState(o.add(SHACK_EAST, 1, SHACK_DOOR), Blocks.SPRUCE_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.WEST)
				.with(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
		// Inside: a bed of a blanket on the floor, a few candles.
		set(world, o.add(SHACK_WEST + 1, 0, SHACK_SOUTH - 1), Blocks.WHITE_CARPET);
		set(world, o.add(SHACK_WEST + 2, 0, SHACK_SOUTH - 1), Blocks.WHITE_CARPET);
		world.setBlockState(o.add(SHACK_WEST + 1, 0, SHACK_NORTH + 1), Blocks.CANDLE.getDefaultState()
				.with(net.minecraft.block.CandleBlock.CANDLES, 3).with(net.minecraft.block.CandleBlock.LIT, true), 2);
	}

	/** Opens or shuts the shack's door. */
	static void shackDoor(ServerWorld world, BlockPos o, boolean open) {
		for (int y = 0; y <= 1; y++) {
			BlockPos p = o.add(SHACK_EAST, y, SHACK_DOOR);
			BlockState state = world.getBlockState(p);
			if (state.contains(DoorBlock.OPEN)) {
				world.setBlockState(p, state.with(DoorBlock.OPEN, open), 2);
			}
		}
	}

	private static void alley(ServerWorld world, BlockPos o) {
		for (int x = EAST + 1; x <= ALLEY_EAST; x++) {
			for (int y = 0; y <= 6; y++) {
				set(world, o.add(x, y, NORTH), Blocks.BRICKS);
				set(world, o.add(x, y, SOUTH), Blocks.BRICKS);
			}
		}
		for (int z = NORTH; z <= SOUTH; z++) {
			for (int y = 0; y <= 6; y++) {
				set(world, o.add(ALLEY_EAST, y, z), Blocks.BRICKS);
			}
		}
		// A lamp on the wall, dim; one over the back door; one on the wall by the bin.
		set(world, o.add(ALLEY_EAST - 1, 2, -3), Blocks.STONE_BRICK_WALL);
		world.setBlockState(o.add(ALLEY_EAST - 1, 3, -3), Blocks.SOUL_LANTERN.getDefaultState(), 2);
		for (int[] lamp : new int[][] {{EAST + 1, 0}, {28, SOUTH - 1}}) {
			set(world, o.add(lamp[0], 4, lamp[1]), Blocks.IRON_BARS);
			world.setBlockState(o.add(lamp[0], 3, lamp[1]), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), 2);
		}
	}

	private static void set(ServerWorld world, BlockPos p, net.minecraft.block.Block block) {
		world.setBlockState(p, block.getDefaultState(), 2);
	}
}
