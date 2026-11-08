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
				// Three laps: the flesh is at the corners (level 3: all of it).
				context.assertTrue(h.run.flesh == 3, "turn " + turn + ": the flesh is at level " + h.run.flesh);
				for (java.util.List<HotelPlan.P> level : HotelPlan.get().flesh) {
					HotelPlan.P q = level.get(0);
					context.assertTrue(at(context, h, q.x(), q.y(), q.z()).isOf(Blocks.NETHER_WART_BLOCK), "turn " + turn + ": no flesh at " + q);
				}
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
				HotelPlan.P q = HotelPlan.get().flesh.get(0).get(0);
				context.assertTrue(!at(context, h, q.x(), q.y(), q.z()).isOf(Blocks.NETHER_WART_BLOCK), "turn " + turn + ": the flesh was left at " + q);
				context.assertTrue(h.run.laps.isEmpty(), "turn " + turn + ": the laps were kept through the day");
				if (turn == 3) {
					leaveHotel(context, h, p);
					context.complete();
				}
			});
		}
	}
}
