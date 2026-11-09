package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.hotel.Hotel;
import io.github.kortev.chainsawman.hotel.HomeBlockEntity;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import io.github.kortev.chainsawman.hotel.HotelPlan;
import io.github.kortev.chainsawman.hotel.HotelRules;
import java.util.ArrayList;
import java.util.List;
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

	/**
	 * Takes away the homes of any hotel an earlier turn put at this origin (a hotel's own placing clears everything else it needs; clearing the
	 * whole neighbourhood of every test was a heavy load on the others running at the same time).
	 */
	static void ground(TestContext context, BlockPos origin) {
		ServerWorld world = context.getWorld();
		for (int rot = 0; rot < 4; rot++) {
			world.setBlockState(HotelBuilder.world(origin, rot, HotelPlan.HOME_X, HotelPlan.HOME_Y, HotelPlan.HOME_Z), Blocks.STONE.getDefaultState(), 2);
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
				for (io.github.kortev.chainsawman.hotel.Squad.Note n : io.github.kortev.chainsawman.hotel.Squad.NOTES) {
					special.add(io.github.kortev.chainsawman.hotel.Squad.notePos(origin, rot, n));
				}
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
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_night", tickLimit = 700)
	public void hotelWakesAtNightAndSleepsByDay(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 1);
		ServerWorld world = context.getWorld();
		ServerPlayerEntity p = ForeverGameTests.player(context, "night_walker");
		world.setTimeOfDay(3000);
		put(p, h, 20.5, 1.0, 3.5, 0.0F, 0.0F);
		ready(context, h, () -> {
			later(context, 25, () -> {
				assertDead(context, h, "day");
				world.setTimeOfDay(14000);
			});
			later(context, 55, () -> {
				assertNight(context, h);
				world.setTimeOfDay(3000);
			});
			later(context, 85, () -> {
				assertDead(context, h, "dawn");
				put(p, h, 400.0, 1.0, 3.5, 0.0F, 0.0F);
				world.setTimeOfDay(14000);
			});
			later(context, 115, () -> {
				assertDead(context, h, "night with nobody near");
				leaveHotel(context, h, p);
				world.setTimeOfDay(3000);
				context.complete();
			});
		});
	}

	/** The wrong things heard: the lobby hums, floor 6's door breathes (it opens and shuts by itself), floor 7's phone rings. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_floors", tickLimit = 800)
	public void eachFloorIsStranger(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 2);
		ServerWorld world = context.getWorld();
		ServerPlayerEntity p = ForeverGameTests.player(context, "climber");
		world.setTimeOfDay(14000);
		put(p, h, 28.5, 1.0, 3.5, 0.0F, 0.0F);
		boolean[] opens = new boolean[2];
		ready(context, h, () -> {
			later(context, 45, () -> {
				context.assertTrue(h.run.humAt > 0, "the lobby did not hum: night " + h.run.night + " player " + p.getPos() + " origin " + h.origin + " local " + local(h, p));
				put(p, h, 20.5, 21.0, 6.5, 0.0F, 0.0F);
				HotelPlan.P d = HotelPlan.get().breathDoor;
				opens[0] = at(context, h, d.x(), d.y(), d.z()).get(DoorBlock.OPEN);
			});
			later(context, 130, () -> {
				HotelPlan.P d = HotelPlan.get().breathDoor;
				opens[1] = at(context, h, d.x(), d.y(), d.z()).get(DoorBlock.OPEN);
				context.assertTrue(h.run.breathAt > 0, "the door on floor 6 did not breathe");
				context.assertTrue(opens[1] == h.run.breathOpen, "the breathing door is not where the hotel says it is");
				put(p, h, 20.5, 25.0, 6.5, 0.0F, 0.0F);
			});
			later(context, 210, () -> {
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
		});
	}

	/**
	 * The loop: past the corridor's window one way or the other a player is put back, at every turn of the hotel; their place across the
	 * corridor and height, their look and their speed are kept; each time is a lap. A player who has taken the deal is not put back; nor is
	 * one on another floor; and down the stairs from the loop floor is back at the corridor's start.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_loop", tickLimit = 900)
	public void theLoopPutsYouBackSeamlesslyAtEveryTurn(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerPlayerEntity p = ForeverGameTests.player(context, "walker");
		HomeBlockEntity[] hh = new HomeBlockEntity[1];
		HomeBlockEntity first = hotel(context, 0, 3);
		ready(context, first, () -> {
			for (int rot = 0; rot < 4; rot++) {
				int base = 1 + rot * 80;
				int turn = rot;
				later(context, base, () -> {
					hh[0] = hotel(context, turn, 3);
					world.setTimeOfDay(14000);
					put(p, hh[0], 24.0, 29.0, 6.5, 40.0F, 12.0F);
				});
				later(context, base + 30, () -> {
					HomeBlockEntity h = hh[0];
					context.assertTrue(h.run.night, "turn " + turn + ": the hotel is asleep");
					// Out of the east end of the window.
					put(p, h, 25.3, 29.0, 6.1, 33.0F, 12.0F);
					Vec3d v = HotelBuilder.world(BlockPos.ORIGIN, turn, 0.21, 0.0, 0.05);
					p.setVelocity(v);
				});
				later(context, base + 33, () -> {
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
				later(context, base + 36, () -> {
					HomeBlockEntity h = hh[0];
					Vec3d lp = local(h, p);
					context.assertTrue(Math.abs(lp.x - 24.8) < 0.02 && Math.abs(lp.z - 6.1) < 0.02, "turn " + turn + ": west end put back at " + lp);
					context.assertTrue(h.run.laps.getOrDefault(p.getUuid(), 0) == 2, "turn " + turn + ": the second was not a lap");
					// While the devil fights the loop is suspended: a player is let walk off.
					h.run.fight = true;
					h.run.fightAt = world.getTime() + 100;
					put(p, h, 25.5, 29.0, 6.1, 33.0F, 12.0F);
				});
				later(context, base + 40, () -> {
					HomeBlockEntity h = hh[0];
					context.assertTrue(Math.abs(local(h, p).x - 25.5) < 0.02, "turn " + turn + ": a player was put back during the fight");
					h.run.fight = false;
					// On the floor below the corridor is a corridor and nothing more.
					put(p, h, 25.5, 25.0, 6.1, 33.0F, 12.0F);
				});
				later(context, base + 44, () -> {
					HomeBlockEntity h = hh[0];
					context.assertTrue(Math.abs(local(h, p).x - 25.5) < 0.02, "turn " + turn + ": a player on floor 7 was put back");
					// Up on the loop floor in the stairwell, then down the stairs.
					put(p, h, 17.5, 29.0, 3.5, 33.0F, 12.0F);
				});
				later(context, base + 48, () -> {
					HomeBlockEntity h = hh[0];
					put(p, h, 16.5, 27.4, 3.5, 33.0F, 12.0F);
				});
				later(context, base + 52, () -> {
					HomeBlockEntity h = hh[0];
					Vec3d lp = local(h, p);
					context.assertTrue(Math.abs(lp.x - 14.0) < 0.05 && Math.abs(lp.y - 29.0) < 0.05 && Math.abs(lp.z - 6.5) < 0.05, "turn " + turn + ": down the stairs ended at " + lp + ", not the corridor's start");
					context.assertTrue(p.getYaw() == HotelBuilder.alongYaw(turn), "turn " + turn + ": the stairs left you looking " + p.getYaw());
					context.assertTrue(h.run.laps.getOrDefault(p.getUuid(), 0) == 3, "turn " + turn + ": the stairs were not a lap");
				});
				later(context, base + 55, () -> {
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
				later(context, base + 56, () -> {
					// Day comes while nobody is on the loop floor: the flesh goes with the night.
					HomeBlockEntity h = hh[0];
					put(p, h, 400.0, 1.0, 3.5, 0.0F, 0.0F);
					world.setTimeOfDay(3000);
				});
				later(context, base + 72, () -> {
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
		});
	}

	// ---- The seal, the hunt, the squad, the beg, the fight, the loot and the devil coming back.

	/** A hotel awake at night with {@code name} on its loop floor, at {@code x} along the corridor. */
	private static ServerPlayerEntity walker(TestContext context, HomeBlockEntity h, String name, double x) {
		ServerPlayerEntity p = ForeverGameTests.player(context, name);
		// (The hotel's mad hunter attacks anyone on sight: the players here can take it.)
		armed(p, 2000.0);
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
		context.getWorld().getGameRules().get(HotelRules.ONE_CONTRACT_PER_DEVIL).set(false, context.getWorld().getServer());
	}

	/** The devil is beaten (with nobody taking its deal) and dies: what the fight comes to. */
	private static void slay(TestContext context, HomeBlockEntity h, io.github.kortev.chainsawman.hotel.EternityDevilEntity d, ServerPlayerEntity by) {
		d.forgo();
		d.damage(by.getDamageSources().playerAttack(by), 10000.0F);
	}

	/** Nothing is offered in the loop any more: not after laps, not after a minute (the devil has no reason to bargain with someone it holds). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_nooffer", tickLimit = 1500)
	public void nothingIsOfferedInTheLoop(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 4);
		ServerPlayerEntity p = walker(context, h, "long_walker", 20.5);
		ready(context, h, () -> {
			later(context, 25, () -> h.run.laps.put(p.getUuid(), 6));
			later(context, 1340, () -> {
				context.assertTrue(h.run.sealed && h.run.inside.contains(p.getUuid()), "the loop did not hold them");
				context.assertTrue(!h.run.fight && h.run.devil == null, "after laps and a long minute something began: a fight or a devil");
				context.assertTrue(h.run.beg == null, "a contract was offered in the loop");
				context.assertTrue(!h.away && h.holder == null, "the deal was taken, or the devil left");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/**
	 * The first player on the loop floor closes the loop: everyone already in the building is inside (friends still climbing are not thrown out
	 * and join the loop when they arrive, the hunt, the squad and the beg include them), the entrance is shut, and anyone who is outside, or comes
	 * in after, is kept out.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_seal", tickLimit = 900)
	public void theHotelSealsButFriendsStillClimbingJoin(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 5);
		ServerPlayerEntity a = ForeverGameTests.player(context, "first_up");
		ServerPlayerEntity c = ForeverGameTests.player(context, "still_climbing");
		ServerPlayerEntity d = ForeverGameTests.player(context, "late_comer");
		armed(a, 2000.0);
		armed(c, 2000.0);
		context.getWorld().setTimeOfDay(14000);
		put(a, h, 20.5, 21.0, 6.5, 90.0F, 0.0F);
		put(c, h, 20.5, 17.0, 6.5, 90.0F, 0.0F);
		put(d, h, 400.0, 1.0, 3.5, 0.0F, 0.0F);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] dev = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		ready(context, h, () -> {
			later(context, 15, () -> {
				context.assertTrue(h.run.night && !h.run.sealed, "sealed before anyone reached the loop floor");
				context.assertTrue(at(context, h, 27, 1, 0).get(DoorBlock.OPEN), "the entrance is not open at night");
				put(a, h, 20.5, 29.0, 6.5, 90.0F, 0.0F);
			});
			later(context, 25, () -> {
				context.assertTrue(h.run.sealed, "reaching the loop floor did not seal the hotel");
				context.assertTrue(h.run.inside.contains(a.getUuid()) && h.run.inside.contains(c.getUuid()), "the one still climbing is not inside");
				context.assertTrue(local(h, c).z > 0.0 && local(h, c).y < 20.0, "the one still climbing was put out: " + local(h, c));
				context.assertTrue(at(context, h, 27, 1, 0).get(DoorBlock.OPEN) == false, "the entrance stayed open");
				// Someone arriving now, at the lobby, is kept out.
				put(d, h, 20.5, 1.0, 3.5, 0.0F, 0.0F);
			});
			later(context, 32, () -> {
				context.assertTrue(local(h, d).z < 0.0 && !h.run.inside.contains(d.getUuid()), "an outsider got into a sealed hotel: " + local(h, d));
				// The climber arrives on the loop floor and is looped like the first.
				put(c, h, 25.3, 29.0, 6.1, 33.0F, 12.0F);
			});
			later(context, 38, () -> {
				context.assertTrue(Math.abs(local(h, c).x - 13.3) < 0.05, "the climber who arrived was not looped: " + local(h, c));
				context.assertTrue(h.run.inside.contains(c.getUuid()), "the climber is not inside");
				dev[0] = fightAt(context, h);
				noSquad(context, h);
			});
			later(context, 140, () -> toTheLine(a, dev[0]));
			later(context, 146, () -> {
				context.assertTrue(h.run.beg != null && h.run.beg.players.contains(a.getUuid()) && h.run.beg.players.contains(c.getUuid()), "the climber was not offered the beg screen");
				leaveHotel(context, h, a, c, d);
				context.getWorld().setTimeOfDay(3000);
				context.complete();
			});
		});
	}

	/** Someone inside who dies respawns outside and cannot get back in until the hotel opens; it opens when nobody is inside. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_die", tickLimit = 800)
	public void aPlayerWhoDiesInsideCannotGetBackIn(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 6);
		ServerPlayerEntity a = walker(context, h, "dies_inside", 20.5);
		ServerPlayerEntity b = walker(context, h, "stays_inside", 22.5);
		ServerPlayerEntity[] back = new ServerPlayerEntity[1];
		ready(context, h, () -> {
			later(context, 20, () -> {
				context.assertTrue(h.run.sealed && h.run.inside.size() == 2, "not sealed with two inside");
				// They drop what they carry where they fall.
				a.getInventory().setStack(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 3));
				a.getInventory().dropAll();
				a.setHealth(0.0F);
				a.onDeath(context.getWorld().getDamageSources().generic());
			});
			later(context, 24, () -> {
				context.assertTrue(!h.run.inside.contains(a.getUuid()), "the dead are still inside");
				context.assertTrue(h.run.sealed && h.run.inside.contains(b.getUuid()), "the hotel opened with someone still inside");
				int diamonds = 0;
				for (net.minecraft.entity.ItemEntity e : context.getWorld().getEntitiesByType(net.minecraft.entity.EntityType.ITEM, e -> e.getStack().getItem() == net.minecraft.item.Items.DIAMOND)) {
					if (HotelBuilder.local(h.origin, h.rot, e.getPos()).y > 20.0) {
						diamonds += e.getStack().getCount();
					}
				}
				context.assertTrue(diamonds == 3, "the dropped items did not stay where they fell: " + diamonds);
				// (The respawn: the same player alive again, outside, and now trying the stairs and doors again.)
				a.setHealth(20.0F);
				back[0] = a;
				put(back[0], h, 20.5, 29.0, 6.5, 90.0F, 0.0F);
			});
			later(context, 32, () -> {
				context.assertTrue(local(h, back[0]).z < 0.0 && !h.run.inside.contains(back[0].getUuid()), "someone who died came back in: " + local(h, back[0]));
				// The last one inside leaves (is gone from the hotel): it opens.
				put(b, h, 400.0, 1.0, 3.5, 0.0F, 0.0F);
			});
			later(context, 60, () -> {
				context.assertTrue(!h.run.sealed && h.run.inside.isEmpty(), "the hotel stayed sealed with nobody inside");
				put(back[0], h, 27.5, 1.0, 3.5, 0.0F, 0.0F);
			});
			later(context, 70, () -> {
				context.assertTrue(local(h, back[0]).z > 0.0, "the hotel did not let them in once it opened");
				leaveHotel(context, h, back[0], b);
				context.getWorld().setTimeOfDay(3000);
				context.complete();
			});
		});
	}

	/** Wherever the fight starts the walls bleed and the devil grows out of them, 300 health and the size it was. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hotel_refuse", tickLimit = 900)
	public void theFightMakesTheWallsBleedAndTheDevilGrows(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 8);
		ServerPlayerEntity p = walker(context, h, "refuser", 20.5);
		ready(context, h, () -> {
			later(context, 25, () -> context.assertTrue(!h.run.fight, "a fight before anything was done"));
			later(context, 30, () -> fightAt(context, h));
			later(context, 40, () -> {
				context.assertTrue(h.run.fight && h.run.devil != null, "no devil grew");
				context.assertTrue(!h.away, "the devil is away");
			});
			later(context, 120, () -> {
				// The wallpaper at the corridor's end has bled.
				int changed = 0;
				for (int x = 14; x <= 22; x++) {
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
				context.assertTrue(Math.abs(lp.x - 37.0) < 0.05 && Math.abs(lp.y - 29.0) < 0.05 && Math.abs(lp.z - 6.5) < 0.05, "the devil is at " + lp + ", not the corridor's end");
				// Its hitbox is its whole front: 9.4 across the great mouth's axis, 7 high, 3.9 out from its back plane.
				net.minecraft.util.math.Box box = d.getBoundingBox();
				context.assertTrue(Math.abs(box.getLengthY() - 7.0) < 0.01 && Math.abs(Math.max(box.getLengthX(), box.getLengthZ()) - 9.4) < 0.01 && Math.abs(Math.min(box.getLengthX(), box.getLengthZ()) - 3.9) < 0.01,
						"its hitbox is " + box.getLengthX() + " x " + box.getLengthY() + " x " + box.getLengthZ());
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	// ---- The hunt.

	private static BlockPos spotPos(HomeBlockEntity h) {
		io.github.kortev.chainsawman.hotel.Hunt u = h.run.hunt;
		return HotelBuilder.world(h.origin, h.rot, u.sx, u.sy, u.sz);
	}

	/** A wall block of the loop floor (not a door, not air) {@code along} blocks along the same wall from the spot, on whichever side has one. */
	private static BlockPos wallAlong(HomeBlockEntity h, int along) {
		io.github.kortev.chainsawman.hotel.Hunt u = h.run.hunt;
		for (int sign : new int[] {-1, 1}) {
			int x = u.sx + sign * along;
			if (x >= 1 && x <= HotelPlan.CORR_X1 && HotelPlan.get().at(x, u.sy, u.sz).isOpaque() && !(HotelPlan.get().at(x, u.sy, u.sz).getBlock() instanceof DoorBlock)) {
				return HotelBuilder.world(h.origin, h.rot, x, u.sy, u.sz);
			}
		}
		throw new IllegalStateException("no wall block " + along + " along from the spot " + u.sx + "," + u.sy + "," + u.sz);
	}

	/** Stands {@code p} in the corridor at the wall of the spot, facing it. */
	private static void standAt(ServerPlayerEntity p, HomeBlockEntity h, double x, int wallZ) {
		put(p, h, x, 29.0, wallZ == 4 ? 5.6 : 7.4, wallZ == 4 ? 0.0F : 180.0F, 0.0F);
	}

	/**
	 * When the loop closes the devil hides at a random spot in the corridor's walls, and shows itself: its heart beats (a sound at the spot),
	 * eyes open in the wallpaper near it and shut when someone looks straight at one, and the wall at the spot breathes.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hunt_signs", tickLimit = 800)
	public void theDevilHidesInTheWallsAndShowsItself(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 9);
		ServerPlayerEntity p = walker(context, h, "searcher", 20.5);
		ready(context, h, () -> {
			later(context, 20, () -> {
				io.github.kortev.chainsawman.hotel.Hunt u = h.run.hunt;
				context.assertTrue(h.run.sealed && u != null, "the devil did not hide when the loop closed");
				context.assertTrue(u.sx >= 15 && u.sx <= 23 && (u.sz == 4 || u.sz == 8) && u.sy >= 29 && u.sy <= 31, "the spot is not in the corridor's walls: " + u.sx + "," + u.sy + "," + u.sz);
				context.assertTrue(HotelPlan.get().at(u.sx, u.sy, u.sz).isOpaque(), "the spot is not a wall block");
				context.assertTrue(u.beats >= 1, "no heartbeat");
				context.assertTrue(u.eyes.size() >= 10, "only " + u.eyes.size() + " eyes in the wallpaper");
				context.assertTrue(u.breath != null && context.getWorld().getEntity(u.breath) != null, "the wall does not breathe");
				// Face away from the wall: the eyes near the spot open, the far ones stay shut.
				standAt(p, h, u.sx + 0.5, u.sz);
				p.updatePositionAndAngles(p.getX(), p.getY(), p.getZ(), u.sz == 4 ? 180.0F : 0.0F, 0.0F);
			});
			later(context, 30, () -> {
				io.github.kortev.chainsawman.hotel.Hunt u = h.run.hunt;
				Vec3d face = u.face(h);
				int near = 0;
				int far = 0;
				io.github.kortev.chainsawman.hotel.FleshGrowthEntity nearOpen = null;
				for (java.util.UUID id : u.eyes) {
					if (context.getWorld().getEntity(id) instanceof io.github.kortev.chainsawman.hotel.FleshGrowthEntity eye) {
						if (eye.getPos().distanceTo(face) <= io.github.kortev.chainsawman.hotel.Hunt.EYE_RANGE) {
							if (eye.isOpen()) {
								near++;
								nearOpen = eye;
							}
						} else if (eye.isOpen()) {
							far++;
						}
					}
				}
				context.assertTrue(near >= 1, "no eye opened near the devil");
				context.assertTrue(far == 0, far + " eyes opened far from the devil");
				// Look straight at the open one: it shuts.
				Vec3d to = nearOpen.getPos().subtract(p.getEyePos());
				float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
				float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z)));
				p.updatePositionAndAngles(p.getX(), p.getY(), p.getZ(), yaw, pitch);
				p.setHeadYaw(yaw);
				eyeAt[0] = nearOpen.getUuid();
			});
			later(context, 40, () -> {
				context.assertTrue(context.getWorld().getEntity(eyeAt[0]) instanceof io.github.kortev.chainsawman.hotel.FleshGrowthEntity e && !e.isOpen(), "the eye stayed open under a player's stare");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	private static final java.util.UUID[] eyeAt = new java.util.UUID[1];

	/** Cutting a wall block of the loop floor: near the devil it bleeds (and takes a little off it), away from it a dull knock and nothing. The left click does it too. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hunt_cut", tickLimit = 800)
	public void cuttingNearTheDevilBleedsAndAwayFromItDoesNot(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 10);
		ServerPlayerEntity p = walker(context, h, "cutter", 20.5);
		ready(context, h, () -> {
			later(context, 20, () -> {
				ServerWorld world = context.getWorld();
				io.github.kortev.chainsawman.hotel.Hunt u = h.run.hunt;
				context.assertTrue(u != null, "no hunt");
				float before = u.health;
				// Away from it: a dull knock, no blood.
				io.github.kortev.chainsawman.hotel.Hunt.cut(world, h, wallAlong(h, 7), p);
				context.assertTrue(u.dull == 1 && u.bled == 0 && u.health == before, "a cut far from it bled: dull " + u.dull + " bled " + u.bled);
				// Through the left click on the other wall, right across from it (more than two blocks if it is on this one).
				net.minecraft.util.ActionResult res = net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.invoker().interact(p, world, net.minecraft.util.Hand.MAIN_HAND, wallAlong(h, 6), net.minecraft.util.math.Direction.NORTH);
				context.assertTrue(res == net.minecraft.util.ActionResult.FAIL, "a sealed wall may be broken");
				context.assertTrue(u.dull == 2 && u.bled == 0, "the left click did not cut the wall: dull " + u.dull);
				// At it: bleeds, takes health.
				int sx = u.sx;
				BlockPos spot = spotPos(h);
				io.github.kortev.chainsawman.hotel.Hunt.cut(world, h, spot, p);
				context.assertTrue(u.bled == 1 && u.health == before - io.github.kortev.chainsawman.hotel.Hunt.BLEED_DAMAGE, "a cut at it did not bleed: bled " + u.bled + ", health " + u.health);
				// A block 2 along from the spot, in the same wall, bleeds too; the wall's own block stays a wall.
				context.assertTrue(world.getBlockState(spot).isOf(Blocks.RED_TERRACOTTA) || world.getBlockState(spot).isOf(Blocks.NETHER_WART_BLOCK), "the cut block did not go red");
			});
			later(context, 90, () -> {
				io.github.kortev.chainsawman.hotel.Hunt u = h.run.hunt;
				context.assertTrue(u != null && u.reds.isEmpty(), "the blood stayed on the wall");
				// It has moved since the cut: a block 2 away from its new spot bleeds, 3 away does not.
				BlockPos spot = spotPos(h);
				net.minecraft.util.math.Direction.Axis a = net.minecraft.util.math.Direction.Axis.X;
				int bled = u.bled;
				io.github.kortev.chainsawman.hotel.Hunt.cut(context.getWorld(), h, wallAlong(h, 3), p);
				context.assertTrue(u.bled == bled, "a cut 3 blocks from it bled");
				io.github.kortev.chainsawman.hotel.Hunt.cut(context.getWorld(), h, wallAlong(h, 2), p);
				context.assertTrue(u.bled == bled + 1, "a cut 2 blocks from it did not bleed");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/**
	 * It slips away when someone lingers by it and from the first cut at a spot, twice at most; after three bleeding cuts at the last spot it tears out
	 * of the wall and the fight starts with the health the cuts left it.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_hunt_move", tickLimit = 900)
	public void itMovesAtMostTwiceAndTearsOutAfterThreeBleedingCuts(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 11);
		ServerPlayerEntity p = walker(context, h, "stalker", 20.5);
		int[] was = new int[3];
		ready(context, h, () -> {
			later(context, 20, () -> {
				io.github.kortev.chainsawman.hotel.Hunt u = h.run.hunt;
				was[0] = u.sx * 10 + u.sz;
				// Linger at the wall by the spot: it moves after about two seconds.
				standAt(p, h, u.sx + 0.5, u.sz);
			});
			later(context, 20 + io.github.kortev.chainsawman.hotel.Hunt.LINGER + 10, () -> {
				io.github.kortev.chainsawman.hotel.Hunt u = h.run.hunt;
				context.assertTrue(u.moves == 1 && u.sx * 10 + u.sz != was[0], "it did not slip away from someone lingering at its wall (moves " + u.moves + ")");
				context.assertTrue(Math.abs(u.sx - was[0] / 10) >= 3, "it moved only " + Math.abs(u.sx - was[0] / 10));
				// The first cut at its new spot: it bleeds and slips away again.
				io.github.kortev.chainsawman.hotel.Hunt.cut(context.getWorld(), h, spotPos(h), p);
				context.assertTrue(u.bled == 1 && u.moves == 2 && u.cuts == 0, "the first cut at the new spot: bled " + u.bled + " moves " + u.moves + " cuts " + u.cuts);
				// Now it stays: lingering by it no longer moves it.
				standAt(p, h, u.sx + 0.5, u.sz);
				was[1] = u.sx * 10 + u.sz;
			});
			later(context, 20 + io.github.kortev.chainsawman.hotel.Hunt.LINGER + 80, () -> {
				io.github.kortev.chainsawman.hotel.Hunt u = h.run.hunt;
				context.assertTrue(u != null && u.moves == 2 && u.sx * 10 + u.sz == was[1], "it moved a third time");
				// Three bleeding cuts: the first two it takes, the third and it tears out.
				io.github.kortev.chainsawman.hotel.Hunt.cut(context.getWorld(), h, spotPos(h), p);
				io.github.kortev.chainsawman.hotel.Hunt.cut(context.getWorld(), h, spotPos(h), p);
				context.assertTrue(h.run.hunt != null && !h.run.fight && u.cuts == 2 && u.moves == 2, "it tore out early or moved: cuts " + u.cuts + " moves " + u.moves);
				io.github.kortev.chainsawman.hotel.Hunt.cut(context.getWorld(), h, spotPos(h), p);
				context.assertTrue(h.run.hunt == null && h.run.fight && h.run.devil != null, "three bleeding cuts did not tear it out and start the fight");
				context.assertTrue(u.eyes.stream().noneMatch(id -> context.getWorld().getEntity(id) != null), "the eyes stayed after it tore out");
				was[2] = u.sx;
			});
			later(context, 20 + io.github.kortev.chainsawman.hotel.Hunt.LINGER + 90, () -> {
				io.github.kortev.chainsawman.hotel.EternityDevilEntity d = (io.github.kortev.chainsawman.hotel.EternityDevilEntity) devilOf(context, h);
				context.assertTrue(d != null, "no devil");
				Vec3d lp = HotelBuilder.local(h.origin, h.rot, d.getPos());
				// (Cut anywhere in the walls, it comes out at the corridor's end, where the arena opens.)
				context.assertTrue(Math.abs(lp.x - 37.0) < 0.2 && io.github.kortev.chainsawman.hotel.Arena.torn(h), "it tore out at " + lp.x + " and the arena is " + (io.github.kortev.chainsawman.hotel.Arena.torn(h) ? "open" : "shut") + " (cut at " + was[2] + ")");
				context.assertTrue(Math.abs(d.getHealth() - (300.0F - 4 * io.github.kortev.chainsawman.hotel.Hunt.BLEED_DAMAGE)) < 0.01F, "it came out with " + d.getHealth() + " health, not what four cuts left");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	// ---- The squad.

	private static java.util.List<io.github.kortev.chainsawman.hotel.SquadMemberEntity> squadOf(TestContext context, HomeBlockEntity h) {
		return io.github.kortev.chainsawman.hotel.Squad.members(context.getWorld(), h);
	}

	private static io.github.kortev.chainsawman.hotel.SquadMemberEntity hungry(TestContext context, HomeBlockEntity h, boolean mad, int skip) {
		for (io.github.kortev.chainsawman.hotel.SquadMemberEntity m : squadOf(context, h)) {
			if (m.isMad() == mad && m.state() != io.github.kortev.chainsawman.hotel.SquadMemberEntity.FOLLOWING && skip-- <= 0) {
				return m;
			}
		}
		return null;
	}

	private static void feed(ServerPlayerEntity p, io.github.kortev.chainsawman.hotel.SquadMemberEntity m) {
		p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.BREAD, 4));
		m.interactMob(p, net.minecraft.util.Hand.MAIN_HAND);
	}

	/** When the loop closes three hunters sit starving against the walls, one of them mad; fed, one gets up and follows the one who fed it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_squad_follow", tickLimit = 900)
	public void feedingASurvivorMakesThemFollow(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 30);
		ServerPlayerEntity p = walker(context, h, "feeder", 20.5);
		armed(p, 2000.0);
		io.github.kortev.chainsawman.hotel.SquadMemberEntity[] m = new io.github.kortev.chainsawman.hotel.SquadMemberEntity[1];
		double[] was = new double[1];
		ready(context, h, () -> {
			later(context, 20, () -> {
				java.util.List<io.github.kortev.chainsawman.hotel.SquadMemberEntity> squad = squadOf(context, h);
				context.assertTrue(squad.size() == 3, "the squad is " + squad.size() + " hunters, not 3");
				context.assertTrue(squad.stream().filter(io.github.kortev.chainsawman.hotel.SquadMemberEntity::isMad).count() == 1, "not exactly one is mad");
				context.assertTrue(squad.stream().map(e -> e.getName().getString()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("Yamada", "Mori", "Sato")), "the names are not Yamada, Mori and Sato");
				io.github.kortev.chainsawman.hotel.SquadMemberEntity yamada = squad.stream().filter(e -> e.variant() == 0).findFirst().orElseThrow();
				context.assertTrue(!yamada.note.isEmpty(), "Yamada carries no note");
				feed(p, yamada);
				context.assertTrue(yamada.note.isEmpty() && !context.getWorld().getEntitiesByType(net.minecraft.entity.EntityType.ITEM, e -> e.getStack().isOf(net.minecraft.item.Items.WRITTEN_BOOK)).isEmpty(), "Yamada did not drop his note when he was fed");
				m[0] = hungry(context, h, false, 0);
				context.assertTrue(m[0].sitting() && m[0].state() == io.github.kortev.chainsawman.hotel.SquadMemberEntity.HUNGRY, "a starving one is not sitting");
				// Not food: nothing. Food: they get up, the bread is eaten.
				p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.STICK));
				m[0].interactMob(p, net.minecraft.util.Hand.MAIN_HAND);
				context.assertTrue(m[0].state() == io.github.kortev.chainsawman.hotel.SquadMemberEntity.HUNGRY, "a stick fed them");
				feed(p, m[0]);
				context.assertTrue(m[0].state() == io.github.kortev.chainsawman.hotel.SquadMemberEntity.FOLLOWING && p.getUuid().equals(m[0].owner), "feeding did not make them follow");
				context.assertTrue(p.getMainHandStack().getCount() == 3, "the bread was not eaten");
				// The player walks off down the corridor; they come after.
				Vec3d mp = HotelBuilder.local(h.origin, h.rot, m[0].getPos());
				put(p, h, mp.x < 19.5 ? 24.6 : 13.6, 29.0, 6.5, 90.0F, 0.0F);
				was[0] = m[0].getPos().distanceTo(p.getPos());
			});
			later(context, 160, () -> {
				double now = m[0].getPos().distanceTo(p.getPos());
				context.assertTrue(now < 5.0 && now < was[0] - 1.0, "the one who was fed did not follow: " + was[0] + " then " + now);
				context.assertTrue(!m[0].sitting(), "they are still sitting");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** The mad one attacks players on sight until he is fed, or knocked low; then he calms and follows. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_squad_mad", tickLimit = 900)
	public void theMadOneAttacksUntilFedOrKnockedLow(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 31);
		ServerPlayerEntity p = walker(context, h, "target", 20.5);
		armed(p, 2000.0);
		io.github.kortev.chainsawman.hotel.SquadMemberEntity[] m = new io.github.kortev.chainsawman.hotel.SquadMemberEntity[1];
		float[] hp = new float[1];
		ready(context, h, () -> {
			later(context, 15, () -> {
				m[0] = hungry(context, h, true, 0);
				context.assertTrue(m[0] != null && !m[0].sitting(), "the mad one is not up and about");
				put(p, h, 21.0, 29.0, 6.5, 90.0F, 0.0F);
			});
			later(context, 120, () -> {
				context.assertTrue(p.getHealth() < 2000.0F, "the mad one never attacked a player in sight");
				// Fed: calm.
				feed(p, m[0]);
				context.assertTrue(!m[0].isMad() && m[0].state() == io.github.kortev.chainsawman.hotel.SquadMemberEntity.FOLLOWING, "feeding did not calm him");
				hp[0] = p.getHealth();
			});
			later(context, 200, () -> {
				context.assertTrue(p.getHealth() >= hp[0], "he attacked after he was fed");
				// Mad again: knocked low, he calms too and follows whoever did it.
				m[0].setMad(true);
				m[0].owner = null;
				m[0].setState(io.github.kortev.chainsawman.hotel.SquadMemberEntity.HUNGRY);
				m[0].setHealth(m[0].getMaxHealth());
				m[0].damage(p.getDamageSources().playerAttack(p), 4.0F);
				context.assertTrue(m[0].isMad() && m[0].state() == io.github.kortev.chainsawman.hotel.SquadMemberEntity.HUNGRY, "one light blow calmed him");
				m[0].timeUntilRegen = 0;
				m[0].damage(p.getDamageSources().playerAttack(p), 9.0F);
				context.assertTrue(!m[0].isMad() && m[0].state() == io.github.kortev.chainsawman.hotel.SquadMemberEntity.FOLLOWING && p.getUuid().equals(m[0].owner), "knocked low he did not calm and follow: mad " + m[0].isMad() + " state " + m[0].state());
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** They are downed, not killed, and fed again they get up. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_squad_down", tickLimit = 900)
	public void aDownedSurvivorCanBeFedAgain(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 32);
		ServerPlayerEntity p = walker(context, h, "medic", 20.5);
		armed(p, 2000.0);
		ready(context, h, () -> {
			later(context, 15, () -> {
				io.github.kortev.chainsawman.hotel.SquadMemberEntity m = hungry(context, h, false, 0);
				feed(p, m);
				m.damage(context.getWorld().getDamageSources().generic(), 500.0F);
				context.assertTrue(m.isAlive() && m.state() == io.github.kortev.chainsawman.hotel.SquadMemberEntity.DOWN, "a hit that would kill did not down them");
				context.assertTrue(!m.damage(context.getWorld().getDamageSources().generic(), 5.0F), "a downed one was hurt");
				feed(p, m);
				context.assertTrue(m.state() == io.github.kortev.chainsawman.hotel.SquadMemberEntity.FOLLOWING && m.getHealth() >= m.getMaxHealth() * 0.59F, "feeding a downed one did not get them up: " + m.state() + " " + m.getHealth());
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** In the fight the ones who were fed cut the devil with katanas. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_squad_fight", tickLimit = 900)
	public void theRescuedCutTheDevilInTheFight(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 33);
		ServerPlayerEntity p = walker(context, h, "leader", 20.5);
		armed(p, 3000.0);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		ready(context, h, () -> {
			later(context, 15, () -> {
				feed(p, hungry(context, h, false, 0));
				feed(p, hungry(context, h, false, 0));
				feed(p, hungry(context, h, true, 0));
				d[0] = fightAt(context, h);
			});
			later(context, 400, () -> {
				context.assertTrue(d[0].getHealth() < 300.0F - 10.0F, "the rescued did not hurt the devil: " + d[0].getHealth());
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** When the loop breaks the rescued walk out and leave, and every player inside is paid per survivor; those not fed stay and are not paid for. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_squad_reward", tickLimit = 1000)
	public void survivorsRewardEveryoneInsideWhenTheLoopBreaks(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 34);
		ServerPlayerEntity p = walker(context, h, "paid_one", 20.5);
		ServerPlayerEntity q = walker(context, h, "paid_two", 22.0);
		armed(p, 3000.0);
		armed(q, 3000.0);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		java.util.List<io.github.kortev.chainsawman.hotel.SquadMemberEntity> fed = new ArrayList<>();
		int[] xp = new int[2];
		ready(context, h, () -> {
			later(context, 15, () -> {
				for (int k = 0; k < 2; k++) {
					io.github.kortev.chainsawman.hotel.SquadMemberEntity m = hungry(context, h, false, 0);
					feed(p, m);
					fed.add(m);
				}
				xp[0] = p.totalExperience;
				xp[1] = q.totalExperience;
				d[0] = fightAt(context, h);
			});
			later(context, 120, () -> {
				slay(context, h, d[0], p);
			});
			later(context, 135, () -> {
				context.assertTrue(p.totalExperience - xp[0] == 2 * io.github.kortev.chainsawman.hotel.Squad.REWARD_XP && q.totalExperience - xp[1] == 2 * io.github.kortev.chainsawman.hotel.Squad.REWARD_XP,
						"each player inside should have 2 survivors' experience: " + (p.totalExperience - xp[0]) + " and " + (q.totalExperience - xp[1]));
				int gold = 0;
				for (int s = 0; s < q.getInventory().size(); s++) {
					if (q.getInventory().getStack(s).isOf(net.minecraft.item.Items.GOLD_INGOT)) {
						gold += q.getInventory().getStack(s).getCount();
					}
				}
				context.assertTrue(gold == 2 * io.github.kortev.chainsawman.hotel.Squad.REWARD_GOLD, "the second player got " + gold + " gold");
				context.assertTrue(h.run.saved == 2, "the hotel counted " + h.run.saved + " saved");
				context.assertTrue(fed.stream().allMatch(m -> m.state() == io.github.kortev.chainsawman.hotel.SquadMemberEntity.LEAVING), "the rescued are not leaving");
			});
			later(context, 700, () -> {
				context.assertTrue(fed.stream().allMatch(net.minecraft.entity.Entity::isRemoved), "the rescued never left");
				tidy(context, h, p, q);
				context.complete();
			});
		});
	}

	/** Eight notes: seven on lecterns on the bedside tables of rooms (never in a corridor), one carried by Yamada; a line each, the squad's story and its hints. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_squad_notes", tickLimit = 100)
	public void theSquadsNotesLieWhereTheyWouldLeaveThem(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 35);
		ServerWorld world = context.getWorld();
		try {
			context.assertTrue(io.github.kortev.chainsawman.hotel.Squad.NOTES.size() == 7, "not 7 notes in rooms");
			java.util.Set<Integer> floors = new HashSet<>();
			HotelPlan plan = HotelPlan.get();
			for (io.github.kortev.chainsawman.hotel.Squad.Note n : io.github.kortev.chainsawman.hotel.Squad.NOTES) {
				BlockPos at = io.github.kortev.chainsawman.hotel.Squad.notePos(h.origin, h.rot, n);
				context.assertTrue(world.getBlockState(at).isOf(Blocks.LECTERN), "no lectern for the note of floor " + (n.floor() + 1) + " at " + at);
				context.assertTrue(world.getBlockEntity(at) instanceof net.minecraft.block.entity.LecternBlockEntity l && l.getBook().get(net.minecraft.component.DataComponentTypes.WRITTEN_BOOK_CONTENT) != null, "no book on it");
				floors.add(n.floor());
				// In a room (not the corridor, z 5 to 7), on the bedside table.
				context.assertTrue(io.github.kortev.chainsawman.hotel.Squad.TABLE_Z > 8 && plan.at(n.x(), HotelPlan.floorY(n.floor()) + 1, io.github.kortev.chainsawman.hotel.Squad.TABLE_Z).getBlock() instanceof net.minecraft.block.SlabBlock, "the note of floor " + (n.floor() + 1) + " is not on a bedside table");
				context.assertTrue(n.text().length() < 110, "a note is long");
			}
			context.assertTrue(floors.size() == 7, "not one on each of the floors below the loop floor");
			String all = String.join(" ", io.github.kortev.chainsawman.hotel.Squad.NOTES.stream().map(io.github.kortev.chainsawman.hotel.Squad.Note::text).toList()) + " " + io.github.kortev.chainsawman.hotel.Squad.LAST.text();
			context.assertTrue(all.contains("bleeds when you cut it") && all.contains("watches from the walls") && all.contains("heartbeat is louder"), "the hints are missing");
		} finally {
			forced(context, h.origin, false);
		}
		context.complete();
	}

	// ---- The beg.

	/** The squad out of the way (their mad one hits players, which the fight tests below do not want to count). */
	private static void noSquad(TestContext context, HomeBlockEntity h) {
		for (io.github.kortev.chainsawman.hotel.SquadMemberEntity m : squadOf(context, h)) {
			m.discard();
		}
		h.run.squad.clear();
	}

	/** Takes the devil to the beg line (it begs at 15%): 60 health and a blow that would cut it to 30. */
	private static void toTheLine(ServerPlayerEntity by, io.github.kortev.chainsawman.hotel.EternityDevilEntity d) {
		d.setHealth(60.0F);
		d.timeUntilRegen = 0;
		d.damage(by.getDamageSources().playerAttack(by), 30.0F);
	}

	private static int itemsOf(TestContext context, HomeBlockEntity h, net.minecraft.item.Item item) {
		int n = 0;
		for (net.minecraft.entity.ItemEntity e : context.getWorld().getEntitiesByType(net.minecraft.entity.EntityType.ITEM, e -> e.getStack().getItem() == item)) {
			if (HotelBuilder.local(h.origin, h.rot, e.getPos()).y > 20.0) {
				n += e.getStack().getCount();
			}
		}
		return n;
	}

	/** At 15% it stops attacking, cowers and begs; a blow that would take it past the line stops there; and it cannot be hurt while it begs. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_beg_line", tickLimit = 900)
	public void theDevilBegsAtFifteenPercentAndCannotBeHurtWhileItDoes(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 36);
		ServerPlayerEntity p = walker(context, h, "closer", 26.0);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		float[] hp = new float[1];
		ready(context, h, () -> {
			later(context, 15, () -> {
				noSquad(context, h);
				d[0] = fightAt(context, h);
			});
			later(context, 110, () -> {
				context.assertTrue(!d[0].begging && h.run.beg == null, "it begged before it was hurt");
				toTheLine(p, d[0]);
				context.assertTrue(Math.abs(d[0].getHealth() - 45.0F) < 0.05F, "the blow went past the beg line: " + d[0].getHealth());
			});
			later(context, 114, () -> {
				context.assertTrue(d[0].begging && h.run.beg != null && h.run.beg.players.contains(p.getUuid()), "it did not beg at 15%");
				d[0].timeUntilRegen = 0;
				context.assertTrue(!d[0].damage(p.getDamageSources().playerAttack(p), 100.0F) && Math.abs(d[0].getHealth() - 45.0F) < 0.05F, "it was hurt while it begged: " + d[0].getHealth());
				hp[0] = p.getHealth();
			});
			later(context, 190, () -> {
				context.assertTrue(p.getHealth() >= hp[0] && context.getWorld().getEntitiesByType(io.github.kortev.chainsawman.ChainsawMan.FLESH_PATCH, e -> true).isEmpty(), "it kept attacking while it begged");
				context.assertTrue(d[0].begging && !d[0].heartOut() == !d[0].heartOut(), "");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** Somebody accepts: each accepter holds the contract, the devil tears off part of itself (flesh and blood for everyone) and sinks away for the respawn days; the hotel opens. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_beg_accept", tickLimit = 900)
	public void acceptingTheBegGivesTheContractAndSparesIt(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 37);
		ServerPlayerEntity a = walker(context, h, "takes_it", 26.0);
		ServerPlayerEntity b = walker(context, h, "leaves_it", 24.0);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		ready(context, h, () -> {
			later(context, 15, () -> {
				noSquad(context, h);
				d[0] = fightAt(context, h);
			});
			later(context, 110, () -> toTheLine(a, d[0]));
			later(context, 116, () -> {
				context.assertTrue(h.run.beg != null && h.run.beg.players.size() == 2, "both are not in the offer");
				Hotel.answer(b, false);
				context.assertTrue(h.run.beg != null && !d[0].isDead(), "the beg ended before everyone answered");
				Hotel.answer(a, true);
				context.assertTrue(io.github.kortev.chainsawman.contract.Contracts.has(a, io.github.kortev.chainsawman.contract.Contract.ETERNITY), "accepting gave no contract");
				context.assertTrue(a.getUuid().equals(h.holder), "the hotel does not know who holds its contract");
			});
			later(context, 122, () -> {
				context.assertTrue(h.run.beg == null && d[0].isDead() && d[0].spared, "the devil was not spared when someone accepted");
				context.assertTrue(!io.github.kortev.chainsawman.contract.Contracts.has(b, io.github.kortev.chainsawman.contract.Contract.ETERNITY), "the one who refused got the contract");
				context.assertTrue(h.away && h.awayUntilDay == Hotel.day(context.getWorld()) + 3, "the devil is not away for 3 days after it sank into the walls");
				context.assertTrue(!h.run.sealed, "the hotel did not open");
			});
			later(context, 200, () -> {
				context.assertTrue(itemsOf(context, h, io.github.kortev.chainsawman.ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP && itemsOf(context, h, io.github.kortev.chainsawman.ChainsawMan.ETERNITY_BLOOD) == Hotel.BLOOD_DROP,
						"its flesh and blood did not drop for everyone");
				tidy(context, h, a, b);
				context.complete();
			});
		});
	}

	/** Nobody accepts (a refusal and silence): it is beaten, does not fight back, and can be killed for the same loot. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_beg_refuse", tickLimit = 900)
	public void ifNobodyAcceptsItCanBeKilledForTheLoot(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 38);
		h.run.begTicks = 60;
		ServerPlayerEntity a = walker(context, h, "refuses", 26.0);
		ServerPlayerEntity b = walker(context, h, "says_nothing", 24.0);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		float[] hp = new float[1];
		ready(context, h, () -> {
			later(context, 15, () -> {
				noSquad(context, h);
				d[0] = fightAt(context, h);
			});
			later(context, 110, () -> toTheLine(a, d[0]));
			later(context, 116, () -> {
				context.assertTrue(h.run.beg != null, "no beg");
				Hotel.answer(a, false);
			});
			// b says nothing: the time runs out.
			later(context, 190, () -> {
				context.assertTrue(h.run.beg == null && !d[0].begging && d[0].broken && !d[0].isDead(), "after nobody accepted it is not beaten and killable (beg " + h.run.beg + ", begging " + d[0].begging + ", broken " + d[0].broken + ")");
				context.assertTrue(!io.github.kortev.chainsawman.contract.Contracts.has(a, io.github.kortev.chainsawman.contract.Contract.ETERNITY) && !io.github.kortev.chainsawman.contract.Contracts.has(b, io.github.kortev.chainsawman.contract.Contract.ETERNITY), "a contract was given");
				hp[0] = a.getHealth();
				d[0].timeUntilRegen = 0;
			});
			later(context, 260, () -> {
				context.assertTrue(a.getHealth() >= hp[0] && b.getHealth() >= hp[0] - 1.0F, "it fought back after it was beaten");
				context.assertTrue(d[0].getHealth() <= 45.01F, "it healed after it was beaten: " + d[0].getHealth());
				context.assertTrue(d[0].damage(a.getDamageSources().playerAttack(a), 100.0F) && d[0].isDead(), "it could not be killed");
				context.assertTrue(!d[0].spared, "it was spared");
			});
			later(context, 400, () -> {
				context.assertTrue(itemsOf(context, h, io.github.kortev.chainsawman.ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP && itemsOf(context, h, io.github.kortev.chainsawman.ChainsawMan.ETERNITY_BLOOD) == Hotel.BLOOD_DROP, "killing it dropped no loot");
				context.assertTrue(h.away && !h.run.sealed && h.holder == null, "the hotel did not open and the devil go away without a holder");
				tidy(context, h, a, b);
				context.complete();
			});
		});
	}

	/** A hybrid gets the screen (Accept greyed out) but cannot accept: an accept gets a line from the devil and no contract. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_beg_hybrid", tickLimit = 900)
	public void aHybridCannotAcceptTheBeg(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 39);
		ServerPlayerEntity hybrid = walker(context, h, "has_pochita", 26.0);
		ServerPlayerEntity plain = walker(context, h, "has_nothing", 24.0);
		io.github.kortev.chainsawman.form.Heart.give(hybrid);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		ready(context, h, () -> {
			later(context, 15, () -> {
				noSquad(context, h);
				d[0] = fightAt(context, h);
			});
			later(context, 110, () -> toTheLine(plain, d[0]));
			later(context, 116, () -> {
				context.assertTrue(h.run.beg != null && h.run.beg.hybrids.contains(hybrid.getUuid()) && !h.run.beg.hybrids.contains(plain.getUuid()), "the hybrid is not marked, or the plain player is");
				Hotel.answer(hybrid, true);
				context.assertTrue(!io.github.kortev.chainsawman.contract.Contracts.has(hybrid, io.github.kortev.chainsawman.contract.Contract.ETERNITY) && h.run.beg.accepted.isEmpty(), "a hybrid accepted a contract");
				context.assertTrue(h.run.beg != null, "the beg ended on a hybrid's answer, with the other still to answer");
				Hotel.answer(plain, false);
			});
			later(context, 124, () -> {
				context.assertTrue(h.run.beg == null && d[0].broken && !d[0].isDead(), "nobody could accept and it is not beaten");
				tidy(context, h, hybrid, plain);
				context.complete();
			});
		});
	}

	/** oneContractPerDevil is still checked at the answer: with another player holding it, an accept is turned down. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_beg_one", tickLimit = 900)
	public void oneContractPerDevilIsCheckedAtTheAnswer(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 40);
		ServerPlayerEntity holder = ForeverGameTests.player(context, "first_holder");
		ServerPlayerEntity p = walker(context, h, "second_comer", 26.0);
		context.getWorld().getGameRules().get(HotelRules.ONE_CONTRACT_PER_DEVIL).set(true, context.getWorld().getServer());
		io.github.kortev.chainsawman.contract.Contracts.grant(holder, io.github.kortev.chainsawman.contract.Contract.ETERNITY);
		h.holder = holder.getUuid();
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		ready(context, h, () -> {
			later(context, 15, () -> {
				noSquad(context, h);
				d[0] = fightAt(context, h);
			});
			later(context, 110, () -> toTheLine(p, d[0]));
			later(context, 116, () -> {
				context.assertTrue(h.run.beg != null, "no beg");
				Hotel.answer(p, true);
				context.assertTrue(!io.github.kortev.chainsawman.contract.Contracts.has(p, io.github.kortev.chainsawman.contract.Contract.ETERNITY), "a second player was given a contract that belongs to one");
				io.github.kortev.chainsawman.contract.Contracts.clear(holder);
				ForeverGameTests.leave(context, holder);
			});
			later(context, 124, () -> {
				context.assertTrue(h.run.beg == null && d[0].broken && !d[0].spared, "nobody got it, so it is beaten, not spared");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** Accepting with every slot full asks which contract to give up; the one chosen goes, and the new one is theirs. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_beg_full", tickLimit = 900)
	public void acceptingWithEverySlotFullAsksWhichToDrop(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 41);
		ServerPlayerEntity p = walker(context, h, "has_three", 26.0);
		io.github.kortev.chainsawman.contract.Contracts.grant(p, "fox");
		io.github.kortev.chainsawman.contract.Contracts.grant(p, "ghost");
		io.github.kortev.chainsawman.contract.Contracts.grant(p, "curse");
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		ready(context, h, () -> {
			later(context, 15, () -> {
				noSquad(context, h);
				d[0] = fightAt(context, h);
			});
			later(context, 110, () -> toTheLine(p, d[0]));
			later(context, 116, () -> {
				Hotel.answer(p, true);
				context.assertTrue(h.run.beg != null && h.run.beg.dropping.contains(p.getUuid()) && !h.run.beg.accepted.contains(p.getUuid()), "a full hand was not asked which to drop");
				context.assertTrue(io.github.kortev.chainsawman.contract.Contracts.ids(p).equals(java.util.List.of("fox", "ghost", "curse")), "the slots changed before they chose");
			});
			later(context, 140, () -> {
				context.assertTrue(h.run.beg != null && !d[0].isDead(), "the beg ended while they were choosing");
				Hotel.drop(p, 1);
				context.assertTrue(io.github.kortev.chainsawman.contract.Contracts.ids(p).equals(java.util.List.of("fox", "eternity", "curse")), "the one they gave up is not the one that went: " + io.github.kortev.chainsawman.contract.Contracts.ids(p));
			});
			later(context, 146, () -> {
				context.assertTrue(h.run.beg == null && d[0].spared && p.getUuid().equals(h.holder), "the deal did not go through after they chose");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	// ---- A whole run without Chainsaw Man.

	/**
	 * Everything works without Chainsaw Man (the rule of the quest plan): a plain player, with no heart, no form and no contract, and the rescued
	 * hunters get through the hunt (the cuts that make it flee and then tear it out of the wall), the fight (the hunters cut it too) and the beg
	 * (the player takes the deal): the contract, its loot, the reward for the two survivors, and the hotel open.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_full_run", tickLimit = 1200)
	public void aPlainPlayerAndSurvivorsFinishTheWholeHotelWithoutChainsawMan(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 42);
		ServerPlayerEntity p = walker(context, h, "plain_hunter", 20.5);
		context.assertTrue(!io.github.kortev.chainsawman.form.Heart.has(p) && !io.github.kortev.chainsawman.form.Form.in(p), "the test player is not plain");
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		java.util.List<io.github.kortev.chainsawman.hotel.SquadMemberEntity> fed = new ArrayList<>();
		int[] xp = new int[1];
		ready(context, h, () -> {
			later(context, 15, () -> {
				context.assertTrue(h.run.sealed && h.run.hunt != null, "the hunt did not begin");
				xp[0] = p.totalExperience;
				// Two of the hunters are fed (the mad one is left to himself).
				for (int k = 0; k < 2; k++) {
					io.github.kortev.chainsawman.hotel.SquadMemberEntity m = hungry(context, h, false, 0);
					feed(p, m);
					fed.add(m);
				}
				// The hunt: cuts at its spot, over and over, until it tears out of the wall.
				for (int k = 0; k < 8 && h.run.hunt != null; k++) {
					io.github.kortev.chainsawman.hotel.Hunt.cut(context.getWorld(), h, spotPos(h), p);
				}
				context.assertTrue(h.run.hunt == null && h.run.fight && h.run.devil != null, "the hunt did not end in the devil tearing out");
				d[0] = (io.github.kortev.chainsawman.hotel.EternityDevilEntity) devilOf(context, h);
			});
			later(context, 160, () -> {
				context.assertTrue(d[0].getHealth() < 268.0F, "the hunters did not cut the devil: " + d[0].getHealth());
				toTheLine(p, d[0]);
			});
			later(context, 168, () -> {
				context.assertTrue(h.run.beg != null && h.run.beg.players.contains(p.getUuid()), "the devil did not beg");
				Hotel.answer(p, true);
				context.assertTrue(io.github.kortev.chainsawman.contract.Contracts.has(p, io.github.kortev.chainsawman.contract.Contract.ETERNITY), "no contract");
			});
			later(context, 290, () -> {
				context.assertTrue(d[0].isDead() && d[0].spared && !h.run.sealed && h.away, "the devil was not spared and the hotel opened");
				context.assertTrue(itemsOf(context, h, io.github.kortev.chainsawman.ChainsawMan.ETERNITY_FLESH) == Hotel.FLESH_DROP && itemsOf(context, h, io.github.kortev.chainsawman.ChainsawMan.ETERNITY_BLOOD) == Hotel.BLOOD_DROP, "no loot");
				context.assertTrue(p.totalExperience - xp[0] == 2 * io.github.kortev.chainsawman.hotel.Squad.REWARD_XP && h.run.saved == 2, "the two survivors were not paid for: " + (p.totalExperience - xp[0]));
				context.assertTrue(!io.github.kortev.chainsawman.form.Heart.has(p) && !io.github.kortev.chainsawman.form.Form.in(p), "Chainsaw Man was needed");
				tidy(context, h, p);
				context.complete();
			});
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
	/**
	 * Runs {@code r} {@code delta} ticks on. It is not handed to runAtTick at once: adding to the test's tick map while the
	 * server is walking it can crash the whole test server, so it is added when the tick is over.
	 */
	static void later(TestContext context, int delta, Runnable r) {
		synchronized (PENDING) {
			if (!hooked) {
				hooked = true;
				net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
					List<Object[]> due;
					synchronized (PENDING) {
						due = new ArrayList<>(PENDING);
						PENDING.clear();
					}
					for (Object[] e : due) {
						TestContext c = (TestContext) e[0];
						try {
							c.runAtTick(Math.max((Long) e[1], c.getTick() + 1L), (Runnable) e[2]);
						} catch (Throwable t) {
							t.printStackTrace();
						}
					}
				});
			}
			PENDING.add(new Object[] {context, context.getTick() + delta, r});
		}
	}

	private static final List<Object[]> PENDING = new ArrayList<>();
	private static boolean hooked;

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
				d[0].forgo();
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
				for (int x = 14; x <= 22; x++) {
					for (int y = 29; y <= 31; y++) {
						bled += !HotelPlan.get().at(x, y, 8).isAir() && !(HotelPlan.get().at(x, y, 8).getBlock() instanceof DoorBlock) && at(context, h, x, y, 8) != HotelPlan.get().at(x, y, 8) ? 1 : 0;
					}
				}
				context.assertTrue(bled >= 3, "only " + bled + " blocks bled");
				slay(context, h, d[0], p);
				context.assertTrue(d[0].isDead(), "the devil did not die");
				// It collapses for 40 ticks before it is gone, not at once.
				int diedAt = (int) context.getTick();
				for (int t = 1; t < 140; t++) {
					int tt = t;
					context.runAtTick(context.getTick() + t, () -> {
						if (tt < 35 && d[0].isRemoved()) {
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

	// ---- The arena the devil tears open.

	/** Every block of floor 8's shell and the roof over it (the redstone lamps without their lit state: the lights die after a kill), by place. */
	private static java.util.Map<BlockPos, BlockState> shell(TestContext context, HomeBlockEntity h) {
		java.util.Map<BlockPos, BlockState> m = new java.util.HashMap<>();
		for (int x = 0; x < HotelPlan.SX; x++) {
			for (int z = 0; z < HotelPlan.SZ; z++) {
				for (int y = 28; y <= 36; y++) {
					BlockPos at = HotelBuilder.world(h.origin, h.rot, x, y, z);
					BlockState state = context.getWorld().getBlockState(at);
					if (state.getBlock() instanceof net.minecraft.block.RedstoneLampBlock) {
						state = state.with(net.minecraft.block.RedstoneLampBlock.LIT, false);
					}
					m.put(at, state);
				}
			}
		}
		return m;
	}

	/** The block entities of the shell (signs and lecterns, with their words and books), as data, by place. */
	private static java.util.Map<BlockPos, String> shellEntities(TestContext context, HomeBlockEntity h) {
		java.util.Map<BlockPos, String> m = new java.util.HashMap<>();
		for (int x = 0; x < HotelPlan.SX; x++) {
			for (int z = 0; z < HotelPlan.SZ; z++) {
				for (int y = 28; y <= 36; y++) {
					BlockPos at = HotelBuilder.world(h.origin, h.rot, x, y, z);
					net.minecraft.block.entity.BlockEntity be = context.getWorld().getBlockEntity(at);
					if (be != null) {
						m.put(at, be.createNbtWithIdentifyingData(context.getWorld().getRegistryManager()).toString());
					}
				}
			}
		}
		return m;
	}

	private static int differences(TestContext context, HomeBlockEntity h, java.util.Map<BlockPos, BlockState> before) {
		int n = 0;
		for (java.util.Map.Entry<BlockPos, BlockState> e : shell(context, h).entrySet()) {
			if (e.getValue() != before.get(e.getKey())) {
				n++;
			}
		}
		return n;
	}

	private static String firstDifference(TestContext context, HomeBlockEntity h, java.util.Map<BlockPos, BlockState> before) {
		for (java.util.Map.Entry<BlockPos, BlockState> e : shell(context, h).entrySet()) {
			if (e.getValue() != before.get(e.getKey())) {
				Vec3d lp = HotelBuilder.local(h.origin, h.rot, Vec3d.ofCenter(e.getKey()));
				return "local (" + (int) lp.x + ", " + (int) lp.y + ", " + (int) lp.z + ") is " + e.getValue() + ", was " + before.get(e.getKey());
			}
		}
		return "none";
	}

	/** The arena is open: the walls above their stumps, the ceiling slab and the roof over it gone; the outer walls, the parapet and the floor stay. */
	private static void assertTorn(TestContext context, HomeBlockEntity h, java.util.Map<BlockPos, BlockState> before, String when) {
		context.assertTrue(io.github.kortev.chainsawman.hotel.Arena.torn(h), when + ": the home does not know the arena is open");
		int gone = differences(context, h, before);
		context.assertTrue(gone >= 400, when + ": only " + gone + " blocks were torn");
		for (int x : new int[] {24, 30, 36}) {
			for (int z : new int[] {1, 6, 12}) {
				context.assertTrue(at(context, h, x, 32, z).isAir() && at(context, h, x, 33, z).isAir(), when + ": the ceiling or the roof is still over (" + x + ", " + z + ")");
			}
		}
		context.assertTrue(at(context, h, 37, 30, 6) == before.get(HotelBuilder.world(h.origin, h.rot, 37, 30, 6)) && !at(context, h, 37, 30, 6).isAir(), when + ": the outer wall behind it went");
		context.assertTrue(at(context, h, 30, 34, 13) == before.get(HotelBuilder.world(h.origin, h.rot, 30, 34, 13)) && !at(context, h, 30, 34, 13).isAir(), when + ": the parapet went");
		context.assertTrue(!at(context, h, 30, 28, 6).isAir() && !at(context, h, 24, 28, 2).isAir(), when + ": the floor went");
		context.assertTrue(at(context, h, 20, 32, 6) == before.get(HotelBuilder.world(h.origin, h.rot, 20, 32, 6)), when + ": the ceiling toward the stairs went");
	}

	private static void assertMended(TestContext context, HomeBlockEntity h, java.util.Map<BlockPos, BlockState> before, java.util.Map<BlockPos, String> entities, String when) {
		context.assertTrue(!io.github.kortev.chainsawman.hotel.Arena.torn(h), when + ": the home still has the arena torn");
		context.assertTrue(differences(context, h, before) == 0, when + ": the hotel is not as it was: " + firstDifference(context, h, before));
		java.util.Map<BlockPos, String> now = shellEntities(context, h);
		context.assertTrue(now.equals(entities), when + ": a block entity (a sign's words, a book) is not as it was (" + now.size() + " now, " + entities.size() + " before)");
	}

	/** Killing the devil: the arena stays open while it collapses, then every block is back as it was (states, lamps, the signs' words). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_arena_kill", tickLimit = 700)
	public void theArenaIsPutBackExactlyAfterAKill(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 43);
		ServerPlayerEntity p = walker(context, h, "arena_kill", 26.0);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		java.util.Map<BlockPos, BlockState>[] before = new java.util.Map[1];
		java.util.Map<BlockPos, String>[] entities = new java.util.Map[1];
		ready(context, h, () -> {
			later(context, 20, () -> {
				before[0] = shell(context, h);
				entities[0] = shellEntities(context, h);
				context.assertTrue(!entities[0].isEmpty(), "no sign in the shell to keep the words of");
				d[0] = fightAt(context, h);
			});
			later(context, 80, () -> assertTorn(context, h, before[0], "in the fight"));
			later(context, 100, () -> slay(context, h, d[0], p));
			later(context, 125, () -> assertTorn(context, h, before[0], "while it collapses"));
			later(context, 160, () -> {
				assertMended(context, h, before[0], entities[0], "after the kill");
				tidy(context, h, p);
				context.complete();
			});
		});
	}

	/** Sparing the devil (someone took its deal): the same, every block back. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_arena_spare", tickLimit = 700)
	public void theArenaIsPutBackExactlyAfterASpare(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 44);
		ServerPlayerEntity a = walker(context, h, "arena_spare", 26.0);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		java.util.Map<BlockPos, BlockState>[] before = new java.util.Map[1];
		java.util.Map<BlockPos, String>[] entities = new java.util.Map[1];
		ready(context, h, () -> {
			later(context, 15, () -> {
				noSquad(context, h);
				before[0] = shell(context, h);
				entities[0] = shellEntities(context, h);
				d[0] = fightAt(context, h);
			});
			later(context, 80, () -> assertTorn(context, h, before[0], "in the fight"));
			later(context, 110, () -> toTheLine(a, d[0]));
			later(context, 116, () -> {
				context.assertTrue(h.run.beg != null, "it did not beg");
				Hotel.answer(a, true);
			});
			later(context, 122, () -> {
				context.assertTrue(d[0].spared && d[0].isDead(), "it was not spared");
				assertTorn(context, h, before[0], "while it sinks away");
			});
			later(context, 172, () -> {
				assertMended(context, h, before[0], entities[0], "after the spare");
				tidy(context, h, a);
				context.complete();
			});
		});
	}

	/** The world left in the middle of the fight (the server restarted: the devil is not kept): the hotel finds its arena torn and puts it back. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_arena_restart", tickLimit = 700)
	public void theArenaIsPutBackWhenTheWorldIsLoadedMidFight(TestContext context) {
		HomeBlockEntity h = hotel(context, 0, 45);
		ServerPlayerEntity p = walker(context, h, "arena_restart", 26.0);
		io.github.kortev.chainsawman.hotel.EternityDevilEntity[] d = new io.github.kortev.chainsawman.hotel.EternityDevilEntity[1];
		java.util.Map<BlockPos, BlockState>[] before = new java.util.Map[1];
		java.util.Map<BlockPos, String>[] entities = new java.util.Map[1];
		ready(context, h, () -> {
			later(context, 20, () -> {
				before[0] = shell(context, h);
				entities[0] = shellEntities(context, h);
				d[0] = fightAt(context, h);
			});
			later(context, 80, () -> {
				assertTorn(context, h, before[0], "in the fight");
				// The server goes down: all that is kept is the home's own data (the arena) and the world's blocks; it comes up with a fresh run.
				d[0].discard();
				h.run.fight = false;
				h.run.devil = null;
				h.run.started = false;
				h.run.arenaCells = null;
				h.run.tearAt = -1;
			});
			later(context, 100, () -> {
				assertMended(context, h, before[0], entities[0], "after the restart");
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
				slay(context, h, dd, p);
			});
			later(context, 110, () -> {
				context.assertTrue(h.away && h.awayUntilDay == day[0] + 3, "the dead devil is not away for 3 days");
				context.assertTrue(h.awayUntilTick - world.getTime() > 3 * 24000L - 400L, "the away clock is not 3 days of game ticks");
				context.assertTrue(!other.away, "another hotel's devil went away with it");
				// The clock of the day is set about: the devil is still away, and the hotel does not wake.
				world.setTimeOfDay((day[0] + 9) * 24000L + 14000L);
			});
			later(context, 280, () -> {
				context.assertTrue(h.away && !h.run.night, "the devil came back early (or the hotel woke while it was away)");
				// The three days of game time pass, at night: it is back, and the hotel wakes.
				world.setTimeOfDay(14000L);
				h.awayUntilTick = world.getTime();
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
