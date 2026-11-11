package io.github.kortev.shootingstar.test;

import io.github.kortev.chainsawman.devil.Devils;
import io.github.kortev.chainsawman.devil.TomatoArrivalEntity;
import io.github.kortev.chainsawman.devil.TomatoArrivals;
import io.github.kortev.chainsawman.devil.TomatoDevilEntity;
import io.github.kortev.chainsawman.devil.TomatoSeedEntity;
import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

/**
 * -Ptarget=firstnight: a Tomato Devil's arrival and its fight, played from your eyes (first person, the HUD on, the normal lens). You stand
 * by your little dirt hut on a plain with a stone sword and torches as night falls (the world's second night, the clock running). Three
 * seconds in, the night's roll is won for you (staged: the only thing forced; the rest is the spawner's own rules and the arrival as it is):
 * the music stops and the air drops out; eyes open low in the dark ahead and something breathes; the ground bulges and bursts and the devil
 * stands there, every eye on you. Then the fight, played through the real keys ({@link TomatoFight#fight}), to its death, and its seeds:
 * three burned with a torch, two cut up with the sword. Staged besides: your health kept from running out (as in the played film).
 */
final class TomatoFirstNight {
	private TomatoFirstNight() {
	}

	/** The world's second night, a moment before the clock calls it night (13000). */
	static final long START = 24000L + 12900L;

	private static int step;
	private static int sub;
	private static volatile int arrivalId = -1;
	private static Vec3d spot;
	private static Vec3d dyingAt;
	private static int torched;
	private static long startAt;

