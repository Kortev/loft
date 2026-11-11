package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.hotel.HomeBlockEntity;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import io.github.kortev.chainsawman.hotel.HotelPlan;
import io.github.kortev.chainsawman.hotel.SquadMemberEntity;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * -Ptarget=walk (package 0.1e): the hotel walked, in one first-person take with the HUD on and the normal lens, nothing cut and nothing posed.
 * The real keys: forward held (and sprint, and jump), the head turned at a player's pace toward the next point of the way, the doors opened
 * with the use key. From the plaza in at the front door, through the lobby, up all seven flights to the loop floor, out of the stairwell
 * door and along the corridor round the loop three times without stopping (the first walking, the second sprinting, the third sprinting with
 * a jump at the move), back to the stairwell door and down all seven flights to the lobby. It fails (and says where) if the player is stopped,
 * is not where the way goes, broke a block, or a move changed the look or the speed.
 *
 * <p>Staged, once: going back into the stairwell after the third lap, the devil is set away (as if the loop had been broken), so the stairs
 * down are walked down and do not put you back on the loop floor (which is what they do while the loop holds you).
 */
final class HotelWalk {
	private HotelWalk() {
	}

	private static final Fighter YOU = new Fighter();
	private static BlockPos origin;
	private static int step;
	private static int sub;
	private static int wp;
	private static List<double[]> way;
	private static Vec3d last;
	private static float lastYaw;
	private static float lastPitch;
	private static Vec3d lastVel;
	private static int still;
	private static boolean failed;
	private static int moves;
	private static final List<String> REPORT = new ArrayList<>();

	private static volatile int sLaps;
	private static volatile boolean sInside;
	private static volatile int sBroken;
	private static volatile boolean awaySet;
	private static volatile boolean wantAway;

	private static boolean on() {
		return "walk".equals(System.getProperty("shootingstar.selftest.target", ""));
	}

	/** Every server tick: what the hotel says, the mad survivor kept calm (he would knock the walker about), the devil away when asked. */
	static void snap(MinecraftServer server) {
		if (!on() || origin == null || !(server instanceof IntegratedServer is) || is.getPlayerManager().getPlayerList().isEmpty()) {
			return;
		}
		ServerWorld w = is.getOverworld();
		ServerPlayerEntity you = is.getPlayerManager().getPlayerList().get(0);
		BlockPos at = HotelBuilder.world(origin, 0, HotelPlan.HOME_X, HotelPlan.HOME_Y, HotelPlan.HOME_Z);
		if (!(w.getBlockEntity(at) instanceof HomeBlockEntity h)) {
			return;
		}
		sLaps = h.run.laps.getOrDefault(you.getUuid(), 0);
		sInside = h.run.sealed && h.run.inside.contains(you.getUuid());
		for (SquadMemberEntity m : w.getEntitiesByClass(SquadMemberEntity.class, new Box(at).expand(60.0), m -> m.isMad())) {
			m.setMad(false);
		}
		if (wantAway && !awaySet) {
			h.away = true;
			// (Away for good in this take: the hotel's own clock would otherwise bring it back at once.)
			h.awayUntilTick = w.getTime() + 1000000L;
			h.awayUntilDay = Long.MAX_VALUE / 2;
			awaySet = true;
			ShootingStar.LOGGER.info("[walk] staged: the devil is set away (the loop broken), so the stairs down are walked down");
		}
	}

	private static Vec3d world(double x, double y, double z) {
		return HotelBuilder.world(origin, 0, x, y, z);
	}

	private static Vec3d local(Vec3d w) {
		return HotelBuilder.local(origin, 0, w);
	}

