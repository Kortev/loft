package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.client.gap.ClientGap;
import io.github.kortev.shootingstar.client.gap.ClientGaps;
import io.github.kortev.shootingstar.gap.GapManager;
import io.github.kortev.shootingstar.gap.GapTimeline;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.strike.Targeting;
import java.util.ArrayDeque;
import java.util.Deque;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * With -Dshootingstar.selftest=gap: joins the quick-play world, turns the Genesis Key on flat ground a little
 * way off, records the whole Ginnungagap event as a video (see {@link Capture}), lets reality back in and
 * photographs what is left of the zone.
 */
public class GapSelfTest implements ClientModInitializer {
	private static final double RANGE = 64.0;

	private enum Stage { WAIT_WORLD, SETUP, SETTLE, FIRE, WATCH, AFTER, DONE, FINISHED }

	private record Still(int age, String name) {
	}

	private static final Deque<Still> STILLS = new ArrayDeque<>();
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static BlockPos target;
	private static boolean fallbackFired;

	@Override
	public void onInitializeClient() {
		if (!"gap".equals(System.getProperty("shootingstar.selftest"))) {
			return;
		}
		for (int age = 10; age < GapTimeline.END + 30; age += 14) {
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
		}, "shootingstar-gap-selftest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		ServerTickEvents.START_SERVER_TICK.register(server -> Capture.serverTickStart());
		ServerTickEvents.END_SERVER_TICK.register(server -> Capture.serverTickEnd());
		ClientTickEvents.END_CLIENT_TICK.register(GapSelfTest::tick);
	}

	private static String phase(int age) {
		if (age < GapTimeline.TURNED) return "key";
		if (age < GapTimeline.CLOSING) return "tear";
		if (age < GapTimeline.CONTACT) return "closing";
		if (age < GapTimeline.FRAMES) return "contact";
		if (age < GapTimeline.ERASURE) return "frames";
		if (age < GapTimeline.NOTHING) return "erasure";
		if (age < GapTimeline.END) return "nothing";
		return "hold";
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
					shot(client, "00_holding_key.png");
					stage = Stage.FIRE;
					ticks = 0;
				}
			}
			case FIRE -> {
				if (ticks == 1) {
					Capture.start(client, client.runDirectory.toPath().resolve("capture"));
				}
				if (ticks == 30) {
					ShootingStar.LOGGER.info("[selftest] turning the Genesis Key");
					client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				}
				if (ClientGaps.mine() != null) {
					ShootingStar.LOGGER.info("[selftest] Ginnungagap open");
					stage = Stage.WATCH;
					ticks = 0;
				} else if (ticks == 90 && !fallbackFired) {
					ShootingStar.LOGGER.warn("[selftest] the key found no target, opening the gap directly");
					fallbackFired = true;
					server.execute(() -> {
						ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
						GapManager.launch(player.getServerWorld(), target, player);
					});
				} else if (ticks > 200) {
					ShootingStar.LOGGER.error("[selftest] no Ginnungagap event ever started");
					stage = Stage.DONE;
				}
			}
			case WATCH -> {
				ClientGap gap = ClientGaps.mine();
				int age = gap != null ? gap.age : Integer.MAX_VALUE;
				while (!STILLS.isEmpty() && age >= STILLS.peek().age()) {
					shot(client, STILLS.poll().name());
				}
				if (age >= GapTimeline.END + 34 || gap == null && ticks > 1200) {
					Capture.stop();
					stage = Stage.AFTER;
					ticks = 0;
				}
			}
			case AFTER -> {
				int r = 96;
				if (ticks == 10) {
					ShootingStar.LOGGER.info("[selftest] letting reality back in");
					client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				}
				if (ticks == 50) {
					server.execute(() -> {
						ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
						player.changeGameMode(GameMode.CREATIVE);
						player.getAbilities().flying = true;
						player.sendAbilitiesUpdate();
					});
				}
				if (ticks == 60) {
					server.execute(() -> lookFrom(server, target.getX(), target.getZ() - r * 3 / 2, 70, Vec3d.ofCenter(target)));
				}
				if (ticks == 200) {
					shot(client, "90_zone_above.png");
					server.execute(() -> lookFrom(server, target.getX() - r - 12, target.getZ() + 6, 12, Vec3d.ofCenter(target.down(30))));
				}
				if (ticks == 320) {
					shot(client, "91_zone_edge.png");
				}
				if (ticks >= 340) {
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

	private static void setUpPlayer(IntegratedServer server) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
		world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
		world.setTimeOfDay(5000);
		world.setWeather(12000, 0, false, false);
		// On foot and in survival, so the hotbar, hearts, hunger and experience are all there at the end.
		player.changeGameMode(GameMode.SURVIVAL);
		player.getInventory().setStack(0, new ItemStack(ModItems.GENESIS_KEY));
		player.getInventory().setStack(1, new ItemStack(Items.DIAMOND_SWORD));
		player.getInventory().setStack(2, new ItemStack(Items.DIAMOND_PICKAXE));
		player.getInventory().setStack(3, new ItemStack(Items.ENDER_PEARL, 16));
		player.getInventory().setStack(4, new ItemStack(Items.BREAD, 12));
		player.getInventory().setStack(5, new ItemStack(Items.GRASS_BLOCK, 64));
		player.getInventory().setStack(6, new ItemStack(Items.TORCH, 32));
		player.getInventory().setStack(7, new ItemStack(Items.WATER_BUCKET));
		player.setExperienceLevel(30);

		BlockPos spawn = world.getSpawnPos();
		double bestScore = Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8;
			int x = spawn.getX() + (int) Math.round(Math.cos(angle) * RANGE);
			int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * RANGE);
			double score = roughness(world, x, z);
			if (score < bestScore) {
				bestScore = score;
				target = new BlockPos(x, top(world, x, z) - 1, z);
			}
		}
		double x = spawn.getX() + 0.5;
		double z = spawn.getZ() + 0.5;
		// Stand on the ground; if the target cannot be seen from there, stand on a pillar tall enough to see it.
		int ground = top(world, spawn.getX(), spawn.getZ());
		int feet = ground;
		while (feet < ground + 60 && !keyReaches(world, new Vec3d(x, feet + player.getStandingEyeHeight(), z), target)) {
			feet++;
		}
		for (int y = ground; y < feet; y++) {
			world.setBlockState(new BlockPos(spawn.getX(), y, spawn.getZ()), Blocks.STONE.getDefaultState());
		}
		double eye = feet + player.getStandingEyeHeight();
		look(server, x, feet, z, Vec3d.ofCenter(target));
		ShootingStar.LOGGER.info("[selftest] eye at {} {} {} aiming at {}", x, eye, z, target);
	}

	private static boolean keyReaches(ServerWorld world, Vec3d eye, BlockPos target) {
		BlockPos hit = Targeting.findTarget(world, eye, Vec3d.ofCenter(target).subtract(eye).normalize(), Targeting.MAX_RANGE);
		return hit != null && hit.getManhattanDistance(target) <= 2;
	}

	private static void lookFrom(IntegratedServer server, int x, int z, int height, Vec3d at) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		ServerWorld world = player.getServerWorld();
		Vec3d from = new Vec3d(x + 0.5, Math.max(top(world, x, z), at.y) + height, z + 0.5);
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
		for (int dx = -24; dx <= 24; dx += 6) {
			for (int dz = -24; dz <= 24; dz += 6) {
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
