package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.TomatoArrivalEntity;
import io.github.kortev.chainsawman.devil.TomatoArrivals;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import io.github.kortev.chainsawman.devil.TomatoSeedEntity;
import io.github.kortev.shootingstar.ShootingStar;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

/**
 * -Ptarget=arrivals: the arrival alone, three takes for reels, at midnight on the levelled plain with a tree line behind where it comes up,
 * first person, the HUD on, the normal lens. Each runs from 3 s before the hush to 3 s after every eye snaps to you, in its own capture
 * folder ({@code capture_a}, {@code _b}, {@code _c}):
 * <ul>
 * <li>a: as it is, from about 15 blocks, standing;</li>
 * <li>b: you walk slowly toward it (sneaking) through the arrival, about 7-9 blocks off at the burst;</li>
 * <li>c: your back to it at the hush; you hear the breath and turn round about 3 s in.</li>
 * </ul>
 * Staged: the night's roll forced 3 s in (for c, the spot looked for behind you rather than in front); the rest is the arrival as it is.
 */
final class TomatoArrivalTakes {
	private TomatoArrivalTakes() {
	}

	/** Midnight of the world's second night. */
	static final long MIDNIGHT = 24000L + 18000L;
	private static final String[] TAKES = {"a", "b", "c"};

	private static int take;
	private static int sub;
	private static int phase;
	private static volatile int arrivalId = -1;
	private static Vec3d spot;
	private static int seenAt;
	private static int burstAt;