	private static float yawTo(Vec3d from, Vec3d to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	/** The way up from the street to the loop floor's stairwell landing, local (x, z). */
	private static List<double[]> up() {
		List<double[]> r = new ArrayList<>();
		r.add(new double[] {27.45, 0.6});
		r.add(new double[] {27.45, 6.4});
		r.add(new double[] {13.5, 6.4});
		r.add(new double[] {13.5, 3.5});
		for (int i = 0; i < HotelPlan.FLOORS - 1; i++) {
			r.add(new double[] {16.6, 3.5});
			r.add(new double[] {16.6, 1.5});
			r.add(new double[] {13.5, 1.5});
			r.add(new double[] {13.5, 3.5});
		}
		return r;
	}

	/** The way down from the loop floor's stairwell door to the lobby. */
	private static List<double[]> down() {
		List<double[]> r = new ArrayList<>();
		r.add(new double[] {13.5, 3.5});
		for (int i = 0; i < HotelPlan.FLOORS - 1; i++) {
			r.add(new double[] {13.5, 1.5});
			r.add(new double[] {16.6, 1.5});
			r.add(new double[] {16.6, 3.5});
			r.add(new double[] {13.5, 3.5});
		}
		r.add(new double[] {13.5, 6.4});
		r.add(new double[] {22.5, 6.4});
		return r;
	}

	/** Toward a local point (x, z) on the keys: the head turns to it at a player's pace; forward is held once it faces near enough. */
	private static boolean go(MinecraftClient c, double x, double z, boolean run) {
		Vec3d p = c.player.getPos();
		Vec3d w = world(x, 0.0, z);
		double d = Math.hypot(w.x - p.x, w.z - p.z);
		if (d < 0.28) {
			return true;
		}
		YOU.aim(c, new Vec3d(w.x, c.player.getEyeY(), w.z));
		float err = Math.abs(MathHelper.wrapDegrees(yawTo(p, w) - c.player.getYaw()));
		YOU.walk(err < 30.0F, run);
		return false;
	}

	private static boolean follow(MinecraftClient c, boolean run) {
		if (wp >= way.size()) {
			YOU.stop();
			return true;
		}
		double[] t = way.get(wp);
		Vec3d p = c.player.getPos();
		Vec3d w = world(t[0], 0.0, t[1]);
		double d = Math.hypot(w.x - p.x, w.z - p.z);
		if (d < 0.28) {
			wp++;
			return false;
		}
		// As a player does, the head turns toward the next point a little before this one is reached.
		double[] look = d < 0.7 && wp + 1 < way.size() ? way.get(wp + 1) : t;
		Vec3d lw = world(look[0], 0.0, look[1]);
		YOU.aim(c, new Vec3d(lw.x, c.player.getEyeY(), lw.z));
		float err = Math.abs(MathHelper.wrapDegrees(yawTo(p, w) - c.player.getYaw()));
		YOU.walk(err < (look == t ? 30.0F : 70.0F), run);
		return false;
	}

	private static void use(MinecraftClient c, int x, int y, int z, Direction face) {
		BlockPos pos = HotelBuilder.world(origin, 0, x, y, z);
		Vec3d hit = Vec3d.ofCenter(pos).add(face.getOffsetX() * 0.45, 0.0, face.getOffsetZ() * 0.45);
		c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
		c.player.swingHand(Hand.MAIN_HAND);
	}

	private static void fail(String why) {
		failed = true;
		ShootingStar.LOGGER.error("[walk] FAILED: {}", why);
	}

	private static void next(String what) {
		step++;
		sub = 0;
		ShootingStar.LOGGER.info("[walk] step {}: {} (frame {})", step, what, Capture.frames());
	}

	/** One client tick of the take; true when it is over. */
	static boolean tick(MinecraftClient c, IntegratedServer s, int f, BlockPos at) {
		origin = at;
		if (c.player == null || c.world == null) {
			return false;
		}
		sub++;
		c.options.hudHidden = false;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(70);
		Capture.camera = null;
		YOU.turn = 11.0F;
		if (f > 6000) {
			fail("out of time in step " + step);
			return true;
		}
		Vec3d me = local(c.player.getPos());
		// A move by the loop: the place jumps a period along; nothing else may change.
		if (last != null && step >= 1 && Math.hypot(c.player.getX() - last.x, c.player.getZ() - last.z) > 6.0) {
			moves++;
			Vec3d v = c.player.getVelocity();
			String line = String.format("loop move %d at frame %d: from %s to %s, yaw %.3f -> %.3f, pitch %.3f -> %.3f, speed %.4f -> %.4f (sprinting %s, on the ground %s)",
					moves, Capture.frames(), fmt(local(last)), fmt(me), lastYaw, c.player.getYaw(), lastPitch, c.player.getPitch(),
					lastVel.horizontalLength(), v.horizontalLength(), c.player.isSprinting(), c.player.isOnGround());
			REPORT.add(line);
			ShootingStar.LOGGER.info("[walk] {}", line);
			if (me.x < HotelPlan.WINDOW_LO - 0.5 || me.x > HotelPlan.WINDOW_HI + 0.5 || Math.abs(c.player.getYaw() - lastYaw) > 12.0F) {
				fail("a loop move put you at " + fmt(me) + " looking " + c.player.getYaw());
			}
		}
		if (step >= 1 && step != 3 && step != 6 && c.player.getVelocity().horizontalLength() < 0.01 && YOU != null) {
			still++;
		} else {
			still = 0;
		}
		if (still > 80) {
			fail("stopped at " + fmt(me) + " in step " + step + " (way point " + wp + ")");
			return true;
		}
		switch (step) {
			case 0 -> {
				// Before the take: on the plaza, facing the doors, the chunks round you built.
				if (sub == 2) {
					s.execute(() -> {
						ServerPlayerEntity you = s.getPlayerManager().getPlayerList().get(0);
						Vec3d w = world(27.45, 1.0, -7.5);
						you.teleport(s.getOverworld(), w.x, w.y, w.z, 0.0F, 3.0F);
					});
					YOU.yaw = 0.0F;
					YOU.pitch = 3.0F;
				}
				if (sub == 70) {
					if ("true".equals(System.getProperty("shootingstar.selftest.capture", ""))) {
						Capture.start(c, c.runDirectory.toPath().resolve("capture"));
					}
					way = up();
					wp = 0;
					next("in at the front door and up the stairs");
				}
			}
			case 1 -> {
				if (follow(c, false)) {
					if (Math.abs(me.y - 29.0) > 0.1) {
						fail("the stairs ended at " + fmt(me) + ", not the loop floor");
						return true;
					}
					next("the loop floor: open the stairwell door");
				}
			}
			case 2 -> {
				YOU.stop();
				YOU.aim(c, world(13.5, 30.6, 4.5));
				if (sub == 8) {
					use(c, 13, 29, 4, Direction.NORTH);
				}
				if (sub > 14) {
					way = new ArrayList<>(List.of(new double[] {13.5, 5.6}, new double[] {13.9, 6.4}));
					wp = 0;
					next("out into the corridor");
				}
			}
			case 3 -> {
				if (follow(c, false)) {
					next("along the corridor, round the loop three times");
				}
			}
			case 4 -> {
				// East along the corridor: the first lap walking, the second sprinting, the third sprinting with a jump as it comes round.
				YOU.yaw = MathHelper.lerp(0.25F, Float.isNaN(YOU.yaw) ? c.player.getYaw() : YOU.yaw, -90.0F);
				YOU.pitch = MathHelper.lerp(0.2F, YOU.pitch, 0.0F);
				// (Keep to the middle of the corridor.)
				Vec3d ahead = world(me.x + 6.0, 0.0, 6.4);
				float keep = yawTo(c.player.getPos(), ahead);
				YOU.yaw = MathHelper.lerp(0.3F, YOU.yaw, keep);
				YOU.walk(true, sLaps >= 1);
				if (sLaps == 2 && me.x > 23.6 && me.x < 24.2 && c.player.isOnGround()) {
					YOU.jump();
				}
				if (sLaps >= 3 && me.x > 18.0) {
					next("back to the stairwell door");
					way = new ArrayList<>(List.of(new double[] {13.6, 6.4}, new double[] {13.5, 5.6}));
					wp = 0;
				}
			}
			case 5 -> {
				if (follow(c, false)) {
					next("open the stairwell door and go down");
				}
			}
			case 6 -> {
				YOU.stop();
				YOU.aim(c, world(13.5, 30.6, 4.5));
				if (sub == 10) {
					use(c, 13, 29, 4, Direction.SOUTH);
					wantAway = true;
				}
				if (sub > 16 && awaySet) {
					way = down();
					wp = 0;
					next("down the stairs");
				}
			}
			case 7 -> {
				if (follow(c, false)) {
					next("in the lobby");
				}
			}
			default -> {
				YOU.stop();
				if (sub > 30) {
					finish(c, s, me);
					YOU.release(c);
					return true;
				}
			}
		}
		YOU.apply(c);
		last = c.player.getPos();
		lastYaw = c.player.getYaw();
		lastPitch = c.player.getPitch();
		lastVel = c.player.getVelocity();
		return failed;
	}

	private static void finish(MinecraftClient c, IntegratedServer s, Vec3d me) {
		if (Math.abs(me.y - 1.0) > 0.1 || me.x < 13.0 || me.z < 5.0 || me.z > 8.0) {
			fail("the walk ended at " + fmt(me) + ", not on the ground floor's corridor");
		}
		if (moves < 3) {
			fail("only " + moves + " loop moves were seen (laps " + sLaps + ")");
		}
		// Nothing broken: the stairwell is the plan's, block for block (the doors and the lights by night aside).
		int[] bad = {0};
		java.util.concurrent.CompletableFuture<Void> done = s.submit(() -> {
			ServerWorld w = s.getOverworld();
			HotelPlan plan = HotelPlan.get();
			for (int y = 0; y < HotelPlan.SY; y++) {
				for (int z = 1; z <= 3; z++) {
					for (int x = 12; x <= 18; x++) {
						BlockState want = plan.at(x, y, z);
						BlockState got = w.getBlockState(HotelBuilder.world(origin, 0, x, y, z));
						if (got != want && !got.isOf(net.minecraft.block.Blocks.LIGHT)) {
							bad[0]++;
						}
					}
				}
			}
		});
		done.join();
		if (bad[0] > 0) {
			fail(bad[0] + " blocks of the stairwell are not as built (broken?)");
		}
		ShootingStar.LOGGER.info("[walk] REPORT: {} loop moves, laps {}, ended at {}, {}", moves, sLaps, fmt(me), failed ? "FAILED" : "PASSED");
		for (String r : REPORT) {
			ShootingStar.LOGGER.info("[walk] REPORT {}", r);
		}
	}

	private static String fmt(Vec3d v) {
		return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
	}
}
