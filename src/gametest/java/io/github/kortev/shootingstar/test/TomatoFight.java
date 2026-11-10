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
 * the {@link Fighter} plays it through the real keys (an iron sword, the game's own attack), watching each move's tell: it meets the devil,
 * steps back out of the slap and cuts it as it settles, stands in the first roll's line and is hit, baits the next roll into an oak and
 * cuts it on its back, steps out of the seeds' fan, and kills it; then burns four seeds with a torch and leaves one. Staged: the comeback
 * (the left seed is brought to its minute, and the swell runs its last four seconds instead of ten), the comeback's health (24 of 60, so
 * the film stays about a minute) and your health kept from running out.
 *
 * <p>-Ptarget=played-stone / played-iron / played-saws (no film): one devil from first sight to its death with that weapon (the saws: in
 * the Chainsaw Man form), logged ("[played] KILL").
 */
final class TomatoFight {
	private TomatoFight() {
	}

	private static final Fighter YOU = new Fighter();
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
		if (!TomatoSelfTest.active() || !(server instanceof IntegratedServer is) || is.getPlayerManager().getPlayerList().isEmpty() || !target().startsWith("played")) {
			return;
		}
		ServerPlayerEntity p = TomatoSelfTest.you(is);
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

	/** Out of the way of what is coming and hitting it when it can be hit; true while something is being dodged. */
	private static boolean fight(MinecraftClient c, TomatoDevilEntity d, boolean takeRoll, boolean baiting) {
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
				mark(c, "spit tell");
			} else if (st == TomatoDevilEntity.ON_BACK) {
				mark(c, "on its back");
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
				if (reach) {
					if (allowHit) {
						YOU.swing(c, 0.9F);
					}
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
				if (t <= TomatoDevilEntity.SPIT_TELL + 3) {
					// Out of the fan, sideways.
					if (dodgeTo == null) {
						double s = Math.abs(across) > 0.4 ? Math.signum(across) : dodgeSide;
						dodgeTo = me.add(side.multiply(s * 5.5));
						dodgeSide = -dodgeSide;
					}
					YOU.strafeTo(c, dodgeTo, 0.3, true);
					return true;
				}
				if (dist > 3.2) {
					YOU.strafeTo(c, d.getPos(), 3.0, true);
				}
				if (reach) {
					if (allowHit) {
						YOU.swing(c, 0.9F);
					}
				}
				return false;
			}
			case TomatoDevilEntity.ON_BACK, TomatoDevilEntity.GET_UP, TomatoDevilEntity.UNFURL, TomatoDevilEntity.EMERGE -> {
				if (dist > 3.0) {
					YOU.strafeTo(c, d.getPos(), 2.8, true);
				} else {
					YOU.stop();
				}
				if (reach) {
					if (allowHit) {
						YOU.swing(c, 0.9F);
					}
				}
				return false;
			}
			default -> {
				return false;
			}
		}
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
		if (f > 6000) {
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
		if (step >= 1 && step <= 4 && d == null && startAt >= 0 && sub > 5) {
			YOU.stop();
			if (killed(c, null)) {
				step = duel ? 99 : 4;
				next(c, "it bursts");
			}
			YOU.apply(c);
			return step == 99 + 1;
		}
		// (In the first part, keep it alive for its moves: cut it only while it has more than half its health.)
		allowHit = step >= 4 || d == null || d.getHealth() > TomatoDevilEntity.MAX_HEALTH * 0.5F;
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
						TomatoSelfTest.devil(w, TomatoSelfTest.site(0.0, 0.0, 2.0), 0.0F);
					});
				}
				YOU.aim(c, TomatoSelfTest.site(0.0, 1.8, 2.0));
				if (sub > (t.equals("played-saws") ? 60 : 20) && d != null) {
					if (film) {
						Capture.start(c, c.runDirectory.toPath().resolve("capture"));
					}
					startAt = c.world.getTime();
					hold(c, 0);
					next(c, "meet it");
				}
			}
			case 1 -> {
				// Meet it: walk up; the first slap is stepped out of and punished; then back off for its roll.
				if (d == null) {
					break;
				}
				boolean busy = fight(c, d, false, false);
				if (!busy && d.state() == TomatoDevilEntity.WALK) {
					double dist = d.distanceTo(c.player);
					if (dist > 3.6) {
						YOU.strafeTo(c, d.getPos(), 3.4, false);
					} else {
						YOU.stop();
					}
					if (c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == d) {
						if (allowHit) {
						YOU.swing(c, 0.95F);
					}
					}
				}
				if (duel ? false : lastState == TomatoDevilEntity.WALK && sub > 40 && slaps >= 1) {
					next(c, "back off for a roll");
				}
				if (duel && killed(c, d)) {
					return true;
				}
			}
			case 2 -> {
				// Off to its roll's range, facing it; the first roll is stood in and taken.
				if (d == null) {
					break;
				}
				boolean busy = fight(c, d, !rollHit, false);
				if (!busy && d.state() == TomatoDevilEntity.WALK) {
					Vec3d away = c.player.getPos().subtract(d.getPos()).multiply(1.0, 0.0, 1.0).normalize();
					if (d.distanceTo(c.player) < 7.5) {
						YOU.strafeTo(c, d.getPos().add(away.multiply(8.5)), 0.4, false);
					} else {
						YOU.stop();
					}
				}
				if (rollHit && d.state() != TomatoDevilEntity.ROLL) {
					next(c, "bait a roll into a tree");
				}
				if (sub > 900) {
					next(c, "no roll came");
				}
			}
			case 3 -> {
				// Between it and an oak, the oak close behind; out of the line when the roll is aimed.
				if (d == null) {
					break;
				}
				if (sub == 1 || d.state() == TomatoDevilEntity.WALK && d.distanceTo(c.player) < 5.0 && sub > 30) {
					// Too close for it to roll: off to the oak furthest from it, running.
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
					if (toSpot > 0.5) {
						// (Backing to it, facing the devil, so it stays in sight.)
						YOU.aim(c, chest(d));
						YOU.strafeTo(c, spot, 0.4, false);
					} else {
						YOU.aim(c, chest(d));
						YOU.stop();
					}
				}
				if (d.state() == TomatoDevilEntity.ON_BACK) {
					next(c, "punish it on its back");
				}
				if (rolls > 6) {
					next(c, "no roll into the tree");
				}
			}
			case 4 -> {
				// Fight it out.
				if (d == null || !d.isAlive()) {
					if (killed(c, d)) {
						next(c, "it bursts");
					}
					break;
				}
				boolean busy = fight(c, d, false, false);
				if (!busy && (d.state() == TomatoDevilEntity.WALK || d.state() == TomatoDevilEntity.SLAP && d.stateTime(0) > TomatoDevilEntity.SLAP_HIT)) {
					double dist = d.distanceTo(c.player);
					if (dist > 3.4) {
						YOU.strafeTo(c, d.getPos(), 3.2, false);
					} else if (dist < 2.6) {
						YOU.strafeTo(c, c.player.getPos().add(c.player.getPos().subtract(d.getPos()).normalize().multiply(1.0)), 0.2, false);
					} else {
						YOU.stop();
					}
					if (c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == d) {
						if (allowHit) {
						YOU.swing(c, 0.95F);
					}
					}
				}
			}
			case 5 -> {
				// The seeds: burn four with a torch, leave the furthest.
				return burnSeeds(c, s, 1);
			}
			case 6 -> {
				// It comes back out of the one that was left (staged: its minute brought on, and the swell's last four seconds).
				hold(c, 0);
				YOU.stop();
				if (left != null) {
					Vec3d lp = left.getPos();
					YOU.aim(c, lp.add(0.0, 0.6, 0.0));
					Vec3d away = c.player.getPos().subtract(lp).multiply(1.0, 0.0, 1.0).normalize();
					YOU.strafeTo(c, lp.add(away.multiply(7.0)), 0.5, false);
				}
				if (sub == 20) {
					s.execute(() -> {
						for (TomatoSeedEntity e : s.getOverworld().getEntitiesByType(Devils.TOMATO_SEED, e -> e.isAlive())) {
							e.lain(TomatoSeedEntity.COMEBACK - 1);
						}
					});
				}
				if (sub == 24) {
					mark(c, "the seed swells");
					s.execute(() -> {
						for (TomatoSeedEntity e : s.getOverworld().getEntitiesByType(Devils.TOMATO_SEED, e -> e.isAlive() && e.swelling() >= 0)) {
							e.swellTo(TomatoSeedEntity.SWELL - 80);
						}
					});
				}
				if (d != null) {
					mark(c, "it climbs out");
					s.execute(() -> {
						for (TomatoDevilEntity e : s.getOverworld().getEntitiesByType(Devils.TOMATO_DEVIL, e -> e.isAlive())) {
							e.setHealth(24.0F);
						}
					});
					next(c, "kill it again");
				}
			}
			case 7 -> {
				if (d == null || !d.isAlive()) {
					if (killed(c, d)) {
						next(c, "it bursts again");
					}
					break;
				}
				boolean busy = fight(c, d, false, false);
				if (!busy && d.state() != TomatoDevilEntity.SLAP) {
					double dist = d.distanceTo(c.player);
					if (dist > 3.4) {
						YOU.strafeTo(c, d.getPos(), 3.2, false);
					} else {
						YOU.stop();
					}
					if (c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == d) {
						if (allowHit) {
						YOU.swing(c, 0.95F);
					}
					}
				}
			}
			case 8 -> {
				return burnSeeds(c, s, 0);
			}
			case 9 -> {
				YOU.stop();
				hold(c, 0);
				YOU.aim(c, c.player.getEyePos().add(forward(c.player.getYaw()).multiply(6.0)).add(0.0, -1.5, 0.0));
				if (sub > 50) {
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
		ShootingStar.LOGGER.info("[played] KILL ({}) {} s from first sight, hits taken {}, rolls {}", target(), String.format("%.1f", (c.world.getTime() - startAt) / 20.0), hits, rolls);
		if (!target().equals("played")) {
			report(c);
			YOU.release(c);
			c.player.getInventory().selectedSlot = 0;
			step = 99;
		}
		return true;
	}

	private static long burnStart = -1;
	private static Vec3d dyingAt;

	/** Walks to each seed and burns it with the torch, leaving {@code leave} of them; true when the take ends. */
	private static boolean burnSeeds(MinecraftClient c, IntegratedServer s, int leave) {
		if (step == 99) {
			return true;
		}
		List<TomatoSeedEntity> ss = seeds(c);
		if (sub < TomatoDevilEntity.BURST_AT + 40) {
			// It curls up and bursts, the seeds fly; back off to watch it, then the line comes up.
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
				YOU.aim(c, ss.isEmpty() ? c.player.getEyePos().add(forward(c.player.getYaw()).multiply(5.0)).add(0, -1.5, 0) : ss.get(0).getPos());
			}
			YOU.apply(c);
			if (sub == 22) {
				mark(c, "the seeds are in the ground");
			}
			return false;
		}
		if (leave > 0 && left == null && !ss.isEmpty()) {
			left = ss.stream().max(Comparator.comparingDouble(x -> x.squaredDistanceTo(c.player))).orElse(null);
			mark(c, "this one is missed on purpose");
		}
		ss.removeIf(x -> x == left);
		if (ss.isEmpty()) {
			YOU.stop();
			YOU.apply(c);
			if (sub > 40) {
				next(c, leave > 0 ? "one left" : "all burned");
			}
			return false;
		}
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
				mark(c, "a seed burns (" + burned + ")");
			}
		}
		YOU.apply(c);
		return false;
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
