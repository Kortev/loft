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
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
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
		return origin(context, 0);
	}

	/**
	 * The tests of a batch all run at once, a few blocks apart, and a hotel is 38 wide and wakes for a player 96 blocks off: each test has its
	 * own slot, 300 blocks along from the last.
	 */
	static BlockPos origin(TestContext context, int slot) {
		return BlockPos.ofFloored(context.getAbsolute(new Vec3d(0.5, 40.0, 0.5))).down().add(2 + 300 * slot, 0, 2);
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
		for (int x = -4; x <= 42; x++) {
			for (int z = -9; z <= 18; z++) {
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
			return z < 0 && y >= 0 && y < 8 && x >= -1 && x <= HotelPlan.SX;
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
			return y == 1 && z < 0 && z >= -HotelBuilder.PLAZA && x >= -1 && x <= HotelPlan.SX;
		}
		BlockState below = plan.at(x, y - 1, z);
		if (below.getBlock() instanceof StairsBlock) {
			return true;
		}
		return y - 1 >= 0 && full(plan, x, y - 1, z);
	}

	/**
	 * The game itself places it: {@code /place structure} makes the structure start and its piece draws the hotel chunk by chunk (as the
	 * world's generation does), and the home that results knows where the hotel stands and how it is turned.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_structure", tickLimit = 400)
	public void theGameCanPlaceTheStructure(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockPos at = origin(context, 19).add(60, 0, 40);
		forced(context, at, true);
		context.runAtTick(2, () -> {
			// What /place structure does: make the structure start, then draw it chunk by chunk.
			net.minecraft.world.gen.structure.Structure structure = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(io.github.kortev.chainsawman.ChainsawMan.id("eternity_hotel"));
			net.minecraft.world.gen.chunk.ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
			net.minecraft.structure.StructureStart start = structure.createStructureStart(world.getRegistryManager(), generator, generator.getBiomeSource(),
					world.getChunkManager().getNoiseConfig(), world.getStructureTemplateManager(), world.getSeed(), new net.minecraft.util.math.ChunkPos(at), 0, world, biome -> true);
			io.github.kortev.chainsawman.ChainsawMan.LOGGER.info("[hotel-test] structure start has children: " + start.hasChildren());
			context.assertTrue(start.hasChildren(), "the structure found nowhere to stand (it looks for flat dry ground)");
			net.minecraft.util.math.BlockBox box = start.getBoundingBox();
			net.minecraft.util.math.ChunkPos a0 = new net.minecraft.util.math.ChunkPos(box.getMinX() >> 4, box.getMinZ() >> 4);
			net.minecraft.util.math.ChunkPos a1 = new net.minecraft.util.math.ChunkPos(box.getMaxX() >> 4, box.getMaxZ() >> 4);
			net.minecraft.util.math.ChunkPos.stream(a0, a1).forEach(c -> start.place(world, world.getStructureAccessor(), generator, world.getRandom(),
					new net.minecraft.util.math.BlockBox(c.getStartX(), world.getBottomY(), c.getStartZ(), c.getEndX(), world.getTopY(), c.getEndZ()), c));
		});
		context.runAtTick(60, () -> {
			HomeBlockEntity found = null;
			for (HomeBlockEntity h : HomeBlockEntity.LOADED) {
				if (h.getWorld() == world && Math.hypot(h.getPos().getX() - at.getX(), h.getPos().getZ() - at.getZ()) < 48.0) {
					found = h;
				}
			}
			context.assertTrue(found != null, "the structure made no home near " + at);
			context.assertTrue(found.origin.getX() != 0 || found.origin.getZ() != 0, "the home does not know where the hotel is");
			// Whole: the plan's blocks are there at the home's own place and turn.
			HotelPlan plan = HotelPlan.get();
			int bad = 0;
			for (int y = 0; y < HotelPlan.SY; y += 3) {
				for (int z = 1; z < HotelPlan.SZ - 1; z += 2) {
					for (int x = 1; x < HotelPlan.SX - 1; x += 2) {
						BlockPos p = HotelBuilder.world(found.origin, found.rot, x, y, z);
						BlockState want = plan.at(x, y, z).rotate(HotelBuilder.turn(found.rot));
						BlockState got = world.getBlockState(p);
						if (got != want && !want.isAir() && !got.isOf(Blocks.CHAIN) && !(want.getBlock() instanceof net.minecraft.block.DoorBlock)) {
							bad++;
						}
					}
				}
			}
			context.assertTrue(bad < 30, bad + " sampled blocks of the placed hotel differ from the plan (turn " + found.rot + ")");
			forced(context, at, false);
			context.complete();
		});
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

	// ---- The hotel awake.

	/** A hotel at {@code rot} on cleared ground, its chunks loaded. */
	static HomeBlockEntity hotel(TestContext context, int rot, int slot) {
		BlockPos origin = origin(context, slot);
		forced(context, origin, true);
		ground(context, origin);
		return Hotel.place(context.getWorld(), origin, rot);
	}

	/** {@code p} at the local point of the hotel, looking as given. */
	static void put(ServerPlayerEntity p, HomeBlockEntity h, double lx, double ly, double lz, float yaw, float pitch) {
		confirm(p);
		Vec3d w = HotelBuilder.world(h.origin, h.rot, lx, ly, lz);
		p.updatePositionAndAngles(w.x, w.y, w.z, yaw, pitch);
		p.setOnGround(true);
	}

	/**
	 * The test player has no client to confirm a teleport, so the connection keeps putting them back where the last one asked (every 20
	 * ticks): confirm it, as a client would.
	 */
	static void confirm(ServerPlayerEntity p) {
		try {
			java.lang.reflect.Field pos = ServerPlayNetworkHandler.class.getDeclaredField("requestedTeleportPos");
			pos.setAccessible(true);
			if (pos.get(p.networkHandler) != null) {
				java.lang.reflect.Field id = ServerPlayNetworkHandler.class.getDeclaredField("requestedTeleportId");
				id.setAccessible(true);
				p.networkHandler.onTeleportConfirm(new net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket(id.getInt(p.networkHandler)));
			}
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	static Vec3d local(HomeBlockEntity h, ServerPlayerEntity p) {
		confirm(p);
		return HotelBuilder.local(h.origin, h.rot, p.getPos());
	}

	static BlockState at(TestContext context, HomeBlockEntity h, int x, int y, int z) {
		return context.getWorld().getBlockState(HotelBuilder.world(h.origin, h.rot, x, y, z));
	}

	static void leaveHotel(TestContext context, HomeBlockEntity h, ServerPlayerEntity... players) {
		for (ServerPlayerEntity p : players) {
			ForeverGameTests.leave(context, p);
		}
		forced(context, h.origin, false);
	}

	private static void assertDead(TestContext context, HomeBlockEntity h, String when) {
		context.assertTrue(!h.run.night, when + ": the hotel is awake");
		context.assertTrue(at(context, h, 27, 1, 0).get(DoorBlock.OPEN) == false, when + ": the entrance is open");
		context.assertTrue(at(context, h, 27, 1, 1).isOf(Blocks.CHAIN), when + ": the entrance is not chained");
		context.assertTrue(at(context, h, 4, 1, 8).get(DoorBlock.OPEN) == false, when + ": a room door is open");
		context.assertTrue(at(context, h, 21, 4, 6).get(net.minecraft.block.RedstoneLampBlock.LIT) == false, when + ": a lamp is lit");
		context.assertTrue(at(context, h, 20, 1, 0).isOf(Blocks.BLACK_STAINED_GLASS), when + ": the lobby windows are not dark");
		context.assertTrue(at(context, h, 23, 3, 3).isAir(), when + ": the lobby has its light");
	}

	private static void assertNight(TestContext context, HomeBlockEntity h) {
		context.assertTrue(h.run.night, "night: the hotel is asleep");
		context.assertTrue(at(context, h, 27, 1, 0).get(DoorBlock.OPEN), "night: the entrance is shut");
		context.assertTrue(at(context, h, 27, 1, 1).isAir(), "night: the entrance is chained");
		context.assertTrue(at(context, h, 4, 1, 8).get(DoorBlock.OPEN), "night: a room door is shut");
		context.assertTrue(at(context, h, 4, 29, 8).get(DoorBlock.OPEN) == false, "night: a door on the loop floor is open");
		context.assertTrue(at(context, h, 21, 4, 6).get(net.minecraft.block.RedstoneLampBlock.LIT), "night: a lamp is dark");
		context.assertTrue(at(context, h, 20, 1, 0).isOf(Blocks.GLASS), "night: the lobby windows are dark");
		context.assertTrue(at(context, h, 23, 3, 3).isOf(Blocks.LIGHT), "night: the lobby is not lit");
		boolean someDark = false;
		boolean someLit = false;
		for (HotelPlan.Glass g : HotelPlan.get().glass) {
			if (g.group() > 0 && g.group() < 1000) {
				boolean lit = at(context, h, g.x(), g.y(), g.z()).isOf(Blocks.GLASS);
				someLit |= lit;
				someDark |= !lit;
			}
		}
		context.assertTrue(someLit && someDark, "night: some windows should be lit and some not (lit " + someLit + ", dark " + someDark + ")");
	}

	/** By day it is dead (shut, chained, dark); at night with a player near it wakes (open, lit, the chains gone); with nobody near it stays dead; by day again it sleeps. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_night", tickLimit = 160)
	public void hotelWakesAtNightAndSleepsByDay(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 1);
		ServerWorld world = context.getWorld();
		ServerPlayerEntity p = ForeverGameTests.player(context, "night_walker");
		world.setTimeOfDay(3000);
		put(p, h, 20.5, 1.0, 3.5, 0.0F, 0.0F);
		context.runAtTick(25, () -> {
			assertDead(context, h, "day");
			world.setTimeOfDay(14000);
		});
		context.runAtTick(55, () -> {
			assertNight(context, h);
			world.setTimeOfDay(3000);
		});
		context.runAtTick(85, () -> {
			assertDead(context, h, "dawn");
			put(p, h, 400.0, 1.0, 3.5, 0.0F, 0.0F);
			world.setTimeOfDay(14000);
		});
		context.runAtTick(115, () -> {
			assertDead(context, h, "night with nobody near");
			leaveHotel(context, h, p);
			world.setTimeOfDay(3000);
			context.complete();
		});
	}

	/** The wrong things heard: the lobby hums, floor 6's door breathes (it opens and shuts by itself), floor 7's phone rings. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_floors", tickLimit = 320)
	public void eachFloorIsStranger(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 2);
		ServerWorld world = context.getWorld();
		ServerPlayerEntity p = ForeverGameTests.player(context, "climber");
		world.setTimeOfDay(14000);
		put(p, h, 28.5, 1.0, 3.5, 0.0F, 0.0F);
		boolean[] opens = new boolean[2];
		context.runAtTick(45, () -> {
			context.assertTrue(h.run.humAt > 0, "the lobby did not hum: night " + h.run.night + " player " + p.getPos() + " origin " + h.origin + " local " + local(h, p));
			put(p, h, 20.5, 21.0, 6.5, 0.0F, 0.0F);
			HotelPlan.P d = HotelPlan.get().breathDoor;
			opens[0] = at(context, h, d.x(), d.y(), d.z()).get(DoorBlock.OPEN);
		});
		context.runAtTick(130, () -> {
			HotelPlan.P d = HotelPlan.get().breathDoor;
			opens[1] = at(context, h, d.x(), d.y(), d.z()).get(DoorBlock.OPEN);
			context.assertTrue(h.run.breathAt > 0, "the door on floor 6 did not breathe");
			context.assertTrue(opens[1] == h.run.breathOpen, "the breathing door is not where the hotel says it is");
			put(p, h, 20.5, 25.0, 6.5, 0.0F, 0.0F);
		});
		context.runAtTick(210, () -> {
			context.assertTrue(h.run.phoneAt > 0, "the phone on floor 7 did not ring");
			context.assertTrue(HotelPlan.get().phone != null, "no phone");
			// Floor 2's repeated number and floor 3's painting are in the plan.
			int found = 0;
			for (HotelPlan.Sign s : HotelPlan.get().signs) {
				if (s.lines()[1].equals("202")) {
					found++;
				}
			}
			context.assertTrue(found == 2, "floor 2 should have the number 202 twice, has it " + found + " times");
			context.assertTrue(at(context, h, 7, 11, 8).isOf(Blocks.YELLOW_WOOL), "floor 3 is missing its painting");
			context.assertTrue(at(context, h, 13, 11, 8).isOf(Blocks.YELLOW_WOOL), "floor 3's painting does not come again");
			leaveHotel(context, h, p);
			world.setTimeOfDay(3000);
			context.complete();
		});
	}

	/**
	 * The loop: past the corridor's window one way or the other a player is put back, at every turn of the hotel; their place across the
	 * corridor and height, their look and their speed are kept; each time is a lap. A player who has taken the deal is not put back; nor is
	 * one on another floor; and down the stairs from the loop floor is back at the corridor's start.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_loop", tickLimit = 340)
	public void theLoopPutsYouBackSeamlesslyAtEveryTurn(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerPlayerEntity p = ForeverGameTests.player(context, "walker");
		HomeBlockEntity[] hh = new HomeBlockEntity[1];
		for (int rot = 0; rot < 4; rot++) {
			int base = 1 + rot * 80;
			int turn = rot;
			context.runAtTick(base, () -> {
				hh[0] = hotel(context, turn, 3);
				world.setTimeOfDay(14000);
				put(p, hh[0], 24.0, 29.0, 6.5, 40.0F, 12.0F);
			});
			context.runAtTick(base + 30, () -> {
				HomeBlockEntity h = hh[0];
				context.assertTrue(h.run.night, "turn " + turn + ": the hotel is asleep");
				// Out of the east end of the window.
				put(p, h, 25.3, 29.0, 6.1, 33.0F, 12.0F);
				Vec3d v = HotelBuilder.world(BlockPos.ORIGIN, turn, 0.21, 0.0, 0.05);
				p.setVelocity(v);
			});
			context.runAtTick(base + 33, () -> {
				HomeBlockEntity h = hh[0];
				Vec3d lp = local(h, p);
				context.assertTrue(Math.abs(lp.x - 13.3) < 0.02 && Math.abs(lp.z - 6.1) < 0.02 && Math.abs(lp.y - 29.0) < 0.02, "turn " + turn + ": put back at " + lp + ", not (13.3, 29, 6.1); player at " + p.getPos() + " origin " + h.origin + " night " + h.run.night + " top " + h.run.top.size());
				context.assertTrue(p.getYaw() == 33.0F && p.getPitch() == 12.0F, "turn " + turn + ": the look changed: " + p.getYaw() + ", " + p.getPitch());
				Vec3d want = HotelBuilder.world(BlockPos.ORIGIN, turn, 0.21, 0.0, 0.05);
				context.assertTrue(p.getVelocity().squaredDistanceTo(want) < 1.0E-8, "turn " + turn + ": the speed changed: " + p.getVelocity());
				context.assertTrue(h.run.laps.getOrDefault(p.getUuid(), 0) == 1, "turn " + turn + ": that was not a lap");
				// And out of the west end.
				put(p, h, 12.8, 29.0, 6.1, 33.0F, 12.0F);
			});
			context.runAtTick(base + 36, () -> {
				HomeBlockEntity h = hh[0];
				Vec3d lp = local(h, p);
				context.assertTrue(Math.abs(lp.x - 24.8) < 0.02 && Math.abs(lp.z - 6.1) < 0.02, "turn " + turn + ": west end put back at " + lp);
				context.assertTrue(h.run.laps.getOrDefault(p.getUuid(), 0) == 2, "turn " + turn + ": the second was not a lap");
				// A player who has taken the deal is let walk off.
				h.run.freed.add(p.getUuid());
				put(p, h, 25.5, 29.0, 6.1, 33.0F, 12.0F);
			});
			context.runAtTick(base + 40, () -> {
				HomeBlockEntity h = hh[0];
				context.assertTrue(Math.abs(local(h, p).x - 25.5) < 0.02, "turn " + turn + ": a freed player was put back");
				h.run.freed.remove(p.getUuid());
				// On the floor below the corridor is a corridor and nothing more.
				put(p, h, 25.5, 25.0, 6.1, 33.0F, 12.0F);
			});
			context.runAtTick(base + 44, () -> {
				HomeBlockEntity h = hh[0];
				context.assertTrue(Math.abs(local(h, p).x - 25.5) < 0.02, "turn " + turn + ": a player on floor 7 was put back");
				// Up on the loop floor in the stairwell, then down the stairs.
				put(p, h, 17.5, 29.0, 3.5, 33.0F, 12.0F);
			});
			context.runAtTick(base + 48, () -> {
				HomeBlockEntity h = hh[0];
				put(p, h, 16.5, 27.4, 3.5, 33.0F, 12.0F);
			});
			context.runAtTick(base + 52, () -> {
				HomeBlockEntity h = hh[0];
				Vec3d lp = local(h, p);
				context.assertTrue(Math.abs(lp.x - 14.0) < 0.05 && Math.abs(lp.y - 29.0) < 0.05 && Math.abs(lp.z - 6.5) < 0.05, "turn " + turn + ": down the stairs ended at " + lp + ", not the corridor's start");
				context.assertTrue(p.getYaw() == HotelBuilder.alongYaw(turn), "turn " + turn + ": the stairs left you looking " + p.getYaw());
				context.assertTrue(h.run.laps.getOrDefault(p.getUuid(), 0) == 3, "turn " + turn + ": the stairs were not a lap");
			});
			context.runAtTick(base + 55, () -> {
				HomeBlockEntity h = hh[0];
				// Three laps: the flesh is at the corners (level 3: all of it), the same at every period.
				context.assertTrue(h.run.flesh == 3, "turn " + turn + ": the flesh is at level " + h.run.flesh);
				context.assertTrue(h.run.growths.size() >= 30, "turn " + turn + ": only " + h.run.growths.size() + " lumps of flesh");
				int found = 0;
				for (java.util.UUID id : h.run.growths) {
					found += world.getEntity(id) != null ? 1 : 0;
				}
				context.assertTrue(found > 0, "turn " + turn + ": none of the " + h.run.growths.size() + " lumps are in the world");
			});
			context.runAtTick(base + 56, () -> {
				// Day comes while nobody is on the loop floor: the flesh goes with the night.
				HomeBlockEntity h = hh[0];
				put(p, h, 20.5, 1.0, 3.5, 0.0F, 0.0F);
				world.setTimeOfDay(3000);
			});
			context.runAtTick(base + 72, () -> {
				HomeBlockEntity h = hh[0];
				context.assertTrue(!h.run.night && h.run.flesh == 0, "turn " + turn + ": the flesh stayed through the day");
				context.assertTrue(h.run.growths.isEmpty(), "turn " + turn + ": the flesh was left");
				context.assertTrue(h.run.laps.isEmpty(), "turn " + turn + ": the laps were kept through the day");
				if (turn == 3) {
					leaveHotel(context, h, p);
					context.complete();
				}
			});
		}
	}

	// ---- The deal, the fight, the loot and the devil coming back.

	/** A hotel awake at night with {@code name} on its loop floor, at {@code x} along the corridor. */
	private static ServerPlayerEntity walker(TestContext context, HomeBlockEntity h, String name, double x) {
		ServerPlayerEntity p = ForeverGameTests.player(context, name);
		context.getWorld().setTimeOfDay(14000);
		put(p, h, x, 29.0, 6.5, 90.0F, 0.0F);
		return p;
	}

	private static net.minecraft.entity.Entity devilOf(TestContext context, HomeBlockEntity h) {
		return h.run.devil == null ? null : context.getWorld().getEntity(h.run.devil);
	}

	private static void tidy(TestContext context, HomeBlockEntity h, ServerPlayerEntity... players) {
		for (ServerPlayerEntity p : players) {
			io.github.kortev.chainsawman.contract.Contracts.clear(p);
			io.github.kortev.chainsawman.form.Heart.take(p);
		}
		net.minecraft.entity.Entity d = devilOf(context, h);
		if (d != null) {
			d.discard();
		}
		leaveHotel(context, h, players);
		context.getWorld().setTimeOfDay(3000);
		Hotel.answerTicks = 400;
		context.getWorld().getGameRules().get(HotelRules.ONE_CONTRACT_PER_DEVIL).set(false, context.getWorld().getServer());
	}

	/** After three laps the devil speaks and then the offer is out (after its words), once. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_deal_laps", tickLimit = 260)
	public void theDealIsOfferedAfterThreeLaps(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 4);
		ServerPlayerEntity p = walker(context, h, "three_laps", 20.5);
		context.runAtTick(25, () -> {
			context.assertTrue(h.run.deal == null && !h.run.dealt, "an offer was out before anything was walked");
			h.run.laps.put(p.getUuid(), 2);
		});
		context.runAtTick(40, () -> context.assertTrue(h.run.deal == null, "the deal came after two laps"));
		context.runAtTick(45, () -> h.run.laps.put(p.getUuid(), 3));
		context.runAtTick(60, () -> {
			context.assertTrue(h.run.deal != null && !h.run.deal.open, "the devil did not start to speak after three laps");
			context.assertTrue(h.run.deal.players.contains(p.getUuid()), "the player is not in the offer");
		});
		context.runAtTick(220, () -> {
			context.assertTrue(h.run.deal != null && h.run.deal.open, "the contract was not offered after the devil's words");
			context.assertTrue(h.run.deal.deadline.get(p.getUuid()) > context.getWorld().getTime(), "no time to answer");
			context.assertTrue(!h.run.fight, "the fight began before an answer");
			tidy(context, h, p);
			context.complete();
		});
	}

	/** About a minute on the floor does it too, with no laps. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_deal_time", tickLimit = 100)
	public void theDealIsOfferedAfterAMinuteOnTheFloor(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 5);
		ServerPlayerEntity p = walker(context, h, "slow_walker", 20.5);
		context.runAtTick(25, () -> {
			context.assertTrue(h.run.deal == null, "an offer was out already");
			h.run.since.put(p.getUuid(), context.getWorld().getTime() - Hotel.FLOOR_TICKS_FOR_DEAL + 8);
		});
		context.runAtTick(30, () -> context.assertTrue(h.run.deal == null, "the deal came before the minute was up"));
		context.runAtTick(60, () -> {
			context.assertTrue(h.run.deal != null, "the deal did not come after a minute on the floor");
			tidy(context, h, p);
			context.complete();
		});
	}

	/** Accepting: the contract, the loop opens for them, the devil is away for the respawn days, and an older contract is ended. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_accept", tickLimit = 300)
	public void acceptingGivesTheContractAndOpensTheLoop(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 6);
		ServerPlayerEntity p = walker(context, h, "acceptor", 20.5);
		context.runAtTick(25, () -> {
			// Holding another devil's contract (one this mod does not know yet): it is ended by the new one.
			p.setAttached(io.github.kortev.chainsawman.contract.Contracts.HELD, "fox");
			h.run.laps.put(p.getUuid(), 3);
		});
		context.runAtTick(215, () -> {
			context.assertTrue(h.run.deal != null && h.run.deal.open, "no offer to accept");
			Hotel.answer(p, true);
			context.assertTrue(io.github.kortev.chainsawman.contract.Contracts.has(p, io.github.kortev.chainsawman.contract.Contract.ETERNITY), "accepting gave no contract");
			context.assertTrue("eternity".equals(p.getAttachedOrElse(io.github.kortev.chainsawman.contract.Contracts.HELD, "")), "the older contract was not ended");
			context.assertTrue(h.run.freed.contains(p.getUuid()), "the loop did not open for them");
			context.assertTrue(p.getUuid().equals(h.holder), "the hotel does not know who holds its contract");
		});
		context.runAtTick(220, () -> {
			context.assertTrue(h.run.deal == null && !h.run.fight, "accepting started a fight");
			context.assertTrue(h.away && h.awayUntilDay == Hotel.day(context.getWorld()) + 3, "the devil is not away for 3 days after the deal: " + h.awayUntilDay);
			// They walk out of the end of the corridor and are not put back.
			put(p, h, 25.6, 29.0, 6.5, 90.0F, 0.0F);
		});
		context.runAtTick(226, () -> {
			context.assertTrue(Math.abs(local(h, p).x - 25.6) < 0.05, "an accepted player was put back: " + local(h, p));
			tidy(context, h, p);
			context.complete();
		});
	}

	/** A hybrid (Pochita's heart) is refused with a line from the devil, gets no contract, and meets the devil. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_hybrid", tickLimit = 300)
	public void aHybridIsRefusedAndGetsTheFight(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 7);
		ServerPlayerEntity p = walker(context, h, "hybrid_walker", 20.5);
		io.github.kortev.chainsawman.form.Heart.give(p);
		context.runAtTick(25, () -> h.run.laps.put(p.getUuid(), 3));
		context.runAtTick(215, () -> {
			context.assertTrue(h.run.deal != null && h.run.deal.open, "no offer for the hybrid to try");
			Hotel.answer(p, true);
			context.assertTrue(!io.github.kortev.chainsawman.contract.Contracts.has(p, io.github.kortev.chainsawman.contract.Contract.ETERNITY), "a hybrid got a contract");
			context.assertTrue(!h.run.freed.contains(p.getUuid()), "a hybrid was let out");
		});
		context.runAtTick(225, () -> {
			context.assertTrue(h.run.fight, "the hybrid did not get the fight");
			context.assertTrue(h.run.devil != null, "no devil grew");
			context.assertTrue(!h.away, "the devil went away though nobody took the deal");
			tidy(context, h, p);
			context.complete();
		});
	}

	/** Refusing: the walls bleed and the devil grows out of them at the corridor's end. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_refuse", tickLimit = 360)
	public void refusingStartsTheFightAndTheWallsBleed(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 8);
		ServerPlayerEntity p = walker(context, h, "refuser", 20.5);
		context.runAtTick(25, () -> h.run.laps.put(p.getUuid(), 3));
		context.runAtTick(215, () -> {
			context.assertTrue(h.run.deal != null && h.run.deal.open, "no offer to refuse");
			Hotel.answer(p, false);
		});
		context.runAtTick(225, () -> {
			context.assertTrue(h.run.fight, "refusing did not start the fight");
			context.assertTrue(h.run.devil != null, "no devil grew");
			context.assertTrue(!h.away && !h.run.freed.contains(p.getUuid()), "refusing let them out");
			context.assertTrue(!io.github.kortev.chainsawman.contract.Contracts.has(p, io.github.kortev.chainsawman.contract.Contract.ETERNITY), "refusing gave a contract");
		});
		context.runAtTick(310, () -> {
			// The wallpaper at the corridor's end has bled.
			int changed = 0;
			for (int x = 24; x <= 36; x++) {
				for (int y = 29; y <= 31; y++) {
					for (int z : new int[] {4, 8}) {
						if (!HotelPlan.get().at(x, y, z).isAir() && !(HotelPlan.get().at(x, y, z).getBlock() instanceof DoorBlock) && at(context, h, x, y, z) != HotelPlan.get().at(x, y, z)) {
							changed++;
						}
					}
				}
			}
			context.assertTrue(changed >= 4, "only " + changed + " blocks of wall have bled");
			net.minecraft.entity.Entity d = devilOf(context, h);
			context.assertTrue(d instanceof io.github.kortev.chainsawman.hotel.EternityDevilEntity, "no devil grew");
			Vec3d lp = HotelBuilder.local(h.origin, h.rot, d.getPos());
			context.assertTrue(Math.abs(lp.x - 34.4) < 0.05 && Math.abs(lp.y - 29.0) < 0.05 && Math.abs(lp.z - 6.5) < 0.05, "the devil is at " + lp + ", not the corridor's end");
			context.assertTrue(Math.abs(d.getWidth() - 4.6F) < 0.01 && Math.abs(d.getHeight() - 4.0F) < 0.01, "its size is " + d.getWidth() + " x " + d.getHeight());
			tidy(context, h, p);
			context.complete();
		});
	}

	/** Not answering within 20 seconds is a refusal. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_silence", tickLimit = 330)
	public void silenceStartsTheFight(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 9);
		h.run.answerTicks = 40;
		ServerPlayerEntity p = walker(context, h, "mute", 20.5);
		context.runAtTick(25, () -> h.run.laps.put(p.getUuid(), 3));
		context.runAtTick(135, () -> context.assertTrue(h.run.deal != null && h.run.deal.open && !h.run.fight, "the offer was not waiting, or the fight came early"));
		context.runAtTick(230, () -> {
			context.assertTrue(h.run.fight, "silence did not start the fight");
			context.assertTrue(h.run.devil != null, "no devil grew");
			tidy(context, h, p);
			context.complete();
		});
	}

	/** With oneContractPerDevil on and a holder who still holds it, the deal is not offered (the fight comes); once they lose it, it is offered again. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_one", tickLimit = 420)
	public void oneContractPerDevilBlocksASecondDeal(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 10);
		ServerPlayerEntity holder = ForeverGameTests.player(context, "first_holder");
		ServerPlayerEntity p = walker(context, h, "second_comer", 20.5);
		context.getWorld().getGameRules().get(HotelRules.ONE_CONTRACT_PER_DEVIL).set(true, context.getWorld().getServer());
		io.github.kortev.chainsawman.contract.Contracts.grant(holder, io.github.kortev.chainsawman.contract.Contract.ETERNITY);
		h.holder = holder.getUuid();
		context.runAtTick(40, () -> {
			context.assertTrue(Hotel.holderStillHolds(context.getWorld(), h), "the holder does not hold it");
			h.run.laps.put(p.getUuid(), 3);
		});
		context.runAtTick(55, () -> {
			context.assertTrue(h.run.deal == null && h.run.dealt, "a second deal was offered");
			context.assertTrue(h.run.fight || h.run.fightAt > 0, "the devil did not turn on the second comer");
		});
		context.runAtTick(150, () -> {
			context.assertTrue(h.run.fight && h.run.devil != null, "no fight");
			io.github.kortev.chainsawman.contract.Contracts.clear(holder);
			context.assertTrue(!Hotel.holderStillHolds(context.getWorld(), h) && h.holder == null, "the holder lost it and it is still theirs");
			ForeverGameTests.leave(context, holder);
			tidy(context, h, p);
			context.complete();
		});
	}

	/** Two in the loop together: one accepts and is let out, the other refuses and fights. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_two", tickLimit = 300)
	public void twoInTheLoopAnswerSeparately(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 11);
		ServerPlayerEntity a = walker(context, h, "takes_it", 20.5);
		ServerPlayerEntity b = walker(context, h, "leaves_it", 22.5);
		context.runAtTick(25, () -> h.run.laps.put(a.getUuid(), 3));
		context.runAtTick(215, () -> {
			context.assertTrue(h.run.deal != null && h.run.deal.open && h.run.deal.players.size() == 2, "both are not in the offer");
			Hotel.answer(a, true);
			context.assertTrue(h.run.deal != null && !h.run.fight, "the deal ended before the other answered");
			Hotel.answer(b, false);
		});
		context.runAtTick(225, () -> {
			context.assertTrue(h.run.fight, "the one who refused did not get the fight");
			context.assertTrue(h.run.freed.contains(a.getUuid()) && !h.run.freed.contains(b.getUuid()), "the wrong one was let out");
			context.assertTrue(io.github.kortev.chainsawman.contract.Contracts.has(a, io.github.kortev.chainsawman.contract.Contract.ETERNITY)
					&& !io.github.kortev.chainsawman.contract.Contracts.has(b, io.github.kortev.chainsawman.contract.Contract.ETERNITY), "the contract went to the wrong one");
			context.assertTrue(h.away, "the devil is not away after one took the deal");
			tidy(context, h, a, b);
			context.complete();
		});
	}

	// ---- The devil.

	/**
	 * Waits until the hotel's chunks are really ticking (a test started with a score of others loads its chunks late: an entity put in
	 * one before is not found and does not age), then runs {@code go}; what it schedules is counted from then ({@link #later}).
	 */
	static void ready(TestContext context, HomeBlockEntity h, Runnable go) {
		boolean[] started = {false};
		for (int t = 1; t < 400; t++) {
			context.runAtTick(t, () -> {
				if (started[0]) {
					return;
				}
				ServerWorld world = context.getWorld();
				BlockPos a = HotelBuilder.world(h.origin, h.rot, 2, 29, 6);
				BlockPos b = HotelBuilder.world(h.origin, h.rot, 34, 29, 6);
				BlockPos c = HotelBuilder.world(h.origin, h.rot, 20, 29, 6);
				if (world.shouldTickEntity(a) && world.shouldTickEntity(b) && world.shouldTickEntity(c) && h.run.started) {
					started[0] = true;
					go.run();
				}
			});
		}
	}

	/** {@code r} in {@code delta} ticks from now (inside a {@link #ready} scenario). */
	static void later(TestContext context, int delta, Runnable r) {
		context.runAtTick(context.getTick() + delta, r);
	}

	private static io.github.kortev.chainsawman.hotel.EternityDevilEntity fightAt(TestContext context, HomeBlockEntity h) {
		Hotel.startFight(context.getWorld(), h, context.getWorld().getTime());
		return (io.github.kortev.chainsawman.hotel.EternityDevilEntity) devilOf(context, h);
	}

	private static void armed(ServerPlayerEntity p, double health) {
		p.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(health);
		p.setHealth((float) health);
	}

	/** It cannot be hurt while it grows; then it fights: mouths and tendrils hurt, flesh creeps; the heart is hidden. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_devil", tickLimit = 700)
	public void theDevilGrowsOutOfTheWallsAndFightsBack(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 12);
		ServerPlayerEntity p = walker(context, h, "fighter", 24.0);
		armed(p, 2000.0);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		int[] patches = {0};
		ready(context, h, () -> {
			d[0] = fightAt(context, h);
			context.assertTrue(d[0] != null && d[0].getHealth() == 300.0F && d[0].getMaxHealth() == 300.0F, "the devil does not have 300 health");
			context.assertTrue(!d[0].damage(p.getDamageSources().playerAttack(p), 50.0F), "it was hurt while it grew");
			context.assertTrue(!d[0].heartOut(), "its heart shows from the start");
			for (int t = 1; t < 460; t++) {
				later(context, t, () -> patches[0] = Math.max(patches[0], context.getWorld().getEntitiesByType(io.github.kortev.chainsawman.ChainsawMan.FLESH_PATCH, e -> true).size()));
			}
			later(context, 100, () -> {
				context.assertTrue(!d[0].growing(), "it is still growing");
				context.assertTrue(d[0].damage(p.getDamageSources().playerAttack(p), 10.0F), "it could not be hurt once grown");
				context.assertTrue(d[0].getHealth() == 290.0F, "a hit of 10 left it at " + d[0].getHealth());
			});
			later(context, 470, () -> {
				context.assertTrue(p.getHealth() < 2000.0F, "the devil never hurt someone standing in its corridor");
				context.assertTrue(patches[0] > 0, "flesh never crept across the floor");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** It regenerates while it is not hit, and is held back while it is. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_regen", tickLimit = 700)
	public void theDevilRegeneratesWhenNotHit(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 13);
		ServerPlayerEntity p = walker(context, h, "slasher", 14.0);
		armed(p, 2000.0);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		float[] mark = new float[2];
		ready(context, h, () -> {
			d[0] = fightAt(context, h);
			later(context, 90, () -> d[0].setHealth(200.0F));
			later(context, 180, () -> {
				context.assertTrue(d[0].getHealth() > 200.0F + 10.0F, "it did not regenerate: " + d[0].getHealth());
				// Hit it: no regeneration for a few seconds.
				d[0].damage(p.getDamageSources().playerAttack(p), 20.0F);
				mark[1] = d[0].getHealth();
			});
			later(context, 220, () -> context.assertTrue(d[0].getHealth() <= mark[1] + 0.01F, "it regenerated within 40 ticks of being hit: " + d[0].getHealth() + " from " + mark[1]));
			later(context, 320, () -> {
				context.assertTrue(d[0].getHealth() > mark[1] + 5.0F, "it did not start again after a few seconds: " + d[0].getHealth() + " from " + mark[1]);
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** Cut enough away and the heart shows; a hit looking at it does three times as much, a hit looking elsewhere the usual. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_heart", tickLimit = 500)
	public void theHeartShowsWhenCutAwayAndTakesMore(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 14);
		ServerPlayerEntity p = walker(context, h, "heart_hunter", 26.0);
		armed(p, 5000.0);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		ready(context, h, () -> {
			d[0] = fightAt(context, h);
			later(context, 90, () -> {
				context.assertTrue(!d[0].heartOut(), "the heart shows at full health");
				d[0].setHealth(300.0F * 0.5F);
			});
			later(context, 94, () -> {
				context.assertTrue(d[0].heartOut(), "the heart does not show with half the devil gone");
				// Look straight at it from the corridor.
				Vec3d eye = p.getEyePos();
				Vec3d to = d[0].heartAt().subtract(eye);
				float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
				p.updatePositionAndAngles(p.getX(), p.getY(), p.getZ(), yaw, (float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z))));
				p.setHeadYaw(yaw);
				float before = d[0].getHealth();
				d[0].timeUntilRegen = 0;
				d[0].damage(p.getDamageSources().playerAttack(p), 4.0F);
				context.assertTrue(Math.abs(before - d[0].getHealth() - 12.0F) < 0.01F, "a hit on the heart did " + (before - d[0].getHealth()) + ", not 12");
				// Look at the floor.
				p.updatePositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 80.0F);
				p.setHeadYaw(p.getYaw());
				before = d[0].getHealth();
				d[0].timeUntilRegen = 0;
				d[0].damage(p.getDamageSources().playerAttack(p), 4.0F);
				context.assertTrue(Math.abs(before - d[0].getHealth() - 4.0F) < 0.01F, "a hit elsewhere did " + (before - d[0].getHealth()) + ", not 4");
				d[0].setHealth(300.0F);
			});
			later(context, 100, () -> {
				context.assertTrue(!d[0].heartOut(), "the heart stayed out after the flesh grew back");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** Chainsaw Man's saws cut it (the slash the attack key makes), a slash looking at its heart most of all. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_saws", tickLimit = 500)
	public void theSawsCutTheDevil(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 18);
		ServerPlayerEntity p = walker(context, h, "saw_hand", 29.0);
		armed(p, 5000.0);
		io.github.kortev.chainsawman.form.Heart.give(p);
		io.github.kortev.chainsawman.form.Blood.set(p, io.github.kortev.chainsawman.form.Blood.MAX);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		ready(context, h, () -> {
			io.github.kortev.chainsawman.form.Form.start(p);
			d[0] = fightAt(context, h);
			later(context, 100, () -> {
				context.assertTrue(io.github.kortev.chainsawman.form.Form.fighting(p), "the saws are not out");
				put(p, h, 29.2, 29.0, 6.5, -90.0F, 0.0F);
				p.setHeadYaw(-90.0F);
				d[0].setHealth(300.0F);
				float before = d[0].getHealth();
				boolean cut = io.github.kortev.chainsawman.form.Saw.slash(p, d[0].getId());
				context.assertTrue(cut, "the saws did not slash");
				context.assertTrue(d[0].getHealth() < before - 5.0F, "a slash took only " + (before - d[0].getHealth()) + " off the devil");
				// With the heart showing and the saws on it a slash does three times as much.
				d[0].setHealth(100.0F);
			});
			later(context, 104, () -> context.assertTrue(d[0].heartOut(), "the heart is hidden at a third of its health"));
			later(context, 120, () -> {
				Vec3d eye = p.getEyePos();
				Vec3d to = d[0].heartAt().subtract(eye);
				float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
				p.updatePositionAndAngles(p.getX(), p.getY(), p.getZ(), yaw, (float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z))));
				p.setHeadYaw(yaw);
				float before = d[0].getHealth();
				io.github.kortev.chainsawman.form.Saw.slash(p, d[0].getId());
				context.assertTrue(before - d[0].getHealth() >= 25.0F, "a slash on the heart took only " + (before - d[0].getHealth()));
				// A kill by the saws does not tear it apart: its body stays for its own collapse.
			});
			later(context, 140, () -> {
				d[0].setHealth(1.0F);
				io.github.kortev.chainsawman.form.Saw.slash(p, d[0].getId());
				context.assertTrue(d[0].isDead() && !d[0].isRemoved(), "the saws' kill removed the devil at once");
			});
			later(context, 170, () -> {
				context.assertTrue(!d[0].isRemoved(), "the dead devil was gone within 30 ticks");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** Killing it: it drops its flesh and blood, the loop breaks (and nobody gets a contract), the flesh and bleeding go, the lights die, and the devil is away for the respawn days. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_kill", tickLimit = 700)
	public void killingTheDevilBreaksTheLoopAndDropsLoot(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 15);
		ServerPlayerEntity p = walker(context, h, "slayer", 20.5);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		ready(context, h, () -> {
			h.run.laps.put(p.getUuid(), 2);
			d[0] = fightAt(context, h);
			later(context, 130, () -> {
				context.assertTrue(h.run.fight, "no fight to win");
				int bled = 0;
				for (int x = 24; x <= 36; x++) {
					for (int y = 29; y <= 31; y++) {
						bled += !HotelPlan.get().at(x, y, 8).isAir() && !(HotelPlan.get().at(x, y, 8).getBlock() instanceof DoorBlock) && at(context, h, x, y, 8) != HotelPlan.get().at(x, y, 8) ? 1 : 0;
					}
				}
				context.assertTrue(bled >= 3, "only " + bled + " blocks bled");
				d[0].damage(p.getDamageSources().playerAttack(p), 10000.0F);
				context.assertTrue(d[0].isDead(), "the devil did not die");
				// It collapses for 70 ticks before it is gone, not at once.
				int diedAt = (int) context.getTick();
				for (int t = 1; t < 140; t++) {
					int tt = t;
					context.runAtTick(context.getTick() + t, () -> {
						if (tt < 65 && d[0].isRemoved()) {
							context.throwGameTestException("the dead devil was gone after " + tt + " ticks (death time " + d[0].deathTime + ")");
						}
					});
				}
			});
			later(context, 140, () -> {
				context.assertTrue(!h.run.fight && h.away && h.awayUntilDay == Hotel.day(context.getWorld()) + 3, "the hotel does not know the devil is dead");
				context.assertTrue(!io.github.kortev.chainsawman.contract.Contracts.has(p, io.github.kortev.chainsawman.contract.Contract.ETERNITY), "killing it gave a contract");
				context.assertTrue(h.run.growths.isEmpty() && h.run.flesh == 0, "the flesh was left");
				// The loop is gone: walking off the end is walking off the end.
				put(p, h, 25.6, 29.0, 6.5, 90.0F, 0.0F);
			});
			later(context, 220, () -> {
				int flesh = 0;
				int blood = 0;
				for (net.minecraft.entity.ItemEntity e : context.getWorld().getEntitiesByType(net.minecraft.entity.EntityType.ITEM, e -> e.getStack().getItem() == io.github.kortev.chainsawman.ChainsawMan.ETERNITY_FLESH
						|| e.getStack().getItem() == io.github.kortev.chainsawman.ChainsawMan.ETERNITY_BLOOD)) {
					if (HotelBuilder.local(h.origin, h.rot, e.getPos()).y > 20.0) {
						if (e.getStack().getItem() == io.github.kortev.chainsawman.ChainsawMan.ETERNITY_FLESH) {
							flesh += e.getStack().getCount();
						} else {
							blood += e.getStack().getCount();
						}
					}
				}
				context.assertTrue(flesh == Hotel.FLESH_DROP && blood == Hotel.BLOOD_DROP, "the loot is " + flesh + " flesh and " + blood + " blood");
			});
			later(context, 146, () -> {
				context.assertTrue(Math.abs(local(h, p).x - 25.6) < 0.05, "the loop was still there after the kill: " + local(h, p));
				context.assertTrue(h.run.night, "the hotel was dark the instant it died, with no lights dying");
			});
			later(context, 330, () -> {
				context.assertTrue(!h.run.night, "the hotel never went dark after the kill");
				context.assertTrue(at(context, h, 21, 4, 6).get(net.minecraft.block.RedstoneLampBlock.LIT) == false, "a lamp is still lit");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** Eternity flesh restores a lot of hunger and makes you sick; Eternity blood fills Chainsaw Man's blood bar. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_loot", tickLimit = 120)
	public void eternityFleshAndBlood(TestContext context) {
		ServerPlayerEntity p = ForeverGameTests.player(context, "eater");
		try {
			net.minecraft.item.ItemStack flesh = new net.minecraft.item.ItemStack(io.github.kortev.chainsawman.ChainsawMan.ETERNITY_FLESH);
			net.minecraft.component.type.FoodComponent food = flesh.get(net.minecraft.component.DataComponentTypes.FOOD);
			context.assertTrue(food != null && food.nutrition() >= 8, "the flesh is not a lot of food");
			p.getHungerManager().setFoodLevel(2);
			p.getHungerManager().setSaturationLevel(0.0F);
			p.eatFood(context.getWorld(), flesh.copy(), food);
			context.assertTrue(p.getHungerManager().getFoodLevel() >= 12, "eating it gave only " + p.getHungerManager().getFoodLevel() + " hunger");
			context.assertTrue(p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.NAUSEA), "eating it did not make you sick");
			io.github.kortev.chainsawman.form.Blood.set(p, 7);
			net.minecraft.item.ItemStack blood = new net.minecraft.item.ItemStack(io.github.kortev.chainsawman.ChainsawMan.ETERNITY_BLOOD);
			context.assertTrue(blood.getUseAction() == net.minecraft.util.UseAction.DRINK, "the blood is not drunk");
			net.minecraft.item.ItemStack left = blood.finishUsing(context.getWorld(), p);
			context.assertTrue(io.github.kortev.chainsawman.form.Blood.get(p) == io.github.kortev.chainsawman.form.Blood.MAX, "drinking it left the blood at " + io.github.kortev.chainsawman.form.Blood.get(p));
			context.assertTrue(left.getItem() == net.minecraft.item.Items.GLASS_BOTTLE || left.isEmpty(), "the bottle is not given back");
		} finally {
			ForeverGameTests.leave(context, p);
		}
		context.complete();
	}

	/** The devil is back after devilRespawnDays: the hotel stays dark while it is away, wakes when it is back, and each hotel's devil is its own. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_back", tickLimit = 700)
	public void theDevilComesBackAfterTheRespawnDays(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 16);
		HomeBlockEntity other = hotel(context, 2, 17);
		ServerWorld world = context.getWorld();
		ServerPlayerEntity p = walker(context, h, "returner", 20.5);
		ServerPlayerEntity q = walker(context, other, "elsewhere", 20.5);
		long[] day = new long[1];
		ready(context, h, () -> {
			context.assertTrue(h.run.night, "the hotel is not awake");
			day[0] = Hotel.day(world);
			io.github.kortev.chainsawman.hotel.EternityDevilEntity d = fightAt(context, h);
			context.assertTrue(d != null, "no devil");
			later(context, 100, () -> {
				io.github.kortev.chainsawman.hotel.EternityDevilEntity dd = (io.github.kortev.chainsawman.hotel.EternityDevilEntity) devilOf(context, h);
				dd.damage(p.getDamageSources().playerAttack(p), 10000.0F);
			});
			later(context, 110, () -> {
				context.assertTrue(h.away && h.awayUntilDay == day[0] + 3, "the dead devil is not away for 3 days");
				context.assertTrue(!other.away, "another hotel's devil went away with it");
				// Two days on, at night: still away, and the hotel does not wake.
				world.setTimeOfDay((day[0] + 2) * 24000L + 14000L);
			});
			later(context, 280, () -> {
				context.assertTrue(h.away && !h.run.night, "the devil came back early (or the hotel woke while it was away)");
				// The third day at night: it is back, and the hotel wakes.
				world.setTimeOfDay((day[0] + 3) * 24000L + 14000L);
			});
			later(context, 320, () -> {
				context.assertTrue(!h.away, "the devil did not come back after 3 days");
				context.assertTrue(h.run.night, "the hotel did not wake with its devil back");
				tidy(context, h, p);
				forced(context, other.origin, false);
				ForeverGameTests.leave(context, q);
				context.complete();
			});
		});
	}
}
