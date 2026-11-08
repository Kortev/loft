package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.hotel.Hotel;
import io.github.kortev.chainsawman.hotel.HomeBlockEntity;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import io.github.kortev.chainsawman.hotel.HotelPlan;
import io.github.kortev.chainsawman.hotel.HotelRules;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;

/**
 * The Eternity Devil's hotel: it is put down whole at every turn with its home in it, a walk from the street through the lobby and up the
 * stairs reaches the loop's corridor, it is in the world's generation (structure, set, biomes), and (further tests below) the rest of what
 * it does.
 */
public class HotelGameTests implements FabricGameTest {
	/** Where the test's hotel is put: the plan's floor on the test ground. */
	static BlockPos origin(TestContext context) {
		return BlockPos.ofFloored(context.getAbsolute(new Vec3d(0.5, 40.0, 0.5))).down().add(2, 0, 2);
	}

	static void forced(TestContext context, BlockPos origin, boolean on) {
		for (int cx = (origin.getX() - 60) >> 4; cx <= (origin.getX() + 60) >> 4; cx++) {
			for (int cz = (origin.getZ() - 60) >> 4; cz <= (origin.getZ() + 60) >> 4; cz++) {
				context.getWorld().setChunkForced(cx, cz, on);
			}
		}
	}

	/** Cleared air and stone round where a hotel goes, so what is checked is what it put there. */
	static void ground(TestContext context, BlockPos origin) {
		ServerWorld world = context.getWorld();
		for (int x = -50; x <= 50; x++) {
			for (int z = -50; z <= 50; z++) {
				for (int y = -8; y <= 40; y++) {
					world.setBlockState(origin.add(x, y, z), y < 0 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
				}
			}
		}
	}

	/** The hotel is whole at every turn: every block of the plan is in the world (but where the builder puts a sign, a chain or the home), and its home knows where it stands. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_place", tickLimit = 40)
	public void hotelIsPlacedWholeAtEveryTurn(TestContext context) {
		BlockPos origin = origin(context);
		forced(context, origin, true);
		ServerWorld world = context.getWorld();
		HotelPlan plan = HotelPlan.get();
		try {
			for (int rot = 0; rot < 4; rot++) {
				ground(context, origin);
				HomeBlockEntity home = Hotel.place(world, origin, rot);
				context.assertTrue(home != null, "the home was not made at turn " + rot);
				context.assertTrue(home.origin.equals(origin) && home.rot == rot, "the home does not know where the hotel stands at turn " + rot);
				Set<BlockPos> special = new HashSet<>();
				for (HotelPlan.Sign s : plan.signs) {
					special.add(HotelBuilder.world(origin, rot, s.x(), s.y(), s.z()));
				}
				for (HotelPlan.P c : plan.chains) {
					special.add(HotelBuilder.world(origin, rot, c.x(), c.y(), c.z()));
				}
				special.add(HotelBuilder.world(origin, rot, HotelPlan.HOME_X, HotelPlan.HOME_Y, HotelPlan.HOME_Z));
				int bad = 0;
				String first = null;
				for (int y = 0; y < HotelPlan.SY; y++) {
					for (int z = 0; z < HotelPlan.SZ; z++) {
						for (int x = 0; x < HotelPlan.SX; x++) {
							BlockPos at = HotelBuilder.world(origin, rot, x, y, z);
							if (special.contains(at)) {
								continue;
							}
							BlockState want = plan.at(x, y, z).rotate(HotelBuilder.turn(rot));
							if (world.getBlockState(at) != want) {
								bad++;
								if (first == null) {
									first = "(" + x + "," + y + "," + z + ") wanted " + want + " got " + world.getBlockState(at);
								}
							}
						}
					}
				}
				context.assertTrue(bad == 0, bad + " blocks of the plan are wrong at turn " + rot + ", the first " + first);
				// The signs say what they should.
				int signs = 0;
				for (HotelPlan.Sign s : plan.signs) {
					if (world.getBlockEntity(HotelBuilder.world(origin, rot, s.x(), s.y(), s.z())) instanceof net.minecraft.block.entity.SignBlockEntity sign
							&& sign.getFrontText().getMessage(1, false).getString().equals(s.lines()[1])) {
						signs++;
					}
				}
				context.assertTrue(signs == plan.signs.size(), "only " + signs + " of " + plan.signs.size() + " signs have their words at turn " + rot);
			}
		} finally {
			forced(context, origin, false);
		}
		context.complete();
	}

	/**
	 * You can walk from the street in front of the entrance through the lobby, up the stairs from floor 1 to floor 8, and out into the loop's
	 * corridor (doors count as open; a step up of one block and a drop of up to two are allowed).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_walk", tickLimit = 200)
	public void theStairsReachTheLoopFloor(TestContext context) {
		HotelPlan plan = HotelPlan.get();
		int[] start = {27, 1, -2};
		Set<Long> seen = new HashSet<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(key(start));
		boolean corridor8 = false;
		boolean floor8window = false;
		int top = 0;
		boolean reachedLobby = false;
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			top = Math.max(top, c[1]);
			if (c[1] == 29 && c[2] >= 5 && c[2] <= 7 && c[0] >= HotelPlan.WINDOW_LO && c[0] < HotelPlan.WINDOW_HI) {
				floor8window = true;
			}
			if (c[1] == 29 && c[2] >= 5 && c[2] <= 7) {
				corridor8 = true;
			}
			if (c[1] == 1 && c[2] == 6 && c[0] == 36) {
				reachedLobby = true;
			}
			int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] d : dirs) {
				for (int dy = 1; dy >= -2; dy--) {
					int[] n = {c[0] + d[0], c[1] + dy, c[2] + d[1]};
					if (dy == 1 && !headroom(plan, c[0], c[1] + 2, c[2])) {
						continue;
					}
					if (standable(plan, n[0], n[1], n[2]) && !seen.contains(key(n))) {
						// A drop needs the cells down the side free.
						boolean clear = true;
						for (int yy = 0; yy > dy; yy--) {
							clear &= free(plan, n[0], c[1] + yy, n[2]) && free(plan, n[0], c[1] + yy + 1, n[2]);
						}
						if (!clear) {
							continue;
						}
						seen.add(key(n));
						queue.add(n);
					}
				}
			}
		}
		context.assertTrue(reachedLobby, "the walk never got into the lobby");
		context.assertTrue(top >= 29, "the stairs only reach feet height " + top + " (floor 8 is 29)");
		context.assertTrue(corridor8, "floor 8's corridor is not reached");
		context.assertTrue(floor8window, "the loop's window is not reached");
		context.complete();
	}

	private static long key(int[] c) {
		return ((long) (c[0] + 64) << 40) | ((long) (c[1] + 64) << 20) | (c[2] + 64);
	}

	private static final java.util.Map<Long, Boolean> FREE = new java.util.HashMap<>();
	private static final java.util.Map<Long, Boolean> FULL = new java.util.HashMap<>();

	private static boolean free(HotelPlan plan, int x, int y, int z) {
		return FREE.computeIfAbsent(key(new int[] {x, y, z}), k -> freeUncached(plan, x, y, z));
	}

	private static boolean full(HotelPlan plan, int x, int y, int z) {
		return FULL.computeIfAbsent(key(new int[] {x, y, z}), k -> {
			BlockState below = plan.at(x, y, z);
			return !below.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).isEmpty()
					&& below.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getMax(net.minecraft.util.math.Direction.Axis.Y) >= 0.99
					&& !(below.getBlock() instanceof DoorBlock);
		});
	}

	private static boolean freeUncached(HotelPlan plan, int x, int y, int z) {
		BlockState s = plan.at(x, y, z);
		if (x < 0 || z < -6 || x >= HotelPlan.SX || z >= HotelPlan.SZ || y < 0 || y >= HotelPlan.SY) {
			return z < 0 && y >= 0 && y < 8;
		}
		if (s.getBlock() instanceof DoorBlock) {
			return true;
		}
		return s.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).isEmpty()
				|| s.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getMax(net.minecraft.util.math.Direction.Axis.Y) <= 0.1;
	}

	private static boolean headroom(HotelPlan plan, int x, int y, int z) {
		return free(plan, x, y, z);
	}

	/** Feet at this cell: it and the one above it free of anything solid, and something to stand on (a full block, or the stair itself). */
	private static boolean standable(HotelPlan plan, int x, int y, int z) {
		BlockState here = plan.at(x, y, z);
		boolean stair = here.getBlock() instanceof StairsBlock;
		if (!stair && !free(plan, x, y, z)) {
			return false;
		}
		if (!free(plan, x, y + 1, z)) {
			return false;
		}
		if (stair) {
			return true;
		}
		if (z < 0 || z >= HotelPlan.SZ || x < 0 || x >= HotelPlan.SX) {
			return y == 1 && z < 0 && z >= -HotelBuilder.PLAZA;
		}
		BlockState below = plan.at(x, y - 1, z);
		if (below.getBlock() instanceof StairsBlock) {
			return true;
		}
		return y - 1 >= 0 && full(plan, x, y - 1, z);
	}

	/** The hotel is in the world's generation: the structure, its set and the biomes it may stand in; and the game rules are there. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_worldgen", tickLimit = 20)
	public void hotelIsInWorldGeneration(TestContext context) {
		net.minecraft.registry.DynamicRegistryManager registries = context.getWorld().getRegistryManager();
		net.minecraft.util.Identifier id = io.github.kortev.chainsawman.ChainsawMan.id("eternity_hotel");
		context.assertTrue(registries.get(RegistryKeys.STRUCTURE).containsId(id), "the structure is not loaded");
		context.assertTrue(registries.get(RegistryKeys.STRUCTURE_SET).containsId(id), "the structure set is not loaded");
		net.minecraft.registry.tag.TagKey<net.minecraft.world.biome.Biome> tag = net.minecraft.registry.tag.TagKey.of(RegistryKeys.BIOME, io.github.kortev.chainsawman.ChainsawMan.id("has_eternity_hotel"));
		var biomes = registries.get(RegistryKeys.BIOME);
		int count = 0;
		for (var entry : biomes.iterateEntries(tag)) {
			count++;
		}
		context.assertTrue(count >= 4, "the biome list has only " + count + " biomes");
		context.assertTrue(context.getWorld().getGameRules().getBoolean(HotelRules.ONE_CONTRACT_PER_DEVIL) == false, "oneContractPerDevil is not off by default");
		context.assertTrue(context.getWorld().getGameRules().getInt(HotelRules.DEVIL_RESPAWN_DAYS) == 3, "devilRespawnDays is not 3 by default");
		context.complete();
	}
}
