package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.client.ClientHotel;
import io.github.kortev.chainsawman.client.ContractScreen;
import io.github.kortev.chainsawman.client.FleshMesh;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.chainsawman.hotel.EternityDevilEntity;
import io.github.kortev.chainsawman.hotel.HomeBlockEntity;
import io.github.kortev.chainsawman.hotel.Hotel;
import io.github.kortev.chainsawman.hotel.HotelBuilder;
import io.github.kortev.chainsawman.hotel.HotelPlan;
import io.github.kortev.chainsawman.hotel.Shaft;
import io.github.kortev.chainsawman.hotel.ShaftArmEntity;
import io.github.kortev.chainsawman.hotel.ShaftCoreEntity;
import io.github.kortev.chainsawman.hotel.ShaftHeadEntity;
import io.github.kortev.chainsawman.network.HotelPayloads;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * -Ptarget=played: the Eternity Devil's fight played, in one first-person take with the HUD on and the normal lens. Nothing is posed: the
 * {@link Fighter} cuts the devil in the corridor (the real attack key's path, the saws), the floor turns, it falls down the shaft into the
 * mouth and lands in the arena inside the devil, and fights there as a player would: it watches each attack's tell and steps out of it (the
 * slam's lane, the head's line, the squeezing half, the hand's reach), punishes what missed, cuts the walls and growths in between, takes a
 * hit once and is grabbed once (and cuts itself free), until the bar fills and it begs; refuses, cuts the core.
 *
 * <p>With -Pcapture=true (the film) the take is kept short: the bar starts part filled in the arena ({@link #FILM_FROM}) and fills
 * {@link #FILM_PACE} times faster, so it still passes both of the arena's stages and the ending in under a minute. Without it the fight is
 * played at its real length (the numbers to measure). It logs each attack, dodge and hit, each phase's time, how much of the time there was
 * something to cut within reach, the longest stretch with nothing, and where the pain came from.
 */
final class HotelFight {
	private HotelFight() {
	}

	/** The film's bar: where it starts in the arena, and how much faster it fills. */
	static final float FILM_FROM = 0.5F;
	static final float FILM_PACE = 4.9F;

	private static final Fighter YOU = new Fighter();
	private static BlockPos origin;
	private static int step;
	private static int sub;
	private static boolean capturing;
	private static boolean film;

	// What the server says, read every server tick.
	private static volatile boolean sInside;
	private static volatile int sDevilAge = -1;
	private static volatile boolean sGrown;
	private static volatile long sFightAt = -1;
	private static volatile boolean sTilting;
	private static volatile boolean sBuilt;
	private static volatile boolean sBegging;
	private static volatile boolean sCuttable;
	private static volatile boolean sDead;
	private static volatile boolean sMended;
	private static volatile float sPain;
	private static volatile String sPainBy = "";
	private static volatile long sNow;
	private static volatile String sCore = "";
	private static volatile int sStage;
	private static volatile java.util.UUID devilId;

	// The numbers.
	private static long grownAt = -1;
	private static long firstCutAt = -1;
	private static long tiltAt = -1;
	private static long shaftAt = -1;
	private static long landAt = -1;
	private static long stage1At = -1;
	private static long stage2At = -1;
	private static long begAt = -1;
	private static long coreCutAt = -1;
	private static long deadAt = -1;
	private static int arenaTicks;
	private static int arenaReach;
	private static int idle;
	private static int longestIdle;
	private static long longestIdleEnd = -1;
	private static int dodges;
	private static int hitsTaken;
	private static int punishes;
	private static float lastHealth = -1.0F;
	private static long hurtSince = -1;
	private static final Map<Integer, String> PLANS = new HashMap<>();
	private static final Map<String, Integer> SEEN = new HashMap<>();
	private static long lastSqueeze = -1;

	static void snap(MinecraftServer server) {
		if (!"played".equals(System.getProperty("shootingstar.selftest.target", "")) || origin == null || !(server instanceof IntegratedServer is)
				|| is.getPlayerManager().getPlayerList().isEmpty()) {
			return;
		}
		HomeBlockEntity h = home(is);
		if (h == null) {
			return;
		}
		ServerWorld w = is.getOverworld();
		ServerPlayerEntity you = you(is);
		sNow = w.getTime();
		sInside = h.run.sealed && h.run.inside.contains(you.getUuid());
		EternityDevilEntity d = h.run.devil == null || !(w.getEntity(h.run.devil) instanceof EternityDevilEntity e) ? null : e;
		sDevilAge = d == null ? -1 : d.age;
		sGrown = d != null && !d.growing();
		sFightAt = h.run.fightAt;
		sTilting = h.run.shaft.tiltAt >= 0;
		sBuilt = h.run.shaft.built;
		sBegging = sBegging || d != null && d.begStarted;
		sCuttable = d != null && d.core() != null && d.core().cuttable();
		if (d != null) {
			sPain = d.pain();
			sStage = Shaft.stage(d);
			float[] p = d.painBy;
			float bar = d.getMaxHealth() * (1.0F - EternityDevilEntity.BEG_AT) / 100.0F;
			sPainBy = String.format("corridor %.1f%%, walls and growths %.1f%%, heads %.1f%%, arms %.1f%% of the bar (raw health: %.0f, %.0f, %.0f, %.0f)", p[0] / bar,
					p[1] / bar, p[2] / bar, p[3] / bar, p[0], p[1], p[2], p[3]);
			devilId = d.getUuid();
		}
		// (Once it dies the home forgets it: it is followed by its own id.)
		if (devilId != null && sBegging && !(w.getEntity(devilId) instanceof EternityDevilEntity e2 && e2.isAlive())) {
			sDead = true;
		}
		sMended = sDead && h.shaft == null && h.arena == null;
		ShaftCoreEntity core = d == null ? null : d.core();
		sCore = core == null ? "none" : "at " + local(core.getPos()) + " cuttable " + core.cuttable();
	}

	private static Vec3d world(double x, double y, double z) {
		return HotelBuilder.at(origin, 0, x, y, z);
	}

	private static Vec3d local(Vec3d w) {
		return HotelBuilder.localOf(origin, 0, w);
	}

	private static ServerPlayerEntity you(IntegratedServer s) {
		return s.getPlayerManager().getPlayerList().get(0);
	}

	private static HomeBlockEntity home(IntegratedServer s) {
		BlockPos at = HotelBuilder.world(origin, 0, HotelPlan.HOME_X, HotelPlan.HOME_Y, HotelPlan.HOME_Z);
		return s.getOverworld().getBlockEntity(at) instanceof HomeBlockEntity h ? h : null;
	}

	private static void still(MinecraftClient c, String name) {
		ShootingStar.LOGGER.info("[played] still {} at {}", name, t(sNow));
		ScreenshotRecorder.saveScreenshot(c.runDirectory, "played_" + name + ".png", c.getFramebuffer(), m -> {
		});
	}

	private static void next() {
		step++;
		sub = 0;
		ShootingStar.LOGGER.info("[played] step {} at server tick {} (fight t {})", step, sNow, sFightAt < 0 ? -1 : sNow - sFightAt);
	}

	private static String t(long at) {
		return at < 0 || sFightAt < 0 ? "-" : String.format("%.1f s", (at - sFightAt) / 20.0);
	}

	// ---- What is about.

	private static EternityDevilEntity devil(MinecraftClient c) {
		for (Entity e : c.world.getEntities()) {
			if (e instanceof EternityDevilEntity d && d.isAlive()) {
				return d;
			}
		}
		return null;
	}

	private static double reachTo(MinecraftClient c, Box b) {
		Vec3d eye = c.player.getEyePos();
		Vec3d near = new Vec3d(Math.max(b.minX, Math.min(eye.x, b.maxX)), Math.max(b.minY, Math.min(eye.y, b.maxY)), Math.max(b.minZ, Math.min(eye.z, b.maxZ)));
		return near.distanceTo(eye);
	}

	private static Vec3d nearest(MinecraftClient c, Box b) {
		Vec3d eye = c.player.getEyePos();
		return new Vec3d(Math.max(b.minX, Math.min(eye.x, b.maxX)), Math.max(b.minY, Math.min(eye.y, b.maxY)), Math.max(b.minZ, Math.min(eye.z, b.maxZ)));
	}

	/** The nearest wall of the arena (local): its distance from {@code me}, and its inward normal (x, z). */
	private static double[] nearestWall(Vec3d me) {
		double[][] w = {{me.x - Shaft.AX0, 1, 0}, {Shaft.AX1 + 1 - me.x, -1, 0}, {me.z - Shaft.AZ0, 0, 1}, {Shaft.AZ1 + 1 - me.z, 0, -1}};
		double[] best = w[0];
		for (double[] x : w) {
			if (x[0] < best[0]) {
				best = x;
			}
		}
		return best;
	}

	private static Vec3d clampIn(Vec3d lp) {
		return new Vec3d(MathHelper.clamp(lp.x, Shaft.AX0 + 1.0, Shaft.AX1), lp.y, MathHelper.clamp(lp.z, Shaft.AZ0 + 1.0, Shaft.AZ1));
	}

	/** Something of the devil's to cut within the saws' reach: a part, or its walls and growths (the floor is not its flesh to cut). */
	private static boolean somethingInReach(MinecraftClient c) {
		double reach = io.github.kortev.chainsawman.form.Saw.SLASH_REACH + 0.4;
		for (Entity e : c.world.getEntities()) {
			if ((e instanceof ShaftHeadEntity || e instanceof ShaftArmEntity || e instanceof ShaftCoreEntity) && e.canHit() && reachTo(c, e.getBoundingBox()) <= reach) {
				return true;
			}
		}
		Vec3d me = local(c.player.getPos());
		if (!Shaft.inArena(me)) {
			return false;
		}
		return false;
	}

	/** A threat to you: where to stand to be clear of it (local), and how soon it lands (ticks). */
	private record Threat(Entity source, String kind, Vec3d safe, float soon, boolean danger) {
	}

	private static Threat slamThreat(MinecraftClient c, ShaftArmEntity a, Vec3d me) {
		Vec3d d = a.dir();
		Vec3d rel = c.player.getPos().subtract(a.getPos());
		double along = rel.dotProduct(d);
		double acrossSigned = rel.x * d.z - rel.z * d.x;
		boolean in = along > -0.6 && along < a.len() + 0.6 && Math.abs(acrossSigned) < ShaftArmEntity.LANE / 2.0 + 0.7;
		Vec3d side = new Vec3d(d.z, 0.0, -d.x);
		Vec3d best = null;
		for (double s : new double[] {Math.signum(acrossSigned) == 0 ? 1.0 : Math.signum(acrossSigned), -Math.signum(acrossSigned)}) {
			Vec3d w = a.getPos().add(d.multiply(MathHelper.clamp(along, 0.5, a.len() - 0.5))).add(side.multiply(s * 2.7));
			Vec3d l = local(w);
			if (l.x > Shaft.AX0 + 0.7 && l.x < Shaft.AX1 + 0.3 && l.z > Shaft.AZ0 + 0.7 && l.z < Shaft.AZ1 + 0.3) {
				best = l;
				break;
			}
		}
		if (best == null) {
			best = clampIn(local(a.getPos().add(d.multiply(a.len() + 1.5))));
		}
		return new Threat(a, "slam", best, ShaftArmEntity.SLAM_TELL + ShaftArmEntity.SLAM_FALL - a.age(0.0F), in);
	}

	private static Threat headThreat(MinecraftClient c, ShaftHeadEntity e, Vec3d me) {
		if (!e.locked()) {
			return new Threat(e, "head", me, ShaftHeadEntity.STRIKE - e.age(0.0F), false);
		}
		Vec3d d = e.dir();
		Vec3d rel = c.player.getPos().subtract(e.getPos());
		double along = rel.dotProduct(d);
		double across = rel.x * d.z - rel.z * d.x;
		boolean in = along > -0.3 && along < ShaftHeadEntity.REACH + 1.0 && Math.abs(across) < ShaftHeadEntity.BITE_RADIUS + 0.8;
		Vec3d side = new Vec3d(d.z, 0.0, -d.x);
		Vec3d best = null;
		for (double s : new double[] {across >= 0 ? 1.0 : -1.0, across >= 0 ? -1.0 : 1.0}) {
			Vec3d l = local(c.player.getPos().add(side.multiply(s * (2.6 - Math.abs(across) * 0.5))));
			if (l.x > Shaft.AX0 + 0.7 && l.x < Shaft.AX1 + 0.3 && l.z > Shaft.AZ0 + 0.7 && l.z < Shaft.AZ1 + 0.3) {
				best = l;
				break;
			}
		}
		return new Threat(e, "head", best == null ? clampIn(me) : best, ShaftHeadEntity.STRIKE - e.age(0.0F), in);
	}

	private static Threat grabThreat(MinecraftClient c, ShaftArmEntity a, Vec3d me) {
		Vec3d rel = c.player.getPos().subtract(a.getPos());
		double out = rel.dotProduct(a.dir());
		boolean in = Math.hypot(rel.x, rel.z) < ShaftArmEntity.REACH_GRAB + ShaftArmEntity.HAND + 0.9;
		Vec3d safe = local(c.player.getPos().add(a.dir().multiply(ShaftArmEntity.REACH_GRAB + ShaftArmEntity.HAND + 2.0 - out)));
		return new Threat(a, "grab", clampIn(safe), ShaftArmEntity.GRAB_TELL - a.age(0.0F), in);
	}

	/** One tick in the arena: dodge what is coming (or take it, as planned), punish what missed, and cut. */
	private static void arena(MinecraftClient c, EternityDevilEntity d) {
		Vec3d me = local(c.player.getPos());
		long now = c.world.getTime();
		// Held by a hand: cut the arm (its grip) until free.
		for (Entity e : c.world.getEntities()) {
			if (e instanceof ShaftArmEntity a && a.mode() == ShaftArmEntity.GRAB && a.holding() && a.getBoundingBox().expand(1.5).contains(c.player.getPos())) {
				YOU.stop();
				YOU.aim(c, a.getBoundingBox().getCenter().add(0.0, -0.3, 0.0));
				YOU.swing(c, 0.9F);
				return;
			}
		}
		// What is coming, and what to do about each (decided once, as it shows).
		Threat soonest = null;
		Entity stunned = null;
		double stunnedD = Double.MAX_VALUE;
		for (Entity e : c.world.getEntities()) {
			Threat th = null;
			if (e instanceof ShaftArmEntity a && a.mode() == ShaftArmEntity.SLAM) {
				if (a.outcome() == ShaftArmEntity.PENDING) {
					th = slamThreat(c, a, me);
				} else if (a.stunned()) {
					double dd = reachTo(c, a.getBoundingBox());
					if (dd < stunnedD) {
						stunnedD = dd;
						stunned = a;
					}
				}
			} else if (e instanceof ShaftArmEntity a && a.mode() == ShaftArmEntity.GRAB) {
				if (a.outcome() == ShaftArmEntity.PENDING) {
					th = grabThreat(c, a, me);
				} else if (a.stunned()) {
					double dd = reachTo(c, a.getBoundingBox());
					if (dd < stunnedD) {
						stunnedD = dd;
						stunned = a;
					}
				}
			} else if (e instanceof ShaftHeadEntity hd) {
				if (hd.outcome() == ShaftHeadEntity.PENDING) {
					th = headThreat(c, hd, me);
				} else if (hd.stunned()) {
					double dd = reachTo(c, hd.getBoundingBox());
					if (dd < stunnedD) {
						stunnedD = dd;
						stunned = hd;
					}
				}
			}
			if (th != null) {
				String kind = th.kind();
				String plan = PLANS.computeIfAbsent(e.getId(), k -> plan(kind));
				if ("take".equals(plan)) {
					continue;
				}
				if (th.danger() && (soonest == null || th.soon() < soonest.soon())) {
					soonest = th;
				}
			}
		}
		// The squeeze (told by its event): out of its half.
		for (ClientHotel.Event e : ClientHotel.events(d.getId())) {
			if (e.kind() == HotelPayloads.Devil.SQUEEZE && now - e.start() < Shaft.SQUEEZE_TELL + 2) {
				if (e.start() != lastSqueeze) {
					lastSqueeze = e.start();
					String plan = plan("squeeze");
					ShootingStar.LOGGER.info("[played] squeeze ({}) at {}", plan, t(sNow));
				}
				boolean in = Shaft.inSqueeze(ClientHotel.squeezeHalf(e), me) || Math.abs(me.x - (ClientHotel.squeezeHalf(e) == 0 ? Shaft.AX0 + Shaft.SQUEEZE_REACH : Shaft.AX1 + 1 - Shaft.SQUEEZE_REACH)) < 1.2;
				double sx = ClientHotel.squeezeHalf(e) == 0 ? Shaft.AX0 + Shaft.SQUEEZE_REACH + 2.4 : Shaft.AX1 + 1 - Shaft.SQUEEZE_REACH - 2.4;
				Threat th = new Threat(null, "squeeze", new Vec3d(sx, me.y, me.z), Shaft.SQUEEZE_TELL - (now - e.start()), in);
				if (in && (soonest == null || !soonest.danger() || th.soon() < soonest.soon())) {
					soonest = th;
				}
			}
		}
		if (soonest != null && soonest.danger()) {
			// Out of it: moving there, watching what comes (and cutting what is in reach on the way).
			boolean safe = YOU.strafeTo(c, world(soonest.safe().x, me.y, soonest.safe().z), 0.25, true);
			if (soonest.source() != null) {
				Vec3d look = soonest.source() instanceof ShaftHeadEntity hd ? hd.headAt(hd.age(0.0F)) : soonest.source() instanceof ShaftArmEntity a && a.mode() == ShaftArmEntity.GRAB
						? a.handAt(a.age(0.0F)) : soonest.source().getPos().add(((ShaftArmEntity) soonest.source()).dir().multiply(3.0)).add(0.0, 0.6, 0.0);
				YOU.aim(c, look);
			} else {
				// (The squeeze: watching the half that bulges, its line lit on the floor.)
				YOU.aim(c, world(soonest.safe().x < Shaft.MID_X ? Shaft.AX1 - 1.0 : Shaft.AX0 + 2.0, Shaft.FLOOR + 1.0, Shaft.MID_Z));
			}
			if (soonest.source() instanceof ShaftArmEntity a && a.mode() == ShaftArmEntity.GRAB && a.age(0.0F) >= ShaftArmEntity.GRAB_TELL - 1) {
				// The hand sweeping in low: jump it as it comes.
				Vec3d in = a.dir();
				Vec3d left = new Vec3d(in.z, 0.0, -in.x);
				Vec3d rel = c.player.getPos().subtract(a.getPos());
				double pa = Math.toDegrees(Math.atan2(rel.dotProduct(left), rel.dotProduct(in)));
				double ahead = (pa - a.angleAt(a.age(0.0F))) * a.sweepDir();
				if (ahead > 0.0 && ahead < 42.0 && c.player.isOnGround()) {
					YOU.jump();
				}
			}
			if (!safe && sub % 20 == 0) {
				ShootingStar.LOGGER.info("[played] dodging a {} at {}", soonest.kind(), t(sNow));
			}
			if (somethingInReach(c)) {
				YOU.swing(c, 0.9F);
			}
			return;
		}
		// Something lies stunned after a miss: in on it and cut it.
		if (stunned != null) {
			Vec3d at = nearest(c, stunned.getBoundingBox());
			Vec3d l = local(at);
			if (stunnedD > 3.1) {
				YOU.aim(c, at);
				YOU.strafeTo(c, world(l.x, me.y, l.z), 2.6, true);
			} else {
				YOU.stop();
				YOU.aim(c, at.add(0.0, -0.1, 0.0));
			}
			if (stunnedD < 3.6) {
				YOU.swing(c, 0.9F);
				if (PLANS.putIfAbsent(-stunned.getId(), "punished") == null) {
					punishes++;
					ShootingStar.LOGGER.info("[played] punishing a miss at {}", t(sNow));
				}
			}
			return;
		}
		// Nothing coming: cut its flesh. Close to the nearest wall, at a slant along it (so the room is in sight), the saws in it.
		double[] w = nearestWall(me);
		Vec3d n = new Vec3d(w[1], 0.0, w[2]);
		Vec3d along = new Vec3d(-n.z, 0.0, n.x);
		double sweep = Math.sin(now * 0.045) >= 0 ? 1.0 : -1.0;
		Vec3d onWall = me.subtract(n.multiply(w[0])).add(along.multiply(2.6 * sweep));
		Vec3d target = world(onWall.x, Shaft.FLOOR + 1.0 + 0.5 * Math.sin(now * 0.11), onWall.z);
		YOU.aim(c, target);
		if (w[0] > 2.4) {
			YOU.strafeTo(c, world(me.x - n.x * (w[0] - 2.0), me.y, me.z - n.z * (w[0] - 2.0)), 0.3, false);
		} else if (w[0] < 1.4) {
			YOU.strafeTo(c, world(me.x + n.x * (2.0 - w[0]), me.y, me.z + n.z * (2.0 - w[0])), 0.3, false);
		} else {
			YOU.stop();
		}
		if (somethingInReach(c)) {
			YOU.swing(c, 0.9F);
		}
	}

	/** What to do about an attack of {@code kind} as it shows: dodge it, or (once each, for the film's sake) stand and take it. */
	private static String plan(String kind) {
		int n = SEEN.merge(kind, 1, Integer::sum);
		String p = "dodge";
		if (kind.equals("slam") && n == 2 || kind.equals("grab") && n == 2) {
			p = "take";
		}
		if (p.equals("dodge")) {
			dodges++;
		}
		ShootingStar.LOGGER.info("[played] {} #{} shows at {}: {}", kind, n, t(sNow), p);
		return p;
	}

	/** One tick of the numbers in the arena. */
	private static void count(MinecraftClient c) {
		boolean reach = somethingInReach(c);
		arenaTicks++;
		if (reach) {
			arenaReach++;
			idle = 0;
		} else if (++idle > longestIdle) {
			longestIdle = idle;
			longestIdleEnd = sNow;
		}
		float hp = c.player.getHealth();
		if (lastHealth >= 0.0F && hp < lastHealth - 0.5F && sNow - hurtSince > 10) {
			hurtSince = sNow;
			hitsTaken++;
			ShootingStar.LOGGER.info("[played] hit, {} to {}, at {}", lastHealth, hp, t(sNow));
		}
		lastHealth = hp;
	}

	/** Cut whatever is in reach (aimed at), in the corridor. */
	private static boolean cutCorridor(MinecraftClient c, EternityDevilEntity d) {
		if (d == null || !d.fightable() || reachTo(c, d.getBoundingBox()) > io.github.kortev.chainsawman.form.Saw.SLASH_REACH) {
			return false;
		}
		float now = c.world.getTime();
		YOU.aim(c, world(EternityDevilEntity.HEART_X, EternityDevilEntity.HEART_Y + 0.6 * Math.sin(now * 0.21), EternityDevilEntity.HEART_Z + 2.2 * Math.sin(now * 0.07)));
		YOU.swing(c, 0.9F);
		if (firstCutAt < 0) {
			firstCutAt = sNow;
		}
		return true;
	}

	/** One client tick of the take; true when it is over. */
	static boolean tick(MinecraftClient c, IntegratedServer s, int f, BlockPos at) {
		origin = at;
		if (c.player == null || c.world == null) {
			return false;
		}
		film = "true".equals(System.getProperty("shootingstar.selftest.capture", ""));
		sub++;
		c.options.hudHidden = false;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(70);
		c.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
		Capture.camera = null;
		if (f > (film ? 4000 : 12000)) {
			ShootingStar.LOGGER.error("[played] stuck in step {} (core {})", step, sCore);
			report();
			return true;
		}
		double corridorY = HotelPlan.floorY(7) + 1.0;
		float east = HotelBuilder.alongYaw(0);
		EternityDevilEntity d = devil(c);
		switch (step) {
			case 0 -> {
				// Before the take: on the loop floor until the loop closes on you; then the fight, in the form.
				if (sub % 8 == 1) {
					s.execute(() -> {
						Vec3d w = world(18.5, corridorY, 6.5);
						you(s).teleport(s.getOverworld(), w.x, w.y, w.z, east, 0.0F);
					});
				}
				if (sInside && sub > 20) {
					s.execute(() -> {
						ServerWorld w = s.getOverworld();
						HomeBlockEntity h = home(s);
						for (EternityDevilEntity e : w.getEntitiesByType(io.github.kortev.chainsawman.ChainsawMan.ETERNITY_DEVIL, e -> true)) {
							e.discard();
						}
						if (h.run.fight) {
							h.run.fight = false;
							io.github.kortev.chainsawman.hotel.Arena.mend(w, h);
						}
						h.run.shaftOn = true;
						ServerPlayerEntity you = you(s);
						Heart.give(you);
						Blood.set(you, Blood.MAX);
						if (!Form.on(you)) {
							Form.start(you);
						}
						for (io.github.kortev.chainsawman.hotel.SquadMemberEntity m : io.github.kortev.chainsawman.hotel.Squad.members(w, h)) {
							m.discard();
						}
						h.run.squad.clear();
						Vec3d p = world(25.5, corridorY, 6.5);
						you.teleport(w, p.x, p.y, p.z, east, 0.0F);
						EternityDevilEntity.painPace = 1.0F;
						Hotel.startFight(w, h, w.getTime());
					});
					next();
				}
			}
			case 1 -> {
				// The devil tears out of the wall at the end of the corridor.
				YOU.aim(c, world(34.0, 31.6, 6.6));
				if (sGrown && grownAt < 0) {
					grownAt = sNow;
					next();
				}
			}
			case 2 -> {
				// Run up to it and cut: in the film, the take starts a moment before the floor turns.
				if (!capturing && film && (sPain >= 0.33F || sub > 400)) {
					capturing = true;
					Capture.start(c, c.runDirectory.toPath().resolve("capture"));
				}
				Vec3d me = local(c.player.getPos());
				if (!cutCorridor(c, d)) {
					YOU.moveTo(c, world(31.2, corridorY, me.z), 0.3, true);
				} else {
					YOU.walk(me.x < 30.6, false);
				}
				if (sTilting) {
					tiltAt = sNow;
					ShootingStar.LOGGER.info("[played] the floor turns at pain {}", sPain);
					next();
				}
			}
			case 3 -> {
				// The floor turns: you slide down the corridor toward it.
				YOU.stop();
				cutCorridor(c, d);
				if (sub == 22) {
					still(c, "a_tilt");
				}
				if (sBuilt) {
					shaftAt = sNow;
					next();
				}
			}
			case 4 -> {
				// Falling down the shaft: looking down into the mouth, through it, and landing in the arena.
				YOU.stop();
				Vec3d me = local(c.player.getPos());
				if (me.y > Shaft.BASE - 1.0) {
					YOU.aim(c, world(Shaft.MID_X + 0.3, Shaft.BASE - 6.0, Shaft.MID_Z + 0.2));
				} else {
					YOU.aim(c, world(Shaft.AX1 - 1.0, Shaft.FLOOR + 2.0, Shaft.MID_Z + 1.5));
				}
				if (sub == 6) {
					still(c, "b_falling");
				}
				if (me.y < Shaft.BASE + 3.0 && me.y > Shaft.BASE - 2.0 && sub % 3 == 0) {
					still(c, "c_mouth_" + sub);
				}
				if (Shaft.inArena(me) && c.player.isOnGround()) {
					landAt = sNow;
					if (film) {
						s.execute(() -> {
							EternityDevilEntity dv = h(s);
							if (dv != null) {
								float line = dv.getMaxHealth() * (1.0F - EternityDevilEntity.BEG_AT);
								dv.addPain(line * FILM_FROM - (dv.getMaxHealth() - dv.getHealth()), EternityDevilEntity.P_CORRIDOR);
							}
							EternityDevilEntity.painPace = FILM_PACE;
						});
					}
					FleshMesh.costReport();
					next();
				}
			}
			case 5 -> {
				// The arena: dodge, punish, cut, until it can bear no more.
				count(c);
				if (d != null) {
					arena(c, d);
				}
				if (sub == 4) {
					still(c, "d_landed");
				}
				if (sub == 60) {
					ShootingStar.LOGGER.info("[played] frame cost, in the arena: {}", FleshMesh.costReport());
				}
				if (sStage >= 1 && stage1At < 0) {
					stage1At = sNow;
					ShootingStar.LOGGER.info("[played] stage 1 at {}", t(sNow));
				}
				if (sStage >= 2 && stage2At < 0) {
					stage2At = sNow;
					ShootingStar.LOGGER.info("[played] stage 2 at {}", t(sNow));
					FleshMesh.costReport();
				}
				if (stage2At > 0 && sNow - stage2At == 60) {
					ShootingStar.LOGGER.info("[played] frame cost, in the arena at its wildest: {}", FleshMesh.costReport());
				}
				if (sub % 40 == 20) {
					still(c, "e_arena_" + sub);
				}
				if (sBegging) {
					begAt = sNow;
					ShootingStar.LOGGER.info("[played] it begs at {}; pain by source: {}", t(sNow), sPainBy);
					next();
				}
			}
			case 6 -> {
				// It begs: the room convulses, the core pushed up in the middle, the deal on the screen; refused.
				YOU.stop();
				Entity core = coreOf(c);
				YOU.aim(c, core != null ? core.getBoundingBox().getCenter() : world(Shaft.MID_X, Shaft.FLOOR + 0.8, Shaft.MID_Z));
				if (sub == 8) {
					still(c, "f_convulse");
				}
				if (c.currentScreen instanceof ContractScreen && sub == 34) {
					still(c, "g_offer");
				}
				if (sub == 46) {
					s.execute(() -> Hotel.answer(you(s), false));
				}
				if (sCuttable && sub > 48) {
					next();
				}
			}
			case 7 -> {
				// The core held up in the middle: cut it.
				Entity core = coreOf(c);
				if (core != null) {
					YOU.aim(c, core.getBoundingBox().getCenter());
					if (reachTo(c, core.getBoundingBox()) > 3.4) {
						YOU.strafeTo(c, core.getPos(), 4.6, false);
					} else {
						YOU.stop();
						YOU.swing(c, 0.9F);
					}
				}
				if (sub == 4) {
					still(c, "h_core");
				}
				if (sDead) {
					coreCutAt = sNow;
					YOU.stop();
					next();
				}
			}
			case 8 -> {
				// The collapse; the hotel put back (you in the corridor, the loot in your pack); the take ends.
				if (deadAt < 0) {
					deadAt = sNow;
				}
				YOU.stop();
				if (sub == 16) {
					still(c, "i_collapse");
				}
				if (sMended && sub > 70 || sub > 160) {
					report();
					YOU.release(c);
					EternityDevilEntity.painPace = 1.0F;
					return true;
				}
			}
			default -> {
			}
		}
		YOU.apply(c);
		return false;
	}

	private static Entity coreOf(MinecraftClient c) {
		for (Entity e : c.world.getEntities()) {
			if (e instanceof ShaftCoreEntity) {
				return e;
			}
		}
		return null;
	}

	private static EternityDevilEntity h(IntegratedServer s) {
		HomeBlockEntity h = home(s);
		return h == null || h.run.devil == null || !(s.getOverworld().getEntity(h.run.devil) instanceof EternityDevilEntity e) ? null : e;
	}

	private static void report() {
		ShootingStar.LOGGER.info("[played] REPORT ({}) fight start (devil spawns) at 0; grown {}; first cut {}; floor turns {}; in the shaft {}; landed {}; stage 1 {}; stage 2 {}; begs {}; core cut {}",
				film ? "the film: bar from " + FILM_FROM + " at " + FILM_PACE + "x" : "real length", t(grownAt), t(firstCutAt), t(tiltAt), t(shaftAt), t(landAt), t(stage1At),
				t(stage2At), t(begAt), t(coreCutAt));
		ShootingStar.LOGGER.info("[played] REPORT in the arena from landing to the beg: {} ticks ({} s); something to cut within reach {}%; longest stretch with nothing to cut {} ticks ({} s) ending at {}",
				arenaTicks, arenaTicks / 20.0, arenaTicks == 0 ? 0 : 100 * arenaReach / arenaTicks, longestIdle, longestIdle / 20.0, t(longestIdleEnd));
		ShootingStar.LOGGER.info("[played] REPORT attacks seen {}; dodges planned {}; hits taken {}; misses punished {}; pain by source: {}; swings sent {}", SEEN, dodges,
				hitsTaken, punishes, sPainBy, YOU.swings);
	}
}
