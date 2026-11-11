package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import io.github.kortev.chainsawman.devil.TomatoSeedEntity;
import io.github.kortev.chainsawman.form.Blood;
import io.github.kortev.chainsawman.form.Form;
import io.github.kortev.chainsawman.form.Heart;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * -Ptarget=played: the Tomato Devil fought from your eyes, first person, the HUD on, the normal lens, at dusk. Nothing is posed in the fight:
 * the {@link Fighter} plays it through the real keys (an iron sword, the game's own attack), watching each move's tell. It walks your way
 * eyes wandering, sees you, every eye snaps to you; you step out of its slap and cut it as it settles; hug it and are hopped off; the roll
 * that follows hits you; you bait the next roll into an oak and cut it on its back; sidestep the spit; kill it. Two seeds burned with a
 * torch, two eaten by Pochita (set down beforehand), one missed; it comes back out of that one (60% of its health, fought for real), is
 * killed, and its seeds are burned and eaten. Staged: its walk toward you before it notices you (told where to walk), the missed seed's
 * minute skipped (its ten-second swell is not), Pochita told to stand and to sit, and your health kept from running out.
 *
 * <p>-Ptarget=played-stone / played-iron / played-saws (no film): one devil from first sight to its death with that weapon (the saws: in
 * the Chainsaw Man form), logged ("[played] KILL").
 */
final class TomatoFight {
	private TomatoFight() {
	}

	static final Fighter YOU = new Fighter();
	private static int step;
	private static int sub;
	private static boolean film;
	private static long startAt = -1;
	private static long killAt = -1;
	private static int rolls;
	private static int hits;
	private static float lastHealth = -1.0F;
	private static int burned;
	private static int lastState = -1;
	private static int lastDevil = -1;
	private static boolean bait;
	private static double dodgeSide = 1.0;
	private static long dodgeUntil = -1;
	private static Vec3d dodgeTo;
	private static int kills;
	private static TomatoSeedEntity left;

	static void snap(MinecraftServer server) {
		if (!TomatoSelfTest.active() || !(server instanceof IntegratedServer is) || is.getPlayerManager().getPlayerList().isEmpty() || !target().startsWith("played") && !target().equals("firstnight")) {
			return;
		}
		ServerPlayerEntity p = TomatoSelfTest.you(is);
		serverTick(server);
		// (Staged: your health kept from running out; the hits still show and count.)
		if (p.isAlive() && p.getHealth() < 9.0F && p.age % 5 == 0) {
			p.setHealth(p.getMaxHealth());
		}
		p.getHungerManager().setFoodLevel(20);
		// The server's side of it, for the log: each state the devil goes into, and every hurt on either side.
		for (TomatoDevilEntity d : is.getOverworld().getEntitiesByType(Devils.TOMATO_DEVIL, e -> true)) {
			if (d.state() != sState || d.getId() != sDevil) {
				sState = d.state();
				sDevil = d.getId();
				ShootingStar.LOGGER.info("[played] server: devil {} state {} at {} ({} from you), its health {}, yours {}", d.getId(), d.state(),
						String.format("%.1f %.1f %.1f", d.getX(), d.getY(), d.getZ()), String.format("%.1f", d.distanceTo(p)), d.getHealth(), p.getHealth());
			}
			if (d.getHealth() != sDevilHealth) {
				sDevilHealth = d.getHealth();
				ShootingStar.LOGGER.info("[played] server: devil health {}", d.getHealth());
			}
		}
		if (p.getHealth() < sYou - 0.4F) {
			ShootingStar.LOGGER.info("[played] server: you hurt to {} by {}", p.getHealth(), p.getRecentDamageSource());
		}
		sYou = p.getHealth();
	}

	private static int sState = -1;
	private static int sDevil = -1;
	private static float sDevilHealth = -1;
	private static float sYou = 20;

	private static String target() {
		return System.getProperty("shootingstar.selftest.target", "");
	}

	private static double now(MinecraftClient c) {
		return Capture.active() ? Capture.time() : c.world.getTime();
	}

	private static void mark(MinecraftClient c, String what) {
		ShootingStar.LOGGER.info("[played] mark {} at capture {} (world {})", what, String.format("%.1f", Capture.active() ? Capture.time() : -1.0), c.world.getTime());
	}

	private static void next(MinecraftClient c, String why) {
		step++;
		sub = 0;
		mark(c, "step " + step + " " + why);
	}

	private static TomatoDevilEntity devil(MinecraftClient c) {
		TomatoDevilEntity best = null;
		for (Entity e : c.world.getEntities()) {
			if (e instanceof TomatoDevilEntity d && d.isAlive() && (best == null || d.squaredDistanceTo(c.player) < best.squaredDistanceTo(c.player))) {
				best = d;
			}
		}
		return best;
	}

	private static List<TomatoSeedEntity> seeds(MinecraftClient c) {
		List<TomatoSeedEntity> out = new ArrayList<>();
		for (Entity e : c.world.getEntities()) {
			if (e instanceof TomatoSeedEntity s && s.isAlive() && s.withering() < 0) {
				out.add(s);
			}
		}
		return out;
	}

	private static Vec3d forward(float yaw) {
		float r = yaw * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
	}

	private static Vec3d chest(TomatoDevilEntity d) {
		return d.getPos().add(0.0, d.state() == TomatoDevilEntity.ON_BACK || d.state() == TomatoDevilEntity.ROLL ? 1.0 : 2.2, 0.0);
	}

	private static void hold(MinecraftClient c, int slot) {
		c.player.getInventory().selectedSlot = slot;
	}

	/** Whether the next hop is stood in and taken (the film's one), or backed out of in its squat. */
	private static boolean takeHop;
	private static boolean hopped;

	/** Out of the way of what is coming and hitting it when it can be hit; true while something is being dodged. */
	static boolean fight(MinecraftClient c, TomatoDevilEntity d, boolean takeRoll, boolean baiting) {
		Vec3d me = c.player.getPos();
		Vec3d f = forward(d.aim());
		Vec3d side = new Vec3d(-f.z, 0.0, f.x);
		Vec3d rel = me.subtract(d.getPos());
		double along = rel.dotProduct(f);
		double across = rel.dotProduct(side);
		double dist = Math.hypot(rel.x, rel.z);
		int st = d.state();
		float t = d.stateTime(0.0F);
		if (st != lastState || d.getId() != lastDevil) {
			lastState = st;
			lastDevil = d.getId();
			dodgeTo = null;
			if (st == TomatoDevilEntity.SLAP) {
				slaps++;
				mark(c, "slap tell");
			} else if (st == TomatoDevilEntity.ROLL_TELL) {
				rolls++;
				mark(c, "roll tell " + rolls + (takeRoll ? " (taken)" : baiting ? " (baited)" : ""));
			} else if (st == TomatoDevilEntity.SPIT) {
				spits++;
				mark(c, "spit tell");
			} else if (st == TomatoDevilEntity.ON_BACK) {
				mark(c, "on its back");
			} else if (st == TomatoDevilEntity.HOP) {
				hops++;
				mark(c, "hop squat " + hops + (takeHop ? " (taken)" : " (backed out of)"));
			}
		}
		YOU.aim(c, chest(d));
		boolean reach = c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == d;
		switch (st) {
			case TomatoDevilEntity.SLAP -> {
				if (t < TomatoDevilEntity.SLAP_HIT + 1) {
					boolean in = along > -0.5 && along < TomatoDevilEntity.SLAP_REACH + 1.2 && Math.abs(across) < TomatoDevilEntity.SLAP_HALF_WIDTH + 1.0;
					if (in) {
						YOU.strafeTo(c, d.getPos().add(f.multiply(TomatoDevilEntity.SLAP_REACH + 2.4)).add(side.multiply(across)), 0.2, false);
					} else {
						YOU.stop();
					}
					return in;
				}
				// It settles on its hands: in and cut it.
				if (dist > 3.2) {
					YOU.strafeTo(c, d.getPos(), 3.0, false);
				} else {
					YOU.stop();
				}
				if (reach && allowHit) {
					YOU.swing(c, 0.9F);
				}
				return false;
			}
			case TomatoDevilEntity.HOP -> {
				if (takeHop) {
					// Stood in: keep cutting, and take it.
					YOU.stop();
					if (reach && allowHit) {
						YOU.swing(c, 0.9F);
					}
					if (t >= TomatoDevilEntity.HOP_LAND) {
						hopped = true;
					}
					return false;
				}
				if (t < TomatoDevilEntity.HOP_LAND + 1) {
					// Out of reach while it squats: well clear of its landing, running.
					double clear = TomatoDevilEntity.WIDTH * 0.5 + TomatoDevilEntity.HOP_REACH + 1.4;
					Vec3d away = dist < 0.1 ? f.multiply(-1.0) : rel.multiply(1.0 / dist);
					if (dist < clear) {
						YOU.strafeTo(c, d.getPos().add(away.multiply(clear + 0.6)), 0.3, true);
					} else {
						YOU.stop();
					}
					return true;
				}
				return false;
			}
			case TomatoDevilEntity.ROLL_TELL, TomatoDevilEntity.ROLL -> {
				boolean inLine = along > -1.0 && Math.abs(across) < 2.2;
				if (takeRoll) {
					YOU.stop();
					return false;
				}
				if (st == TomatoDevilEntity.ROLL_TELL && t < TomatoDevilEntity.ROLL_AIM + 1) {
					YOU.stop();
					return true;
				}
				if (inLine || dodgeTo != null) {
					if (dodgeTo == null) {
						double s = Math.abs(across) > 0.3 ? Math.signum(across) : dodgeSide;
						dodgeTo = me.add(side.multiply(s * (3.6 - Math.abs(across))));
					}
					YOU.strafeTo(c, dodgeTo, 0.3, true);
					return true;
				}
				YOU.stop();
				return true;
			}
			case TomatoDevilEntity.SPIT -> {
				if (t <= TomatoDevilEntity.SPIT_AIM + 2) {
					// Wait for it to set its aim...
					YOU.stop();
					return true;
				}
				if (t <= TomatoDevilEntity.SPIT_TELL + 8) {
					// ...then from 5 blocks or more one step sideways at a walk, between two seeds; closer in, the gaps are too narrow: out
					// of the fan altogether, running.
					boolean near = along < 4.6;
					if (dodgeTo == null) {
						double s = Math.abs(across) > 0.4 ? (near ? Math.signum(across) : -Math.signum(across)) : dodgeSide;
						dodgeTo = me.add(side.multiply(s * (near ? 3.6 : 1.15)));
						dodgeSide = -dodgeSide;
					}
					YOU.strafeTo(c, dodgeTo, 0.12, near);
					return true;
				}
				if (dist > 3.2) {
					YOU.strafeTo(c, d.getPos(), 3.0, false);
				}
				if (reach && allowHit) {
					YOU.swing(c, 0.9F);
				}
				return false;
			}
			case TomatoDevilEntity.ON_BACK, TomatoDevilEntity.GET_UP, TomatoDevilEntity.UNFURL, TomatoDevilEntity.EMERGE -> {
				if (dist > 3.7) {
					YOU.strafeTo(c, d.getPos(), 3.4, true);
				} else {
					YOU.stop();
				}
				if (reach && allowHit) {
					YOU.swing(c, 0.9F);
				}
				return false;
			}
			default -> {
				return false;
			}
		}
	}

	/** Close in and cut it between its moves (backing off a step if right up against it). */
	static void press(MinecraftClient c, TomatoDevilEntity d) {
		double dist = d.distanceTo(c.player);
		if (dist > 3.4) {
			YOU.strafeTo(c, d.getPos(), 3.2, false);
		} else if (dist < 2.6) {
			YOU.strafeTo(c, c.player.getPos().add(c.player.getPos().subtract(d.getPos()).normalize().multiply(1.0)), 0.2, false);
		} else {
			YOU.stop();
		}
		if (c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == d && allowHit) {
			YOU.swing(c, 0.95F);
		}
	}

	private static int spits;
	private static int hops;
	private static int pochitaId = -1;
	/** The server's side: sit Pochita down again once every seed but the one left on purpose is gone. */
	private static volatile boolean sitWhenDone;
	private static volatile int leftId = -1;
	private static volatile int keepSeeds;

	private static TomatoDevilEntity serverDevil(IntegratedServer s) {
		for (TomatoDevilEntity e : s.getOverworld().getEntitiesByType(Devils.TOMATO_DEVIL, e -> e.isAlive())) {
			return e;
		}
		return null;
	}

	/** One client tick of the take; true when it is over. */
	static boolean tick(MinecraftClient c, IntegratedServer s, int f) {
		if (c.player == null || c.world == null) {
			return false;
		}
		film = TomatoSelfTest.capturing();
		sub++;
		c.options.hudHidden = false;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(70);
		c.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
		Capture.camera = null;
		// (No chat in the film: taming messages and the like.)
		c.inGameHud.getChatHud().clear(false);
		if (f > 9000) {
			ShootingStar.LOGGER.error("[played] stuck in step {}", step);
			report(c);
			YOU.release(c);
			return true;
		}
		float hp = c.player.getHealth();
		if (lastHealth >= 0.0F && hp < lastHealth - 0.5F) {
			hits++;
			TomatoDevilEntity rd = devil(c);
			if (rd != null && (rd.state() == TomatoDevilEntity.ROLL || rd.state() == TomatoDevilEntity.ON_BACK || rd.state() == TomatoDevilEntity.UNFURL)) {
				rollHit = true;
			}
			mark(c, "you are hit (" + lastHealth + " to " + hp + ")");
		}
		lastHealth = hp;
		String t = target();
		boolean duel = !t.equals("played");
		TomatoDevilEntity d = devil(c);
		boolean fightStep = step >= 2 && step <= 6 || step == 9;
		if (fightStep && d == null && startAt >= 0 && sub > 5) {
			YOU.stop();
			if (killed(c, null)) {
				step = duel ? 99 : step == 9 ? 9 : 6;
				next(c, "it bursts");
			}
			YOU.apply(c);
			return step == 99 + 1;
		}
		// (In the first part of the film, keep it alive for its moves: cut it only while it has more than half its health.)
		allowHit = duel || step >= 6 || d == null || d.getHealth() > TomatoDevilEntity.MAX_HEALTH * 0.5F;
		switch (step) {
			case 0 -> {
				if (sub == 1) {
					s.execute(() -> {
						ServerWorld w = s.getOverworld();
						for (TomatoDevilEntity e : w.getEntitiesByType(Devils.TOMATO_DEVIL, e -> true)) {
							e.discard();
						}
						ServerPlayerEntity you = TomatoSelfTest.you(s);
						Vec3d at = TomatoSelfTest.site(0.0, 0.0, -14.0);
						you.teleport(w, at.x, at.y, at.z, 180.0F, 0.0F);
						if (t.equals("played-stone")) {
							you.getInventory().setStack(0, new ItemStack(Items.STONE_SWORD));
						}
						if (t.equals("played-saws")) {
							Heart.give(you);
							Blood.set(you, Blood.MAX);
							Form.start(you);
						}
						if (duel) {
							TomatoSelfTest.devil(w, TomatoSelfTest.site(0.0, 0.0, 2.0), 0.0F);
						} else {
							// The film: it comes from far off, walking your way, not having seen you yet; Pochita sits off to one side. (You are
							// no hybrid here, and the world has hearts to spare: else he would turn wild, every heart being taken.)
							you.removeAttached(Heart.MERGED);
							s.getGameRules().get(io.github.kortev.chainsawman.form.TeamRules.CHAINSAW_HEARTS).set(8, s);
							TomatoDevilEntity e = TomatoSelfTest.devil(w, TomatoSelfTest.site(1.0, 0.0, 10.0), 180.0F);
							e.walkTo = TomatoSelfTest.site(0.0, 0.0, -14.0);
							io.github.kortev.chainsawman.PochitaEntity pochita = io.github.kortev.chainsawman.ChainsawMan.POCHITA_ENTITY.create(w);
							Vec3d pa = TomatoSelfTest.site(-7.0, 0.0, -15.0);
							pochita.refreshPositionAndAngles(pa.x, pa.y, pa.z, 90.0F, 0.0F);
							pochita.setTamed(true, true);
							pochita.setOwner(you);
							pochita.setSitting(true);
							pochita.setInSittingPose(true);
							pochita.setPersistent();
							w.spawnEntity(pochita);
							pochitaId = pochita.getId();
						}
					});
				}
				YOU.aim(c, TomatoSelfTest.site(0.0, 1.8, 2.0));
				if (sub > (t.equals("played-saws") ? 60 : 20) && d != null) {
					if (film) {
						Capture.start(c, c.runDirectory.toPath().resolve("capture"));
					}
					startAt = c.world.getTime();
					hold(c, 0);
					next(c, duel ? "meet it" : "it walks your way");
					if (duel) {
						step = 2;
					}
				}
			}
			case 1 -> {
				// The film's first seconds: it walks at you, eyes wandering; then it sees you, and every eye snaps to you.
				if (d == null) {
					break;
				}
				YOU.aim(c, d.getPos().add(0.0, 2.3, 0.0));
				YOU.stop();
				if (sub == 70) {
					mark(c, "it notices you");
					s.execute(() -> {
						TomatoDevilEntity e = serverDevil(s);
						if (e != null) {
							e.walkTo = null;
						}
					});
				}
				if (sub > 70 + TomatoDevilEntity.NOTICE_STILL + 6) {
					next(c, "meet it");
				}
			}
			case 2 -> {
				// Meet it: walk up; the first slap is stepped out of and punished.
				if (d == null) {
					break;
				}
				takeHop = false;
				boolean busy = fight(c, d, false, false);
				if (!busy && d.state() == TomatoDevilEntity.WALK) {
					press(c, d);
				}
				if (!duel && lastState == TomatoDevilEntity.WALK && sub > 40 && slaps >= 1) {
					next(c, "hug it");
				}
				if (duel && killed(c, d)) {
					return true;
				}
			}
			case 3 -> {
				// Hug it, cutting away: it squats, and the hop comes down on you.
				if (d == null) {
					break;
				}
				takeHop = true;
				boolean busy = fight(c, d, true, false);
				if (!busy && d.state() == TomatoDevilEntity.WALK) {
					double dist = d.distanceTo(c.player);
					// (Right up to its arms, but not so close the lens goes inside it when it squats.)
					if (dist > 3.4) {
						YOU.strafeTo(c, d.getPos(), 3.1, false);
					} else {
						YOU.stop();
					}
					if (c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == d && allowHit) {
						YOU.swing(c, 0.95F);
					}
				}
				if (hopped && d.state() != TomatoDevilEntity.HOP) {
					takeHop = false;
					next(c, "the roll follows");
				}
			}
			case 4 -> {
				// Thrown off it, you are in the roll's line: it comes, and you take it.
				if (d == null) {
					break;
				}
				boolean busy = fight(c, d, !rollHit, false);
				if (!busy && d.state() == TomatoDevilEntity.WALK) {
					Vec3d away = c.player.getPos().subtract(d.getPos()).multiply(1.0, 0.0, 1.0).normalize();
					if (d.distanceTo(c.player) < 7.0) {
						YOU.strafeTo(c, d.getPos().add(away.multiply(8.0)), 0.4, false);
					} else {
						YOU.stop();
					}
				}
				if (rollHit && d.state() != TomatoDevilEntity.ROLL) {
					next(c, "bait a roll into a tree");
				}
				if (sub > 600) {
					next(c, "no roll came");
				}
			}
			case 5 -> {
				// Between it and an oak, the oak close behind; out of the line when the roll is aimed.
				if (d == null) {
					break;
				}
				if (sub == 1 || d.state() == TomatoDevilEntity.WALK && d.distanceTo(c.player) < 5.0 && sub > 30) {
					Vec3d o0 = TomatoSelfTest.site(TomatoSelfTest.OAKS[0][0], 0.0, TomatoSelfTest.OAKS[0][1]);
					Vec3d o1 = TomatoSelfTest.site(TomatoSelfTest.OAKS[1][0], 0.0, TomatoSelfTest.OAKS[1][1]);
					otherOak = o1.squaredDistanceTo(d.getPos()) > o0.squaredDistanceTo(d.getPos());
					sub = 2;
				}
				Vec3d tree = oak(c);
				Vec3d fromTree = d.getPos().subtract(tree).multiply(1.0, 0.0, 1.0).normalize();
				Vec3d spot = tree.add(fromTree.multiply(2.6));
				boolean busy = d.state() != TomatoDevilEntity.WALK && fight(c, d, false, true);
				if (!busy && (d.state() == TomatoDevilEntity.WALK || d.state() == TomatoDevilEntity.SLAP)) {
					double toSpot = Math.hypot(spot.x - c.player.getX(), spot.z - c.player.getZ());
					YOU.aim(c, chest(d));
					if (toSpot > 0.5) {
						YOU.strafeTo(c, spot, 0.4, false);
					} else {
						YOU.stop();
					}
				}
				if (d.state() == TomatoDevilEntity.ON_BACK) {
					next(c, "punish it on its back");
				}
				if (rolls > 7) {
					next(c, "no roll into the tree");
				}
			}
			case 6 -> {
				// Fight it out: hops backed out of, spits sidestepped.
				if (d == null || !d.isAlive()) {
					if (killed(c, d)) {
						next(c, "it bursts");
					}
					break;
				}
				takeHop = false;
				boolean busy = fight(c, d, false, false);
				if (!busy && (d.state() == TomatoDevilEntity.WALK || d.state() == TomatoDevilEntity.SLAP && d.stateTime(0) > TomatoDevilEntity.SLAP_HIT)) {
					press(c, d);
				}
			}
			case 7 -> {
				// The seeds: two burned with a torch, two eaten by Pochita, one missed on purpose.
				return seeds(c, s, 2, true);
			}
			case 8 -> {
				// It comes back out of the one that was left (its minute brought on; the swell's ten seconds as they are).
				hold(c, 0);
				YOU.stop();
				TomatoSeedEntity lf = left != null && left.isAlive() ? left : null;
				if (lf != null) {
					Vec3d lp = lf.getPos();
					YOU.aim(c, lp.add(0.0, 0.8, 0.0));
					Vec3d away = c.player.getPos().subtract(lp).multiply(1.0, 0.0, 1.0).normalize();
					YOU.strafeTo(c, lp.add(away.multiply(7.0)), 0.5, false);
				}
				if (sub == 20) {
					mark(c, "the missed seed's minute is skipped");
					s.execute(() -> {
						for (TomatoSeedEntity e : s.getOverworld().getEntitiesByType(Devils.TOMATO_SEED, e -> true)) {
							ShootingStar.LOGGER.info("[played] server: seed {} (left {}) alive {} landed {} burning {} chewed {} withering {} swelling {} lain {}", e.getId(),
									leftId, e.isAlive(), e.landed(), e.burning(), e.chewed(), e.withering(), e.swelling(), e.ground);
							if (e.isAlive() && !e.ended()) {
								e.lain(TomatoSeedEntity.COMEBACK - 1);
							}
						}
					});
				}
				if (sub == 24) {
					mark(c, "the seed swells");
				}
				if (d != null) {
					mark(c, "it climbs out");
					next(c, "kill it again");
					step = 9;
					sub = 0;
				}
			}
			case 9 -> {
				if (d == null || !d.isAlive()) {
					if (killed(c, d)) {
						next(c, "it bursts again");
					}
					break;
				}
				takeHop = false;
				boolean busy = fight(c, d, false, false);
				if (!busy && (d.state() == TomatoDevilEntity.WALK || d.state() == TomatoDevilEntity.SLAP && d.stateTime(0) > TomatoDevilEntity.SLAP_HIT)) {
					press(c, d);
				}
			}
			case 10 -> {
				return seeds(c, s, 2, false);
			}
			case 11 -> {
				YOU.stop();
				hold(c, 0);
				YOU.aim(c, c.player.getEyePos().add(forward(c.player.getYaw()).multiply(6.0)).add(0.0, -1.5, 0.0));
				if (sub > 40) {
					report(c);
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

	private static int slaps;
	private static boolean rollHit;

	private static boolean killed(MinecraftClient c, TomatoDevilEntity d) {
		if (d != null && d.isAlive()) {
			return false;
		}
		if (killAt < 0 || kills == 0) {
			killAt = c.world.getTime();
		}
		kills++;
		mark(c, "killed after " + String.format("%.1f s", (c.world.getTime() - startAt) / 20.0));
		ShootingStar.LOGGER.info("[played] KILL ({}) {} s from first sight, hits taken {}, rolls {}, hops {}, spits {}, slaps {}", target(),
				String.format("%.1f", (c.world.getTime() - startAt) / 20.0), hits, rolls, hops, spits, slaps);
		if (!target().equals("played")) {
			report(c);
			YOU.release(c);
			c.player.getInventory().selectedSlot = 0;
			step = 99;
		}
		return true;
	}

	private static Vec3d dyingAt;
	private static int torched;
	private static boolean pochitaUp;

	/**
	 * The seeds after a kill: it bursts and they fly (watched from a step back); {@code torch} of them burned with a torch, the nearest first;
	 * then Pochita stood up to eat the rest, but for the one missed on purpose ({@code leaveOne}: the one furthest from him), and sat down
	 * again. True when the take ends.
	 */
	private static boolean seeds(MinecraftClient c, IntegratedServer s, int torch, boolean leaveOne) {
		if (step == 99) {
			return true;
		}
		List<TomatoSeedEntity> ss = seeds(c);
		ss.removeIf(TomatoSeedEntity::ended);
		if (sub == 1) {
			torched = 0;
			pochitaUp = false;
			left = null;
			leftId = -1;
		}
		if (sub < TomatoDevilEntity.BURST_AT + 40) {
			TomatoDevilEntity dying = null;
			for (Entity e : c.world.getEntities()) {
				if (e instanceof TomatoDevilEntity td && td.isDead()) {
					dying = td;
				}
			}
			if (dying != null) {
				dyingAt = dying.getPos();
			}
			if (dyingAt != null && sub < TomatoDevilEntity.BURST_AT + 6) {
				YOU.aim(c, dyingAt.add(0.0, 1.4, 0.0));
				Vec3d away = c.player.getPos().subtract(dyingAt).multiply(1.0, 0.0, 1.0).normalize();
				YOU.strafeTo(c, dyingAt.add(away.multiply(6.0)), 0.4, false);
			} else {
				YOU.stop();
				YOU.aim(c, dyingAt != null ? dyingAt.add(0.0, 0.3, 0.0) : c.player.getEyePos().add(forward(c.player.getYaw()).multiply(5.0)).add(0, -1.5, 0));
			}
			YOU.apply(c);
			if (sub == 22) {
				mark(c, "the seeds are in the ground");
			}
			return false;
		}
		Entity pochita = pochitaId >= 0 ? c.world.getEntityById(pochitaId) : null;
		// (The one missed on purpose is whichever is left when Pochita has had his two: he goes for the nearest, his own way.)
		keepSeeds = leaveOne ? 1 : 0;
		if (torched < torch && !ss.isEmpty()) {
			hold(c, 1);
			ss.sort(Comparator.comparingDouble(x -> x.squaredDistanceTo(c.player)));
			TomatoSeedEntity target = ss.get(0);
			Vec3d at = target.getPos().add(0.0, 0.15, 0.0);
			YOU.aim(c, at);
			double dist = Math.hypot(at.x - c.player.getX(), at.z - c.player.getZ());
			if (dist > 2.2) {
				YOU.strafeTo(c, at, 2.0, dist > 4.0);
			} else {
				YOU.stop();
				if (c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == target && sub % 6 == 0) {
					c.interactionManager.interactEntity(c.player, target, Hand.MAIN_HAND);
					c.player.swingHand(Hand.MAIN_HAND);
					burned++;
					torched++;
					mark(c, "a seed burns (" + burned + ")");
				}
			}
			YOU.apply(c);
			return false;
		}
		// Pochita's turn: up he gets, and goes for them. (Staged: told to stand and to sit by the server, as your use key on him would.)
		hold(c, 0);
		if (!pochitaUp) {
			pochitaUp = true;
			mark(c, "Pochita is let go at the seeds");
			sitWhenDone = true;
			s.execute(() -> {
				if (s.getOverworld().getEntityById(pochitaId) instanceof io.github.kortev.chainsawman.PochitaEntity p) {
					p.setSitting(false);
					p.setInSittingPose(false);
				}
			});
		}
		// You stand by the seeds (he follows you there) and watch him at them.
		Vec3d look = pochita != null ? pochita.getPos() : !ss.isEmpty() ? ss.get(0).getPos() : null;
		if (!ss.isEmpty()) {
			Vec3d sp = ss.get(0).getPos();
			double dist = Math.hypot(sp.x - c.player.getX(), sp.z - c.player.getZ());
			if (dist > 4.5) {
				YOU.strafeTo(c, sp, 3.5, false);
			} else {
				YOU.stop();
			}
		} else {
			YOU.stop();
		}
		if (look != null) {
			YOU.aim(c, look.add(0.0, 0.3, 0.0));
		}
		YOU.apply(c);
		if (ss.size() <= keepSeeds && !sitWhenDone) {
			if (leaveOne && left == null && !ss.isEmpty()) {
				left = ss.get(0);
				mark(c, "this one is missed on purpose");
			}
			if (sub % 20 == 0) {
				mark(c, "the seeds are done for");
			}
			if (pochitaDoneAt < 0) {
				pochitaDoneAt = sub;
			}
			if (sub - pochitaDoneAt > 30) {
				pochitaDoneAt = -1;
				next(c, leaveOne ? "one left" : "all ended");
			}
		}
		return false;
	}

	private static int pochitaDoneAt = -1;

	/** The server's tick for the film: Pochita sat down again once only the seed left on purpose is there. */
	static void serverTick(MinecraftServer server) {
		if (!sitWhenDone || !(server instanceof IntegratedServer is)) {
			return;
		}
		ServerWorld w = is.getOverworld();
		// (Seeds he has bitten count till they are gone, so the last is never left half eaten.)
		int alive = w.getEntitiesByType(Devils.TOMATO_SEED, e -> e.isAlive() && e.burning() < 0 && e.withering() < 0).size();
		if (alive <= keepSeeds && w.getEntityById(pochitaId) instanceof io.github.kortev.chainsawman.PochitaEntity p) {
			p.setSitting(true);
			p.setInSittingPose(true);
			p.getNavigation().stop();
			sitWhenDone = false;
		}
	}

	private static boolean otherOak;
	private static boolean allowHit = true;

	private static Vec3d oak(MinecraftClient c) {
		int[] o = TomatoSelfTest.OAKS[otherOak ? 1 : 0];
		return TomatoSelfTest.site(o[0], 0.0, o[1]);
	}

	private static Vec3d nearestOak(MinecraftClient c) {
		Vec3d best = null;
		for (int[] o : TomatoSelfTest.OAKS) {
			Vec3d p = TomatoSelfTest.site(o[0], 0.0, o[1]);
			if (best == null || p.squaredDistanceTo(c.player.getPos()) < best.squaredDistanceTo(c.player.getPos())) {
				best = p;
			}
		}
		return best;
	}

	private static void report(MinecraftClient c) {
		ShootingStar.LOGGER.info("[played] REPORT ({}) first kill {} s from first sight; hits taken {}; rolls {}; seeds burned {}; total {} s", target(),
				killAt < 0 ? "-" : String.format("%.1f", (killAt - startAt) / 20.0), hits, rolls, burned, String.format("%.1f", (c.world.getTime() - startAt) / 20.0));
	}
}
