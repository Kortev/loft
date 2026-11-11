package io.github.kortev.chainsawman.hotel;

import io.github.kortev.chainsawman.ChainsawMan;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.StructureWorldAccess;

/**
 * Puts {@link HotelPlan} down in the world at one of four turns (0 to 3 quarter turns clockwise), whole or only the part inside a box (a chunk,
 * while the world is made), and changes it between day and night, with the flesh of the loop floor and the walls bleeding. The local
 * coordinates of the plan turn about its own origin, which the home remembers.
 */
public final class HotelBuilder {
	/** The plaza in front: z from -5 to -1 (local), and how deep the foundation goes. */
	public static final int PLAZA = 5;
	public static final int FOUNDATION = 6;

	private HotelBuilder() {
	}

	/** The world position of the local cell {@code (x, y, z)} of a hotel at {@code origin} turned {@code rot} quarter turns. */
	public static BlockPos world(BlockPos origin, int rot, int x, int y, int z) {
		return switch (rot & 3) {
			case 0 -> origin.add(x, y, z);
			case 1 -> origin.add(-z, y, x);
			case 2 -> origin.add(-x, y, -z);
			default -> origin.add(z, y, -x);
		};
	}

	/** The world point of a local point (the same turn, for entities and players). */
	public static Vec3d world(BlockPos origin, int rot, double x, double y, double z) {
		double dx;
		double dz;
		switch (rot & 3) {
			case 0 -> {
				dx = x;
				dz = z;
			}
			case 1 -> {
				dx = -z;
				dz = x;
			}
			case 2 -> {
				dx = -x;
				dz = -z;
			}
			default -> {
				dx = z;
				dz = -x;
			}
		}
		return new Vec3d(origin.getX() + dx, origin.getY() + y, origin.getZ() + dz);
	}

	/** The local point of a world point: the inverse of {@link #world(BlockPos, int, double, double, double)}. */
	public static Vec3d local(BlockPos origin, int rot, Vec3d p) {
		double dx = p.x - origin.getX();
		double dz = p.z - origin.getZ();
		double y = p.y - origin.getY();
		return switch (rot & 3) {
			case 0 -> new Vec3d(dx, y, dz);
			case 1 -> new Vec3d(dz, y, -dx);
			case 2 -> new Vec3d(-dx, y, -dz);
			default -> new Vec3d(-dz, y, dx);
		};
	}

	public static BlockRotation turn(int rot) {
		return BlockRotation.values()[rot & 3];
	}

	/** The yaw a player looking along the local +x (down the corridor, from the stairs end) has. */
	public static float alongYaw(int rot) {
		return -90.0F + 90.0F * (rot & 3);
	}

	/** The box of everything a hotel puts down (the plaza and foundation too). */
	public static BlockBox box(BlockPos origin, int rot) {
		BlockPos a = world(origin, rot, -1, -FOUNDATION, -PLAZA);
		BlockPos b = world(origin, rot, HotelPlan.SX, HotelPlan.SY, HotelPlan.SZ);
		return BlockBox.create(a, b);
	}

	/** Puts the hotel down (all of it, or only inside {@code clip}) in its day state: dark, shut and chained, the home buried under the lobby. */
	public static void place(StructureWorldAccess world, BlockPos origin, int rot, BlockBox clip) {
		HotelPlan plan = HotelPlan.get();
		BlockRotation turn = turn(rot);
		for (int lz = -PLAZA; lz <= HotelPlan.SZ; lz++) {
			for (int lx = -1; lx <= HotelPlan.SX; lx++) {
				boolean inside = lx >= 0 && lx < HotelPlan.SX && lz >= 0 && lz < HotelPlan.SZ;
				boolean skip = !inside && lz >= 0;
				if (skip) {
					continue;
				}
				// The foundation under it, and the plaza's.
				for (int ly = -FOUNDATION; ly < 0; ly++) {
					set(world, world(origin, rot, lx, ly, lz), inside ? Blocks.STONE_BRICKS.getDefaultState() : Blocks.STONE.getDefaultState(), clip);
				}
				for (int ly = 0; ly < HotelPlan.SY; ly++) {
					BlockState state;
					if (inside) {
						state = plan.at(lx, ly, lz).rotate(turn);
					} else if (ly == 0) {
						state = Blocks.SMOOTH_STONE.getDefaultState();
					} else if (ly <= 7) {
						state = Blocks.AIR.getDefaultState();
					} else {
						continue;
					}
					set(world, world(origin, rot, lx, ly, lz), state, clip);
				}
			}
		}
		// The signs' words.
		for (HotelPlan.Sign s : plan.signs) {
			BlockPos at = world(origin, rot, s.x(), s.y(), s.z());
			if (clip != null && !clip.contains(at)) {
				continue;
			}
			Direction facing = turn.rotate(s.facing());
			BlockState state = Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, facing);
			world.setBlockState(at, state, Block.NOTIFY_LISTENERS);
			BlockEntity be = world.getBlockEntity(at);
			if (be instanceof SignBlockEntity sign) {
				SignText text = new SignText();
				for (int i = 0; i < s.lines().length && i < 4; i++) {
					text = text.withMessage(i, Text.literal(s.lines()[i]));
				}
				sign.setText(text, true);
				sign.setWaxed(true);
			}
		}
		// Chains in front of the doors.
		for (HotelPlan.P c : plan.chains) {
			set(world, world(origin, rot, c.x(), c.y(), c.z()), Blocks.CHAIN.getDefaultState(), clip);
		}
		// The squad's notes, on lecterns in the corridors.
		Squad.placeNotes(world, origin, rot, clip);
		// The home, buried.
		BlockPos home = world(origin, rot, HotelPlan.HOME_X, HotelPlan.HOME_Y, HotelPlan.HOME_Z);
		if (clip == null || clip.contains(home)) {
			world.setBlockState(home, ChainsawMan.HOME_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
			if (world.getBlockEntity(home) instanceof HomeBlockEntity be) {
				be.set(origin, rot);
			}
		}
	}

