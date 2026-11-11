package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.hotel.HomeBlockEntity;
import io.github.kortev.chainsawman.hotel.Hotel;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import io.github.kortev.chainsawman.hotel.HotelPlan;
import io.github.kortev.chainsawman.hotel.SquadMemberEntity;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.List;
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
 * -Ptarget=corridor (package 0.1g): the hotel walked in one first-person take, HUD on, the normal lens, nothing cut: in at the front door, up
 * the stairs, round the loop's corridor three times, back into the stairwell and down the stairs twice inside the loop (out on 8 each
 * time), out into the corridor and along it to its end, where the devil tears out of the wall. The real keys (forward, sprint, jump, the use
 * key on the doors). It fails (and says where) if the player is stopped, a move changes the look, or the stairs do not come out on 8.
 *
 * <p>Staged, once: at the corridor's end the devil is made to tear out at once (the hunt for it in the walls is skipped).
 */
final class HotelCorridorWalk {
	private HotelCorridorWalk() {
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
	private static int still;
	private static boolean failed;
	private static int moves;
	private static int stairMoves;
	private static final List<String> REPORT = new ArrayList<>();

	private static volatile int sLaps;
	private static volatile boolean wantFight;
	private static volatile boolean fightSet;

	private static boolean on() {
		String t = System.getProperty("shootingstar.selftest.target", "");
		return "corridor".equals(t) || "corridorlook".equals(t);
	}

	static boolean stills() {
		return "corridorlook".equals(System.getProperty("shootingstar.selftest.target", ""));
	}

	private static volatile boolean wantLaps;
	private static volatile boolean wantEye;

	/** The stills (-Ptarget=corridorlook): where you stand (local feet) and what you look at, close, for checking the look. */
	private static final double[][] SHOTS = {
		{16.5, 29.0, 6.3, 16.5, 30.5, 5.0},
		{15.5, 29.0, 6.4, 15.5, 29.2, 5.0},
		{21.5, 29.0, 6.6, 21.5, 30.0, 8.0},
		{19.5, 29.0, 5.6, 19.5, 31.5, 8.0},
		{13.5, 29.0, 3.5, 16.0, 28.0, 2.0},
		{17.5, 27.0, 3.6, 14.0, 26.4, 3.0},
		{14.2, 25.6, 3.5, 17.0, 27.0, 3.2},
		{30.5, 1.0, 5.6, 30.5, 3.5, 8.0},
	};

	private static void shoot(MinecraftClient c, IntegratedServer s, int i) {
		double[] t = SHOTS[i];
		Vec3d eye = world(t[0], t[1] + 1.62, t[2]);
		Vec3d at = world(t[3], t[4], t[5]);
		float yaw = yawTo(eye, at);
		float pitch = (float) -Math.toDegrees(Math.atan2(at.y - eye.y, Math.hypot(at.x - eye.x, at.z - eye.z)));
		Vec3d w = world(t[0], t[1], t[2]);
		s.execute(() -> s.getPlayerManager().getPlayerList().get(0).teleport(s.getOverworld(), w.x, w.y, w.z, yaw, pitch));
		YOU.yaw = yaw;
		YOU.pitch = pitch;
		YOU.turn = 360.0F;
	}

	private static void still(MinecraftClient c, String name) {
		ShootingStar.LOGGER.info("[corridor] still {}", name);
		net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, "corridor_" + name + ".png", c.getFramebuffer(), m -> {
		});
	}

	/** One client tick of the stills; true when they are done. */
	static boolean look(MinecraftClient c, IntegratedServer s, int f, BlockPos at) {
		origin = at;
		if (c.player == null || c.world == null) {
			return false;
		}
		c.options.hudHidden = true;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(70);
		Capture.camera = null;
		if (f == 2) {
			Vec3d w = world(20.0, 29.0, 6.5);
			s.execute(() -> s.getPlayerManager().getPlayerList().get(0).teleport(s.getOverworld(), w.x, w.y, w.z, -90.0F, 0.0F));
		}
		if (f == 40) {
			wantLaps = true;
			wantEye = true;
		}
		int i = (f - 120) / 40;
		if (f >= 120 && i < SHOTS.length) {
			if ((f - 120) % 40 == 0) {
				shoot(c, s, i);
			}
			if ((f - 120) % 40 == 36) {
				still(c, "after_" + i);
			}
		}
		int end = 120 + SHOTS.length * 40;
		if (f == end) {
			shoot(c, s, 0);
			wantFight = true;
		}
		if (f == end + 5) {
			SHOTS[0] = new double[] {16.0, 29.0, 6.5, 24.0, 30.0, 6.5};
			shoot(c, s, 0);
		}
		if (f == end + 100) {
			still(c, "after_torn");
		}
		YOU.apply(c);
		return f > end + 110;
	}

