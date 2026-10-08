package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.client.gfx.Timings;
import io.github.kortev.shootingstar.client.thunder.ClientThunder;
import io.github.kortev.shootingstar.client.thunder.ClientThunders;
import io.github.kortev.shootingstar.registry.ModBlocks;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.strike.Targeting;
import io.github.kortev.shootingstar.thunder.ThunderManager;
import io.github.kortev.shootingstar.thunder.ThunderTimeline;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/**
 * With -Dshootingstar.selftest=mjolnir: joins the quick-play world, raises Mjölnir at flat ground a way off with a
 * creeper, a pig, a villager and some zombies standing in and round the zone, records the whole strike as a video (see
 * {@link Capture}) with a screenshot at every beat, then flies round what it left and photographs the crater, the bolt
 * and the scar. Every second of the capture it logs what each of the mod's drawing passes cost (see {@link Timings}),
 * how long the server's ticks took, and how far behind the lighting and the chunk meshes are.
 */
public class MjolnirSelfTest implements ClientModInitializer {
	private enum Stage { WAIT_WORLD, SETUP, SETTLE, FIRE, WATCH, FLYOVER, AFTER, DONE, FINISHED }

	private record Still(int age, String name) {
	}

	/** The beats to photograph, in ticks of the strike. */
	private static final int[] AGES = {4, 10, 16, 18, 21, 26, 34, 42, 50, 58, 70, 90, 110, 125, 145, 165, 185, 200, 212, 228,
			244, 260, 272, 280, 292, 304, 316, 326, 330, 336, 344, 352, 358, 362, 365, 366, 367, 368, 369, 371, 373, 375, 377,
			380, 383, 386, 389, 392, 396, 402, 412, 425, 440, 455, 470, 486, 500, 512, 530, 560, 600};
	private static final int FLYOVER_TICKS = 260;

	private static final Deque<Still> STILLS = new ArrayDeque<>();
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static BlockPos target;
	private static boolean fallbackFired;
	private static final List<Entity> SUBJECTS = new ArrayList<>();
	private static long serverTickStart;
	private static int perfTicks;

	@Override
	public void onInitializeClient() {
		if (!"mjolnir".equals(System.getProperty("shootingstar.selftest"))) {
			return;
		}
		Timings.on = true;
		for (int age : AGES) {
			STILLS.add(new Still(age, String.format("%02d_age%03d_%s.png", STILLS.size() + 1, age, phase(age))));
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(55 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-mjolnir-selftest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		// The lockstep wait happens in Capture's handler, so the tick is timed from after it.
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			Capture.serverTickStart();
			serverTickStart = System.nanoTime();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Timings.add("server.tick", System.nanoTime() - serverTickStart);
			Capture.serverTickEnd();
		});
		WorldRenderEvents.START.register(context -> Timings.begin("world"));
		WorldRenderEvents.END.register(context -> Timings.end());
		ClientTickEvents.END_CLIENT_TICK.register(MjolnirSelfTest::tick);
	}

	private static String phase(int age) {
		if (age < ThunderTimeline.CALL) return "raise";
		if (age < ThunderTimeline.RISE) return "call";
		if (age < ThunderTimeline.FEED) return "rise";
		if (age < ThunderTimeline.DRAW) return "orbit";
		if (age < ThunderTimeline.FORGE) return "draw";
		if (age < ThunderTimeline.LEADER) return "forge";
		if (age < ThunderTimeline.INBOUND) return "leader";
		if (age < ThunderTimeline.STROKE) return "inbound";
		if (age < ThunderTimeline.FRAMES_END) return "stroke";
		if (age < ThunderTimeline.WIDE_END) return "overhead";
		if (age < ThunderTimeline.CAMERA_END) return "back";
		return "after";
	}