	private static void set(WorldAccess world, BlockPos pos, BlockState state, BlockBox clip) {
		if (clip == null || clip.contains(pos)) {
			world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
		}
	}

	// ---- Day and night, for a hotel already standing.

	/** The night state: the doors stand open, the lobby and some windows lit, the chains gone; or the day state back. */
	public static void setNight(net.minecraft.server.world.ServerWorld world, BlockPos origin, int rot, boolean night) {
		HotelPlan plan = HotelPlan.get();
		BlockRotation turn = turn(rot);
		int topFloor = HotelPlan.floorY(HotelPlan.FLOORS - 1);
		for (HotelPlan.Door d : plan.doors) {
			if (d.kind() == HotelPlan.DoorKind.LIFT) {
				continue;
			}
			boolean open = night && (d.kind() == HotelPlan.DoorKind.ENTRANCE || d.y() < topFloor);
			for (int h = 0; h < 2; h++) {
				BlockPos at = world(origin, rot, d.x(), d.y() + h, d.z());
				BlockState s = world.getBlockState(at);
				if (s.getBlock() instanceof DoorBlock && s.get(DoorBlock.OPEN) != open) {
					world.setBlockState(at, s.with(DoorBlock.OPEN, open), Block.NOTIFY_LISTENERS);
				}
			}
		}
		for (HotelPlan.P c : plan.chains) {
			BlockPos at = world(origin, rot, c.x(), c.y(), c.z());
			BlockState want = night ? Blocks.AIR.getDefaultState() : Blocks.CHAIN.getDefaultState();
			if (world.getBlockState(at).getBlock() != want.getBlock()) {
				world.setBlockState(at, want, Block.NOTIFY_LISTENERS);
			}
		}
		for (HotelPlan.Lamp l : plan.lamps) {
			lamp(world, world(origin, rot, l.x(), l.y(), l.z()), night);
		}
		for (HotelPlan.Glass g : plan.glass) {
			boolean lit = night && plan.litGroups.contains(g.group());
			BlockPos at = world(origin, rot, g.x(), g.y(), g.z());
			BlockState want = lit ? Blocks.GLASS.getDefaultState() : Blocks.BLACK_STAINED_GLASS.getDefaultState();
			if (world.getBlockState(at) != want) {
				world.setBlockState(at, want, Block.NOTIFY_LISTENERS);
			}
		}
		for (HotelPlan.Glass l : plan.lights) {
			boolean lit = night && (plan.litGroups.contains(l.group()) || l.group() >= 1000);
			BlockPos at = world(origin, rot, l.x(), l.y(), l.z());
			BlockState want = lit ? Blocks.LIGHT.getDefaultState() : Blocks.AIR.getDefaultState();
			if (world.getBlockState(at) != want) {
				world.setBlockState(at, want, Block.NOTIFY_LISTENERS);
			}
		}
	}

	/** The stairwell's version: 1 is the walkable one (0, before it, could not be climbed without breaking the slab over the second step). */
	public static final int STAIRS = 1;

	/** The stairwell's space (local, both ends included): the well, its slabs and its walls, every floor. */
	public static final int WELL_X0 = HotelPlan.SHAFT_X0;
	public static final int WELL_X1 = HotelPlan.SHAFT_X1;

