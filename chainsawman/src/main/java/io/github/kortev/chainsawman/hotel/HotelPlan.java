package io.github.kortev.chainsawman.hotel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * The Eternity Devil's hotel, as a plan in its own coordinates: a plain eight-storey city hotel, 38 blocks long (x) and 14 deep (z), a storey
 * every 4 blocks, its front (the entrance and the lobby) on z 0. {@link HotelBuilder} puts it down in the world at any of four turns.
 *
 * <pre>
 * z 0        front wall          rooms z 1-3 (front rooms, 6 along x, the third of them the stairwell)
 * z 4        wall, doors         corridor z 5-7 (36 long, x 1-36)
 * z 8        wall, doors         rooms z 9-12 (back rooms)       z 13 back wall
 * </pre>
 *
 * Storey i has its slab at y = 4i (floor 1 is i 0), the walking surface at 4i + 1, three blocks of air, and the next slab as its ceiling.
 * The 8th floor's corridor is the loop: everything in it repeats every {@link #PERIOD} blocks, so a walk past {@link #WINDOW_HI} can be put
 * back {@link #PERIOD} blocks with nothing to see ({@link #WINDOW_LO}).
 */
public final class HotelPlan {
	public static final int SX = 38;
	public static final int SY = 36;
	public static final int SZ = 14;
	public static final int PITCH = 4;
	public static final int FLOORS = 8;
	/** The corridor's cells (both ends included), and its walls' z. */
	public static final int CORR_X0 = 1;
	public static final int CORR_X1 = 36;
	public static final int CORR_Z0 = 5;
	public static final int CORR_Z1 = 7;
	/** The loop: the pattern repeats every PERIOD blocks, the walkable window is [WINDOW_LO, WINDOW_HI). */
	public static final int PERIOD = 12;
	public static final int WINDOW_LO = 13;
	public static final int WINDOW_HI = 25;
	/** The stairwell: front room 2, x 13-17, z 1-3. */
	public static final int SHAFT_X0 = 13;
	public static final int SHAFT_X1 = 17;
	/** Where the home (the devil's heart of the place) is buried, under the lobby. */
	public static final int HOME_X = 24;
	public static final int HOME_Y = -1;
	public static final int HOME_Z = 3;

	public enum DoorKind {
		ROOM, STAIR, ENTRANCE, LIFT
	}

	public record P(int x, int y, int z) {
	}

	public record Door(int x, int y, int z, DoorKind kind) {
	}

	public record Glass(int x, int y, int z, int group) {
	}

	public record Sign(int x, int y, int z, Direction facing, String[] lines) {
	}

	/** A ceiling lamp; {@code phase} is x mod 12 on the loop's floor, else -1 (lobby), -2 (the rest). */
	public record Lamp(int x, int y, int z, int phase, int floor) {
	}

	private final BlockState[] cells = new BlockState[SX * SY * SZ];
	public final List<Door> doors = new ArrayList<>();
	public final List<Glass> glass = new ArrayList<>();
	public final List<Sign> signs = new ArrayList<>();
	public final List<Lamp> lamps = new ArrayList<>();
	/** Chains in front of the doors by day. */
	public final List<P> chains = new ArrayList<>();
	/** Invisible light blocks of the lit rooms, by window group (the lobby is group 0). */
	public final List<Glass> lights = new ArrayList<>();
	/** The window groups that are lit at night; the stairwell's lights (groups 1000 on) are on every night. */
	public final java.util.Set<Integer> litGroups = new java.util.HashSet<>();
	public P breathDoor;
	public P phone;
	public P bell;
	/** The corridor's ends of the loop floor, where flesh grows as the laps go on: level 1, 2, 3. */
	public final List<List<P>> flesh = new ArrayList<>();
	public int groups = 1;

	private static HotelPlan instance;

	public static synchronized HotelPlan get() {
		if (instance == null) {
			instance = new HotelPlan();
		}
		return instance;
	}

	public static int floorY(int floor) {
		return PITCH * floor;
	}

	/** The block of the plan at {@code (x, y, z)}: air outside it or where nothing is put. */
	public BlockState at(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= SX || y >= SY || z >= SZ) {
			return Blocks.AIR.getDefaultState();
		}
		BlockState s = cells[(y * SZ + z) * SX + x];
		return s == null ? Blocks.AIR.getDefaultState() : s;
	}

	private void set(int x, int y, int z, BlockState s) {
		if (x < 0 || y < 0 || z < 0 || x >= SX || y >= SY || z >= SZ) {
			return;
		}
		cells[(y * SZ + z) * SX + x] = s;
	}

	private void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
		for (int y = y0; y <= y1; y++) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					set(x, y, z, s);
				}
			}
		}
	}

	private static BlockState b(Block block) {
		return block.getDefaultState();
	}

	private static final BlockState CONCRETE = b(Blocks.LIGHT_GRAY_CONCRETE);
	private static final BlockState DARK = b(Blocks.GRAY_CONCRETE);
	private static final BlockState TILE = b(Blocks.POLISHED_ANDESITE);
	private static final BlockState QUARTZ = b(Blocks.SMOOTH_QUARTZ);
	private static final BlockState CREAM = b(Blocks.SMOOTH_SANDSTONE);
	private static final BlockState CREAM2 = b(Blocks.CUT_SANDSTONE);
	private static final BlockState SKIRT = b(Blocks.DARK_OAK_PLANKS);
	private static final BlockState GLASS_DARK = b(Blocks.BLACK_STAINED_GLASS);

	private HotelPlan() {
		for (int i = 0; i < FLOORS; i++) {
			storey(i);
		}
		roof();
		lobby();
		stairs();
		wrongThings();
		bleedAndFlesh();
	}

	// ---- The shell, floor by floor.

	private void storey(int i) {
		int y0 = floorY(i);
		boolean top = i == FLOORS - 1;
		// The slab, and the band on the outside.
		for (int z = 0; z < SZ; z++) {
			for (int x = 0; x < SX; x++) {
				boolean ring = x == 0 || z == 0 || x == SX - 1 || z == SZ - 1;
				set(x, y0, z, i == 0 ? (ring ? DARK : b(Blocks.GRAY_CONCRETE)) : (ring ? DARK : QUARTZ));
			}
		}
		// The outer walls.
		for (int y = y0 + 1; y <= y0 + 3; y++) {
			BlockState wall = i == 0 && y <= y0 + 2 ? TILE : CONCRETE;
			for (int x = 0; x < SX; x++) {
				set(x, y, 0, wall);
				set(x, y, SZ - 1, wall);
			}
			for (int z = 0; z < SZ; z++) {
				set(0, y, z, wall);
				set(SX - 1, y, z, wall);
			}
		}
		// The walls round the corridor, and between the rooms: wallpaper in stripes over a dark skirting.
		for (int y = y0 + 1; y <= y0 + 3; y++) {
			for (int x = 0; x < SX; x++) {
				BlockState paper = y == y0 + 1 ? SKIRT : (x % 3 == 0 ? CREAM2 : CREAM);
				set(x, y, 4, paper);
				set(x, y, 8, paper);
			}
			for (int j = 1; j <= 6; j++) {
				for (int z = 1; z <= 3; z++) {
					set(6 * j, y, z, y == y0 + 1 ? SKIRT : CREAM);
				}
				for (int z = 9; z <= 12; z++) {
					set(6 * j, y, z, y == y0 + 1 ? SKIRT : CREAM);
				}
			}
		}
		// The carpet in the corridor: a red runner and brown edges with a gold dash.
		for (int x = CORR_X0; x <= CORR_X1; x++) {
			set(x, y0 + 1, 5, x % 4 == 3 ? b(Blocks.YELLOW_CARPET) : b(Blocks.BROWN_CARPET));
			set(x, y0 + 1, 6, b(Blocks.RED_CARPET));
			set(x, y0 + 1, 7, x % 4 == 3 ? b(Blocks.YELLOW_CARPET) : b(Blocks.BROWN_CARPET));
		}
		// Ceiling lamps every four blocks.
		for (int x = CORR_X0; x <= CORR_X1; x++) {
			if (x % 4 == 1) {
				set(x, y0 + 4, 6, b(Blocks.REDSTONE_LAMP));
				lamps.add(new Lamp(x, y0 + 4, 6, top ? x % PERIOD : -2, i));
			}
		}
		// Rooms: eleven, and the stairwell in the third at the front.
		for (int side = 0; side < 2; side++) {
			for (int j = 0; j < 6; j++) {
				if (side == 0 && j == 2) {
					continue;
				}
				if (i == 0 && side == 0 && j >= 3) {
					continue;
				}
				room(i, side, j);
			}
		}
		if (i == 0) {
			return;
		}
	}

	private int roomGroup(int i, int side, int j) {
		return 1 + i * 12 + side * 6 + j;
	}

	private boolean roomLit(int i, int side, int j) {
		return i < FLOORS - 1 && (i * 7 + j * 3 + side * 5) % 10 < 3;
	}

	/** Room {@code j} on {@code side} (0 front, 1 back) of storey {@code i}: its door, its window, its bed. */
	private void room(int i, int side, int j) {
		int y0 = floorY(i);
		boolean top = i == FLOORS - 1;
		int x0 = 6 * j + 1;
		int x1 = 6 * j + 5;
		int z0 = side == 0 ? 1 : 9;
		int z1 = side == 0 ? 3 : 12;
		// The door: a gap in the corridor wall.
		int doorX = side == 0 ? x0 : 6 * j + 4;
		int doorZ = side == 0 ? 4 : 8;
		Direction facing = side == 0 ? Direction.NORTH : Direction.SOUTH;
		if (top && side == 0 && j % 2 == 0) {
			// On the loop's floor every other front door is the stair door: exit sign over it, "8F" beside it (the loop repeats them).
			door(doorX, y0 + 1, doorZ, facing, DoorKind.ROOM, Blocks.BIRCH_DOOR);
			set(doorX, y0 + 3, doorZ, b(Blocks.LIME_CONCRETE));
			signs.add(new Sign(doorX + 1, y0 + 2, 5, Direction.SOUTH, new String[] {"", "8F", "", ""}));
		} else {
			door(doorX, y0 + 1, doorZ, facing, DoorKind.ROOM, Blocks.DARK_OAK_DOOR);
			// A number plate in the corridor above it, and chains in front.
			String number = top ? (side == 1 ? (doorX % PERIOD == 4 ? "801" : "803") : "802")
					: (i + 1) + String.format("%02d", roomNumber(side, j));
			signs.add(new Sign(doorX, y0 + 3, side == 0 ? 5 : 7, side == 0 ? Direction.SOUTH : Direction.NORTH, new String[] {"", number, "", ""}));
			chains.add(new P(doorX, y0 + 1, side == 0 ? 5 : 7));
			chains.add(new P(doorX, y0 + 2, side == 0 ? 5 : 7));
		}
		// The window in the outer wall.
		int wz = side == 0 ? 0 : SZ - 1;
		int group = roomGroup(i, side, j);
		groups = Math.max(groups, group + 1);
		boolean lit = roomLit(i, side, j) && !(i == 0 && side == 0);
		if (lit) {
			litGroups.add(group);
		}
		for (int x = x0 + 1; x <= x0 + 3; x++) {
			for (int y = y0 + 2; y <= y0 + 3; y++) {
				set(x, y, wz, GLASS_DARK);
				glass.add(new Glass(x, y, wz, group));
			}
		}
		if (lit) {
			lights.add(new Glass((x0 + x1) / 2, y0 + 3, (z0 + z1) / 2, group));
		}
		if (top) {
			// Nothing in the rooms of the loop's floor: bare.
			return;
		}
		// The carpet and a bed against the far end, a desk by the window.
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				set(x, y0 + 1, z, b(Blocks.LIGHT_GRAY_CARPET));
			}
		}
		int bedZ = side == 0 ? z0 : z1;
		Direction head = side == 0 ? Direction.NORTH : Direction.SOUTH;
		int bz2 = side == 0 ? bedZ + 1 : bedZ - 1;
		set(x1, y0 + 1, bedZ, Blocks.WHITE_BED.getDefaultState().with(Properties.HORIZONTAL_FACING, head).with(net.minecraft.block.BedBlock.PART, BedPart.HEAD));
		set(x1, y0 + 1, bz2, Blocks.WHITE_BED.getDefaultState().with(Properties.HORIZONTAL_FACING, head).with(net.minecraft.block.BedBlock.PART, BedPart.FOOT));
		set(x1 - 1, y0 + 1, bedZ, b(Blocks.DARK_OAK_SLAB).with(Properties.SLAB_TYPE, SlabType.BOTTOM));
		set(x0 + 1, y0 + 1, side == 0 ? z0 : z1, b(Blocks.DARK_OAK_SLAB).with(Properties.SLAB_TYPE, SlabType.BOTTOM));
		set(x0 + 1, y0 + 2, side == 0 ? z0 : z1, b(Blocks.REPEATER));
	}

	/** The number of a room on a floor: the back rooms are 01-06 from the stair end, the front ones 07 on. */
	static int roomNumber(int side, int j) {
		if (side == 1) {
			return j + 1;
		}
		return j < 2 ? 7 + j : 6 + j;
	}

	private void door(int x, int y, int z, Direction facing, DoorKind kind, Block block) {
		BlockState lower = block.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.HINGE, DoorHinge.LEFT)
				.with(DoorBlock.HALF, DoubleBlockHalf.LOWER).with(DoorBlock.OPEN, false);
		set(x, y, z, lower);
		set(x, y + 1, z, lower.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		doors.add(new Door(x, y, z, kind));
	}

	private void roof() {
		int y = floorY(FLOORS);
		for (int z = 0; z < SZ; z++) {
			for (int x = 0; x < SX; x++) {
				set(x, y, z, QUARTZ);
				set(x, y + 1, z, DARK);
				if (x == 0 || z == 0 || x == SX - 1 || z == SZ - 1) {
					set(x, y + 2, z, CONCRETE);
					set(x, y + 3, z, CONCRETE);
				}
			}
		}
		// A water tank and a vent for a silhouette.
		box(30, y + 2, 9, 33, y + 3, 12, b(Blocks.IRON_BLOCK));
		box(5, y + 2, 5, 6, y + 3, 7, b(Blocks.STONE_BRICKS));
	}

	// ---- The lobby.

	private void lobby() {
		// Front rooms 3-5 and the wall between them and the corridor are knocked through into the lobby, x 19-36.
		for (int y = 1; y <= 3; y++) {
			for (int x = 19; x <= 36; x++) {
				for (int z = 1; z <= 4; z++) {
					set(x, y, z, null);
				}
			}
		}
		for (int x = 19; x <= 36; x++) {
			for (int z = 1; z <= 7; z++) {
				set(x, 0, z, b(Blocks.SMOOTH_STONE));
			}
			set(x, 1, 6, b(Blocks.RED_CARPET));
		}
		doors.removeIf(d -> d.y() == 1 && d.z() == 4 && d.x() >= 19);
		chains.removeIf(p -> p.y() <= 2 && p.z() == 5 && p.x() >= 19);
		signs.removeIf(s -> s.y() == 3 && s.z() == 5 && s.x() >= 19);
		// The ceiling lamps over the lobby, a row of three across it.
		for (int x = 21; x <= 33; x += 4) {
			set(x, 4, 3, b(Blocks.REDSTONE_LAMP));
			lamps.add(new Lamp(x, 4, 3, -1, 0));
		}
		// The way in: a double glass door and a transom, the shop windows each side.
		BlockState a = b(Blocks.IRON_DOOR).with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HINGE, DoorHinge.LEFT)
				.with(DoorBlock.HALF, DoubleBlockHalf.LOWER).with(DoorBlock.OPEN, false);
		set(27, 1, 0, a);
		set(27, 2, 0, a.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		BlockState c = a.with(DoorBlock.HINGE, DoorHinge.RIGHT);
		set(28, 1, 0, c);
		set(28, 2, 0, c.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		doors.add(new Door(27, 1, 0, DoorKind.ENTRANCE));
		doors.add(new Door(28, 1, 0, DoorKind.ENTRANCE));
		chains.add(new P(27, 1, 1));
		chains.add(new P(27, 2, 1));
		chains.add(new P(28, 1, 1));
		chains.add(new P(28, 2, 1));
		// A button each side inside, so whoever is in can always get out (the doors are iron: nothing opens them from outside).
		set(26, 2, 1, b(Blocks.STONE_BUTTON).with(net.minecraft.block.ButtonBlock.FACE, net.minecraft.block.enums.BlockFace.WALL).with(net.minecraft.block.ButtonBlock.FACING, Direction.SOUTH));
		set(29, 2, 1, b(Blocks.STONE_BUTTON).with(net.minecraft.block.ButtonBlock.FACE, net.minecraft.block.enums.BlockFace.WALL).with(net.minecraft.block.ButtonBlock.FACING, Direction.SOUTH));
		for (int x = 27; x <= 28; x++) {
			set(x, 3, 0, GLASS_DARK);
			glass.add(new Glass(x, 3, 0, 0));
		}
		for (int x = 20; x <= 25; x++) {
			for (int y = 1; y <= 3; y++) {
				set(x, y, 0, GLASS_DARK);
				glass.add(new Glass(x, y, 0, 0));
			}
		}
		for (int x = 30; x <= 35; x++) {
			for (int y = 1; y <= 3; y++) {
				set(x, y, 0, GLASS_DARK);
				glass.add(new Glass(x, y, 0, 0));
			}
		}
		litGroups.add(0);
		lights.add(new Glass(23, 3, 3, 0));
		lights.add(new Glass(33, 3, 3, 0));
		lights.add(new Glass(28, 3, 3, 0));
		lights.add(new Glass(28, 3, 6, 0));
		lights.add(new Glass(34, 3, 6, 0));
		// The front desk against the way in, a bell on it, a sign behind.
		for (int x = 30; x <= 34; x++) {
			set(x, 1, 4, SKIRT);
			set(x, 2, 4, b(Blocks.DARK_OAK_SLAB).with(Properties.SLAB_TYPE, SlabType.BOTTOM));
		}
		set(32, 3, 4, b(Blocks.BELL).with(net.minecraft.block.BellBlock.ATTACHMENT, net.minecraft.block.enums.Attachment.FLOOR)
				.with(net.minecraft.block.BellBlock.FACING, Direction.NORTH));
		bell = new P(32, 3, 4);
		signs.add(new Sign(32, 3, 7, Direction.NORTH, new String[] {"", "FRONT DESK", "", ""}));
		// A couch and a fern, a clock's worth of nothing.
		for (int x = 21; x <= 23; x++) {
			set(x, 1, 2, b(Blocks.CRIMSON_STAIRS).with(StairsBlock.FACING, Direction.SOUTH));
		}
		set(25, 1, 1, b(Blocks.POTTED_FERN));
		set(36, 1, 1, b(Blocks.POTTED_FERN));
		// The lift that does not work: iron doors and a plate.
		BlockState lift = b(Blocks.IRON_DOOR).with(DoorBlock.FACING, Direction.WEST).with(DoorBlock.HINGE, DoorHinge.LEFT)
				.with(DoorBlock.HALF, DoubleBlockHalf.LOWER).with(DoorBlock.OPEN, false);
		set(36, 1, 6, lift);
		set(36, 2, 6, lift.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		doors.add(new Door(36, 1, 6, DoorKind.LIFT));
		signs.add(new Sign(36, 3, 6, Direction.WEST, new String[] {"", "OUT OF", "ORDER", ""}));
		// The painting over the couch (the same one that comes back upstairs).
		painting(6, 2, 8, 0, false);
		// The home sits under the lobby floor, in the dark.
		set(HOME_X, 0, HOME_Z, b(Blocks.GRAY_CONCRETE));
	}

	/** A 3 x 2 painting on the wall plane at {@code (x..x+2, y..y+1)} in the row {@code z}, a sun over green and a blue sky. */
	private void painting(int x, int y, int z, int unused, boolean small) {
		set(x, y + 1, z, b(Blocks.LIGHT_BLUE_WOOL));
		set(x + 1, y + 1, z, b(Blocks.YELLOW_WOOL));
		set(x + 2, y + 1, z, b(Blocks.LIGHT_BLUE_WOOL));
		set(x, y, z, b(Blocks.GREEN_WOOL));
		set(x + 1, y, z, b(Blocks.LIME_WOOL));
		set(x + 2, y, z, b(Blocks.GREEN_WOOL));
	}

	// ---- The stairs.

	private void stairs() {
		int x0 = SHAFT_X0;
		int x1 = SHAFT_X1;
		for (int i = 0; i < FLOORS; i++) {
			int y0 = floorY(i);
			// The shaft's own floor is a plain concrete, and its walls the same as the rest.
			for (int x = x0; x <= x1; x++) {
				for (int z = 1; z <= 3; z++) {
					set(x, y0 + 1, z, null);
				}
			}
			// The door in the corridor wall at the west end.
			door(x0, y0 + 1, 4, Direction.NORTH, DoorKind.STAIR, Blocks.BIRCH_DOOR);
			// The way up (even floors east in the lane at the door's side, odd floors west in the far lane).
			if (i < FLOORS - 1) {
				boolean east = i % 2 == 0;
				int lane = east ? 3 : 1;
				for (int k = 0; k < 4; k++) {
					int x = east ? x0 + 1 + k : x1 - 1 - k;
					int y = y0 + 1 + k;
					set(x, y, lane, b(Blocks.STONE_BRICK_STAIRS).with(StairsBlock.FACING, east ? Direction.EAST : Direction.WEST));
				}
				// The hole in the slab above, over the three upper steps.
				int ny = y0 + 4;
				for (int k = 1; k <= 3; k++) {
					int x = east ? x0 + 1 + k : x1 - 1 - k;
					if (k < 3) {
						set(x, ny, lane, null);
					}
				}
				// (the top step stands where the slab was)
				if (east) {
					set(x0 + 4, ny, lane, b(Blocks.STONE_BRICK_STAIRS).with(StairsBlock.FACING, Direction.EAST));
				} else {
					set(x1 - 4, ny, lane, b(Blocks.STONE_BRICK_STAIRS).with(StairsBlock.FACING, Direction.WEST));
				}
			}
			// The floor sign beside the door, the exit over it (the loop's floor reads 8, as its copies do).
			signs.add(new Sign(x0 + 1, y0 + 2, 5, Direction.SOUTH, new String[] {"", (i + 1) + "F", "", ""}));
			set(x0, y0 + 3, 4, b(Blocks.LIME_CONCRETE));
			// A lamp's worth of light in the shaft by night.
			lights.add(new Glass(15, y0 + 3, 2, 1000 + i));
		}
		// The shaft is dark and bare: concrete on its inside walls.
		for (int i = 0; i < FLOORS; i++) {
			int y0 = floorY(i);
			for (int y = y0 + 1; y <= y0 + 3; y++) {
				for (int z = 1; z <= 3; z++) {
					set(SHAFT_X0 - 1, y, z, b(Blocks.STONE_BRICKS));
					set(SHAFT_X1 + 1, y, z, b(Blocks.STONE_BRICKS));
				}
			}
		}
	}

	// ---- What is wrong, floor by floor.

	private void wrongThings() {
		// Floor 2: a number repeats.
		replaceSign(1, 1, 1, "202");
		replaceSign(1, 1, 2, "202");
		// Floor 3: the painting comes back, on every wall space of the corridor.
		int y3 = floorY(2);
		for (int k = 0; k < 5; k++) {
			painting(6 + 6 * k, y3 + 2, 8, 0, false);
		}
		for (int k = 0; k < 6; k++) {
			painting(3 + 6 * k, y3 + 2, 4, 0, false);
		}
		// Floor 2 has the painting once, as the lobby does.
		painting(6, floorY(1) + 2, 8, 0, false);
		// Floor 4: a stain spreads on the carpet from the stair end to a door.
		int y4 = floorY(3);
		for (int x = 16; x <= 29; x++) {
			int reach = x < 22 ? 1 : (x < 26 ? 2 : 3);
			for (int z = 5; z <= 7; z++) {
				boolean in = z == 6 || reach >= 2 && z == 5 || reach >= 3 && z == 7;
				if (in && (x * 7 + z * 3) % 5 != 0) {
					set(x, y4 + 1, z, b(x % 3 == 0 ? Blocks.BLACK_CARPET : Blocks.BROWN_CARPET));
				}
			}
		}
		set(28, y4 + 1, 7, b(Blocks.BLACK_CARPET));
		set(29, y4 + 1, 7, b(Blocks.BLACK_CARPET));
		set(29, y4 + 1, 6, b(Blocks.BLACK_CARPET));
		// Floor 5: an eye in the wallpaper, again and again.
		int y5 = floorY(4);
		for (int k = 0; k < 6; k++) {
			for (int z : new int[] {4, 8}) {
				int x = z == 4 ? 3 + 6 * k : 6 + 6 * k;
				if (z == 8 && k == 5) {
					continue;
				}
				set(x, y5 + 3, z, b(Blocks.WHITE_CONCRETE));
				set(x + 1, y5 + 3, z, b(Blocks.RED_CONCRETE));
				set(x + 2, y5 + 3, z, b(Blocks.WHITE_CONCRETE));
				set(x, y5 + 2, z, b(Blocks.WHITE_CONCRETE));
				set(x + 1, y5 + 2, z, b(Blocks.BLACK_CONCRETE));
				set(x + 2, y5 + 2, z, b(Blocks.WHITE_CONCRETE));
			}
		}
		// Floor 6: a door that breathes.
		breathDoor = new P(22, floorY(5) + 1, 8);
		// Floor 7: a phone in an empty room, the front room 3 (nothing in it but a desk and the phone).
		int y7 = floorY(6);
		phone = new P(21, y7 + 2, 2);
		for (int x = 19; x <= 23; x++) {
			for (int z = 1; z <= 3; z++) {
				set(x, y7 + 1, z, null);
				set(x, y7 + 2, z, null);
			}
		}
		set(21, y7 + 1, 2, b(Blocks.DARK_OAK_SLAB).with(Properties.SLAB_TYPE, SlabType.BOTTOM));
		set(21, y7 + 2, 2, b(Blocks.REPEATER));
	}

	private void replaceSign(int floorIndex, int side, int j, String text) {
		int y0 = floorY(floorIndex);
		int doorX = side == 0 ? 6 * j + 1 : 6 * j + 4;
		int z = side == 0 ? 5 : 7;
		for (int k = 0; k < signs.size(); k++) {
			Sign s = signs.get(k);
			if (s.x() == doorX && s.y() == y0 + 3 && s.z() == z) {
				signs.set(k, new Sign(s.x(), s.y(), s.z(), s.facing(), new String[] {"", text, "", ""}));
			}
		}
	}

	// ---- The corridor's flesh and bleeding, laid out for later (the builder puts them down and takes them away).

	private void bleedAndFlesh() {
		int y8 = floorY(FLOORS - 1);
		// Level 1 at the corners where the wall meets the floor and the ceiling, every period; 2 spreads along the walls; 3 reaches over the ceiling.
		List<P> one = new ArrayList<>();
		List<P> two = new ArrayList<>();
		List<P> three = new ArrayList<>();
		for (int x = CORR_X0; x <= CORR_X1; x++) {
			int m = x % PERIOD;
			if (m == 3 || m == 9) {
				one.add(new P(x, y8 + 1, 5));
				one.add(new P(x, y8 + 3, 7));
			}
			if (m == 2 || m == 3 || m == 4 || m == 8 || m == 9 || m == 10) {
				two.add(new P(x, y8 + 1, 7));
				two.add(new P(x, y8 + 3, 5));
			}
			if (m == 3 || m == 9 || m == 6 || m == 0) {
				three.add(new P(x, y8 + 2, 5));
				three.add(new P(x, y8 + 2, 7));
				three.add(new P(x, y8 + 3, 6));
			}
		}
		flesh.add(one);
		flesh.add(two);
		flesh.add(three);
	}
}
