package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.hotel.HomeBlockEntity;
import io.github.kortev.chainsawman.hotel.Hotel;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import io.github.kortev.chainsawman.hotel.HotelPlan;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The hotel can be walked (package 0.1e). The stairs are walked for real at every turn of the hotel: a player's own body is moved by the
 * game's own collision (the step up a stair is the game's, a step higher than that stops it, a block over the head stops it), at a walk and at
 * a sprint, from the street in at the front door, up every flight to the loop floor's corridor and back down and out, with two free blocks
 * over the feet the whole way. An old hotel's stairwell is rebuilt. And the loop's move keeps a player's place, look and speed exactly (it is
 * a relative move: what is sent is the difference, with every axis and the look marked relative), for two players each looping alone, with
 * what follows them carried and a dropped item kept where walking reaches it.
 */
public class HotelWalkGameTests implements FabricGameTest {
	/** The route, local (x, z), in order: from the street to the loop floor's corridor (feet height follows from the walking). */
	static List<double[]> upRoute() {
		List<double[]> r = new ArrayList<>();
		r.add(new double[] {27.45, -2.5});
		r.add(new double[] {27.45, 0.5});
		r.add(new double[] {27.45, 6.5});
		r.add(new double[] {13.5, 6.5});
		r.add(new double[] {13.5, 3.5});
		for (int i = 0; i < HotelPlan.FLOORS - 1; i++) {
			r.add(new double[] {16.6, 3.5});
			r.add(new double[] {16.6, 1.5});
			r.add(new double[] {13.5, 1.5});
			r.add(new double[] {13.5, 3.5});
		}
		r.add(new double[] {13.5, 6.5});
		r.add(new double[] {20.5, 6.5});
		return r;
	}

	/** Back down and out into the street. */
	static List<double[]> downRoute() {
		List<double[]> r = new ArrayList<>();
		r.add(new double[] {13.5, 6.5});
		r.add(new double[] {13.5, 3.5});
		for (int i = 0; i < HotelPlan.FLOORS - 1; i++) {
			r.add(new double[] {13.5, 1.5});
			r.add(new double[] {16.6, 1.5});
			r.add(new double[] {16.6, 3.5});
			r.add(new double[] {13.5, 3.5});
		}
		r.add(new double[] {13.5, 6.5});
		r.add(new double[] {27.45, 6.5});
		r.add(new double[] {27.45, 0.5});
		r.add(new double[] {27.45, -2.5});
		return r;
	}