	/**
	 * Puts the plan's stairwell back over an older one, in a hotel already standing (a world from before {@link #STAIRS}): every block of the
	 * well (x 13-17, z 1-3) from the ground to the roof, in the night state if {@code night}. Nothing else of the hotel is touched.
	 */
	public static void rebuildStairs(net.minecraft.server.world.ServerWorld world, BlockPos origin, int rot, boolean night) {
		HotelPlan plan = HotelPlan.get();
		BlockRotation turn = turn(rot);
		for (int y = 0; y < HotelPlan.SY; y++) {
			for (int z = 1; z <= 3; z++) {
				for (int x = WELL_X0; x <= WELL_X1; x++) {
					BlockPos at = world(origin, rot, x, y, z);
					BlockState want = plan.at(x, y, z).rotate(turn);
					if (world.getBlockState(at) != want) {
						world.setBlockState(at, want, Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
		if (night) {
			for (HotelPlan.Glass l : plan.lights) {
				if (l.group() >= 1000) {
					world.setBlockState(world(origin, rot, l.x(), l.y(), l.z()), Blocks.LIGHT.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	/** The entrance doors shut (and left shut: a sealed hotel). */
	public static void shutEntrance(net.minecraft.server.world.ServerWorld world, BlockPos origin, int rot) {
		for (HotelPlan.Door d : HotelPlan.get().doors) {
			if (d.kind() != HotelPlan.DoorKind.ENTRANCE) {
				continue;
			}
			for (int h = 0; h < 2; h++) {
				BlockPos at = world(origin, rot, d.x(), d.y() + h, d.z());
				BlockState s = world.getBlockState(at);
				if (s.getBlock() instanceof DoorBlock && s.get(DoorBlock.OPEN)) {
					world.setBlockState(at, s.with(DoorBlock.OPEN, false), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	public static void lamp(net.minecraft.server.world.ServerWorld world, BlockPos at, boolean lit) {
		BlockState s = world.getBlockState(at);
		if (s.getBlock() instanceof RedstoneLampBlock && s.get(RedstoneLampBlock.LIT) != lit) {
			world.setBlockState(at, s.with(RedstoneLampBlock.LIT, lit), Block.NOTIFY_LISTENERS);
		}
	}

	/**
	 * The walls bleeding at the loop floor's east end: {@code amount} 0 to 1 turns more of the wallpaper there into flesh and red, the nearer
	 * the end the sooner; 0 puts the walls back.
	 */
	public static void bleed(net.minecraft.server.world.ServerWorld world, BlockPos origin, int rot, float amount) {
		bleed(world, origin, rot, amount, 24, HotelPlan.CORR_X1);
	}

	/** The same, over the walls from local x {@code x0} to {@code x1} (the nearer {@code x1}, the sooner), and 0 puts back every wall of the loop floor. */
	public static void bleed(net.minecraft.server.world.ServerWorld world, BlockPos origin, int rot, float amount, int x0, int x1) {
		bleed(world, origin, rot, amount, x0, x1, false);
	}

	/** The same; with {@code arenaOpen} the walls of the arena the devil tore open ({@link Arena}) are left alone (they are gone, and put back with it). */
	public static void bleed(net.minecraft.server.world.ServerWorld world, BlockPos origin, int rot, float amount, int x0, int x1, boolean arenaOpen) {
		HotelPlan plan = HotelPlan.get();
		if (amount <= 0.0F) {
			x0 = 1;
			x1 = HotelPlan.CORR_X1;
		}
		BlockRotation turn = turn(rot);
		int y8 = HotelPlan.floorY(HotelPlan.FLOORS - 1);
		for (int x = x0; x <= x1; x++) {
			if (arenaOpen && x >= Arena.X0) {
				continue;
			}
			for (int z : new int[] {4, 8}) {
				for (int y = y8 + 1; y <= y8 + 3; y++) {
					if (plan.at(x, y, z).getBlock() instanceof DoorBlock || plan.at(x, y, z).isAir()) {
						continue;
					}
					double near = (x - x0) / (double) Math.max(1, x1 - x0);
					double h = ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 255;
					boolean red = amount > 0.0F && h / 255.0 < amount * (0.12 + 0.5 * near);
					BlockPos at = world(origin, rot, x, y, z);
					BlockState want = red ? ((h % 3 == 0) ? Blocks.RED_TERRACOTTA.getDefaultState()
							: (h % 3 == 1 ? Blocks.NETHER_WART_BLOCK.getDefaultState() : Blocks.PINK_TERRACOTTA.getDefaultState())) : plan.at(x, y, z).rotate(turn);
					if (world.getBlockState(at) != want) {
						world.setBlockState(at, want, Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
	}
}