	/** Every server tick: the hotel's state for the take, the mad survivor kept calm, the devil torn out when asked. */
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
		for (SquadMemberEntity m : w.getEntitiesByClass(SquadMemberEntity.class, new Box(at).expand(60.0), m -> m.isMad())) {
			m.setMad(false);
		}
		if (wantLaps && h.run.sealed) {
			wantLaps = false;
			h.run.laps.put(you.getUuid(), 3);
		}
		if (wantEye && h.run.sealed) {
			wantEye = false;
			io.github.kortev.chainsawman.hotel.FleshGrowthEntity e = new io.github.kortev.chainsawman.hotel.FleshGrowthEntity(io.github.kortev.chainsawman.ChainsawMan.FLESH_GROWTH, w);
			Vec3d p = HotelBuilder.at(origin, 0, 16.5, 30.5, 5.0);
			e.refreshPositionAndAngles(p.x, p.y, p.z, HotelBuilder.alongYaw(0) + 90.0F, 0.0F);
			e.set(io.github.kortev.chainsawman.hotel.FleshGrowthEntity.EYE, 5);
			e.setOpen(true);
			w.spawnEntity(e);
		}
		if (wantFight && !fightSet) {
			fightSet = true;
			Hotel.startFight(w, h, w.getTime());
			ShootingStar.LOGGER.info("[corridor] staged: the devil tears out now");
		}
	}

	private static Vec3d world(double x, double y, double z) {
		return HotelBuilder.at(origin, 0, x, y, z);
	}

	private static Vec3d local(Vec3d w) {
		return HotelBuilder.localOf(origin, 0, w);
	}

	private static float yawTo(Vec3d from, Vec3d to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

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

	/** Down from the loop floor's landing, {@code storeys} times (the loop puts you back on 8's landing at the foot of each). */
	private static List<double[]> down(int storeys) {
		List<double[]> r = new ArrayList<>();
		r.add(new double[] {13.5, 3.5});
		for (int i = 0; i < storeys; i++) {
			r.add(new double[] {13.5, 1.5});
			r.add(new double[] {16.6, 1.5});
			r.add(new double[] {16.6, 3.5});
			// (Right off the last step onto the landing, as a player walking down does.)
			r.add(new double[] {13.35, 3.5});
		}
		return r;
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
		ShootingStar.LOGGER.error("[corridor] FAILED: {}", why);
	}

	private static void next(String what) {
		step++;
		sub = 0;
		ShootingStar.LOGGER.info("[corridor] step {}: {} (frame {})", step, what, Capture.frames());
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
		if (f > 7000) {
			fail("out of time in step " + step);
			return true;
		}
		Vec3d me = local(c.player.getPos());
		if (last != null && step >= 1) {
			Vec3d lp = local(last);
			boolean along = Math.abs(me.x - lp.x) > 6.0;
			boolean upDown = Math.abs(me.y - lp.y) > 3.0 && Math.hypot(me.x - lp.x, me.z - lp.z) < 1.0;
			if (along || upDown) {
				if (upDown) {
					stairMoves++;
				} else {
					moves++;
				}
				String line = String.format("%s move at frame %d: from %s to %s, yaw %.3f -> %.3f, pitch %.3f -> %.3f", upDown ? "stair" : "loop", Capture.frames(),
						fmt(lp), fmt(me), lastYaw, c.player.getYaw(), lastPitch, c.player.getPitch());
				REPORT.add(line);
				ShootingStar.LOGGER.info("[corridor] {}", line);
				if (Math.abs(c.player.getYaw() - lastYaw) > 12.0F) {
					fail("a move changed the look: " + line);
				}
			}
		}
		if (step >= 1 && step != 3 && step != 6 && step != 8 && step < 10 && c.player.getVelocity().horizontalLength() < 0.01) {
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
				YOU.yaw = MathHelper.lerp(0.25F, Float.isNaN(YOU.yaw) ? c.player.getYaw() : YOU.yaw, -90.0F);
				YOU.pitch = MathHelper.lerp(0.2F, YOU.pitch, 0.0F);
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
					next("open the stairwell door and go down, twice");
				}
			}
			case 6 -> {
				YOU.stop();
				YOU.aim(c, world(13.5, 30.6, 4.5));
				if (sub == 10) {
					use(c, 13, 29, 4, Direction.SOUTH);
				}
				if (sub > 16) {
					way = down(2);
					wp = 0;
					next("down the stairs, twice");
				}
			}
			case 7 -> {
				if (wp >= way.size() && (stairMoves < 2 || Math.abs(me.y - 29.0) > 0.1) && sub < 400) {
					// (The last step down: the server's move comes a tick or two after the feet land.)
					YOU.stop();
				} else if (follow(c, false)) {
					if (Math.abs(me.y - 29.0) > 0.1 || stairMoves < 2) {
						fail("down the stairs twice ended at " + fmt(me) + " after " + stairMoves + " stair moves, not on 8");
						return true;
					}
					next("on 8 again: open the door");
				}
			}
			case 8 -> {
				YOU.stop();
				YOU.aim(c, world(13.5, 30.6, 4.5));
				if (sub == 12) {
					use(c, 13, 29, 4, Direction.NORTH);
				}
				if (sub > 20) {
					way = new ArrayList<>(List.of(new double[] {13.5, 5.6}, new double[] {14.2, 6.4}, new double[] {17.4, 6.4}));
					wp = 0;
					next("along the corridor to its end");
				}
			}
			case 9 -> {
				if (follow(c, false)) {
					wantFight = true;
					next("the devil tears out at the corridor's end");
				}
			}
			case 10 -> {
				YOU.stop();
				Vec3d end = world(30.0, 30.4, 6.5);
				YOU.aim(c, end);
				if (sub > 150) {
					next("over");
				}
			}
			default -> {
				YOU.stop();
				if (sub > 10) {
					finish(me);
					YOU.release(c);
					return true;
				}
			}
		}
		YOU.apply(c);
		last = c.player.getPos();
		lastYaw = c.player.getYaw();
		lastPitch = c.player.getPitch();
		return failed;
	}

	private static void finish(Vec3d me) {
		if (moves < 3) {
			fail("only " + moves + " loop moves were seen (laps " + sLaps + ")");
		}
		if (stairMoves < 2) {
			fail("only " + stairMoves + " stair moves were seen");
		}
		ShootingStar.LOGGER.info("[corridor] REPORT: {} loop moves, {} stair moves, laps {}, ended at {}, {}", moves, stairMoves, sLaps, fmt(me), failed ? "FAILED" : "PASSED");
		for (String r : REPORT) {
			ShootingStar.LOGGER.info("[corridor] REPORT {}", r);
		}
	}

	private static String fmt(Vec3d v) {
		return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
	}
}
