package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import io.github.kortev.shootingstar.client.ClientStrike;
import io.github.kortev.shootingstar.client.ClientStrikes;
import io.github.kortev.shootingstar.registry.ModItems;
import io.github.kortev.shootingstar.strike.StrikeManager;
import io.github.kortev.shootingstar.strike.StrikeTimeline;
import java.util.ArrayDeque;
import java.util.Deque;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/**
 * With -Dshootingstar.selftest=true: joins the quick-play world, fires the uplink at a point 64
 * blocks away and saves a screenshot at every phase of the strike, then quits.
 */
public class ClientSelfTest implements ClientModInitializer {
	private enum Stage { WAIT_WORLD, SETUP, SETTLE, FIRE, WATCH, AFTER, DONE, FINISHED }

	private record Capture(int age, String name) {
	}

	private static final Deque<Capture> CAPTURES = new ArrayDeque<>();
	private static Stage stage = Stage.WAIT_WORLD;
	private static int ticks;
	private static BlockPos target;
	private static boolean fallbackFired;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("shootingstar.selftest")) {
			return;
		}
		int[] ages = {6, 20, 38, 62, 88, 112, 136, 158, 178, 222, 258, 278, 300, 312, 324, 336, 342, 348, 364, 400, 436, 470, 520};
		for (int age : ages) {
			CAPTURES.add(new Capture(age, String.format("%02d_age%03d_%s.png", CAPTURES.size() + 1, age, phase(age))));
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(15 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			ShootingStar.LOGGER.error("[selftest] timed out in stage {}", stage);
			Runtime.getRuntime().halt(3);
		}, "shootingstar-selftest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
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
				if (ticks == 260) {
					client.player.getInventory().selectedSlot = 0;
				}
				if (ticks >= 300) {
					shot(client, "00_holding_uplink.png");
					stage = Stage.FIRE;
					ticks = 0;
				}
			}
			case FIRE -> {
				if (ticks == 5) {
					ShootingStar.LOGGER.info("[selftest] using the uplink");
					client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				}
				if (ClientStrikes.mine() != null) {
					ShootingStar.LOGGER.info("[selftest] strike locked");
					stage = Stage.WATCH;
					ticks = 0;
				} else if (ticks == 60 && !fallbackFired) {
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
				int age = strike != null ? strike.age : Integer.MAX_VALUE;
				while (!CAPTURES.isEmpty() && age >= CAPTURES.peek().age()) {
					shot(client, CAPTURES.poll().name());
				}
				if (CAPTURES.isEmpty() || strike == null && ticks > 700) {
					stage = Stage.AFTER;
					ticks = 0;
				}
			}
			case AFTER -> {
				if (ticks == 20) {
					server.execute(() -> look(server, target.add(0, 60, -40), target));
				}
				if (ticks == 140) {
					shot(client, "90_crater_above.png");
					server.execute(() -> look(server, target.add(-50, 18, 8), target.add(0, 30, 0)));
				}
				if (ticks == 260) {
					shot(client, "91_crater_side.png");
					server.execute(() -> look(server, target.add(12, 6, 10), target.add(0, 2, 0)));
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
		int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX() + 64, spawn.getZ()) - 1;
		target = new BlockPos(spawn.getX() + 64, ground, spawn.getZ());
		int standY = Math.max(ground, world.getTopY(Heightmap.Type.MOTION_BLOCKING, spawn.getX(), spawn.getZ())) + 26;
		look(server, new BlockPos(spawn.getX(), standY, spawn.getZ()), target);
		ShootingStar.LOGGER.info("[selftest] standing at {} aiming at {}", spawn.withY(standY), target);
	}

	private static void look(IntegratedServer server, BlockPos from, BlockPos at) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
		double dx = at.getX() + 0.5 - (from.getX() + 0.5);
		double dy = at.getY() + 0.5 - (from.getY() + player.getStandingEyeHeight());
		double dz = at.getZ() + 0.5 - (from.getZ() + 0.5);
		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * MathHelper.DEGREES_PER_RADIAN);
		player.networkHandler.requestTeleport(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, yaw, pitch);
	}
}