	/**
	 * Walks {@code p} along {@code route} (local x, z) with the game's own collision: each step a little toward the next point (its step up, as
	 * a player's, is the game's), then down onto whatever is under it. Fails where it is stopped, or where there are not two free blocks over its
	 * feet. Returns the local place it ends at.
	 */
	static Vec3d walk(TestContext context, HomeBlockEntity h, ServerPlayerEntity p, List<double[]> route, double stride, String what) {
		ServerWorld world = context.getWorld();
		int stuck = 0;
		// (Local points as the blocks lie: HotelBuilder's point frame is a block off the block frame at turns 1 to 3; Shaft's is not.)
		for (double[] to : route) {
			for (int n = 0; n < 4000; n++) {
				Vec3d lp = io.github.kortev.chainsawman.hotel.Shaft.localOf(h.origin, h.rot, p.getPos());
				double dx = to[0] - lp.x;
				double dz = to[1] - lp.z;
				double d = Math.hypot(dx, dz);
				if (d < 0.06) {
					break;
				}
				double s = Math.min(stride, d);
				Vec3d step = HotelBuilder.world(BlockPos.ORIGIN, h.rot, dx / d * s, 0.0, dz / d * s);
				Vec3d before = p.getPos();
				p.setOnGround(true);
				p.move(MovementType.SELF, new Vec3d(step.x, -0.05, step.z));
				for (int k = 0; k < 4; k++) {
					p.move(MovementType.SELF, new Vec3d(0.0, -0.3, 0.0));
				}
				double moved = Math.hypot(p.getX() - before.x, p.getZ() - before.z);
				stuck = moved < s * 0.3 ? stuck + 1 : 0;
				Vec3d now = io.github.kortev.chainsawman.hotel.Shaft.localOf(h.origin, h.rot, p.getPos());
				if (stuck > 6) {
					context.throwGameTestException(what + ": stopped at local " + fmt(now) + " on the way to (" + to[0] + ", " + to[1] + ") at turn " + h.rot
							+ " (in front: " + world.getBlockState(BlockPos.ofFloored(p.getPos().add(step.normalize().multiply(0.5)))) + ", over the head: "
							+ world.getBlockState(BlockPos.ofFloored(p.getEyePos().add(0.0, 0.4, 0.0))) + ")");
				}
				// Two free blocks over the feet (over the floor a carpet lies on: a doorway is two blocks over the floor, the carpet a sixteenth on it).
				Box body = p.getBoundingBox();
				double feet = body.minY - Math.floor(body.minY + 1.0E-6) < 0.07 ? Math.floor(body.minY + 1.0E-6) : body.minY;
				Box two = new Box(body.minX, body.minY + 1.0E-3, body.minZ, body.maxX, feet + 2.0 - 1.0E-3, body.maxZ);
				if (!world.isSpaceEmpty(p, two)) {
					StringBuilder hit = new StringBuilder();
					for (net.minecraft.util.shape.VoxelShape v : world.getBlockCollisions(p, two)) {
						Box vb = v.getBoundingBox();
						hit.append(" ").append(fmt(io.github.kortev.chainsawman.hotel.Shaft.localOf(h.origin, h.rot, vb.getCenter()))).append(" ")
								.append(world.getBlockState(BlockPos.ofFloored(vb.getCenter())).getBlock());
					}
					context.throwGameTestException(what + ": not two free blocks over the feet at local " + fmt(now) + " at turn " + h.rot + ", in the way:" + hit);
				}
				if (now.y < -1.0) {
					context.throwGameTestException(what + ": fell out of the hotel at " + fmt(now));
				}
			}
		}
		return io.github.kortev.chainsawman.hotel.Shaft.localOf(h.origin, h.rot, p.getPos());
	}

	static String fmt(Vec3d v) {
		return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
	}

	/** The doors along the way as a player finds them (the hotel's own night opens all but the loop floor's, which a player opens by hand). */
	static void doorsOpen(ServerWorld world, HomeBlockEntity h) {
		HotelBuilder.setNight(world, h.origin, h.rot, true);
		for (HotelPlan.Door d : HotelPlan.get().doors) {
			if (d.kind() == HotelPlan.DoorKind.STAIR) {
				for (int k = 0; k < 2; k++) {
					BlockPos at = HotelBuilder.world(h.origin, h.rot, d.x(), d.y() + k, d.z());
					BlockState s = world.getBlockState(at);
					if (s.getBlock() instanceof DoorBlock) {
						world.setBlockState(at, s.with(DoorBlock.OPEN, true), 2);
					}
				}
			}
		}
	}

	/** A copy of the hotel's blocks (to see that walking broke nothing). */
	static BlockState[] snapshot(ServerWorld world, HomeBlockEntity h) {
		BlockState[] out = new BlockState[HotelPlan.SX * HotelPlan.SY * HotelPlan.SZ];
		int i = 0;
		for (int y = 0; y < HotelPlan.SY; y++) {
			for (int z = 0; z < HotelPlan.SZ; z++) {
				for (int x = 0; x < HotelPlan.SX; x++) {
					out[i++] = world.getBlockState(HotelBuilder.world(h.origin, h.rot, x, y, z));
				}
			}
		}
		return out;
	}

