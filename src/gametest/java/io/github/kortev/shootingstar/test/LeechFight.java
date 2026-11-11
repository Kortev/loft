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
 * -Ptarget=played: the Leech Devil fought from your eyes, first person, the HUD on, the normal lens, at dusk going into night, on open
 * grass. Nothing is posed in the fight: the {@link Fighter} plays it through the real keys (an iron sword, the game's own attack), watching
 * each move's tell. It walks your way not having seen you; it stops, its face finds you, it hums and circles at reach. It is a reach fighter
 * and so is the fight: you keep a few blocks off and cut what it reaches out at you: its tongue, sidestepped and cut while it is held out;
 * its arm, let catch you, cut loose, then cut again where it lies stunned on the ground; its body only when it skids after a trample (baited
 * by backing off). You kill it, watch it fold and pick up its sucker. Staged: its walk toward you before it notices you (told where to
 * walk), and your health kept from running out.
 *
 * <p>-Ptarget=played-iron / played-stone (no film): one fight from first sight to its death with that sword, logged ("[played] KILL").
 *
 * <p>The reels' takes (each its own capture): -Ptarget=reel-walk (at night it walks toward you from far off, humming, stops, and its face
 * turns to you), reel-latch (the sucker whips onto you, red at the edges, three cuts, it drops), reel-tongue (the tongue lancing past the
 * lens as you sidestep, cut; then the trample and the skid, cut).
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
	private static int limpCuts;
	private static int skidCuts;
	private static int bodyCuts;
	private static boolean film;
	private static long lastTrampleAt;
	private static boolean baiting;
	private static Vec3d diedAt;

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

	/** The nearest part of that kind (the tongue's two boxes, the stuck arm, the arm lying stunned). */
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

	/** Where to look: its middle, a little toward its face, so the whole of it is in the frame. */
	private static Vec3d chest(LeechDevilEntity d) {
		return d.getPos().add(0.0, 1.3, 0.0).add(forward(d.getBodyYaw()).multiply(0.8));
	}

	static void snap(MinecraftServer server) {
		if (!LeechSelfTest.active() || !(server instanceof IntegratedServer is) || is.getPlayerManager().getPlayerList().isEmpty() || !LeechSelfTest.portrait()
				&& !target().startsWith("played")) {
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
				ShootingStar.LOGGER.info("[played] server: devil state {} ({} from you), its health {}, yours {}", d.state(), String.format("%.1f", d.distanceTo(p)), d.getHealth(),
						p.getHealth());
			}
		}
		sYou = p.getHealth();
	}

	private static int sState = -1;
	private static float sYou = 20;

	/** Cut whatever is under the crosshair if it is {@code what}; true if a cut went in. */
	private static boolean cut(MinecraftClient c, Entity what, float charge) {
		return c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == what && YOU.swing(c, charge);
	}

	/** Go to a part (to the edge of your reach) and cut it; true if a cut went in. */
	private static boolean cutPart(MinecraftClient c, LeechPartEntity p, float charge) {
		Vec3d at = p.getPos().add(0.0, p.getHeight() * 0.85, 0.0);
		YOU.aim(c, at);
		double dd = Math.hypot(at.x - c.player.getX(), at.z - c.player.getZ());
		if (dd > 2.6) {
			YOU.strafeTo(c, at, 2.3, dd > 5.0);
		} else {
			YOU.stop();
		}
		return cut(c, p, charge);
	}

	/** Between its moves: keep a few blocks off it, facing it; it circles you. */
	private static void hold(MinecraftClient c, LeechDevilEntity d) {
		YOU.aim(c, chest(d));
		double dist = Math.hypot(d.getX() - c.player.getX(), d.getZ() - c.player.getZ());
		double want = baiting ? 11.0 : 5.8;
		Vec3d away = c.player.getPos().subtract(d.getPos()).multiply(1.0, 0.0, 1.0).normalize();
		if (dist < want - 0.8) {
			YOU.strafeTo(c, d.getPos().add(away.multiply(want)), 0.4, false);
		} else if (dist > want + 2.0) {
			YOU.strafeTo(c, d.getPos().add(away.multiply(want)), 0.4, false);
		} else {
			YOU.stop();
		}
	}

	private static void fight(MinecraftClient c, LeechDevilEntity d) {
		Vec3d me = c.player.getPos();
		Vec3d f = forward(d.aim());
		Vec3d side = new Vec3d(-f.z, 0.0, f.x);
		Vec3d rel = me.subtract(d.getPos());
		double across = rel.dotProduct(side);
		double along = rel.dotProduct(f);
		int st = d.state();
		float t = d.stateTime(0.0F);
		long now = c.world.getTime();
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
					mark(c, "latch tell " + latches);
				}
				case LeechDevilEntity.LATCHED -> mark(c, "latched");
				case LeechDevilEntity.RECOIL -> {
					latchesCut++;
					mark(c, "the arm is cut loose (" + latchesCut + ")");
				}
				case LeechDevilEntity.TRAMPLE_TELL -> {
					tramples++;
					baiting = false;
					lastTrampleAt = now;
					mark(c, "trample tell " + tramples);
				}
				case LeechDevilEntity.SKID -> mark(c, "it skids");
				default -> {
				}
			}
		}
		// No trample for a while: back off and bait one.
		if (!baiting && st == LeechDevilEntity.WALK && now - lastTrampleAt > 520 && d.limpAt() == null) {
			baiting = true;
			mark(c, "back off to bait the trample");
		}
		switch (st) {
			case LeechDevilEntity.TONGUE -> {
				if (t < 3) {
					YOU.aim(c, chest(d));
					YOU.stop();
				} else if (t < LeechDevilEntity.TONGUE_HIT + 1) {
					// on the move sideways all through the tell: it lances where it heard you
					YOU.aim(c, chest(d));
					if (dodgeTo == null) {
						double s = Math.abs(across) > 0.5 ? Math.signum(across) : dodgeSide;
						dodgeSide = -dodgeSide;
						dodgeTo = me.add(side.multiply(s * 4.0));
					}
					YOU.strafeTo(c, dodgeTo, 0.3, true);
				} else if (t < LeechDevilEntity.TONGUE_HIT + LeechDevilEntity.TONGUE_HOLD) {
					// held out: its end is by you: cut it
					LeechPartEntity tp = part(c, LeechPartEntity.TONGUE);
					if (tp != null && cutPart(c, tp, 0.5F)) {
						tongueCuts++;
						mark(c, "the tongue is cut (" + tongueCuts + ")");
					}
					if (tp == null) {
						YOU.stop();
					}
				} else {
					hold(c, d);
				}
			}
			case LeechDevilEntity.LATCH -> {
				// stood: let it catch you, the arm is what you want to cut
				YOU.aim(c, chest(d));
				YOU.stop();
			}
			case LeechDevilEntity.LATCHED -> {
				if (d.stuckTo() != c.player.getId()) {
					hold(c, d);
					break;
				}
				YOU.stop();
				LeechPartEntity arm = part(c, LeechPartEntity.ARM);
				if (arm != null) {
					YOU.aim(c, arm.getPos().add(0.0, arm.getHeight() * 0.5, 0.0));
					cut(c, arm, 0.9F);
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
						dodgeTo = me.add(side.multiply(s * (4.6 - Math.abs(across))));
					}
					YOU.strafeTo(c, dodgeTo, 0.3, true);
				} else {
					YOU.stop();
				}
			}
			case LeechDevilEntity.SKID -> {
				// open: in to its body and cut
				YOU.aim(c, chest(d));
				double dist = Math.hypot(rel.x, rel.z);
				if (dist > LeechDevilEntity.WIDTH * 0.5 + 2.5) {
					YOU.strafeTo(c, d.getPos(), LeechDevilEntity.WIDTH * 0.5 + 2.3, true);
				} else {
					YOU.stop();
				}
				if (cut(c, d, 0.9F)) {
					skidCuts++;
				}
			}
			default -> {
				// the arm lying stunned near you: cut it
				LeechPartEntity limp = d.limpAt() != null ? part(c, LeechPartEntity.ARM) : null;
				if (limp != null) {
					if (cutPart(c, limp, 0.9F)) {
						limpCuts++;
					}
				} else {
					hold(c, d);
					if (cut(c, d, 0.95F)) {
						bodyCuts++;
					}
				}
			}
		}
	}

	private static void common(MinecraftClient c) {
		c.options.hudHidden = false;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(70);
		c.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
		Capture.camera = null;
		c.inGameHud.getChatHud().clear(false);
	}

	private static void watchHealth(MinecraftClient c) {
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
		}
		lastHealth = hp;
	}

	/** One client tick of the take; true when it is over. */
	static boolean tick(MinecraftClient c, IntegratedServer s, int f) {
		if (c.player == null || c.world == null) {
			return false;
		}
		if (target().startsWith("reel")) {
			return reel(c, s, f);
		}
		film = target().equals("played");
		sub++;
		common(c);
		if (f > 9000) {
			ShootingStar.LOGGER.error("[played] stuck in step {}", step);
			report(c);
			YOU.release(c);
			return true;
		}
		watchHealth(c);
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
							// at dusk, the light going as it fights you; it comes from far off, walking your way, not having seen you yet
							w.setTimeOfDay(LeechSelfTest.NIGHTFALL);
							s.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(true, s);
							LeechDevilEntity e = LeechSelfTest.devil(w, LeechSelfTest.site(3.0, 0.0, 10.0), 170.0F);
							e.walkTo = LeechSelfTest.site(0.0, 0.0, -5.0);
						} else {
							LeechSelfTest.devil(w, LeechSelfTest.site(0.0, 0.0, -4.0), 180.0F);
						}
					});
				}
				YOU.aim(c, LeechSelfTest.site(1.0, 1.6, 4.0));
				if (sub > 20 && d != null) {
					if (film && LeechSelfTest.capturing()) {
						Capture.start(c, c.runDirectory.toPath().resolve("capture"));
					}
					startAt = c.world.getTime();
					lastTrampleAt = startAt;
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
				YOU.aim(c, d.getPos().add(0.0, 1.4, 0.0));
				YOU.stop();
				if (sub == 70) {
					mark(c, "it notices you");
					s.execute(() -> {
						for (LeechDevilEntity e : s.getOverworld().getEntitiesByType(Devils.LEECH_DEVIL, e -> true)) {
							e.walkTo = null;
						}
					});
				}
				if (sub > 70 + LeechDevilEntity.NOTICE_STILL + 16 || sub > 70 && d.state() != LeechDevilEntity.WALK) {
					next(c, "fight it");
				}
			}
			case 2 -> {
				if (d == null || d.isDead()) {
					killAt = c.world.getTime();
					diedAt = d != null ? d.getPos() : c.player.getPos();
					mark(c, "killed after " + String.format("%.1f s", (killAt - startAt) / 20.0));
					ShootingStar.LOGGER.info("[played] KILL ({}) {} s from first sight; hits taken {}; tongues {} (hit {}, cut {}); latches {} (cut loose {}); limp-arm cuts {}; "
							+ "tramples {} (hit {}, skid cuts {}); body cuts otherwise {}", target(), String.format("%.1f", (killAt - startAt) / 20.0), hits, tongues, tongueHits,
							tongueCuts, latches, latchesCut, limpCuts, tramples, trampleHits, skidCuts, bodyCuts);
					if (!film) {
						report(c);
						YOU.release(c);
						return true;
					}
					next(c, "it dies");
					break;
				}
				fight(c, d);
			}
			case 3 -> {
				Vec3d at = d != null ? d.getPos() : diedAt;
				YOU.aim(c, at.add(0.0, 0.8, 0.0));
				double dist = Math.hypot(at.x - c.player.getX(), at.z - c.player.getZ());
				if (dist < 6.0) {
					YOU.strafeTo(c, at.add(c.player.getPos().subtract(at).multiply(1.0, 0.0, 1.0).normalize().multiply(6.5)), 0.4, false);
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

	private static LeechDevilEntity serverDevil(IntegratedServer s) {
		for (LeechDevilEntity e : s.getOverworld().getEntitiesByType(Devils.LEECH_DEVIL, e -> e.isAlive())) {
			return e;
		}
		return null;
	}

	/** The reels' takes. */
	private static boolean reel(MinecraftClient c, IntegratedServer s, int f) {
		sub++;
		common(c);
		watchHealth(c);
		String t = target();
		LeechDevilEntity d = devil(c);
		if (f > 1200) {
			mark(c, "reel ran out of time");
			YOU.release(c);
			return true;
		}
		if (step == 0) {
			if (sub == 1) {
				s.execute(() -> {
					ServerWorld w = s.getOverworld();
					for (LeechDevilEntity e : w.getEntitiesByType(Devils.LEECH_DEVIL, e -> true)) {
						e.discard();
					}
					ServerPlayerEntity you = LeechSelfTest.you(s);
					Vec3d at = LeechSelfTest.site(0.0, 0.0, -14.0);
					you.teleport(w, at.x, at.y, at.z, 0.0F, 0.0F);
					if (t.equals("reel-walk")) {
						w.setTimeOfDay(18000L);
						LeechDevilEntity e = LeechSelfTest.devil(w, LeechSelfTest.site(0.0, 0.0, 14.0), 180.0F);
						e.walkTo = LeechSelfTest.site(0.0, 0.0, -6.0);
					} else {
						w.setTimeOfDay(LeechSelfTest.NIGHTFALL + 700L);
						LeechDevilEntity e = LeechSelfTest.devil(w, LeechSelfTest.site(0.0, 0.0, -7.0), 180.0F);
						e.walkTo = e.getPos();
					}
				});
			}
			YOU.aim(c, LeechSelfTest.site(0.0, 1.4, 4.0));
			if (sub > 20 && d != null) {
				if (LeechSelfTest.capturing()) {
					Capture.start(c, c.runDirectory.toPath().resolve("capture"));
				}
				startAt = c.world.getTime();
				step = 1;
				sub = 0;
				mark(c, "reel " + t + " starts");
			}
			YOU.apply(c);
			return false;
		}
		if (d == null) {
			YOU.apply(c);
			return sub > 40;
		}
		switch (t) {
			case "reel-walk" -> {
				// it walks at you from far off, humming; at 9 blocks it stops, and its face finds you
				YOU.aim(c, d.getPos().add(0.0, 1.3, 0.0));
				YOU.stop();
				double dist = d.distanceTo(c.player);
				if (sub % 20 == 0) {
					mark(c, String.format("devil at %.1f %.1f, %.1f from you, state %d", d.getX() - LeechSelfTest.SITE.getX(), d.getZ() - LeechSelfTest.SITE.getZ(), dist,
							d.state()));
				}
				if (step == 1 && dist < 12.5) {
					step = 2;
					sub = 0;
					mark(c, "it stops and notices you");
					s.execute(() -> {
						LeechDevilEntity e = serverDevil(s);
						if (e != null) {
							e.walkTo = null;
						}
					});
				}
				if (step == 2 && sub > LeechDevilEntity.NOTICE_STILL + 50) {
					YOU.release(c);
					return true;
				}
			}
			case "reel-latch" -> {
				YOU.stop();
				if (step == 1) {
					YOU.aim(c, chest(d));
					if (sub == 30) {
						s.execute(() -> {
							LeechDevilEntity e = serverDevil(s);
							if (e != null) {
								e.walkTo = null;
								e.begin(LeechDevilEntity.LATCH);
							}
						});
					}
					if (d.state() == LeechDevilEntity.LATCHED) {
						step = 2;
						sub = 0;
						mark(c, "latched");
					}
				} else if (step == 2) {
					LeechPartEntity arm = part(c, LeechPartEntity.ARM);
					if (d.state() == LeechDevilEntity.LATCHED && arm != null) {
						YOU.aim(c, arm.getPos().add(0.0, arm.getHeight() * 0.5, 0.0));
						cut(c, arm, 0.9F);
					}
					if (d.state() == LeechDevilEntity.RECOIL) {
						step = 3;
						sub = 0;
						mark(c, "torn loose");
					}
				} else {
					// it shrieks, the sucker drops; look at it, then at the devil
					YOU.aim(c, sub < 30 ? c.player.getEyePos().add(forward(c.player.getYaw()).multiply(2.0)).add(0.0, -1.6, 0.0) : chest(d));
					if (sub > 70) {
						YOU.release(c);
						return true;
					}
				}
			}
			default -> {
				// reel-tongue
				int st = d.state();
				float tt = d.stateTime(0.0F);
				Vec3d fw = forward(d.aim());
				Vec3d side = new Vec3d(-fw.z, 0.0, fw.x);
				if (step == 1) {
					YOU.aim(c, chest(d));
					YOU.stop();
					if (sub == 30) {
						s.execute(() -> {
							LeechDevilEntity e = serverDevil(s);
							if (e != null) {
								e.walkTo = null;
								e.begin(LeechDevilEntity.TONGUE);
							}
						});
					}
					if (st == LeechDevilEntity.TONGUE) {
						step = 2;
						sub = 0;
					}
				} else if (step == 2) {
					// a late step aside: the tongue goes past the lens
					if (st == LeechDevilEntity.TONGUE && tt < LeechDevilEntity.TONGUE_HIT + 1) {
						YOU.aim(c, chest(d));
						if (tt >= 10) {
							if (dodgeTo == null) {
								dodgeTo = c.player.getPos().add(side.multiply(3.0));
							}
							YOU.strafeTo(c, dodgeTo, 0.2, false);
						} else {
							YOU.stop();
						}
					} else if (st == LeechDevilEntity.TONGUE && tt < LeechDevilEntity.TONGUE_HIT + LeechDevilEntity.TONGUE_HOLD) {
						LeechPartEntity tp = part(c, LeechPartEntity.TONGUE);
						if (tp != null && cutPart(c, tp, 0.5F)) {
							mark(c, "the tongue is cut");
						}
					} else {
						dodgeTo = null;
						step = 3;
						sub = 0;
					}
				} else if (step == 3) {
					// back off, and it charges
					YOU.aim(c, chest(d));
					Vec3d away = c.player.getPos().subtract(d.getPos()).multiply(1.0, 0.0, 1.0).normalize();
					YOU.strafeTo(c, d.getPos().add(away.multiply(11.0)), 0.5, false);
					if (d.distanceTo(c.player) > 9.5 && st == LeechDevilEntity.WALK) {
						s.execute(() -> {
							LeechDevilEntity e = serverDevil(s);
							if (e != null && e.state() == LeechDevilEntity.WALK) {
								e.begin(LeechDevilEntity.TRAMPLE_TELL);
							}
						});
					}
					if (st == LeechDevilEntity.TRAMPLE_TELL) {
						step = 4;
						sub = 0;
					}
				} else if (step == 4) {
					fight(c, d);
					if (st == LeechDevilEntity.SKID && tt > 16 || st == LeechDevilEntity.WALK && sub > 40) {
						YOU.release(c);
						return true;
					}
				}
			}
		}
		YOU.apply(c);
		return false;
	}

	private static void report(MinecraftClient c) {
		ShootingStar.LOGGER.info("[played] REPORT ({}) killed {} s from first sight; hits taken {}; tongues {} (hit {}, cut {}); latches {} (cut loose {}); limp-arm cuts {}; "
				+ "tramples {} (hit {}, skid cuts {}); body cuts otherwise {}; total {} s", target(), killAt < 0 ? "-" : String.format("%.1f", (killAt - startAt) / 20.0), hits,
				tongues, tongueHits, tongueCuts, latches, latchesCut, limpCuts, tramples, trampleHits, skidCuts, bodyCuts, String.format("%.1f", (c.world.getTime() - startAt) / 20.0));
	}
}