	/** Your hut: dirt, 5 by 5, a doorway toward the plain, a torch by the door (to your left and behind you). */
	static void hut(ServerWorld world) {
		BlockPos o = BlockPos.ofFloored(TomatoSelfTest.site(-9.0, 0.0, -21.0));
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				for (int y = 0; y < 4; y++) {
					boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
					boolean roof = y == 3;
					boolean door = z == 4 && x == 2 && y < 2;
					BlockPos p = o.add(x, y, z);
					world.setBlockState(p, (wall || roof) && !door ? Blocks.DIRT.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		world.setBlockState(o.add(3, 1, 5), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
		world.setBlockState(o.add(2, 0, 2), Blocks.CRAFTING_TABLE.getDefaultState(), Block.NOTIFY_LISTENERS);
	}

	private static void mark(MinecraftClient c, String what) {
		ShootingStar.LOGGER.info("[firstnight] mark {} at capture {} (world {})", what, String.format("%.1f", Capture.active() ? Capture.time() : -1.0),
				c.world.getTime());
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
			if (e instanceof TomatoSeedEntity s && s.isAlive() && !s.ended()) {
				out.add(s);
			}
		}
		return out;
	}

	private static Vec3d chest(TomatoDevilEntity d) {
		return d.getPos().add(0.0, d.state() == TomatoDevilEntity.ON_BACK || d.state() == TomatoDevilEntity.ROLL ? 1.0 : 2.2, 0.0);
	}

	/**
	 * Close in and cut it between its moves, at the edge of the sword's reach (its middle about 3.6-4.1 blocks off: its skin under 3), so the
	 * whole of it stays in the frame rather than filling it.
	 */
	private static void press(MinecraftClient c, TomatoDevilEntity d) {
		Fighter you = TomatoFight.YOU;
		double dist = Math.hypot(d.getX() - c.player.getX(), d.getZ() - c.player.getZ());
		if (dist > 4.1) {
			you.strafeTo(c, d.getPos(), 3.8, false);
		} else if (dist < 3.4) {
			you.strafeTo(c, c.player.getPos().add(c.player.getPos().subtract(d.getPos()).multiply(1.0, 0.0, 1.0).normalize().multiply(1.0)), 0.2, false);
		} else {
			you.stop();
		}
		you.aim(c, d.getPos().add(0.0, 1.8, 0.0));
		if (c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == d) {
			you.swing(c, 0.95F);
		}
	}

	/** Seeds given up on (out of reach behind something): their ids. */
	private static final java.util.Set<Integer> SKIPPED = new java.util.HashSet<>();
	private static int targetId = -1;
	private static int targetSince;

	/** One client tick of the take; true when it is over. */
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
		if (f > 6000) {
			ShootingStar.LOGGER.error("[firstnight] stuck in step {}", step);
			you.release(c);
			return true;
		}
		switch (step) {
			case 0 -> {
				if (sub == 1) {
					s.execute(() -> {
						ServerWorld w = s.getOverworld();
						for (Entity e : w.getEntitiesByType(Devils.TOMATO_DEVIL, e -> true)) {
							e.discard();
						}
						ServerPlayerEntity p = TomatoSelfTest.you(s);
						p.removeAttached(TomatoArrivals.QUIET_UNTIL);
						Vec3d at = TomatoSelfTest.site(0.0, 0.0, -14.0);
						p.teleport(w, at.x, at.y, at.z, 0.0F, 4.0F);
						p.getInventory().selectedSlot = 0;
						w.setTimeOfDay(START);
						s.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(true, s);
					});
				}
				you.aim(c, TomatoSelfTest.site(0.0, 1.0, 4.0));
				you.stop();
				if (sub > 40) {
					if (TomatoSelfTest.capturing()) {
						Capture.start(c, c.runDirectory.toPath().resolve("capture"));
					}
					startAt = c.world.getTime();
					next(c, "night falls by the hut");
				}
			}
			case 1 -> {
				// Three seconds of the plain as night falls; then the roll is won (and tried again till the clock says night).
				you.aim(c, TomatoSelfTest.site(1.5 * Math.sin(sub * 0.02), 1.0, 4.0));
				you.stop();
				if (sub >= 60 && sub % 5 == 0 && arrivalId < 0) {
					s.execute(() -> {
						ServerWorld w = s.getOverworld();
						var rule = w.getGameRules().get(GameRules.DO_MOB_SPAWNING);
						rule.set(true, s);
						TomatoArrivalEntity a = TomatoArrivals.arrive(TomatoSelfTest.you(s));
						rule.set(false, s);
						if (a != null) {
							arrivalId = a.getId();
							ShootingStar.LOGGER.info("[firstnight] arrival at {} (time of day {})", a.getPos(), w.getTimeOfDay() % 24000L);
						}
					});
				}
				if (arrivalId >= 0 && c.world.getEntityById(arrivalId) instanceof TomatoArrivalEntity a) {
					spot = a.getPos();
					next(c, "the music stops");
				}
			}
			case 2 -> {
				// The arrival: still at first; at the breath, turn to look at it.
				you.stop();
				if (sub < TomatoArrivalEntity.EYES + 6) {
					you.aim(c, TomatoSelfTest.site(0.0, 1.0, 4.0));
				} else {
					you.aim(c, spot.add(0.0, sub < TomatoArrivalEntity.BULGE ? 0.3 : 1.0, 0.0));
				}
				if (sub == TomatoArrivalEntity.EYES) {
					mark(c, "eyes open");
				}
				if (sub == TomatoArrivalEntity.BULGE) {
					mark(c, "the ground bulges");
				}
				TomatoDevilEntity d = devil(c);
				if (d != null) {
					next(c, "it bursts out");
				}
			}
			case 3 -> {
				TomatoDevilEntity d = devil(c);
				if (d == null) {
					if (sub > 5) {
						mark(c, "killed after " + String.format("%.1f s", (c.world.getTime() - startAt) / 20.0));
						next(c, "it bursts");
					}
					break;
				}
				if (sub < 28) {
					// Every eye on you: you stand a moment.
					you.aim(c, chest(d));
					you.stop();
					break;
				}
				boolean busy = TomatoFight.fight(c, d, false, false);
				if (!busy && (d.state() == TomatoDevilEntity.WALK || d.state() == TomatoDevilEntity.SLAP && d.stateTime(0) > TomatoDevilEntity.SLAP_HIT)) {
					press(c, d);
				}
			}
			case 4 -> {
				// It curls up and bursts: watched from a step back; then its seeds.
				if (sub < TomatoDevilEntity.BURST_AT + 40) {
					for (Entity e : c.world.getEntities()) {
						if (e instanceof TomatoDevilEntity td && td.isDead()) {
							dyingAt = td.getPos();
						}
					}
					if (dyingAt != null && sub < TomatoDevilEntity.BURST_AT + 6) {
						you.aim(c, dyingAt.add(0.0, 1.4, 0.0));
						Vec3d away = c.player.getPos().subtract(dyingAt).multiply(1.0, 0.0, 1.0).normalize();
						you.strafeTo(c, dyingAt.add(away.multiply(6.0)), 0.4, false);
					} else {
						you.stop();
						you.aim(c, dyingAt != null ? dyingAt.add(0.0, 0.3, 0.0) : c.player.getEyePos().add(0.0, -1.5, 3.0));
					}
					break;
				}
				List<TomatoSeedEntity> ss = seeds(c);
				ss.removeIf(x -> SKIPPED.contains(x.getId()));
				if (ss.isEmpty()) {
					next(c, "the seeds are done for");
					break;
				}
				ss.sort(Comparator.comparingDouble(x -> x.squaredDistanceTo(c.player)));
				TomatoSeedEntity target = ss.get(0);
				if (target.getId() != targetId) {
					targetId = target.getId();
					targetSince = sub;
				} else if (sub - targetSince > 100) {
					mark(c, "a seed out of reach is given up on");
					SKIPPED.add(targetId);
					break;
				}
				Vec3d at = target.getPos().add(0.0, 0.15, 0.0);
				you.aim(c, at);
				double dist = Math.hypot(at.x - c.player.getX(), at.z - c.player.getZ());
				boolean torch = torched < 3;
				c.player.getInventory().selectedSlot = torch ? 1 : 0;
				if (dist > 2.2) {
					you.strafeTo(c, at, 2.0, dist > 4.0);
				} else {
					you.stop();
					if (c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() == target) {
						if (torch && sub % 6 == 0) {
							c.interactionManager.interactEntity(c.player, target, Hand.MAIN_HAND);
							c.player.swingHand(Hand.MAIN_HAND);
							torched++;
							mark(c, "a seed burns (" + torched + ")");
						} else if (!torch && you.swing(c, 0.9F)) {
							mark(c, "a seed is cut");
						}
					}
				}
			}
			case 5 -> {
				you.stop();
				c.player.getInventory().selectedSlot = 0;
				if (sub > 50) {
					ShootingStar.LOGGER.info("[firstnight] REPORT total {} s", String.format("%.1f", (c.world.getTime() - startAt) / 20.0));
					you.release(c);
					return true;
				}
			}
			default -> {
			}
		}
		you.apply(c);
		return false;
	}
}
