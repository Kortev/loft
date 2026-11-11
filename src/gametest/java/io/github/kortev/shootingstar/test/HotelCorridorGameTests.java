package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.hotel.HomeBlockEntity;
import io.github.kortev.chainsawman.hotel.Hotel;
import io.github.kortev.chainsawman.hotel.HotelBlocks;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import io.github.kortev.chainsawman.hotel.HotelPlan;
import java.util.EnumSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The hotel's corridor and stairwell (package 0.1g): the stairs inside the loop lead back to the loop's floor with nothing changed but the
 * storey (place across, look and speed kept, the move only a storey with every axis relative), up and down; every turn of the hotel puts
 * its points and its blocks in the same place; and the clocks read 8:18 only inside the loop (the lobby's keeps the time), the floor signs
 * 8 while it holds.
 */
public class HotelCorridorGameTests implements FabricGameTest {
	/**
	 * Down the stairs from the loop's floor and up them, at two turns: past the window's foot a player is put a storey up, past its head a
	 * storey down, their place across the well and their height in the storey to a millionth, look and speed exact; the client is told only
	 * the storey with every axis and the look relative; a pig at their heels comes with them; and they are on the loop's floor's landing.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_corridor_stairs", tickLimit = 600 + HotelGameTests.READY_WAIT)
	public void theStairsInTheLoopLeadBackToTheLoopFloor(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerPlayerEntity a = ForeverGameTests.player(context, "stair_looper");
		HomeBlockEntity first = HotelGameTests.hotel(context, 1, 93);
		HomeBlockEntity[] hh = {first};
		PigEntity[] pig = new PigEntity[1];
		HotelGameTests.ready(context, first, () -> {
			int[] turns = {1, 3};
			for (int n = 0; n < turns.length; n++) {
				int turn = turns[n];
				int base = 1 + n * 140;
				HotelGameTests.later(context, base, () -> {
					if (turn != 1) {
						hh[0] = HotelGameTests.hotel(context, turn, 93);
					}
					world.setTimeOfDay(14000);
					HotelGameTests.put(a, hh[0], 20.0, 29.0, 6.5, 0.0F, 0.0F);
				});
				for (int way = 0; way < 2; way++) {
					boolean down = way == 0;
					int at = base + 40 + way * 30;
					// Down: on the last step above the floor below's landing, stepping off it. Up: on the loop floor's first step up.
					double[] from = down ? new double[] {13.93, 25.13, 3.41} : new double[] {14.21, 29.37, 3.52};
					double[] want = down ? new double[] {13.93, 29.13, 3.41} : new double[] {14.21, 25.37, 3.52};
					HotelGameTests.later(context, at, () -> {
						HomeBlockEntity h = hh[0];
						context.assertTrue(h.run.sealed && h.run.inside.contains(a.getUuid()), "turn " + turn + ": the loop did not close");
						HotelGameTests.put(a, h, from[0], from[1], from[2], 77.3F, 21.5F);
						a.setVelocity(HotelBuilder.dir(turn, down ? -0.2159 : 0.2159, down ? -0.08 : 0.42, 0.03));
						if (down) {
							pig[0] = EntityType.PIG.create(world);
							Vec3d p = HotelBuilder.at(h.origin, h.rot, 15.6, 26.1, 3.5);
							pig[0].refreshPositionAndAngles(p.x, p.y, p.z, 0.0F, 0.0F);
							pig[0].setAiDisabled(true);
							world.spawnEntity(pig[0]);
						}
						HotelWalkGameTests.sent(a);
					});
					HotelGameTests.later(context, at + 3, () -> {
						HomeBlockEntity h = hh[0];
						String t = "turn " + turn + (down ? ", down: " : ", up: ");
						Vec3d l = HotelGameTests.local(h, a);
						context.assertTrue(Math.abs(l.x - want[0]) < 1.0E-6 && Math.abs(l.y - want[1]) < 1.0E-6 && Math.abs(l.z - want[2]) < 1.0E-6, t + "put at " + l);
						context.assertTrue(a.getYaw() == 77.3F && a.getPitch() == 21.5F, t + "the look changed: " + a.getYaw() + "/" + a.getPitch());
						context.assertTrue(a.getVelocity().squaredDistanceTo(HotelBuilder.dir(turn, down ? -0.2159 : 0.2159, down ? -0.08 : 0.42, 0.03)) < 1.0E-12, t + "the speed changed: " + a.getVelocity() + " for " + HotelBuilder.dir(turn, down ? -0.2159 : 0.2159, down ? -0.08 : 0.42, 0.03));
						PlayerPositionLookS2CPacket m = HotelWalkGameTests.lastMove(a);
						context.assertTrue(m != null, t + "no move was sent");
						Set<PositionFlag> all = EnumSet.of(PositionFlag.X, PositionFlag.Y, PositionFlag.Z, PositionFlag.Y_ROT, PositionFlag.X_ROT);
						context.assertTrue(m.getFlags().containsAll(all), t + "the move is not all relative: " + m.getFlags());
						context.assertTrue(Math.abs(m.getX()) < 1.0E-9 && Math.abs(m.getY() - (down ? 4.0 : -4.0)) < 1.0E-9 && Math.abs(m.getZ()) < 1.0E-9
								&& m.getYaw() == 0.0F && m.getPitch() == 0.0F, t + "the move is not just a storey: " + m.getX() + ", " + m.getY() + ", " + m.getZ());
						// On the loop's floor's flight, between its landing and the floor below's: the window. The landing it walks to is 8's.
						context.assertTrue(l.y >= Hotel.WELL_LO && l.y < Hotel.WELL_HI, t + "out of the stairs' window at " + l);
						context.assertTrue(h.run.top.contains(a.getUuid()), t + "no longer on the loop's floor");
						BlockState sign = HotelGameTests.at(context, h, HotelPlan.SHAFT_X0, HotelPlan.floorY(HotelPlan.FLOORS - 1) + 2, 2);
						context.assertTrue(sign.isOf(HotelBlocks.FLOOR_SIGN) && sign.get(HotelBlocks.FloorSign.FLOOR) == 8, t + "the landing's sign is " + sign);
						BlockState below = HotelGameTests.at(context, h, HotelPlan.SHAFT_X0, HotelPlan.floorY(HotelPlan.FLOORS - 2) + 2, 2);
						context.assertTrue(below.isOf(HotelBlocks.FLOOR_SIGN) && below.get(HotelBlocks.FloorSign.FLOOR) == 8, t + "the floor below's sign does not read 8 in the loop: " + below);
						if (down) {
							Vec3d pl = HotelBuilder.localOf(h.origin, h.rot, pig[0].getPos());
							context.assertTrue(Math.abs(pl.y - 30.1) < 0.05 && Math.abs(pl.x - 15.6) < 0.05, t + "the pig at the heels was left at " + pl);
							pig[0].discard();
						}
					});
				}
				HotelGameTests.later(context, base + 110, () -> {
					HotelGameTests.put(a, hh[0], 400.0, 1.0, 3.5, 0.0F, 0.0F);
					world.setTimeOfDay(3000);
				});
				if (n == turns.length - 1) {
					HotelGameTests.later(context, base + 130, () -> {
						HotelGameTests.leaveHotel(context, hh[0], a);
						context.complete();
					});
				}
			}
		});
	}

	/**
	 * At every turn of the hotel a local point and its block are the same place: the point in the middle of a local block is in that block's
	 * world block, a world point's local point is back where it was, and a wall's eye (at its face) sits on the face of its wall block, the
	 * corridor side, with the wall block behind it.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_corridor_frame", tickLimit = 40)
	public void everyTurnPutsPointsAndBlocksTogether(TestContext context) {
		BlockPos origin = HotelGameTests.origin(context, 94);
		int[][] cells = {{0, 0, 0}, {13, 29, 2}, {20, 30, 4}, {36, 31, 7}, {5, 3, 12}, {37, 35, 13}, {-1, 0, -5}};
		for (int rot = 0; rot < 4; rot++) {
			for (int[] c : cells) {
				BlockPos block = HotelBuilder.world(origin, rot, c[0], c[1], c[2]);
				for (double[] f : new double[][] {{0.5, 0.5, 0.5}, {0.01, 0.2, 0.99}, {0.99, 0.8, 0.01}}) {
					Vec3d p = HotelBuilder.at(origin, rot, c[0] + f[0], c[1] + f[1], c[2] + f[2]);
					context.assertTrue(BlockPos.ofFloored(p).equals(block), "turn " + rot + ": the point " + (c[0] + f[0]) + "," + (c[1] + f[1]) + "," + (c[2] + f[2])
							+ " is in " + BlockPos.ofFloored(p) + ", its block in " + block);
					Vec3d back = HotelBuilder.localOf(origin, rot, p);
					context.assertTrue(back.squaredDistanceTo(c[0] + f[0], c[1] + f[1], c[2] + f[2]) < 1.0E-12, "turn " + rot + ": local of world is " + back);
				}
			}
			// An eye on the north corridor wall (z 4), at its face z 5: just outside its block, inside the corridor's.
			Vec3d eye = HotelBuilder.at(origin, rot, 18.5, 30.5, 5.0);
			Vec3d into = eye.add(HotelBuilder.dir(rot, 0.0, 0.0, -0.01));
			Vec3d out = eye.add(HotelBuilder.dir(rot, 0.0, 0.0, 0.01));
			context.assertTrue(BlockPos.ofFloored(into).equals(HotelBuilder.world(origin, rot, 18, 30, 4)) && BlockPos.ofFloored(out).equals(HotelBuilder.world(origin, rot, 18, 30, 5)),
					"turn " + rot + ": an eye at the wall's face is not on its wall block");
		}
		context.complete();
	}

	/**
	 * The clocks: before the loop every one keeps the time of day; while it holds the loop floor's read 8:18 (stopped) and the lobby's still
	 * keeps the time; let go, they keep the time again. The floor signs read their floors, 8 while the loop holds, theirs again after.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "h_corridor_clock", tickLimit = 300 + HotelGameTests.READY_WAIT)
	public void theClocksStopAt818OnlyInTheLoop(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerPlayerEntity a = ForeverGameTests.player(context, "clock_watcher");
		HomeBlockEntity h = HotelGameTests.hotel(context, 2, 95);
		HotelPlan plan = HotelPlan.get();
		world.setTimeOfDay(14000);
		HotelGameTests.ready(context, h, () -> {
			HotelGameTests.later(context, 2, () -> {
				// Not looped yet (nobody on 8): every clock keeps the time (14000 is 20:00, eight on the dial).
				for (HotelPlan.P c : plan.clocks) {
					BlockState s = world.getBlockState(HotelBuilder.world(h.origin, h.rot, c.x(), c.y(), c.z()));
					context.assertTrue(s.isOf(HotelBlocks.CLOCK), "no clock at " + c);
					int r = HotelBlocks.reading(world, s);
					context.assertTrue(r != HotelBlocks.STOPPED_AT && r == 8 * 60, "before the loop the clock at " + c + " reads " + r);
				}
				signs(context, h, false);
				HotelGameTests.put(a, h, 20.0, 29.0, 6.5, 0.0F, 0.0F);
			});
			HotelGameTests.later(context, 40, () -> {
				context.assertTrue(h.run.sealed, "the loop did not close");
				int stopped = 0;
				for (HotelPlan.P c : plan.clocks) {
					BlockState s = world.getBlockState(HotelBuilder.world(h.origin, h.rot, c.x(), c.y(), c.z()));
					int r = HotelBlocks.reading(world, s);
					if (HotelPlan.inLoop(c)) {
						context.assertTrue(r == HotelBlocks.STOPPED_AT, "in the loop the clock at " + c + " reads " + r / 60 + ":" + r % 60);
						stopped++;
					} else {
						context.assertTrue(r != HotelBlocks.STOPPED_AT, "the lobby's clock stopped with the loop");
					}
				}
				context.assertTrue(stopped >= 4, "only " + stopped + " clocks in the loop");
				signs(context, h, true);
				HotelGameTests.put(a, h, 400.0, 1.0, 3.5, 0.0F, 0.0F);
			});
			HotelGameTests.later(context, 90, () -> {
				context.assertTrue(!h.run.sealed, "the loop did not let go");
				for (HotelPlan.P c : plan.clocks) {
					BlockState s = world.getBlockState(HotelBuilder.world(h.origin, h.rot, c.x(), c.y(), c.z()));
					context.assertTrue(HotelBlocks.reading(world, s) != HotelBlocks.STOPPED_AT, "after the loop the clock at " + c + " is still stopped");
				}
				signs(context, h, false);
				world.setTimeOfDay(3000);
				HotelGameTests.leaveHotel(context, h, a);
				context.complete();
			});
		});
	}

	private static void signs(TestContext context, HomeBlockEntity h, boolean looped) {
		HotelPlan plan = HotelPlan.get();
		for (int i = 0; i < plan.floorSigns.size(); i++) {
			HotelPlan.P s = plan.floorSigns.get(i);
			BlockState b = HotelGameTests.at(context, h, s.x(), s.y(), s.z());
			int want = looped ? 8 : Math.min(8, i + 1);
			context.assertTrue(b.isOf(HotelBlocks.FLOOR_SIGN) && b.get(HotelBlocks.FloorSign.FLOOR) == want, "the floor sign " + (i + 1) + " reads " + b + ", not " + want);
		}
	}
}
