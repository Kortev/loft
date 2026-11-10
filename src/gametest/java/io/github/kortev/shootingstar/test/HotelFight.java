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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * -Ptarget=played: the Eternity Devil's fight played, start to finish, in one first-person take with the HUD on and the normal lens. Nothing
 * is posed: the {@link Fighter} walks up the corridor, aims and slashes with the saws (the real attack key's path), dodges its tendrils,
 * and goes on through the shaft as it is: the floor turns, it cuts at the arm that comes up for it, drops down the shaft (into the mouth),
 * cuts its way out, cuts the mass and its heads until the devil begs, refuses, cuts the core, and walks to the loot. The only setting-up is
 * before the fight (the loop closed on you, the form on). It logs what the fight was like to play: each phase's time, how much of the time
 * there was something to cut within reach, the longest stretch with nothing, and where the pain came from.
 */
final class HotelFight {
	private HotelFight() {
	}

	private static final Fighter YOU = new Fighter();
	private static BlockPos origin;
	private static int step;
	private static int sub;
	private static boolean capturing;

	// What the server says, read every server tick.
	private static volatile boolean sInside;
	private static volatile boolean sStarted;
	private static volatile int sDevilAge = -1;
	private static volatile boolean sGrown;
	private static volatile long sFightAt = -1;
	private static volatile boolean sTilting;
	private static volatile boolean sBuilt;
	private static volatile boolean sSwallowed;
	private static volatile boolean sBegging;
	private static volatile boolean sCuttable;
	private static volatile boolean sDead;
	private static volatile boolean sMended;
	private static volatile float sPain;
	private static volatile String sPainBy = "";
	private static volatile long sNow;
	private static volatile double sLocalY;
	private static volatile String sCore = "";
	private static volatile java.util.UUID devilId;

	// The numbers.
	private static long grownAt = -1;
	private static long firstCutAt = -1;
	private static long tiltAt = -1;
	private static long shaftAt = -1;
	private static long swallowAt = -1;
	private static long burstAt = -1;
	private static long begAt = -1;
	private static long coreCutAt = -1;
	private static long deadAt = -1;
	private static int fightTicks;
	private static int reachTicks;
	private static int idle;
	private static int longestIdle;
	private static long longestIdleEnd = -1;
	private static int corridorTicks;
	private static int corridorReach;
	private static int shaftTicks;
	private static int shaftReach;

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
		sStarted = h.run.fight || d != null;
		sDevilAge = d == null ? -1 : d.age;
		sGrown = d != null && !d.growing();
		sFightAt = h.run.fightAt;
		sTilting = h.run.shaft.tiltAt >= 0;
		sBuilt = h.run.shaft.built;
		sSwallowed = Shaft.swallowed(h, you);
		sBegging = sBegging || d != null && d.begStarted;
		sCuttable = d != null && d.core() != null && d.core().cuttable();
		if (d != null) {
			sPain = d.pain();
			float[] p = d.painBy;
			sPainBy = String.format("corridor %.0f, mass %.0f, heads %.0f, arms %.0f, mouth %.0f", p[0], p[1], p[2], p[3], p[4]);
		}
		if (d != null) {
			devilId = d.getUuid();
		}
		// (Once it dies the home forgets it: it is followed by its own id.)
		if (devilId != null && sBegging && !(w.getEntity(devilId) instanceof EternityDevilEntity e2 && e2.isAlive())) {
			sDead = true;
		}
		sMended = sDead && h.shaft == null && h.arena == null;
		sLocalY = HotelBuilder.local(origin, 0, you.getPos()).y;
		ShaftCoreEntity core = d == null ? null : d.core();
		sCore = core == null ? "none" : "at " + HotelBuilder.local(origin, 0, core.getPos()) + " cuttable " + core.cuttable() + " canHit " + core.canHit()
				+ " form " + Form.fighting(you) + " downed " + io.github.kortev.chainsawman.form.Downed.is(you) + " you at " + HotelBuilder.local(origin, 0, you.getPos());
	}

	private static Vec3d world(double x, double y, double z) {
		return HotelBuilder.world(origin, 0, x, y, z);
	}

	private static Vec3d local(Vec3d w) {
		return HotelBuilder.local(origin, 0, w);
	}

	private static ServerPlayerEntity you(IntegratedServer s) {
		return s.getPlayerManager().getPlayerList().get(0);
	}

	private static HomeBlockEntity home(IntegratedServer s) {
		BlockPos at = HotelBuilder.world(origin, 0, HotelPlan.HOME_X, HotelPlan.HOME_Y, HotelPlan.HOME_Z);
		return s.getOverworld().getBlockEntity(at) instanceof HomeBlockEntity h ? h : null;
	}

	private static void put(IntegratedServer s, double x, double y, double z, float yaw, float pitch) {
		s.execute(() -> {
			Vec3d w = world(x, y, z);
			you(s).teleport(s.getOverworld(), w.x, w.y, w.z, yaw, pitch);
		});
	}

	private static void still(MinecraftClient c, String name) {
		ShootingStar.LOGGER.info("[played] still {}", name);
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

	// ---- What is in reach.

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

	/** The nearest thing of the devil's to cut, within the saws' reach (or null): heads and arms first, the core, then the devil itself. */
	private static Entity cuttable(MinecraftClient c) {
		double reach = io.github.kortev.chainsawman.form.Saw.SLASH_REACH;
		Entity best = null;
		double bestD = reach;
		for (Entity e : c.world.getEntities()) {
			if ((e instanceof ShaftHeadEntity || e instanceof ShaftArmEntity || e instanceof ShaftCoreEntity) && e.canHit()) {
				double d = reachTo(c, e.getBoundingBox());
				if (d <= bestD) {
					bestD = d;
					best = e;
				}
			}
		}
		if (best != null) {
			return best;
		}
		EternityDevilEntity d = devil(c);
		return d != null && d.fightable() && reachTo(c, d.getBoundingBox()) <= reach ? d : null;
	}

	/** Where to aim at {@code e}: a head's or an arm's tip, the core, or the devil's face (swept a little across it, as a player's hand does). */
	private static Vec3d aimPoint(MinecraftClient c, Entity e) {
		if (e instanceof ShaftArmEntity arm) {
			return new Vec3d(arm.getX(), Math.min(arm.tip() - 0.4, c.player.getEyeY() + 0.5), arm.getZ());
		}
		if (e instanceof ShaftHeadEntity) {
			return e.getPos().add(0.0, 0.9, 0.0);
		}
		if (e instanceof ShaftCoreEntity) {
			return e.getBoundingBox().getCenter();
		}
		float now = c.world.getTime();
		if (((EternityDevilEntity) e).shaft()) {
			// On the mass: cut down at it, a step ahead.
			Vec3d look = c.player.getRotationVec(1.0F);
			return c.player.getPos().add(look.x * 1.5, 0.1, look.z * 1.5);
		}
		Vec3d heart = world(EternityDevilEntity.HEART_X, EternityDevilEntity.HEART_Y + 0.6 * Math.sin(now * 0.21), EternityDevilEntity.HEART_Z + 2.2 * Math.sin(now * 0.07));
		return heart;
	}

	/** One tick of the numbers (from the devil grown to the beg). */
	private static void count(MinecraftClient c, boolean inShaft) {
		boolean reach = sSwallowed || cuttable(c) != null;
		fightTicks++;
		if (inShaft) {
			shaftTicks++;
			shaftReach += reach ? 1 : 0;
		} else {
			corridorTicks++;
			corridorReach += reach ? 1 : 0;
		}
		if (reach) {
			reachTicks++;
			idle = 0;
		} else if (++idle > longestIdle) {
			longestIdle = idle;
			longestIdleEnd = sNow;
		}
	}

	/** Cut whatever is in reach (aimed at), and say whether there was something. */
	private static boolean cutWhatIsNear(MinecraftClient c) {
		if (sSwallowed) {
			// Inside the mouth: cut at the walls ahead.
			YOU.swing(c, 0.9F);
			return true;
		}
		Entity e = cuttable(c);
		if (e == null) {
			return false;
		}
		YOU.aim(c, aimPoint(c, e));
		YOU.swing(c, 0.9F);
		if (firstCutAt < 0) {
			firstCutAt = sNow;
		}
		return true;
	}

	/** A bite or a tendril on its way to where you stand: strafe out of it. */
	private static void dodgeAttacks(MinecraftClient c, EternityDevilEntity d) {
		if (d == null || YOU.dodging()) {
			return;
		}
		Vec3d me = local(c.player.getPos());
		for (ClientHotel.Event e : ClientHotel.events(d.getId())) {
			long age = c.world.getTime() - e.start();
			Vec3d at = local(new Vec3d(e.x(), e.y(), e.z()));
			if (e.kind() == HotelPayloads.Devil.TENDRIL && age >= 6 && age < e.ticks() - 4 && Math.abs(at.z - me.z) < 1.6) {
				// (Out of its lane, toward the wider side.)
				YOU.dodge(me.z < 6.5 ? -1 : 1, 9);
				ShootingStar.LOGGER.info("[played] dodge a tendril at {}", sNow);
				return;
			}
			if (e.kind() == HotelPayloads.Devil.BITE && age >= 8 && age < e.ticks() - 3 && Math.hypot(at.x - me.x, at.z - me.z) < 1.9) {
				YOU.dodge(at.z > me.z ? -1 : 1, 8);
				ShootingStar.LOGGER.info("[played] dodge a bite at {}", sNow);
				return;
			}
		}
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
		c.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
		Capture.camera = null;
		if (f > 2400) {
			ShootingStar.LOGGER.error("[played] stuck in step {}", step);
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
					put(s, 18.5, corridorY, 6.5, east, 0.0F);
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
						Vec3d p = world(18.5, corridorY, 6.5);
						you.teleport(w, p.x, p.y, p.z, east, 0.0F);
						Hotel.startFight(w, h, w.getTime());
					});
					next();
				}
			}
			case 1 -> {
				// The devil tears out of the wall at the end of the corridor (the take starts as it grows), and you watch it from the middle.
				YOU.aim(c, world(34.0, 31.6, 6.6));
				if (sDevilAge >= 28 && !capturing && System.getProperty("shootingstar.selftest.capture", "").equals("true")) {
					capturing = true;
					Capture.start(c, c.runDirectory.toPath().resolve("capture"));
				}
				if (sGrown && grownAt < 0) {
					grownAt = sNow;
					FleshMesh.costReport();
				}
				if (grownAt >= 0 && sNow - grownAt == 6) {
					still(c, "corridor_end");
				}
				if (grownAt >= 0 && sNow - grownAt >= 10) {
					ShootingStar.LOGGER.info("[played] frame cost, the devil filling the corridor's end from its middle: {}", FleshMesh.costReport());
					next();
				}
			}
			case 2 -> {
				// Run up to it and cut, cut, cut: dodging what it sends.
				count(c, false);
				dodgeAttacks(c, d);
				Vec3d me = local(c.player.getPos());
				if (!cutWhatIsNear(c)) {
					YOU.moveTo(c, world(31.2, corridorY, me.z), 0.3, true);
				} else {
					YOU.walk(false, false);
					if (me.x < 30.6 && !YOU.dodging()) {
						// (A step closer each time it pushes you back.)
						YOU.walk(true, false);
					}
				}
				if (sub == 60) {
					still(c, "cutting");
				}
				if (sub == 40) {
					// (From here: the first frames loaded the growths' meshes.)
					FleshMesh.costReport();
				}
				if (sub == 100) {
					ShootingStar.LOGGER.info("[played] frame cost, in the fight at its face: {}", FleshMesh.costReport());
				}
				if (sTilting) {
					tiltAt = sNow;
					ShootingStar.LOGGER.info("[played] the floor turns at pain {}", sPain);
					next();
				}
			}
			case 3 -> {
				// The floor turns: you slide down the corridor toward it (nothing to do; the hotel decides).
				count(c, false);
				YOU.stop();
				cutWhatIsNear(c);
				if (sub == 22) {
					still(c, "tilt");
				}
				if (sBuilt) {
					shaftAt = sNow;
					YOU.free();
					next();
				}
			}
			case 4 -> {
				// On a ledge: cut at the arm that comes up for you; then (taken down, or not) drop down the shaft.
				count(c, true);
				YOU.stop();
				boolean cut = cutWhatIsNear(c);
				if (!cut) {
					YOU.aim(c, world(19.5, Shaft.BASE + 1.0, 6.5));
				}
				if (sub == 8) {
					still(c, "ledge");
				}
				boolean armOut = false;
				for (Entity e : c.world.getEntities()) {
					armOut |= e instanceof ShaftArmEntity;
				}
				if (sub > 30 && !armOut && sLocalY > Shaft.BASE + 3.0) {
					// Down: off the lip into the shaft.
					YOU.aim(c, world(19.5, sLocalY - 2.0, 6.5));
					YOU.walk(true, false);
				}
				if (sLocalY < Shaft.BASE + 3.0 || sSwallowed) {
					next();
				}
			}
			case 5 -> {
				// Down the shaft: into the mouth, and cut out of it; then on the mass, cut it and its heads until it can bear no more.
				count(c, true);
				if (sSwallowed && swallowAt < 0) {
					swallowAt = sNow;
					sub = 0;
				}
				if (swallowAt >= 0 && burstAt < 0 && !sSwallowed) {
					burstAt = sNow;
				}
				if (sSwallowed && sub == 6) {
					still(c, "swallowed");
				}
				if (sSwallowed && sub == 26) {
					still(c, "swallowed_cut");
				}
				YOU.stop();
				Vec3d me5 = local(c.player.getPos());
				boolean overPit = me5.x < 21.3 && me5.x > 17.5 && me5.z > 4.5 && me5.z < 8.5;
				if (!sSwallowed && burstAt >= 0 && overPit) {
					// Out of the mouth: off the pit first (onto the mass east of it), cutting on the way.
					YOU.moveTo(c, world(22.4, Shaft.BASE, me5.z), 0.3, true);
					YOU.swing(c, 0.9F);
				} else if (!cutWhatIsNear(c)) {
					// Nothing near: go to the mass beside the pit.
					YOU.moveTo(c, world(21.6, Shaft.BASE, 6.5), 0.5, false);
				}
				if (burstAt >= 0 && sNow - burstAt == 30) {
					still(c, "on_the_mass");
					ShootingStar.LOGGER.info("[played] frame cost, on the mass: {}", FleshMesh.costReport());
				}
				if (sBegging) {
					begAt = sNow;
					ShootingStar.LOGGER.info("[played] it begs; pain by source: {}", sPainBy);
					next();
				}
			}
			case 6 -> {
				// It begs: the deal on the screen; refused.
				YOU.stop();
				if (d != null && d.core() == null) {
					YOU.aim(c, world(19.5, Shaft.BASE + 1.0, 6.5));
				}
				if (c.currentScreen instanceof ContractScreen && sub == 30) {
					still(c, "offer");
				}
				if (sub == 50) {
					s.execute(() -> Hotel.answer(you(s), false));
				}
				if (sCuttable && sub > 52) {
					next();
				}
			}
			case 7 -> {
				// The core held out over the pit: cut it.
				Entity core = null;
				for (Entity e : c.world.getEntities()) {
					if (e instanceof ShaftCoreEntity) {
						core = e;
					}
				}
				if (core != null) {
					YOU.aim(c, core.getBoundingBox().getCenter());
					if (reachTo(c, core.getBoundingBox()) > 3.0) {
						YOU.moveTo(c, core.getPos(), 2.6, false);
						YOU.aim(c, core.getBoundingBox().getCenter());
					} else {
						YOU.stop();
						YOU.swing(c, 0.9F);
					}
				}
				if (sub == 4) {
					still(c, "core");
				}
				if (sub % 20 == 0) {
					ShootingStar.LOGGER.info("[played] core: {} (client: core {} reach {} screen {})", sCore, core == null ? "none" : local(core.getPos()), core == null ? -1 : reachTo(c, core.getBoundingBox()), c.currentScreen);
				}
				if (sDead) {
					coreCutAt = sNow;
					YOU.stop();
					next();
				}
			}
			case 8 -> {
				// The collapse, the hotel put back, and the loot.
				if (deadAt < 0) {
					deadAt = sNow;
				}
				if (sub == 20) {
					still(c, "collapse");
				}
				if (sMended) {
					ItemEntity loot = null;
					for (Entity e : c.world.getEntities()) {
						if (e instanceof ItemEntity it && (loot == null || it.squaredDistanceTo(c.player) < loot.squaredDistanceTo(c.player))) {
							loot = it;
						}
					}
					if (loot != null) {
						YOU.aim(c, loot.getPos());
						if (c.player.squaredDistanceTo(loot) > 2.0) {
							YOU.moveTo(c, loot.getPos(), 0.8, false);
							YOU.aim(c, loot.getPos());
						} else {
							YOU.stop();
						}
					}
				} else {
					YOU.stop();
				}
				if (sub == 110) {
					still(c, "loot");
				}
				if (sub > 150) {
					report();
					YOU.release(c);
					return true;
				}
			}
			default -> {
			}
		}
		YOU.apply(c);
		return false;
	}

	private static void report() {
		ShootingStar.LOGGER.info("[played] REPORT fight start (devil spawns) at 0; grown {}; first cut {}; floor turns {}; in the shaft {}; swallowed {}; out {}; begs {}; core cut {}",
				t(grownAt), t(firstCutAt), t(tiltAt), t(shaftAt), t(swallowAt), t(burstAt), t(begAt), t(coreCutAt));
		ShootingStar.LOGGER.info("[played] REPORT from grown to the beg: {} ticks ({} s); something to cut within reach {} ticks ({}%); corridor {}/{} ticks, shaft {}/{} ticks; longest stretch with nothing to cut {} ticks ({} s) ending at {}",
				fightTicks, fightTicks / 20.0, reachTicks, fightTicks == 0 ? 0 : 100 * reachTicks / fightTicks, corridorReach, corridorTicks, shaftReach, shaftTicks, longestIdle,
				longestIdle / 20.0, t(longestIdleEnd));
		ShootingStar.LOGGER.info("[played] REPORT pain by source: {}; swings sent {}", sPainBy, YOU.swings);
	}
}
