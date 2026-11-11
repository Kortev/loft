package io.github.kortev.chainsawman.safety;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * The village notice board: put up by a village's bell the first time that bell comes within {@link #NOTICE} blocks of a player, if its
 * village has none (old worlds too). A small spruce board, {@link #WIDTH} wide on two fence posts under a slab roof, the poster on its left
 * half and the green phone on its right. On flat open ground within {@link #NEAR} blocks of the bell, facing it; replacing only air and
 * plants; never under a roof, and on a path only if nothing else will do. One per village, remembered in {@link SafetyLedger}, and never
 * put up again (breaking it is the player's business).
 *
 * <p>A bell counts only inside a vanilla village (the structure): never one in a player's own build, and never one in or by one of the
 * mod's hotels. A bell judged and turned down is not judged again for {@link #RETRY} ticks.
 */
public final class NoticeBoards {
	/** How near a player a bell must come. */
	public static final int NOTICE = 64;
	/** How far from the bell the board may stand. */
	public static final int NEAR = 6;
	/** Bells within this of a bell done are the same village. */
	public static final int SAME_VILLAGE = 48;
	/** How long a bell turned down is left alone before it is looked at again (ticks). */
	public static final long RETRY = 1200L;
	/** Bells turned down: dimension and position, and when they may be looked at again (not saved: a restart looks again). */
	private static final java.util.Map<String, Long> REJECTED = new java.util.HashMap<>();
	/** Test-only: bells the tests have put down that count as a village's (game tests have no village structures). */
	public static final java.util.Set<BlockPos> TEST_VILLAGE_BELLS = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** The board's width (blocks): poster centred on the second, the phone on the fourth. */
	public static final int WIDTH = 4;
	public static final int POSTER_AT = 1;
	public static final int PHONE_AT = 3;
	/** Each player is looked round once in this many ticks. */
	public static final int EVERY = 40;

	private NoticeBoards() {
	}

	static void tick(ServerWorld world) {
		long t = world.getTime();
		if (t % RETRY == 0 && !REJECTED.isEmpty()) {
			REJECTED.values().removeIf(until -> until <= t);
		}
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (!p.isSpectator() && (t + p.getId()) % EVERY == 0) {
				lookRound(world, p.getBlockPos());
			}
		}
	}

	/** Every bell near {@code at} whose village has no board yet gets one (if it is a village's and its ground is loaded). */
	public static void lookRound(ServerWorld world, BlockPos at) {
		List<BlockPos> bells = bells(world, at);
		if (bells.isEmpty()) {
			return;
		}
		SafetyLedger ledger = SafetyLedger.of(world.getServer());
		String dim = world.getRegistryKey().getValue().toString();
		long now = world.getTime();
		for (BlockPos bell : bells) {
			String key = dim + "|" + bell.asLong();
			Long until = REJECTED.get(key);
			if (until != null && now < until || ledger.doneNear(dim, bell, SAME_VILLAGE) || !loaded(world, bell)) {
				continue;
			}
			if (inHotel(world, bell) || !village(world, bell)) {
				REJECTED.put(key, now + RETRY);
				continue;
			}
			REJECTED.remove(key);
			raise(world, bell);
			ledger.done(dim, bell);
		}
	}

	/** Whether {@code bell} was turned down and is being left alone for now. */
	public static boolean resting(ServerWorld world, BlockPos bell) {
		Long until = REJECTED.get(world.getRegistryKey().getValue() + "|" + bell.asLong());
		return until != null && world.getTime() < until;
	}

	/** Whether {@code bell} is in or by one of the mod's hotels (a loaded home's whole box, a board's reach round it; or the hotel structure). */
	public static boolean inHotel(ServerWorld world, BlockPos bell) {
		for (io.github.kortev.chainsawman.hotel.HomeBlockEntity h : io.github.kortev.chainsawman.hotel.HomeBlockEntity.LOADED) {
			if (h.getWorld() == world && io.github.kortev.chainsawman.hotel.HotelBuilder.box(h.origin, h.rot).expand(NEAR + WIDTH).contains(bell)) {
				return true;
			}
		}
		var hotel = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.STRUCTURE)
				.get(io.github.kortev.chainsawman.ChainsawMan.id("eternity_hotel"));
		return hotel != null && world.getStructureAccessor().getStructureAt(bell, hotel).hasChildren();
	}

	/**
	 * The bells within {@link #NOTICE} blocks of {@code at}, in loaded chunks. Read from the blocks, not the bells' points of interest (a village
	 * just generated may not have those yet): each chunk section's palette says whether a bell is in it at all, and only those are looked through.
	 */
	public static List<BlockPos> bells(ServerWorld world, BlockPos at) {
		List<BlockPos> out = new ArrayList<>();
		int r = (NOTICE >> 4) + 1;
		int acx = at.getX() >> 4;
		int acz = at.getZ() >> 4;
		double r2 = (double) NOTICE * NOTICE;
		for (int cx = acx - r; cx <= acx + r; cx++) {
			for (int cz = acz - r; cz <= acz + r; cz++) {
				WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
				if (chunk == null) {
					continue;
				}
				ChunkSection[] sections = chunk.getSectionArray();
				for (int i = 0; i < sections.length; i++) {
					ChunkSection section = sections[i];
					if (section.isEmpty() || !section.hasAny(s -> s.isOf(Blocks.BELL))) {
						continue;
					}
					int y0 = chunk.sectionIndexToCoord(i) << 4;
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (section.getBlockState(x, y, z).isOf(Blocks.BELL)) {
									BlockPos b = new BlockPos((cx << 4) + x, y0 + y, (cz << 4) + z);
									if (b.getSquaredDistance(at) <= r2) {
										out.add(b);
									}
								}
							}
						}
					}
				}
			}
		}
		return out;
	}

	private static boolean loaded(ServerWorld world, BlockPos bell) {
		int r = NEAR + WIDTH + 2;
		for (int dx = -r; dx <= r; dx += r) {
			for (int dz = -r; dz <= r; dz += r) {
				if (!world.isChunkLoaded(bell.getX() + dx >> 4, bell.getZ() + dz >> 4)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Whether {@code bell} is a village's: inside a vanilla village structure (or put down by a test as one). */
	public static boolean village(ServerWorld world, BlockPos bell) {
		return TEST_VILLAGE_BELLS.contains(bell) || world.getStructureAccessor().getStructureContaining(bell, StructureTags.VILLAGE).hasChildren();
	}

	/** A spot found for a board: its back row's left end (seen from the front) at ground height, and which way it faces. */
	public record Spot(BlockPos left, Direction facing) {
		public BlockPos cell(int i) {
			return left.offset(right(facing), i);
		}

		public BlockPos poster() {
			return cell(POSTER_AT).up().offset(facing);
		}

		public BlockPos phone() {
			return cell(PHONE_AT).up().offset(facing);
		}
	}

	/** Seen from in front of a board facing {@code facing}, the way to the right. */
	static Direction right(Direction facing) {
		return facing.rotateYCounterclockwise();
	}

	/** Puts up a board by {@code bell} if a spot can be found; the spot, or null. */
	public static Spot raise(ServerWorld world, BlockPos bell) {
		Spot spot = find(world, bell);
		if (spot != null) {
			build(world, spot);
		}
		return spot;
	}

	/** The best spot for a board by {@code bell}, or null. */
	public static Spot find(ServerWorld world, BlockPos bell) {
		Spot best = null;
		double bestScore = Double.MAX_VALUE;
		for (int dx = -NEAR; dx <= NEAR; dx++) {
			for (int dz = -NEAR; dz <= NEAR; dz++) {
				for (Direction f : Direction.Type.HORIZONTAL) {
					BlockPos column = bell.add(dx, 0, dz);
					int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, column.getX(), column.getZ());
					Spot s = new Spot(new BlockPos(column.getX(), y, column.getZ()), f);
					double score = score(world, bell, s);
					if (score < bestScore) {
						bestScore = score;
						best = s;
					}
				}
			}
		}
		return best;
	}

	/** How good a spot is (lower is better), or MAX_VALUE if it will not do. */
	static double score(ServerWorld world, BlockPos bell, Spot s) {
		int y = s.left().getY();
		if (y < bell.getY() - 4 || y > bell.getY() + 2) {
			return Double.MAX_VALUE;
		}
		int paths = 0;
		double cx = 0;
		double cz = 0;
		for (int i = 0; i < WIDTH; i++) {
			BlockPos back = s.cell(i);
			cx += back.getX() + 0.5;
			cz += back.getZ() + 0.5;
			// The board's own column, the column in front (the poster and phone), and the one in front of that (to stand in).
			for (int row = 0; row < 3; row++) {
				BlockPos c = back.offset(s.facing(), row);
				if (Math.abs(c.getX() - bell.getX()) <= 1 && Math.abs(c.getZ() - bell.getZ()) <= 1) {
					return Double.MAX_VALUE;
				}
				// Open to the sky (nothing above but what is replaced), flat, on solid ground.
				if (world.getTopY(Heightmap.Type.MOTION_BLOCKING, c.getX(), c.getZ()) != y) {
					return Double.MAX_VALUE;
				}
				BlockState ground = world.getBlockState(c.down());
				if (ground.isOf(Blocks.DIRT_PATH)) {
					paths++;
				} else if (!ground.isSideSolidFullSquare(world, c.down(), Direction.UP) || !ground.getFluidState().isEmpty()) {
					return Double.MAX_VALUE;
				}
				int height = row == 0 ? 4 : row == 1 ? 3 : 2;
				for (int h = 0; h < height; h++) {
					if (!free(world.getBlockState(c.up(h)))) {
						return Double.MAX_VALUE;
					}
				}
			}
		}
		cx /= WIDTH;
		cz /= WIDTH;
		double ox = bell.getX() + 0.5 - cx;
		double oz = bell.getZ() + 0.5 - cz;
		double dist = Math.sqrt(ox * ox + oz * oz);
		// Facing the bell (its front toward it), about four blocks off.
		double facing = (ox * s.facing().getOffsetX() + oz * s.facing().getOffsetZ()) / Math.max(dist, 0.01);
		double score = Math.abs(dist - 4.0) + (1.0 - facing) * 3.0;
		return score + paths * 100.0;
	}

	/** Whether a board may go where {@code state} is: air, or a plant (no water). */
	static boolean free(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		return state.isReplaceable() || state.isIn(BlockTags.FLOWERS) && !state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.SAPLINGS);
	}

	/** Builds the board at {@code s}. */
	public static void build(ServerWorld world, Spot s) {
		BlockState fence = Blocks.SPRUCE_FENCE.getDefaultState();
		BlockState planks = Blocks.SPRUCE_PLANKS.getDefaultState();
		BlockState slab = Blocks.SPRUCE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
		// Clear plants where the poster's paper and the phone hang, and in front of the board.
		for (int i = 0; i < WIDTH; i++) {
			BlockPos front = s.cell(i).offset(s.facing());
			for (int h = 0; h < 3; h++) {
				if (!world.getBlockState(front.up(h)).isAir()) {
					world.setBlockState(front.up(h), Blocks.AIR.getDefaultState());
				}
			}
		}
		for (int i = 0; i < WIDTH; i++) {
			BlockPos c = s.cell(i);
			world.setBlockState(c, i == 0 || i == WIDTH - 1 ? fence : Blocks.AIR.getDefaultState());
			world.setBlockState(c.up(1), planks);
			world.setBlockState(c.up(2), planks);
			world.setBlockState(c.up(3), slab);
		}
		world.setBlockState(s.poster(), Safety.POSTER.getDefaultState().with(BoardBlock.FACING, s.facing()));
		world.setBlockState(s.phone(), Safety.PHONE.getDefaultState().with(BoardBlock.FACING, s.facing()));
	}
}