	/**
	 * Every flight, up and down, at every turn of the hotel, at a walk and a sprint: from the street in at the front door, up to the loop
	 * floor's corridor, back down and out. Each step of the stairs has a rise the game steps up by itself and two free blocks over it, and so
	 * does every cell of the route (checked block by block as well, so a slab over the head anywhere on it shows).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_walk_stairs", tickLimit = 400)
	public void everyFlightCanBeWalkedUpAndDownAtEveryTurn(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerPlayerEntity p = ForeverGameTests.player(context, "stair_walker");
		world.setTimeOfDay(3000);
		context.runAtTick(5, () -> {
			try {
				for (int rot = 0; rot < 4; rot++) {
					HomeBlockEntity h = HotelGameTests.hotel(context, rot, 90);
					h.away = true;
					doorsOpen(world, h);
					BlockState[] before = snapshot(world, h);
					for (double stride : new double[] {0.2159, 0.2806}) {
						String how = (stride < 0.25 ? "walking" : "sprinting") + " at turn " + rot;
						Vec3d start = io.github.kortev.chainsawman.hotel.Shaft.at(h.origin, h.rot, 27.45, 1.0, -2.5);
						p.refreshPositionAndAngles(start.x, start.y, start.z, 0.0F, 0.0F);
						p.setVelocity(Vec3d.ZERO);
						Vec3d up = walk(context, h, p, upRoute(), stride, how + ", going up");
						context.assertTrue(Math.abs(up.y - 29.0) < 0.1 && up.z > 5.0 && up.z < 8.0 && Math.abs(up.x - 20.5) < 0.1,
								how + ": going up ended at " + fmt(up) + ", not in the loop floor's corridor");
						Vec3d down = walk(context, h, p, downRoute(), stride, how + ", going down");
						context.assertTrue(Math.abs(down.y - 1.0) < 0.1 && down.z < -1.5, how + ": going down ended at " + fmt(down) + ", not out in the street");
					}
					BlockState[] after = snapshot(world, h);
					int changed = 0;
					for (int i = 0; i < before.length; i++) {
						changed += before[i] == after[i] ? 0 : 1;
					}
					context.assertTrue(changed == 0, changed + " blocks of the hotel changed while it was walked at turn " + rot);
					// The plan, cell by cell: every stair and every landing cell of the well has two free blocks over where the feet go.
					for (int i = 0; i < HotelPlan.FLOORS; i++) {
						int y0 = HotelPlan.floorY(i);
						List<int[]> cells = new ArrayList<>();
						cells.add(new int[] {13, y0 + 1, 1});
						cells.add(new int[] {13, y0 + 1, 2});
						cells.add(new int[] {13, y0 + 1, 3});
						if (i < HotelPlan.FLOORS - 1) {
							cells.add(new int[] {14, y0 + 1, 3});
							cells.add(new int[] {15, y0 + 2, 3});
							for (int z = 1; z <= 3; z++) {
								cells.add(new int[] {16, y0 + 3, z});
								cells.add(new int[] {17, y0 + 3, z});
							}
							cells.add(new int[] {15, y0 + 3, 1});
							cells.add(new int[] {14, y0 + 4, 1});
						}
						for (int[] c : cells) {
							BlockPos at = HotelBuilder.world(h.origin, h.rot, c[0], c[1], c[2]);
							BlockState s = world.getBlockState(at);
							double feet = c[1] + (s.getCollisionShape(world, at).isEmpty() ? 0.0 : s.getCollisionShape(world, at).getMax(net.minecraft.util.math.Direction.Axis.Y));
							Vec3d a = io.github.kortev.chainsawman.hotel.Shaft.at(h.origin, h.rot, c[0] + 0.35, feet + 0.001, c[2] + 0.35);
							Vec3d b = io.github.kortev.chainsawman.hotel.Shaft.at(h.origin, h.rot, c[0] + 0.65, feet + 1.999, c[2] + 0.65);
							context.assertTrue(world.isSpaceEmpty(new Box(a, b)), "turn " + rot + ": not two free blocks over (" + c[0] + ", " + c[1] + ", " + c[2] + ")");
						}
					}
				}
			} finally {
				ForeverGameTests.leave(context, p);
				HotelGameTests.forced(context, HotelGameTests.origin(context, 90), false);
			}
			context.complete();
		});
	}

	/**
	 * A hotel from an older world (its home saved before the stairwell was kept, with the old stairs in it: one flight a storey under a slab
	 * opened over two steps) gets the new stairwell at its first tick, and nothing else changes.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_walk_old", tickLimit = 200)
	public void anOldHotelGetsTheNewStairs(TestContext context) {
		ServerWorld world = context.getWorld();
		world.setTimeOfDay(3000);
		HomeBlockEntity h = HotelGameTests.hotel(context, 1, 91);
		// The old stairwell, as the old plan put it (the slab whole but over two steps; the steps four a storey in one lane).
		for (int i = 0; i < HotelPlan.FLOORS; i++) {
			int y0 = HotelPlan.floorY(i);
			for (int y = y0; y <= y0 + 3; y++) {
				for (int z = 1; z <= 3; z++) {
					for (int x = 13; x <= 17; x++) {
						BlockState s = y == y0 ? Blocks.SMOOTH_QUARTZ.getDefaultState() : Blocks.AIR.getDefaultState();
						world.setBlockState(HotelBuilder.world(h.origin, h.rot, x, y, z), s, 2);
					}
				}
			}
		}
		h.stairs = 0;
		HotelPlan plan = HotelPlan.get();
		HotelGameTests.ready(context, h, () -> HotelGameTests.later(context, 5, () -> {
			context.assertTrue(h.stairs == HotelBuilder.STAIRS, "the old hotel's stairs were not rebuilt (version " + h.stairs + ")");
			int bad = 0;
			for (int y = 0; y < HotelPlan.SY; y++) {
				for (int z = 1; z <= 3; z++) {
					for (int x = 13; x <= 17; x++) {
						BlockState want = plan.at(x, y, z).rotate(HotelBuilder.turn(h.rot));
						bad += world.getBlockState(HotelBuilder.world(h.origin, h.rot, x, y, z)) == want ? 0 : 1;
					}
				}
			}
			context.assertTrue(bad == 0, bad + " blocks of the rebuilt stairwell are not the plan's");
			HotelGameTests.leaveHotel(context, h);
			context.complete();
		}));
	}

	/** What the connection of a test player has been told (it has no client: the packets wait in its channel). */
	static List<Object> sent(ServerPlayerEntity p) {
		try {
			java.lang.reflect.Field f = net.minecraft.server.network.ServerCommonNetworkHandler.class.getDeclaredField("connection");
			f.setAccessible(true);
			net.minecraft.network.ClientConnection c = (net.minecraft.network.ClientConnection) f.get(p.networkHandler);
			java.lang.reflect.Field ch = net.minecraft.network.ClientConnection.class.getDeclaredField("channel");
			ch.setAccessible(true);
			io.netty.channel.embedded.EmbeddedChannel channel = (io.netty.channel.embedded.EmbeddedChannel) ch.get(c);
			List<Object> out = new ArrayList<>();
			Object m;
			while ((m = channel.readOutbound()) != null) {
				out.add(m);
			}
			return out;
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	static PlayerPositionLookS2CPacket lastMove(ServerPlayerEntity p) {
		PlayerPositionLookS2CPacket last = null;
		for (Object m : sent(p)) {
			if (m instanceof PlayerPositionLookS2CPacket pk) {
				last = pk;
			} else if (m instanceof net.minecraft.network.packet.s2c.play.BundleS2CPacket b) {
				for (var q : b.getPackets()) {
					if (q instanceof PlayerPositionLookS2CPacket pk) {
						last = pk;
					}
				}
			}
		}
		return last;
	}

	/**
	 * Two players each looping alone, at two turns of the hotel, one going each way, sprinting and in the air: each is put back exactly a
	 * period, nothing else changed (their place across the corridor and height to a millionth, their look and speed exactly), and what the
	 * client is sent is only the difference with every axis and the look marked relative (so the client keeps its own place, look and speed,
	 * and its last frame's place, and nothing jumps). A pig at one's heels comes with them; one at the other's heels with the other; an item
	 * dropped past the window is put back in it; a survivor sitting against the wall stays.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_walk_loop", tickLimit = 700)
	public void theLoopMoveKeepsPlaceLookAndSpeedForTwoPlayers(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerPlayerEntity a = ForeverGameTests.player(context, "looper_a");
		ServerPlayerEntity b = ForeverGameTests.player(context, "looper_b");
		HomeBlockEntity first = HotelGameTests.hotel(context, 1, 92);
		HomeBlockEntity[] hh = {first};
		PigEntity[] pigs = new PigEntity[2];
		ItemEntity[] item = new ItemEntity[1];
		Vec3d[] squad = new Vec3d[1];
		HotelGameTests.ready(context, first, () -> {
			int[] turns = {1, 2};
			for (int n = 0; n < turns.length; n++) {
				int turn = turns[n];
				int base = 1 + n * 120;
				HotelGameTests.later(context, base, () -> {
					if (turn != 1) {
						hh[0] = HotelGameTests.hotel(context, turn, 92);
					}
					world.setTimeOfDay(14000);
					HotelGameTests.put(a, hh[0], 22.0, 29.0, 6.1, 0.0F, 0.0F);
					HotelGameTests.put(b, hh[0], 16.0, 29.0, 7.2, 0.0F, 0.0F);
				});
				HotelGameTests.later(context, base + 40, () -> {
					HomeBlockEntity h = hh[0];
					context.assertTrue(h.run.sealed && h.run.inside.contains(a.getUuid()) && h.run.inside.contains(b.getUuid()), "turn " + turn + ": the loop did not close on both");
					squad[0] = null;
					for (io.github.kortev.chainsawman.hotel.SquadMemberEntity m : world.getEntitiesByClass(io.github.kortev.chainsawman.hotel.SquadMemberEntity.class,
							new Box(HotelBuilder.world(h.origin, h.rot, 0.0, 27.0, 0.0), HotelBuilder.world(h.origin, h.rot, 38.0, 33.0, 14.0)), m -> m.sitting())) {
						squad[0] = m.getPos();
					}
					// A sprinting jump out of the east end, and a fall walking out of the west end.
					HotelGameTests.put(a, h, 25.31, 29.37, 6.13, 33.7F, -12.5F);
					HotelGameTests.put(b, h, 12.77, 29.81, 7.21, -121.3F, 40.2F);
					a.setVelocity(HotelBuilder.world(BlockPos.ORIGIN, turn, 0.2806, 0.3, 0.04));
					b.setVelocity(HotelBuilder.world(BlockPos.ORIGIN, turn, -0.2159, -0.15, 0.01));
					// A pig at each one's heels, an item dropped just past the window.
					for (int k = 0; k < 2; k++) {
						PigEntity pig = EntityType.PIG.create(world);
						Vec3d at = k == 0 ? HotelBuilder.world(h.origin, h.rot, 24.4, 29.0, 5.6) : HotelBuilder.world(h.origin, h.rot, 13.6, 29.0, 7.6);
						pig.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
						pig.setAiDisabled(true);
						world.spawnEntity(pig);
						pigs[k] = pig;
					}
					Vec3d drop = HotelBuilder.world(h.origin, h.rot, 27.0, 29.0, 6.5);
					item[0] = new ItemEntity(world, drop.x, drop.y, drop.z, new ItemStack(Items.BREAD));
					item[0].setVelocity(Vec3d.ZERO);
					item[0].setPickupDelay(200);
					world.spawnEntity(item[0]);
					sent(a);
					sent(b);
				});
				HotelGameTests.later(context, base + 43, () -> {
					HomeBlockEntity h = hh[0];
					PlayerPositionLookS2CPacket ma = lastMove(a);
					PlayerPositionLookS2CPacket mb = lastMove(b);
					Vec3d la = HotelGameTests.local(h, a);
					Vec3d lb = HotelGameTests.local(h, b);
					String t = "turn " + turn + ": ";
					context.assertTrue(Math.abs(la.x - 13.31) < 1.0E-6 && Math.abs(la.y - 29.37) < 1.0E-6 && Math.abs(la.z - 6.13) < 1.0E-6, t + "the first was put at " + la);
					context.assertTrue(Math.abs(lb.x - 24.77) < 1.0E-6 && Math.abs(lb.y - 29.81) < 1.0E-6 && Math.abs(lb.z - 7.21) < 1.0E-6, t + "the second was put at " + lb);
					context.assertTrue(a.getYaw() == 33.7F && a.getPitch() == -12.5F && b.getYaw() == -121.3F && b.getPitch() == 40.2F,
							t + "a look changed: " + a.getYaw() + "/" + a.getPitch() + ", " + b.getYaw() + "/" + b.getPitch());
					context.assertTrue(a.getVelocity().squaredDistanceTo(HotelBuilder.world(BlockPos.ORIGIN, turn, 0.2806, 0.3, 0.04)) < 1.0E-12
							&& b.getVelocity().squaredDistanceTo(HotelBuilder.world(BlockPos.ORIGIN, turn, -0.2159, -0.15, 0.01)) < 1.0E-12, t + "a speed changed");
					context.assertTrue(h.run.laps.getOrDefault(a.getUuid(), 0) == 1 && h.run.laps.getOrDefault(b.getUuid(), 0) == 1,
							t + "laps " + h.run.laps.get(a.getUuid()) + ", " + h.run.laps.get(b.getUuid()));
					// What the clients were told: only the difference, everything relative.
					Set<PositionFlag> all = EnumSet.of(PositionFlag.X, PositionFlag.Y, PositionFlag.Z, PositionFlag.Y_ROT, PositionFlag.X_ROT);
					for (Object[] c : new Object[][] {{ma, -12.0, "first"}, {mb, 12.0, "second"}}) {
						PlayerPositionLookS2CPacket m = (PlayerPositionLookS2CPacket) c[0];
						context.assertTrue(m != null, t + "the " + c[2] + " was sent no move");
						Vec3d want = HotelBuilder.world(BlockPos.ORIGIN, turn, (double) c[1], 0.0, 0.0);
						context.assertTrue(m.getFlags().containsAll(all), t + "the " + c[2] + "'s move is not all relative: " + m.getFlags());
						context.assertTrue(Math.abs(m.getX() - want.x) < 1.0E-9 && Math.abs(m.getY()) < 1.0E-9 && Math.abs(m.getZ() - want.z) < 1.0E-9
								&& m.getYaw() == 0.0F && m.getPitch() == 0.0F, t + "the " + c[2] + "'s move is not just a period: " + m.getX() + ", " + m.getY() + ", " + m.getZ() + ", " + m.getYaw() + ", " + m.getPitch());
					}
					// What was about them.
					Vec3d p0 = HotelBuilder.local(h.origin, h.rot, pigs[0].getPos());
					Vec3d p1 = HotelBuilder.local(h.origin, h.rot, pigs[1].getPos());
					context.assertTrue(Math.abs(p0.x - 12.4) < 0.05 && Math.abs(p0.z - 5.6) < 0.05, t + "the pig at the first's heels was left at " + p0);
					context.assertTrue(Math.abs(p1.x - 25.6) < 0.05 && Math.abs(p1.z - 7.6) < 0.05, t + "the pig at the second's heels was left at " + p1);
					Vec3d li = HotelBuilder.local(h.origin, h.rot, item[0].getPos());
					context.assertTrue(Math.abs(li.x - 15.0) < 0.4, t + "the item past the window is at " + li);
					if (squad[0] != null) {
						boolean stayed = false;
						for (io.github.kortev.chainsawman.hotel.SquadMemberEntity m : world.getEntitiesByClass(io.github.kortev.chainsawman.hotel.SquadMemberEntity.class,
								new Box(squad[0], squad[0]).expand(0.3), m -> true)) {
							stayed = true;
						}
						context.assertTrue(stayed, t + "a survivor sitting against the wall was moved");
					}
					pigs[0].discard();
					pigs[1].discard();
					item[0].discard();
					HotelGameTests.put(a, h, 400.0, 1.0, 3.5, 0.0F, 0.0F);
					HotelGameTests.put(b, h, 400.0, 1.0, 9.5, 0.0F, 0.0F);
					world.setTimeOfDay(3000);
				});
				if (n == turns.length - 1) {
					HotelGameTests.later(context, base + 70, () -> {
						HotelGameTests.leaveHotel(context, hh[0], a, b);
						context.complete();
					});
				}
			}
		});
	}
}