	/** A ragged line of oaks across the plain beyond where it comes up. */
	static void trees(ServerWorld world) {
		int[] xs = {-23, -19, -16, -12, -9, -5, -2, 2, 5, 9, 12, 16, 19, 23};
		for (int i = 0; i < xs.length; i++) {
			int dz = 11 + (i * 5 % 3);
			BlockPos base = BlockPos.ofFloored(TomatoSelfTest.site(xs[i], 0.0, dz));
			TomatoSelfTest.oak(world, base);
			if (i % 2 == 0) {
				world.setBlockState(base.up(6), Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
			}
		}
	}

	private static void mark(MinecraftClient c, String what) {
		ShootingStar.LOGGER.info("[arrivals] take {} mark {} at capture {} (world {})", TAKES[Math.min(take, 2)], what,
				String.format("%.1f", Capture.active() ? Capture.time() : -1.0), c.world.getTime());
	}

	private static TomatoDevilEntity devil(MinecraftClient c) {
		for (Entity e : c.world.getEntities()) {
			if (e instanceof TomatoDevilEntity d && d.isAlive()) {
				return d;
			}
		}
		return null;
	}

	/** One client tick of the takes; true when all three are done. */
	static boolean tick(MinecraftClient c, IntegratedServer s, int f) {
		if (c.player == null || c.world == null) {
			return false;
		}
		Fighter you = TomatoFight.YOU;
		sub++;
		c.options.hudHidden = false;
		c.options.setPerspective(Perspective.FIRST_PERSON);
		c.options.getFov().setValue(70);
		c.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
		Capture.camera = null;
		c.inGameHud.getChatHud().clear(false);
		if (f > 8000) {
			ShootingStar.LOGGER.error("[arrivals] stuck in take {} phase {}", take, phase);
			Capture.stop();
			you.release(c);
			return true;
		}
		String name = TAKES[take];
		boolean back = name.equals("c");
		boolean sneak = false;
		Vec3d ahead = TomatoSelfTest.site(0.0, 1.0, 4.0);
		Vec3d behind = TomatoSelfTest.site(0.0, 1.2, -40.0);
		switch (phase) {
			case 0 -> {
				// Ready: nothing of the last take left, you where you stand, midnight.
				if (sub == 1) {
					arrivalId = -1;
					spot = null;
					s.execute(() -> {
						ServerWorld w = s.getOverworld();
						for (Entity e : w.getEntitiesByClass(Entity.class, new Box(TomatoSelfTest.SITE).expand(140.0, 80.0, 140.0),
								e -> e instanceof TomatoDevilEntity || e instanceof TomatoSeedEntity || e instanceof TomatoArrivalEntity)) {
							e.discard();
						}
						s.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, s);
						w.setTimeOfDay(MIDNIGHT);
						ServerPlayerEntity p = TomatoSelfTest.you(s);
						p.removeAttached(TomatoArrivals.QUIET_UNTIL);
						p.setHealth(p.getMaxHealth());
						Vec3d at = TomatoSelfTest.site(0.0, 0.0, -14.0);
						p.teleport(w, at.x, at.y, at.z, back ? 180.0F : 0.0F, 4.0F);
						p.getInventory().selectedSlot = 0;
					});
				}
				you.turn = 18.0F;
				you.stop();
				if (sub > 3) {
					you.aim(c, back ? behind : ahead);
				}
				if (sub > 50) {
					if (TomatoSelfTest.capturing()) {
						Capture.start(c, c.runDirectory.toPath().resolve("capture_" + name));
					}
					phase = 1;
					sub = 0;
					mark(c, "start");
				}
			}
			case 1 -> {
				// Three seconds of the dark plain, then the roll is won.
				you.aim(c, back ? behind : TomatoSelfTest.site(1.2 * Math.sin(sub * 0.02), 1.0, 4.0));
				you.stop();
				if (sub >= 60 && sub % 5 == 0 && arrivalId < 0) {
					s.execute(() -> {
						ServerWorld w = s.getOverworld();
						var rule = w.getGameRules().get(GameRules.DO_MOB_SPAWNING);
						rule.set(true, s);
						TomatoArrivalEntity a = TomatoArrivals.arrive(TomatoSelfTest.you(s), 0.0F);
						rule.set(false, s);
						if (a != null) {
							arrivalId = a.getId();
							ShootingStar.LOGGER.info("[arrivals] arrival at {} ({} blocks)", a.getPos(), String.format("%.1f", a.distanceTo(TomatoSelfTest.you(s))));
						}
					});
				}
				if (arrivalId >= 0 && c.world.getEntityById(arrivalId) instanceof TomatoArrivalEntity a) {
					spot = a.getPos();
					seenAt = sub;
					phase = 2;
					sub = 0;
					mark(c, "the hush");
				}
			}
			case 2 -> {
				// The arrival.
				if (name.equals("a")) {
					you.stop();
					you.aim(c, sub < TomatoArrivalEntity.EYES + 6 ? ahead : spot.add(0.0, sub < TomatoArrivalEntity.BULGE ? 0.3 : 0.9, 0.0));
				} else if (name.equals("b")) {
					you.aim(c, spot.add(0.0, sub < TomatoArrivalEntity.BULGE ? 0.3 : 0.9, 0.0));
					double d = Math.hypot(spot.x - c.player.getX(), spot.z - c.player.getZ());
					if (d > 7.2) {
						you.strafeTo(c, spot, 7.2, false);
						sneak = true;
					} else {
						you.stop();
					}
				} else {
					you.stop();
					if (sub < TomatoArrivalEntity.EYES + 14) {
						you.aim(c, behind);
					} else {
						// The breath behind you: you turn round.
						you.turn = 9.0F;
						you.aim(c, spot.add(0.0, sub < TomatoArrivalEntity.BULGE ? 0.3 : 0.9, 0.0));
					}
				}
				if (sub == TomatoArrivalEntity.EYES) {
					mark(c, "eyes open");
				}
				if (sub == TomatoArrivalEntity.BULGE) {
					mark(c, "the ground bulges");
				}
				TomatoDevilEntity d = devil(c);
				if (d != null) {
					phase = 3;
					burstAt = sub;
					sub = 0;
					mark(c, "the burst, " + String.format("%.1f", Math.hypot(spot.x - c.player.getX(), spot.z - c.player.getZ())) + " blocks off");
				}
			}
			case 3 -> {
				// Every eye on you; three seconds of it.
				you.stop();
				TomatoDevilEntity d = devil(c);
				if (d != null) {
					you.aim(c, d.getPos().add(0.0, 2.0, 0.0));
				}
				if (sub >= 60) {
					mark(c, "end");
					Capture.stop();
					you.release(c);
					c.options.sneakKey.setPressed(false);
					take++;
					phase = 0;
					sub = 0;
					if (take >= TAKES.length) {
						return true;
					}
				}
			}
			default -> {
			}
		}
		you.apply(c);
		c.options.sneakKey.setPressed(sneak);
		return false;
	}
}
