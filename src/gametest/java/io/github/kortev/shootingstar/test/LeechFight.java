package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.LeechDevilEntity;
import io.github.kortev.chainsawman.devil.LeechPartEntity;
import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * -Ptarget=played: the Leech Devil fought from your eyes, first person, the HUD on, the normal lens, at dusk, on open grass. Nothing is posed
 * in the fight: the {@link Fighter} plays it through the real keys (an iron sword, the game's own attack), watching each move's tell. It
 * walks your way not having seen you; it stops, its face finds you, it hums and circles; you close in and cut it between its moves; its
 * tongue is sidestepped (and cut while it is held out); the first latch is let catch you and cut loose (three cuts on the arm), the rest
 * stepped out of; you back off to bait the trample, step aside and cut it as it skids; you kill it, watch it fold, and pick up its sucker.
 * Staged: its walk toward you before it notices you (told where to walk), and your health kept from running out.
 *
 * <p>-Ptarget=played-iron / played-stone (no film): one fight from first sight to its death with that sword, every latch stepped out of,
 * logged ("[played] KILL").
 */
final class LeechFight {
	private LeechFight() {
	}

	private static final Fighter YOU = new Fighter();
	private static int step;
	private static int sub;
	private static long startAt = -1;
	private static long killAt = -1;
	private static int hits;
	private static float lastHealth = -1.0F;
	private static int lastState = -1;
	private static double dodgeSide = 1.0;
	private static Vec3d dodgeTo;
	private static int tongues;
	private static int tongueHits;
	private static int latches;
	private static int latchesCut;
	private static int tramples;
	private static int trampleHits;
	private static int tongueCuts;
	private static boolean film;
	/** The fight's parts (the film): 0 meet it, 1 bait the trample, 2 fight it out. */
	private static int phase;
	private static Vec3d diedAt;
	private static int trampledBefore;

	private static String target() {
		return System.getProperty("shootingstar.selftest.target", "");
	}

	private static void mark(MinecraftClient c, String what) {
		ShootingStar.LOGGER.info("[played] mark {} at capture {} (world {}, {} s in)", what, String.format("%.1f", Capture.active() ? Capture.time() : -1.0), c.world.getTime(),
				startAt < 0 ? "-" : String.format("%.1f", (c.world.getTime() - startAt) / 20.0));
	}

	private static void next(MinecraftClient c, String why) {
		step++;
		sub = 0;
		mark(c, "step " + step + " " + why);
	}

	private static LeechDevilEntity devil(MinecraftClient c) {
		LeechDevilEntity best = null;
		for (Entity e : c.world.getEntities()) {
			if (e instanceof LeechDevilEntity d && !d.isRemoved() && (best == null || d.squaredDistanceTo(c.player) < best.squaredDistanceTo(c.player))) {
				best = d;
			}
		}
		return best;
	}

	private static LeechPartEntity part(MinecraftClient c, int kind) {
		LeechPartEntity best = null;
		for (Entity e : c.world.getEntities()) {
			if (e instanceof LeechPartEntity p && p.kind() == kind && (best == null || p.squaredDistanceTo(c.player) < best.squaredDistanceTo(c.player))) {
				best = p;
			}
		}
		return best;
	}

	private static Vec3d forward(float yaw) {
		float r = yaw * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(r), 0.0, MathHelper.cos(r));
	}

	/** Where to look on it: its face when it is near, its middle further off. */
	private static Vec3d chest(LeechDevilEntity d) {
		return d.getPos().add(0.0, 1.4, 0.0).add(forward(d.getBodyYaw()).multiply(0.6));
	}

	static void snap(MinecraftServer server) {
		if (!LeechSelfTest.active() || !(server instanceof IntegratedServer is) || is.getPlayerManager().getPlayerList().isEmpty() || !target().startsWith("played")) {
			return;
		}
		ServerPlayerEntity p = LeechSelfTest.you(is);
		// (Staged: your health kept from running out; the hits still show and count.)
		if (p.isAlive() && p.getHealth() < 8.0F && p.age % 5 == 0) {
			p.setHealth(p.getMaxHealth());
		}
		p.getHungerManager().setFoodLevel(20);
		for (LeechDevilEntity d : is.getOverworld().getEntitiesByType(Devils.LEECH_DEVIL, e -> true)) {
			if (d.state() != sState) {
				sState = d.state();
				ShootingStar.LOGGER.info("[played] server: devil state {} at {} ({} from you), its health {}, yours {}", d.state(),
						String.format("%.1f %.1f %.1f", d.getX(), d.getY(), d.getZ()), String.format("%.1f", d.distanceTo(p)), d.getHealth(), p.getHealth());
			}
		}
		if (p.getHealth() < sYou - 0.4F) {
			ShootingStar.LOGGER.info("[played] server: you hurt to {} by {}", p.getHealth(), p.getRecentDamageSource());
		}
		sYou = p.getHealth();
	}

	private static int sState = -1;
	private static float sYou = 20;

	private static boolean allowHit(LeechDevilEntity d) {
		// (In the film's first parts, keep it alive for its moves: cut it only while it has more than half its health.)
		return !film || phase >= 2 || d.getHealth() > LeechDevilEntity.MAX_HEALTH * 0.5F;
	}

	/**
	 * Step in and cut it, and step back out while the sword charges again (so you are not standing in its face): in to the edge of your reach
	 * only once the blade is ready.
	 */
	private static void press(MinecraftClient c, LeechDevilEntity d) {
		double dist = Math.hypot(d.getX() - c.player.getX(), d.getZ() - c.player.getZ());
		YOU.aim(c, chest(d));
		boolean ready = c.player.getAttackCooldownProgress(0.5F) > 0.8F;
		double want = ready && allowHit(d) ? LeechDevilEntity.WIDTH * 0.5 + 2.6 : LeechDevilEntity.WIDTH * 0.5 + 4.8;
		if (dist > want + 0.4) {
			YOU.strafeTo(c, d.getPos(), want, dist > want + 3.0);
		} else if (dist < want - 0.6) {
			YOU.strafeTo(c, c.player.getPos().add(c.player.getPos().subtract(d.getPos()).multiply(1.0, 0.0, 1.0).normalize().multiply(2.0)), 0.2, false);
		} else {
			YOU.stop();
		}
		if (c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == d && allowHit(d)) {
			YOU.swing(c, 0.95F);
		}
	}

	/** Out of the way of what is coming and cutting it when it can be cut. */
	private static void fight(MinecraftClient c, LeechDevilEntity d, boolean takeLatch) {
		Vec3d me = c.player.getPos();
		Vec3d f = forward(d.aim());
		Vec3d side = new Vec3d(-f.z, 0.0, f.x);
		Vec3d rel = me.subtract(d.getPos());
		double across = rel.dotProduct(side);
		double along = rel.dotProduct(f);
		int st = d.state();
		float t = d.stateTime(0.0F);
		if (st != lastState) {
			lastState = st;
			dodgeTo = null;
			switch (st) {
				case LeechDevilEntity.TONGUE -> {
					tongues++;
					mark(c, "tongue tell " + tongues);
				}
				case LeechDevilEntity.LATCH -> {
					latches++;
					mark(c, "latch tell " + latches + (takeLatch ? " (let catch you)" : ""));
				}
				case LeechDevilEntity.LATCHED -> mark(c, "latched");
				case LeechDevilEntity.RECOIL -> {
					latchesCut++;
					mark(c, "the arm is cut loose (" + latchesCut + ")");
				}
				case LeechDevilEntity.TRAMPLE_TELL -> {
					tramples++;
					mark(c, "trample tell " + tramples);
				}
				case LeechDevilEntity.SKID -> mark(c, "it skids");
				default -> {
				}
			}
		}
		switch (st) {
			case LeechDevilEntity.TONGUE -> {
				YOU.aim(c, chest(d));
				if (t < 3) {
					// (a moment to see the tell)
					YOU.stop();
				} else if (t < LeechDevilEntity.TONGUE_HIT + 1) {
					// on the move, sideways, all through the tell: it lances where it heard you
					if (dodgeTo == null) {
						double s = Math.abs(across) > 0.5 ? Math.signum(across) : dodgeSide;
						dodgeSide = -dodgeSide;
						dodgeTo = me.add(side.multiply(s * 4.0));
					}
					YOU.strafeTo(c, dodgeTo, 0.3, true);
				} else if (t < LeechDevilEntity.TONGUE_HIT + LeechDevilEntity.TONGUE_HOLD) {
					// it is held out: cut it
					LeechPartEntity tp = part(c, LeechPartEntity.TONGUE);
					if ((int) t == LeechDevilEntity.TONGUE_HIT + 3) {
						mark(c, "tongue held out: part " + (tp == null ? "none" : String.format("%.1f away", tp.getPos().distanceTo(me))) + ", crosshair on "
								+ (c.crosshairTarget instanceof EntityHitResult eh ? eh.getEntity().getType().getUntranslatedName() : String.valueOf(c.crosshairTarget.getType()))
								+ String.format(", charged %.2f", c.player.getAttackCooldownProgress(0.5F)));
					}
					if (tp != null) {
						Vec3d at = tp.getPos().add(0.0, 0.65, 0.0);
						YOU.aim(c, at);
						double dd = Math.hypot(at.x - me.x, at.z - me.z);
						if (dd > 2.4) {
							YOU.strafeTo(c, at, 2.2, false);
						} else {
							YOU.stop();
						}
						if (c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == tp && YOU.swing(c, 0.7F)) {
							tongueCuts++;
							mark(c, "the tongue is cut (" + tongueCuts + ")");
						}
					} else {
						YOU.stop();
					}
				} else {
					press(c, d);
				}
			}
			case LeechDevilEntity.LATCH -> {
				YOU.aim(c, chest(d));
				if (takeLatch || t < LeechDevilEntity.LATCH_AIM) {
					// stood (to be caught), or waiting for it to set its aim
					YOU.stop();
				} else if (t < LeechDevilEntity.LATCH_TELL + LeechDevilEntity.LATCH_OUT + 2) {
					if (dodgeTo == null) {
						double s = Math.abs(across) > 0.5 ? Math.signum(across) : dodgeSide;
						dodgeSide = -dodgeSide;
						dodgeTo = me.add(side.multiply(s * 3.5));
					}
					YOU.strafeTo(c, dodgeTo, 0.3, true);
				} else {
					press(c, d);
				}
			}
			case LeechDevilEntity.LATCHED -> {
				if (d.stuckTo() != c.player.getId()) {
					press(c, d);
					break;
				}
				// stuck: cut the arm
				YOU.stop();
				LeechPartEntity arm = part(c, LeechPartEntity.ARM);
				if (arm != null) {
					YOU.aim(c, arm.getPos().add(0.0, 0.65, 0.0));
					if (c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == arm) {
						YOU.swing(c, 0.9F);
					}
				}
			}
			case LeechDevilEntity.TRAMPLE_TELL, LeechDevilEntity.TRAMPLE -> {
				YOU.aim(c, chest(d));
				boolean inLine = along > -2.0 && Math.abs(across) < LeechDevilEntity.TRAMPLE_HALF_WIDTH + 1.4;
				if (st == LeechDevilEntity.TRAMPLE_TELL && t < LeechDevilEntity.TRAMPLE_AIM - 2) {
					YOU.stop();
				} else if (inLine || dodgeTo != null) {
					if (dodgeTo == null) {
						double s = Math.abs(across) > 0.3 ? Math.signum(across) : dodgeSide;
						dodgeSide = -dodgeSide;
						dodgeTo = me.add(side.multiply(s * (4.0 - Math.abs(across))));
					}
					YOU.strafeTo(c, dodgeTo, 0.3, true);
				} else {
					YOU.stop();
				}
			}
			case LeechDevilEntity.SKID, LeechDevilEntity.RECOIL -> press(c, d);
			default -> {
				if (film && phase == 1) {
					// bait it: back off to 10 blocks and wait there, facing it
					YOU.aim(c, chest(d));
					double dist = Math.hypot(rel.x, rel.z);
					if (dist < 10.5) {
						Vec3d away = dist < 0.1 ? f : new Vec3d(rel.x, 0.0, rel.z).multiply(1.0 / dist);
						YOU.strafeTo(c, d.getPos().add(away.multiply(12.0)), 0.5, false);
					} else {
						YOU.stop();
					}
				} else {
					press(c, d);
				}
			}
		}
	}

	/** One client tick of the take; true when it is over. */
	static boolean tick(MinecraftClient c, IntegratedServer s, int f) {
		if (c.player == null || c.world == null) {
			return false;
		}
		film = target().equals("played");
		sub++;
		c.options.hudHidden = false;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(70);
		c.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
		Capture.camera = null;
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
			LeechDevilEntity rd = devil(c);
			if (rd != null && rd.state() == LeechDevilEntity.TONGUE) {
				tongueHits++;
			}
			if (rd != null && (rd.state() == LeechDevilEntity.TRAMPLE || rd.state() == LeechDevilEntity.SKID)) {
				trampleHits++;
			}
			mark(c, "you are hit (" + lastHealth + " to " + hp + ")");
		}
		lastHealth = hp;
		LeechDevilEntity d = devil(c);
		switch (step) {
			case 0 -> {
				if (sub == 1) {
					s.execute(() -> {
						ServerWorld w = s.getOverworld();
						for (LeechDevilEntity e : w.getEntitiesByType(Devils.LEECH_DEVIL, e -> true)) {
							e.discard();
						}
						ServerPlayerEntity you = LeechSelfTest.you(s);
						Vec3d at = LeechSelfTest.site(0.0, 0.0, -14.0);
						you.teleport(w, at.x, at.y, at.z, 0.0F, 0.0F);
						if (film) {
							// it comes from far off, walking your way, not having seen you yet
							LeechDevilEntity e = LeechSelfTest.devil(w, LeechSelfTest.site(3.0, 0.0, 9.0), 170.0F);
							e.walkTo = LeechSelfTest.site(0.0, 0.0, -8.0);
						} else {
							LeechSelfTest.devil(w, LeechSelfTest.site(0.0, 0.0, -2.0), 180.0F);
						}
					});
				}
				YOU.aim(c, LeechSelfTest.site(1.0, 1.6, 4.0));
				if (sub > 20 && d != null) {
					if (film && LeechSelfTest.capturing()) {
						Capture.start(c, c.runDirectory.toPath().resolve("capture"));
					}
					startAt = c.world.getTime();
					next(c, film ? "it walks your way" : "meet it");
					if (!film) {
						step = 2;
					}
				}
			}
			case 1 -> {
				if (d == null) {
					break;
				}
				YOU.aim(c, d.getPos().add(0.0, 1.6, 0.0));
				YOU.stop();
				if (sub == 70) {
					mark(c, "it notices you");
					s.execute(() -> {
						for (LeechDevilEntity e : s.getOverworld().getEntitiesByType(Devils.LEECH_DEVIL, e -> true)) {
							e.walkTo = null;
						}
					});
				}
				if (sub > 70 + LeechDevilEntity.NOTICE_STILL + 16) {
					next(c, "fight it");
				}
			}
			case 2 -> {
				if (d == null || d.isDead()) {
					killAt = c.world.getTime();
					diedAt = d != null ? d.getPos() : c.player.getPos();
					mark(c, "killed after " + String.format("%.1f s", (killAt - startAt) / 20.0));
					ShootingStar.LOGGER.info("[played] KILL ({}) {} s from first sight; hits taken {}; tongues {} (hit {}, cut {}); latches {} (cut loose {}); tramples {} (hit {})",
							target(), String.format("%.1f", (killAt - startAt) / 20.0), hits, tongues, tongueHits, tongueCuts, latches, latchesCut, tramples, trampleHits);
					if (!film) {
						report(c);
						YOU.release(c);
						return true;
					}
					next(c, "it dies");
					break;
				}
				// The film's parts: let the first latch catch you; once it is cut loose, bait the trample; once it has skidded, fight it out.
				if (film && phase == 0 && latchesCut >= 1 && d.state() == LeechDevilEntity.WALK) {
					phase = 1;
					trampledBefore = tramples;
					mark(c, "phase: bait the trample");
				}
				if (film && phase == 1 && tramples >= trampledBefore + 1 && d.state() != LeechDevilEntity.TRAMPLE_TELL && d.state() != LeechDevilEntity.TRAMPLE) {
					phase = 2;
					mark(c, "phase: fight it out");
				}
				fight(c, d, film && latchesCut == 0);
			}
			case 3 -> {
				// It sags and folds; watched from a few steps back.
				Vec3d at = d != null ? d.getPos() : diedAt;
				YOU.aim(c, at.add(0.0, 0.8, 0.0));
				double dist = Math.hypot(at.x - c.player.getX(), at.z - c.player.getZ());
				if (dist < 5.0) {
					YOU.strafeTo(c, at.add(c.player.getPos().subtract(at).multiply(1.0, 0.0, 1.0).normalize().multiply(5.5)), 0.4, false);
				} else {
					YOU.stop();
				}
				if (d == null && sub > 20 || sub > LeechDevilEntity.DEATH_TICKS + 20) {
					next(c, "pick up its sucker");
				}
			}
			case 4 -> {
				ItemEntity item = null;
				for (Entity e : c.world.getEntities()) {
					if (e instanceof ItemEntity ie && ie.getStack().isOf(Devils.LEECH_SUCKER)) {
						item = ie;
					}
				}
				if (item != null) {
					YOU.aim(c, item.getPos());
					YOU.strafeTo(c, item.getPos(), 0.3, false);
				} else {
					YOU.stop();
				}
				if (item == null && sub > 10 || sub > 140) {
					next(c, "done");
				}
			}
			case 5 -> {
				YOU.stop();
				YOU.aim(c, c.player.getEyePos().add(forward(c.player.getYaw()).multiply(6.0)).add(0.0, -2.0, 0.0));
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

	private static void report(MinecraftClient c) {
		ShootingStar.LOGGER.info("[played] REPORT ({}) killed {} s from first sight; hits taken {}; tongues {} (hit {}, cut {}); latches {} (cut loose {}); tramples {} (hit {}); total {} s",
				target(), killAt < 0 ? "-" : String.format("%.1f", (killAt - startAt) / 20.0), hits, tongues, tongueHits, tongueCuts, latches, latchesCut, tramples, trampleHits,
				String.format("%.1f", (c.world.getTime() - startAt) / 20.0));
	}
}