	private static void shot(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot {}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {
		});
	}

	/** Once a second while capturing: what each pass cost, the server's ticks, and the lighting and meshing backlogs. */
	private static void perf(MinecraftClient client) {
		if (!Capture.active() || ++perfTicks % 20 != 0) {
			return;
		}
		ClientThunder thunder = ClientThunders.mine();
		IntegratedServer server = client.getServer();
		boolean serverLight = server != null && server.getOverworld().getLightingProvider().hasUpdates();
		ShootingStar.LOGGER.info("[perf] age {} | {}| light pending client {} server {} | {} | particles {}",
				thunder != null ? thunder.age : -1, Timings.drain(), client.world.getLightingProvider().hasUpdates(), serverLight,
				client.worldRenderer.getChunkBuilder().getDebugString(), client.particleManager.getDebugString());
	}

	private static void tick(MinecraftClient client) {
		ticks++;
		IntegratedServer server = client.getServer();
		perf(client);
		switch (stage) {
			case WAIT_WORLD -> {
				if (client.world != null && client.player != null && server != null) {
					ShootingStar.LOGGER.info("[selftest] joined world");
					stage = Stage.SETUP;
					ticks = 0;
				}
			}
			case SETUP -> {
				if (ticks < 40) {
					return;
				}
				server.execute(() -> setUp(server));
				stage = Stage.SETTLE;
				ticks = 0;
			}
			case SETTLE -> {
				if (ticks == 460) {
					client.player.getInventory().selectedSlot = 0;
				}
				if (ticks >= 500) {
					shot(client, "00_holding_mjolnir.png");
					stage = Stage.FIRE;
					ticks = 0;
				}
			}
			case FIRE -> {
				if (ticks == 1) {
					Capture.start(client, client.runDirectory.toPath().resolve("capture"));
				}
				if (ticks == 30) {
					ShootingStar.LOGGER.info("[selftest] raising Mjölnir");
					client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				}
				if (ClientThunders.mine() != null) {
					ShootingStar.LOGGER.info("[selftest] storm called");
					stage = Stage.WATCH;
					ticks = 0;
				} else if (ticks == 90 && !fallbackFired) {
					ShootingStar.LOGGER.warn("[selftest] the hammer found no target, calling the storm directly");
					fallbackFired = true;
					server.execute(() -> {
						ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
						ThunderManager.launch(player.getServerWorld(), target, player);
					});
				} else if (ticks > 200) {
					ShootingStar.LOGGER.error("[selftest] no strike ever started");
					stage = Stage.DONE;
				}
			}
			case WATCH -> {
				ClientThunder thunder = ClientThunders.mine();
				if (thunder != null) {
					target = thunder.target;
				}
				int age = thunder != null ? thunder.age : Integer.MAX_VALUE;
				while (!STILLS.isEmpty() && age >= STILLS.peek().age()) {
					shot(client, STILLS.poll().name());
				}
				if (age >= ThunderTimeline.CAMERA_END + 30 && age != Integer.MAX_VALUE || thunder == null && ticks > 900) {
					stage = Stage.FLYOVER;
					ticks = 0;
				}
			}
			case FLYOVER -> {
				ClientThunder thunder = ClientThunders.mine();
				while (thunder != null && !STILLS.isEmpty() && thunder.age >= STILLS.peek().age()) {
					shot(client, STILLS.poll().name());
				}
				if (ticks == 1) {
					client.options.hudHidden = true;
					Capture.camera = flyover(client);
					report(server, "after the strike");
				}
				if (ticks == FLYOVER_TICKS / 2) {
					shot(client, "80_flyover_mid.png");
				}
				if (ticks >= FLYOVER_TICKS) {
					shot(client, "81_flyover_end.png");
					Capture.stop();
					client.options.hudHidden = false;
					stage = Stage.AFTER;
					ticks = 0;
				}
			}
			case AFTER -> {
				int r = 64;
				if (ticks == 10) {
					client.options.hudHidden = true;
					server.execute(() -> lookFrom(server, target.getX(), target.getZ() + r * 13 / 10, r, Vec3d.ofCenter(target)));
				}
				if (ticks == 120) {
					shot(client, "90_crater_above.png");
					// Low at the rim, looking up at the bolt.
					server.execute(() -> lookFrom(server, target.getX() + r / 2, target.getZ() + r / 3, 3,
							Vec3d.ofCenter(target.up(30))));
				}
				if (ticks == 220) {
					shot(client, "91_bolt.png");
					// Down into the crater's fused floor.
					server.execute(() -> lookFrom(server, target.getX() + 16, target.getZ() + 4, 10, Vec3d.ofCenter(target.down(8))));
				}
				if (ticks == 320) {
					shot(client, "92_crater_close.png");
					// Out over the scar at the edge of the zone.
					server.execute(() -> lookFrom(server, target.getX() - r, target.getZ() - 10, 14,
							Vec3d.ofCenter(target.add(-r - 20, 0, -10))));
				}
				if (ticks == 420) {
					shot(client, "93_scar.png");
					// Night falls on it: the glow of the charged fulgurite.
					server.execute(() -> {
						server.getOverworld().setTimeOfDay(18000);
						lookFrom(server, target.getX(), target.getZ() + r * 13 / 10, r, Vec3d.ofCenter(target));
					});
				}
				if (ticks == 540) {
					shot(client, "94_crater_night.png");
					report(server, "at the end");
				}
				if (ticks >= 560) {
					client.options.hudHidden = false;
					stage = Stage.DONE;
				}
			}
			case DONE -> {
				ShootingStar.LOGGER.info("[selftest] finished");
				stage = Stage.FINISHED;
				client.scheduleStop();
			}
			case FINISHED -> {
			}
		}
	}

	/** What became of the creatures set out round the zone, and how much of the ground glows. */
	private static void report(IntegratedServer server, String when) {
		server.execute(() -> {
			StringBuilder out = new StringBuilder();
			for (Entity e : SUBJECTS) {
				out.append(EntityType.getId(e.getType()).getPath()).append(e.isRemoved() ? " removed(" + e.getRemovalReason() + ")" : "");
				if (e instanceof LivingEntity living) {
					out.append(String.format(Locale.ROOT, " hp %.1f", living.getHealth())).append(living.isAlive() ? "" : " dead");
					if (living.getRecentDamageSource() != null) {
						out.append(" last hit ").append(living.getRecentDamageSource().getName());
					}
				}
				if (e instanceof CreeperEntity creeper) {
					out.append(creeper.shouldRenderOverlay() ? " charged" : " plain");
				}
				out.append(String.format(Locale.ROOT, " at %.0f from the strike; ", Math.sqrt(e.squaredDistanceTo(Vec3d.ofCenter(target)))));
			}
			ServerWorld world = server.getOverworld();
			int charged = 0;
			int fire = 0;
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (int dx = -100; dx <= 100; dx++) {
				for (int dz = -100; dz <= 100; dz++) {
					int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, target.getX() + dx, target.getZ() + dz);
					for (int y = top; y > top - 20; y--) {
						BlockState state = world.getBlockState(pos.set(target.getX() + dx, y, target.getZ() + dz));
						charged += state.isOf(ModBlocks.CHARGED_FULGURITE) ? 1 : 0;
						fire += state.isOf(Blocks.FIRE) ? 1 : 0;
					}
				}
			}
			ShootingStar.LOGGER.info("[selftest] {}: {}| {} charged fulgurite, {} fires in the top 20 blocks", when, out, charged, fire);
		});
	}

	/** A slow crane round the crater on the shooter's side, easing in from wherever the camera is. */
	private static DoubleFunction<Capture.Pose> flyover(MinecraftClient client) {
		Vec3d startPos = client.gameRenderer.getCamera().getPos();
		float startYaw = client.gameRenderer.getCamera().getYaw();
		float startPitch = client.gameRenderer.getCamera().getPitch();
		double t0 = Capture.time();
		Vec3d center = Vec3d.ofBottomCenter(target.up());
		Vec3d away = startPos.subtract(center);
		double bearing = Math.atan2(away.z, away.x);
		int r = 64;
		double floor = center.y;
		for (int i = 0; i <= 48; i++) {
			double a = bearing + Math.toRadians(-60 + 120 * i / 48.0);
			for (double dist = 0.9 * r; dist <= 1.8 * r; dist += 8) {
				int x = MathHelper.floor(center.x + Math.cos(a) * dist);
				int z = MathHelper.floor(center.z + Math.sin(a) * dist);
				floor = Math.max(floor, client.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) + 10.0);
			}
		}
		double safe = floor;
		return time -> {
			double p = MathHelper.clamp((time - t0) / FLYOVER_TICKS, 0.0, 1.0);
			double e = p * p * (3 - 2 * p);
			double a = bearing + Math.toRadians(-55 + 110 * e);
			double dist = MathHelper.lerp(e, 1.7 * r, 1.0 * r);
			double y = Math.max(center.y + MathHelper.lerp(e, 0.9 * r, 0.35 * r), safe);
			Vec3d eye = new Vec3d(center.x + Math.cos(a) * dist, y, center.z + Math.sin(a) * dist);
			Vec3d at = center.add(0, 0.3 * r, 0);
			Capture.Pose to = pose(eye, at);
			double b = MathHelper.clamp((time - t0) / 40.0, 0.0, 1.0);
			b = b * b * (3 - 2 * b);
			return new Capture.Pose(MathHelper.lerp(b, startPos.x, to.x()), MathHelper.lerp(b, startPos.y, to.y()),
					MathHelper.lerp(b, startPos.z, to.z()), MathHelper.lerpAngleDegrees((float) b, startYaw, to.yaw()),
					(float) MathHelper.lerp(b, startPitch, to.pitch()));
		};
	}

	private static Capture.Pose pose(Vec3d eye, Vec3d at) {
		Vec3d d = at.subtract(eye);
		float yaw = (float) (MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * MathHelper.DEGREES_PER_RADIAN);
		return new Capture.Pose(eye.x, eye.y, eye.z, yaw, pitch);
	}

	private static void setUp(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		world.setTimeOfDay(4000);
		world.setWeather(12000, 0, false, false);
		player.changeGameMode(GameMode.CREATIVE);
		player.getInventory().setStack(0, new ItemStack(ModItems.MJOLNIR));

		// The flattest ground a little beyond the closest the hammer will strike.
		BlockPos spawn = world.getSpawnPos();
		double range = ThunderTimeline.minRange(ThunderTimeline.DEFAULT_RADIUS) + 30.0;
		double bestScore = Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8;
			int x = spawn.getX() + (int) Math.round(Math.cos(angle) * range);
			int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * range);
			double score = roughness(world, x, z);
			if (score < bestScore) {
				bestScore = score;
				target = new BlockPos(x, top(world, x, z) - 1, z);
			}
		}
		double x = spawn.getX() + 0.5;
		double z = spawn.getZ() + 0.5;
		// Stand on the ground; if the target cannot be seen from there, on a pillar tall enough to see it.
		int ground = top(world, spawn.getX(), spawn.getZ());
		int feet = ground;
		while (feet < ground + 60 && !reaches(world, new Vec3d(x, feet + player.getStandingEyeHeight(), z), target)) {
			feet++;
		}
		for (int y = ground; y < feet; y++) {
			world.setBlockState(new BlockPos(spawn.getX(), y, spawn.getZ()), Blocks.STONE.getDefaultState());
		}
		look(server, x, feet, z, Vec3d.ofCenter(target));
		ShootingStar.LOGGER.info("[selftest] standing at {} {} {}, aiming at {}", x, feet, z, target);

		// Creatures round the target, on the side facing the shooter: two in the zone, three in the ring the arcs reach.
		Vec3d toward = new Vec3d(x - target.getX(), 0, z - target.getZ()).normalize();
		Vec3d side = new Vec3d(-toward.z, 0, toward.x);
		subject(world, EntityType.ZOMBIE, toward.multiply(30).add(side.multiply(8)));
		subject(world, EntityType.ZOMBIE, toward.multiply(50).add(side.multiply(-14)));
		subject(world, EntityType.CREEPER, toward.multiply(76).add(side.multiply(10)));
		subject(world, EntityType.PIG, toward.multiply(70).add(side.multiply(-22)));
		subject(world, EntityType.VILLAGER, toward.multiply(84).add(side.multiply(-6)));
	}

	private static void subject(ServerWorld world, EntityType<? extends MobEntity> type, Vec3d offset) {
		int x = MathHelper.floor(target.getX() + offset.x);
		int z = MathHelper.floor(target.getZ() + offset.z);
		BlockPos at = new BlockPos(x, top(world, x, z), z);
		MobEntity mob = type.spawn(world, at, SpawnReason.COMMAND);
		if (mob != null) {
			mob.setPersistent();
			SUBJECTS.add(mob);
			ShootingStar.LOGGER.info("[selftest] {} set out at {}", EntityType.getId(type).getPath(), at);
		}
	}

	private static boolean reaches(ServerWorld world, Vec3d eye, BlockPos target) {
		BlockPos hit = Targeting.findTarget(world, eye, Vec3d.ofCenter(target).subtract(eye).normalize(), ThunderTimeline.MAX_RANGE);
		return hit != null && hit.getManhattanDistance(target) <= 2;
	}

	private static void lookFrom(IntegratedServer server, int x, int z, int height, Vec3d at) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		Vec3d from = new Vec3d(x + 0.5, Math.max(top(world, x, z), at.y - 40) + height, z + 0.5);
		look(server, from.x, from.y - player.getStandingEyeHeight(), from.z, at);
	}

	private static int top(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
	}

	private static double roughness(ServerWorld world, int cx, int cz) {
		double sum = 0;
		double sumSq = 0;
		int n = 0;
		for (int dx = -32; dx <= 32; dx += 8) {
			for (int dz = -32; dz <= 32; dz += 8) {
				int h = top(world, cx + dx, cz + dz);
				sum += h;
				sumSq += (double) h * h;
				n++;
			}
		}
		double mean = sum / n;
		double variance = sumSq / n - mean * mean;
		boolean water = !world.getFluidState(new BlockPos(cx, top(world, cx, cz) - 1, cz)).isEmpty();
		return variance + (water ? 400 : 0);
	}

	private static void look(IntegratedServer server, double x, double feet, double z, Vec3d at) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		double dx = at.x - x;
		double dy = at.y - (feet + player.getStandingEyeHeight());
		double dz = at.z - z;
		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * MathHelper.DEGREES_PER_RADIAN);
		player.networkHandler.requestTeleport(x, feet, z, yaw, pitch);
	}
}
