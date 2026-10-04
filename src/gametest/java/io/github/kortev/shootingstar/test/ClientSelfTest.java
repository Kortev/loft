package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.strike.StrikeManager;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import io.github.kortev.shootingstar.strike.Targeting;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.DoubleFunction;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * With -Dshootingstar.selftest=true: joins the quick-play world, fires the uplink at a point just
 * outside danger-close range, records the whole strike as a video (see {@link Capture}) and saves a
 * screenshot at every phase, then flies over the crater and quits.
 */
public class ClientSelfTest implements ClientModInitializer {
	private enum Stage { WAIT_WORLD, SETUP, SETTLE, FIRE, WATCH, FLYOVER, AFTER, DONE, FINISHED }

	private static final int FLYOVER_TICKS = 220;

	/** A screenshot to save when the strike reaches {@code age}. */
	private record Still(int age, String name) {
	}

	private static final Deque<Still> STILLS = new ArrayDeque<>();
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static BlockPos target;
	private static boolean fallbackFired;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("shootingstar.selftest")) {
			return;
		}
		int[] ages = {6, 20, 38, 53, 62, 74, 82, 87, 90, 95, 100, 108, 120, 134, 143, 150, 160, 165, 180, 196, 210, 224, 236,
				245, 254, 262, 267, 272, 282, 296, 304, 310, 314, 324, 336, 342, 346, 352, 364, 400, 436, 470, 520};
		for (int age : ages) {
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
		}, "shootingstar-selftest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
		ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		ClientTickEvents.END_CLIENT_TICK.register(ClientSelfTest::tick);
	}

	private static String phase(int age) {
		if (age < StrikeTimeline.RISE) return "lock";
		if (age < StrikeTimeline.ORBIT) return "rise";
		if (age < StrikeTimeline.RELAY) return "orbit";
		if (age < StrikeTimeline.WAKE) return "relay";
		if (age < StrikeTimeline.LOADING) return "wake";
		if (age < StrikeTimeline.LAPS) return "loading";
		if (age < StrikeTimeline.DEBRIS) return "laps";
		if (age < StrikeTimeline.TERMINAL) return "debris";
		if (age < StrikeTimeline.INBOUND) return "terminal";
		if (age < StrikeTimeline.IMPACT) return "inbound";
		if (age < StrikeTimeline.IMPACT_FRAME_END) return "impact";
		if (age < StrikeTimeline.WIDE_END) return "wide";
		return "aftermath";
	}

	private static void shot(MinecraftClient client, String name) {
		ShootingStar.LOGGER.info("[selftest] screenshot {}", name);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> {
		});
	}

	private static void tick(MinecraftClient client) {
		ticks++;
		IntegratedServer server = client.getServer();
		ClientPlayerEntity self = client.player;
		if (self != null && stage.ordinal() >= Stage.SETTLE.ordinal() && self.getAbilities().allowFlying
				&& !self.getAbilities().flying) {
			// Vanilla stops creative flight whenever the player touches the ground. Keep hovering where the test puts us.
			self.getAbilities().flying = true;
			self.sendAbilitiesUpdate();
		}
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
				server.execute(() -> setUpPlayer(server));
				stage = Stage.SETTLE;
				ticks = 0;
			}
			case SETTLE -> {
				if (ticks == 460) {
					client.player.getInventory().selectedSlot = 0;
				}
				if (ticks >= 500) {
					shot(client, "00_holding_uplink.png");
					stage = Stage.FIRE;
					ticks = 0;
				}
			}
			case FIRE -> {
				if (ticks == 1) {
					Capture.start(client, client.runDirectory.toPath().resolve("capture"));
				}
				if (ticks == 30) {
					ShootingStar.LOGGER.info("[selftest] using the uplink");
					client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				}
				if (ClientStrikes.mine() != null) {
					ShootingStar.LOGGER.info("[selftest] strike locked");
					stage = Stage.WATCH;
					ticks = 0;
				} else if (ticks == 90 && !fallbackFired) {
					ShootingStar.LOGGER.warn("[selftest] the uplink found no target, calling the strike in directly");
					fallbackFired = true;
					server.execute(() -> {
						ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
						StrikeManager.launch(player.getServerWorld(), target, player);
					});
				} else if (ticks > 200) {
					ShootingStar.LOGGER.error("[selftest] no strike ever started");
					stage = Stage.DONE;
				}
			}
			case WATCH -> {
				ClientStrike strike = ClientStrikes.mine();
				if (strike != null) {
					target = strike.target;
				}
				int age = strike != null ? strike.age : Integer.MAX_VALUE;
				while (!STILLS.isEmpty() && age >= STILLS.peek().age()) {
					shot(client, STILLS.poll().name());
				}
				// Into the fly-over a moment after the camera is back with the shooter.
				if (age >= StrikeTimeline.CAMERA_END + 30 && age != Integer.MAX_VALUE || strike == null && ticks > 700) {
					stage = Stage.FLYOVER;
					ticks = 0;
				}
			}
			case FLYOVER -> {
				ClientStrike strike = ClientStrikes.mine();
				while (strike != null && !STILLS.isEmpty() && strike.age >= STILLS.peek().age()) {
					shot(client, STILLS.poll().name());
				}
				if (ticks == 1) {
					client.options.hudHidden = true;
					Capture.camera = flyover(client, target);
				}
				if (ticks >= FLYOVER_TICKS) {
					Capture.stop();
					client.options.hudHidden = false;
					stage = Stage.AFTER;
					ticks = 0;
				}
			}
			case AFTER -> {
				int r = Targeting.DEFAULT_RADIUS;
				if (ticks == 10) {
					compareWorlds(client, server, target, r);
				}
				if (ticks == 20) {
					server.execute(() -> lookFromAbove(server, target.getX(), target.getZ() - r * 13 / 10, r * 9 / 10,
							Vec3d.ofCenter(target)));
				}
				if (ticks == 140) {
					shot(client, "90_crater_above.png");
					server.execute(() -> lookFromAbove(server, target.getX() - r * 17 / 10, target.getZ() + 10, 16,
							Vec3d.ofCenter(target.up(r / 2))));
				}
				if (ticks == 260) {
					shot(client, "91_crater_side.png");
					server.execute(() -> lookFromAbove(server, target.getX() + r / 3, target.getZ() + r / 4, 8,
							Vec3d.ofCenter(target.up(2))));
				}
				if (ticks == 380) {
					shot(client, "92_crater_close.png");
				}
				if (ticks >= 420) {
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

	/**
	 * A slow crane round the crater on the shooter's side (where the chunks are loaded), easing in from
	 * wherever the camera is when it starts.
	 */
	private static DoubleFunction<Capture.Pose> flyover(MinecraftClient client, BlockPos target) {
		Camera camera = client.gameRenderer.getCamera();
		Vec3d startPos = camera.getPos();
		float startYaw = camera.getYaw();
		float startPitch = camera.getPitch();
		double t0 = Capture.time();
		Vec3d center = Vec3d.ofBottomCenter(target.up());
		Vec3d away = startPos.subtract(center);
		double bearing = Math.atan2(away.z, away.x);
		int r = Targeting.DEFAULT_RADIUS;
		// Keep the whole arc clear of the hills it passes over.
		double floor = center.y;
		for (int i = 0; i <= 48; i++) {
			double a = bearing + Math.toRadians(-60 + 120 * i / 48.0);
			for (double dist = 1.2 * r; dist <= 2.0 * r; dist += 8) {
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
			double dist = MathHelper.lerp(e, 1.9 * r, 1.35 * r);
			double y = Math.max(center.y + MathHelper.lerp(e, 0.85 * r, 0.4 * r), safe);
			Vec3d eye = new Vec3d(center.x + Math.cos(a) * dist, y, center.z + Math.sin(a) * dist);
			Vec3d at = center.add(0, 0.25 * r, 0);
			double dx = at.x - eye.x;
			double dy = at.y - eye.y;
			double dz = at.z - eye.z;
			float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
			float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * MathHelper.DEGREES_PER_RADIAN);
			// Ease in from the starting view over the first two seconds.
			double b = MathHelper.clamp((time - t0) / 40.0, 0.0, 1.0);
			b = b * b * (3 - 2 * b);
			return new Capture.Pose(MathHelper.lerp(b, startPos.x, eye.x), MathHelper.lerp(b, startPos.y, eye.y),
					MathHelper.lerp(b, startPos.z, eye.z), MathHelper.lerpAngleDegrees((float) b, startYaw, yaw),
					(float) MathHelper.lerp(b, startPitch, pitch));
		};
	}

	/**
	 * Diagnostics for holes in the crater: compares the client's blocks around the crater with the
	 * server's and logs the sections that differ, plus sections the client thinks are empty.
	 */
	private static void compareWorlds(MinecraftClient client, IntegratedServer server, BlockPos center, int r) {
		int reach = r * 16 / 10;
		int x0 = center.getX() - reach;
		int z0 = center.getZ() - reach;
		int size = reach * 2 + 1;
		int y0 = center.getY() - 48;
		int height = 112;
		int[] serverIds;
		try {
			serverIds = server.submit(() -> {
				ServerWorld world = server.getOverworld();
				int[] ids = new int[size * size * height];
				BlockPos.Mutable pos = new BlockPos.Mutable();
				for (int i = 0; i < size; i++) {
					for (int k = 0; k < size; k++) {
						for (int j = 0; j < height; j++) {
							ids[(i * size + k) * height + j] = Block.getRawIdFromState(world.getBlockState(pos.set(x0 + i, y0 + j, z0 + k)));
						}
					}
				}
				return ids;
			}).get();
		} catch (Exception e) {
			ShootingStar.LOGGER.error("[selftest] could not read the server world", e);
			return;
		}
		Map<Long, Integer> sections = new TreeMap<>();
		Map<Long, String> examples = new TreeMap<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int mismatches = 0;
		for (int i = 0; i < size; i++) {
			for (int k = 0; k < size; k++) {
				if (!client.world.getChunkManager().isChunkLoaded((x0 + i) >> 4, (z0 + k) >> 4)) {
					// Past the client's view distance: it has no blocks there to compare.
					continue;
				}
				for (int j = 0; j < height; j++) {
					pos.set(x0 + i, y0 + j, z0 + k);
					BlockState mine = client.world.getBlockState(pos);
					int theirs = serverIds[(i * size + k) * height + j];
					if (Block.getRawIdFromState(mine) != theirs) {
						mismatches++;
						long key = ChunkSectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
						sections.merge(key, 1, Integer::sum);
						examples.putIfAbsent(key, pos.toShortString() + " client=" + mine + " server=" + Block.getStateFromRawId(theirs));
					}
				}
			}
		}
		ShootingStar.LOGGER.info("[selftest] client/server block mismatches around the crater: {} in {} sections", mismatches,
				sections.size());
		sections.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(40).forEach(e -> {
			ChunkSectionPos s = ChunkSectionPos.from(e.getKey());
			double dx = s.getSectionX() * 16 + 8 - center.getX();
			double dz = s.getSectionZ() * 16 + 8 - center.getZ();
			ShootingStar.LOGGER.info("[selftest]   section {} {} {} ({} blocks out): {} differ, e.g. {}", s.getSectionX(),
					s.getSectionY(), s.getSectionZ(), (int) Math.sqrt(dx * dx + dz * dz), e.getValue(), examples.get(e.getKey()));
		});
		int empty = 0;
		for (int sx = x0 >> 4; sx <= (x0 + size) >> 4; sx++) {
			for (int sz = z0 >> 4; sz <= (z0 + size) >> 4; sz++) {
				WorldChunk chunk = client.world.getChunkManager().getWorldChunk(sx, sz);
				if (chunk == null) {
					ShootingStar.LOGGER.info("[selftest]   chunk {} {} is not loaded on the client", sx, sz);
					continue;
				}
				for (int sy = y0 >> 4; sy <= (y0 + height) >> 4; sy++) {
					ChunkSection section = chunk.getSection(client.world.sectionCoordToIndex(sy));
					if (section.isEmpty() && !client.world.getBlockState(pos.set(sx * 16 + 8, sy * 16 + 8, sz * 16 + 8)).isAir()) {
						empty++;
						ShootingStar.LOGGER.info("[selftest]   section {} {} {} counts as empty but is not", sx, sy, sz);
					}
				}
			}
		}
		ShootingStar.LOGGER.info("[selftest] sections wrongly empty on the client: {}", empty);
	}

	private static void setUpPlayer(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		world.setTimeOfDay(5000);
		world.setWeather(12000, 0, false, false);
		player.changeGameMode(GameMode.CREATIVE);
		player.getAbilities().flying = true;
		player.sendAbilitiesUpdate();
		player.getInventory().setStack(0, new ItemStack(ModItems.GUNGNIR_UPLINK));

		BlockPos spawn = world.getSpawnPos();
		// Aim at the flattest spot just outside danger-close range so the crater and the camera shots are not
		// hidden by hills.
		double range = Targeting.minRange(Targeting.DEFAULT_RADIUS) + 8.0;
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
		// Climb until the uplink's own raycast reaches the target, so the first shot is a clean firing solution.
		double x = spawn.getX() + 0.5;
		double z = spawn.getZ() + 0.5;
		double eye = Math.max(top(world, spawn.getX(), spawn.getZ()) + 2.0, target.getY() + 6.0);
		for (int i = 0; i < 100 && !uplinkReaches(world, new Vec3d(x, eye, z), target); i++) {
			eye += 2.0;
		}
		look(server, x, eye - player.getStandingEyeHeight(), z, Vec3d.ofCenter(target));
		ShootingStar.LOGGER.info("[selftest] eye at {} {} {} aiming at {}", x, eye, z, target);
	}

	private static boolean uplinkReaches(ServerWorld world, Vec3d eye, BlockPos target) {
		BlockPos hit = Targeting.findTarget(world, eye, Vec3d.ofCenter(target).subtract(eye).normalize(), Targeting.MAX_RANGE);
		return hit != null && hit.getManhattanDistance(target) <= 2;
	}

	/** Moves the player at least {@code height} blocks above the ground at x/z, high enough to see {@code at}. */
	private static void lookFromAbove(IntegratedServer server, int x, int z, int height, Vec3d at) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		Vec3d from = new Vec3d(x + 0.5, top(world, x, z) + height, z + 0.5);
		for (int i = 0; i < 100 && !canSee(world, from, at); i++) {
			from = from.add(0, 2, 0);
		}
		look(server, from.x, from.y - player.getStandingEyeHeight(), from.z, at);
	}

	/** True when the line of sight from {@code eye} reaches {@code at} or stops within a few blocks of it. */
	private static boolean canSee(ServerWorld world, Vec3d eye, Vec3d at) {
		Vec3d delta = at.subtract(eye);
		BlockPos hit = Targeting.findTarget(world, eye, delta.normalize(), delta.length() + 4.0);
		return hit == null || Vec3d.ofCenter(hit).distanceTo(at) <= 6.0;
	}

	private static int top(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
	}

	/** Spread of ground heights over the middle of the crater, with water counted as rough. */
	private static double roughness(ServerWorld world, int cx, int cz) {
		double sum = 0;
		double sumSq = 0;
		int n = 0;
		int reach = Targeting.DEFAULT_RADIUS / 2;
		for (int dx = -reach; dx <= reach; dx += 8) {
			for (int dz = -reach; dz <= reach; dz += 8) {
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
